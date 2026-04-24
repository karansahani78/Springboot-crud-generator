package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates REST Controller classes with OpenAPI 3.0 documentation and pagination support.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>All endpoints now return {@code EntityDto} (via {@code Mapper.toDto()}) instead
 *       of raw entity objects. This prevents:
 *       <ul>
 *         <li>Jackson infinite recursion on bidirectional relationships</li>
 *         <li>Hibernate {@code LazyInitializationException} outside the transaction</li>
 *         <li>Leaking internal entity graph to API consumers</li>
 *       </ul>
 *   </li>
 *   <li>URL path uses kebab-case conversion ({@code OrderItem} → {@code order-items})
 *       instead of naive {@code toLowerCase()} which produced {@code orderitem}.</li>
 *   <li>Replaced 78-arg positional {@code String.format} with a builder method —
 *       each endpoint is assembled independently and is readable.</li>
 * </ul>
 */
public class ControllerGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String basePkg       = meta.basePackage();
        String controllerPkg = basePkg + ".controller";
        PsiDirectory dir     = PsiDirectoryUtil.createPackageDirs(root, controllerPkg);

        String entity    = meta.getClassName();              // "OrderItem"
        String idType    = meta.getIdType();                 // "Long"
        String urlPath   = toKebabCase(entity) + "s";       // "order-items"
        String varName   = meta.getVariableName();           // "orderItem"

        String code = buildControllerCode(
                basePkg, controllerPkg, meta.getPackageName(),
                entity, idType, urlPath, varName
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        entity + "Controller.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }

    // =========================================================================
    // Main code builder
    // =========================================================================

    private static String buildControllerCode(
            String basePkg,
            String controllerPkg,
            String entityPackage,
            String entity,
            String idType,
            String urlPath,
            String varName
    ) {
        String imports   = buildImports(basePkg, entityPackage, entity);
        String classHead = buildClassHead(entity, urlPath);
        String getAll    = buildGetAll(entity, urlPath);
        String getPaged  = buildGetPaginated(entity, urlPath);
        String getById   = buildGetById(entity, idType, urlPath);
        String create    = buildCreate(entity, idType, urlPath, varName);
        String update    = buildUpdate(entity, idType, urlPath, varName);
        String delete    = buildDelete(entity, idType, urlPath);
        String exists    = buildExists(entity, idType, urlPath);
        String count     = buildCount(entity, urlPath);

        return "package " + controllerPkg + ";\n\n"
                + imports + "\n"
                + classHead
                + getAll
                + getPaged
                + getById
                + create
                + update
                + delete
                + exists
                + count
                + "}\n";
    }

    // =========================================================================
    // Imports
    // =========================================================================

    private static String buildImports(String basePkg, String entityPackage, String entity) {
        return String.format("""
                import %s.%s;
                import %s.dto.%sDto;
                import %s.dto.ErrorResponse;
                import %s.dto.PageResponse;
                import %s.mapper.%sMapper;
                import %s.service.%sService;
                import io.swagger.v3.oas.annotations.Operation;
                import io.swagger.v3.oas.annotations.Parameter;
                import io.swagger.v3.oas.annotations.media.Content;
                import io.swagger.v3.oas.annotations.media.Schema;
                import io.swagger.v3.oas.annotations.responses.ApiResponse;
                import io.swagger.v3.oas.annotations.responses.ApiResponses;
                import io.swagger.v3.oas.annotations.tags.Tag;
                import jakarta.validation.Valid;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.data.domain.Page;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                import java.util.List;
                import java.util.stream.Collectors;
                
                """,
                entityPackage, entity,       // entity import
                basePkg, entity,             // DTO import
                basePkg,                     // ErrorResponse
                basePkg,                     // PageResponse
                basePkg, entity,             // Mapper import
                basePkg, entity              // Service import
        );
    }

    // =========================================================================
    // Class declaration + constructor
    // =========================================================================

    private static String buildClassHead(String entity, String urlPath) {
        return String.format("""
                /**
                 * REST Controller for {@link %s} entity operations.
                 *
                 * <p>All responses return {@link %sDto} — raw entity objects are never
                 * exposed to avoid circular-reference serialisation issues and
                 * lazy-loading exceptions on relationship fields.
                 */
                @RestController
                @RequestMapping("/api/%s")
                @Tag(name = "%s Management", description = "Operations for managing %s resources")
                public class %sController {
                
                    private static final Logger log = LoggerFactory.getLogger(%sController.class);
                    private final %sService service;
                
                    public %sController(%sService service) {
                        this.service = service;
                    }
                
                """,
                entity, entity,              // Javadoc @link
                urlPath,                     // @RequestMapping
                entity, entity,              // @Tag name + description
                entity,                      // class name
                entity,                      // logger
                entity,                      // field type
                entity, entity               // constructor
        );
    }

    // =========================================================================
    // GET /  — find all (unpaginated)
    // =========================================================================

    private static String buildGetAll(String entity, String urlPath) {
        return String.format("""
                    @Operation(summary = "Get all %s", description = "Retrieve all %s entities (unpaginated)")
                    @ApiResponses({
                            @ApiResponse(responseCode = "200", description = "Successfully retrieved list",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = %sDto.class))),
                            @ApiResponse(responseCode = "500", description = "Internal server error",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)))
                    })
                    @GetMapping
                    public ResponseEntity<List<%sDto>> getAll() {
                        log.debug("GET /api/%s - Retrieving all entities");
                        List<%sDto> result = service.findAll().stream()
                                .map(%sMapper::toDto)
                                .collect(Collectors.toList());
                        return ResponseEntity.ok(result);
                    }
                
                """,
                entity, entity,   // Operation summary/description
                entity,           // schema impl
                entity,           // return type
                urlPath,          // log path
                entity,           // stream type
                entity            // Mapper::toDto
        );
    }

    // =========================================================================
    // GET /paginated
    // =========================================================================

    private static String buildGetPaginated(String entity, String urlPath) {
        return String.format("""
                    @Operation(summary = "Get paginated %s", description = "Retrieve a paginated, sorted list of %s entities")
                    @ApiResponses({
                            @ApiResponse(responseCode = "200", description = "Successfully retrieved paginated list",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = PageResponse.class))),
                            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)))
                    })
                    @GetMapping("/paginated")
                    public ResponseEntity<PageResponse<%sDto>> getAllPaginated(
                            @Parameter(description = "Page number (0-indexed)", example = "0")
                            @RequestParam(defaultValue = "0") int page,
                            @Parameter(description = "Page size (max 100)", example = "10")
                            @RequestParam(defaultValue = "10") int size,
                            @Parameter(description = "Field to sort by", example = "id")
                            @RequestParam(defaultValue = "id") String sortBy,
                            @Parameter(description = "Sort direction (ASC/DESC)", example = "ASC")
                            @RequestParam(defaultValue = "ASC") String sortDirection
                    ) {
                        log.debug("GET /api/%s/paginated — page={}, size={}, sortBy={}, dir={}",
                                page, size, sortBy, sortDirection);
                
                        Page<%s> pageResult = service.findAllPaginated(page, size, sortBy, sortDirection);
                        Page<%sDto> dtoPage = pageResult.map(%sMapper::toDto);
                        PageResponse<%sDto> response = PageResponse.of(dtoPage);
                
                        log.debug("Returning page {} with {} items", page, response.getContent().size());
                        return ResponseEntity.ok(response);
                    }
                
                """,
                entity, entity,   // Operation
                entity,           // return type PageResponse<EntityDto>
                urlPath,          // log path
                entity,           // Page<Entity> from service
                entity,           // Page<EntityDto> after map
                entity,           // Mapper::toDto
                entity            // PageResponse<EntityDto>
        );
    }

    // =========================================================================
    // GET /{id}
    // =========================================================================

    private static String buildGetById(String entity, String idType, String urlPath) {
        return String.format("""
                    @Operation(summary = "Get %s by ID", description = "Retrieve a specific %s by its primary key")
                    @ApiResponses({
                            @ApiResponse(responseCode = "200", description = "Successfully retrieved",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = %sDto.class))),
                            @ApiResponse(responseCode = "404", description = "%s not found",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class))),
                            @ApiResponse(responseCode = "400", description = "Invalid ID supplied",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)))
                    })
                    @GetMapping("/{id}")
                    public ResponseEntity<%sDto> getById(
                            @Parameter(description = "ID of the %s to retrieve", required = true)
                            @PathVariable %s id
                    ) {
                        log.debug("GET /api/%s/{} - Retrieving by id", id);
                        %sDto dto = %sMapper.toDto(service.findById(id));
                        return ResponseEntity.ok(dto);
                    }
                
                """,
                entity, entity,   // Operation
                entity,           // schema impl
                entity,           // 404 description
                entity,           // return type
                entity,           // @Parameter description
                idType,           // @PathVariable type
                urlPath,          // log path
                entity,           // EntityDto var
                entity            // Mapper.toDto
        );
    }

    // =========================================================================
    // POST /
    // =========================================================================

    private static String buildCreate(String entity, String idType, String urlPath, String varName) {
        return String.format("""
                    @Operation(summary = "Create %s", description = "Create a new %s entity")
                    @ApiResponses({
                            @ApiResponse(responseCode = "201", description = "%s created successfully",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = %sDto.class))),
                            @ApiResponse(responseCode = "400", description = "Invalid input data",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class))),
                            @ApiResponse(responseCode = "409", description = "%s already exists",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)))
                    })
                    @PostMapping
                    public ResponseEntity<%sDto> create(
                            @Parameter(description = "%s data to create", required = true)
                            @Valid @RequestBody %sDto dto
                    ) {
                        log.info("POST /api/%s - Creating %s: {}", dto);
                        %s created = service.create(dto);
                        log.info("Created %s with id={}", created.getId());
                        return ResponseEntity.status(HttpStatus.CREATED).body(%sMapper.toDto(created));
                    }
                
                """,
                entity, entity,   // Operation
                entity,           // 201 description
                entity,           // schema impl
                entity,           // 409 description
                entity,           // return type
                entity,           // @Parameter description
                entity,           // @RequestBody type
                urlPath,          // log path
                entity,           // log entity name
                entity,           // created var type
                entity,           // log entity name
                entity            // Mapper.toDto
        );
    }

    // =========================================================================
    // PUT /{id}
    // =========================================================================

    private static String buildUpdate(String entity, String idType, String urlPath, String varName) {
        return String.format("""
                    @Operation(summary = "Update %s", description = "Update an existing %s entity by ID")
                    @ApiResponses({
                            @ApiResponse(responseCode = "200", description = "%s updated successfully",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = %sDto.class))),
                            @ApiResponse(responseCode = "404", description = "%s not found",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class))),
                            @ApiResponse(responseCode = "400", description = "Invalid input data",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)))
                    })
                    @PutMapping("/{id}")
                    public ResponseEntity<%sDto> update(
                            @Parameter(description = "ID of the %s to update", required = true)
                            @PathVariable %s id,
                            @Parameter(description = "Updated %s data", required = true)
                            @Valid @RequestBody %sDto dto
                    ) {
                        log.info("PUT /api/%s/{} - Updating %s: {}", id, dto);
                        %s updated = service.update(id, dto);
                        log.info("Updated %s with id={}", updated.getId());
                        return ResponseEntity.ok(%sMapper.toDto(updated));
                    }
                
                """,
                entity, entity,   // Operation
                entity,           // 200 description
                entity,           // schema impl
                entity,           // 404 description
                entity,           // return type
                entity,           // @Parameter id description
                idType,           // @PathVariable type
                entity,           // @Parameter body description
                entity,           // @RequestBody type
                urlPath,          // log path
                entity,           // log entity name
                entity,           // updated var type
                entity,           // log entity name
                entity            // Mapper.toDto
        );
    }

    // =========================================================================
    // DELETE /{id}
    // =========================================================================

    private static String buildDelete(String entity, String idType, String urlPath) {
        return String.format("""
                    @Operation(summary = "Delete %s", description = "Delete a %s entity by ID")
                    @ApiResponses({
                            @ApiResponse(responseCode = "204", description = "%s deleted successfully"),
                            @ApiResponse(responseCode = "404", description = "%s not found",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class))),
                            @ApiResponse(responseCode = "400", description = "Invalid ID supplied",
                                    content = @Content(mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)))
                    })
                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(
                            @Parameter(description = "ID of the %s to delete", required = true)
                            @PathVariable %s id
                    ) {
                        log.info("DELETE /api/%s/{} - Deleting %s id={}", id);
                        service.delete(id);
                        return ResponseEntity.noContent().build();
                    }
                
                """,
                entity, entity,   // Operation
                entity,           // 204 description
                entity,           // 404 description
                entity,           // @Parameter description
                idType,           // @PathVariable type
                urlPath,          // log path
                entity            // log entity name
        );
    }

    // =========================================================================
    // HEAD /{id}  — existence check
    // =========================================================================

    private static String buildExists(String entity, String idType, String urlPath) {
        return String.format("""
                    @Operation(summary = "Check %s exists", description = "Returns 200 if the %s exists, 404 otherwise")
                    @ApiResponses({
                            @ApiResponse(responseCode = "200", description = "%s exists"),
                            @ApiResponse(responseCode = "404", description = "%s not found")
                    })
                    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
                    public ResponseEntity<Void> exists(
                            @Parameter(description = "ID of the %s to check", required = true)
                            @PathVariable %s id
                    ) {
                        log.debug("HEAD /api/%s/{} - Checking existence", id);
                        return service.existsById(id)
                                ? ResponseEntity.ok().build()
                                : ResponseEntity.notFound().build();
                    }
                
                """,
                entity, entity,   // Operation
                entity,           // 200 description
                entity,           // 404 description
                entity,           // @Parameter description
                idType,           // @PathVariable type
                urlPath           // log path
        );
    }

    // =========================================================================
    // GET /count
    // =========================================================================

    private static String buildCount(String entity, String urlPath) {
        return String.format("""
                    @Operation(summary = "Count %s entities", description = "Returns the total number of %s records")
                    @ApiResponses({
                            @ApiResponse(responseCode = "200", description = "Successfully retrieved count")
                    })
                    @GetMapping("/count")
                    public ResponseEntity<Long> count() {
                        log.debug("GET /api/%s/count - Counting entities");
                        return ResponseEntity.ok(service.count());
                    }
                
                """,
                entity, entity,   // Operation
                urlPath           // log path
        );
    }

    // =========================================================================
    // Utility — class name → kebab-case URL segment
    // =========================================================================

    /**
     * Converts a PascalCase class name to kebab-case for use in URL paths.
     *
     * <pre>
     *   "OrderItem"       → "order-item"
     *   "Department"      → "department"
     *   "UserProfile"     → "user-profile"
     * </pre>
     */
    static String toKebabCase(String className) {
        if (className == null || className.isEmpty()) return className;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}