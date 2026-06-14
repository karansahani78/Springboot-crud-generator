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

public final class PsiDirectoryUtil {

    private static final String JAKARTA_ENTITY   = "jakarta.persistence.Entity";
    private static final String JAVAX_ENTITY      = "javax.persistence.Entity";
    private static final String JAKARTA_ID        = "jakarta.persistence.Id";
    private static final String JAVAX_ID          = "javax.persistence.Id";
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
    // FIX 1: resolveBasePackage — strips .entity / .entities suffix
    // This ensures basePackage() in ClassMeta always points to the correct
    // parent package (e.g. com.karan.app) and NOT com.karan.app.entity.
    // Without this fix, every generated import like:
    //   import com.karan.app.entity.entity.User   <-- double .entity
    // or
    //   import com.karan.app.dto.UserDto           <-- basePackage wrong
    // =========================================================================
    /**
     * Resolves the "base" application package from a fully-qualified entity
     * package name by stripping a trailing {@code .entity} or {@code .entities}
     * segment.
     *
     * <p>Examples:
     * <pre>
     *   com.karan.app.entity   → com.karan.app
     *   com.karan.app.entities → com.karan.app
     *   com.karan.app.model    → com.karan.app.model  (no-op — not entity-named)
     *   com.karan.app          → com.karan.app         (no-op — no suffix)
     * </pre>
     *
     * @param packageName the fully-qualified package of the entity class
     * @return the base application package, never {@code null}
     */
    public static String resolveBasePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return packageName;
        if (packageName.endsWith(".entity")) {
            return packageName.substring(0, packageName.length() - ".entity".length());
        }
        if (packageName.endsWith(".entities")) {
            return packageName.substring(0, packageName.length() - ".entities".length());
        }
        // If the entity lives in e.g. com.karan.app.domain.model, we cannot
        // automatically strip — return as-is and let ClassMeta use it directly.
        return packageName;
    }

    // =========================================================================
    // Source root + directory creation (unchanged)
    // =========================================================================

    public static PsiDirectory getSourceRoot(PsiFile file) {
        if (file == null) return null;
        PsiDirectory dir = file.getContainingDirectory();
        while (dir != null) {
            if ("java".equals(dir.getName())) return dir;
            dir = dir.getParentDirectory();
        }
        return null;
    }

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
    // Project-wide entity scanning
    // =========================================================================

    public static List<ClassMeta> getAllEntityMetas(Project project) {
        if (project == null) return Collections.emptyList();

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        List<ClassMeta> result = new ArrayList<>();

        for (String entityFqn : new String[]{JAKARTA_ENTITY, JAVAX_ENTITY}) {
            PsiClass entityAnnotation = psiFacade.findClass(entityFqn, scope);
            if (entityAnnotation == null) continue;

            Query<PsiClass> query = AnnotatedElementsSearch.searchPsiClasses(entityAnnotation, scope);
            query.forEach(psiClass -> {
                try {
                    ClassMeta meta = toClassMeta(psiClass);
                    if (!result.contains(meta)) {
                        result.add(meta);
                    }
                } catch (Exception e) {
                    System.err.println("⚠️  Skipping entity class '"
                            + psiClass.getName() + "': " + e.getMessage());
                }
                return true;
            });
        }

        return Collections.unmodifiableList(result);
    }

    // =========================================================================
    // Single-class metadata extraction
    // FIX: basePackage is now resolved via resolveBasePackage() so downstream
    //      generators always use the correct parent package (not .entity).
    // =========================================================================

    public static ClassMeta toClassMeta(PsiClass psiClass) {
        if (psiClass == null) throw new IllegalArgumentException("PSI class cannot be null");

        String className = psiClass.getName();
        if (className == null) throw new IllegalStateException("Class name is null");

        PsiFile containingFile = psiClass.getContainingFile();
        if (!(containingFile instanceof PsiJavaFile javaFile)) {
            throw new IllegalStateException("Class '" + className + "' is not in a Java file");
        }

        String rawPackage = javaFile.getPackageName();
        if (rawPackage == null || rawPackage.isEmpty()) {
            throw new IllegalStateException("Package name is empty for class: " + className);
        }

        // FIX: derive the base (application-level) package correctly.
        // rawPackage  = "com.karan.app.entity"
        // basePackage = "com.karan.app"
        // ClassMeta stores BOTH so generators can build sub-packages correctly.
        String basePackage = resolveBasePackage(rawPackage);

        String idType = "Long";
        List<FieldMeta> scalarFields        = new ArrayList<>();
        List<RelationshipMeta> relationships = new ArrayList<>();

        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;

            String fieldName = field.getName();
            PsiType fieldType = field.getType();
            if (fieldName == null || fieldType == null) continue;

            if (hasIdAnnotation(field)) {
                idType = fieldType.getPresentableText();
                scalarFields.add(new FieldMeta(fieldName, idType));
                continue;
            }

            RelationshipType relType = detectRelationshipType(field);
            if (relType != null) {
                RelationshipMeta rel = buildRelationshipMeta(
                        field, fieldName, fieldType, relType, psiClass.getProject());
                if (rel != null) relationships.add(rel);
                continue;
            }

            scalarFields.add(new FieldMeta(fieldName, fieldType.getPresentableText()));
        }

        // Pass rawPackage (entity package) and basePackage separately.
        // ClassMeta must expose both — see note in ClassMeta about the constructor.
        return new ClassMeta(className, rawPackage, basePackage, idType, scalarFields, relationships);
    }

    // =========================================================================
    // Relationship detection helpers
    // =========================================================================

    private static RelationshipType detectRelationshipType(PsiField field) {
        if (hasAnyAnnotation(field, JAKARTA_ONE_TO_MANY, JAVAX_ONE_TO_MANY))   return RelationshipType.ONE_TO_MANY;
        if (hasAnyAnnotation(field, JAKARTA_MANY_TO_ONE, JAVAX_MANY_TO_ONE))   return RelationshipType.MANY_TO_ONE;
        if (hasAnyAnnotation(field, JAKARTA_MANY_TO_MANY, JAVAX_MANY_TO_MANY)) return RelationshipType.MANY_TO_MANY;
        if (hasAnyAnnotation(field, JAKARTA_ONE_TO_ONE, JAVAX_ONE_TO_ONE))     return RelationshipType.ONE_TO_ONE;
        return null;
    }

    private static RelationshipMeta buildRelationshipMeta(
            PsiField field,
            String fieldName,
            PsiType fieldType,
            RelationshipType relType,
            Project project
    ) {
        String relatedEntityName = resolveRelatedEntityName(fieldType, relType);
        if (relatedEntityName == null || relatedEntityName.isBlank()) {
            System.err.println("⚠️  Cannot resolve related entity name for field: " + fieldName);
            return null;
        }

        String mappedBy = extractMappedBy(field, relType);
        boolean owning  = mappedBy.isEmpty();
        String relatedIdType = resolveRelatedEntityIdType(relatedEntityName, project);

        return new RelationshipMeta(relType, relatedEntityName, fieldName, mappedBy, owning, relatedIdType);
    }

    // =========================================================================
    // Related entity name resolution
    // =========================================================================

    private static String resolveRelatedEntityName(PsiType fieldType, RelationshipType relType) {
        String presentable = fieldType.getPresentableText();
        if (relType == RelationshipType.ONE_TO_MANY || relType == RelationshipType.MANY_TO_MANY) {
            return extractGenericTypeName(presentable);
        } else {
            int lastDot = presentable.lastIndexOf('.');
            return lastDot >= 0 ? presentable.substring(lastDot + 1) : presentable;
        }
    }

    private static String extractGenericTypeName(String presentableType) {
        int open  = presentableType.indexOf('<');
        int close = presentableType.lastIndexOf('>');
        if (open < 0 || close < 0 || close <= open + 1) return null;
        String inner = presentableType.substring(open + 1, close).trim();
        int comma = inner.indexOf(',');
        if (comma > 0) inner = inner.substring(0, comma).trim();
        int lastDot = inner.lastIndexOf('.');
        return lastDot >= 0 ? inner.substring(lastDot + 1) : inner;
    }

    // =========================================================================
    // mappedBy extraction
    // =========================================================================

    private static String extractMappedBy(PsiField field, RelationshipType relType) {
        String[] fqns = switch (relType) {
            case ONE_TO_MANY  -> new String[]{JAKARTA_ONE_TO_MANY,  JAVAX_ONE_TO_MANY};
            case MANY_TO_MANY -> new String[]{JAKARTA_MANY_TO_MANY, JAVAX_MANY_TO_MANY};
            case ONE_TO_ONE   -> new String[]{JAKARTA_ONE_TO_ONE,   JAVAX_ONE_TO_ONE};
            default           -> new String[]{};
        };

        for (String fqn : fqns) {
            PsiAnnotation annotation = field.getAnnotation(fqn);
            if (annotation == null) continue;
            PsiAnnotationMemberValue mappedByValue = annotation.findAttributeValue("mappedBy");
            if (mappedByValue == null) continue;
            String raw = mappedByValue.getText();
            if (raw != null && raw.length() >= 2 && raw.startsWith("\"")) {
                return raw.substring(1, raw.length() - 1);
            }
        }
        return "";
    }

    // =========================================================================
    // Related entity @Id type resolution
    // =========================================================================

    private static String resolveRelatedEntityIdType(String relatedEntityName, Project project) {
        if (project == null || relatedEntityName == null) return "Long";

        try {
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            PsiClass[] candidates = psiFacade.findClasses(relatedEntityName, scope);

            for (PsiClass candidate : candidates) {
                if (!isEntityClass(candidate)) continue;
                for (PsiField field : candidate.getFields()) {
                    if (hasIdAnnotation(field) && field.getType() != null) {
                        return field.getType().getPresentableText();
                    }
                }
            }

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

        return "Long";
    }

    // =========================================================================
    // Annotation helpers
    // =========================================================================

    private static boolean hasIdAnnotation(PsiField field) {
        return hasAnyAnnotation(field, JAKARTA_ID, JAVAX_ID);
    }

    private static boolean isEntityClass(PsiClass psiClass) {
        return psiClass.hasAnnotation(JAKARTA_ENTITY) || psiClass.hasAnnotation(JAVAX_ENTITY);
    }

    private static boolean hasAnyAnnotation(PsiField field, String... fqns) {
        for (String fqn : fqns) {
            if (field.hasAnnotation(fqn)) return true;
        }
        return false;
    }
}