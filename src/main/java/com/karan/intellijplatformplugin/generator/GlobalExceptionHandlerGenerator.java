package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates Global Exception Handler with @ControllerAdvice.
 */
public class GlobalExceptionHandlerGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".exception";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import %s.dto.ErrorResponse;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.validation.FieldError;
                import org.springframework.web.bind.MethodArgumentNotValidException;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.RestControllerAdvice;
                import org.springframework.web.context.request.WebRequest;
                import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
                
                import java.util.stream.Collectors;
                
                /**
                 * Global exception handler for consistent error responses across the application.
                 */
                @RestControllerAdvice
                public class GlobalExceptionHandler {
                    
                    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
                    
                    /**
                     * Handles ResourceNotFoundException - 404 NOT FOUND
                     */
                    @ExceptionHandler(ResourceNotFoundException.class)
                    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
                            ResourceNotFoundException ex,
                            WebRequest request
                    ) {
                        log.error("Resource not found: {}", ex.getMessage());
                        
                        ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.NOT_FOUND.value(),
                                "Not Found",
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", "")
                        );
                        
                        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
                    }
                    
                    /**
                     * Handles BadRequestException - 400 BAD REQUEST
                     */
                    @ExceptionHandler(BadRequestException.class)
                    public ResponseEntity<ErrorResponse> handleBadRequestException(
                            BadRequestException ex,
                            WebRequest request
                    ) {
                        log.error("Bad request: {}", ex.getMessage());
                        
                        ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", "")
                        );
                        
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                    
                    /**
                     * Handles DuplicateResourceException - 409 CONFLICT
                     */
                    @ExceptionHandler(DuplicateResourceException.class)
                    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(
                            DuplicateResourceException ex,
                            WebRequest request
                    ) {
                        log.error("Duplicate resource: {}", ex.getMessage());
                        
                        ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.CONFLICT.value(),
                                "Conflict",
                                ex.getMessage(),
                                request.getDescription(false).replace("uri=", "")
                        );
                        
                        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
                    }
                    
                    /**
                     * Handles validation errors - 400 BAD REQUEST
                     */
                    @ExceptionHandler(MethodArgumentNotValidException.class)
                    public ResponseEntity<ErrorResponse> handleValidationException(
                            MethodArgumentNotValidException ex,
                            WebRequest request
                    ) {
                        log.error("Validation failed: {}", ex.getMessage());
                        
                        ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Validation Failed",
                                "Invalid input data",
                                request.getDescription(false).replace("uri=", "")
                        );
                        
                        // Add field-specific validation errors
                        errorResponse.setDetails(
                                ex.getBindingResult()
                                        .getFieldErrors()
                                        .stream()
                                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                        .collect(Collectors.toList())
                        );
                        
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                    
                    /**
                     * Handles type mismatch errors - 400 BAD REQUEST
                     */
                    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
                    public ResponseEntity<ErrorResponse> handleTypeMismatchException(
                            MethodArgumentTypeMismatchException ex,
                            WebRequest request
                    ) {
                        log.error("Type mismatch: {}", ex.getMessage());
                        
                        String message = String.format(
                                "Invalid value '%%s' for parameter '%%s'. Expected type: %%s",
                                ex.getValue(),
                                ex.getName(),
                                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
                        );
                        
                        ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                message,
                                request.getDescription(false).replace("uri=", "")
                        );
                        
                        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
                    }
                    
                    /**
                     * Handles all other exceptions - 500 INTERNAL SERVER ERROR
                     */
                    @ExceptionHandler(Exception.class)
                    public ResponseEntity<ErrorResponse> handleGlobalException(
                            Exception ex,
                            WebRequest request
                    ) {
                        log.error("Unexpected error occurred: ", ex);
                        
                        ErrorResponse errorResponse = new ErrorResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                "Internal Server Error",
                                "An unexpected error occurred. Please try again later.",
                                request.getDescription(false).replace("uri=", "")
                        );
                        
                        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }
                """, pkg, meta.basePackage());

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "GlobalExceptionHandler.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}