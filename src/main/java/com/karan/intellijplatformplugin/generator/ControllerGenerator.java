package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

public class ControllerGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {

        String basePkg = meta.basePackage();
        String controllerPkg = basePkg + ".controller";

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, controllerPkg);

        String entity = meta.getClassName();
        String idType = meta.getIdType();
        String lower = entity.toLowerCase();

        String code = String.format("""
                package %s;

                import %s.entity.%s;
                import %s.dto.%sDto;
                import %s.service.%sService;
                import jakarta.validation.Valid;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;

                import java.util.List;

                @RestController
                @RequestMapping("/api/%s")
                public class %sController {

                    private final %sService service;

                    public %sController(%sService service) {
                        this.service = service;
                    }

                    @GetMapping
                    public ResponseEntity<List<%s>> getAll() {
                        return ResponseEntity.ok(service.findAll());
                    }

                    @PostMapping
                    public ResponseEntity<%s> create(@Valid @RequestBody %sDto dto) {
                        return ResponseEntity.ok(service.create(dto));
                    }

                    @PutMapping("/{id}")
                    public ResponseEntity<%s> update(
                            @PathVariable %s id,
                            @Valid @RequestBody %sDto dto
                    ) {
                        return ResponseEntity.ok(service.update(id, dto));
                    }

                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(@PathVariable %s id) {
                        service.delete(id);
                        return ResponseEntity.noContent().build();
                    }

                    @GetMapping("/{id}")
                    public ResponseEntity<%s> getById(@PathVariable %s id) {
                        return ResponseEntity.ok(service.findById(id));
                    }
                }
                """,
                controllerPkg,          // 1
                basePkg, entity,        // 2, 3 - entity import
                basePkg, entity,        // 4, 5 - dto import
                basePkg, entity,        // 6, 7 - service import
                lower,                  // 8 - request mapping
                entity,                 // 9 - controller class name
                entity,                 // 10 - service field type
                entity, entity,         // 11, 12 - constructor
                entity,                 // 13 - getAll return type
                entity, entity,         // 14, 15 - create method
                entity, idType, entity, // 16, 17, 18 - update method
                idType,                 // 19 - delete method
                entity, idType          // 20, 21 - getById method
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