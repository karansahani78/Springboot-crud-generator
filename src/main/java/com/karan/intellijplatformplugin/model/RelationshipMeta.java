package com.karan.intellijplatformplugin.model;

import java.util.Objects;

/**
 * Represents a JPA relationship on an entity field.
 *
 * <p>Examples this covers:
 * <pre>
 *   @ManyToOne
 *   private Department department;          → MANY_TO_ONE, "Department", "department"
 *
 *   @OneToMany(mappedBy = "department")
 *   private List<Employee> employees;       → ONE_TO_MANY,  "Employee",  "employees"
 *
 *   @ManyToMany
 *   private List<Role> roles;              → MANY_TO_MANY, "Role",      "roles"
 *
 *   @OneToOne
 *   private Address address;               → ONE_TO_ONE,  "Address",   "address"
 * </pre>
 *
 * <p>Generators use this to:
 * <ul>
 *   <li>DTOs     → emit {@code Long departmentId} instead of {@code Department department}</li>
 *   <li>Services → call {@code departmentRepository.findById(dto.getDepartmentId())}</li>
 *   <li>Mappers  → skip relationship fields (set manually in service)</li>
 * </ul>
 */
public class RelationshipMeta {

    /**
     * JPA relationship types supported by the generator.
     */
    public enum RelationshipType {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }

    // -----------------------------------------------------------------------
    // Core fields
    // -----------------------------------------------------------------------

    /** The JPA annotation type, e.g. MANY_TO_ONE. */
    private final RelationshipType relationshipType;

    /**
     * Simple name of the related entity class, e.g. {@code "Department"}.
     * Used to build repository names ({@code departmentRepository}),
     * DTO field names ({@code departmentId}), and import statements.
     */
    private final String relatedEntityName;

    /**
     * The field name on THIS entity, e.g. {@code "department"} or {@code "employees"}.
     * Used to generate getter/setter names and DTO field names.
     */
    private final String fieldName;

    /**
     * The {@code mappedBy} value from the annotation (may be null/empty).
     * Present on the inverse side of a bidirectional relationship.
     * Generators use this to identify the owning side.
     */
    private final String mappedBy;

    /**
     * Whether this side is the owning side of the relationship.
     * Owning side = the side that does NOT have {@code mappedBy}.
     * Only the owning side generates a foreign-key / join-table column.
     */
    private final boolean owning;

    /**
     * The ID type of the related entity (e.g. {@code "Long"}, {@code "UUID"}).
     * Used when generating {@code Long departmentId} in DTOs.
     * Defaults to {@code "Long"} when it cannot be resolved.
     */
    private final String relatedEntityIdType;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public RelationshipMeta(
            RelationshipType relationshipType,
            String relatedEntityName,
            String fieldName,
            String mappedBy,
            boolean owning,
            String relatedEntityIdType
    ) {
        Objects.requireNonNull(relationshipType,  "relationshipType must not be null");
        Objects.requireNonNull(relatedEntityName, "relatedEntityName must not be null");
        Objects.requireNonNull(fieldName,         "fieldName must not be null");

        this.relationshipType    = relationshipType;
        this.relatedEntityName   = relatedEntityName;
        this.fieldName           = fieldName;
        this.mappedBy            = (mappedBy == null) ? "" : mappedBy;
        this.owning              = owning;
        this.relatedEntityIdType = (relatedEntityIdType == null || relatedEntityIdType.isBlank())
                ? "Long"
                : relatedEntityIdType;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public String getRelatedEntityName() {
        return relatedEntityName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getMappedBy() {
        return mappedBy;
    }

    public boolean isOwning() {
        return owning;
    }

    public String getRelatedEntityIdType() {
        return relatedEntityIdType;
    }

    // -----------------------------------------------------------------------
    // Derived helpers (used by generators — avoids repeated string logic)
    // -----------------------------------------------------------------------

    /**
     * Returns the repository bean name for the related entity.
     * e.g. "Department" → "departmentRepository"
     */
    public String getRelatedRepositoryFieldName() {
        String lower = Character.toLowerCase(relatedEntityName.charAt(0))
                + relatedEntityName.substring(1);
        return lower + "Repository";
    }

    /**
     * Returns the repository interface name for the related entity.
     * e.g. "Department" → "DepartmentRepository"
     */
    public String getRelatedRepositoryClassName() {
        return relatedEntityName + "Repository";
    }

    /**
     * For MANY_TO_ONE / ONE_TO_ONE (singular): returns the DTO field name
     * that carries the FK id, e.g. "department" → "departmentId".
     *
     * For ONE_TO_MANY / MANY_TO_MANY (collection): returns the DTO field
     * name that carries the list of IDs, e.g. "employees" → "employeeIds".
     */
    public String getDtoIdFieldName() {
        if (isCollection()) {
            // Strip trailing 's' if the field name is a simple plural,
            // then append "Ids". Works for common English plurals.
            // e.g. "employees" → "employeeIds", "roles" → "roleIds"
            String base = fieldName.endsWith("s")
                    ? fieldName.substring(0, fieldName.length() - 1)
                    : fieldName;
            return base + "Ids";
        }
        // "department" → "departmentId"
        return fieldName + "Id";
    }

    /**
     * Returns the capitalised DTO id field name for getters/setters.
     * e.g. "departmentId" → "DepartmentId"
     */
    public String getCapitalisedDtoIdFieldName() {
        String raw = getDtoIdFieldName();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /**
     * Returns true for ONE_TO_MANY and MANY_TO_MANY (collection-side).
     * DTOs emit {@code List<Long>} for these.
     */
    public boolean isCollection() {
        return relationshipType == RelationshipType.ONE_TO_MANY
                || relationshipType == RelationshipType.MANY_TO_MANY;
    }

    /**
     * Returns true if this relationship should generate a DTO ID field.
     * Inverse ONE_TO_MANY (has mappedBy) are read-only and are excluded
     * from create/update DTOs to avoid circular writes.
     */
    public boolean isIncludedInDto() {
        if (relationshipType == RelationshipType.ONE_TO_MANY && !mappedBy.isEmpty()) {
            // Inverse side — do not include in DTO; managed by the owning side
            return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Standard overrides
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RelationshipMeta that = (RelationshipMeta) o;
        return owning == that.owning
                && relationshipType == that.relationshipType
                && Objects.equals(relatedEntityName, that.relatedEntityName)
                && Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relationshipType, relatedEntityName, fieldName, owning);
    }

    @Override
    public String toString() {
        return "RelationshipMeta{"
                + "type=" + relationshipType
                + ", relatedEntity='" + relatedEntityName + '\''
                + ", fieldName='" + fieldName + '\''
                + ", mappedBy='" + mappedBy + '\''
                + ", owning=" + owning
                + ", idType='" + relatedEntityIdType + '\''
                + '}';
    }
}