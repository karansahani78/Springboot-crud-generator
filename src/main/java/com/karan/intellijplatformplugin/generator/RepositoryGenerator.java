package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Spring Data JPA Repository interfaces.
 */
public class RepositoryGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".repository";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.%s;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;
                
                /**
                 * Repository interface for %s entity.
                 */
                @Repository
                public interface %sRepository extends JpaRepository<%s, %s> {
                }
                """,
                pkg,
                meta.getPackageName(), meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(), meta.getIdType()
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