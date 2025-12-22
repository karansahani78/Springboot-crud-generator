package com.karan.intellijplatformplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents metadata for a class including its fields and package information.
 */
public class ClassMeta {

    private final String className;
    private final String packageName;
    private final String idType;
    private final List<FieldMeta> fields;

    public ClassMeta(String className, String packageName, String idType, List<FieldMeta> fields) {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("Package name cannot be null or empty");
        }
        if (idType == null || idType.trim().isEmpty()) {
            throw new IllegalArgumentException("ID type cannot be null or empty");
        }

        this.className = className;
        this.packageName = packageName;
        this.idType = idType;
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getIdType() {
        return idType;
    }

    public List<FieldMeta> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * Returns the base package by removing the last segment if it's "model" or "entity".
     */
    public String basePackage() {
        if (packageName.endsWith(".model") || packageName.endsWith(".entity")) {
            return packageName.substring(0, packageName.lastIndexOf('.'));
        }
        return packageName;
    }

    /**
     * Returns non-ID fields only.
     */
    public List<FieldMeta> getNonIdFields() {
        return fields.stream()
                .filter(f -> !f.getName().equalsIgnoreCase("id"))
                .toList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassMeta classMeta = (ClassMeta) o;
        return Objects.equals(className, classMeta.className) &&
                Objects.equals(packageName, classMeta.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, packageName);
    }

    @Override
    public String toString() {
        return "ClassMeta{className='" + className + "', packageName='" + packageName +
                "', idType='" + idType + "', fields=" + fields.size() + "}";
    }
}