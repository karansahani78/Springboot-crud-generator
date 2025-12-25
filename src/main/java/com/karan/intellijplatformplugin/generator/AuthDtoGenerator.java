package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates authentication DTOs.
 */
public class AuthDtoGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".dto";

        // ✅ CHECK IF FILES ALREADY EXIST
        if (!FileExistsUtil.fileExistsInPackage(root, pkg, "AuthenticationRequest.java")) {
            PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);
            generateAuthenticationRequest(project, dir, pkg);
        } else {
            System.out.println("AuthenticationRequest.java already exists, skipping.");
        }

        if (!FileExistsUtil.fileExistsInPackage(root, pkg, "RegisterRequest.java")) {
            PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);
            generateRegisterRequest(project, dir, pkg);
        } else {
            System.out.println("RegisterRequest.java already exists, skipping.");
        }

        if (!FileExistsUtil.fileExistsInPackage(root, pkg, "AuthenticationResponse.java")) {
            PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);
            generateAuthenticationResponse(project, dir, pkg);
        } else {
            System.out.println("AuthenticationResponse.java already exists, skipping.");
        }
    }

    private static void generateAuthenticationRequest(Project project, PsiDirectory dir, String pkg) {
        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                import jakarta.validation.constraints.NotBlank;
                
                @Schema(description = "Authentication request with username and password")
                public class AuthenticationRequest {
                    
                    @Schema(description = "Username", example = "john.doe")
                    @NotBlank(message = "Username is required")
                    private String username;
                    
                    @Schema(description = "Password", example = "password123")
                    @NotBlank(message = "Password is required")
                    private String password;
                    
                    public AuthenticationRequest() {}
                    
                    public AuthenticationRequest(String username, String password) {
                        this.username = username;
                        this.password = password;
                    }
                    
                    public String getUsername() {
                        return username;
                    }
                    
                    public void setUsername(String username) {
                        this.username = username;
                    }
                    
                    public String getPassword() {
                        return password;
                    }
                    
                    public void setPassword(String password) {
                        this.password = password;
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("AuthenticationRequest.java", JavaFileType.INSTANCE, code);
        dir.add(file);
    }

    private static void generateRegisterRequest(Project project, PsiDirectory dir, String pkg) {
        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                import jakarta.validation.constraints.Email;
                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.Size;
                
                @Schema(description = "Registration request for new user")
                public class RegisterRequest {
                    
                    @Schema(description = "Username", example = "john.doe")
                    @NotBlank(message = "Username is required")
                    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
                    private String username;
                    
                    @Schema(description = "Email address", example = "john.doe@example.com")
                    @NotBlank(message = "Email is required")
                    @Email(message = "Email must be valid")
                    private String email;
                    
                    @Schema(description = "Password", example = "SecurePass123!")
                    @NotBlank(message = "Password is required")
                    @Size(min = 6, message = "Password must be at least 6 characters")
                    private String password;
                    
                    public RegisterRequest() {}
                    
                    public RegisterRequest(String username, String email, String password) {
                        this.username = username;
                        this.email = email;
                        this.password = password;
                    }
                    
                    public String getUsername() {
                        return username;
                    }
                    
                    public void setUsername(String username) {
                        this.username = username;
                    }
                    
                    public String getEmail() {
                        return email;
                    }
                    
                    public void setEmail(String email) {
                        this.email = email;
                    }
                    
                    public String getPassword() {
                        return password;
                    }
                    
                    public void setPassword(String password) {
                        this.password = password;
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("RegisterRequest.java", JavaFileType.INSTANCE, code);
        dir.add(file);
    }

    private static void generateAuthenticationResponse(Project project, PsiDirectory dir, String pkg) {
        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                
                @Schema(description = "Authentication response with JWT token")
                public class AuthenticationResponse {
                    
                    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                    private String token;
                    
                    @Schema(description = "Token type", example = "Bearer")
                    private String type = "Bearer";
                    
                    public AuthenticationResponse() {}
                    
                    public AuthenticationResponse(String token) {
                        this.token = token;
                    }
                    
                    public String getToken() {
                        return token;
                    }
                    
                    public void setToken(String token) {
                        this.token = token;
                    }
                    
                    public String getType() {
                        return type;
                    }
                    
                    public void setType(String type) {
                        this.type = type;
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("AuthenticationResponse.java", JavaFileType.INSTANCE, code);
        dir.add(file);
    }
}