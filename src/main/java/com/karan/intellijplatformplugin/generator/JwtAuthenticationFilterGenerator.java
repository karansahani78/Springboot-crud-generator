package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates JWT authentication filter.
 */
public class JwtAuthenticationFilterGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".security";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, "JwtAuthenticationFilter.java")) {
            System.out.println("JwtAuthenticationFilter.java already exists, skipping generation.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import jakarta.servlet.FilterChain;
                import jakarta.servlet.ServletException;
                import jakarta.servlet.http.HttpServletRequest;
                import jakarta.servlet.http.HttpServletResponse;
                import org.springframework.lang.NonNull;
                import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                import org.springframework.security.core.context.SecurityContextHolder;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
                import org.springframework.stereotype.Component;
                import org.springframework.web.filter.OncePerRequestFilter;
                
                import java.io.IOException;
                
                /**
                 * JWT Authentication Filter (Spring Boot 3/4 compatible)
                 */
                @Component
                public class JwtAuthenticationFilter extends OncePerRequestFilter {
                
                    private final JwtService jwtService;
                    private final UserDetailsService userDetailsService;
                
                    public JwtAuthenticationFilter(JwtService jwtService,
                                                   UserDetailsService userDetailsService) {
                        this.jwtService = jwtService;
                        this.userDetailsService = userDetailsService;
                    }
                
                    @Override
                    protected void doFilterInternal(
                            @NonNull HttpServletRequest request,
                            @NonNull HttpServletResponse response,
                            @NonNull FilterChain filterChain
                    ) throws ServletException, IOException {
                
                        final String authHeader = request.getHeader("Authorization");
                
                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            filterChain.doFilter(request, response);
                            return;
                        }
                
                        final String jwt = authHeader.substring(7);
                        final String username = jwtService.extractUsername(jwt);
                
                        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                            if (jwtService.isTokenValid(jwt, userDetails)) {
                                UsernamePasswordAuthenticationToken authToken =
                                        new UsernamePasswordAuthenticationToken(
                                                userDetails,
                                                null,
                                                userDetails.getAuthorities()
                                        );
                
                                authToken.setDetails(
                                        new WebAuthenticationDetailsSource().buildDetails(request)
                                );
                
                                SecurityContextHolder.getContext().setAuthentication(authToken);
                            }
                        }
                
                        filterChain.doFilter(request, response);
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("JwtAuthenticationFilter.java", JavaFileType.INSTANCE, code);

        dir.add(file);
    }
}