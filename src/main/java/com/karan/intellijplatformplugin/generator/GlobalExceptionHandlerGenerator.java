package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

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
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
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
                import org.springframework.web.server.MethodNotAllowedException;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.RestControllerAdvice;
                import org.springframework.web.context.request.WebRequest;
                import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
                import org.springframework.web.servlet.NoHandlerFoundException;
                
                import java.util.List;
                import java.util.stream.Collectors;
                
                @RestControllerAdvice
                public class GlobalExceptionHandler {
                
                    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
                
                    // ── Resource Not Found ─────────────────────────────
                    @ExceptionHandler(ResourceNotFoundException.class)
                    public ResponseEntity<ErrorResponse> handleResourceNotFound(
                            ResourceNotFoundException ex, WebRequest request) {
                
                        log.warn("Resource not found: {}", ex.getMessage());
                        return build(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request);
                    }
                
                    // ── Bad Request ─────────────────────────────
                    @ExceptionHandler(BadRequestException.class)
                    public ResponseEntity<ErrorResponse> handleBadRequest(
                            BadRequestException ex, WebRequest request) {
                
                        log.warn("Bad request: {}", ex.getMessage());
                        return build(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), request);
                    }
                
                    // ── Duplicate ─────────────────────────────
                    @ExceptionHandler(DuplicateResourceException.class)
                    public ResponseEntity<ErrorResponse> handleDuplicateResource(
                            DuplicateResourceException ex, WebRequest request) {
                
                        log.warn("Duplicate resource: {}", ex.getMessage());
                        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
                    }
                
                    // ── Validation ─────────────────────────────
                    @ExceptionHandler(MethodArgumentNotValidException.class)
                    public ResponseEntity<ErrorResponse> handleValidation(
                            MethodArgumentNotValidException ex, WebRequest request) {
                
                        log.warn("Validation failed");
                
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
                
                    // ── Type Mismatch ─────────────────────────────
                    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
                    public ResponseEntity<ErrorResponse> handleTypeMismatch(
                            MethodArgumentTypeMismatchException ex, WebRequest request) {
                
                        String message = String.format(
                                "Invalid value '%s' for parameter '%s'. Expected type: %s",
                                ex.getValue(),
                                ex.getName(),
                                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
                        );
                        return build(HttpStatus.BAD_REQUEST, "Bad Request", message, request);
                    }
                
                    // ── Malformed JSON ─────────────────────────────
                    @ExceptionHandler(HttpMessageNotReadableException.class)
                    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
                            HttpMessageNotReadableException ex, WebRequest request) {
                
                        return build(
                                HttpStatus.BAD_REQUEST,
                                "Bad Request",
                                "Malformed JSON request",
                                request
                        );
                    }
                
                    // ── DB Constraint ─────────────────────────────
                    @ExceptionHandler(DataIntegrityViolationException.class)
                    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
                            DataIntegrityViolationException ex, WebRequest request) {
                
                        return build(HttpStatus.CONFLICT, "Conflict", "Database constraint violated", request);
                    }
                
                    // ── 404 ─────────────────────────────
                    @ExceptionHandler(NoHandlerFoundException.class)
                    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
                            NoHandlerFoundException ex, WebRequest request) {
                
                        return build(
                                HttpStatus.NOT_FOUND,
                                "Not Found",
                                "No endpoint found",
                                request
                        );
                    }
                
                    // ── 405 (FIXED) ─────────────────────────────
                    @ExceptionHandler(MethodNotAllowedException.class)
                    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
                            MethodNotAllowedException ex, WebRequest request) {
                
                        return build(
                                HttpStatus.METHOD_NOT_ALLOWED,
                                "Method Not Allowed",
                                ex.getMessage(),
                                request
                        );
                    }
                
                    // ── 500 ─────────────────────────────
                    @ExceptionHandler(Exception.class)
                    public ResponseEntity<ErrorResponse> handleUnexpected(
                            Exception ex, WebRequest request) {
                
                        log.error("Unexpected error", ex);
                        return build(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal Server Error",
                                "Something went wrong",
                                request
                        );
                    }
                
                    // ── Helper ─────────────────────────────
                    private ResponseEntity<ErrorResponse> build(
                            HttpStatus status, String error, String message, WebRequest request) {
                
                        ErrorResponse body = new ErrorResponse(
                                status.value(), error, message, extractPath(request));
                        return new ResponseEntity<>(body, status);
                    }
                
                    private String extractPath(WebRequest request) {
                        return request.getDescription(false).replace("uri=", "");
                    }
                }
                """, pkg, basePackage);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
    }

    private static boolean fileExistsInPsiDirectory(PsiDirectory dir, String fileName) {
        if (dir == null || fileName == null) return false;
        for (PsiFile existing : dir.getFiles()) {
            if (fileName.equals(existing.getName())) return true;
        }
        return false;
    }
}