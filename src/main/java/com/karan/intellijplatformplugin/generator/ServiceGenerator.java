package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Service layer classes with CRUD operations, pagination, and sorting.
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
                import %s.exception.ResourceNotFoundException;
                import %s.exception.BadRequestException;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.PageRequest;
                import org.springframework.data.domain.Pageable;
                import org.springframework.data.domain.Sort;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                
                import java.util.List;
                
                /**
                 * Service class for %s entity operations with pagination and sorting support.
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
                     * Retrieves paginated and sorted entities.
                     * 
                     * @param page Page number (0-indexed)
                     * @param size Number of items per page
                     * @param sortBy Field name to sort by
                     * @param sortDirection Sort direction (ASC or DESC)
                     * @return Paginated result
                     */
                    public Page<%s> findAllPaginated(int page, int size, String sortBy, String sortDirection) {
                        log.debug("Finding paginated %s - page: {}, size: {}, sortBy: {}, direction: {}", 
                                  page, size, sortBy, sortDirection);
                        
                        if (page < 0) {
                            throw new BadRequestException("Page number cannot be negative");
                        }
                        
                        if (size <= 0) {
                            throw new BadRequestException("Page size must be greater than 0");
                        }
                        
                        if (size > 100) {
                            log.warn("Page size {} is too large, limiting to 100", size);
                            size = 100;
                        }
                        
                        Sort.Direction direction = sortDirection.equalsIgnoreCase("DESC") 
                                ? Sort.Direction.DESC 
                                : Sort.Direction.ASC;
                        
                        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
                        
                        Page<%s> result = repository.findAll(pageable);
                        log.debug("Found {} entities in page {} of {}", 
                                  result.getNumberOfElements(), page, result.getTotalPages());
                        
                        return result;
                    }
                
                    /**
                     * Retrieves an entity by ID.
                     * 
                     * @param id Entity ID
                     * @return Entity
                     * @throws ResourceNotFoundException if entity not found
                     */
                    public %s findById(%s id) {
                        log.debug("Finding %s by id: {}", id);
                        
                        if (id == null) {
                            throw new BadRequestException("ID cannot be null");
                        }
                        
                        return repository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "%s", "id", id
                                ));
                    }
                
                    /**
                     * Creates a new entity from DTO.
                     * 
                     * @param dto Entity DTO
                     * @return Created entity
                     * @throws BadRequestException if DTO is invalid
                     */
                    @Transactional
                    public %s create(%sDto dto) {
                        log.info("Creating new %s from DTO: {}", dto);
                        
                        if (dto == null) {
                            throw new BadRequestException("DTO cannot be null");
                        }
                        
                        %s entity = %sMapper.toEntity(dto);
                        log.info("Mapped entity before save: {}", entity);
                        
                        %s saved = repository.save(entity);
                        log.info("Saved entity: {}", saved);
                        
                        return saved;
                    }
                
                    /**
                     * Updates an existing entity from DTO.
                     * 
                     * @param id Entity ID
                     * @param dto Entity DTO
                     * @return Updated entity
                     * @throws ResourceNotFoundException if entity not found
                     * @throws BadRequestException if DTO is invalid
                     */
                    @Transactional
                    public %s update(%s id, %sDto dto) {
                        log.info("Updating %s with id: {} from DTO: {}", id, dto);
                        
                        if (id == null) {
                            throw new BadRequestException("ID cannot be null");
                        }
                        
                        if (dto == null) {
                            throw new BadRequestException("DTO cannot be null");
                        }
                        
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
                     * 
                     * @param id Entity ID
                     * @throws ResourceNotFoundException if entity not found
                     * @throws BadRequestException if ID is invalid
                     */
                    @Transactional
                    public void delete(%s id) {
                        log.info("Deleting %s with id: {}", id);
                        
                        if (id == null) {
                            throw new BadRequestException("ID cannot be null");
                        }
                        
                        %s entity = findById(id);
                        repository.delete(entity);
                        
                        log.info("Deleted %s with id: {}", id);
                    }
                
                    /**
                     * Checks if an entity exists by ID.
                     * 
                     * @param id Entity ID
                     * @return true if exists, false otherwise
                     */
                    public boolean existsById(%s id) {
                        if (id == null) {
                            return false;
                        }
                        return repository.existsById(id);
                    }
                
                    /**
                     * Counts all entities.
                     * 
                     * @return Total count
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
                meta.basePackage(),
                meta.basePackage(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
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