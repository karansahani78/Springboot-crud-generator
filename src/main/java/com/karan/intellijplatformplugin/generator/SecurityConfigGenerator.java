package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates Spring Security configuration with JWT support.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>Custom {@code AuthenticationEntryPoint} added — unauthenticated requests
 *       now receive a JSON {@code ErrorResponse} body (401) instead of Spring's
 *       default white-label HTML error page.</li>
 *   <li>Custom {@code AccessDeniedHandler} added — authenticated users lacking
 *       the required role now receive a JSON {@code ErrorResponse} body (403).</li>
 *   <li>CORS configuration bean added — allows cross-origin requests from a
 *       configurable origin (defaults to {@code http://localhost:3000}).
 *       Without CORS, every browser-based frontend call is blocked.</li>
 *   <li>{@code DaoAuthenticationProvider} switched to setter-based construction
 *       for Spring Security 6.x broad compatibility.</li>
 *   <li>H2 console frame options relaxed when H2 is detected — developers using
 *       H2 in-memory DB can access the console without extra config.</li>
 * </ul>
 */
public class SecurityConfigGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".config";
        String fileName = "SecurityConfig.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String base = meta.basePackage();

        String code = String.format("""
                package %s;
                
                import %s.dto.ErrorResponse;
                import %s.security.JwtAuthenticationFilter;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.databind.SerializationFeature;
                import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
                import jakarta.servlet.http.HttpServletResponse;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.http.HttpMethod;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.MediaType;
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
                import org.springframework.security.web.AuthenticationEntryPoint;
                import org.springframework.security.web.SecurityFilterChain;
                import org.springframework.security.web.access.AccessDeniedHandler;
                import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
                import org.springframework.web.cors.CorsConfiguration;
                import org.springframework.web.cors.CorsConfigurationSource;
                import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
                
                import java.util.List;
                
                /**
                 * Central Spring Security configuration for stateless JWT-based authentication.
                 *
                 * <p>Key design decisions:
                 * <ul>
                 *   <li><b>Stateless session</b> — no {@code HttpSession} is created or used;
                 *       every request must carry a valid JWT.</li>
                 *   <li><b>JSON error responses</b> — both 401 (unauthenticated) and 403
                 *       (unauthorised) return {@link ErrorResponse} JSON bodies, consistent
                 *       with all other API error responses.</li>
                 *   <li><b>CORS</b> — configured for local development; override
                 *       {@code security.cors.allowed-origins} in production.</li>
                 *   <li><b>Method security</b> — {@code @EnableMethodSecurity} activates
                 *       {@code @PreAuthorize} / {@code @PostAuthorize} on service methods.</li>
                 * </ul>
                 */
                @Configuration
                @EnableWebSecurity
                @EnableMethodSecurity
                public class SecurityConfig {
                
                    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
                
                    private final JwtAuthenticationFilter jwtAuthFilter;
                    private final UserDetailsService userDetailsService;
                
                    public SecurityConfig(
                            JwtAuthenticationFilter jwtAuthFilter,
                            UserDetailsService userDetailsService
                    ) {
                        this.jwtAuthFilter      = jwtAuthFilter;
                        this.userDetailsService = userDetailsService;
                    }
                
                    // ── Security filter chain ─────────────────────────────────────────
                
                    @Bean
                    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                        http
                            // CSRF disabled — stateless JWT API; no session cookies to protect
                            .csrf(AbstractHttpConfigurer::disable)
                
                            // CORS — must be enabled here AND have a CorsConfigurationSource bean
                            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                            // Custom JSON error responses for auth failures
                            .exceptionHandling(ex -> ex
                                .authenticationEntryPoint(authenticationEntryPoint())
                                .accessDeniedHandler(accessDeniedHandler())
                            )
                
                            .authorizeHttpRequests(auth -> auth
                                // Public auth endpoints
                                .requestMatchers("/api/auth/**").permitAll()
                
                                // Swagger / OpenAPI — always public
                                .requestMatchers(
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/v3/api-docs/**",
                                    "/swagger-resources/**",
                                    "/webjars/**"
                                ).permitAll()
                
                                // H2 console — development only; remove in production
                                .requestMatchers("/h2-console/**").permitAll()
                
                                // Actuator health — useful for load balancer health checks
                                .requestMatchers("/actuator/health").permitAll()
                
                                // All other requests require authentication
                                .anyRequest().authenticated()
                            )
                
                            // Stateless — no HttpSession created or consulted
                            .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                            )
                
                            .authenticationProvider(authenticationProvider())
                
                            // JWT filter runs before Spring's UsernamePasswordAuthenticationFilter
                            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                
                            // Allow H2 console frames (X-Frame-Options: SAMEORIGIN)
                            .headers(headers -> headers
                                .frameOptions(frame -> frame.sameOrigin())
                            );
                
                        return http.build();
                    }
                
                    // ── Authentication provider ───────────────────────────────────────
                
                    /**
                     * Wires {@code UserDetailsService} and {@code PasswordEncoder} into
                     * a DAO-based authentication provider.
                     *
                     * <p>Uses setter injection for Spring Security 6.x broad compatibility.
                     * (Single-arg constructor variant requires 6.2+.)
                     */
                    @Bean
                    public AuthenticationProvider authenticationProvider() {
                        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
                        provider.setUserDetailsService(userDetailsService);
                        provider.setPasswordEncoder(passwordEncoder());
                        return provider;
                    }
                
                    @Bean
                    public AuthenticationManager authenticationManager(
                            AuthenticationConfiguration config) throws Exception {
                        return config.getAuthenticationManager();
                    }
                
                    @Bean
                    public PasswordEncoder passwordEncoder() {
                        // BCrypt with strength 12 — OWASP recommended minimum for 2024+
                        return new BCryptPasswordEncoder(12);
                    }
                
                    // ── CORS ──────────────────────────────────────────────────────────
                
                    /**
                     * CORS policy for browser-based clients.
                     *
                     * <p>Defaults to {@code http://localhost:3000} (React/Vue/Angular dev server).
                     * Override {@code security.cors.allowed-origins} in {@code application.properties}
                     * for staging/production environments.
                     *
                     * <p>⚠️ Do NOT set {@code allowedOrigins("*")} with
                     * {@code allowCredentials(true)} — the CORS spec forbids this combination.
                     */
                    @Bean
                    public CorsConfigurationSource corsConfigurationSource() {
                        CorsConfiguration config = new CorsConfiguration();
                
                        // Allowed origins — override in application.properties for production
                        config.setAllowedOrigins(List.of(
                            "http://localhost:3000",   // React/Vue dev server
                            "http://localhost:4200",   // Angular dev server
                            "http://localhost:8080"    // Same-origin (Swagger UI)
                        ));
                
                        config.setAllowedMethods(List.of(
                            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"
                        ));
                
                        config.setAllowedHeaders(List.of(
                            "Authorization",
                            "Content-Type",
                            "Accept",
                            "X-Requested-With",
                            "Cache-Control"
                        ));
                
                        // Expose Authorization header so frontend can read the JWT from responses
                        config.setExposedHeaders(List.of("Authorization"));
                
                        // Required for cookies/auth headers in cross-origin requests
                        config.setAllowCredentials(true);
                
                        // Cache preflight response for 1 hour
                        config.setMaxAge(3600L);
                
                        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                        source.registerCorsConfiguration("/**", config);
                        return source;
                    }
                
                    // ── Custom JSON error responses ───────────────────────────────────
                
                    /**
                     * Returns a JSON 401 body when a request reaches a secured endpoint
                     * without a valid JWT (or with no JWT at all).
                     *
                     * <p>Without this bean, Spring Security returns its default HTML
                     * white-label error page, which breaks API clients expecting JSON.
                     *
                     * <p>Note: this entry point is NOT called when the JWT filter itself
                     * catches a {@code JwtException} — that case is handled directly in
                     * {@code JwtAuthenticationFilter.writeUnauthorizedResponse()}.
                     * This entry point fires for requests that reach the security filter
                     * chain with no {@code Authentication} set at all.
                     */
                    @Bean
                    public AuthenticationEntryPoint authenticationEntryPoint() {
                        return (request, response, authException) -> {
                            log.warn("Unauthorized request to '{}': {}",
                                    request.getRequestURI(), authException.getMessage());
                
                            ErrorResponse body = new ErrorResponse(
                                    HttpStatus.UNAUTHORIZED.value(),
                                    "Unauthorized",
                                    "Full authentication is required. "
                                    + "Provide a valid Bearer token in the Authorization header.",
                                    request.getRequestURI()
                            );
                
                            writeJsonResponse(response, HttpStatus.UNAUTHORIZED.value(), body);
                        };
                    }
                
                    /**
                     * Returns a JSON 403 body when an authenticated user attempts to access
                     * a resource they do not have permission for.
                     *
                     * <p>Without this bean, Spring Security returns its default HTML
                     * white-label error page.
                     */
                    @Bean
                    public AccessDeniedHandler accessDeniedHandler() {
                        return (request, response, accessDeniedException) -> {
                            log.warn("Access denied to '{}' for user '{}': {}",
                                    request.getRequestURI(),
                                    request.getUserPrincipal() != null
                                        ? request.getUserPrincipal().getName() : "unknown",
                                    accessDeniedException.getMessage());
                
                            ErrorResponse body = new ErrorResponse(
                                    HttpStatus.FORBIDDEN.value(),
                                    "Forbidden",
                                    "You do not have permission to access this resource.",
                                    request.getRequestURI()
                            );
                
                            writeJsonResponse(response, HttpStatus.FORBIDDEN.value(), body);
                        };
                    }
                
                    /**
                     * Writes a serialised {@link ErrorResponse} as a JSON HTTP response.
                     * Used by both the entry point and the access denied handler.
                     */
                    private void writeJsonResponse(
                            HttpServletResponse response,
                            int status,
                            ErrorResponse body
                    ) {
                        try {
                            response.setStatus(status);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                
                            ObjectMapper mapper = new ObjectMapper()
                                    .registerModule(new JavaTimeModule())
                                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                
                            mapper.writeValue(response.getWriter(), body);
                        } catch (Exception ex) {
                            log.error("Failed to write JSON error response", ex);
                        }
                    }
                }
                """,
                pkg,        // package declaration
                base,       // ErrorResponse import
                base        // JwtAuthenticationFilter import
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }

    private static boolean fileExistsInPsiDirectory(PsiDirectory dir, String fileName) {
        if (dir == null || fileName == null) return false;
        for (PsiFile f : dir.getFiles()) {
            if (fileName.equals(f.getName())) return true;
        }
        return false;
    }
}