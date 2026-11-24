package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates {@code AppUserRepository} for Spring Security user lookup.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>Extends {@code JpaSpecificationExecutor} — enables admin user search
 *       by role, status, or date range without custom JPQL.</li>
 *   <li>Added {@code findByRole()} — useful for admin dashboards listing
 *       users by role.</li>
 *   <li>Added {@code findByEnabledFalse()} — for admin user management views.</li>
 * </ul>
 */
public class AppUserRepositoryGenerator {

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
        String fileName = "AppUserRepository.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String base = meta.basePackage();

        String code = String.format("""
                package %s;
                
                import %s.entity.AppUser;
                import %s.entity.Role;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
                import org.springframework.stereotype.Repository;
                import java.util.List;
                import java.util.Optional;
                
                /**
                 * Repository for {@link AppUser} — the Spring Security principal entity.
                 *
                 * <p>Extends {@link JpaSpecificationExecutor} to support dynamic admin
                 * queries (e.g. filter by role + enabled status) without custom JPQL.
                 *
                 * <p>All query methods ({@code findBy*}, {@code existsBy*}) are read-only
                 * and use Spring Data's default transaction management.
                 */
                @Repository
                public interface AppUserRepository extends
                        JpaRepository<AppUser, Long>,
                        JpaSpecificationExecutor<AppUser> {
                
                    // ── Spring Security lookup ────────────────────────────────────────
                
                    /**
                     * Loads a user by login name — called by {@code UserDetailsServiceImpl}
                     * on every authenticated request. Backed by the unique index on
                     * {@code username} for O(log n) lookup.
                     */
                    Optional<AppUser> findByUsername(String username);
                
                    /**
                     * Loads a user by email — used during password-reset flows.
                     */
                    Optional<AppUser> findByEmail(String email);
                
                    // ── Duplicate checks ──────────────────────────────────────────────
                
                    /** Returns {@code true} if any user has the given username. */
                    boolean existsByUsername(String username);
                
                    /** Returns {@code true} if any user has the given email. */
                    boolean existsByEmail(String email);
                
                    // ── Admin queries ─────────────────────────────────────────────────
                
                    /**
                     * Returns all users assigned the given role.
                     * Useful for admin dashboards listing users by role.
                     */
                    List<AppUser> findByRole(Role role);
                
                    /**
                     * Returns all disabled user accounts.
                     * Useful for admin account management views.
                     */
                    List<AppUser> findByEnabledFalse();
                
                    /**
                     * Returns all locked user accounts.
                     */
                    List<AppUser> findByAccountNonLockedFalse();
                }
                """, pkg, base, base);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }

    private static boolean fileExistsInPsiDirectory(PsiDirectory dir, String fileName) {
        if (dir == null || fileName == null) return false;
        for (PsiFile f : dir.getFiles()) {
            if (fileName.equals(f.getName())) return true;
        }
        return false;
    }
}