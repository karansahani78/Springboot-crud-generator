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
 * Generates DTO classes with OpenAPI schema annotations.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Relationship fields are emitted as ID fields, not entity references.
 *       e.g. {@code private Department department} → {@code private Long departmentId}</li>
 *   <li>ONE_TO_MANY / MANY_TO_MANY owning sides → {@code private List<Long> roleIds}</li>
 *   <li>Inverse ONE_TO_MANY sides (mappedBy set) are excluded from the DTO entirely
 *       to prevent circular writes.</li>
 *   <li>{@code List} import is added automatically when collection IDs are present.</li>
 * </ul>
 */
public class DtoGenerator {

    /**
     * Updated signature — accepts full entity list for relationship resolution.
     * The {@code allEntities} list is not directly used inside DtoGenerator
     * (relationship data is already on {@code meta.getRelationships()}) but is
     * part of the standard multi-entity generator contract so the call site is
     * uniform across all generators.
     */
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

        // Determine whether we need a List import (for collection ID fields)
        boolean needsListImport = meta.getDtoRelationships().stream()
                .anyMatch(RelationshipMeta::isCollection);

        // ── Field declarations ────────────────────────────────────────────
        StringBuilder fields         = new StringBuilder();
        StringBuilder gettersSetters = new StringBuilder();
        StringBuilder toStringParts  = new StringBuilder();
        int fieldCount = 0;

        // 1. Scalar fields (skip the primary key — not part of create/update DTO)
        for (FieldMeta f : meta.getFields()) {
            if (f.getName().equalsIgnoreCase("id")) {
                continue;
            }

            String fieldName      = f.getName();
            String fieldType      = f.getType();
            String capitalizedName = f.getCapitalizedName();

            appendField(fields, capitalizedName, meta.getClassName().toLowerCase(),
                    fieldName, fieldType);
            appendGetter(gettersSetters, fieldType, capitalizedName, fieldName);
            appendSetter(gettersSetters, capitalizedName, fieldType, fieldName);

            if (fieldCount++ > 0) toStringParts.append(", ");
            toStringParts.append(fieldName).append("='\" + ").append(fieldName).append(" + \"'");
        }

        // 2. Relationship ID fields
        //    getDtoRelationships() already filters out inverse ONE_TO_MANY sides.
        for (RelationshipMeta rel : meta.getDtoRelationships()) {

            if (rel.isCollection()) {
                // ONE_TO_MANY (owning) / MANY_TO_MANY → List<IdType>
                String dtoField       = rel.getDtoIdFieldName();          // e.g. "roleIds"
                String capitalizedDto = rel.getCapitalisedDtoIdFieldName(); // e.g. "RoleIds"
                String idType         = rel.getRelatedEntityIdType();     // e.g. "Long"
                String listType       = "List<" + idType + ">";

                appendField(fields, capitalizedDto,
                        meta.getClassName().toLowerCase() + " " + rel.getRelatedEntityName().toLowerCase() + " ids",
                        dtoField, listType);
                appendGetter(gettersSetters, listType, capitalizedDto, dtoField);
                appendSetter(gettersSetters, capitalizedDto, listType, dtoField);

                if (fieldCount++ > 0) toStringParts.append(", ");
                toStringParts.append(dtoField).append("='\" + ").append(dtoField).append(" + \"'");

            } else {
                // MANY_TO_ONE / ONE_TO_ONE → single ID
                String dtoField       = rel.getDtoIdFieldName();          // e.g. "departmentId"
                String capitalizedDto = rel.getCapitalisedDtoIdFieldName(); // e.g. "DepartmentId"
                String idType         = rel.getRelatedEntityIdType();     // e.g. "Long"

                appendField(fields, capitalizedDto,
                        rel.getRelatedEntityName() + " id for " + meta.getClassName().toLowerCase(),
                        dtoField, idType);
                appendGetter(gettersSetters, idType, capitalizedDto, dtoField);
                appendSetter(gettersSetters, capitalizedDto, idType, dtoField);

                if (fieldCount++ > 0) toStringParts.append(", ");
                toStringParts.append(dtoField).append("='\" + ").append(dtoField).append(" + \"'");
            }
        }

        // ── toString ─────────────────────────────────────────────────────
        String toStringMethod = buildToString(meta.getClassName(), toStringParts, fieldCount);

        // ── Assemble file ─────────────────────────────────────────────────
        String listImport = needsListImport ? "import java.util.List;\n" : "";

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

    private static void appendField(
            StringBuilder sb,
            String capitalizedName,
            String schemaDescription,
            String fieldName,
            String fieldType
    ) {
        sb.append(String.format("""
                    @Schema(description = "%s of the %s", example = "Sample %s", requiredMode = Schema.RequiredMode.REQUIRED)
                    @NotNull(message = "%s cannot be null")
                    private %s %s;
                
                """,
                capitalizedName,
                schemaDescription,
                fieldName,
                capitalizedName,
                fieldType,
                fieldName
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
                                "%s" +
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