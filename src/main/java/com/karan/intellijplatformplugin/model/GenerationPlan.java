package com.karan.intellijplatformplugin.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Editable, dependency-aware plan for prompt-based generation.
 *
 * <p>The plan is built from parsed {@link GenerationOptions}, can be edited by
 * the plan dialog, and can be converted back to options before execution.
 */
public final class GenerationPlan {

    private final String entityName;
    private final List<GenerationTask> tasks;
    private final List<GenerationTask> originalTasks;

    public GenerationPlan(String entityName, List<GenerationTask> tasks) {
        this.entityName = entityName == null ? "" : entityName;
        this.tasks = new ArrayList<>(tasks == null ? List.of() : tasks);
        this.originalTasks = new ArrayList<>();
        for (GenerationTask task : this.tasks) originalTasks.add(task.copy());
    }

    public String getEntityName() { return entityName; }

    public List<GenerationTask> getTasks() { return tasks; }

    /** Returns tasks in execution order. */
    public List<GenerationTask> orderedTasks() {
        List<GenerationTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator.comparingInt(GenerationTask::getExecutionOrder));
        return ordered;
    }

    public GenerationTask find(String id) {
        for (GenerationTask task : tasks) {
            if (task.getId().equals(id)) return task;
        }
        return null;
    }

    public void setEnabled(String id, boolean enabled) {
        GenerationTask task = find(id);
        if (task != null) task.setEnabled(enabled);
    }

    /** Removes an optional task. Required/structural tasks cannot be removed. */
    public boolean remove(String id) {
        GenerationTask task = find(id);
        if (task == null || !task.isRemovable()) return false;
        return tasks.remove(task);
    }

    /** Adds a supported task if it is not already present. */
    public boolean add(GenerationTask task) {
        if (task == null || find(task.getId()) != null) return false;
        return tasks.add(task);
    }

    /**
     * Moves a task one position earlier (-1) or later (+1) in execution order.
     * Returns false when the move would place a task before one of its
     * dependencies or after a task that depends on it.
     */
    public boolean reorder(String id, int direction) {
        GenerationTask task = find(id);
        if (task == null || (direction != -1 && direction != 1)) return false;

        List<GenerationTask> ordered = orderedTasks();
        int index = ordered.indexOf(task);
        int targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= ordered.size()) return false;

        GenerationTask neighbor = ordered.get(targetIndex);

        // Moving before a dependency, or after a dependent task, is invalid.
        if (direction < 0 && task.getDependencies().contains(neighbor.getId())) return false;
        if (direction > 0 && neighbor.getDependencies().contains(id)) return false;

        int currentOrder = task.getExecutionOrder();
        task.setExecutionOrder(neighbor.getExecutionOrder());
        neighbor.setExecutionOrder(currentOrder);
        normalizeOrders();
        return true;
    }

    /** Restores the plan to the exact state produced by the parser. */
    public void reset() {
        tasks.clear();
        for (GenerationTask original : originalTasks) tasks.add(original.copy());
    }

    /**
     * Validates dependencies and ordering.
     *
     * @return human-readable problems; empty when the plan is executable
     */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (GenerationTask task : tasks) {
            task.setValidationStatus(GenerationTask.ValidationStatus.OK);
            if (!task.isEnabled()) continue;
            for (String dependencyId : task.getDependencies()) {
                GenerationTask dependency = find(dependencyId);
                if (dependency == null || !dependency.isEnabled()) {
                    task.setValidationStatus(GenerationTask.ValidationStatus.MISSING_DEPENDENCY);
                    problems.add(task.getDisplayName() + " requires '" + dependencyId
                            + "', which is missing or disabled.");
                } else if (dependency.getExecutionOrder() >= task.getExecutionOrder()) {
                    task.setValidationStatus(GenerationTask.ValidationStatus.ORDER_CONFLICT);
                    problems.add(task.getDisplayName() + " must run after '" + dependencyId + "'.");
                }
            }
        }
        return problems;
    }

    /** Converts the enabled task set back into generation options. */
    public GenerationOptions toGenerationOptions() {
        GenerationOptions.Builder builder = GenerationOptions.builder();
        for (GenerationTask task : tasks) {
            boolean enabled = task.isEnabled();
            switch (task.getId()) {
                case "entity" -> builder.generateEntity(enabled);
                case "dto" -> builder.generateDto(enabled);
                case "mapper" -> builder.generateMapper(enabled);
                case "repository" -> builder.generateRepository(enabled);
                case "service" -> builder.generateService(enabled);
                case "controller" -> builder.generateController(enabled);
                case "pagination" -> builder.generatePagination(enabled);
                case "validation" -> builder.generateValidation(enabled);
                case "swagger" -> builder.generateSwagger(enabled);
                case "exception-handling" -> builder.generateExceptionHandling(enabled);
                case "auditing" -> builder.generateAuditing(enabled);
                case "security" -> builder.includeSecurity(enabled);
                case "properties" -> builder.generateProperties(enabled);
                case "dependencies" -> builder.injectDependencies(enabled);
                default -> { /* unknown task ids are ignored defensively */ }
            }
        }
        // Entity creation is non-negotiable in prompt mode.
        builder.generateEntity(true);
        return builder.build();
    }

    /** Builds the default full CRUD plan for an entity. */
    public static GenerationPlan defaults(String entityName) {
        return from(GenerationOptions.defaults(), entityName);
    }

    /** Builds a plan from parsed generation options. */
    public static GenerationPlan from(GenerationOptions options, String entityName) {
        Objects.requireNonNull(options, "options");
        List<GenerationTask> tasks = new ArrayList<>();
        int order = 0;
        tasks.add(task("entity", "Create " + entityName + ".java",
                "Generates the JPA entity from parsed fields.",
                GenerationTask.TaskType.ENTITY, true, order++, false, List.of()));
        tasks.add(task("validation", "Generate Validation",
                "Adds Bean Validation annotations to the generated entity.",
                GenerationTask.TaskType.VALIDATION, options.isGenerateValidation(), order++, true,
                List.of("entity")));
        tasks.add(task("dependencies", "Inject Dependencies",
                "Adds required Spring Boot / JPA / JWT dependencies to the build file.",
                GenerationTask.TaskType.DEPENDENCIES, options.isInjectDependencies(), order++, true,
                List.of()));
        tasks.add(task("properties", "Configure application.properties",
                "Appends Swagger, JPA and optional JWT properties.",
                GenerationTask.TaskType.PROPERTIES, options.isGenerateProperties(), order++, true,
                List.of()));
        tasks.add(task("swagger", "Generate Swagger Configuration",
                "Creates OpenApiConfig and API_DOCUMENTATION.md.",
                GenerationTask.TaskType.SWAGGER, options.isGenerateSwagger(), order++, true,
                List.of()));
        tasks.add(task("auditing", "Generate Auditing Infrastructure",
                "Creates BaseAuditEntity, JpaAuditingConfig and AUDITING_GUIDE.md.",
                GenerationTask.TaskType.AUDITING, options.isGenerateAuditing(), order++, true,
                List.of("entity")));
        tasks.add(task("exception-handling", "Generate Exception Handling",
                "Creates exception classes, ErrorResponse and GlobalExceptionHandler.",
                GenerationTask.TaskType.EXCEPTION_HANDLING, options.isGenerateExceptionHandling(), order++, true,
                List.of()));
        tasks.add(task("pagination", "Generate Pagination Support",
                "Creates PageResponse and SortDirection.",
                GenerationTask.TaskType.PAGINATION, options.isGeneratePagination(), order++, true,
                List.of()));
        tasks.add(task("dto", "Generate " + entityName + "Dto.java",
                "Creates the Data Transfer Object.",
                GenerationTask.TaskType.DTO, options.isGenerateDto(), order++, true,
                List.of("entity")));
        tasks.add(task("mapper", "Generate " + entityName + "Mapper.java",
                "Maps between the entity and its DTO.",
                GenerationTask.TaskType.MAPPER, options.isGenerateMapper(), order++, true,
                List.of("entity", "dto")));
        tasks.add(task("repository", "Generate " + entityName + "Repository.java",
                "Creates the Spring Data JPA repository.",
                GenerationTask.TaskType.REPOSITORY, options.isGenerateRepository(), order++, true,
                List.of("entity")));
        tasks.add(task("service", "Generate " + entityName + "Service.java",
                "Creates the transactional service layer.",
                GenerationTask.TaskType.SERVICE, options.isGenerateService(), order++, true,
                List.of("entity", "repository", "dto", "mapper")));
        tasks.add(task("controller", "Generate " + entityName + "Controller.java",
                "Creates the REST controller.",
                GenerationTask.TaskType.CONTROLLER, options.isGenerateController(), order++, true,
                List.of("entity", "service", "mapper", "dto", "pagination")));
        tasks.add(task("security", "Configure JWT Security",
                "Creates the full JWT authentication stack.",
                GenerationTask.TaskType.SECURITY, options.isIncludeSecurity(), order, true,
                List.of("entity", "exception-handling", "properties")));
        return new GenerationPlan(entityName, tasks);
    }

    /** Creates a supported optional task for the Add operation in the plan editor. */
    public static GenerationTask supportedTask(String id, String entityName) {
        GenerationOptions all = GenerationOptions.defaults();
        GenerationPlan full = GenerationPlan.from(all.toBuilder().includeSecurity(true).build(), entityName);
        GenerationTask template = full.find(id);
        if (template == null) return null;
        GenerationTask copy = template.copy();
        copy.setEnabled(true);
        return copy;
    }

    private static GenerationTask task(
            String id, String displayName, String description, GenerationTask.TaskType type,
            boolean enabled, int order, boolean removable, List<String> dependencies
    ) {
        return new GenerationTask(id, displayName, description, type, enabled, order,
                dependencies, removable, List.of());
    }

    private void normalizeOrders() {
        List<GenerationTask> ordered = orderedTasks();
        Map<String, Integer> positions = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) positions.put(ordered.get(i).getId(), i);
        for (GenerationTask task : tasks) task.setExecutionOrder(positions.get(task.getId()));
    }
}
