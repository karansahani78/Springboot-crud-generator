package com.karan.intellijplatformplugin.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.generator.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Action to generate complete CRUD code with optional security.
 */
public class GenerateCrudAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        if (project == null) {
            Messages.showErrorDialog("No project found", "Error");
            return;
        }

        if (!(file instanceof PsiJavaFile)) {
            Messages.showErrorDialog(
                    "Please select a Java class file",
                    "Invalid Selection"
            );
            return;
        }

        PsiJavaFile javaFile = (PsiJavaFile) file;
        PsiClass[] classes = javaFile.getClasses();

        if (classes.length == 0) {
            Messages.showErrorDialog(
                    "No class found in the selected file",
                    "Invalid Selection"
            );
            return;
        }

        PsiClass psiClass = classes[0];

        if (!isEntity(psiClass)) {
            int result = Messages.showYesNoDialog(
                    project,
                    "The selected class does not have @Entity annotation. Continue anyway?",
                    "Not an Entity",
                    Messages.getQuestionIcon()
            );
            if (result != Messages.YES) {
                return;
            }
        }

        // Ask user if they want to include Spring Security
        int securityChoice = Messages.showYesNoCancelDialog(
                project,
                """
                Do you want to include Spring Security with JWT authentication?
                
                This will generate:
                • JWT-based authentication
                • User registration endpoint (/api/auth/register)
                • User login endpoint (/api/auth/login)
                • Role-based authorization (USER, ADMIN, MODERATOR)
                • Password encryption with BCrypt
                • Protected API endpoints
                
                Note: You'll need to add Spring Security & JWT dependencies.
                """,
                "Include Spring Security?",
                "Yes, Include Security",
                "No, Skip Security",
                "Cancel",
                Messages.getQuestionIcon()
        );

        if (securityChoice == Messages.CANCEL) {
            return;
        }

        boolean includeSecurity = (securityChoice == Messages.YES);

        try {
            ClassMeta meta = PsiDirectoryUtil.toClassMeta(psiClass);

            PsiDirectory sourceRoot = PsiDirectoryUtil.getSourceRoot(file);
            if (sourceRoot == null) {
                Messages.showErrorDialog(
                        "Cannot locate source root (src/main/java)",
                        "Error"
                );
                return;
            }

            WriteCommandAction.runWriteCommandAction(project, () -> {
                // Generate Swagger/OpenAPI documentation
                SwaggerConfigGenerator.generate(project, sourceRoot, meta, includeSecurity); // ✅ FIXED: Added includeSecurity parameter
                SwaggerReadmeGenerator.generate(project, sourceRoot, meta);
                ApplicationPropertiesGenerator.generate(project, sourceRoot, meta, includeSecurity);

                // Generate Spring Security (OPTIONAL)
                if (includeSecurity) {
                    SecurityConfigGenerator.generate(project, sourceRoot, meta);
                    JwtServiceGenerator.generate(project, sourceRoot, meta);
                    JwtAuthenticationFilterGenerator.generate(project, sourceRoot, meta);
                    UserEntityGenerator.generate(project, sourceRoot, meta);
                    RoleEnumGenerator.generate(project, sourceRoot, meta);
                    AppUserRepositoryGenerator.generate(project, sourceRoot, meta);
                    UserDetailsServiceImplGenerator.generate(project, sourceRoot, meta);
                    AuthenticationServiceGenerator.generate(project, sourceRoot, meta);
                    AuthControllerGenerator.generate(project, sourceRoot, meta);
                    AuthDtoGenerator.generate(project, sourceRoot, meta);
                    SecurityReadmeGenerator.generate(project, sourceRoot, meta);
                }

                // Generate auditing support (integrated with Security if enabled)
                BaseAuditEntityGenerator.generate(project, sourceRoot, meta);
                JpaAuditingConfigGenerator.generate(project, sourceRoot, meta, includeSecurity);
                AuditingReadmeGenerator.generate(project, sourceRoot, meta);

                // Generate pagination support
                PaginationGenerator.generate(project, sourceRoot, meta);

                // Generate exception handling
                ExceptionGenerator.generate(project, sourceRoot, meta);
                ErrorResponseGenerator.generate(project, sourceRoot, meta);
                GlobalExceptionHandlerGenerator.generate(project, sourceRoot, meta);

                // Generate CRUD components
                DtoGenerator.generate(project, sourceRoot, meta);
                MapperGenerator.generate(project, sourceRoot, meta);
                RepositoryGenerator.generate(project, sourceRoot, meta);
                ServiceGenerator.generate(project, sourceRoot, meta);
                ControllerGenerator.generate(project, sourceRoot, meta);
            });

            // Build success message based on what was generated
            String securityMessage = includeSecurity ? """
                    
                    🔒 Security Components:
                    ✓ Spring Security Configuration (JWT)
                    ✓ Authentication Controller (Register/Login)
                    ✓ JWT Service & Filter
                    ✓ User Entity with Roles
                    ✓ User Repository & UserDetailsService
                    ✓ Security Setup Guide
                    
                    📄 Public Endpoints:
                    • POST /api/auth/register - Register new user
                    • POST /api/auth/login - Login
                    
                    🔒 Protected Endpoints (requires JWT token):
                    """ : """
                    
                    📄 API Endpoints (No Authentication):
                    """;

            String message = String.format("""
                    Successfully generated CRUD code for %s:
                    
                    ✓ Swagger Configuration
                    ✓ OpenAPI Documentation
                    ✓ JPA Auditing (CreatedAt, UpdatedAt, CreatedBy, UpdatedBy)
                    ✓ Pagination Support (PageResponse, SortDirection)
                    ✓ Custom Exceptions
                    ✓ Error Response DTO
                    ✓ Global Exception Handler
                    ✓ DTO with Validation
                    ✓ Mapper
                    ✓ Repository
                    ✓ Service (with pagination)
                    ✓ Controller (with paginated endpoint)
                    ✓ Complete Documentation
                    %s
                    • GET /api/%s - Get all
                    • GET /api/%s/paginated - Get paginated & sorted
                    • GET /api/%s/{id} - Get by ID
                    • POST /api/%s - Create
                    • PUT /api/%s/{id} - Update
                    • DELETE /api/%s/{id} - Delete
                    • HEAD /api/%s/{id} - Check exists
                    • GET /api/%s/count - Count all
                    
                    📝 Next Steps:
                    %s
                    
                    Access Swagger UI at: http://localhost:8080/swagger-ui.html
                    """,
                    meta.getClassName(),
                    securityMessage,
                    meta.getClassName().toLowerCase(),
                    meta.getClassName().toLowerCase(),
                    meta.getClassName().toLowerCase(),
                    meta.getClassName().toLowerCase(),
                    meta.getClassName().toLowerCase(),
                    meta.getClassName().toLowerCase(),
                    meta.getClassName().toLowerCase(),
                    meta.getClassName().toLowerCase(),
                    includeSecurity ?
                            "1. Add Spring Security & JWT dependencies to pom.xml\n" +
                                    "    2. Check SECURITY_GUIDE.md for complete setup\n" +
                                    "    3. Register a user at /api/auth/register\n" +
                                    "    4. Use the token in Authorization header" :
                            "1. Your endpoints are publicly accessible\n" +
                                    "    2. Consider adding security later if needed\n" +
                                    "    3. Check generated documentation"
            );

            Messages.showInfoMessage(project, message, "Spring Boot CRUD Generator");

        } catch (Exception ex) {
            Messages.showErrorDialog(
                    project,
                    "Failed to generate CRUD code: " + ex.getMessage(),
                    "Generation Error"
            );
            ex.printStackTrace();
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        Project project = e.getProject();

        PsiJavaFile javaFile = null;

        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (psiFile instanceof PsiJavaFile) {
            javaFile = (PsiJavaFile) psiFile;
        }

        if (javaFile == null) {
            PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
            if (element instanceof PsiJavaFile) {
                javaFile = (PsiJavaFile) element;
            } else if (element instanceof PsiClass) {
                PsiFile containingFile = element.getContainingFile();
                if (containingFile instanceof PsiJavaFile) {
                    javaFile = (PsiJavaFile) containingFile;
                }
            }
        }

        if (javaFile == null && project != null) {
            VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if (vf != null) {
                PsiFile fileFromVf = PsiManager.getInstance(project).findFile(vf);
                if (fileFromVf instanceof PsiJavaFile) {
                    javaFile = (PsiJavaFile) fileFromVf;
                }
            }
        }

        boolean enabled = javaFile != null && javaFile.getClasses().length > 0;
        presentation.setEnabledAndVisible(enabled);
    }

    private boolean isEntity(PsiClass psiClass) {
        return psiClass.hasAnnotation("jakarta.persistence.Entity") ||
                psiClass.hasAnnotation("javax.persistence.Entity");
    }
}