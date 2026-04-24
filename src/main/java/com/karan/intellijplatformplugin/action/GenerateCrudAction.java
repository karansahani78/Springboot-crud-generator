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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Action to generate complete CRUD code with optional security.
 * Supports multi-entity projects with JPA relationships.
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
                Messages.getQuestionIcon(),
                dbOptions,
                dbOptions[0]
        );

        if (dbChoice == -1) return;

        String selectedDb = dbOptions[dbChoice];
        boolean isDbSelected = !Objects.equals(selectedDb, "None");

        try {
            // ================= PRIMARY ENTITY META =================
            // Parse the selected entity as the "current" entity being generated
            ClassMeta meta = PsiDirectoryUtil.toClassMeta(psiClass);

            // ================= MULTI-ENTITY CONTEXT =================
            // Scan the entire project for all @Entity classes.
            // This list is passed to every generator so that:
            //   - DTOs can reference related entity IDs instead of entity objects
            //   - Services can inject the correct repositories
            //   - Mappers can resolve relationships correctly
            List<ClassMeta> allEntities = PsiDirectoryUtil.getAllEntityMetas(project);

            // If the scan found nothing (e.g., PSI index not ready), fall back
            // to just the selected entity so generation still succeeds.
            if (allEntities.isEmpty()) {
                allEntities = List.of(meta);
            }

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

            // Capture final references for use inside lambda
            final List<ClassMeta> finalAllEntities = allEntities;

            // ================= GENERATION =================
            // Every generator now receives (project, sourceRoot, meta, finalAllEntities, includeSecurity?)
            // so that each layer can resolve cross-entity relationships.
            WriteCommandAction.runWriteCommandAction(project, () -> {

                // --- Infrastructure / Config ---
                SwaggerConfigGenerator.generate(project, sourceRoot, meta, finalAllEntities, includeSecurity);
                SwaggerReadmeGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                ApplicationPropertiesGenerator.generate(project, sourceRoot, meta, finalAllEntities, includeSecurity);

                // --- Security (optional) ---
                if (includeSecurity) {
                    SecurityConfigGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    JwtServiceGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    JwtAuthenticationFilterGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    UserEntityGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    RoleEnumGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    AppUserRepositoryGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    UserDetailsServiceImplGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    AuthenticationServiceGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    AuthControllerGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    AuthDtoGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                    SecurityReadmeGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                }

                // --- Auditing ---
                BaseAuditEntityGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                JpaAuditingConfigGenerator.generate(project, sourceRoot, meta, finalAllEntities, includeSecurity);
                AuditingReadmeGenerator.generate(project, sourceRoot, meta, finalAllEntities);

                // --- Pagination ---
                PaginationGenerator.generate(project, sourceRoot, meta, finalAllEntities);

                // --- Exception Handling ---
                ExceptionGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                ErrorResponseGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                GlobalExceptionHandlerGenerator.generate(project, sourceRoot, meta, finalAllEntities);

                // --- Core CRUD Layers ---
                // These are the critical generators that use allEntities to resolve
                // relationships: DTO uses IDs, Mapper skips relations,
                // Service fetches related entities via their repositories.
                DtoGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                MapperGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                RepositoryGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                ServiceGenerator.generate(project, sourceRoot, meta, finalAllEntities);
                ControllerGenerator.generate(project, sourceRoot, meta, finalAllEntities);
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

            // Show how many entities were discovered so the user knows
            // multi-entity context was active during generation.
            int entityCount = finalAllEntities.size();
            String entityContext = entityCount > 1
                    ? String.format("🔗 Multi-Entity Mode: %d entities resolved", entityCount)
                    : "📦 Single-Entity Mode";

            String message = String.format("""
                    ✅ Successfully generated CRUD for %s
                    
                    %s
                    
                    ✓ Swagger + OpenAPI
                    ✓ JPA / Mongo
                    ✓ Pagination
                    ✓ Exception Handling
                    ✓ Full CRUD Layers
                    ✓ Relationship-Aware DTOs
                    ✓ Auto Repository Injection
                    %s
                    
                    %s
                    
                    ⚠️ IMPORTANT:
                    %s
                    
                    🌐 Swagger:
                    http://localhost:8080/swagger-ui.html
                    """,
                    meta.getClassName(),
                    entityContext,
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

    /**
     * Checks whether the given PSI class carries a JPA @Entity annotation.
     * Both jakarta.persistence and javax.persistence are supported for
     * compatibility with Spring Boot 2.x and 3.x.
     */
    private boolean isEntity(PsiClass psiClass) {
        return psiClass.hasAnnotation("jakarta.persistence.Entity") ||
                psiClass.hasAnnotation("javax.persistence.Entity");
    }
}