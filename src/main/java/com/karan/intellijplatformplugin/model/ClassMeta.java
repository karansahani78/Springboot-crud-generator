package com.karan.intellijplatformplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents metadata for a JPA entity class.
 *
 * <p>Extended to carry {@link RelationshipMeta} entries so that
 * every generator has full cross-entity context without re-parsing PSI.
 */
public class ClassMeta {

    private final String className;
    private final String packageName;   // rawPackage  — e.g. com.karan.app.entity
    private final String basePackage;   // resolved    — e.g. com.karan.app
    private final String idType;

    /** Plain scalar / value-type fields (NOT relationship fields). */
    private final List<FieldMeta> fields;

    /**
     * JPA relationships declared on this entity.
     * Populated by {@code PsiDirectoryUtil.toClassMeta()} during PSI parsing.
     */
    private final List<RelationshipMeta> relationships;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Full 6-param constructor — used by PsiDirectoryUtil which passes
     * rawPackage and basePackage separately after calling resolveBasePackage().
     */
    public ClassMeta(
            String className,
            String packageName,
            String basePackage,
            String idType,
            List<FieldMeta> fields,
            List<RelationshipMeta> relationships
    ) {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("Package name cannot be null or empty");
        }
        if (idType == null || idType.trim().isEmpty()) {
            throw new IllegalArgumentException("ID type cannot be null or empty");
        }

        this.className   = className;
        this.packageName = packageName;
        this.basePackage = (basePackage != null && !basePackage.isBlank()) ? basePackage : deriveBasePackage(packageName);
        this.idType      = idType;
        this.fields        = fields        != null ? new ArrayList<>(fields)        : new ArrayList<>();
        this.relationships = relationships != null ? new ArrayList<>(relationships) : new ArrayList<>();
    }

    /**
     * Backward-compatible 5-param constructor — derives basePackage automatically.
     * Existing call sites that pass only 5 args continue to compile.
     */
    public ClassMeta(
            String className,
            String packageName,
            String idType,
            List<FieldMeta> fields,
            List<RelationshipMeta> relationships
    ) {
        this(className, packageName, deriveBasePackage(packageName), idType, fields, relationships);
    }

    /**
     * Backward-compatible 4-param constructor — no relationships.
     * Existing call sites that pass only 4 args continue to compile.
     */
    public ClassMeta(
            String className,
            String packageName,
            String idType,
            List<FieldMeta> fields
    ) {
        this(className, packageName, idType, fields, Collections.emptyList());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String deriveBasePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "";
        if (packageName.endsWith(".entity"))   return packageName.substring(0, packageName.length() - ".entity".length());
        if (packageName.endsWith(".entities")) return packageName.substring(0, packageName.length() - ".entities".length());
        if (packageName.endsWith(".model"))    return packageName.substring(0, packageName.length() - ".model".length());
        return packageName;
    }

    // -----------------------------------------------------------------------
    // Core accessors
    // -----------------------------------------------------------------------

    public String getClassName() {
        return className;
    }

    /** The raw entity package — e.g. {@code com.karan.app.entity} */
    public String getPackageName() {
        return packageName;
    }

    /** The resolved base/application package — e.g. {@code com.karan.app} */
    public String getBasePackage() {
        return basePackage;
    }

    public String getIdType() {
        return idType;
    }

    /** Returns all scalar (non-relationship) fields as an unmodifiable list. */
    public List<FieldMeta> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /** Returns all JPA relationships on this entity as an unmodifiable list. */
    public List<RelationshipMeta> getRelationships() {
        return Collections.unmodifiableList(relationships);
    }

    // -----------------------------------------------------------------------
    // Derived helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the resolved base package (stored value — no re-computation).
     * Replaces the old inline-derived version.
     */
    public String basePackage() {
        return basePackage;
    }

    /**
     * Returns scalar fields that are not the primary key.
     */
    public List<FieldMeta> getNonIdFields() {
        return fields.stream()
                .filter(f -> !f.getName().equalsIgnoreCase("id"))
                .toList();
    }

    /**
     * Convenience: relationships that should appear in the create/update DTO.
     * Filters out inverse ONE_TO_MANY sides (they have {@code mappedBy} set)
     * to avoid circular write problems.
     */
    public List<RelationshipMeta> getDtoRelationships() {
        return relationships.stream()
                .filter(RelationshipMeta::isIncludedInDto)
                .toList();
    }

    /**
     * Convenience: MANY_TO_ONE and ONE_TO_ONE relationships only.
     */
    public List<RelationshipMeta> getSingularRelationships() {
        return relationships.stream()
                .filter(r -> !r.isCollection())
                .toList();
    }

    /**
     * Convenience: ONE_TO_MANY and MANY_TO_MANY relationships only.
     */
    public List<RelationshipMeta> getCollectionRelationships() {
        return relationships.stream()
                .filter(RelationshipMeta::isCollection)
                .toList();
    }

    /**
     * Returns true if this entity has at least one JPA relationship.
     */
    public boolean hasRelationships() {
        return !relationships.isEmpty();
    }

    /**
     * Returns the camelCase variable name for this entity.
     * e.g. {@code "Department"} → {@code "department"}
     */
    public String getVariableName() {
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    // -----------------------------------------------------------------------
    // Standard overrides
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassMeta classMeta = (ClassMeta) o;
        return Objects.equals(className, classMeta.className)
                && Objects.equals(packageName, classMeta.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, packageName);
    }

    @Override
    public String toString() {
        return "ClassMeta{"
                + "className='" + className + '\''
                + ", packageName='" + packageName + '\''
                + ", basePackage='" + basePackage + '\''
                + ", idType='" + idType + '\''
                + ", fields=" + fields.size()
                + ", relationships=" + relationships.size()
                + '}';
    }
}