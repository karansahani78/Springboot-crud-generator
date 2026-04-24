package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates {@code ErrorResponse} DTO for consistent API error responses.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>PSI directory guard added — prevents {@code dir.add()} collision within a
 *       single {@code WriteCommandAction} when multiple entities are generated.</li>
 *   <li>Added {@code addDetails(List<String>)} bulk adder — used by
 *       {@code GlobalExceptionHandler} when collecting multiple field errors.</li>
 * </ul>
 */
public class ErrorResponseGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".dto";
        String fileName = "ErrorResponse.java";

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
                
                import com.fasterxml.jackson.annotation.JsonFormat;
                import com.fasterxml.jackson.annotation.JsonInclude;
                import io.swagger.v3.oas.annotations.media.Schema;
                import java.time.LocalDateTime;
                import java.util.ArrayList;
                import java.util.List;
                
                /**
                 * Standard error response body returned by {@code GlobalExceptionHandler}
                 * for all error conditions.
                 *
                 * <p>{@code details} is omitted from the JSON response when empty
                 * (via {@code @JsonInclude}) to keep simple error responses clean.
                 */
                @Schema(description = "Standard API error response")
                @JsonInclude(JsonInclude.Include.NON_EMPTY)
                public class ErrorResponse {
                
                    @Schema(description = "Timestamp when the error occurred",
                            example = "2024-12-23T17:28:34")
                    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
                    private LocalDateTime timestamp;
                
                    @Schema(description = "HTTP status code", example = "404")
                    private int status;
                
                    @Schema(description = "HTTP error type", example = "Not Found")
                    private String error;
                
                    @Schema(description = "Human-readable error message",
                            example = "Department not found with id: '42'")
                    private String message;
                
                    @Schema(description = "Request path where the error occurred",
                            example = "/api/employees/7")
                    private String path;
                
                    @Schema(description = "Field-level validation errors or additional context")
                    private List<String> details;
                
                    // ── Constructors ──────────────────────────────────────────────────
                
                    public ErrorResponse() {
                        this.timestamp = LocalDateTime.now();
                        this.details   = new ArrayList<>();
                    }
                
                    public ErrorResponse(int status, String error, String message, String path) {
                        this();
                        this.status  = status;
                        this.error   = error;
                        this.message = message;
                        this.path    = path;
                    }
                
                    // ── Getters & Setters ─────────────────────────────────────────────
                
                    public LocalDateTime getTimestamp() { return timestamp; }
                    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
                
                    public int getStatus() { return status; }
                    public void setStatus(int status) { this.status = status; }
                
                    public String getError() { return error; }
                    public void setError(String error) { this.error = error; }
                
                    public String getMessage() { return message; }
                    public void setMessage(String message) { this.message = message; }
                
                    public String getPath() { return path; }
                    public void setPath(String path) { this.path = path; }
                
                    public List<String> getDetails() { return details; }
                    public void setDetails(List<String> details) { this.details = details; }
                
                    /**
                     * Appends a single detail string to the details list.
                     *
                     * @param detail field-level error or additional context message
                     */
                    public void addDetail(String detail) {
                        if (this.details == null) this.details = new ArrayList<>();
                        this.details.add(detail);
                    }
                
                    /**
                     * Appends multiple detail strings in bulk.
                     * Used by {@code GlobalExceptionHandler} when collecting
                     * all field errors from a {@code MethodArgumentNotValidException}.
                     *
                     * @param moreDetails list of additional detail messages
                     */
                    public void addDetails(List<String> moreDetails) {
                        if (moreDetails == null) return;
                        if (this.details == null) this.details = new ArrayList<>();
                        this.details.addAll(moreDetails);
                    }
                
                    @Override
                    public String toString() {
                        return "ErrorResponse{"
                                + "status=" + status
                                + ", error='" + error + '\\''
                                + ", message='" + message + '\\''
                                + ", path='" + path + '\\''
                                + ", detailCount=" + (details != null ? details.size() : 0)
                                + '}';
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
        for (PsiFile existing : dir.getFiles()) {
            if (fileName.equals(existing.getName())) return true;
        }
        return false;
    }
}