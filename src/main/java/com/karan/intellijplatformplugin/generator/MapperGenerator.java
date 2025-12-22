package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.FieldMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Mapper classes for entity-DTO conversion.
 */
public class MapperGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".mapper";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        StringBuilder toEntityMapping = new StringBuilder();
        StringBuilder toDtoMapping = new StringBuilder();

        for (FieldMeta f : meta.getFields()) {
            if (!f.getName().equalsIgnoreCase("id")) {
                String capitalizedName = f.getCapitalizedName();

                // For toEntity mapping
                toEntityMapping.append(String.format("""
                                entity.set%s(dto.get%s());
                        """, capitalizedName, capitalizedName));

                // For toDto mapping
                toDtoMapping.append(String.format("""
                                dto.set%s(entity.get%s());
                        """, capitalizedName, capitalizedName));
            }
        }

        String code = String.format("""
                package %s;
                
                import %s.%s;
                import %s.dto.%sDto;
                
                /**
                 * Mapper for converting between %s entity and %sDto.
                 */
                public class %sMapper {
                
                    /**
                     * Converts DTO to entity.
                     */
                    public static %s toEntity(%sDto dto) {
                        if (dto == null) {
                            return null;
                        }
                        
                        %s entity = new %s();
                        %s
                        return entity;
                    }
                
                    /**
                     * Converts entity to DTO.
                     */
                    public static %sDto toDto(%s entity) {
                        if (entity == null) {
                            return null;
                        }
                        
                        %sDto dto = new %sDto();
                        %s
                        return dto;
                    }
                
                    /**
                     * Updates entity from DTO.
                     */
                    public static void updateEntity(%s entity, %sDto dto) {
                        if (entity == null || dto == null) {
                            return;
                        }
                        %s
                    }
                }
                """,
                pkg,
                meta.getPackageName(), meta.getClassName(),
                meta.basePackage(), meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(), meta.getClassName(), toEntityMapping.toString(),
                meta.getClassName(), meta.getClassName(),
                meta.getClassName(), meta.getClassName(), toDtoMapping.toString(),
                meta.getClassName(), meta.getClassName(),
                toEntityMapping.toString()
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