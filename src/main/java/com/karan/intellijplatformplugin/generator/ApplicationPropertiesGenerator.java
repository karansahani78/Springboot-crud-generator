package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;

import java.io.IOException;

/**
 * Updates application.properties with configurations.
 */
public class ApplicationPropertiesGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta, boolean includeSecurity) {
        if (project == null || root == null || meta == null) {
            return;
        }

        try {
            PsiDirectory resourcesDir = findResourcesDirectory(root);
            if (resourcesDir == null) {
                return;
            }

            String configurations = """
                    
                    # ========================================
                    # Springdoc OpenAPI Configuration
                    # ========================================
                    springdoc.api-docs.path=/v3/api-docs
                    springdoc.swagger-ui.path=/swagger-ui.html
                    springdoc.swagger-ui.enabled=true
                    springdoc.swagger-ui.operations-sorter=method
                    springdoc.swagger-ui.tags-sorter=alpha
                    """;

            // Add JWT configuration only if security is enabled
            if (includeSecurity) {
                configurations += """
                        
                        # ========================================
                        # JWT Configuration
                        # ========================================
                        jwt.secret-key=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
                        jwt.expiration=86400000
                        # Note: Change secret key in production! Generate with: openssl rand -base64 32
                        # Expiration time in milliseconds (86400000 = 24 hours)
                        """;
            }

            PsiFile existingFile = resourcesDir.findFile("application.properties");

            if (existingFile != null) {
                VirtualFile virtualFile = existingFile.getVirtualFile();
                if (virtualFile != null && virtualFile.isWritable()) {
                    String currentContent = new String(virtualFile.contentsToByteArray());

                    // Only add if not already present
                    if (!currentContent.contains("Springdoc OpenAPI Configuration")) {
                        String newContent = currentContent + configurations;
                        virtualFile.setBinaryContent(newContent.getBytes());
                    }
                }
            } else {
                PsiFile file = PsiFileFactory.getInstance(project)
                        .createFileFromText("application.properties", configurations);
                resourcesDir.add(file);
            }
        } catch (IOException e) {
            // Silently fail
        }
    }

    private static PsiDirectory findResourcesDirectory(PsiDirectory sourceRoot) {
        PsiDirectory main = sourceRoot.getParentDirectory();
        if (main == null) return null;

        PsiDirectory resources = main.findSubdirectory("resources");
        if (resources == null) {
            try {
                resources = main.createSubdirectory("resources");
            } catch (Exception e) {
                return null;
            }
        }

        return resources;
    }
}