package com.karan.intellijplatformplugin.service;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.karan.intellijplatformplugin.generator.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.GenerationOptions;
import com.karan.intellijplatformplugin.model.GenerationPlan;
import com.karan.intellijplatformplugin.model.GenerationTask;
import com.karan.intellijplatformplugin.model.GenerationTaskResult;
import com.karan.intellijplatformplugin.util.DependencyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared orchestration over the existing generator classes.
 *
 * <p>Both the original entity action and prompt mode call this service, so
 * both workflows converge on the same generators. No generator implementation
 * was changed.
 */
public final class CrudGenerationService {

    private CrudGenerationService() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Exactly reproduces the original GenerateCrudAction generator sequence.
     * Used by the legacy action so its generated output is unchanged.
     */
    public static List<GenerationTaskResult> generateLegacy(
            Project project,
            PsiDirectory sourceRoot,
            ClassMeta meta,
            List<ClassMeta> allEntities,
            boolean includeSecurity
    ) {
        List<GenerationTaskResult> results = new ArrayList<>();

        run(results, "SwaggerConfig", "Swagger Configuration", () ->
                SwaggerConfigGenerator.generate(project, sourceRoot, meta, allEntities, includeSecurity));
        run(results, "SwaggerReadme", "Swagger Documentation", () ->
                SwaggerReadmeGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "properties", "application.properties", () ->
                ApplicationPropertiesGenerator.generate(project, sourceRoot, meta, allEntities, includeSecurity));

        if (includeSecurity) {
            run(results, "security:config", "SecurityConfig", () ->
                    SecurityConfigGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:jwt", "JwtService", () ->
                    JwtServiceGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:filter", "JwtAuthenticationFilter", () ->
                    JwtAuthenticationFilterGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:user", "AppUser", () ->
                    UserEntityGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:role", "Role", () ->
                    RoleEnumGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:repository", "AppUserRepository", () ->
                    AppUserRepositoryGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:details", "UserDetailsServiceImpl", () ->
                    UserDetailsServiceImplGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:auth-service", "AuthenticationService", () ->
                    AuthenticationServiceGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:auth-controller", "AuthenticationController", () ->
                    AuthControllerGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:auth-dto", "Authentication DTOs", () ->
                    AuthDtoGenerator.generate(project, sourceRoot, meta, allEntities));
            run(results, "security:readme", "Security Guide", () ->
                    SecurityReadmeGenerator.generate(project, sourceRoot, meta, allEntities));
        }

        run(results, "auditing:base", "BaseAuditEntity", () ->
                BaseAuditEntityGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "auditing:config", "JpaAuditingConfig", () ->
                JpaAuditingConfigGenerator.generate(project, sourceRoot, meta, allEntities, includeSecurity));
        run(results, "auditing:readme", "Auditing Guide", () ->
                AuditingReadmeGenerator.generate(project, sourceRoot, meta, allEntities));

        run(results, "pagination", "Pagination", () ->
                PaginationGenerator.generate(project, sourceRoot, meta, allEntities));

        run(results, "exception-handling:exceptions", "Exception classes", () ->
                ExceptionGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "exception-handling:error", "ErrorResponse", () ->
                ErrorResponseGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "exception-handling:handler", "GlobalExceptionHandler", () ->
                GlobalExceptionHandlerGenerator.generate(project, sourceRoot, meta, allEntities));

        run(results, "dto", "DTO", () ->
                DtoGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "mapper", "Mapper", () ->
                MapperGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "repository", "Repository", () ->
                RepositoryGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "service", "Service", () ->
                ServiceGenerator.generate(project, sourceRoot, meta, allEntities));
        run(results, "controller", "Controller", () ->
                ControllerGenerator.generate(project, sourceRoot, meta, allEntities));

        return results;
    }

