package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.*;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates DTO (Data Transfer Object) classes with proper getters, setters, and toString.
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
        StringBuilder toStringBuilder = new StringBuilder();

        int fieldCount = 0;
        // Generate fields and getters/setters for non-ID fields
        for (FieldMeta f : meta.getFields()) {
            if (f.getName().equalsIgnoreCase("id")) {
                continue; // Skip ID field in DTO
            }

            String fieldName = f.getName();
            String fieldType = f.getType();
            String capitalizedName = f.getCapitalizedName();

            // Add field with validation annotation
            fields.append(String.format("""
                    @jakarta.validation.constraints.NotNull
                    private %s %s;
                    
                    """, fieldType, fieldName));

            // Add getter
            gettersSetters.append(String.format("""
                    public %s get%s() {
                        return %s;
                    }
                    
                    """, fieldType, capitalizedName, fieldName));

            // Add setter
            gettersSetters.append(String.format("""
                    public void set%s(%s %s) {
                        this.%s = %s;
                    }
                    
                    """, capitalizedName, fieldType, fieldName, fieldName, fieldName));

            // Build toString
            if (fieldCount > 0) {
                toStringBuilder.append(", ");
            }
            toStringBuilder.append(String.format("%s='\" + %s + \"'", fieldName, fieldName));
            fieldCount++;
        }

        // Generate toString method
        String toStringMethod;
        if (fieldCount > 0) {
            toStringMethod = String.format("""
                    
                    @Override
                    public String toString() {
                        return "%sDto{" +
                                "%s" +
                                "}";
                    }
                    """, meta.getClassName(), toStringBuilder.toString());
        } else {
            toStringMethod = String.format("""
                    
                    @Override
                    public String toString() {
                        return "%sDto{}";
                    }
                    """, meta.getClassName());
        }

        String code = String.format("""
                package %s;
                
                import jakarta.validation.constraints.NotNull;
                
                /**
                 * DTO for %s entity.
                 */
                public class %sDto {
                    
                %s%s%s}
                """,
                pkg,
                meta.getClassName(),
                meta.getClassName(),
                fields.toString(),
                gettersSetters.toString(),
                toStringMethod);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        meta.getClassName() + "Dto.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}