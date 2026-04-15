package com.karan.intellijplatformplugin.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.generator.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.DependencyManager;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import javax.swing.Icon;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

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

        // ================= SECURITY =================
        int securityChoice = Messages.showYesNoCancelDialog(
                project,
                """
                Do you want to include Spring Security with JWT authentication?
                
                Dependencies will be auto-added if missing.
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

        // ================= DATABASE (OPTIONAL) =================
        String[] dbOptions = {"None", "MySQL", "PostgreSQL", "MongoDB", "H2"};

        // FIX: showChooseDialog signature is:
        // (Object parentComponent, String message, String title,
        //  Icon icon, String[] values, String initialValue)
        int dbChoice = Messages.showChooseDialog(
                project,
                """
                Select Database (Optional):
                
                • None → Configure later
                • MySQL / PostgreSQL → SQL DB
                • MongoDB → NoSQL
                • H2 → In-memory DB
                """,
                "Select Database",
                Messages.getQuestionIcon(),   // Icon  ← was missing / wrong type
                dbOptions,                    // String[] values
                dbOptions[0]                  // String initialValue
        );

        if (dbChoice == -1) return;

        String selectedDb = dbOptions[dbChoice];
        boolean isDbSelected = !Objects.equals(selectedDb, "None");

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

            // ================= BUILD TOOL DETECTION =================
            String reloadMessage = "Reload your project";
            boolean isGradle = false;
            boolean isMaven = false;

            try {
                String basePath = project.getBasePath();
                if (basePath != null) {
                    Path gradleFile = Path.of(basePath, "build.gradle");
                    Path mavenFile  = Path.of(basePath, "pom.xml");

                    if (Files.exists(gradleFile)) {
                        reloadMessage = "Reload Gradle project";
                        isGradle = true;
                    } else if (Files.exists(mavenFile)) {
                        reloadMessage = "Reload Maven project";
                        isMaven = true;
                    }
                }
            } catch (Exception ignored) {}

            // ================= DEPENDENCY INJECTION =================
            try {
                DependencyManager.injectDependencies(project, includeSecurity, selectedDb);
            } catch (Exception depEx) {
                System.out.println("⚠️ Dependency injection failed: " + depEx.getMessage());
            }

            // ================= GENERATION =================
            WriteCommandAction.runWriteCommandAction(project, () -> {
                SwaggerConfigGenerator.generate(project, sourceRoot, meta, includeSecurity);
                SwaggerReadmeGenerator.generate(project, sourceRoot, meta);
                ApplicationPropertiesGenerator.generate(project, sourceRoot, meta, includeSecurity);

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

                BaseAuditEntityGenerator.generate(project, sourceRoot, meta);
                JpaAuditingConfigGenerator.generate(project, sourceRoot, meta, includeSecurity);
                AuditingReadmeGenerator.generate(project, sourceRoot, meta);

                PaginationGenerator.generate(project, sourceRoot, meta);

                ExceptionGenerator.generate(project, sourceRoot, meta);
                ErrorResponseGenerator.generate(project, sourceRoot, meta);
                GlobalExceptionHandlerGenerator.generate(project, sourceRoot, meta);

                DtoGenerator.generate(project, sourceRoot, meta);
                MapperGenerator.generate(project, sourceRoot, meta);
                RepositoryGenerator.generate(project, sourceRoot, meta);
                ServiceGenerator.generate(project, sourceRoot, meta);
                ControllerGenerator.generate(project, sourceRoot, meta);
            });

            // ================= SMART DB WARNING =================
            String dbWarning;

            if (!isDbSelected) {
                dbWarning = """
                        
                        ⚠️ DATABASE NOT CONFIGURED
                        
                        Configure DB manually later (MySQL / PostgreSQL / H2)
                        """;
            } else if (selectedDb.equals("MongoDB")) {
                dbWarning = """
                        
                        🍃 MongoDB Selected
                        ✔ Mongo dependency added
                        ⚠ JPA will not be used
                        """;
            } else {
                dbWarning = "🛢️ " + selectedDb + " driver added successfully";
            }

            String securityMessage = includeSecurity ? """
                    
                    🔒 Security Components:
                    ✓ JWT Authentication
                    ✓ Auth Controller
                    ✓ Security Config
                    """ : "";

            String message = String.format("""
                    ✅ Successfully generated CRUD for %s
                    
                    ✓ Swagger + OpenAPI
                    ✓ JPA / Mongo
                    ✓ Pagination
                    ✓ Exception Handling
                    ✓ Full CRUD Layers
                    %s
                    
                    %s
                    
                    ⚠️ IMPORTANT:
                    %s
                    
                    🌐 Swagger:
                    http://localhost:8080/swagger-ui.html
                    """,
                    meta.getClassName(),
                    securityMessage,
                    dbWarning,
                    reloadMessage
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