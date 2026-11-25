package com.karan.intellijplatformplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Structured outcome of one generation task. */
public final class GenerationTaskResult {

    public enum Status { SUCCESS, SKIPPED, FAILED, WARNING }

    private final String taskId;
    private final String displayName;
    private final Status status;
    private final String detail;

    public GenerationTaskResult(String taskId, String displayName, Status status, String detail) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.displayName = displayName == null ? taskId : displayName;
        this.status = status == null ? Status.FAILED : status;
        this.detail = detail == null ? "" : detail;
    }

    public static GenerationTaskResult success(String taskId, String displayName) {
        return new GenerationTaskResult(taskId, displayName, Status.SUCCESS, "Completed");
    }

    public static GenerationTaskResult success(String taskId, String displayName, String detail) {
        return new GenerationTaskResult(taskId, displayName, Status.SUCCESS, detail);
    }

    public static GenerationTaskResult skipped(String taskId, String displayName, String reason) {
        return new GenerationTaskResult(taskId, displayName, Status.SKIPPED, reason);
    }

    public static GenerationTaskResult warning(String taskId, String displayName, String detail) {
        return new GenerationTaskResult(taskId, displayName, Status.WARNING, detail);
    }

    public static GenerationTaskResult failed(String taskId, String displayName, Throwable error) {
        String detail = error == null ? "Unknown error"
                : error.getClass().getSimpleName() + ": " + error.getMessage();
        return new GenerationTaskResult(taskId, displayName, Status.FAILED, detail);
    }

    public String getTaskId() { return taskId; }
    public String getDisplayName() { return displayName; }
    public Status getStatus() { return status; }
    public String getDetail() { return detail; }

    public static List<GenerationTaskResult> failures(List<GenerationTaskResult> results) {
        List<GenerationTaskResult> failures = new ArrayList<>();
        for (GenerationTaskResult result : results) {
            if (result.getStatus() == Status.FAILED) failures.add(result);
        }
        return Collections.unmodifiableList(failures);
    }

    @Override
    public String toString() {
        return status + " " + displayName + (detail.isEmpty() ? "" : " — " + detail);
    }
}
