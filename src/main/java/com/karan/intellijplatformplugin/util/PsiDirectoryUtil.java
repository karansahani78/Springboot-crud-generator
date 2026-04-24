package com.karan.intellijplatformplugin.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.Query;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.FieldMeta;
import com.karan.intellijplatformplugin.model.RelationshipMeta;
import com.karan.intellijplatformplugin.model.RelationshipMeta.RelationshipType;

import java.util.*;

/**
 * Utility class for working with PSI directories and extracting class metadata.
 *
 * <p>Multi-entity additions:
 * <ul>
 *   <li>{@link #getAllEntityMetas(Project)} — scans the entire project for
 *       {@code @Entity}-annotated classes and returns a {@link ClassMeta} for each.</li>
 *   <li>{@link #toClassMeta(PsiClass)} — now detects JPA relationship annotations
 *       ({@code @OneToMany}, {@code @ManyToOne}, {@code @ManyToMany}, {@code @OneToOne})
 *       and populates {@link RelationshipMeta} entries instead of adding those fields
 *       to the scalar {@link FieldMeta} list.</li>
 *   <li>{@link #resolveRelatedEntityIdType(PsiClass, Project)} — inspects the related
 *       entity's PSI class to find the type of its {@code @Id} field so DTOs can emit
 *       the correct ID type (e.g. {@code Long} vs {@code UUID}).</li>
 * </ul>
 */
public final class PsiDirectoryUtil {

    // ── JPA annotation FQNs ──────────────────────────────────────────────────

    // Entity marker
    private static final String JAKARTA_ENTITY = "jakarta.persistence.Entity";
    private static final String JAVAX_ENTITY    = "javax.persistence.Entity";

    // ID marker
    private static final String JAKARTA_ID = "jakarta.persistence.Id";
    private static final String JAVAX_ID   = "javax.persistence.Id";

    // Relationship annotations
    private static final String JAKARTA_ONE_TO_MANY  = "jakarta.persistence.OneToMany";
    private static final String JAVAX_ONE_TO_MANY    = "javax.persistence.OneToMany";
    private static final String JAKARTA_MANY_TO_ONE  = "jakarta.persistence.ManyToOne";
    private static final String JAVAX_MANY_TO_ONE    = "javax.persistence.ManyToOne";
    private static final String JAKARTA_MANY_TO_MANY = "jakarta.persistence.ManyToMany";
    private static final String JAVAX_MANY_TO_MANY   = "javax.persistence.ManyToMany";
    private static final String JAKARTA_ONE_TO_ONE   = "jakarta.persistence.OneToOne";
    private static final String JAVAX_ONE_TO_ONE     = "javax.persistence.OneToOne";

    private PsiDirectoryUtil() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    // =========================================================================
    // Source root + directory creation (unchanged)
    // =========================================================================

    /**
     * Walks up the directory tree to find the {@code java} source root.
     *
     * @param file any PSI file inside the source tree
     * @return the {@code java} directory, or {@code null} if not found
     */
    public static PsiDirectory getSourceRoot(PsiFile file) {
        if (file == null) return null;

        PsiDirectory dir = file.getContainingDirectory();
        while (dir != null) {
            if ("java".equals(dir.getName())) return dir;
            dir = dir.getParentDirectory();
        }
        return null;
    }

    /**
     * Creates (or reuses) all package-segment subdirectories under {@code root}.
     *
     * @param root top-level source directory (the {@code java} folder)
     * @param pkg  dot-separated package name, e.g. {@code com.example.service}
     * @return the leaf {@link PsiDirectory} for {@code pkg}
     */
    public static PsiDirectory createPackageDirs(PsiDirectory root, String pkg) {
        if (root == null) throw new IllegalArgumentException("Root directory cannot be null");
        if (pkg == null || pkg.isBlank()) return root;

        PsiDirectory current = root;
        for (String part : pkg.split("\\.")) {
            if (part.isEmpty()) continue;
            PsiDirectory next = current.findSubdirectory(part);
            if (next == null) next = current.createSubdirectory(part);
            current = next;
        }
        return current;
    }

    // =========================================================================
    // Project-wide entity scanning  ← NEW
    // =========================================================================

