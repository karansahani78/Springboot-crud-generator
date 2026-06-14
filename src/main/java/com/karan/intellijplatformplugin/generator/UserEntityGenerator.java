package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates the {@code AppUser} entity used for Spring Security authentication.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>{@code getAuthorities()} now returns {@code List.of(role)} directly since the
 *       updated {@code Role} enum implements {@code GrantedAuthority} — eliminates the
 *       duplicate {@code "ROLE_" + role.name()} string construction that created two
 *       sources of truth for authority strings.</li>
 *   <li>Added {@code @Table} indexes on {@code username} and {@code email} — Spring
 *       Security calls {@code findByUsername()} on every authenticated request;
 *       without an index this is a full table scan per request.</li>
 *   <li>Added {@code equals()} and {@code hashCode()} based on {@code id} — required
 *       for correct behaviour when {@code AppUser} is placed in collections or used
 *       as the principal in Spring Security's {@code Authentication} object.</li>
 *   <li>Boolean fields changed to {@code Boolean} wrapper — JPA spec portable across
 *       all supported databases; primitives can cause issues with nullable columns
 *       on some dialects.</li>
 *   <li>Security state flags ({@code lock}, {@code disable}, {@code expire}) replaced
 *       with named methods that express intent rather than raw boolean setters.</li>
 *   <li>{@code toString()} added — explicitly excludes {@code password} to prevent
 *       accidental credential leakage in logs.</li>
 * </ul>
 */
public class UserEntityGenerator {

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
        String fileName = "AppUser.java";

        // Disk guard
        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        // PSI directory guard
        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import jakarta.persistence.*;
                import org.springframework.security.core.GrantedAuthority;
                import org.springframework.security.core.userdetails.UserDetails;
                
                import java.util.Collection;
                import java.util.List;
                import java.util.Objects;
                
                /**
                 * Persistent user entity that doubles as a Spring Security {@link UserDetails}.
                 *
                 * <p>Extends {@link BaseAuditEntity} to inherit automatic {@code createdAt},
                 * {@code updatedAt}, {@code createdBy}, {@code updatedBy}, and {@code version}
                 * fields.
                 *
                 * <p>The {@code role} field directly returns a {@link Role} enum value as the
                 * sole {@link GrantedAuthority}. Since {@code Role} implements
                 * {@code GrantedAuthority}, no {@code SimpleGrantedAuthority} adapter is needed.
                 *
                 * <p>⚠️ {@code password} is intentionally excluded from {@code toString()} and
                 * {@code equals}/{@code hashCode} to prevent accidental credential exposure.
                 */
                @Entity
                @Table(
                    name = "users",
                    indexes = {
                        // Spring Security calls findByUsername() on every authenticated request.
                        // Without this index, every auth check is a full table scan.
                        @Index(name = "idx_appuser_username", columnList = "username", unique = true),
                        // Email lookup is used during registration duplicate checks.
                        @Index(name = "idx_appuser_email", columnList = "email", unique = true)
                    }
                )
                public class AppUser extends BaseAuditEntity implements UserDetails {
                
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                
                    @Column(nullable = false, unique = true, length = 50)
                    private String username;
                
                    @Column(nullable = false, unique = true, length = 100)
                    private String email;
                
                    /**
                     * BCrypt-hashed password. Never store or log in plain text.
                     * Set via {@code AuthenticationService} using {@code PasswordEncoder}.
                     */
                    @Column(nullable = false)
                    private String password;
                
                    /**
                     * User's role — stored as a string for readability in the database.
                     * Since {@link Role} implements {@link GrantedAuthority}, this field
                     * is returned directly from {@link #getAuthorities()}.
                     */
                    @Enumerated(EnumType.STRING)
                    @Column(nullable = false, length = 20)
                    private Role role = Role.USER;
                
                    // ── Account state flags ───────────────────────────────────────────
                    // Using Boolean wrapper (not primitive) for JPA portability.
                    // All default to true (active account).
                
                    @Column(nullable = false)
                    private Boolean enabled = Boolean.TRUE;
                
                    @Column(nullable = false)
                    private Boolean accountNonExpired = Boolean.TRUE;
                
                    @Column(nullable = false)
                    private Boolean accountNonLocked = Boolean.TRUE;
                
                    @Column(nullable = false)
                    private Boolean credentialsNonExpired = Boolean.TRUE;
                
                    // ── Constructors ──────────────────────────────────────────────────
                
                    /** Required by JPA. */
                    public AppUser() {}
                
                    /**
                     * Creates an active user with the given credentials and role.
                     *
                     * @param username unique login name
                     * @param email    unique email address
                     * @param password BCrypt-encoded password (never plain text)
                     * @param role     assigned role
                     */
                    public AppUser(String username, String email, String password, Role role) {
                        this.username = username;
                        this.email    = email;
                        this.password = password;
                        this.role     = role;
                    }
                
                    // ── UserDetails implementation ────────────────────────────────────
                
