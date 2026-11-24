package com.karan.intellijplatformplugin.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents metadata for a scalar (non-relationship) field in an entity.
 *
 * <p>Relationship fields (@OneToMany, @ManyToOne, etc.) are NOT stored here;
 * they live in {@link RelationshipMeta} inside {@link ClassMeta#getRelationships()}.
 * This separation keeps DTO generation clean — iterating {@link ClassMeta#getFields()}
 * never accidentally includes entity-typed fields.
 */
public class FieldMeta {

    /**
     * Fully-qualified or simple type names that are considered JPA entity
     * relationship types and should never appear as plain FieldMeta entries.
     * PsiDirectoryUtil uses this set to skip relationship fields during parsing.
     */
    public static final Set<String> RELATIONSHIP_ANNOTATIONS = Set.of(
            "OneToMany", "ManyToOne", "ManyToMany", "OneToOne",
            "jakarta.persistence.OneToMany",  "jakarta.persistence.ManyToOne",
            "jakarta.persistence.ManyToMany", "jakarta.persistence.OneToOne",
            "javax.persistence.OneToMany",    "javax.persistence.ManyToOne",
            "javax.persistence.ManyToMany",   "javax.persistence.OneToOne"
    );

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

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    /**
     * Returns the field name with the first letter capitalised.
     * Used to build getter/setter names: {@code getName()} → {@code "Name"}.
     */
    public String getCapitalizedName() {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // -----------------------------------------------------------------------
    // Type classification helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true if this field's type is a Java primitive or common
     * value type (String, numbers, dates, boolean, UUID).
     * Generators use this to skip fields that look like embedded entities
     * which were not caught during PSI parsing.
     */
    public boolean isScalarType() {
        return switch (type) {
            case "String", "Integer", "int", "Long", "long",
                 "Double", "double", "Float", "float",
                 "Boolean", "boolean", "Byte", "byte",
                 "Short", "short", "Character", "char",
                 "BigDecimal", "BigInteger",
                 "LocalDate", "LocalDateTime", "LocalTime",
                 "ZonedDateTime", "OffsetDateTime", "Instant",
                 "Date", "UUID" -> true;
            default -> false;
        };
    }

    /**
     * Returns true if the type looks like a JPA collection
     * (List, Set, Collection) — defensive guard used during PSI parsing
     * to detect relationship fields that slipped through annotation checks.
     */
    public boolean isCollectionType() {
        return type.startsWith("List")
                || type.startsWith("Set")
                || type.startsWith("Collection");
    }

    // -----------------------------------------------------------------------
    // Standard overrides
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldMeta fieldMeta = (FieldMeta) o;
        return Objects.equals(name, fieldMeta.name)
                && Objects.equals(type, fieldMeta.type);
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