    /**
     * Scans the entire project for classes annotated with {@code @Entity}
     * (both {@code jakarta.persistence} and {@code javax.persistence} variants)
     * and returns a {@link ClassMeta} for each one found.
     *
     * <p>This method is the foundation of multi-entity support: by collecting
     * all entity metas up front, every generator receives full relationship
     * context without re-parsing PSI on each call.
     *
     * <p>Uses {@link AnnotatedElementsSearch} which queries the IntelliJ index —
     * the index must be ready (i.e. this must not be called during indexing).
     * If a class cannot be parsed it is silently skipped so a single malformed
     * entity never aborts the whole generation run.
     *
     * @param project the current IntelliJ project
     * @return unmodifiable list of {@link ClassMeta}, one per {@code @Entity} class;
     *         never {@code null}, may be empty
     */
    public static List<ClassMeta> getAllEntityMetas(Project project) {
        if (project == null) return Collections.emptyList();

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

        List<ClassMeta> result = new ArrayList<>();

        // Search for both jakarta and javax @Entity annotations
        for (String entityFqn : new String[]{JAKARTA_ENTITY, JAVAX_ENTITY}) {

            PsiClass entityAnnotation = psiFacade.findClass(entityFqn, scope);
            if (entityAnnotation == null) {
                // Annotation not on the classpath for this variant — skip
                continue;
            }

            // AnnotatedElementsSearch finds all classes carrying this annotation
            Query<PsiClass> query = AnnotatedElementsSearch.searchPsiClasses(
                    entityAnnotation, scope);

            query.forEach(psiClass -> {
                try {
                    ClassMeta meta = toClassMeta(psiClass);
                    // Deduplicate — same class may appear under both jakarta + javax
                    if (!result.contains(meta)) {
                        result.add(meta);
                    }
                } catch (Exception e) {
                    // Log and skip — a broken entity must not abort generation
                    System.err.println("⚠️  Skipping entity class '"
                            + psiClass.getName() + "': " + e.getMessage());
                }
                return true; // continue iteration
            });
        }

        return Collections.unmodifiableList(result);
    }

    // =========================================================================
    // Single-class metadata extraction  ← HEAVILY UPDATED
    // =========================================================================

    /**
     * Extracts complete metadata from a PSI class, including:
     * <ul>
     *   <li>Scalar fields (primitive wrappers, String, dates, UUID, …)</li>
     *   <li>JPA relationship fields → {@link RelationshipMeta} entries</li>
     *   <li>Primary key type detection via {@code @Id}</li>
     * </ul>
     *
     * <p>Relationship fields are <em>not</em> added to {@link ClassMeta#getFields()}.
     * They live exclusively in {@link ClassMeta#getRelationships()} so that
     * every downstream generator can handle them correctly.
     *
     * @param psiClass the PSI class to inspect (must not be {@code null})
     * @return fully populated {@link ClassMeta}
     * @throws IllegalArgumentException if {@code psiClass} is {@code null}
     * @throws IllegalStateException    if the class has no name or package
     */
    public static ClassMeta toClassMeta(PsiClass psiClass) {
        if (psiClass == null) throw new IllegalArgumentException("PSI class cannot be null");

        // ── Class identity ────────────────────────────────────────────────
        String className = psiClass.getName();
        if (className == null) throw new IllegalStateException("Class name is null");

        PsiFile containingFile = psiClass.getContainingFile();
        if (!(containingFile instanceof PsiJavaFile javaFile)) {
            throw new IllegalStateException("Class '" + className + "' is not in a Java file");
        }

        String packageName = javaFile.getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalStateException("Package name is empty for class: " + className);
        }

        // ── Field iteration ───────────────────────────────────────────────
        String idType = "Long";                          // safe default
        List<FieldMeta> scalarFields    = new ArrayList<>();
        List<RelationshipMeta> relationships = new ArrayList<>();

        for (PsiField field : psiClass.getFields()) {

            // 1. Skip static / synthetic fields
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;

            String fieldName = field.getName();
            PsiType fieldType = field.getType();
            if (fieldName == null || fieldType == null) continue;

            // 2. Capture @Id type before deciding field bucket
            if (hasIdAnnotation(field)) {
                idType = fieldType.getPresentableText();
                // The id field itself goes into scalarFields so mappers can
                // reference it (Mapper.toDto copies it for GET responses).
                scalarFields.add(new FieldMeta(fieldName, idType));
                continue;
            }

            // 3. Check for JPA relationship annotations — these go into
            //    RelationshipMeta, NOT into scalarFields.
            RelationshipType relType = detectRelationshipType(field);

            if (relType != null) {
                RelationshipMeta rel = buildRelationshipMeta(
                        field, fieldName, fieldType, relType, psiClass.getProject());
                if (rel != null) relationships.add(rel);
                // Do NOT add to scalarFields — generators handle these separately
                continue;
            }

            // 4. Everything else is a scalar field
            scalarFields.add(new FieldMeta(fieldName, fieldType.getPresentableText()));
        }

