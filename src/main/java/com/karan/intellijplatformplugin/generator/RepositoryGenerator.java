package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

public class RepositoryGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {

        String pkg = meta.basePackage() + ".repository";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = """
                package %s;

                import %s.%s;
                import org.springframework.data.jpa.repository.JpaRepository;

                public interface %sRepository
                        extends JpaRepository<%s, %s> {
                }
                """.formatted(
                pkg,
                meta.getPackageName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getIdType()
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        meta.getClassName() + "Repository.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}
