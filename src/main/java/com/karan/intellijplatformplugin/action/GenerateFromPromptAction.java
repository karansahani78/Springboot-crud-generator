package com.karan.intellijplatformplugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.GenerationOptions;
import com.karan.intellijplatformplugin.model.GenerationPlan;
import com.karan.intellijplatformplugin.model.GenerationTaskResult;
import com.karan.intellijplatformplugin.prompt.EntityDefinition;
import com.karan.intellijplatformplugin.prompt.PromptParser;
import com.karan.intellijplatformplugin.prompt.PromptSpecification;
import com.karan.intellijplatformplugin.prompt.PromptValidationResult;
import com.karan.intellijplatformplugin.service.ChangePreviewService;
import com.karan.intellijplatformplugin.service.ProjectContextAnalyzer;
import com.karan.intellijplatformplugin.service.PromptGenerationService;
import com.karan.intellijplatformplugin.ui.PromptGenerationDialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prompt-driven entry point for AutoSpring generation.
 *
 * <p>No entity selection is required: the developer describes the entity in a
 * local prompt, reviews the resulting plan, confirms file conflicts and then
 * generates directly inside the current Spring Boot project.
 */
public class GenerateFromPromptAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            Messages.showErrorDialog("No project is open. Open a Spring Boot project first.",
                    "AutoSpring – Prompt Generation");
            return;
        }

        ProjectContextAnalyzer.Context context = ProjectContextAnalyzer.analyze(project);
        String namespace = context.getPersistenceNamespace();
        if (namespace == null || namespace.isBlank() || "unknown".equalsIgnoreCase(namespace)) {
            int choice = Messages.showChooseDialog(
                    project,
                    "AutoSpring could not determine whether this project uses jakarta or javax "
                            + "persistence APIs. Select the namespace to use for generated code.",
                    "Persistence Namespace",
                    Messages.getQuestionIcon(),
                    new String[]{"jakarta", "javax"},
                    "jakarta");
            if (choice < 0) return;
            namespace = choice == 0 ? "jakarta" : "javax";
        } else {
            namespace = namespace.toLowerCase(Locale.ROOT);
        }

        PromptGenerationDialog dialog = new PromptGenerationDialog(project, context);
        if (!dialog.showAndGet()) return;

        PromptSpecification specification = dialog.getSpecification();
        if (specification == null) {
            PromptValidationResult result = PromptParser.parse(dialog.getPromptText());
            if (!result.isValid()) {
                Messages.showErrorDialog(project, result.formatErrors(), "Invalid Prompt");
                return;
            }
            specification = result.getSpecification();
        }

        // Merge dialog settings with the parsed specification. The checkbox set
        // is authoritative for component selection, while package / namespace /
        // security are resolved explicitly below.
        GenerationOptions selectedOptions = dialog.getSelectedGenerationOptions();
        specification = specification.withResolvedSettings(
                dialog.getEntityPackage(), namespace, selectedOptions.isIncludeSecurity());
        specification = applyDialogSettings(specification, dialog, selectedOptions, namespace);

        GenerationPlan plan = dialog.getReviewedPlan() != null
                ? dialog.getReviewedPlan()
                : GenerationPlan.from(specification.getGenerationOptions(), dialog.getEntityName());

        PsiDirectory sourceRoot = dialog.getSourceRoot();
        if (sourceRoot == null && !context.getSourceRoots().isEmpty()) {
            sourceRoot = context.getSourceRoots().get(0);
        }
        if (sourceRoot == null) {
            Messages.showErrorDialog(project,
                    "No Java source root was found. Mark src/main/java as a Sources Root and retry.",
                    "AutoSpring – Prompt Generation");
            return;
        }

        ClassMeta previewMeta = buildPreviewMeta(specification, dialog, context);
        List<ChangePreviewService.TargetFile> targets =
                ChangePreviewService.predictTargets(project, sourceRoot, previewMeta, plan);
        Map<String, ChangePreviewService.Decision> decisions =
                ChangePreviewService.resolveDecisions(project, targets);
        if (decisions == null) return;

        List<GenerationTaskResult> results;
        try {
            results = PromptGenerationService.execute(
                    project, sourceRoot, specification, plan, namespace, decisions);
        } catch (IllegalArgumentException ex) {
            Messages.showErrorDialog(project, ex.getMessage(), "AutoSpring – Generation Error");
            return;
        }

        showReport(project, results);
    }

    @Override
    public void update(AnActionEvent e) {
        // Prompt mode works without a selected entity or file.
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    private static PromptSpecification applyDialogSettings(
            PromptSpecification spec,
            PromptGenerationDialog dialog,
            GenerationOptions selected,
            String namespace
    ) {
        EntityDefinition original = spec.getEntityDefinition();
        String entityName = dialog.getEntityName();
        if (entityName == null || entityName.isBlank()) entityName = original.getClassName();
        String entityPackage = dialog.getEntityPackage();
        if (entityPackage == null || entityPackage.isBlank()) {
            entityPackage = original.getPackageName();
        }

        EntityDefinition resolvedDefinition = new EntityDefinition(
                entityName, entityPackage, original.getTableName(),
                original.isTableAnnotation(), original.getFields());

        GenerationOptions merged = spec.getGenerationOptions().toBuilder()
                .generateEntity(true)
                .generateDto(selected.isGenerateDto())
                .generateMapper(selected.isGenerateMapper())
                .generateRepository(selected.isGenerateRepository())
                .generateService(selected.isGenerateService())
                .generateController(selected.isGenerateController())
                .generatePagination(selected.isGeneratePagination())
                .generateValidation(selected.isGenerateValidation())
                .generateSwagger(selected.isGenerateSwagger())
                .generateExceptionHandling(selected.isGenerateExceptionHandling())
                .generateAuditing(selected.isGenerateAuditing())
                .includeSecurity(selected.isIncludeSecurity())
                .generateProperties(selected.isGenerateProperties())
                .injectDependencies(selected.isInjectDependencies())
                .build();

        String securityMode = selected.isIncludeSecurity() ? "JWT" : "None";
        return new PromptSpecification(
                resolvedDefinition,
                merged,
                dialog.getDatabaseType(),
                securityMode,
                dialog.getEntityPackage(),
                namespace,
                spec.getWarnings());
    }

    private static ClassMeta buildPreviewMeta(
            PromptSpecification spec,
            PromptGenerationDialog dialog,
            ProjectContextAnalyzer.Context context
    ) {
        String entityName = dialog.getEntityName();
        if (entityName == null || entityName.isBlank()) {
            entityName = spec.getEntityDefinition().getClassName();
        }
        String entityPackage = dialog.getEntityPackage();
        if (entityPackage == null || entityPackage.isBlank()) {
            entityPackage = spec.getEntityDefinition().getPackageName();
        }
        if (entityPackage == null || entityPackage.isBlank()) {
            entityPackage = "entity";
        }
        String basePackage = dialog.getBasePackage();
        if (basePackage == null || basePackage.isBlank()) {
            basePackage = context.getBasePackage();
        }
        if (basePackage == null || basePackage.isBlank()) {
            basePackage = spec.getTargetPackage() == null ? "com.example.app" : spec.getTargetPackage();
        }

        String idType = "Long";
        if (spec.getEntityDefinition().getIdField() != null) {
            idType = spec.getEntityDefinition().getIdField().getJavaType();
        }
        return new ClassMeta(entityName, entityPackage, basePackage, idType,
                List.of(), List.of());
    }

    private static void showReport(Project project, List<GenerationTaskResult> results) {
        Map<GenerationTaskResult.Status, List<GenerationTaskResult>> grouped = new LinkedHashMap<>();
        for (GenerationTaskResult.Status status : GenerationTaskResult.Status.values()) {
            grouped.put(status, new ArrayList<>());
        }
        for (GenerationTaskResult result : results) {
            grouped.get(result.getStatus()).add(result);
        }

        boolean entityFailed = results.stream()
                .anyMatch(r -> r.getStatus() == GenerationTaskResult.Status.FAILED
                        && "entity".equals(r.getTaskId()));
        boolean anyFailed = results.stream()
                .anyMatch(r -> r.getStatus() == GenerationTaskResult.Status.FAILED);
        String overall = entityFailed ? "FAILED"
                : anyFailed ? "PARTIALLY COMPLETED" : "COMPLETED";

        StringBuilder report = new StringBuilder("AutoSpring generation " + overall + "\n\n");
        for (Map.Entry<GenerationTaskResult.Status, List<GenerationTaskResult>> entry : grouped.entrySet()) {
            List<GenerationTaskResult> group = entry.getValue();
            if (group.isEmpty()) continue;
            report.append(entry.getKey()).append(" (").append(group.size()).append("):\n");
            for (GenerationTaskResult result : group) {
                report.append("  • ").append(result.getDisplayName());
                if (result.getDetail() != null && !result.getDetail().isBlank()) {
                    report.append(" — ").append(result.getDetail());
                }
                report.append('\n');
            }
            report.append('\n');
        }
        report.append("Reload the Gradle/Maven project if dependencies were added.");
        Messages.showInfoMessage(project, report.toString(), "AutoSpring – Generation Report");
    }
}
