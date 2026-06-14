package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates the {@code Role} enum used by Spring Security for role-based
 * access control.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>PSI directory guard added — prevents {@code dir.add()} collision within the
 *       same {@code WriteCommandAction} when multiple entities are generated.</li>
 *   <li>Generated {@code Role} enum now implements {@code GrantedAuthority} so it
 *       can be used directly with Spring Security without an adapter class.</li>
 *   <li>Added {@code getAuthority()} with {@code ROLE_} prefix — required by Spring
 *       Security's {@code hasRole()} / {@code hasAuthority()} checks.</li>
 *   <li>Added {@code fromString()} factory for safe parsing of JWT claim strings.</li>
 * </ul>
 */
public class RoleEnumGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".entity";
        String fileName = "Role.java";

        // Disk guard — file already written in a previous run
        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        // PSI directory guard — file already added in this WriteCommandAction
        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import org.springframework.security.core.GrantedAuthority;
                
                /**
                 * Application roles used for Spring Security authorization.
                 *
                 * <p>Implements {@link GrantedAuthority} so role enum values can be used
                 * directly wherever Spring Security expects an authority — no adapter needed.
                 *
                 * <p>Authority strings follow the {@code ROLE_} prefix convention required
                 * by Spring Security's {@code hasRole()} expression:
                 * <pre>
                 *   .requestMatchers("/api/admin/**").hasRole("ADMIN")
                 *   // resolves to GrantedAuthority "ROLE_ADMIN"
                 * </pre>
                 */
                public enum Role implements GrantedAuthority {
                
                    /**
                     * Standard authenticated user — read access to own resources.
                     */
                    USER,
                
                    /**
                     * Administrator — full access to all resources and management endpoints.
                     */
                    ADMIN,
                
                    /**
                     * Moderator — elevated read access and limited write permissions.
                     */
                    MODERATOR;
                
                    /**
                     * Returns the authority string expected by Spring Security.
                     *
                     * <p>Prefixes the enum name with {@code ROLE_} so that
                     * {@code hasRole("ADMIN")} correctly matches a user with
                     * the {@code ADMIN} role without the caller needing to know
                     * the prefix convention.
                     *
                     * @return authority string, e.g. {@code "ROLE_ADMIN"}
                     */
                    @Override
                    public String getAuthority() {
                        return "ROLE_" + this.name();
                    }
                
                    /**
                     * Case-insensitive factory that converts a string role name
                     * (e.g. from a JWT claim or database column) to its enum constant.
                     *
                     * <p>Strips a leading {@code ROLE_} prefix if present so that
                     * both {@code "ADMIN"} and {@code "ROLE_ADMIN"} resolve correctly.
                     *
                     * @param value raw string value to parse
                     * @return matching {@code Role} constant
                     * @throws IllegalArgumentException if the value does not match any role
                     */
                    public static Role fromString(String value) {
                        if (value == null || value.isBlank()) {
                            throw new IllegalArgumentException("Role value must not be null or blank");
                        }
                        // Strip ROLE_ prefix if present
                        String normalised = value.trim().toUpperCase();
                        if (normalised.startsWith("ROLE_")) {
                            normalised = normalised.substring(5);
                        }
                        try {
                            return Role.valueOf(normalised);
                        } catch (IllegalArgumentException ex) {
                            throw new IllegalArgumentException(
                                    "Unknown role: '" + value + "'. Valid values: USER, ADMIN, MODERATOR");
                        }
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }

    /**
     * Checks whether a file with the given name already exists inside
     * the PSI directory's current in-memory children.
     */
    private static boolean fileExistsInPsiDirectory(PsiDirectory dir, String fileName) {
        if (dir == null || fileName == null) return false;
        for (PsiFile existing : dir.getFiles()) {
            if (fileName.equals(existing.getName())) return true;
        }
        return false;
    }
}