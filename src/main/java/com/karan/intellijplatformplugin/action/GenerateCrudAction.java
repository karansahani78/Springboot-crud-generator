package com.karan.intellijplatformplugin.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.generator.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Action to generate complete CRUD code for a selected entity class.
 */
public class GenerateCrudAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        // Validate project and file
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

        // Validate that it's an entity
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
            // Extract metadata
            ClassMeta meta = PsiDirectoryUtil.toClassMeta(psiClass);

            // Find source root
            PsiDirectory sourceRoot = PsiDirectoryUtil.getSourceRoot(file);
            if (sourceRoot == null) {
                Messages.showErrorDialog(
                        "Cannot locate source root (src/main/java)",
                        "Error"
                );
                return;
            }

            // Generate all CRUD components
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    DtoGenerator.generate(project, sourceRoot, meta);
                    MapperGenerator.generate(project, sourceRoot, meta);
                    RepositoryGenerator.generate(project, sourceRoot, meta);
                    ServiceGenerator.generate(project, sourceRoot, meta);
                    ControllerGenerator.generate(project, sourceRoot, meta);
                } catch (Exception ex) {
                    throw new RuntimeException("Error generating CRUD code: " + ex.getMessage(), ex);
                }
            });

            // Show success message
            Messages.showInfoMessage(
                    project,
                    "Successfully generated CRUD code for " + meta.getClassName() + ":\n\n" +
                            "✓ DTO\n" +
                            "✓ Mapper\n" +
                            "✓ Repository\n" +
                            "✓ Service\n" +
                            "✓ Controller",
                    "Spring Boot CRUD Generator"
            );

        } catch (IllegalArgumentException ex) {
            Messages.showErrorDialog(
                    project,
                    "Invalid class structure: " + ex.getMessage(),
                    "Generation Error"
            );
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
        // Enable action only when a Java file is selected
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
        boolean enabled = file instanceof PsiJavaFile;
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    /**
     * Checks if the class has @Entity annotation.
     */
    private boolean isEntity(PsiClass psiClass) {
        return psiClass.hasAnnotation("jakarta.persistence.Entity") ||
                psiClass.hasAnnotation("javax.persistence.Entity");
    }
}