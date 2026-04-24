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
 * <p>Multi-entity changes — replaces {@code BeanUtils.copyProperties} with
 * hand-rolled setters for two critical reasons:
 * <ol>
 *   <li>{@code BeanUtils} matches by property name only — it cannot bridge
 *       {@code dto.departmentId} ↔ {@code entity.department} (different names,
 *       different types). It silently does nothing for every relationship field.</li>
 *   <li>Relationship fields must be handled asymmetrically:
 *       <ul>
 *         <li>{@code toEntity}    — skip ALL relationship fields (service sets them)</li>
 *         <li>{@code toDto}       — extract IDs from loaded entity objects</li>
 *         <li>{@code updateEntity}— skip ALL relationship fields (service re-resolves)</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Generated {@code toDto()} example for an {@code Employee} with
 * {@code @ManyToOne Department} and {@code @ManyToMany List<Role>}:
 * <pre>
 *   dto.setDepartmentId(entity.getDepartment() != null
 *       ? entity.getDepartment().getId() : null);
 *   dto.setRoleIds(entity.getRoles() != null
 *       ? entity.getRoles().stream()
 *           .map(r -> r.getId())
 *           .collect(Collectors.toList())
 *       : java.util.Collections.emptyList());
 * </pre>
 */
public class MapperGenerator {

    /**
     * Entry point — updated to accept {@code allEntities} to match the
     * standard multi-entity generator contract.  The list is used to look up
     * the ID type of related entities when building {@code toDto()} collection
     * mappings (e.g. confirming the ID is {@code Long} vs {@code UUID}).
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

        String pkg       = meta.basePackage() + ".mapper";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String className = meta.getClassName();
        String basePackage = meta.basePackage();

        // Relationships that appear in the DTO
        // (inverse ONE_TO_MANY sides are excluded — they have no DTO field)
        List<RelationshipMeta> dtoRels = meta.getDtoRelationships();

        // All relationships (including inverse) needed for toDto() —
        // we still want to read collection IDs from inverse sides for GET responses
        List<RelationshipMeta> allRels = meta.getRelationships();

        boolean needsListImport = allRels.stream().anyMatch(RelationshipMeta::isCollection);

        // ── Scalar field mapping blocks ────────────────────────────────────
        String toEntityScalars   = buildToEntityScalars(meta);
        String toDtoScalars      = buildToDtoScalars(meta);
        String updateEntityScalars = buildUpdateEntityScalars(meta);

        // ── Relationship mapping blocks ────────────────────────────────────
        // toEntity / updateEntity: intentionally empty — service handles these
        // toDto: extract IDs from loaded entity references
        String toDtoRelationships = buildToDtoRelationships(allRels);

        // ── Imports ───────────────────────────────────────────────────────
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
                
                    // Private constructor — static utility class
                    private %sMapper() {}
                
                    // ── toEntity ─────────────────────────────────────────────────────
                
                    /**
                     * Converts a {@link %sDto} to a new {@link %s} entity.
                     *
                     * <p>Only scalar (non-relationship) fields are copied.
                     * The caller (service layer) is responsible for setting
                     * all relationship fields before persisting.
                     *
                     * @param dto source DTO; returns {@code null} if {@code dto} is {@code null}
                     */
                    public static %s toEntity(%sDto dto) {
                        if (dto == null) return null;
                
                        %s entity = new %s();
                %s
                        return entity;
                    }
                
                    // ── toDto ────────────────────────────────────────────────────────
                
                    /**
                     * Converts a {@link %s} entity to a {@link %sDto}.
                     *
                     * <p>Scalar fields are copied directly.
                     * Relationship fields are converted to their ID equivalents
                     * (e.g. {@code entity.getDepartment().getId()} → {@code dto.setDepartmentId(...)}).
                     *
                     * @param entity source entity; returns {@code null} if {@code entity} is {@code null}
                     */
                    public static %sDto toDto(%s entity) {
                        if (entity == null) return null;
                
                        %sDto dto = new %sDto();
                %s
                %s
                        return dto;
                    }
                
                    // ── updateEntity ─────────────────────────────────────────────────
                
