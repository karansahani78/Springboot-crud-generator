package com.karan.intellijplatformplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One editable operation in an AutoSpring generation plan. */
public final class GenerationTask {

    public enum TaskType {
        ENTITY, DTO, MAPPER, REPOSITORY, SERVICE, CONTROLLER,
        PAGINATION, VALIDATION, SWAGGER, EXCEPTION_HANDLING,
        AUDITING, SECURITY, PROPERTIES, DEPENDENCIES, SUPPORT
    }

    public enum ValidationStatus {
        OK, MISSING_DEPENDENCY, ORDER_CONFLICT, NOT_REMOVABLE, UNSUPPORTED
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final TaskType taskType;
    private final List<String> dependencies;
    private final boolean removable;
    private boolean enabled;
    private int executionOrder;
    private List<String> targetPaths;
    private ValidationStatus validationStatus = ValidationStatus.OK;

    public GenerationTask(
            String id,
            String displayName,
            String description,
            TaskType taskType,
            boolean enabled,
            int executionOrder,
            List<String> dependencies,
            boolean removable,
            List<String> targetPaths
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = displayName == null ? id : displayName;
        this.description = description == null ? "" : description;
        this.taskType = taskType == null ? TaskType.SUPPORT : taskType;
        this.enabled = enabled;
        this.executionOrder = executionOrder;
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.removable = removable;
        this.targetPaths = targetPaths == null ? new ArrayList<>() : new ArrayList<>(targetPaths);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public TaskType getTaskType() { return taskType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getExecutionOrder() { return executionOrder; }
    public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }
    public List<String> getDependencies() { return Collections.unmodifiableList(dependencies); }
    public boolean isRemovable() { return removable; }
    public List<String> getTargetPaths() { return Collections.unmodifiableList(targetPaths); }
    public void setTargetPaths(List<String> targetPaths) {
        this.targetPaths = targetPaths == null ? new ArrayList<>() : new ArrayList<>(targetPaths);
    }
    public ValidationStatus getValidationStatus() { return validationStatus; }
    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus == null ? ValidationStatus.OK : validationStatus;
    }

    /** Creates an independent copy for plan reset support. */
    public GenerationTask copy() {
        GenerationTask copy = new GenerationTask(
                id, displayName, description, taskType, enabled, executionOrder,
                dependencies, removable, targetPaths);
        copy.validationStatus = validationStatus;
        return copy;
    }

    @Override
    public String toString() {
        return (enabled ? "[✓] " : "[ ] ") + displayName;
    }
}
