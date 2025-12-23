package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates custom exception classes for better error handling.
 */
public class ExceptionGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".exception";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        // Generate ResourceNotFoundException
        generateResourceNotFoundException(project, dir, meta);

        // Generate BadRequestException
        generateBadRequestException(project, dir);

        // Generate DuplicateResourceException
        generateDuplicateResourceException(project, dir);
    }

    private static void generateResourceNotFoundException(Project project, PsiDirectory dir, ClassMeta meta) {
        String code = String.format("""
                package %s.exception;
                
                /**
                 * Exception thrown when a requested resource is not found.
                 */
                public class ResourceNotFoundException extends RuntimeException {
                    
                    private final String resourceName;
                    private final String fieldName;
                    private final Object fieldValue;
                    
                    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
                        super(String.format("%%s not found with %%s: '%%s'", resourceName, fieldName, fieldValue));
                        this.resourceName = resourceName;
                        this.fieldName = fieldName;
                        this.fieldValue = fieldValue;
                    }
                    
                    public ResourceNotFoundException(String message) {
                        super(message);
                        this.resourceName = null;
                        this.fieldName = null;
                        this.fieldValue = null;
                    }
                    
                    public String getResourceName() {
                        return resourceName;
                    }
                    
                    public String getFieldName() {
                        return fieldName;
                    }
                    
                    public Object getFieldValue() {
                        return fieldValue;
                    }
                }
                """, meta.basePackage());

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "ResourceNotFoundException.java",
                        JavaFileType.INSTANCE,
                        code
                );
        dir.add(file);
    }

    private static void generateBadRequestException(Project project, PsiDirectory dir) {
        String pkg = dir.getVirtualFile().getPath()
                .substring(dir.getVirtualFile().getPath().lastIndexOf("java/") + 5)
                .replace("/", ".");

        String code = String.format("""
                package %s;
                
                /**
                 * Exception thrown when a request contains invalid data.
                 */
                public class BadRequestException extends RuntimeException {
                    
                    public BadRequestException(String message) {
                        super(message);
                    }
                    
                    public BadRequestException(String message, Throwable cause) {
                        super(message, cause);
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "BadRequestException.java",
                        JavaFileType.INSTANCE,
                        code
                );
        dir.add(file);
    }

    private static void generateDuplicateResourceException(Project project, PsiDirectory dir) {
        String pkg = dir.getVirtualFile().getPath()
                .substring(dir.getVirtualFile().getPath().lastIndexOf("java/") + 5)
                .replace("/", ".");

        String code = String.format("""
                package %s;
                
                /**
                 * Exception thrown when attempting to create a resource that already exists.
                 */
                public class DuplicateResourceException extends RuntimeException {
                    
                    private final String resourceName;
                    private final String fieldName;
                    private final Object fieldValue;
                    
                    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
                        super(String.format("%%s already exists with %%s: '%%s'", resourceName, fieldName, fieldValue));
                        this.resourceName = resourceName;
                        this.fieldName = fieldName;
                        this.fieldValue = fieldValue;
                    }
                    
                    public String getResourceName() {
                        return resourceName;
                    }
                    
                    public String getFieldName() {
                        return fieldName;
                    }
                    
                    public Object getFieldValue() {
                        return fieldValue;
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "DuplicateResourceException.java",
                        JavaFileType.INSTANCE,
                        code
                );
        dir.add(file);
    }
}