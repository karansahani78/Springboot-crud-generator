package com.karan.intellijplatformplugin.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Parsed definition of the JPA entity requested by the developer. */
public final class EntityDefinition {

    private final String className;
    private final String packageName;
    private final String tableName;
    private final boolean tableAnnotation;
    private final List<PromptFieldDefinition> fields;
    private final PromptFieldDefinition idField;

    public EntityDefinition(
            String className,
            String packageName,
            String tableName,
            boolean tableAnnotation,
            List<PromptFieldDefinition> fields
    ) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("Entity class name is required");
        }
        this.className = className;
        this.packageName = packageName;
        this.tableName = tableName;
        this.tableAnnotation = tableAnnotation;
        this.fields = fields == null ? List.of() : List.copyOf(fields);

        PromptFieldDefinition resolvedId = null;
        for (PromptFieldDefinition field : this.fields) {
            if (field.isPrimaryKey()) {
                resolvedId = field;
                break;
            }
        }
        this.idField = resolvedId;
    }

    public String getClassName() { return className; }
    public String getPackageName() { return packageName; }
    public String getTableName() { return tableName; }
    public boolean isTableAnnotation() { return tableAnnotation; }
    public List<PromptFieldDefinition> getFields() {
        return Collections.unmodifiableList(fields);
    }
    public PromptFieldDefinition getIdField() { return idField; }

    /**
     * Returns non-primary-key fields only. The generator writes the id field
     * first with JPA annotations, then all remaining scalar fields.
     */
    public List<PromptFieldDefinition> getNonIdFields() {
        List<PromptFieldDefinition> result = new ArrayList<>();
        for (PromptFieldDefinition field : fields) {
            if (!field.isPrimaryKey()) result.add(field);
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityDefinition other)) return false;
        return tableAnnotation == other.tableAnnotation
                && Objects.equals(className, other.className)
                && Objects.equals(packageName, other.packageName)
                && Objects.equals(tableName, other.tableName)
                && Objects.equals(fields, other.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, packageName, tableName, tableAnnotation, fields);
    }

    @Override
    public String toString() {
        return "EntityDefinition{className='" + className + "', packageName='" + packageName
                + "', fields=" + fields.size() + '}';
    }
}