    /**
     * Executes only the enabled tasks of a prompt generation plan.
     *
     * @param decisions per-task conflict decisions from {@link ChangePreviewService}
     */
    public static List<GenerationTaskResult> generate(
            Project project,
            PsiDirectory sourceRoot,
            ClassMeta meta,
            List<ClassMeta> allEntities,
            GenerationOptions options,
            String databaseType,
            GenerationPlan plan,
            Map<String, ChangePreviewService.Decision> decisions
    ) {
        List<GenerationTaskResult> results = new ArrayList<>();

        if (options.isInjectDependencies()) {
            try {
                DependencyManager.injectDependencies(project, options.isIncludeSecurity(), databaseType);
                results.add(GenerationTaskResult.success("dependencies", "Inject Dependencies"));
            } catch (Exception ex) {
                results.add(GenerationTaskResult.failed("dependencies", "Inject Dependencies", ex));
            }
        } else {
            results.add(GenerationTaskResult.skipped("dependencies", "Inject Dependencies", "Disabled in plan"));
        }

        for (GenerationTask task : plan.orderedTasks()) {
            if (task.getId().equals("dependencies")) continue; // handled once above
            if (!task.isEnabled()) {
                results.add(GenerationTaskResult.skipped(task.getId(), task.getDisplayName(), "Disabled in plan"));
                continue;
            }

            ChangePreviewService.Decision decision = decisions.get(task.getId());
            if (decision == ChangePreviewService.Decision.SKIP) {
                results.add(GenerationTaskResult.skipped(task.getId(), task.getDisplayName(),
                        "Existing file skipped by developer"));
                continue;
            }
            if (decision == ChangePreviewService.Decision.REPLACE
                    && !task.getId().equals("entity")
                    && !task.getId().equals("validation")) {
                deleteExisting(project, sourceRoot, meta, task.getId(), results);
            }

            switch (task.getId()) {
                case "entity" -> results.add(GenerationTaskResult.success(
                        "entity", "Entity", "Created before the shared generation pipeline"));
                case "validation" -> results.add(GenerationTaskResult.success(
                        "validation", "Validation", "Annotations applied to the generated entity"));
                case "dependencies" -> { /* handled above */ }
                case "properties" -> {
                    if (options.isGenerateProperties()) {
                        run(results, "properties", "application.properties", () ->
                                ApplicationPropertiesGenerator.generate(
                                        project, sourceRoot, meta, allEntities, options.isIncludeSecurity()));
                    }
                }
                case "swagger" -> {
                    run(results, "swagger:config", "OpenApiConfig", () ->
                            SwaggerConfigGenerator.generate(
                                    project, sourceRoot, meta, allEntities, options.isIncludeSecurity()));
                    run(results, "swagger:readme", "API Documentation", () ->
                            SwaggerReadmeGenerator.generate(project, sourceRoot, meta, allEntities));
                }
                case "auditing" -> {
                    run(results, "auditing:base", "BaseAuditEntity", () ->
                            BaseAuditEntityGenerator.generate(project, sourceRoot, meta, allEntities));
                    run(results, "auditing:config", "JpaAuditingConfig", () ->
                            JpaAuditingConfigGenerator.generate(
                                    project, sourceRoot, meta, allEntities, options.isIncludeSecurity()));
                    run(results, "auditing:readme", "Auditing Guide", () ->
                            AuditingReadmeGenerator.generate(project, sourceRoot, meta, allEntities));
                }
                case "exception-handling" -> {
                    run(results, "exception-handling:exceptions", "Exception classes", () ->
                            ExceptionGenerator.generate(project, sourceRoot, meta, allEntities));
                    run(results, "exception-handling:error", "ErrorResponse", () ->
                            ErrorResponseGenerator.generate(project, sourceRoot, meta, allEntities));
                    run(results, "exception-handling:handler", "GlobalExceptionHandler", () ->
                            GlobalExceptionHandlerGenerator.generate(project, sourceRoot, meta, allEntities));
                }
                case "pagination" -> run(results, "pagination", "Pagination", () ->
                        PaginationGenerator.generate(project, sourceRoot, meta, allEntities));
                case "dto" -> run(results, "dto", "DTO", () ->
                        DtoGenerator.generate(project, sourceRoot, meta, allEntities));
                case "mapper" -> run(results, "mapper", "Mapper", () ->
                        MapperGenerator.generate(project, sourceRoot, meta, allEntities));
                case "repository" -> run(results, "repository", "Repository", () ->
                        RepositoryGenerator.generate(project, sourceRoot, meta, allEntities));
                case "service" -> run(results, "service", "Service", () ->
                        ServiceGenerator.generate(project, sourceRoot, meta, allEntities));
                case "controller" -> run(results, "controller", "Controller", () ->
                        ControllerGenerator.generate(project, sourceRoot, meta, allEntities));
                case "security" -> {
                    if (options.isIncludeSecurity()) {
                        run(results, "security:config", "SecurityConfig", () ->
                                SecurityConfigGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:jwt", "JwtService", () ->
                                JwtServiceGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:filter", "JwtAuthenticationFilter", () ->
                                JwtAuthenticationFilterGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:user", "AppUser", () ->
                                UserEntityGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:role", "Role", () ->
                                RoleEnumGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:repository", "AppUserRepository", () ->
                                AppUserRepositoryGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:details", "UserDetailsServiceImpl", () ->
                                UserDetailsServiceImplGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:auth-service", "AuthenticationService", () ->
                                AuthenticationServiceGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:auth-controller", "AuthenticationController", () ->
                                AuthControllerGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:auth-dto", "Authentication DTOs", () ->
                                AuthDtoGenerator.generate(project, sourceRoot, meta, allEntities));
                        run(results, "security:readme", "Security Guide", () ->
                                SecurityReadmeGenerator.generate(project, sourceRoot, meta, allEntities));
                    }
                }
                default -> results.add(GenerationTaskResult.warning(
                        task.getId(), task.getDisplayName(), "Unknown task — nothing generated"));
            }
        }
        return results;
    }

    private static void deleteExisting(
            Project project,
            PsiDirectory sourceRoot,
            ClassMeta meta,
            String taskId,
            List<GenerationTaskResult> results
    ) {
        for (PsiFile file : ChangePreviewService.existingFilesForTask(project, sourceRoot, meta, taskId)) {
            try {
                file.delete();
            } catch (Exception ex) {
                results.add(GenerationTaskResult.failed(
                        taskId + ":replace", "Replace " + file.getName(), ex));
            }
        }
    }

    private static void run(List<GenerationTaskResult> results, String id, String displayName, Runnable task) {
        try {
            task.run();
            results.add(GenerationTaskResult.success(id, displayName));
        } catch (Exception ex) {
            System.err.printf("⚠️ [AutoSpring] %s failed: %s%n", id, ex.getMessage());
            ex.printStackTrace();
            results.add(GenerationTaskResult.failed(id, displayName, ex));
        }
    }
}
