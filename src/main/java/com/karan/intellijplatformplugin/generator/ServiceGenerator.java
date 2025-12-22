package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Service layer classes with full CRUD operations and detailed logging.
 */
public class ServiceGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".service";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.%s;
                import %s.dto.%sDto;
                import %s.mapper.%sMapper;
                import %s.repository.%sRepository;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                
                import java.util.List;
                
                /**
                 * Service class for %s entity operations.
                 */
                @Service
                @Transactional(readOnly = true)
                public class %sService {
                
                    private static final Logger log = LoggerFactory.getLogger(%sService.class);
                    private final %sRepository repository;
                
                    public %sService(%sRepository repository) {
                        this.repository = repository;
                    }
                
                    /**
                     * Retrieves all entities.
                     */
                    public List<%s> findAll() {
                        log.debug("Finding all %s entities");
                        return repository.findAll();
                    }
                
                    /**
                     * Retrieves an entity by ID.
                     */
                    public %s findById(%s id) {
                        log.debug("Finding %s by id: {}", id);
                        return repository.findById(id)
                                .orElseThrow(() -> new RuntimeException("%s not found with id: " + id));
                    }
                
                    /**
                     * Creates a new entity from DTO.
                     */
                    @Transactional
                    public %s create(%sDto dto) {
                        log.info("Creating new %s from DTO: {}", dto);
                        %s entity = %sMapper.toEntity(dto);
                        log.info("Mapped entity before save: {}", entity);
                        %s saved = repository.save(entity);
                        log.info("Saved entity: {}", saved);
                        return saved;
                    }
                
                    /**
                     * Updates an existing entity from DTO.
                     */
                    @Transactional
                    public %s update(%s id, %sDto dto) {
                        log.info("Updating %s with id: {} from DTO: {}", id, dto);
                        %s entity = findById(id);
                        log.info("Entity before update: {}", entity);
                        %sMapper.updateEntity(entity, dto);
                        log.info("Entity after mapping: {}", entity);
                        %s updated = repository.save(entity);
                        log.info("Updated entity: {}", updated);
                        return updated;
                    }
                
                    /**
                     * Deletes an entity by ID.
                     */
                    @Transactional
                    public void delete(%s id) {
                        log.info("Deleting %s with id: {}", id);
                        %s entity = findById(id);
                        repository.delete(entity);
                        log.info("Deleted %s with id: {}", id);
                    }
                
                    /**
                     * Checks if an entity exists by ID.
                     */
                    public boolean existsById(%s id) {
                        return repository.existsById(id);
                    }
                
                    /**
                     * Counts all entities.
                     */
                    public long count() {
                        return repository.count();
                    }
                }
                """,
                pkg,
                meta.getPackageName(), meta.getClassName(),
                meta.basePackage(), meta.getClassName(),
                meta.basePackage(), meta.getClassName(),
                meta.basePackage(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getIdType(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getIdType(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getIdType(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getIdType()
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        meta.getClassName() + "Service.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}