package com.karan.intellijplatformplugin.prompt;

import java.util.Objects;

/**
 * Represents one scalar field declared in a prompt.
 *
 * <p>Only constraints that have a deterministic mapping to JPA / Bean
 * Validation annotations are modelled here. Relationship declarations are not
 * supported in prompt mode and are rejected by {@link PromptParser}.
 */
public final class PromptFieldDefinition {

    private final String name;
    private final String javaType;
    private final boolean primaryKey;
    private final boolean generated;
    private final boolean required;
    private final boolean unique;
    private final boolean email;
    private final Integer max;
    private final Integer min;
    private final Integer size;
    private final int lineNumber;

    private PromptFieldDefinition(Builder builder) {
        this.name = builder.name;
        this.javaType = builder.javaType;
        this.primaryKey = builder.primaryKey;
        this.generated = builder.generated;
        this.required = builder.required;
        this.unique = builder.unique;
        this.email = builder.email;
        this.max = builder.max;
        this.min = builder.min;
        this.size = builder.size;
        this.lineNumber = builder.lineNumber;
    }

    public String getName() { return name; }
    public String getJavaType() { return javaType; }
    public boolean isPrimaryKey() { return primaryKey; }
    public boolean isGenerated() { return generated; }
    public boolean isRequired() { return required; }
    public boolean isUnique() { return unique; }
    public boolean isEmail() { return email; }
    public Integer getMax() { return max; }
    public Integer getMin() { return min; }
    public Integer getSize() { return size; }
    public int getLineNumber() { return lineNumber; }

    public static Builder builder(String name, String javaType, int lineNumber) {
        return new Builder(name, javaType, lineNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PromptFieldDefinition other)) return false;
        return primaryKey == other.primaryKey
                && generated == other.generated
                && required == other.required
                && unique == other.unique
                && email == other.email
                && lineNumber == other.lineNumber
                && Objects.equals(name, other.name)
                && Objects.equals(javaType, other.javaType)
                && Objects.equals(max, other.max)
                && Objects.equals(min, other.min)
                && Objects.equals(size, other.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, javaType, primaryKey, generated, required, unique, email, max, min, size);
    }

    @Override
    public String toString() {
        return "PromptFieldDefinition{name='" + name + "', javaType='" + javaType + "', primaryKey=" + primaryKey + '}';
    }

    public static final class Builder {
        private final String name;
        private final String javaType;
        private final int lineNumber;
        private boolean primaryKey;
        private boolean generated;
        private boolean required;
        private boolean unique;
        private boolean email;
        private Integer max;
        private Integer min;
        private Integer size;

        private Builder(String name, String javaType, int lineNumber) {
            this.name = name;
            this.javaType = javaType;
            this.lineNumber = lineNumber;
        }

        public Builder primaryKey(boolean value) { this.primaryKey = value; return this; }
        public Builder generated(boolean value) { this.generated = value; return this; }
        public Builder required(boolean value) { this.required = value; return this; }
        public Builder unique(boolean value) { this.unique = value; return this; }
        public Builder email(boolean value) { this.email = value; return this; }
        public Builder max(Integer value) { this.max = value; return this; }
        public Builder min(Integer value) { this.min = value; return this; }
        public Builder size(Integer value) { this.size = value; return this; }

        public PromptFieldDefinition build() {
            return new PromptFieldDefinition(this);
        }
    }
}
