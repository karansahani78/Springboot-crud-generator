package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.RelationshipMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates the Service layer with full relationship-aware CRUD logic.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — used to confirm related
 *       repositories exist before injecting them.</li>
 *   <li>Constructor injects one repository per related entity (MANY_TO_ONE,
 *       ONE_TO_ONE owning, MANY_TO_MANY owning sides).</li>
 *   <li>{@code create()} and {@code update()} fetch each related entity by the
 *       ID supplied in the DTO and set it on the entity before saving.</li>
 *   <li>Inverse ONE_TO_MANY (mappedBy) sides are read-only and are NOT
 *       resolved here — they are managed by the owning side.</li>
 * </ul>
 */
public class ServiceGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String basePackage = meta.basePackage();
        String pkg         = basePackage + ".service";
        PsiDirectory dir   = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String className  = meta.getClassName();
        String varName    = meta.getVariableName();          // e.g. "department"
        String idType     = meta.getIdType();

        // Relationships that the SERVICE needs to resolve:
        // - MANY_TO_ONE, ONE_TO_ONE owning, MANY_TO_MANY owning
        // - Inverse ONE_TO_MANY (mappedBy != "") are excluded — managed by owning side
        List<RelationshipMeta> dtoRels = meta.getDtoRelationships();

        // ── Imports ───────────────────────────────────────────────────────
        StringBuilder imports = buildImports(basePackage, className, dtoRels, allEntities);

        // ── Constructor injection ──────────────────────────────────────────
        StringBuilder fieldDeclarations  = new StringBuilder();
        StringBuilder constructorParams  = new StringBuilder();
        StringBuilder constructorAssigns = new StringBuilder();

        // Primary repository — always present
        fieldDeclarations.append(String.format(
                "    private final %sRepository repository;\n", className));
        constructorParams.append(String.format(
                "%sRepository repository", className));
        constructorAssigns.append(
                "        this.repository = repository;\n");

        // One additional repository per DTO relationship
        for (RelationshipMeta rel : dtoRels) {
            String repoClass = rel.getRelatedRepositoryClassName(); // e.g. "DepartmentRepository"
            String repoField = rel.getRelatedRepositoryFieldName(); // e.g. "departmentRepository"

            fieldDeclarations.append(String.format(
                    "    private final %s %s;\n", repoClass, repoField));
            constructorParams.append(String.format(
                    ",\n            %s %s", repoClass, repoField));
            constructorAssigns.append(String.format(
                    "        this.%s = %s;\n", repoField, repoField));
        }

        // ── Relationship resolution blocks (create / update) ──────────────
        String relResolutionBlock = buildRelationshipResolutionBlock(dtoRels, varName);

        // ── Relationship setter calls (after mapper maps scalars) ──────────
        String relSetterBlock = buildRelationshipSetterBlock(dtoRels, varName);

        // ── Full service code ─────────────────────────────────────────────
        String code = buildServiceCode(
                pkg, basePackage, className, varName, idType,
                imports, fieldDeclarations, constructorParams, constructorAssigns,
                relResolutionBlock, relSetterBlock
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        className + "Service.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }

    // =========================================================================
    // Import builder
    // =========================================================================

    private static StringBuilder buildImports(
            String basePackage,
            String className,
            List<RelationshipMeta> dtoRels,
            List<ClassMeta> allEntities
    ) {
        StringBuilder sb = new StringBuilder();

        // Core imports
        sb.append(String.format("import %s.entity.%s;\n",          basePackage, className));
        sb.append(String.format("import %s.dto.%sDto;\n",           basePackage, className));
        sb.append(String.format("import %s.mapper.%sMapper;\n",     basePackage, className));
        sb.append(String.format("import %s.repository.%sRepository;\n", basePackage, className));
        sb.append(String.format("import %s.exception.ResourceNotFoundException;\n", basePackage));
        sb.append(String.format("import %s.exception.BadRequestException;\n",       basePackage));

        // Per-relationship imports
        for (RelationshipMeta rel : dtoRels) {
            String relEntity = rel.getRelatedEntityName();

            // Find the related entity's package from allEntities list
            // Fall back to basePackage.entity if not found (safe default)
            String relPackage = allEntities.stream()
                    .filter(e -> e.getClassName().equals(relEntity))
                    .map(ClassMeta::getPackageName)
                    .findFirst()
                    .orElse(basePackage + ".entity");

            sb.append(String.format("import %s.%s;\n",              relPackage, relEntity));
            sb.append(String.format("import %s.repository.%sRepository;\n",
                    basePackage, relEntity));

            if (rel.isCollection()) {
                sb.append("import java.util.List;\n");
                sb.append("import java.util.stream.Collectors;\n");
            }
        }

        // Standard Spring / utility imports
        sb.append("""
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.PageRequest;
                import org.springframework.data.domain.Pageable;
                import org.springframework.data.domain.Sort;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import java.util.List;
                """);

        return sb;
    }

    // =========================================================================
    // Relationship resolution — fetches related entities from DB by ID
    // =========================================================================

    /**
     * Builds the block that appears at the TOP of create() and update(),
     * resolving each related entity from its repository before the mapper runs.
     *
     * <p>Example output:
     * <pre>
     *   Department department = departmentRepository.findById(dto.getDepartmentId())
     *       .orElseThrow(() -> new ResourceNotFoundException("Department", "id", dto.getDepartmentId()));
     *   List<Role> roleList = dto.getRoleIds().stream()
     *       .map(id -> roleRepository.findById(id)
     *           .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id)))
     *       .collect(Collectors.toList());
     * </pre>
     */
    private static String buildRelationshipResolutionBlock(
            List<RelationshipMeta> dtoRels,
            String ownerVarName
    ) {
        if (dtoRels.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        for (RelationshipMeta rel : dtoRels) {
            String relEntity  = rel.getRelatedEntityName();         // "Department"
            String repoField  = rel.getRelatedRepositoryFieldName(); // "departmentRepository"
            String dtoGetter  = "dto.get" + rel.getCapitalisedDtoIdFieldName() + "()";

            if (rel.isCollection()) {
                // Fetch list of related entities
                String localListVar = rel.getFieldName() + "List"; // "rolesList" / "employeeList"
                sb.append(String.format("""
                        List<%s> %s = %s.stream()
                                .map(id -> %s.findById(id)
                                        .orElseThrow(() -> new ResourceNotFoundException("%s", "id", id)))
                                .collect(Collectors.toList());
                        """,
                        relEntity, localListVar, dtoGetter,
                        repoField,
                        relEntity
                ));
            } else {
                // Fetch single related entity
                String localVar = Character.toLowerCase(relEntity.charAt(0))
                        + relEntity.substring(1); // "department"
                sb.append(String.format("""
                        %s %s = %s.findById(%s)
                                .orElseThrow(() -> new ResourceNotFoundException("%s", "id", %s));
                        """,
                        relEntity, localVar, repoField, dtoGetter,
                        relEntity, dtoGetter
                ));
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Relationship setter calls — applied to the entity AFTER mapper runs
    // =========================================================================

    /**
     * Builds the block that sets resolved related entities onto the entity object.
     *
     * <p>Example output:
     * <pre>
     *   employee.setDepartment(department);
     *   employee.setRoles(rolesList);
     * </pre>
     */
    private static String buildRelationshipSetterBlock(
            List<RelationshipMeta> dtoRels,
            String ownerVarName
    ) {
        if (dtoRels.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        for (RelationshipMeta rel : dtoRels) {
            String fieldName = rel.getFieldName(); // "department" / "roles"
            String capitalizedField = Character.toUpperCase(fieldName.charAt(0))
                    + fieldName.substring(1); // "Department" / "Roles"

            if (rel.isCollection()) {
                String localListVar = rel.getFieldName() + "List"; // "rolesList"
                sb.append(String.format(
                        "        %s.set%s(%s);\n",
                        ownerVarName, capitalizedField, localListVar
                ));
            } else {
                String localVar = Character.toLowerCase(rel.getRelatedEntityName().charAt(0))
                        + rel.getRelatedEntityName().substring(1); // "department"
                sb.append(String.format(
                        "        %s.set%s(%s);\n",
                        ownerVarName, capitalizedField, localVar
                ));
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Full service class assembly
    // =========================================================================

    private static String buildServiceCode(
            String pkg,
            String basePackage,
            String className,
            String varName,
            String idType,
            StringBuilder imports,
            StringBuilder fieldDeclarations,
            StringBuilder constructorParams,
            StringBuilder constructorAssigns,
            String relResolutionBlock,
            String relSetterBlock
    ) {
        // Indent the resolution block for readability inside method body
        String indentedResolution = relResolutionBlock.isEmpty()
                ? ""
                : "        " + relResolutionBlock.replace("\n", "\n        ").stripTrailing() + "\n\n";

        return String.format("""
                package %s;
                
                %s
                
                /**
                 * Service class for %s entity operations.
                 *
                 * <p>Handles full CRUD with pagination, sorting, and
                 * relationship resolution via injected repositories.
                 */
                @Service
                @Transactional(readOnly = true)
                public class %sService {
                
                    private static final Logger log = LoggerFactory.getLogger(%sService.class);
                
                %s
                    public %sService(%s) {
                %s    }
                
                    // ── Read ──────────────────────────────────────────────────────────
                
                    /**
                     * Returns all %s entities (use paginated version for large datasets).
                     */
                    public List<%s> findAll() {
                        log.debug("Finding all %s entities");
                        return repository.findAll();
                    }
                
                    /**
                     * Returns a paginated, sorted page of %s entities.
                     */
                    public Page<%s> findAllPaginated(int page, int size, String sortBy, String sortDirection) {
                        log.debug("Paginating %s — page={}, size={}, sort={} {}", page, size, sortBy, sortDirection);
                
                        if (page < 0) throw new BadRequestException("Page number cannot be negative");
                        if (size <= 0) throw new BadRequestException("Page size must be greater than 0");
                        if (size > 100) {
                            log.warn("Page size {} exceeds maximum, capping at 100", size);
                            size = 100;
                        }
                
                        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection)
                                ? Sort.Direction.DESC : Sort.Direction.ASC;
                
                        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
                        Page<%s> result = repository.findAll(pageable);
                
                        log.debug("Found {} of {} total %s entities on page {}",
                                result.getNumberOfElements(), result.getTotalElements(), result.getNumber());
                        return result;
                    }
                
                    /**
                     * Finds a single %s by primary key.
                     *
                     * @throws ResourceNotFoundException when no record matches the given id
                     */
                    public %s findById(%s id) {
                        log.debug("Finding %s by id={}", id);
                        if (id == null) throw new BadRequestException("ID cannot be null");
                
                        return repository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("%s", "id", id));
                    }
                
                    // ── Write ─────────────────────────────────────────────────────────
                
                    /**
                     * Creates a new %s from the supplied DTO.
                     *
                     * <p>Relationship fields are resolved from their repositories
                     * using the IDs provided in the DTO.
                     */
                    @Transactional
                    public %s create(%sDto dto) {
                        log.info("Creating %s from dto={}", dto);
                        if (dto == null) throw new BadRequestException("DTO cannot be null");
                
                %s
                        %s %s = %sMapper.toEntity(dto);
                %s
                        %s saved = repository.save(%s);
                        log.info("Created %s id={}", saved.getId());
                        return saved;
                    }
                
                    /**
                     * Updates an existing %s identified by {@code id} using the supplied DTO.
                     *
                     * <p>Relationship fields are re-resolved on every update call
                     * so stale foreign-key references are never persisted.
                     */
                    @Transactional
                    public %s update(%s id, %sDto dto) {
                        log.info("Updating %s id={} with dto={}", id, dto);
                        if (id == null)  throw new BadRequestException("ID cannot be null");
                        if (dto == null) throw new BadRequestException("DTO cannot be null");
                
                %s
                        %s %s = findById(id);
                        %sMapper.updateEntity(%s, dto);
                %s
                        %s updated = repository.save(%s);
                        log.info("Updated %s id={}", updated.getId());
                        return updated;
                    }
                
                    /**
                     * Deletes the %s with the given {@code id}.
                     *
                     * @throws ResourceNotFoundException when no record matches the given id
                     */
                    @Transactional
                    public void delete(%s id) {
                        log.info("Deleting %s id={}", id);
                        if (id == null) throw new BadRequestException("ID cannot be null");
                
                        %s entity = findById(id);
                        repository.delete(entity);
                        log.info("Deleted %s id={}", id);
                    }
                
                    // ── Utility ───────────────────────────────────────────────────────
                
                    public boolean existsById(%s id) {
                        return id != null && repository.existsById(id);
                    }
                
                    public long count() {
                        return repository.count();
                    }
                }
                """,
                // package
                pkg,
                // imports block
                imports,
                // Javadoc class name
                className,
                // class name ×2
                className, className,
                // field declarations
                fieldDeclarations,
                // constructor signature
                className, constructorParams,
                // constructor body
                constructorAssigns,
                // findAll
                className, className, className,
                // findAllPaginated ×2 entity name in log
                className, className, className, className, className,
                // findById
                className, className, idType, className, className,
                // create — method signature + body
                className,
                className, className, className,
                indentedResolution,               // relationship resolution
                className, varName, className,    // entity = Mapper.toEntity(dto)
                relSetterBlock,                   // entity.setDepartment(department); ...
                className, varName,               // saved = repository.save(varName)
                className,                        // log "Created X id=..."
                // update — method signature + body
                className,
                className, idType, className, className,
                indentedResolution,               // relationship resolution (same block)
                className, varName,               // entity = findById(id)
                className, varName,               // Mapper.updateEntity(entity, dto)
                relSetterBlock,                   // entity.setDepartment(department); ...
                className, varName,               // updated = repository.save(varName)
                className,                        // log "Updated X id=..."
                // delete
                className, idType, className,
                className,
                className, idType,
                // existsById
                idType
        );
    }
}