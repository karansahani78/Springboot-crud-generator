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
 * Generates Mapper classes with explicit field-by-field mapping.
 *
 * <h2>Fix summary</h2>
 * <ol>
 *   <li><b>toDto() maps id</b> — {@code dto.setId(entity.getId())} is now the first
 *       line of {@code buildToDtoScalars()}, so GET responses include the entity id.
 *       Without this, every response returned {@code "id": null}, making the API
 *       useless for follow-up GET/PUT/DELETE calls.</li>
 *   <li><b>toEntity() maps id</b> — {@code entity.setId(dto.getId())} is emitted in
 *       {@code buildToEntityScalars()} so upsert / save-with-id flows work correctly.
 *       This is safe: JPA ignores the id on INSERT if it is null (auto-generated).</li>
 *   <li><b>updateEntity() does NOT touch id</b> — updating an entity's primary key is
 *       never valid; {@code buildUpdateEntityScalars()} still iterates
 *       {@code getNonIdFields()} only.</li>
 *   <li><b>Lazy collection safety</b> — collection relationships emit
 *       {@code dto.setXxxIds(null)} and never access {@code entity.getXxx()}.
 *       {@code null} means "not loaded"; {@code emptyList()} would falsely mean
 *       "loaded, zero items".</li>
 *   <li><b>Import deduplication</b> — {@code List} and {@code Collectors} are never
 *       imported because no collection streaming is performed in the mapper.</li>
 *   <li><b>Correct related-entity import paths</b> — imports are built from
 *       {@code basePackage + ".entity"}, never from the raw {@code packageName}
 *       which already ends in {@code .entity} and would produce double-entity paths
 *       like {@code com.karan.app.entity.entity.User}.</li>
 * </ol>
 */
