package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates JPA Auditing configuration.
 */
public class JpaAuditingConfigGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".config";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.data.domain.AuditorAware;
                import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
                
                import java.util.Optional;
                
                /**
                 * Configuration for JPA Auditing.
                 * Enables automatic population of @CreatedDate, @LastModifiedDate, @CreatedBy, @LastModifiedBy.
                 */
                @Configuration
                @EnableJpaAuditing(auditorAwareRef = "auditorProvider")
                public class JpaAuditingConfig {
                    
                    /**
                     * Provides the current auditor (user who is creating/modifying the entity).
                     * 
                     * Default implementation returns "system".
                     * 
                     * To integrate with Spring Security, modify this to return the authenticated user:
                     * 
                     * <pre>
                     * {@code
                     * @Bean
                     * public AuditorAware<String> auditorProvider() {
                     *     return () -> {
                     *         Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                     *         if (authentication == null || !authentication.isAuthenticated()) {
                     *             return Optional.of("system");
                     *         }
                     *         return Optional.of(authentication.getName());
                     *     };
                     * }
                     * }
                     * </pre>
                     * 
                     * @return AuditorAware bean
                     */
                    @Bean
                    public AuditorAware<String> auditorProvider() {
                        // Default implementation - returns "system"
                        // TODO: Integrate with Spring Security to get actual user
                        return () -> Optional.of("system");
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "JpaAuditingConfig.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}