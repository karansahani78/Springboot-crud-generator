package com.karan.intellijplatformplugin.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.generator.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

public class GenerateCrudAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {

        Project project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null || !(file instanceof PsiJavaFile)) {
            Messages.showErrorDialog("Select a Java class", "Invalid Selection");
            return;
        }

        PsiClass psiClass = ((PsiJavaFile) file).getClasses()[0];
        ClassMeta meta = PsiDirectoryUtil.toClassMeta(psiClass);

        PsiDirectory sourceRoot = PsiDirectoryUtil.getSourceRoot(file);
        if (sourceRoot == null) {
            Messages.showErrorDialog("Cannot locate src/main/java", "Error");
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            DtoGenerator.generate(project, sourceRoot, meta);
            MapperGenerator.generate(project, sourceRoot, meta);
            RepositoryGenerator.generate(project, sourceRoot, meta);
            ServiceGenerator.generate(project, sourceRoot, meta);
            ControllerGenerator.generate(project, sourceRoot, meta);
        });

        Messages.showInfoMessage(
                project,
                "Production CRUD generated for " + meta.getClassName(),
                "Spring Boot CRUD Generator"
        );
    }
}
