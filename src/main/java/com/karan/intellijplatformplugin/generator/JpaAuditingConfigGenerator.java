package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates JPA Auditing configuration.
 *
 * <p>Multi-entity change: accepts {@code List<ClassMeta> allEntities} — standard
 * contract. The generated {@code JpaAuditingConfig} is a singleton bean that
 * does not vary per entity, so the list is intentionally unused in the output.
 */
public class JpaAuditingConfigGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities,  // ← standard contract; unused in output
            boolean withSecurity
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".config";
        String fileName = "JpaAuditingConfig.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists — skipping generation.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code;

        if (withSecurity) {
            code = String.format("""
                    package %s;
                    
                    import org.springframework.context.annotation.Bean;
                    import org.springframework.context.annotation.Configuration;
                    import org.springframework.data.domain.AuditorAware;
                    import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
                    import org.springframework.security.core.Authentication;
                    import org.springframework.security.core.context.SecurityContextHolder;
                    
                    import java.util.Optional;
                    
                    /**
                     * JPA Auditing configuration integrated with Spring Security.
                     * Automatically tracks createdBy / updatedBy using the authenticated username.
                     */
                    @Configuration
                    @EnableJpaAuditing(auditorAwareRef = "auditorProvider")
                    public class JpaAuditingConfig {
                        
                        /**
                         * Provides the current auditor from the Spring Security context.
                         * Returns the authenticated username, or "anonymous" if no principal is present.
                         */
                        @Bean
                        public AuditorAware<String> auditorProvider() {
                            return () -> {
                                Authentication authentication = SecurityContextHolder
                                        .getContext()
                                        .getAuthentication();
                                
                                if (authentication == null || !authentication.isAuthenticated()) {
                                    return Optional.of("anonymous");
                                }
                                
                                return Optional.of(authentication.getName());
                            };
                        }
                    }
                    """, pkg);
        } else {
            code = String.format("""
                    package %s;
                    
                    import org.springframework.context.annotation.Bean;
                    import org.springframework.context.annotation.Configuration;
                    import org.springframework.data.domain.AuditorAware;
                    import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
                    
                    import java.util.Optional;
                    
                    /**
                     * JPA Auditing configuration.
                     * Enables automatic population of @CreatedDate, @LastModifiedDate,
                     * @CreatedBy, and @LastModifiedBy on entities extending BaseAuditEntity.
                     *
                     * <p>To integrate with Spring Security, replace the auditorProvider() body with:
                     * <pre>
                     * return () -> {
                     *     Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                     *     if (auth == null || !auth.isAuthenticated()) return Optional.of("system");
                     *     return Optional.of(auth.getName());
                     * };
                     * </pre>
                     */
                    @Configuration
                    @EnableJpaAuditing(auditorAwareRef = "auditorProvider")
                    public class JpaAuditingConfig {
                        
                        @Bean
                        public AuditorAware<String> auditorProvider() {
                            // Default: "system" — integrate with Spring Security for real usernames
                            return () -> Optional.of("system");
                        }
                    }
                    """, pkg);
        }

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }
}