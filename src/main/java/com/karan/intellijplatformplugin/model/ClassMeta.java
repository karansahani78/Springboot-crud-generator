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
    private final String packageName;
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
     * Full constructor — used by PsiDirectoryUtil when relationships are known.
     */
    public ClassMeta(
            String className,
            String packageName,
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

        this.className     = className;
        this.packageName   = packageName;
        this.idType        = idType;
        this.fields        = fields        != null ? new ArrayList<>(fields)        : new ArrayList<>();
        this.relationships = relationships != null ? new ArrayList<>(relationships) : new ArrayList<>();
    }

    /**
     * Backward-compatible constructor — no relationships.
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
    // Core accessors
    // -----------------------------------------------------------------------

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
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
     * Returns the base package by stripping the last segment when it is
     * {@code "model"} or {@code "entity"}.
     *
     * <p>e.g. {@code com.example.entity} → {@code com.example}
     */
    public String basePackage() {
        if (packageName.endsWith(".model") || packageName.endsWith(".entity")) {
            return packageName.substring(0, packageName.lastIndexOf('.'));
        }
        return packageName;
    }

    /**
     * Returns scalar fields that are not the primary key.
     * Relationship fields are already excluded because they live in
     * {@link #relationships}, not in {@link #fields}.
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
     * These are the FK-owning sides that the service must fetch by ID
     * before saving the entity.
     */
    public List<RelationshipMeta> getSingularRelationships() {
        return relationships.stream()
                .filter(r -> !r.isCollection())
                .toList();
    }

    /**
     * Convenience: ONE_TO_MANY and MANY_TO_MANY relationships only.
     * These require fetching a {@code List} of related entities by their IDs.
     */
    public List<RelationshipMeta> getCollectionRelationships() {
        return relationships.stream()
                .filter(RelationshipMeta::isCollection)
                .toList();
    }

    /**
     * Returns true if this entity has at least one JPA relationship.
     * Used by generators to decide whether to emit extra repository injections.
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
                + ", idType='" + idType + '\''
                + ", fields=" + fields.size()
                + ", relationships=" + relationships.size()
                + '}';
    }
}