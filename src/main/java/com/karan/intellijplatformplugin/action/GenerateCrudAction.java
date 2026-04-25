package com.karan.intellijplatformplugin.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Action to generate complete CRUD code with optional security.
 * Supports multi-entity projects with JPA relationships.
 *
 * GENERATOR UPGRADE STATUS
 * ─────────────────────────────────────────────────────
 * ✅ ALL generators now use the NEW signature:
 *      (Project, PsiDirectory, ClassMeta, List<ClassMeta>, ...)
 *
 *      DtoGenerator, MapperGenerator, RepositoryGenerator,
 *      ServiceGenerator, ControllerGenerator,
 *      ExceptionGenerator, ErrorResponseGenerator,
 *      GlobalExceptionHandlerGenerator, PaginationGenerator,
 *      BaseAuditEntityGenerator,
 *      JwtServiceGenerator, JwtAuthenticationFilterGenerator,
 *      UserEntityGenerator, RoleEnumGenerator,
 *      AppUserRepositoryGenerator, UserDetailsServiceImplGenerator,
 *      AuthenticationServiceGenerator, AuthDtoGenerator,
 *      ApplicationPropertiesGenerator,
 *      SwaggerConfigGenerator, SwaggerReadmeGenerator,
 *      SecurityConfigGenerator, AuthControllerGenerator,
 *      SecurityReadmeGenerator, JpaAuditingConfigGenerator,
 *      AuditingReadmeGenerator
 * ─────────────────────────────────────────────────────
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

        if (securityChoice == Messages.CANCEL) return;

        boolean includeSecurity = (securityChoice == Messages.YES);

        // ================= DATABASE (OPTIONAL) =================
        String[] dbOptions = {"None", "MySQL", "PostgreSQL", "MongoDB", "H2"};

        int dbChoice = Messages.showChooseDialog(
                project,
                """
                Select Database (Optional):
                
                • None  → Configure later
                • MySQL / PostgreSQL → SQL DB
                • MongoDB → NoSQL
                • H2    → In-memory DB
                """,
                "Select Database",
                Messages.getQuestionIcon(),
                dbOptions,
                dbOptions[0]
        );

        if (dbChoice == -1) return;

        String selectedDb    = dbOptions[dbChoice];
        boolean isDbSelected = !Objects.equals(selectedDb, "None");

        try {
            // ================= PRIMARY ENTITY META =================
            ClassMeta meta = PsiDirectoryUtil.toClassMeta(psiClass);
            if (meta == null) {
                Messages.showErrorDialog(
                        project,
                        "Could not parse the selected class. " +
                                "Ensure the file compiles without errors and retry.",
                        "Parse Error"
                );
                return;
            }

            // ================= MULTI-ENTITY CONTEXT =================
            // ReadAction ensures we hold the PSI read lock when querying the index.
            List<ClassMeta> scannedEntities = ReadAction.compute(
                    () -> PsiDirectoryUtil.getAllEntityMetas(project)
            );

            List<ClassMeta> allEntities = new ArrayList<>(scannedEntities);

            // Guarantee the primary entity is always in the list even if the
            // PSI index hasn't flushed the current unsaved file yet.
            boolean primaryPresent = allEntities.stream()
                    .anyMatch(m -> m.getClassName().equals(meta.getClassName()));
            if (!primaryPresent) {
                allEntities.add(0, meta);
            }

            if (allEntities.isEmpty()) {
                allEntities = new ArrayList<>(List.of(meta));
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

            try {
                String basePath = project.getBasePath();
                if (basePath != null) {
                    Path gradleFile = Path.of(basePath, "build.gradle");
                    Path mavenFile  = Path.of(basePath, "pom.xml");

                    if (Files.exists(gradleFile)) {
                        reloadMessage = "Reload Gradle project (Ctrl+Shift+O)";
                    } else if (Files.exists(mavenFile)) {
                        reloadMessage = "Reload Maven project (Ctrl+Shift+M)";
                    }
                }
            } catch (Exception ignored) {}

            // ================= DEPENDENCY INJECTION =================
            try {
                DependencyManager.injectDependencies(project, includeSecurity, selectedDb);
            } catch (Exception depEx) {
                System.err.println("⚠️ Dependency injection failed: " + depEx.getMessage());
            }

            // Final captures for use inside the lambda.
            final ClassMeta       finalMeta        = meta;
            final List<ClassMeta> finalAllEntities = allEntities;
            final boolean         finalSecurity    = includeSecurity;

            // =========================================================
            // GENERATION
            // Each call is wrapped in runSafe() so that one broken
            // generator never aborts all subsequent generators.
            //
            // All generators now use the NEW unified signature:
            //   generate(project, sourceRoot, finalMeta, finalAllEntities, ...)
            // =========================================================
            WriteCommandAction.runWriteCommandAction(project, () -> {

                // --- Infrastructure / Config ---
                // ✅ NEW signature — allEntities passed
                runSafe("SwaggerConfig",  () -> SwaggerConfigGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities, finalSecurity));

                // ✅ NEW signature — allEntities passed
                runSafe("SwaggerReadme",  () -> SwaggerReadmeGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                // ✅ NEW signature
                runSafe("AppProperties",  () -> ApplicationPropertiesGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities, finalSecurity));

                // --- Security (optional) ---
                if (finalSecurity) {
                    // ✅ NEW signature — allEntities passed
                    runSafe("SecurityConfig",         () -> SecurityConfigGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("JwtService",             () -> JwtServiceGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("JwtAuthFilter",          () -> JwtAuthenticationFilterGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("UserEntity",             () -> UserEntityGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("RoleEnum",               () -> RoleEnumGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("AppUserRepository",      () -> AppUserRepositoryGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("UserDetailsServiceImpl", () -> UserDetailsServiceImplGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("AuthenticationService",  () -> AuthenticationServiceGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature — allEntities passed
                    runSafe("AuthController",         () -> AuthControllerGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature
                    runSafe("AuthDto",                () -> AuthDtoGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));

                    // ✅ NEW signature — allEntities passed
                    runSafe("SecurityReadme",         () -> SecurityReadmeGenerator.generate(
                            project, sourceRoot, finalMeta, finalAllEntities));
                }

                // --- Auditing ---
                // ✅ NEW signature
                runSafe("BaseAuditEntity",   () -> BaseAuditEntityGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                // ✅ NEW signature — allEntities inserted before finalSecurity
                runSafe("JpaAuditingConfig", () -> JpaAuditingConfigGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities, finalSecurity));

                // ✅ NEW signature — allEntities passed
                runSafe("AuditingReadme",    () -> AuditingReadmeGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                // --- Pagination ---
                // ✅ NEW signature
                runSafe("Pagination",        () -> PaginationGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                // --- Exception Handling ---
                // ✅ NEW signature
                runSafe("Exception",         () -> ExceptionGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                // ✅ NEW signature
                runSafe("ErrorResponse",     () -> ErrorResponseGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                // ✅ NEW signature
                runSafe("GlobalExHandler",   () -> GlobalExceptionHandlerGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                // --- Core CRUD Layers (all ✅ NEW signature) ---
                // DtoGenerator      → emits Long xxxId fields instead of entity refs
                // MapperGenerator   → skips relationship fields entirely
                // ServiceGenerator  → fetches related entities via their repositories
                // ControllerGenerator → standard CRUD wiring
                runSafe("Dto",        () -> DtoGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                runSafe("Mapper",     () -> MapperGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                runSafe("Repository", () -> RepositoryGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                runSafe("Service",    () -> ServiceGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));

                runSafe("Controller", () -> ControllerGenerator.generate(
                        project, sourceRoot, finalMeta, finalAllEntities));
            });

            // ================= SUCCESS DIALOG =================
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

            String securityMessage = finalSecurity ? """
                    🔒 Security Components:
                    ✓ JWT Authentication
                    ✓ Auth Controller
                    ✓ Security Config
                    """ : "";

            int    entityCount = finalAllEntities.size();
            String entityNames = finalAllEntities.stream()
                    .map(ClassMeta::getClassName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(finalMeta.getClassName());

            String entityContext = entityCount > 1
                    ? String.format("🔗 Multi-Entity Mode: %d entities resolved (%s)",
                    entityCount, entityNames)
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
                    
                    ⚠️ IMPORTANT: %s
                    
                    🌐 Swagger: http://localhost:8080/swagger-ui.html
                    """,
                    finalMeta.getClassName(),
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

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes a generator step, catching any exception so one broken
     * generator never prevents the rest from running.
     * Failures are written to stderr and visible in Help → Show Log.
     */
    private void runSafe(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            System.err.printf("⚠️ [CrudGenerator] %s failed: %s%n", name, ex.getMessage());
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
     * Checks for JPA {@code @Entity} on the class.
     * Covers both jakarta (Boot 3.x) and javax (Boot 2.x) namespaces.
     */
    private boolean isEntity(PsiClass psiClass) {
        return psiClass.hasAnnotation("jakarta.persistence.Entity") ||
                psiClass.hasAnnotation("javax.persistence.Entity");
    }
}