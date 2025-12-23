package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates ErrorResponse DTO for consistent API error responses.
 */
public class ErrorResponseGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".dto";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import com.fasterxml.jackson.annotation.JsonFormat;
                import io.swagger.v3.oas.annotations.media.Schema;
                import java.time.LocalDateTime;
                import java.util.ArrayList;
                import java.util.List;
                
                /**
                 * Standard error response structure for API errors.
                 */
                @Schema(description = "Error response structure")
                public class ErrorResponse {
                    
                    @Schema(description = "Timestamp when error occurred", example = "2024-12-23T17:28:34")
                    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
                    private LocalDateTime timestamp;
                    
                    @Schema(description = "HTTP status code", example = "404")
                    private int status;
                    
                    @Schema(description = "Error type", example = "Not Found")
                    private String error;
                    
                    @Schema(description = "Error message", example = "Resource not found")
                    private String message;
                    
                    @Schema(description = "Request path", example = "/api/users/123")
                    private String path;
                    
                    @Schema(description = "Additional error details")
                    private List<String> details;
                    
                    public ErrorResponse() {
                        this.timestamp = LocalDateTime.now();
                        this.details = new ArrayList<>();
                    }
                    
                    public ErrorResponse(int status, String error, String message, String path) {
                        this();
                        this.status = status;
                        this.error = error;
                        this.message = message;
                        this.path = path;
                    }
                    
                    public LocalDateTime getTimestamp() {
                        return timestamp;
                    }
                    
                    public void setTimestamp(LocalDateTime timestamp) {
                        this.timestamp = timestamp;
                    }
                    
                    public int getStatus() {
                        return status;
                    }
                    
                    public void setStatus(int status) {
                        this.status = status;
                    }
                    
                    public String getError() {
                        return error;
                    }
                    
                    public void setError(String error) {
                        this.error = error;
                    }
                    
                    public String getMessage() {
                        return message;
                    }
                    
                    public void setMessage(String message) {
                        this.message = message;
                    }
                    
                    public String getPath() {
                        return path;
                    }
                    
                    public void setPath(String path) {
                        this.path = path;
                    }
                    
                    public List<String> getDetails() {
                        return details;
                    }
                    
                    public void setDetails(List<String> details) {
                        this.details = details;
                    }
                    
                    public void addDetail(String detail) {
                        if (this.details == null) {
                            this.details = new ArrayList<>();
                        }
                        this.details.add(detail);
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "ErrorResponse.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}