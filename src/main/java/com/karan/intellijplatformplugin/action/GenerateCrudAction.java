package com.karan.intellijplatformplugin.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.generator.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Action to generate complete CRUD code with Swagger documentation.
 */
public class GenerateCrudAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null) {
            Messages.showErrorDialog("No project found", "Error");
            return;
        }

        if (!(file instanceof PsiJavaFile)) {
            Messages.showErrorDialog(
                    "Please select a Java class file",
                    "Invalid Selection"
            );
            return;
        }

        PsiJavaFile javaFile = (PsiJavaFile) file;
        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            Messages.showErrorDialog(
                    "No class found in the selected file",
                    "Invalid Selection"
            );
            return;
        }

        PsiClass psiClass = classes[0];

        if (!isEntity(psiClass)) {
            int result = Messages.showYesNoDialog(
                    project,
                    "The selected class does not have @Entity annotation. Continue anyway?",
                    "Not an Entity",
                    Messages.getQuestionIcon()
            );
            if (result != Messages.YES) {
                return;
            }
        }

        try {
            ClassMeta meta = PsiDirectoryUtil.toClassMeta(psiClass);

            PsiDirectory sourceRoot = PsiDirectoryUtil.getSourceRoot(file);
            if (sourceRoot == null) {
                Messages.showErrorDialog(
                        "Cannot locate source root (src/main/java)",
                        "Error"
                );
                return;
            }

            WriteCommandAction.runWriteCommandAction(project, () -> {
                // Generate Swagger/OpenAPI documentation
                SwaggerConfigGenerator.generate(project, sourceRoot, meta);
                SwaggerReadmeGenerator.generate(project, sourceRoot, meta);
                ApplicationPropertiesGenerator.generate(project, sourceRoot, meta);

                // Generate exception handling
                ExceptionGenerator.generate(project, sourceRoot, meta);
                ErrorResponseGenerator.generate(project, sourceRoot, meta);
                GlobalExceptionHandlerGenerator.generate(project, sourceRoot, meta);

                // Generate CRUD components
                DtoGenerator.generate(project, sourceRoot, meta);
                MapperGenerator.generate(project, sourceRoot, meta);
                RepositoryGenerator.generate(project, sourceRoot, meta);
                ServiceGenerator.generate(project, sourceRoot, meta);
                ControllerGenerator.generate(project, sourceRoot, meta);
            });

            String message = String.format("""
                    Successfully generated CRUD code for %s:
                    
                    ✓ Swagger Configuration
                    ✓ OpenAPI Documentation
                    ✓ Custom Exceptions
                    ✓ Error Response DTO
                    ✓ Global Exception Handler
                    ✓ DTO with Schema Annotations
                    ✓ Mapper
                    ✓ Repository
                    ✓ Service
                    ✓ Controller with API Docs
                    ✓ Swagger Setup README
                    
                    Access Swagger UI at: http://localhost:8080/swagger-ui.html
                    """, meta.getClassName());

            Messages.showInfoMessage(project, message, "Spring Boot CRUD Generator");

        } catch (Exception ex) {
            Messages.showErrorDialog(
                    project,
                    "Failed to generate CRUD code: " + ex.getMessage(),
                    "Generation Error"
            );
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        Project project = e.getProject();

        PsiJavaFile javaFile = null;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile instanceof PsiJavaFile) {
            javaFile = (PsiJavaFile) psiFile;
        }

        if (javaFile == null) {
            PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
            if (element instanceof PsiJavaFile) {
                javaFile = (PsiJavaFile) element;
            } else if (element instanceof PsiClass) {
                PsiFile containingFile = element.getContainingFile();
                if (containingFile instanceof PsiJavaFile) {
                    javaFile = (PsiJavaFile) containingFile;
                }
            }
        }

        if (javaFile == null && project != null) {
            VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (vf != null) {
                PsiFile fileFromVf = PsiManager.getInstance(project).findFile(vf);
                if (fileFromVf instanceof PsiJavaFile) {
                    javaFile = (PsiJavaFile) fileFromVf;
                }
            }
        }

        boolean enabled = javaFile != null && javaFile.getClasses().length > 0;
        presentation.setEnabledAndVisible(enabled);
    }

    private boolean isEntity(PsiClass psiClass) {
        return psiClass.hasAnnotation("jakarta.persistence.Entity") ||
                psiClass.hasAnnotation("javax.persistence.Entity");
    }
}