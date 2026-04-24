package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.FieldMeta;
import com.karan.intellijplatformplugin.model.RelationshipMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates a DTO class for a JPA entity.
 *
 * <p>Scalar fields are emitted as-is (minus the @Id field).
 * Relationship fields are emitted as ID references:
 * <ul>
 *   <li>@ManyToOne / @OneToOne  → {@code private Long fieldNameId;}</li>
 *   <li>@OneToMany / @ManyToMany → {@code private List<Long> fieldNameIds;}</li>
 * </ul>
 * ALL relationship sides are included (including inverse @OneToMany with mappedBy)
 * so the generated Mapper never calls a setter that doesn't exist in the DTO.
 * Inverse collection fields are marked NOT_REQUIRED and carry no @NotNull —
 * they are populated on GET responses but ignored by the service on POST/PUT.
 */
public class DtoGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".dto";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        // ALL relationships — including inverse OneToMany — so every
        // setter the Mapper calls has a matching field in the DTO.
        List<RelationshipMeta> allRels = meta.getRelationships();

        boolean needsListImport = allRels.stream().anyMatch(RelationshipMeta::isCollection);

        StringBuilder fields         = new StringBuilder();
        StringBuilder gettersSetters = new StringBuilder();
        StringBuilder toStringParts  = new StringBuilder();
        int fieldCount = 0;

        // ── 1. Scalar fields (skip @Id — not part of create/update body) ──
        for (FieldMeta f : meta.getFields()) {
            if (f.getName().equalsIgnoreCase("id")) continue;

            String fieldName       = f.getName();
            String fieldType       = f.getType();
            String capitalizedName = f.getCapitalizedName();

            appendField(fields,
                    capitalizedName,
                    meta.getClassName().toLowerCase(),
                    fieldName,
                    fieldType,
                    false);
            appendGetter(gettersSetters, fieldType, capitalizedName, fieldName);
            appendSetter(gettersSetters, capitalizedName, fieldType, fieldName);

            if (fieldCount++ > 0) toStringParts.append(" + \", \" +\n                ");
            toStringParts.append("\"").append(fieldName).append("='\" + ")
                    .append(fieldName).append(" + \"'\"");
        }

        // ── 2. Relationship fields — ALL sides so Mapper always compiles ──
        for (RelationshipMeta rel : allRels) {

            // Inverse side = collection with mappedBy set.
            // Still emitted so Mapper's dto.setXxxIds(...) compiles,
            // but marked NOT_REQUIRED / no @NotNull.
            boolean isInverse = rel.isCollection() && !rel.getMappedBy().isEmpty();

            if (rel.isCollection()) {
                // @OneToMany / @ManyToMany → List<IdType>
                String dtoField       = rel.getDtoIdFieldName();            // e.g. "commentIds"
                String capitalizedDto = rel.getCapitalisedDtoIdFieldName(); // e.g. "CommentIds"
                String idType         = rel.getRelatedEntityIdType();       // e.g. "Long"
                String listType       = "List<" + idType + ">";

                String schemaDesc = meta.getClassName().toLowerCase()
                        + " " + rel.getRelatedEntityName().toLowerCase() + " ids"
                        + (isInverse ? " (read-only, populated on GET)" : "");

                appendField(fields, capitalizedDto, schemaDesc, dtoField, listType, isInverse);
                appendGetter(gettersSetters, listType, capitalizedDto, dtoField);
                appendSetter(gettersSetters, capitalizedDto, listType, dtoField);

            } else {
                // @ManyToOne / @OneToOne → single IdType
                String dtoField       = rel.getDtoIdFieldName();            // e.g. "userId"
                String capitalizedDto = rel.getCapitalisedDtoIdFieldName(); // e.g. "UserId"
                String idType         = rel.getRelatedEntityIdType();       // e.g. "Long"

                String schemaDesc = rel.getRelatedEntityName() + " id for "
                        + meta.getClassName().toLowerCase();

                appendField(fields, capitalizedDto, schemaDesc, dtoField, idType, false);
                appendGetter(gettersSetters, idType, capitalizedDto, dtoField);
                appendSetter(gettersSetters, capitalizedDto, idType, dtoField);
            }

            if (fieldCount++ > 0) toStringParts.append(" + \", \" +\n                ");
            String dtoField = rel.getDtoIdFieldName();
            toStringParts.append("\"").append(dtoField).append("='\" + ")
                    .append(dtoField).append(" + \"'\"");
        }

        String toStringMethod = buildToString(meta.getClassName(), toStringParts, fieldCount);
        String listImport     = needsListImport ? "import java.util.List;\n" : "";

        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                import jakarta.validation.constraints.NotNull;
                %s
                /**
                 * DTO for %s entity.
                 *
                 * <p>Relationship fields are represented as IDs to avoid circular
                 * references and keep the API contract simple.
                 * Fields marked "read-only" are populated on GET responses but
                 * ignored during POST/PUT — the service manages them.
                 */
                @Schema(description = "Data Transfer Object for %s")
                public class %sDto {
                
                %s
                %s
                %s
                }
                """,
                pkg,
                listImport,
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                fields,
                gettersSetters,
                toStringMethod
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        meta.getClassName() + "Dto.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Appends a single annotated field declaration.
     *
     * @param sb               target string builder
     * @param capitalizedName  PascalCase name — used in @Schema description and @NotNull message
     * @param schemaDescription human-readable description suffix for @Schema
     * @param fieldName        camelCase Java field name
     * @param fieldType        Java type (e.g. "String", "Long", "List<Long>")
     * @param readOnly         true for inverse collection fields — omits @NotNull,
     *                         sets requiredMode = NOT_REQUIRED
     */
    private static void appendField(
            StringBuilder sb,
            String capitalizedName,
            String schemaDescription,
            String fieldName,
            String fieldType,
            boolean readOnly
    ) {
        String notNullLine = readOnly
                ? ""
                : String.format("    @NotNull(message = \"%s cannot be null\")\n", capitalizedName);

        sb.append(String.format(
                "    @Schema(description = \"%s of the %s\", example = \"Sample %s\", requiredMode = Schema.RequiredMode.%s)\n"
                        + "%s"
                        + "    private %s %s;\n\n",
                capitalizedName,                             // description label
                schemaDescription,                           // description context
                fieldName,                                   // example value
                readOnly ? "NOT_REQUIRED" : "REQUIRED",      // requiredMode
                notNullLine,                                 // @NotNull line or ""
                fieldType,                                   // Java type
                fieldName                                    // Java field name
        ));
    }

    private static void appendGetter(
            StringBuilder sb,
            String returnType,
            String capitalizedName,
            String fieldName
    ) {
        sb.append(String.format("""
                    public %s get%s() {
                        return %s;
                    }
                
                """, returnType, capitalizedName, fieldName));
    }

    private static void appendSetter(
            StringBuilder sb,
            String capitalizedName,
            String paramType,
            String fieldName
    ) {
        sb.append(String.format("""
                    public void set%s(%s %s) {
                        this.%s = %s;
                    }
                
                """, capitalizedName, paramType, fieldName, fieldName, fieldName));
    }

    private static String buildToString(
            String className,
            StringBuilder toStringParts,
            int fieldCount
    ) {
        if (fieldCount > 0) {
            return String.format("""
                        @Override
                        public String toString() {
                            return "%sDto{" +
                                    %s +
                                    "}";
                        }
                    """, className, toStringParts);
        }
        return String.format("""
                    @Override
                    public String toString() {
                        return "%sDto{}";
                    }
                """, className);
    }
}