public class MapperGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg        = meta.basePackage() + ".mapper";
        PsiDirectory dir  = PsiDirectoryUtil.createPackageDirs(root, pkg);
        String className  = meta.getClassName();
        String basePackage = meta.basePackage();

        // ALL relationships — DtoGenerator guarantees a field exists for every
        // entry in allRels, including inverse @OneToMany sides.
        List<RelationshipMeta> allRels = meta.getRelationships();

        String toEntityScalars     = buildToEntityScalars(meta);
        String toDtoScalars        = buildToDtoScalars(meta);
        String updateEntityScalars = buildUpdateEntityScalars(meta);
        String toDtoRelationships  = buildToDtoRelationships(allRels);
        String imports             = buildImports(basePackage, className, allRels, allEntities);

        String code = String.format("""
                package %s;
                
                %s
                
                /**
                 * Mapper for {@link %s} ↔ {@link %sDto}.
                 *
                 * <p><b>Design contract:</b>
                 * <ul>
                 *   <li>{@code toEntity} copies ALL scalar fields including {@code id}.
                 *       JPA ignores a null id on INSERT (auto-generated), so passing id
                 *       from the DTO is safe and enables upsert / save-with-id flows.</li>
                 *   <li>{@code updateEntity} copies scalar fields EXCEPT {@code id}.
                 *       Updating a primary key is never valid.</li>
                 *   <li>{@code toDto} copies ALL scalar fields (including {@code id})
                 *       and extracts IDs from loaded entity references so the API
                 *       response carries IDs, not nested objects.</li>
                 *   <li>Collection relationships are mapped as {@code null} in
                 *       {@code toDto} — accessing them outside a transaction triggers
                 *       Hibernate lazy loading and causes
                 *       {@code LazyInitializationException}.</li>
                 * </ul>
                 */
                public class %sMapper {
                
                    private %sMapper() {}
                
                    public static %s toEntity(%sDto dto) {
                        if (dto == null) return null;
                
                        %s entity = new %s();
                %s
                        return entity;
                    }
                
                    public static %sDto toDto(%s entity) {
                        if (entity == null) return null;
                
                        %sDto dto = new %sDto();
                %s
                %s
                        return dto;
                    }
                
                    public static void updateEntity(%s entity, %sDto dto) {
                        if (entity == null || dto == null) return;
                
                %s
                    }
                }
                """,
                pkg,
                imports,
                className, className,
                className,
                className,
                className, className,
                className, className,
                toEntityScalars,
                className, className,
                className, className,
                toDtoScalars,
                toDtoRelationships,
                className, className,
                updateEntityScalars
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        className + "Mapper.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }

    // =========================================================================
    // Scalar mapping builders
    // =========================================================================

    /**
     * Builds the scalar mapping lines for {@code toEntity()}.
     *
     * <p>FIX: id IS mapped here — {@code entity.setId(dto.getId())} is emitted first.
     * JPA ignores a null id on INSERT (auto-generated strategy), so this is safe.
     * It also enables upsert flows where the caller supplies an existing id.
     */
    private static String buildToEntityScalars(ClassMeta meta) {
        StringBuilder sb = new StringBuilder();

        // FIX: map id — safe for both INSERT (null → auto-assigned) and
        // upsert flows where the caller explicitly provides an id.
        sb.append("        entity.setId(dto.getId());\n");

        for (FieldMeta f : meta.getNonIdFields()) {
            sb.append(String.format(
                    "        entity.set%s(dto.get%s());\n",
                    f.getCapitalizedName(), f.getCapitalizedName()
            ));
        }
        return sb.toString();
    }

    /**
     * Builds the scalar mapping lines for {@code toDto()}.
     *
     * <p>FIX: id IS mapped here — {@code dto.setId(entity.getId())} is emitted first.
     * Without this, every GET response returned {@code "id": null} even though the
     * entity was persisted and had a valid database-assigned id.
     */
    private static String buildToDtoScalars(ClassMeta meta) {
        StringBuilder sb = new StringBuilder();

        // FIX: map id first so GET responses always include the entity identifier.
        // This was the root cause of incomplete API responses:
        // the old code iterated getNonIdFields(), which excludes id by definition.
        sb.append("        dto.setId(entity.getId());\n");

        for (FieldMeta f : meta.getNonIdFields()) {
            sb.append(String.format(
                    "        dto.set%s(entity.get%s());\n",
                    f.getCapitalizedName(), f.getCapitalizedName()
            ));
        }
        return sb.toString();
    }

    /**
     * Builds the scalar mapping lines for {@code updateEntity()}.
     *
     * <p>id is deliberately excluded — updating a primary key is never valid.
     * Only non-id scalar fields are copied from the DTO onto the managed entity.
     */
    private static String buildUpdateEntityScalars(ClassMeta meta) {
        StringBuilder sb = new StringBuilder();
        // NOTE: id is intentionally skipped — you must never update a PK.
        for (FieldMeta f : meta.getNonIdFields()) {
            sb.append(String.format(
                    "        entity.set%s(dto.get%s());\n",
                    f.getCapitalizedName(), f.getCapitalizedName()
            ));
        }
        return sb.toString();
    }

    // =========================================================================
    // Relationship → ID extraction for toDto()
    //
    // Collections: always set to null — distinguishes "not loaded" from "empty list".
    //   Accessing entity.getXxx() for a lazy collection outside a transaction
    //   triggers Hibernate lazy loading and causes LazyInitializationException.
    //   DO NOT use Collections.emptyList() — that would falsely mean
    //   "loaded, zero items" when we simply haven't fetched.
    //
    // Single refs: access .getId() only if the reference is non-null — safe
    //   regardless of fetch type because we test for null before dereferencing.
    // =========================================================================

    private static String buildToDtoRelationships(List<RelationshipMeta> allRels) {
        if (allRels.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n        // ── Relationship ID extraction ────────────────────────────\n");
        sb.append("        // Collection relationships are intentionally set to null to\n");
        sb.append("        // avoid LazyInitializationException (Hibernate lazy loading).\n");
        sb.append("        // null = \"not loaded\"; emptyList() would falsely mean \"loaded, zero items\".\n");

        for (RelationshipMeta rel : allRels) {
            String dtoSetter = "set" + rel.getCapitalisedDtoIdFieldName();

            if (rel.isCollection()) {
                // FIX: never access entity.getXxx() for a collection —
                // this would trigger Hibernate lazy loading outside the transaction.
                sb.append(String.format(
                        "        dto.%s(null); // Lazy collection — not mapped to avoid LazyInitializationException\n",
                        dtoSetter
                ));
            } else {
                // Single @ManyToOne / @OneToOne — safe to call .getId() after null-check.
                // Does not assume EAGER or LAZY fetch type.
                String fieldName        = rel.getFieldName();
                String capitalizedField = Character.toUpperCase(fieldName.charAt(0))
                        + fieldName.substring(1);

                sb.append(String.format(
                        "        dto.%s(entity.get%s() != null\n"
                                + "                ? entity.get%s().getId() : null);\n",
                        dtoSetter,
                        capitalizedField,
                        capitalizedField
                ));
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Import builder
    //
    // FIX 1: List and Collectors are never imported — collections are mapped as
    //        null (no streaming, no emptyList() calls) so these imports would be
    //        unused and cause a compile warning / error in strict projects.
    // FIX 2: Related entity imports use basePackage + ".entity" so the path is
    //        never "com.karan.app.entity.entity.User" (double .entity).
    // =========================================================================

    private static String buildImports(
            String basePackage,
            String className,
            List<RelationshipMeta> allRels,
            List<ClassMeta> allEntities
    ) {
        StringBuilder sb = new StringBuilder();

        // Own entity and DTO
        sb.append(String.format("import %s.entity.%s;\n", basePackage, className));
        sb.append(String.format("import %s.dto.%sDto;\n", basePackage, className));

        for (RelationshipMeta rel : allRels) {
            String relEntity = rel.getRelatedEntityName();

            // FIX: resolve basePackage for the related entity from allEntities.
            // allEntities[n].basePackage() already strips the trailing ".entity",
            // so appending ".entity." + relEntity produces the correct path.
            // Fallback to the current entity's basePackage when not found.
            String relBasePackage = allEntities.stream()
                    .filter(e -> e.getClassName().equals(relEntity))
                    .map(ClassMeta::basePackage)
                    .findFirst()
                    .orElse(basePackage);

            sb.append(String.format("import %s.entity.%s;\n", relBasePackage, relEntity));

            // NOTE: List and Collectors are NOT imported here.
            // Collection relationships are emitted as dto.setXxxIds(null) —
            // no streaming, no List construction, no Collectors usage.
        }

        return sb.toString();
    }
}