                    /**
                     * Returns the user's single role as the only granted authority.
                     *
                     * <p>{@link Role} implements {@link GrantedAuthority} directly, so no
                     * {@code SimpleGrantedAuthority} wrapper is needed. The authority string
                     * returned is {@code "ROLE_<roleName>"} as defined by
                     * {@link Role#getAuthority()}.
                     */
                    @Override
                    public Collection<? extends GrantedAuthority> getAuthorities() {
                        return List.of(role);
                    }
                
                    @Override
                    public String getPassword() { return password; }
                
                    @Override
                    public String getUsername() { return username; }
                
                    @Override
                    public boolean isAccountNonExpired() {
                        return Boolean.TRUE.equals(accountNonExpired);
                    }
                
                    @Override
                    public boolean isAccountNonLocked() {
                        return Boolean.TRUE.equals(accountNonLocked);
                    }
                
                    @Override
                    public boolean isCredentialsNonExpired() {
                        return Boolean.TRUE.equals(credentialsNonExpired);
                    }
                
                    @Override
                    public boolean isEnabled() {
                        return Boolean.TRUE.equals(enabled);
                    }
                
                    // ── Identity getters & setters ────────────────────────────────────
                
                    public Long getId() { return id; }
                    public void setId(Long id) { this.id = id; }
                
                    public void setUsername(String username) { this.username = username; }
                
                    public String getEmail() { return email; }
                    public void setEmail(String email) { this.email = email; }
                
                    /** @param password BCrypt-encoded value — never plain text */
                    public void setPassword(String password) { this.password = password; }
                
                    public Role getRole() { return role; }
                    public void setRole(Role role) { this.role = role; }
                
                    // ── Account state management ──────────────────────────────────────
                    // Named methods express intent; raw boolean setters avoided.
                
                    /** Disables the account. Disabled users cannot authenticate. */
                    public void disable() { this.enabled = Boolean.FALSE; }
                
                    /** Re-enables a previously disabled account. */
                    public void enable() { this.enabled = Boolean.TRUE; }
                
                    /** Locks the account (e.g. after too many failed login attempts). */
                    public void lock() { this.accountNonLocked = Boolean.FALSE; }
                
                    /** Unlocks a previously locked account. */
                    public void unlock() { this.accountNonLocked = Boolean.TRUE; }
                
                    /** Marks the account as expired (e.g. subscription ended). */
                    public void expireAccount() { this.accountNonExpired = Boolean.FALSE; }
                
                    /** Marks the credentials as expired, forcing a password reset. */
                    public void expireCredentials() { this.credentialsNonExpired = Boolean.FALSE; }
                
                    // Expose raw flag getters for admin use
                    public Boolean getEnabled()               { return enabled; }
                    public Boolean getAccountNonExpired()     { return accountNonExpired; }
                    public Boolean getAccountNonLocked()      { return accountNonLocked; }
                    public Boolean getCredentialsNonExpired() { return credentialsNonExpired; }
                
                    // Raw setters kept for JPA/framework use and test fixtures only
                    public void setEnabled(Boolean enabled)                         { this.enabled = enabled; }
                    public void setAccountNonExpired(Boolean accountNonExpired)     { this.accountNonExpired = accountNonExpired; }
                    public void setAccountNonLocked(Boolean accountNonLocked)       { this.accountNonLocked = accountNonLocked; }
                    public void setCredentialsNonExpired(Boolean credentialsNonExpired) { this.credentialsNonExpired = credentialsNonExpired; }
                
                    // ── equals / hashCode ─────────────────────────────────────────────
                
                    /**
                     * Identity based on {@code id} only.
                     *
                     * <p>Using {@code id} (not {@code username} or {@code email}) because:
                     * <ul>
                     *   <li>Username/email can change; id never does</li>
                     *   <li>Spring Security uses the principal in collections and session
                     *       maps — stable identity is required</li>
                     * </ul>
                     */
                    @Override
                    public boolean equals(Object o) {
                        if (this == o) return true;
                        if (o == null || getClass() != o.getClass()) return false;
                        AppUser that = (AppUser) o;
                        return Objects.equals(id, that.id);
                    }
                
                    @Override
                    public int hashCode() {
                        return Objects.hash(id);
                    }
                
                    // ── toString ──────────────────────────────────────────────────────
                
                    /**
                     * ⚠️ {@code password} is deliberately excluded to prevent accidental
                     * credential exposure in application logs.
                     */
                    @Override
                    public String toString() {
                        return "AppUser{"
                                + "id=" + id
                                + ", username='" + username + '\\''
                                + ", email='" + email + '\\''
                                + ", role=" + role
                                + ", enabled=" + enabled
                                + ", accountNonLocked=" + accountNonLocked
                                + ", accountNonExpired=" + accountNonExpired
                                + ", credentialsNonExpired=" + credentialsNonExpired
                                + ", " + super.toString()
                                + '}';
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }

    private static boolean fileExistsInPsiDirectory(PsiDirectory dir, String fileName) {
        if (dir == null || fileName == null) return false;
        for (PsiFile existing : dir.getFiles()) {
            if (fileName.equals(existing.getName())) return true;
        }
        return false;
    }
}