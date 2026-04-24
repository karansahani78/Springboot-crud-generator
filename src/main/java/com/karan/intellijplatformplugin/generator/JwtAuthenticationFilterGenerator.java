package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates the {@code JwtAuthenticationFilter} servlet filter.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>Added {@code JwtException} catch block in {@code doFilterInternal} —
 *       without this, a malformed or expired JWT throws an unhandled exception
 *       that bypasses {@code GlobalExceptionHandler} (filters run outside the
 *       Spring MVC DispatcherServlet). The filter now writes a JSON 401 directly
 *       to the response so the client receives a consistent error body.</li>
 *   <li>Added {@code ObjectMapper} for writing the JSON 401 error body —
 *       consistent with the {@code ErrorResponse} structure used across the app.</li>
 * </ul>
 */
public class JwtAuthenticationFilterGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".security";
        String fileName = "JwtAuthenticationFilter.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import com.fasterxml.jackson.databind.ObjectMapper;
                import com.fasterxml.jackson.databind.SerializationFeature;
                import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
                import io.jsonwebtoken.JwtException;
                import jakarta.servlet.FilterChain;
                import jakarta.servlet.ServletException;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.MediaType;
                import org.springframework.lang.NonNull;
                import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                import org.springframework.security.core.context.SecurityContextHolder;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
                import org.springframework.stereotype.Component;
                import org.springframework.web.filter.OncePerRequestFilter;
                
                import java.io.IOException;
                import java.time.LocalDateTime;
                import java.util.LinkedHashMap;
                import java.util.Map;
                
                /**
                 * Stateless JWT authentication filter — runs once per request before the
                 * Spring Security filter chain processes authorization.
                 *
                 * <p>Flow:
                 * <ol>
                 *   <li>Extract {@code Authorization: Bearer <token>} header.</li>
                 *   <li>Parse and validate the JWT using {@link JwtService}.</li>
                 *   <li>If valid and no authentication is set, load the {@link UserDetails}
                 *       and set a {@link UsernamePasswordAuthenticationToken} on the
                 *       {@link SecurityContextHolder}.</li>
                 *   <li>Continue the filter chain.</li>
                 * </ol>
                 *
                 * <p>Exception handling: {@link JwtException} (malformed/expired/invalid-signature
                 * tokens) is caught HERE, not in {@code GlobalExceptionHandler}, because filters
                 * run outside the Spring MVC {@code DispatcherServlet}. The filter writes a
                 * JSON 401 body directly to the response so clients receive a consistent error
                 * structure rather than Spring Boot's default white-label error page.
                 */
                @Component
                public class JwtAuthenticationFilter extends OncePerRequestFilter {
                
                    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
                
                    private final JwtService jwtService;
                    private final UserDetailsService userDetailsService;
                    private final ObjectMapper objectMapper;
                
                    public JwtAuthenticationFilter(
                            JwtService jwtService,
                            UserDetailsService userDetailsService
                    ) {
                        this.jwtService         = jwtService;
                        this.userDetailsService = userDetailsService;
                        // Standalone ObjectMapper — not injected to avoid circular bean dependency
                        this.objectMapper = new ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    }
                
                    @Override
                    protected void doFilterInternal(
                            @NonNull HttpServletRequest request,
                            @NonNull HttpServletResponse response,
                            @NonNull FilterChain filterChain
                    ) throws ServletException, IOException {
                
                        final String authHeader = request.getHeader("Authorization");
                
                        // Pass through if no Bearer token present
                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            filterChain.doFilter(request, response);
                            return;
                        }
                
                        final String jwt = authHeader.substring(7);
                
                        try {
                            final String username = jwtService.extractUsername(jwt);
                
                            if (username != null
                                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                                if (jwtService.isTokenValid(jwt, userDetails)) {
                                    UsernamePasswordAuthenticationToken authToken =
                                            new UsernamePasswordAuthenticationToken(
                                                    userDetails,
                                                    null,
                                                    userDetails.getAuthorities()
                                            );
                                    authToken.setDetails(
                                            new WebAuthenticationDetailsSource().buildDetails(request));
                                    SecurityContextHolder.getContext().setAuthentication(authToken);
                                    log.debug("Authenticated user '{}' for request {}",
                                            username, request.getRequestURI());
                                } else {
                                    log.warn("Invalid JWT token for user '{}'", username);
                                }
                            }
                
                            filterChain.doFilter(request, response);
                
                        } catch (JwtException ex) {
                            // Filters run OUTSIDE DispatcherServlet — GlobalExceptionHandler
                            // cannot catch this. We must write the 401 response directly.
                            log.warn("JWT processing failed for request {}: {}",
                                    request.getRequestURI(), ex.getMessage());
                            writeUnauthorizedResponse(response, request.getRequestURI(), ex.getMessage());
                        }
                    }
                
                    /**
                     * Writes a JSON 401 error body directly to the servlet response.
                     *
                     * <p>The structure mirrors {@code ErrorResponse} so API clients receive
                     * a consistent error format for both MVC and filter-layer errors.
                     */
                    private void writeUnauthorizedResponse(
                            HttpServletResponse response,
                            String path,
                            String detail
                    ) throws IOException {
                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("timestamp", LocalDateTime.now().toString());
                        body.put("status", HttpStatus.UNAUTHORIZED.value());
                        body.put("error", "Unauthorized");
                        body.put("message", "Invalid or expired JWT token. Please re-authenticate.");
                        body.put("path", path);
                
                        objectMapper.writeValue(response.getWriter(), body);
                    }
                }
                """, pkg);

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