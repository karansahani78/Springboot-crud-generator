package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.*;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates DTO (Data Transfer Object) classes.
 */
public class DtoGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".dto";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        StringBuilder fields = new StringBuilder();
        StringBuilder gettersSetters = new StringBuilder();

        for (FieldMeta f : meta.getFields()) {
            if (!f.getName().equalsIgnoreCase("id")) {
                // Add field with validation annotation
                fields.append(String.format("""
                        @jakarta.validation.constraints.NotNull
                        private %s %s;
                    
                    """, f.getType(), f.getName()));

                // Add getter
                gettersSetters.append(String.format("""
                        public %s get%s() {
                            return %s;
                        }
                    
                    """, f.getType(), f.getCapitalizedName(), f.getName()));

                // Add setter
                gettersSetters.append(String.format("""
                        public void set%s(%s %s) {
                            this.%s = %s;
                        }
                    
                    """, f.getCapitalizedName(), f.getType(), f.getName(), f.getName(), f.getName()));
            }
        }

        String code = String.format("""
                package %s;
                
                import jakarta.validation.constraints.NotNull;
                
                /**
                 * DTO for %s entity.
                 */
                public class %sDto {
                    
                    %s
                    %s
                }
                """, pkg, meta.getClassName(), meta.getClassName(), fields.toString(), gettersSetters.toString());

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        meta.getClassName() + "Dto.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}