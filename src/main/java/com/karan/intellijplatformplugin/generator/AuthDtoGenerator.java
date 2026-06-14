package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates authentication DTOs: request, register, and response.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard contract.</li>
 *   <li>Directory resolved once and shared across sub-generators.</li>
 *   <li>PSI directory guard added per file.</li>
 *   <li>{@code AuthenticationResponse} now carries {@code expiresIn} so clients
 *       can schedule token refresh without parsing the JWT.</li>
 *   <li>{@code RegisterRequest} password minimum raised to 8 chars and adds
 *       a pattern constraint for production-grade strength.</li>
 *   <li>{@code AuthenticationRequest.toString()} excludes password.</li>
 * </ul>
 */
public class AuthDtoGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".dto";

        // Resolve directory once — all three DTOs live in the same package
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        generateAuthenticationRequest(project, dir, root, pkg);
        generateRegisterRequest(project, dir, root, pkg);
        generateAuthenticationResponse(project, dir, root, pkg);
    }

    // =========================================================================
    // AuthenticationRequest
    // =========================================================================

    private static void generateAuthenticationRequest(
            Project project, PsiDirectory dir, PsiDirectory root, String pkg) {

        String fileName = "AuthenticationRequest.java";
        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }
        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                import jakarta.validation.constraints.NotBlank;
                
                /**
                 * Login request payload carrying credentials.
                 *
                 * <p>⚠️ {@code password} is excluded from {@code toString()} to prevent
                 * accidental credential exposure in application logs.
                 */
                @Schema(description = "Login request with username and password")
                public class AuthenticationRequest {
                
                    @Schema(description = "Registered username", example = "john.doe")
                    @NotBlank(message = "Username is required")
                    private String username;
                
                    @Schema(description = "Account password", example = "SecurePass123!")
                    @NotBlank(message = "Password is required")
                    private String password;
                
                    public AuthenticationRequest() {}
                
                    public AuthenticationRequest(String username, String password) {
                        this.username = username;
                        this.password = password;
                    }
                
                    public String getUsername() { return username; }
                    public void setUsername(String username) { this.username = username; }
                
                    public String getPassword() { return password; }
                    public void setPassword(String password) { this.password = password; }
                
                    /**
                     * ⚠️ Password intentionally excluded to prevent log exposure.
                     */
                    @Override
                    public String toString() {
                        return "AuthenticationRequest{username='" + username + "'}";
                    }
                }
                """, pkg);

        addFile(project, dir, fileName, code);
    }

    // =========================================================================
    // RegisterRequest
    // =========================================================================

    private static void generateRegisterRequest(
            Project project, PsiDirectory dir, PsiDirectory root, String pkg) {

        String fileName = "RegisterRequest.java";
        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }
        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                import jakarta.validation.constraints.Email;
                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.Pattern;
                import jakarta.validation.constraints.Size;
                
                /**
                 * New user registration payload.
                 *
                 * <p>⚠️ {@code password} is excluded from {@code toString()} to prevent
                 * accidental credential exposure in application logs.
                 */
                @Schema(description = "Registration request for a new user account")
                public class RegisterRequest {
                
                    @Schema(description = "Unique login name", example = "john.doe")
                    @NotBlank(message = "Username is required")
                    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
                    private String username;
                
                    @Schema(description = "Valid email address", example = "john.doe@example.com")
                    @NotBlank(message = "Email is required")
                    @Email(message = "Email must be a valid address")
                    private String email;
                
                    @Schema(description = "Password (min 8 chars, must contain upper, lower, digit, special)",
                            example = "SecurePass123!")
                    @NotBlank(message = "Password is required")
                    @Size(min = 8, message = "Password must be at least 8 characters")
                    @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\\\d)(?=.*[@$!%%*?&])[A-Za-z\\\\d@$!%%*?&]{8,}$",
                        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%%*?&)"
                    )
                    private String password;
                
                    public RegisterRequest() {}
                
                    public RegisterRequest(String username, String email, String password) {
                        this.username = username;
                        this.email    = email;
                        this.password = password;
                    }
                
                    public String getUsername() { return username; }
                    public void setUsername(String username) { this.username = username; }
                
                    public String getEmail() { return email; }
                    public void setEmail(String email) { this.email = email; }
                
                    public String getPassword() { return password; }
                    public void setPassword(String password) { this.password = password; }
                
                    /**
                     * ⚠️ Password intentionally excluded to prevent log exposure.
                     */
                    @Override
                    public String toString() {
                        return "RegisterRequest{username='" + username
                                + "', email='" + email + "'}";
                    }
                }
                """, pkg);

        addFile(project, dir, fileName, code);
    }

    // =========================================================================
    // AuthenticationResponse
    // =========================================================================

    private static void generateAuthenticationResponse(
            Project project, PsiDirectory dir, PsiDirectory root, String pkg) {

        String fileName = "AuthenticationResponse.java";
        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }
        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                
                /**
                 * JWT authentication response returned after successful login or registration.
                 *
                 * <p>Carries the access token, token type, and expiry duration so clients can
                 * schedule token refresh without parsing the JWT claims themselves.
                 */
                @Schema(description = "JWT authentication response")
                public class AuthenticationResponse {
                
                    @Schema(description = "JWT access token",
                            example = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...")
                    private String token;
                
                    @Schema(description = "Token type — always 'Bearer'", example = "Bearer")
                    private String type = "Bearer";
                
                    @Schema(description = "Token lifetime in milliseconds", example = "86400000")
                    private long expiresIn;
                
                    public AuthenticationResponse() {}
                
                    public AuthenticationResponse(String token) {
                        this.token = token;
                    }
                
                    public AuthenticationResponse(String token, long expiresIn) {
                        this.token     = token;
                        this.expiresIn = expiresIn;
                    }
                
                    public String getToken() { return token; }
                    public void setToken(String token) { this.token = token; }
                
                    public String getType() { return type; }
                    public void setType(String type) { this.type = type; }
                
                    public long getExpiresIn() { return expiresIn; }
                    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
                
                    @Override
                    public String toString() {
                        return "AuthenticationResponse{type='" + type
                                + "', expiresIn=" + expiresIn + "}";
                    }
                }
                """, pkg);

        addFile(project, dir, fileName, code);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void addFile(Project project, PsiDirectory dir, String fileName, String code) {
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