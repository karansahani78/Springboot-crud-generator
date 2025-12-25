package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates UserDetailsService implementation.
 */
public class UserDetailsServiceImplGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".security";
        // CHECK IF FILE ALREADY EXISTS
        if (FileExistsUtil.fileExistsInPackage(root, pkg, "UserDetailsServiceImpl.java")) {
            System.out.println("UserDetailsServiceImpl.java already exists, skipping generation.");
            return;
        }
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.repository.AppUserRepository;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.core.userdetails.UsernameNotFoundException;
                import org.springframework.stereotype.Service;
                
                /**
                 * UserDetailsService implementation for loading user data.
                 */
                @Service
                public class UserDetailsServiceImpl implements UserDetailsService {
                    
                    private final AppUserRepository repository;
                    
                    public UserDetailsServiceImpl(AppUserRepository repository) {
                        this.repository = repository;
                    }
                    
                    @Override
                    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                        return repository.findByUsername(username)
                                .orElseThrow(() -> new UsernameNotFoundException(
                                        "User not found with username: " + username
                                ));
                    }
                }
                """, pkg, meta.basePackage());

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "UserDetailsServiceImpl.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}