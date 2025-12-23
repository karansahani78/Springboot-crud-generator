package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;

import java.io.IOException;

/**
 * Updates application.properties with Springdoc OpenAPI configuration.
 */
public class ApplicationPropertiesGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            return;
        }

        try {
            PsiDirectory resourcesDir = findResourcesDirectory(root);
            if (resourcesDir == null) {
                return;
            }

            String openApiProperties = """
                    
                    # ========================================
                    # Springdoc OpenAPI Configuration
                    # ========================================
                    # Access Swagger UI at: http://localhost:${server.port}/swagger-ui.html
                    # Access OpenAPI JSON at: http://localhost:${server.port}/v3/api-docs
                    
                    springdoc.api-docs.path=/v3/api-docs
                    springdoc.swagger-ui.path=/swagger-ui.html
                    springdoc.swagger-ui.enabled=true
                    springdoc.swagger-ui.operations-sorter=method
                    springdoc.swagger-ui.tags-sorter=alpha
                    springdoc.swagger-ui.doc-expansion=none
                    """;

            PsiFile existingFile = resourcesDir.findFile("application.properties");

            if (existingFile != null) {
                VirtualFile virtualFile = existingFile.getVirtualFile();
                if (virtualFile != null && virtualFile.isWritable()) {
                    String currentContent = new String(virtualFile.contentsToByteArray());

                    if (!currentContent.contains("Springdoc OpenAPI Configuration")) {
                        String newContent = currentContent + openApiProperties;
                        virtualFile.setBinaryContent(newContent.getBytes());
                    }
                }
            } else {
                PsiFile file = PsiFileFactory.getInstance(project)
                        .createFileFromText("application.properties", openApiProperties);
                resourcesDir.add(file);
            }
        } catch (IOException e) {
            // Silently fail - not critical
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