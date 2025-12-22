package com.karan.intellijplatformplugin.model;

import java.util.Objects;

/**
 * Represents metadata for a field in a class.
 */
public class FieldMeta {

    private final String name;
    private final String type;

    public FieldMeta(String name, String type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Field name cannot be null or empty");
        }
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Field type cannot be null or empty");
        }
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getCapitalizedName() {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldMeta fieldMeta = (FieldMeta) o;
        return Objects.equals(name, fieldMeta.name) && Objects.equals(type, fieldMeta.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return "FieldMeta{name='" + name + "', type='" + type + "'}";
    }
}