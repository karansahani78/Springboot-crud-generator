package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates AppUser repository.
 */
public class AppUserRepositoryGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".repository";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.entity.AppUser;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;
                
                import java.util.Optional;
                
                /**
                 * Repository interface for AppUser entity.
                 */
                @Repository
                public interface AppUserRepository extends JpaRepository<AppUser, Long> {
                    
                    Optional<AppUser> findByUsername(String username);
                    
                    Optional<AppUser> findByEmail(String email);
                    
                    boolean existsByUsername(String username);
                    
                    boolean existsByEmail(String email);
                }
                """, pkg, meta.basePackage());

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "AppUserRepository.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}