package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates Authentication controller for login/register.
 *
 * <p>Multi-entity change: accepts {@code List<ClassMeta> allEntities} — standard
 * contract. The auth controller is a singleton and does not vary per entity,
 * so the list is intentionally unused in the output.
 */
public class AuthControllerGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities   // ← standard contract; unused in output
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".controller";
        String fileName = "AuthenticationController.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists — skipping generation.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String base = meta.basePackage();

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
                 * Endpoints are public — no JWT required.
                 */
                @RestController
                @RequestMapping("/api/auth")
                @Tag(name = "Authentication", description = "Register and login endpoints")
                public class AuthenticationController {
                    
                    private final AuthenticationService authenticationService;
                    
                    public AuthenticationController(AuthenticationService authenticationService) {
                        this.authenticationService = authenticationService;
                    }
                    
                    @Operation(
                        summary = "Register new user",
                        description = "Creates a new account and returns a JWT token"
                    )
                    @PostMapping("/register")
                    public ResponseEntity<AuthenticationResponse> register(
                            @Valid @RequestBody RegisterRequest request
                    ) {
                        return ResponseEntity.ok(authenticationService.register(request));
                    }
                    
                    @Operation(
                        summary = "Authenticate user",
                        description = "Validates credentials and returns a JWT token"
                    )
                    @PostMapping("/login")
                    public ResponseEntity<AuthenticationResponse> authenticate(
                            @Valid @RequestBody AuthenticationRequest request
                    ) {
                        return ResponseEntity.ok(authenticationService.authenticate(request));
                    }
                }
                """,
                pkg,
                base, base, base, base
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }
}