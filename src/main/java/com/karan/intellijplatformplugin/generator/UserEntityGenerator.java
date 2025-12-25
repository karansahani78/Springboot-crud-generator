package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates User entity for authentication.
 */
public class UserEntityGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".entity";
        // CHECK IF FILE ALREADY EXISTS
        if (FileExistsUtil.fileExistsInPackage(root, pkg, "AppUser.java")) {
            System.out.println("AppUser.java already exists, skipping generation.");
            return;
        }
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import jakarta.persistence.*;
                import org.springframework.security.core.GrantedAuthority;
                import org.springframework.security.core.authority.SimpleGrantedAuthority;
                import org.springframework.security.core.userdetails.UserDetails;
                
                import java.util.Collection;
                import java.util.List;
                
                /**
                 * User entity for authentication and authorization.
                 * Implements UserDetails for Spring Security integration.
                 */
                @Entity
                @Table(name = "users")
                public class AppUser extends BaseAuditEntity implements UserDetails {
                    
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                    
                    @Column(nullable = false, unique = true)
                    private String username;
                    
                    @Column(nullable = false, unique = true)
                    private String email;
                    
                    @Column(nullable = false)
                    private String password;
                    
                    @Enumerated(EnumType.STRING)
                    @Column(nullable = false)
                    private Role role = Role.USER;
                    
                    @Column(nullable = false)
                    private boolean enabled = true;
                    
                    @Column(nullable = false)
                    private boolean accountNonExpired = true;
                    
                    @Column(nullable = false)
                    private boolean accountNonLocked = true;
                    
                    @Column(nullable = false)
                    private boolean credentialsNonExpired = true;
                    
                    // Constructors
                    public AppUser() {}
                    
                    public AppUser(String username, String email, String password, Role role) {
                        this.username = username;
                        this.email = email;
                        this.password = password;
                        this.role = role;
                    }
                    
                    // UserDetails implementation
                    @Override
                    public Collection<? extends GrantedAuthority> getAuthorities() {
                        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
                    }
                    
                    @Override
                    public String getPassword() {
                        return password;
                    }
                    
                    @Override
                    public String getUsername() {
                        return username;
                    }
                    
                    @Override
                    public boolean isAccountNonExpired() {
                        return accountNonExpired;
                    }
                    
                    @Override
                    public boolean isAccountNonLocked() {
                        return accountNonLocked;
                    }
                    
                    @Override
                    public boolean isCredentialsNonExpired() {
                        return credentialsNonExpired;
                    }
                    
                    @Override
                    public boolean isEnabled() {
                        return enabled;
                    }
                    
                    // Getters and Setters
                    public Long getId() {
                        return id;
                    }
                    
                    public void setId(Long id) {
                        this.id = id;
                    }
                    
                    public void setUsername(String username) {
                        this.username = username;
                    }
                    
                    public String getEmail() {
                        return email;
                    }
                    
                    public void setEmail(String email) {
                        this.email = email;
                    }
                    
                    public void setPassword(String password) {
                        this.password = password;
                    }
                    
                    public Role getRole() {
                        return role;
                    }
                    
                    public void setRole(Role role) {
                        this.role = role;
                    }
                    
                    public void setEnabled(boolean enabled) {
                        this.enabled = enabled;
                    }
                    
                    public void setAccountNonExpired(boolean accountNonExpired) {
                        this.accountNonExpired = accountNonExpired;
                    }
                    
                    public void setAccountNonLocked(boolean accountNonLocked) {
                        this.accountNonLocked = accountNonLocked;
                    }
                    
                    public void setCredentialsNonExpired(boolean credentialsNonExpired) {
                        this.credentialsNonExpired = credentialsNonExpired;
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "AppUser.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}