                    /**
                     * Applies scalar fields from {@code dto} onto an existing {@code entity}.
                     *
                     * <p>The entity's primary key ({@code id}) is never overwritten.
                     * Relationship fields are deliberately skipped — the service layer
                     * re-resolves and re-sets them on every update call.
                     *
                     * @param entity target entity (must not be {@code null})
                     * @param dto    source DTO   (must not be {@code null})
                     */
                    public static void updateEntity(%s entity, %sDto dto) {
                        if (entity == null || dto == null) return;
                
                %s
                    }
                }
                """,
                pkg,                        // package statement
                imports,                    // import block
                className, className,       // Javadoc @link
                className,                  // class name
                className,                  // private constructor
                className, className,       // toEntity Javadoc
                className, className,       // toEntity signature
                className, className,       // entity = new Entity()
                toEntityScalars,            // entity.setX(dto.getX()); ...
                className, className,       // toDto Javadoc
                className, className,       // toDto signature
                className, className,       // dto = new Dto()
                toDtoScalars,              // dto.setX(entity.getX()); ...
                toDtoRelationships,        // dto.setDeptId(entity.getDept().getId()); ...
                className, className,       // updateEntity signature
                updateEntityScalars        // entity.setX(dto.getX()); ...
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
     * Generates {@code entity.setX(dto.getX())} for every non-id scalar field.
     * Relationship fields are completely absent — that is intentional.
     */
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

    /**
     * Generates {@code dto.setX(entity.getX())} for every non-id scalar field.
     * Relationship ID extraction is handled separately in
     * {@link #buildToDtoRelationships(List)}.
     */
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

    /**
     * Same as {@link #buildToEntityScalars} — used for the {@code updateEntity} method.
     * ID is always excluded (the entity keeps its own PK).
     */
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
    // =========================================================================

    /**
     * Generates null-safe ID extraction from loaded entity references.
     *
     * <p>Uses ALL relationships (including inverse ONE_TO_MANY) so that GET
     * responses include the full picture of associations even when the current
     * entity is not the owner.
     *
     * <p>Examples:
     * <pre>
     *   // MANY_TO_ONE / ONE_TO_ONE
     *   dto.setDepartmentId(entity.getDepartment() != null
     *       ? entity.getDepartment().getId() : null);
     *
     *   // ONE_TO_MANY / MANY_TO_MANY
     *   dto.setRoleIds(entity.getRoles() != null
     *       ? entity.getRoles().stream()
     *           .map(r -> r.getId())
     *           .collect(Collectors.toList())
     *       : java.util.Collections.emptyList());
     * </pre>
     */
    private static String buildToDtoRelationships(List<RelationshipMeta> allRels) {
        if (allRels.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n        // ── Relationship ID extraction ────────────────────────────\n");

        for (RelationshipMeta rel : allRels) {
            String fieldName      = rel.getFieldName();       // "department" / "roles"
            String capitalizedField = Character.toUpperCase(fieldName.charAt(0))
                    + fieldName.substring(1);                 // "Department" / "Roles"

            // The DTO setter name differs from the entity field name:
            // entity.getDepartment() → dto.setDepartmentId()
            // entity.getRoles()      → dto.setRoleIds()
            String dtoSetter = "set" + rel.getCapitalisedDtoIdFieldName();

            if (rel.isCollection()) {
                // Use a short single-letter lambda variable derived from entity name
                String lambdaVar = String.valueOf(
                        Character.toLowerCase(rel.getRelatedEntityName().charAt(0)));

                sb.append(String.format("""
                        dto.%s(entity.get%s() != null
                                ? entity.get%s().stream()
                                    .map(%s -> %s.getId())
                                    .collect(Collectors.toList())
                                : java.util.Collections.emptyList());
                        """,
                        dtoSetter,
                        capitalizedField,  // entity.getRoles()
                        capitalizedField,  // .stream()
                        lambdaVar,         // r ->
                        lambdaVar          // r.getId()
                ));
            } else {
                sb.append(String.format("""
                        dto.%s(entity.get%s() != null
                                ? entity.get%s().getId() : null);
                        """,
                        dtoSetter,
                        capitalizedField,  // entity.getDepartment()
                        capitalizedField   // .getId()
                ));
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Import builder
    // =========================================================================

    private static String buildImports(
            String basePackage,
            String className,
            List<RelationshipMeta> allRels,
            List<ClassMeta> allEntities
    ) {
        StringBuilder sb = new StringBuilder();

        // Entity and DTO
        sb.append(String.format("import %s.entity.%s;\n", basePackage, className));
        sb.append(String.format("import %s.dto.%sDto;\n", basePackage, className));

        // Related entity imports (for toDto() type references in Javadoc & cast safety)
        for (RelationshipMeta rel : allRels) {
            String relEntity = rel.getRelatedEntityName();
            String relPackage = allEntities.stream()
                    .filter(e -> e.getClassName().equals(relEntity))
                    .map(ClassMeta::getPackageName)
                    .findFirst()
                    .orElse(basePackage + ".entity");

            sb.append(String.format("import %s.%s;\n", relPackage, relEntity));

            if (rel.isCollection()) {
                sb.append("import java.util.List;\n");
                sb.append("import java.util.stream.Collectors;\n");
            }
        }

        return sb.toString();
    }
}