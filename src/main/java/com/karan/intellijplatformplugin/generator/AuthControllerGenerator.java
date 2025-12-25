package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Authentication controller for login/register.
 */
public class AuthControllerGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".controller";
        // CHECK IF FILE ALREADY EXISTS
        if (FileExistsUtil.fileExistsInPackage(root, pkg, "AuthenticationController.java")) {
            System.out.println("AuthenticationController.java already exists, skipping generation.");
            return;
        }
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.dto.AuthenticationRequest;
                import %s.dto.AuthenticationResponse;
                import %s.dto.RegisterRequest;
                import %s.service.AuthenticationService;
                import io.swagger.v3.oas.annotations.Operation;
                import io.swagger.v3.oas.annotations.tags.Tag;
                import jakarta.validation.Valid;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                /**
                 * REST Controller for authentication operations.
                 */
                @RestController
                @RequestMapping("/api/auth")
                @Tag(name = "Authentication", description = "Authentication endpoints for login and registration")
                public class AuthenticationController {
                    
                    private final AuthenticationService authenticationService;
                    
                    public AuthenticationController(AuthenticationService authenticationService) {
                        this.authenticationService = authenticationService;
                    }
                    
                    @Operation(summary = "Register new user", description = "Create a new user account")
                    @PostMapping("/register")
                    public ResponseEntity<AuthenticationResponse> register(
                            @Valid @RequestBody RegisterRequest request
                    ) {
                        return ResponseEntity.ok(authenticationService.register(request));
                    }
                    
                    @Operation(summary = "Authenticate user", description = "Login with username and password")
                    @PostMapping("/login")
                    public ResponseEntity<AuthenticationResponse> authenticate(
                            @Valid @RequestBody AuthenticationRequest request
                    ) {
                        return ResponseEntity.ok(authenticationService.authenticate(request));
                    }
                }
                """,
                pkg,
                meta.basePackage(),
                meta.basePackage(),
                meta.basePackage(),
                meta.basePackage()
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "AuthenticationController.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}