        return new ClassMeta(className, packageName, idType, scalarFields, relationships);
    }

    // =========================================================================
    // Relationship detection helpers  ← NEW
    // =========================================================================

    /**
     * Returns the {@link RelationshipType} if the field carries any JPA
     * relationship annotation, or {@code null} if it is a plain scalar field.
     */
    private static RelationshipType detectRelationshipType(PsiField field) {
        if (hasAnyAnnotation(field, JAKARTA_ONE_TO_MANY, JAVAX_ONE_TO_MANY))   return RelationshipType.ONE_TO_MANY;
        if (hasAnyAnnotation(field, JAKARTA_MANY_TO_ONE, JAVAX_MANY_TO_ONE))   return RelationshipType.MANY_TO_ONE;
        if (hasAnyAnnotation(field, JAKARTA_MANY_TO_MANY, JAVAX_MANY_TO_MANY)) return RelationshipType.MANY_TO_MANY;
        if (hasAnyAnnotation(field, JAKARTA_ONE_TO_ONE, JAVAX_ONE_TO_ONE))     return RelationshipType.ONE_TO_ONE;
        return null;
    }

    /**
     * Constructs a {@link RelationshipMeta} from a relationship-annotated field.
     *
     * <p>Steps:
     * <ol>
     *   <li>Extract the related entity's simple class name from the field type
     *       (unwrapping {@code List<Department>} → {@code "Department"})</li>
     *   <li>Read {@code mappedBy} attribute from the annotation if present</li>
     *   <li>Determine owning side ({@code mappedBy} absent = owning)</li>
     *   <li>Resolve the related entity's {@code @Id} type via PSI lookup</li>
     * </ol>
     *
     * @return populated {@link RelationshipMeta}, or {@code null} if the
     *         related entity name cannot be resolved (malformed generic type etc.)
     */
    private static RelationshipMeta buildRelationshipMeta(
            PsiField field,
            String fieldName,
            PsiType fieldType,
            RelationshipType relType,
            Project project
    ) {
        // ── 1. Resolve related entity name ───────────────────────────────
        String relatedEntityName = resolveRelatedEntityName(fieldType, relType);
        if (relatedEntityName == null || relatedEntityName.isBlank()) {
            System.err.println("⚠️  Cannot resolve related entity name for field: " + fieldName);
            return null;
        }

        // ── 2. Read mappedBy from the annotation ──────────────────────────
        String mappedBy = extractMappedBy(field, relType);

        // ── 3. Owning side = no mappedBy present ─────────────────────────
        boolean owning = mappedBy.isEmpty();

        // ── 4. Resolve related entity's @Id type ─────────────────────────
        String relatedIdType = resolveRelatedEntityIdType(relatedEntityName, project);

        return new RelationshipMeta(
                relType,
                relatedEntityName,
                fieldName,
                mappedBy,
                owning,
                relatedIdType
        );
    }

    // =========================================================================
    // Related entity name resolution  ← NEW
    // =========================================================================

    /**
     * Extracts the simple class name of the related entity from a field's PSI type.
     *
     * <p>Handles both:
     * <ul>
     *   <li>Singular references: {@code Department department} → {@code "Department"}</li>
     *   <li>Collection generics: {@code List<Employee>} → {@code "Employee"}</li>
     * </ul>
     */
    private static String resolveRelatedEntityName(PsiType fieldType, RelationshipType relType) {
        String presentable = fieldType.getPresentableText();
        // presentable examples: "Department", "List<Employee>", "Set<Role>"

        if (relType == RelationshipType.ONE_TO_MANY || relType == RelationshipType.MANY_TO_MANY) {
            // Unwrap the generic parameter: "List<Employee>" → "Employee"
            return extractGenericTypeName(presentable);
        } else {
            // MANY_TO_ONE / ONE_TO_ONE — type IS the entity name (possibly qualified)
            // Strip package prefix if present: "com.example.entity.Department" → "Department"
            int lastDot = presentable.lastIndexOf('.');
            return lastDot >= 0 ? presentable.substring(lastDot + 1) : presentable;
        }
    }

    /**
     * Extracts the type argument from a generic type string.
     *
     * <pre>
     *   "List&lt;Employee&gt;"  → "Employee"
     *   "Set&lt;Role&gt;"      → "Role"
     *   "Employee"           → null  (no generic)
     * </pre>
     */
    private static String extractGenericTypeName(String presentableType) {
        int open  = presentableType.indexOf('<');
        int close = presentableType.lastIndexOf('>');
        if (open < 0 || close < 0 || close <= open + 1) return null;

        String inner = presentableType.substring(open + 1, close).trim();
        // Handle nested generics like "List<Map<K,V>>" — take only the first token
        int comma = inner.indexOf(',');
        if (comma > 0) inner = inner.substring(0, comma).trim();

        // Strip any package prefix
        int lastDot = inner.lastIndexOf('.');
        return lastDot >= 0 ? inner.substring(lastDot + 1) : inner;
    }

    // =========================================================================
    // mappedBy extraction  ← NEW
    // =========================================================================

    /**
     * Reads the {@code mappedBy} attribute value from the JPA relationship
     * annotation on the given field.
     *
     * @return the {@code mappedBy} string, or {@code ""} if not present
     */
    private static String extractMappedBy(PsiField field, RelationshipType relType) {
        // Determine which annotation FQNs to look at
        String[] fqns = switch (relType) {
            case ONE_TO_MANY  -> new String[]{JAKARTA_ONE_TO_MANY,  JAVAX_ONE_TO_MANY};
            case MANY_TO_MANY -> new String[]{JAKARTA_MANY_TO_MANY, JAVAX_MANY_TO_MANY};
            case ONE_TO_ONE   -> new String[]{JAKARTA_ONE_TO_ONE,   JAVAX_ONE_TO_ONE};
            // MANY_TO_ONE never has mappedBy — always the owning side
            default           -> new String[]{};
        };

        for (String fqn : fqns) {
            PsiAnnotation annotation = field.getAnnotation(fqn);
            if (annotation == null) continue;

            PsiAnnotationMemberValue mappedByValue =
                    annotation.findAttributeValue("mappedBy");
            if (mappedByValue == null) continue;

            // The value is a PsiLiteralExpression — strip surrounding quotes
            String raw = mappedByValue.getText();
            if (raw != null && raw.length() >= 2 && raw.startsWith("\"")) {
                return raw.substring(1, raw.length() - 1);
            }
        }
        return "";
    }

    // =========================================================================
    // Related entity @Id type resolution  ← NEW
    // =========================================================================

    /**
     * Looks up the related entity class in the PSI index and returns the
     * presentable type of its {@code @Id}-annotated field.
     *
     * <p>Falls back to {@code "Long"} when:
     * <ul>
     *   <li>The related class is not found in the project scope</li>
     *   <li>No {@code @Id} field is present on the related class</li>
     *   <li>The PSI index is not yet ready</li>
     * </ul>
     *
     * @param relatedEntityName simple class name, e.g. {@code "Department"}
     * @param project           current IntelliJ project
     * @return presentable type string, e.g. {@code "Long"} or {@code "UUID"}
     */
    private static String resolveRelatedEntityIdType(String relatedEntityName, Project project) {
        if (project == null || relatedEntityName == null) return "Long";

        try {
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

            // Search by short name — works even without the package prefix
            PsiClass[] candidates = psiFacade.findClasses(relatedEntityName, scope);

            for (PsiClass candidate : candidates) {
                // Prefer classes that are actually @Entity-annotated
                if (!isEntityClass(candidate)) continue;

                for (PsiField field : candidate.getFields()) {
                    if (hasIdAnnotation(field) && field.getType() != null) {
                        return field.getType().getPresentableText();
                    }
                }
            }

            // Fallback — check any class with that name even without @Entity
            // (handles edge cases where the annotation is on a parent class)
            for (PsiClass candidate : candidates) {
                for (PsiField field : candidate.getFields()) {
                    if (hasIdAnnotation(field) && field.getType() != null) {
                        return field.getType().getPresentableText();
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️  Could not resolve @Id type for '"
                    + relatedEntityName + "': " + e.getMessage());
        }

        return "Long"; // safe default
    }

    // =========================================================================
    // Annotation helper methods  ← NEW / UPDATED
    // =========================================================================

    /**
     * Returns {@code true} if the field has a {@code @Id} annotation from
     * either the {@code jakarta.persistence} or {@code javax.persistence} namespace.
     */
    private static boolean hasIdAnnotation(PsiField field) {
        return hasAnyAnnotation(field, JAKARTA_ID, JAVAX_ID);
    }

    /**
     * Returns {@code true} if the given PSI class is annotated with
     * {@code @Entity} from either JPA namespace.
     */
    private static boolean isEntityClass(PsiClass psiClass) {
        return psiClass.hasAnnotation(JAKARTA_ENTITY)
                || psiClass.hasAnnotation(JAVAX_ENTITY);
    }

    /**
     * Returns {@code true} if the field carries ANY of the given annotation FQNs.
     * Short-circuits on the first match.
     */
    private static boolean hasAnyAnnotation(PsiField field, String... fqns) {
        for (String fqn : fqns) {
            if (field.hasAnnotation(fqn)) return true;
        }
        return false;
    }
}