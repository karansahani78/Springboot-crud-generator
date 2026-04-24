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
            // Parse the selected entity first. Guard against null — PSI can
            // return null if the file has unresolved symbols at parse time.
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
            // Wrap inside ReadAction so PSI index queries are executed on a
            // thread that holds the read lock — safe from EDT and background.
            List<ClassMeta> scannedEntities = ReadAction.compute(
                    () -> PsiDirectoryUtil.getAllEntityMetas(project)
            );

            // Build a mutable list so we can guarantee the primary entity is
            // always present (the PSI index may not have flushed unsaved changes).
            List<ClassMeta> allEntities = new ArrayList<>(scannedEntities);

            boolean primaryEntityPresent = allEntities.stream()
                    .anyMatch(m -> m.getClassName().equals(meta.getClassName()));

            if (!primaryEntityPresent) {
                // Insert at front so it's the "current" entity for generators
                // that care about ordering.
                allEntities.add(0, meta);
            }

            // Last-resort fallback: scan returned nothing at all.
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
            boolean isGradle = false;
            boolean isMaven  = false;

            try {
                String basePath = project.getBasePath();
                if (basePath != null) {
                    Path gradleFile = Path.of(basePath, "build.gradle");
                    Path mavenFile  = Path.of(basePath, "pom.xml");

                    if (Files.exists(gradleFile)) {
                        reloadMessage = "Reload Gradle project (Ctrl+Shift+O)";
                        isGradle = true;
                    } else if (Files.exists(mavenFile)) {
                        reloadMessage = "Reload Maven project (Ctrl+Shift+M)";
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

            // Capture final references for lambda (compiler requirement).
            final ClassMeta       finalMeta        = meta;
            final List<ClassMeta> finalAllEntities = allEntities;
            final boolean         finalSecurity    = includeSecurity;

            // ================= GENERATION =================
            // Each generator call is wrapped individually so that one failure
            // does NOT abort all subsequent generators — partial generation is
            // always better than a hard stop mid-way.
            WriteCommandAction.runWriteCommandAction(project, () -> {

                // --- Infrastructure / Config ---
                runSafe("SwaggerConfig",     () -> SwaggerConfigGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities, finalSecurity));
                runSafe("SwaggerReadme",     () -> SwaggerReadmeGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("AppProperties",     () -> ApplicationPropertiesGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities, finalSecurity));

                // --- Security (optional) ---
                if (finalSecurity) {
                    runSafe("SecurityConfig",          () -> SecurityConfigGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("JwtService",              () -> JwtServiceGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("JwtAuthFilter",           () -> JwtAuthenticationFilterGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("UserEntity",              () -> UserEntityGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("RoleEnum",                () -> RoleEnumGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("AppUserRepository",       () -> AppUserRepositoryGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("UserDetailsServiceImpl",  () -> UserDetailsServiceImplGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("AuthenticationService",   () -> AuthenticationServiceGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("AuthController",          () -> AuthControllerGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("AuthDto",                 () -> AuthDtoGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                    runSafe("SecurityReadme",          () -> SecurityReadmeGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                }

                // --- Auditing ---
                runSafe("BaseAuditEntity",    () -> BaseAuditEntityGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("JpaAuditingConfig",  () -> JpaAuditingConfigGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities, finalSecurity));
                runSafe("AuditingReadme",     () -> AuditingReadmeGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));

                // --- Pagination ---
                runSafe("Pagination",         () -> PaginationGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));

                // --- Exception Handling ---
                runSafe("Exception",          () -> ExceptionGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("ErrorResponse",      () -> ErrorResponseGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("GlobalExHandler",    () -> GlobalExceptionHandlerGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));

                // --- Core CRUD Layers ---
                // These are the most relationship-sensitive generators:
                //   DtoGenerator      → emits Long xxxId fields instead of entity refs
                //   MapperGenerator   → skips relationship fields
                //   ServiceGenerator  → fetches related entities via their repositories
                //   ControllerGenerator → wires everything together
                runSafe("Dto",         () -> DtoGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("Mapper",      () -> MapperGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("Repository",  () -> RepositoryGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("Service",     () -> ServiceGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
                runSafe("Controller",  () -> ControllerGenerator.generate(project, sourceRoot, finalMeta, finalAllEntities));
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
            int    entityCount  = finalAllEntities.size();
            String entityNames  = finalAllEntities.stream()
                    .map(ClassMeta::getClassName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(finalMeta.getClassName());

            String entityContext = entityCount > 1
                    ? String.format("🔗 Multi-Entity Mode: %d entities resolved (%s)", entityCount, entityNames)
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
     * Executes a single generator step inside a try/catch so that one broken
     * generator never prevents the rest from running.  Failures are surfaced
     * in the IDE log (Help → Show Log) rather than swallowed silently.
     *
     * @param generatorName human-readable label used in log messages
     * @param task          the generator lambda to execute
     */
    private void runSafe(String generatorName, Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            // Log to the IDE console so developers can diagnose without crashing.
            System.err.printf(
                    "⚠️ [CrudGenerator] %s failed: %s%n",
                    generatorName, ex.getMessage()
            );
            ex.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

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
     * Returns true if the PSI class carries a JPA {@code @Entity} annotation.
     * Both {@code jakarta.persistence} and {@code javax.persistence} are checked
     * so that Spring Boot 2.x (javax) and 3.x (jakarta) projects are both covered.
     */
    private boolean isEntity(PsiClass psiClass) {
        return psiClass.hasAnnotation("jakarta.persistence.Entity") ||
                psiClass.hasAnnotation("javax.persistence.Entity");
    }
}