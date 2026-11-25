package com.karan.intellijplatformplugin.service;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.karan.intellijplatformplugin.generator.EntityGenerator;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.GenerationOptions;
import com.karan.intellijplatformplugin.model.GenerationPlan;
import com.karan.intellijplatformplugin.model.GenerationTask;
import com.karan.intellijplatformplugin.model.GenerationTaskResult;
import com.karan.intellijplatformplugin.prompt.PromptSpecification;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes prompt mode from a validated specification and an editable plan.
 *
 * <p>The generated entity is routed through {@code PsiDirectoryUtil.toClassMeta}
 * and then through the same {@link CrudGenerationService} used by the original
 * entity workflow.
 */
public final class PromptGenerationService {

    private PromptGenerationService() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /** Pre-flight validation result: empty means the plan can be executed. */
    public static List<String> validatePlan(GenerationPlan plan, PromptSpecification specification) {
        List<String> problems = new ArrayList<>(plan.validate());
        if (!plan.find("entity").isEnabled()) {
            problems.add("Entity creation cannot be disabled in prompt mode.");
        }
        if (specification.getEntityDefinition().getIdField() == null) {
            problems.add("Exactly one primary key is required.");
        }
        return problems;
    }

    /**
     * Executes the plan. Must be called on the EDT; the service manages its own
     * write action so all generated files remain undoable in IntelliJ.
     */
    public static List<GenerationTaskResult> execute(
            Project project,
            PsiDirectory sourceRoot,
            PromptSpecification specification,
            GenerationPlan plan,
            String persistenceNamespace,
            Map<String, ChangePreviewService.Decision> decisions
    ) {
        List<String> problems = validatePlan(plan, specification);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Invalid generation plan:\n- " + String.join("\n- ", problems));
        }

        GenerationOptions options = plan.toGenerationOptions();
        String namespace = "jakarta".equalsIgnoreCase(persistenceNamespace)
                || "javax".equalsIgnoreCase(persistenceNamespace)
                ? persistenceNamespace.toLowerCase() : "jakarta";

        AtomicReference<List<GenerationTaskResult>> reference = new AtomicReference<>(new ArrayList<>());

        WriteCommandAction.runWriteCommandAction(project, () -> {
            List<GenerationTaskResult> results = new ArrayList<>();
            ClassMeta meta;

            try {
                meta = createOrReuseEntity(
                        project, sourceRoot, specification, options, namespace, decisions, results);
            } catch (Exception ex) {
                results.add(GenerationTaskResult.failed("entity", "Entity", ex));
                reference.set(results);
                return;
            }

            List<ClassMeta> scannedEntities = ReadAction.compute(
                    () -> PsiDirectoryUtil.getAllEntityMetas(project));
            List<ClassMeta> allEntities = new ArrayList<>();
            for (ClassMeta scanned : scannedEntities) {
                if (!scanned.equals(meta)) allEntities.add(scanned);
            }
            allEntities.add(0, meta);

            results.addAll(CrudGenerationService.generate(
                    project, sourceRoot, meta, allEntities, options,
                    specification.getDatabaseType(), plan, decisions));
            reference.set(results);
        });

        return reference.get();
    }

    private static ClassMeta createOrReuseEntity(
            Project project,
            PsiDirectory sourceRoot,
            PromptSpecification specification,
            GenerationOptions options,
            String namespace,
            Map<String, ChangePreviewService.Decision> decisions,
            List<GenerationTaskResult> results
    ) {
        String packageName = specification.getEntityDefinition().getPackageName();
        if (packageName == null || packageName.isBlank()) {
            String target = specification.getTargetPackage();
            packageName = target == null || target.isBlank() ? "entity" : target;
        }
        String entityName = specification.getEntityDefinition().getClassName();
        PsiDirectory entityDir = PsiDirectoryUtil.createPackageDirs(sourceRoot, packageName);
        PsiFile existing = entityDir.findFile(entityName + ".java");

        ChangePreviewService.Decision decision = decisions.get("entity");
        if (existing != null && decision == ChangePreviewService.Decision.SKIP) {
            if (!(existing instanceof PsiJavaFile javaFile) || javaFile.getClasses().length == 0) {
                throw new IllegalStateException("Existing entity file is not a valid Java class: " + existing.getName());
            }
            PsiClass psiClass = javaFile.getClasses()[0];
            ClassMeta reused = PsiDirectoryUtil.toClassMeta(psiClass);
            results.add(GenerationTaskResult.warning("entity", "Entity",
                    "Reused existing " + entityName + ".java (skipped by developer)"));
            return reused;
        }

        if (existing != null) {
            if (decision != ChangePreviewService.Decision.REPLACE) {
                throw new IllegalStateException("Entity file already exists: " + entityName + ".java");
            }
            existing.delete();
        }

        String basePackage = packageName.endsWith(".entity")
                ? packageName.substring(0, packageName.length() - ".entity".length())
                : packageName;
        EntityGenerator.Result generated = EntityGenerator.generate(
                project, sourceRoot, specification.getEntityDefinition(), options, namespace, basePackage);
        results.add(GenerationTaskResult.success("entity", "Entity", "Created " + entityName + ".java"));
        return generated.getClassMeta();
    }
}
