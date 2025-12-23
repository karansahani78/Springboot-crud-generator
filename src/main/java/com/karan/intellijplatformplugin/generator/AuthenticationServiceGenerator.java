package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Authentication service.
 */
public class AuthenticationServiceGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".service";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.dto.AuthenticationRequest;
                import %s.dto.AuthenticationResponse;
                import %s.dto.RegisterRequest;
                import %s.entity.AppUser;
                import %s.entity.Role;
                import %s.repository.AppUserRepository;
                import %s.security.JwtService;
                import org.springframework.security.authentication.AuthenticationManager;
                import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                
                /**
                 * Service for authentication operations (register, login).
                 */
                @Service
                @Transactional(readOnly = true)
                public class AuthenticationService {
                    
                    private final AppUserRepository repository;
                    private final PasswordEncoder passwordEncoder;
                    private final JwtService jwtService;
                    private final AuthenticationManager authenticationManager;
                    
                    public AuthenticationService(
                            AppUserRepository repository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService,
                            AuthenticationManager authenticationManager
                    ) {
                        this.repository = repository;
                        this.passwordEncoder = passwordEncoder;
                        this.jwtService = jwtService;
                        this.authenticationManager = authenticationManager;
                    }
                    
                    /**
                     * Register a new user.
                     */
                    @Transactional
                    public AuthenticationResponse register(RegisterRequest request) {
                        // Check if username already exists
                        if (repository.existsByUsername(request.getUsername())) {
                            throw new RuntimeException("Username already exists");
                        }
                        
                        // Check if email already exists
                        if (repository.existsByEmail(request.getEmail())) {
                            throw new RuntimeException("Email already exists");
                        }
                        
                        var user = new AppUser(
                                request.getUsername(),
                                request.getEmail(),
                                passwordEncoder.encode(request.getPassword()),
                                Role.USER
                        );
                        
                        repository.save(user);
                        
                        var jwtToken = jwtService.generateToken(user);
                        return new AuthenticationResponse(jwtToken);
                    }
                    
                    /**
                     * Authenticate user and generate JWT token.
                     */
                    public AuthenticationResponse authenticate(AuthenticationRequest request) {
                        authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                        request.getUsername(),
                                        request.getPassword()
                                )
                        );
                        
                        var user = repository.findByUsername(request.getUsername())
                                .orElseThrow(() -> new RuntimeException("User not found"));
                        
                        var jwtToken = jwtService.generateToken(user);
                        return new AuthenticationResponse(jwtToken);
                    }
                }
                """,
                pkg,
                meta.basePackage(),
                meta.basePackage(),
                meta.basePackage(),
                meta.basePackage(),
                meta.basePackage(),
                meta.basePackage(),
                meta.basePackage()
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "AuthenticationService.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}