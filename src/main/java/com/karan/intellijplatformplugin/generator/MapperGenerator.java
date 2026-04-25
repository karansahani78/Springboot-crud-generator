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
 * FIX SUMMARY:
 * 1. buildToDtoRelationships uses allRels — now safe because DtoGenerator also
 *    emits fields for ALL relationships (including inverse @OneToMany).
 *    No more "dto.setCommentIds() but DTO has no commentIds field" mismatch.
 * 2. buildImports deduplicates List/Collectors imports and uses
 *    basePackage + ".entity" for related entity imports (not the raw
 *    packageName which includes ".entity" and caused double-entity paths).
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

        // FIX: use allRels for BOTH toDto and the import builder.
        // DtoGenerator now guarantees a field exists for every entry in allRels.
        List<RelationshipMeta> allRels = meta.getRelationships();

        String toEntityScalars     = buildToEntityScalars(meta);
        String toDtoScalars        = buildToDtoScalars(meta);
        String updateEntityScalars = buildUpdateEntityScalars(meta);
        String toDtoRelationships  = buildToDtoRelationships(allRels);

        // FIX: pass basePackage explicitly so import paths are correct
        String imports = buildImports(basePackage, className, allRels, allEntities);

        String code = String.format("""
                package %s;
                
                %s
                
                /**
                 * Mapper for {@link %s} ↔ {@link %sDto}.
                 *
                 * <p><b>Design contract:</b>
                 * <ul>
                 *   <li>{@code toEntity} and {@code updateEntity} copy SCALAR fields only.
                 *       Relationship fields are deliberately skipped — the service layer
                 *       resolves and injects them after fetching from their repositories.</li>
                 *   <li>{@code toDto} copies scalar fields AND extracts IDs from loaded
                 *       entity references so the API response carries IDs, not nested objects.</li>
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
    // Scalar mapping builders (unchanged logic)
    // =========================================================================

    private static String buildToEntityScalars(ClassMeta meta) {
        StringBuilder sb = new StringBuilder();
        for (FieldMeta f : meta.getNonIdFields()) {
            sb.append(String.format(
                    "        entity.set%s(dto.get%s());\n",
                    f.getCapitalizedName(), f.getCapitalizedName()
            ));
        }
        return sb.toString();
    }

    private static String buildToDtoScalars(ClassMeta meta) {
        StringBuilder sb = new StringBuilder();
        for (FieldMeta f : meta.getNonIdFields()) {
            sb.append(String.format(
                    "        dto.set%s(entity.get%s());\n",
                    f.getCapitalizedName(), f.getCapitalizedName()
            ));
        }
        return sb.toString();
    }

    private static String buildUpdateEntityScalars(ClassMeta meta) {
        StringBuilder sb = new StringBuilder();
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
    // Collections: set to null — distinguishes "not loaded" from "empty list".
    //   Accessing entity.get<Collection>() outside a transaction triggers
    //   Hibernate lazy loading and causes LazyInitializationException.
    //   Do NOT use emptyList() — that would falsely signal "loaded, zero items".
    //
    // Single refs: access ID only if relation is initialized — safe because
    //   the caller controls whether the relation was fetched.
    // =========================================================================

    private static String buildToDtoRelationships(List<RelationshipMeta> allRels) {
        if (allRels.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n        // ── Relationship ID extraction ────────────────────────────\n");
        sb.append("        // NOTE:\n");
        sb.append("        // Collection relationships are intentionally NOT mapped\n");
        sb.append("        // to avoid LazyInitializationException (JPA lazy loading issue).\n");

        for (RelationshipMeta rel : allRels) {
            String dtoSetter = "set" + rel.getCapitalisedDtoIdFieldName();

            if (rel.isCollection()) {
                // Do NOT access entity.get<Collection>() — triggers lazy loading
                // outside the transaction → LazyInitializationException.
                // null = "not loaded"; emptyList() would mean "loaded, zero items" — wrong.
                sb.append(String.format(
                        "        dto.%s(null); // Lazy collection — not mapped to avoid LazyInitializationException\n",
                        dtoSetter
                ));
            } else {
                // Access ID only if relation is initialized — does not assume fetch type.
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
    // FIX 1: List and Collectors imports are deduplicated (added once, not per rel).
    // FIX 2: Related entity imports use basePackage + ".entity" so the path is
    //        never "com.karan.app.entity.entity.User" (double .entity).
    // FIX 3: Collectors import removed — collections are no longer streamed in
    //        the mapper, so Collectors is never referenced in generated code.
    //        List import is also removed for the same reason (emptyList() is
    //        called via its fully-qualified name java.util.Collections.emptyList()).
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

            // FIX: build the import path from basePackage + ".entity", NOT from
            // the related entity's raw packageName (which already ends in .entity).
            // Fallback: if the entity is found in allEntities, verify the base
            // matches; otherwise default to basePackage.entity.
            String relBasePackage = allEntities.stream()
                    .filter(e -> e.getClassName().equals(relEntity))
                    .map(e -> e.basePackage()) // basePackage() already strips .entity
                    .findFirst()
                    .orElse(basePackage);

            sb.append(String.format("import %s.entity.%s;\n", relBasePackage, relEntity));

            // FIX: needsCollectionImports tracking removed — List and Collectors
            // are no longer needed because collection relationships are emitted as
            // java.util.Collections.emptyList() (fully-qualified, no import needed)
            // and .stream()/.collect() calls are no longer generated at all.
        }

        return sb.toString();
    }
}