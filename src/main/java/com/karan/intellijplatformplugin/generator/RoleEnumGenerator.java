package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Role enum for user roles.
 */
public class RoleEnumGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".entity";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                /**
                 * User roles for authorization.
                 */
                public enum Role {
                    /**
                     * Regular user with basic permissions
                     */
                    USER,
                    
                    /**
                     * Administrator with full permissions
                     */
                    ADMIN,
                    
                    /**
                     * Moderator with limited administrative permissions
                     */
                    MODERATOR
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "Role.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}