package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.FieldMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates Spring Data JPA Repository interfaces.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>Extends both {@code JpaRepository} and {@code JpaSpecificationExecutor} for
 *       filtering/search support without additional boilerplate.</li>
 *   <li>Generates {@code findBy} query method stubs for each unique scalar field
 *       so developers have ready-made query methods without writing them manually.</li>
 *   <li>PSI directory duplicate guard prevents {@code dir.add()} collision when
 *       the same entity is processed more than once in a run.</li>
 * </ul>
 */
public class RepositoryGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".repository";
        String fileName = meta.getClassName() + "Repository.java";

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        // Guard against duplicate generation within same WriteCommandAction
        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String className = meta.getClassName();
        String idType    = meta.getIdType();

        // Build findBy query stubs for non-id scalar fields
        String queryMethods = buildQueryMethods(meta);

        String code = String.format("""
                package %s;
                
                import %s.%s;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.Pageable;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                import org.springframework.data.jpa.repository.Query;
                import org.springframework.stereotype.Repository;
                import java.util.List;
                import java.util.Optional;
                
                /**
                 * Repository for {@link %s} entity.
                 *
                 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic
                 * filtering via {@code Specification} predicates without custom JPQL.
                 */
                @Repository
                public interface %sRepository extends
                        JpaRepository<%s, %s>,
                        JpaSpecificationExecutor<%s> {
                
                %s
                }
                """,
                pkg,
                meta.getPackageName(), className,   // entity import
                className,                          // Javadoc @link
                className,                          // interface name
                className, idType,                  // JpaRepository<Entity, Id>
                className,                          // JpaSpecificationExecutor<Entity>
                queryMethods                        // findBy stubs
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }

    // =========================================================================
    // Query method stub builder
    // =========================================================================

    /**
     * Generates {@code findBy} query method stubs for each non-id scalar field.
     *
     * <p>Only fields whose types are unambiguously searchable are included:
     * String fields get both exact-match and contains variants; numeric/boolean
     * fields get exact-match only.
     *
     * <p>Example output for an {@code Employee} entity with fields
     * {@code name:String}, {@code email:String}, {@code active:Boolean}:
     * <pre>
     *   Optional&lt;Employee&gt; findByName(String name);
     *   List&lt;Employee&gt; findByNameContainingIgnoreCase(String name, Pageable pageable);
     *   Optional&lt;Employee&gt; findByEmail(String email);
     *   List&lt;Employee&gt; findByEmailContainingIgnoreCase(String email, Pageable pageable);
     *   List&lt;Employee&gt; findByActive(Boolean active);
     * </pre>
     */
    private static String buildQueryMethods(ClassMeta meta) {
        StringBuilder sb = new StringBuilder();
        String className = meta.getClassName();

        for (FieldMeta f : meta.getNonIdFields()) {
            String type            = f.getType();
            String capitalizedName = f.getCapitalizedName();

            // Skip collection/complex types — only generate for scalar types
            if (!f.isScalarType()) continue;

            if ("String".equals(type)) {
                // Exact match — useful for unique fields like email/username
                sb.append(String.format(
                        "    /** Finds a %s by exact {@code %s} match. */\n"
                                + "    Optional<%s> findBy%s(String %s);\n\n",
                        className, f.getName(),
                        className, capitalizedName, f.getName()
                ));

                // Partial match with pagination — useful for search/autocomplete
                sb.append(String.format(
                        "    /** Finds all %s where {@code %s} contains the given substring (case-insensitive). */\n"
                                + "    Page<%s> findBy%sContainingIgnoreCase(String %s, Pageable pageable);\n\n",
                        className, f.getName(),
                        className, capitalizedName, f.getName()
                ));

            } else if (isNumericType(type)) {
                sb.append(String.format(
                        "    /** Finds all %s by {@code %s}. */\n"
                                + "    List<%s> findBy%s(%s %s);\n\n",
                        className, f.getName(),
                        className, capitalizedName, type, f.getName()
                ));

            } else if ("Boolean".equals(type) || "boolean".equals(type)) {
                sb.append(String.format(
                        "    /** Finds all %s where {@code %s} matches the given value. */\n"
                                + "    List<%s> findBy%s(Boolean %s);\n\n",
                        className, f.getName(),
                        className, capitalizedName, f.getName()
                ));
            }
            // LocalDate, LocalDateTime, UUID etc. — skip stubs; too context-specific
        }

        // Always add a paginated findAll override for convenience
        sb.append(String.format("""
                    /**
                     * Returns all %s entities matching the given {@link Pageable} settings.
                     * Provided by {@code JpaRepository} — listed here for discoverability.
                     */
                    Page<%s> findAll(Pageable pageable);
                """, className, className));

        return sb.toString();
    }

    /**
     * Returns {@code true} for Java numeric wrapper and primitive types
     * that are safe to use as single-value {@code findBy} parameters.
     */
    private static boolean isNumericType(String type) {
        return switch (type) {
            case "Integer", "int", "Long", "long",
                 "Double", "double", "Float", "float",
                 "Short", "short", "Byte", "byte",
                 "BigDecimal", "BigInteger" -> true;
            default -> false;
        };
    }

    /**
     * Checks whether a file with the given name already exists inside
     * the PSI directory's current in-memory children.
     *
     * <p>Guards against duplicate {@code dir.add()} calls within the same
     * {@code WriteCommandAction} where disk flush has not yet occurred.
     */
    private static boolean fileExistsInPsiDirectory(PsiDirectory dir, String fileName) {
        if (dir == null || fileName == null) return false;
        for (PsiFile existing : dir.getFiles()) {
            if (fileName.equals(existing.getName())) return true;
        }
        return false;
    }
}