package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates REST Controller classes with OpenAPI 3.0 documentation and pagination support.
 */
public class ControllerGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String basePkg = meta.basePackage();
        String controllerPkg = basePkg + ".controller";

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, controllerPkg);

        String entity = meta.getClassName();
        String entityPackage = meta.getPackageName();
        String idType = meta.getIdType();
        String lower = entity.toLowerCase();

        String code = String.format("""
                package %s;
                
                import %s.%s;
                import %s.dto.%sDto;
                import %s.dto.ErrorResponse;
                import %s.dto.PageResponse;
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
                
                /**
                 * REST Controller for %s entity operations with pagination support.
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
                
                    @Operation(
                            summary = "Get all %s",
                            description = "Retrieve a list of all %s entities (unpaginated)"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "200",
                                    description = "Successfully retrieved list",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = %s.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "500",
                                    description = "Internal server error",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            )
                    })
                    @GetMapping
                    public ResponseEntity<List<%s>> getAll() {
                        log.debug("GET /api/%s - Retrieving all entities");
                        List<%s> entities = service.findAll();
                        return ResponseEntity.ok(entities);
                    }
                
                    @Operation(
                            summary = "Get paginated %s",
                            description = "Retrieve paginated and sorted list of %s entities"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "200",
                                    description = "Successfully retrieved paginated list",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = PageResponse.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "400",
                                    description = "Invalid pagination parameters",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            )
                    })
                    @GetMapping("/paginated")
                    public ResponseEntity<PageResponse<%s>> getAllPaginated(
                            @Parameter(description = "Page number (0-indexed)", example = "0")
                            @RequestParam(defaultValue = "0") int page,
                            
                            @Parameter(description = "Page size (max 100)", example = "10")
                            @RequestParam(defaultValue = "10") int size,
                            
                            @Parameter(description = "Sort field", example = "id")
                            @RequestParam(defaultValue = "id") String sortBy,
                            
                            @Parameter(description = "Sort direction (ASC/DESC)", example = "ASC")
                            @RequestParam(defaultValue = "ASC") String sortDirection
                    ) {
                        log.debug("GET /api/%s/paginated - page: {}, size: {}, sortBy: {}, direction: {}", 
                                  page, size, sortBy, sortDirection);
                        
                        Page<%s> pageResult = service.findAllPaginated(page, size, sortBy, sortDirection);
                        PageResponse<%s> response = PageResponse.of(pageResult);
                        
                        log.debug("Returning page {} with {} items", page, response.getContent().size());
                        return ResponseEntity.ok(response);
                    }
                
                    @Operation(
                            summary = "Get %s by ID",
                            description = "Retrieve a specific %s entity by its ID"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "200",
                                    description = "Successfully retrieved entity",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = %s.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "404",
                                    description = "%s not found",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "400",
                                    description = "Invalid ID supplied",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            )
                    })
                    @GetMapping("/{id}")
                    public ResponseEntity<%s> getById(
                            @Parameter(description = "ID of the %s to retrieve", required = true)
                            @PathVariable %s id
                    ) {
                        log.debug("GET /api/%s/{} - Retrieving entity by ID", id);
                        %s entity = service.findById(id);
                        return ResponseEntity.ok(entity);
                    }
                
                    @Operation(
                            summary = "Create a new %s",
                            description = "Create a new %s entity"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "201",
                                    description = "%s created successfully",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = %s.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "400",
                                    description = "Invalid input data",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "409",
                                    description = "%s already exists",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            )
                    })
                    @PostMapping
                    public ResponseEntity<%s> create(
                            @Parameter(description = "%s data to create", required = true)
                            @Valid @RequestBody %sDto dto
                    ) {
                        log.info("POST /api/%s - Creating new entity: {}", dto);
                        %s created = service.create(dto);
                        log.info("Created entity with ID: {}", created);
                        return ResponseEntity.status(HttpStatus.CREATED).body(created);
                    }
                
                    @Operation(
                            summary = "Update %s",
                            description = "Update an existing %s entity by ID"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "200",
                                    description = "%s updated successfully",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = %s.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "404",
                                    description = "%s not found",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "400",
                                    description = "Invalid input data",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            )
                    })
                    @PutMapping("/{id}")
                    public ResponseEntity<%s> update(
                            @Parameter(description = "ID of the %s to update", required = true)
                            @PathVariable %s id,
                            @Parameter(description = "Updated %s data", required = true)
                            @Valid @RequestBody %sDto dto
                    ) {
                        log.info("PUT /api/%s/{} - Updating entity: {}", id, dto);
                        %s updated = service.update(id, dto);
                        log.info("Updated entity: {}", updated);
                        return ResponseEntity.ok(updated);
                    }
                
                    @Operation(
                            summary = "Delete %s",
                            description = "Delete a %s entity by ID"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "204",
                                    description = "%s deleted successfully"
                            ),
                            @ApiResponse(
                                    responseCode = "404",
                                    description = "%s not found",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            ),
                            @ApiResponse(
                                    responseCode = "400",
                                    description = "Invalid ID supplied",
                                    content = @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ErrorResponse.class)
                                    )
                            )
                    })
                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(
                            @Parameter(description = "ID of the %s to delete", required = true)
                            @PathVariable %s id
                    ) {
                        log.info("DELETE /api/%s/{} - Deleting entity", id);
                        service.delete(id);
                        log.info("Deleted entity with ID: {}", id);
                        return ResponseEntity.noContent().build();
                    }
                
                    @Operation(
                            summary = "Check if %s exists",
                            description = "Check if a %s entity exists by ID"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "200",
                                    description = "%s exists"
                            ),
                            @ApiResponse(
                                    responseCode = "404",
                                    description = "%s not found"
                            )
                    })
                    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
                    public ResponseEntity<Void> exists(
                            @Parameter(description = "ID of the %s to check", required = true)
                            @PathVariable %s id
                    ) {
                        log.debug("HEAD /api/%s/{} - Checking existence", id);
                        boolean exists = service.existsById(id);
                        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
                    }
                
                    @Operation(
                            summary = "Count all %s",
                            description = "Get the total count of %s entities"
                    )
                    @ApiResponses(value = {
                            @ApiResponse(
                                    responseCode = "200",
                                    description = "Successfully retrieved count"
                            )
                    })
                    @GetMapping("/count")
                    public ResponseEntity<Long> count() {
                        log.debug("GET /api/%s/count - Counting entities");
                        long count = service.count();
                        return ResponseEntity.ok(count);
                    }
                }
                """,
                // Format arguments (total: 88)
                controllerPkg,                          // 1
                entityPackage, entity,                  // 2, 3
                basePkg, entity,                        // 4, 5
                basePkg,                                // 6
                basePkg,                                // 7 - PageResponse import
                basePkg, entity,                        // 8, 9
                entity,                                 // 10
                lower,                                  // 11
                entity, entity,                         // 12, 13
                entity,                                 // 14
                entity,                                 // 15
                entity,                                 // 16
                entity, entity,                         // 17, 18
                entity, entity,                         // 19, 20
                entity,                                 // 21
                entity,                                 // 22
                lower,                                  // 23
                entity,                                 // 24
                entity, entity,                         // 25, 26
                entity,                                 // 27 - paginated return type
                lower,                                  // 28
                entity,                                 // 29 - Page<Entity>
                entity,                                 // 30 - PageResponse<Entity>
                entity, entity,                         // 31, 32
                entity,                                 // 33
                entity,                                 // 34
                entity,                                 // 35
                entity,                                 // 36
                idType,                                 // 37
                lower,                                  // 38
                entity,                                 // 39
                entity, entity,                         // 40, 41
                entity,                                 // 42
                entity,                                 // 43
                entity,                                 // 44
                entity,                                 // 45
                entity,                                 // 46
                entity,                                 // 47
                lower,                                  // 48
                entity,                                 // 49
                entity, entity,                         // 50, 51
                entity,                                 // 52
                entity,                                 // 53
                entity,                                 // 54
                entity,                                 // 55
                entity,                                 // 56
                idType,                                 // 57
                entity,                                 // 58
                entity,                                 // 59
                lower,                                  // 60
                entity,                                 // 61
                entity, entity,                         // 62, 63
                entity,                                 // 64
                entity,                                 // 65
                entity,                                 // 66
                idType,                                 // 67
                lower,                                  // 68
                entity, entity,                         // 69, 70
                entity,                                 // 71
                entity,                                 // 72
                entity,                                 // 73
                idType,                                 // 74
                lower,                                  // 75
                entity, entity,                         // 76, 77
                lower                                   // 78
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        entity + "Controller.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}