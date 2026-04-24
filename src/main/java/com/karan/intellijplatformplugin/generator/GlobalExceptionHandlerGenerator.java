package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates {@code GlobalExceptionHandler} with {@code @RestControllerAdvice}.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>Added {@code DataIntegrityViolationException} handler — critical in multi-entity
 *       systems where FK constraint violations occur when related entity IDs are invalid
 *       or when cascade deletes are blocked by child records.</li>
 *   <li>Added {@code HttpMessageNotReadableException} handler — catches malformed JSON
 *       bodies, which are common when callers send entity objects instead of ID fields
 *       in relationship DTOs.</li>
 *   <li>Added {@code NoHandlerFoundException} handler for clean 404 on unknown paths.</li>
 *   <li>Added {@code HttpRequestMethodNotAllowedException} handler for clean 405.</li>
 *   <li>Validation error handler uses {@code addDetails()} bulk method from
 *       updated {@code ErrorResponse}.</li>
 * </ul>
 */
public class GlobalExceptionHandlerGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".exception";
        String fileName = "GlobalExceptionHandler.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String basePackage = meta.basePackage();

        String code = String.format("""
                package %s;
                
                import %s.dto.ErrorResponse;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.dao.DataIntegrityViolationException;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.http.converter.HttpMessageNotReadableException;
                import org.springframework.validation.FieldError;
                import org.springframework.web.server.MethodNotAllowedException;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.RestControllerAdvice;
                import org.springframework.web.context.request.WebRequest;
                import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
                import org.springframework.web.servlet.NoHandlerFoundException;
                
                import java.util.List;
                import java.util.stream.Collectors;
                
                /**
                 * Centralised exception handler — converts all application exceptions to
                 * structured {@link ErrorResponse} JSON bodies with appropriate HTTP status codes.
                 *
                 * <p>Handler priority (most specific first, least specific last):
                 * <ol>
                 *   <li>{@link ResourceNotFoundException}      → 404</li>
                 *   <li>{@link BadRequestException}            → 400</li>
                 *   <li>{@link DuplicateResourceException}     → 409</li>
                 *   <li>{@link MethodArgumentNotValidException}→ 400 (bean validation)</li>
                 *   <li>{@link MethodArgumentTypeMismatchException} → 400</li>
                 *   <li>{@link HttpMessageNotReadableException}→ 400 (malformed JSON)</li>
                 *   <li>{@link DataIntegrityViolationException}→ 409 (FK / unique constraint)</li>
                 *   <li>{@link NoHandlerFoundException}        → 404 (unknown path)</li>
                 *   <li>{@link HttpRequestMethodNotAllowedException} → 405</li>
                 *   <li>{@link Exception}                      → 500 (catch-all)</li>
                 * </ol>
                 */
                @RestControllerAdvice
                public class GlobalExceptionHandler {
                
                    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
                
                    // ── Domain exceptions ─────────────────────────────────────────────
                
                    /**
                     * {@link ResourceNotFoundException} → 404 Not Found.
                     *
                     * <p>Triggered by service layer when an entity or related entity
                     * cannot be found by the supplied ID.
                     */
                    @ExceptionHandler(ResourceNotFoundException.class)
                    public ResponseEntity<ErrorResponse> handleResourceNotFound(
                            ResourceNotFoundException ex, WebRequest request) {
                
                        log.warn("Resource not found: {}", ex.getMessage());
                        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
                    }
                
                    /**
                     * {@link BadRequestException} → 400 Bad Request.
                     */
                    @ExceptionHandler(BadRequestException.class)
                    public ResponseEntity<ErrorResponse> handleBadRequest(
                            BadRequestException ex, WebRequest request) {
                
                        log.warn("Bad request: {}", ex.getMessage());
                        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
                    }
                
                    /**
                     * {@link DuplicateResourceException} → 409 Conflict.
                     */
                    @ExceptionHandler(DuplicateResourceException.class)
                    public ResponseEntity<ErrorResponse> handleDuplicateResource(
                            DuplicateResourceException ex, WebRequest request) {
                
                        log.warn("Duplicate resource: {}", ex.getMessage());
                        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
                    }
                
                    // ── Spring MVC / Validation exceptions ────────────────────────────
                
                    /**
                     * Bean validation failure ({@code @Valid} on request body) → 400.
                     *
                     * <p>Each field error is collected into {@code ErrorResponse.details}
                     * so the caller knows exactly which DTO fields are invalid.
                     */
                    @ExceptionHandler(MethodNotAllowedException.class)
                    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
                            MethodNotAllowedException ex, WebRequest request){
                
                        log.warn("Validation failed: {} field error(s)", ex.getBindingResult().getErrorCount());
                
                        List<String> fieldErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                                .collect(Collectors.toList());
                
                        ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Validation Failed",
                                "One or more fields failed validation",
                                extractPath(request)
                        );
                        errorResponse.addDetails(fieldErrors);
                
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                
                    /**
                     * Path/query variable type mismatch → 400.
                     *
                     * <p>Example: {@code /api/departments/abc} where {@code id} is {@code Long}.
                     */
                    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
                    public ResponseEntity<ErrorResponse> handleTypeMismatch(
                            MethodArgumentTypeMismatchException ex, WebRequest request) {
                
                        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
                
                        String message = String.format(
                                "Invalid value '%%s' for parameter '%%s'. Expected type: %%s",
                                ex.getValue(),
                                ex.getName(),
                                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
                        );
                        return build(HttpStatus.BAD_REQUEST, "Bad Request", message, request);
                    }
                
                    /**
                     * Malformed or unreadable JSON request body → 400.
                     *
                     * <p>Common in multi-entity APIs when callers send entity objects
                     * instead of ID fields (e.g. {@code "department": {...}} instead of
                     * {@code "departmentId": 5}).
                     */
                    @ExceptionHandler(HttpMessageNotReadableException.class)
                    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
                            HttpMessageNotReadableException ex, WebRequest request) {
                
                        log.warn("Unreadable HTTP message: {}", ex.getMessage());
                        return build(
                                HttpStatus.BAD_REQUEST,
                                "Bad Request",
                                "Malformed or unreadable JSON request body. "
                                + "Ensure relationship fields are provided as IDs, not nested objects.",
                                request
                        );
                    }
                
                    // ── Database constraint exceptions ────────────────────────────────
                
                    /**
                     * Database-level constraint violation → 409 Conflict.
                     *
                     * <p>Critical in multi-entity systems — covers:
                     * <ul>
                     *   <li>FK constraint failure (related entity ID does not exist in DB)</li>
                     *   <li>Unique constraint violation (duplicate email, username, etc.)</li>
                     *   <li>NOT NULL constraint (required FK column left null)</li>
                     *   <li>Cascade delete blocked by child records</li>
                     * </ul>
                     *
                     * <p>The root cause message is sanitised before returning to the client
                     * to avoid leaking internal table/column names.
                     */
                    @ExceptionHandler(DataIntegrityViolationException.class)
                    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
                            DataIntegrityViolationException ex, WebRequest request) {
                
                        log.error("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
                
                        // Determine a safe user-facing message based on constraint type
                        String rootMsg = ex.getMostSpecificCause().getMessage();
                        String userMessage;
                
                        if (rootMsg != null && rootMsg.toLowerCase().contains("unique")) {
                            userMessage = "A record with the same unique value already exists. "
                                        + "Check for duplicate fields (e.g. email, username).";
                        } else if (rootMsg != null && rootMsg.toLowerCase().contains("foreign key")) {
                            userMessage = "A referenced entity does not exist. "
                                        + "Ensure all relationship IDs (e.g. departmentId, roleIds) "
                                        + "refer to existing records.";
                        } else if (rootMsg != null && rootMsg.toLowerCase().contains("not null")) {
                            userMessage = "A required field or relationship is missing. "
                                        + "Ensure all mandatory IDs are provided in the request.";
                        } else {
                            userMessage = "A database constraint was violated. "
                                        + "Please verify your input data and try again.";
                        }
                
                        return build(HttpStatus.CONFLICT, "Conflict", userMessage, request);
                    }
                
                    // ── Routing exceptions ────────────────────────────────────────────
                
                    /**
                     * No handler mapped to the requested path → 404.
                     *
                     * <p>Requires {@code spring.mvc.throw-exception-if-no-handler-found=true}
                     * and {@code spring.web.resources.add-mappings=false} in
                     * {@code application.properties}.
                     */
                    @ExceptionHandler(NoHandlerFoundException.class)
                    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
                            NoHandlerFoundException ex, WebRequest request) {
                
                        log.warn("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());
                        return build(
                                HttpStatus.NOT_FOUND,
                                "Not Found",
                                "No endpoint found for " + ex.getHttpMethod() + " " + ex.getRequestURL(),
                                request
                        );
                    }
                
                    /**
                     * HTTP method not supported on the matched endpoint → 405.
                     */
                    @ExceptionHandler(HttpRequestMethodNotAllowedException.class)
                    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
                            HttpRequestMethodNotAllowedException ex, WebRequest request) {
                
                        log.warn("Method not allowed: {}", ex.getMessage());
                        return build(
                                HttpStatus.METHOD_NOT_ALLOWED,
                                "Method Not Allowed",
                                ex.getMessage(),
                                request
                        );
                    }
                
                    // ── Catch-all ─────────────────────────────────────────────────────
                
                    /**
                     * Any unhandled exception → 500 Internal Server Error.
                     *
                     * <p>The full stack trace is logged but never exposed to the client.
                     */
                    @ExceptionHandler(Exception.class)
                    public ResponseEntity<ErrorResponse> handleUnexpected(
                            Exception ex, WebRequest request) {
                
                        log.error("Unexpected error: ", ex);
                        return build(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal Server Error",
                                "An unexpected error occurred. Please try again later.",
                                request
                        );
                    }
                
                    // ── Private builder ───────────────────────────────────────────────
                
                    /**
                     * Constructs a {@link ResponseEntity} wrapping an {@link ErrorResponse}
                     * for simple (no field-level details) error cases.
                     */
                    private ResponseEntity<ErrorResponse> build(
                            HttpStatus status, String error, String message, WebRequest request) {
                
                        ErrorResponse body = new ErrorResponse(
                                status.value(), error, message, extractPath(request));
                        return new ResponseEntity<>(body, status);
                    }
                
                    /**
                     * Extracts the plain request path from a {@link WebRequest},
                     * stripping the {@code uri=} prefix added by Spring.
                     */
                    private String extractPath(WebRequest request) {
                        return request.getDescription(false).replace("uri=", "");
                    }
                }
                """, pkg, basePackage);

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