package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates custom exception classes for consistent error handling.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>PSI directory guard added to every sub-generator — prevents
 *       {@code dir.add()} collision within a single {@code WriteCommandAction}
 *       when multiple entities are generated in the same run.</li>
 *   <li>Directory is resolved once and passed to sub-generators — avoids
 *       repeated {@code createPackageDirs()} calls for the same package.</li>
 * </ul>
 */
public class ExceptionGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".exception";

        // Resolve the directory once — all three exception classes land here
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        generateResourceNotFoundException(project, dir, root, pkg, meta);
        generateBadRequestException(project, dir, root, pkg, meta);
        generateDuplicateResourceException(project, dir, root, pkg, meta);
    }

    // =========================================================================
    // ResourceNotFoundException
    // =========================================================================

    private static void generateResourceNotFoundException(
            Project project,
            PsiDirectory dir,
            PsiDirectory root,
            String pkg,
            ClassMeta meta
    ) {
        String fileName = "ResourceNotFoundException.java";

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
                
                import org.springframework.http.HttpStatus;
                import org.springframework.web.bind.annotation.ResponseStatus;
                
                /**
                 * Thrown when a requested resource cannot be found in the data store.
                 *
                 * <p>Maps to HTTP {@code 404 Not Found}.
                 *
                 * <p>In multi-entity systems this is also thrown by service methods when
                 * a related entity referenced by ID in the DTO does not exist.
                 *
                 * <p>Usage:
                 * <pre>
                 *   repository.findById(id)
                 *       .orElseThrow(() -> new ResourceNotFoundException(entityName, "id", id));
                 * </pre>
                 */
                @ResponseStatus(HttpStatus.NOT_FOUND)
                public class ResourceNotFoundException extends RuntimeException {
                
                    private final String resourceName;
                    private final String fieldName;
                    private final Object fieldValue;
                
                    /**
                     * Full constructor — generates a descriptive message automatically.
                     *
                     * @param resourceName simple entity name, e.g. {@code "User"}
                     * @param fieldName    name of the lookup field, e.g. {@code "id"}
                     * @param fieldValue   value that was not found, e.g. {@code 42L}
                     */
                    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
                        super(String.format("%%s not found with %%s : '%%s'", resourceName, fieldName, fieldValue));
                        this.resourceName = resourceName;
                        this.fieldName    = fieldName;
                        this.fieldValue   = fieldValue;
                    }
                
                    /**
                     * Simple constructor for custom messages.
                     *
                     * @param message human-readable error message
                     */
                    public ResourceNotFoundException(String message) {
                        super(message);
                        this.resourceName = null;
                        this.fieldName    = null;
                        this.fieldValue   = null;
                    }
                
                    public String getResourceName() { return resourceName; }
                    public String getFieldName()    { return fieldName; }
                    public Object getFieldValue()   { return fieldValue; }
                }
                """, pkg);

        addFile(project, dir, fileName, code);
    }

    // =========================================================================
    // BadRequestException
    // =========================================================================

    private static void generateBadRequestException(
            Project project,
            PsiDirectory dir,
            PsiDirectory root,
            String pkg,
            ClassMeta meta
    ) {
        String fileName = "BadRequestException.java";

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
                
                import org.springframework.http.HttpStatus;
                import org.springframework.web.bind.annotation.ResponseStatus;
                
                /**
                 * Thrown when an incoming request contains semantically invalid data.
                 *
                 * <p>Maps to HTTP {@code 400 Bad Request}.
                 *
                 * <p>Common triggers in multi-entity systems:
                 * <ul>
                 *   <li>Null ID supplied where a related entity ID is required</li>
                 *   <li>Null DTO passed to a service create/update method</li>
                 *   <li>Invalid pagination parameters (negative page, zero size)</li>
                 * </ul>
                 */
                @ResponseStatus(HttpStatus.BAD_REQUEST)
                public class BadRequestException extends RuntimeException {
                
                    /**
                     * @param message human-readable description of why the request is invalid
                     */
                    public BadRequestException(String message) {
                        super(message);
                    }
                
                    /**
                     * @param message human-readable description of why the request is invalid
                     * @param cause   the underlying cause (e.g. a parsing exception)
                     */
                    public BadRequestException(String message, Throwable cause) {
                        super(message, cause);
                    }
                }
                """, pkg);

        addFile(project, dir, fileName, code);
    }

    // =========================================================================
    // DuplicateResourceException
    // =========================================================================

    private static void generateDuplicateResourceException(
            Project project,
            PsiDirectory dir,
            PsiDirectory root,
            String pkg,
            ClassMeta meta
    ) {
        String fileName = "DuplicateResourceException.java";

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
                
                import org.springframework.http.HttpStatus;
                import org.springframework.web.bind.annotation.ResponseStatus;
                
                /**
                 * Thrown when attempting to create a resource that already exists
                 * (unique constraint violation at the business logic level).
                 *
                 * <p>Maps to HTTP {@code 409 Conflict}.
                 *
                 * <p>Note: database-level unique constraint violations are caught separately
                 * by {@code GlobalExceptionHandler.handleDataIntegrityViolation()} so this
                 * exception is for explicit business-rule duplicate checks in service methods.
                 */
                @ResponseStatus(HttpStatus.CONFLICT)
                public class DuplicateResourceException extends RuntimeException {
                
                    private final String resourceName;
                    private final String fieldName;
                    private final Object fieldValue;
                
                    /**
                     * @param resourceName simple entity name, e.g. {@code "User"}
                     * @param fieldName    name of the duplicate field, e.g. {@code "email"}
                     * @param fieldValue   the duplicate value, e.g. {@code "admin@example.com"}
                     */
                    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
                        super(String.format("%%s already exists with %%s : '%%s'", resourceName, fieldName, fieldValue));
                        this.resourceName = resourceName;
                        this.fieldName    = fieldName;
                        this.fieldValue   = fieldValue;
                    }
                
                    public String getResourceName() { return resourceName; }
                    public String getFieldName()    { return fieldName; }
                    public Object getFieldValue()   { return fieldValue; }
                }
                """, pkg);

        addFile(project, dir, fileName, code);
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private static void addFile(
            Project project,
            PsiDirectory dir,
            String fileName,
            String code
    ) {
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