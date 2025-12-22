package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Mapper classes using Spring BeanUtils for reliable property copying.
 */
public class MapperGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".mapper";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.%s;
                import %s.dto.%sDto;
                import org.springframework.beans.BeanUtils;
                
                /**
                 * Mapper for converting between %s entity and %sDto.
                 */
                public class %sMapper {
                
                    /**
                     * Converts DTO to entity.
                     * Copies all matching properties from DTO to entity.
                     */
                    public static %s toEntity(%sDto dto) {
                        if (dto == null) {
                            return null;
                        }
                        
                        %s entity = new %s();
                        BeanUtils.copyProperties(dto, entity);
                        return entity;
                    }
                
                    /**
                     * Converts entity to DTO.
                     * Copies all properties except 'id' from entity to DTO.
                     */
                    public static %sDto toDto(%s entity) {
                        if (entity == null) {
                            return null;
                        }
                        
                        %sDto dto = new %sDto();
                        BeanUtils.copyProperties(entity, dto, "id");
                        return dto;
                    }
                
                    /**
                     * Updates entity from DTO (preserves ID).
                     * Copies all properties except 'id' from DTO to entity.
                     */
                    public static void updateEntity(%s entity, %sDto dto) {
                        if (entity == null || dto == null) {
                            return;
                        }
                        BeanUtils.copyProperties(dto, entity, "id");
                    }
                }
                """,
                pkg,
                meta.getPackageName(), meta.getClassName(),
                meta.basePackage(), meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(), meta.getClassName()
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        meta.getClassName() + "Mapper.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}