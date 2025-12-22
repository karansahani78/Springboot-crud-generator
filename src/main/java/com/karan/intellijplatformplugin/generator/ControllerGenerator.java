package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates REST Controller classes with full CRUD endpoints.
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
        String idType = meta.getIdType();
        String lower = entity.toLowerCase();

        String code = String.format("""
                package %s;
                
                import %s.%s;
                import %s.dto.%sDto;
                import %s.service.%sService;
                import jakarta.validation.Valid;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                import java.util.List;
                
                /**
                 * REST Controller for %s entity operations.
                 */
                @RestController
                @RequestMapping("/api/%s")
                public class %sController {
                
                    private final %sService service;
                
                    public %sController(%sService service) {
                        this.service = service;
                    }
                
                    /**
                     * GET /api/%s - Retrieves all entities.
                     */
                    @GetMapping
                    public ResponseEntity<List<%s>> getAll() {
                        List<%s> entities = service.findAll();
                        return ResponseEntity.ok(entities);
                    }
                
                    /**
                     * GET /api/%s/{id} - Retrieves a single entity by ID.
                     */
                    @GetMapping("/{id}")
                    public ResponseEntity<%s> getById(@PathVariable %s id) {
                        %s entity = service.findById(id);
                        return ResponseEntity.ok(entity);
                    }
                
                    /**
                     * POST /api/%s - Creates a new entity.
                     */
                    @PostMapping
                    public ResponseEntity<%s> create(@Valid @RequestBody %sDto dto) {
                        %s created = service.create(dto);
                        return ResponseEntity.status(HttpStatus.CREATED).body(created);
                    }
                
                    /**
                     * PUT /api/%s/{id} - Updates an existing entity.
                     */
                    @PutMapping("/{id}")
                    public ResponseEntity<%s> update(
                            @PathVariable %s id,
                            @Valid @RequestBody %sDto dto
                    ) {
                        %s updated = service.update(id, dto);
                        return ResponseEntity.ok(updated);
                    }
                
                    /**
                     * DELETE /api/%s/{id} - Deletes an entity by ID.
                     */
                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(@PathVariable %s id) {
                        service.delete(id);
                        return ResponseEntity.noContent().build();
                    }
                
                    /**
                     * HEAD /api/%s/{id} - Checks if an entity exists.
                     */
                    @RequestMapping(value = "/{id}", method = RequestMethod.HEAD)
                    public ResponseEntity<Void> exists(@PathVariable %s id) {
                        boolean exists = service.existsById(id);
                        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
                    }
                
                    /**
                     * GET /api/%s/count - Returns the total count of entities.
                     */
                    @GetMapping("/count")
                    public ResponseEntity<Long> count() {
                        long count = service.count();
                        return ResponseEntity.ok(count);
                    }
                }
                """,
                controllerPkg,
                meta.getPackageName(), entity,
                basePkg, entity,
                basePkg, entity,
                entity,
                lower,
                entity,
                entity,
                entity, entity,
                lower,
                entity, entity,
                lower,
                entity, idType, entity,
                lower,
                entity, entity, entity,
                lower,
                entity, idType, entity, entity,
                lower,
                idType,
                lower,
                idType,
                lower
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