package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Spring Security configuration with JWT support.
 */
public class SecurityConfigGenerator {

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
                import org.springframework.security.authentication.AuthenticationManager;
                import org.springframework.security.authentication.AuthenticationProvider;
                import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
                import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
                import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;
                import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
                import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
                import org.springframework.security.config.http.SessionCreationPolicy;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
                
                /**
                 * Spring Security configuration with JWT authentication.
                 * 
                 * This configuration:
                 * - Enables JWT-based authentication
                 * - Configures stateless session management
                 * - Sets up public and protected endpoints
                 * - Enables method-level security with @PreAuthorize
                 */
                @Configuration
                @EnableWebSecurity
                @EnableMethodSecurity
                public class SecurityConfig {
                    
                    private final JwtAuthenticationFilter jwtAuthFilter;
                    private final UserDetailsService userDetailsService;
                    
                    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, 
                                        UserDetailsService userDetailsService) {
                        this.jwtAuthFilter = jwtAuthFilter;
                        this.userDetailsService = userDetailsService;
                    }
                    
                    /**
                     * Configures HTTP security with JWT authentication.
                     * 
                     * Public endpoints (no authentication required):
                     * - /api/auth/** (login, register)
                     * - /swagger-ui/** (API documentation)
                     * - /v3/api-docs/** (OpenAPI spec)
                     * 
                     * Protected endpoints:
                     * - All other /api/** endpoints require authentication
                     */
                    @Bean
                    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                        http
                            .csrf(AbstractHttpConfigurer::disable)
                            .authorizeHttpRequests(auth -> auth
                                // Public endpoints
                                .requestMatchers(
                                    "/api/auth/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/v3/api-docs/**",
                                    "/swagger-resources/**",
                                    "/webjars/**"
                                ).permitAll()
                                // All other endpoints require authentication
                                .anyRequest().authenticated()
                            )
                            .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                            )
                            .authenticationProvider(authenticationProvider())
                            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                        
                        return http.build();
                    }
                    
                    /**
                     * Configures the authentication provider with UserDetailsService and PasswordEncoder.
                     */
                    @Bean
                    public AuthenticationProvider authenticationProvider() {
                        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
                        authProvider.setUserDetailsService(userDetailsService);
                        authProvider.setPasswordEncoder(passwordEncoder());
                        return authProvider;
                    }
                    
                    /**
                     * Provides the authentication manager bean.
                     */
                    @Bean
                    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) 
                            throws Exception {
                        return config.getAuthenticationManager();
                    }
                    
                    /**
                     * Provides BCrypt password encoder for secure password hashing.
                     */
                    @Bean
                    public PasswordEncoder passwordEncoder() {
                        return new BCryptPasswordEncoder();
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "SecurityConfig.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}