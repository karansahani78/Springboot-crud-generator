package com.karan.intellijplatformplugin.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Result of validating a developer prompt. Contains no UI code. */
public final class PromptValidationResult {

    private final boolean valid;
    private final List<PromptError> errors;
    private final List<String> warnings;
    private final PromptSpecification specification;

    private PromptValidationResult(
            boolean valid,
            List<PromptError> errors,
            List<String> warnings,
            PromptSpecification specification
    ) {
        this.valid = valid;
        this.errors = List.copyOf(errors);
        this.warnings = List.copyOf(warnings);
        this.specification = specification;
    }

    public boolean isValid() { return valid; }
    public List<PromptError> getErrors() { return Collections.unmodifiableList(errors); }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
    public PromptSpecification getSpecification() { return specification; }

    public static PromptValidationResult success(PromptSpecification specification, List<String> warnings) {
        return new PromptValidationResult(true, List.of(),
                warnings == null ? List.of() : warnings, specification);
    }

    public static PromptValidationResult failure(List<PromptError> errors, List<String> warnings) {
        return new PromptValidationResult(false,
                errors == null ? List.of() : errors,
                warnings == null ? List.of() : warnings, null);
    }

    public static PromptValidationResult failure(PromptError error) {
        List<PromptError> errors = new ArrayList<>();
        errors.add(error);
        return failure(errors, List.of());
    }

    /** Formats all errors for display in a dialog or inline validation label. */
    public String formatErrors() {
        StringBuilder sb = new StringBuilder();
        for (PromptError error : errors) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("Line ").append(error.getLineNumber()).append(": ")
                    .append(error.getMessage());
            if (error.getSuggestion() != null && !error.getSuggestion().isBlank()) {
                sb.append('\n').append(error.getSuggestion());
            }
        }
        return sb.toString();
    }

    /** A single line-numbered parser error. */
    public static final class PromptError {
        private final int lineNumber;
        private final String message;
        private final String suggestion;

        public PromptError(int lineNumber, String message) {
            this(lineNumber, message, null);
        }

        public PromptError(int lineNumber, String message, String suggestion) {
            this.lineNumber = lineNumber;
            this.message = Objects.requireNonNull(message, "message");
            this.suggestion = suggestion;
        }

        public int getLineNumber() { return lineNumber; }
        public String getMessage() { return message; }
        public String getSuggestion() { return suggestion; }

        @Override
        public String toString() {
            return "Line " + lineNumber + ": " + message;
        }
    }
}
