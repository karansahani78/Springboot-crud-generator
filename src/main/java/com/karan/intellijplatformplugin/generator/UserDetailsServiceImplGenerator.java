package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates {@code UserDetailsServiceImpl} — the Spring Security bridge that
 * loads {@link com.karan.intellijplatformplugin.model.ClassMeta} by username.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>{@code @Transactional(readOnly = true)} added — ensures the DB query
 *       runs inside a read-optimised transaction; without it, some JPA providers
 *       open a new transaction per call which adds connection-pool pressure under
 *       high authentication load.</li>
 *   <li>Logger added — failed user lookups are a security event and must be logged
 *       at WARN level for audit/intrusion-detection purposes.</li>
 * </ul>
 */
public class UserDetailsServiceImplGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".security";
        String fileName = "UserDetailsServiceImpl.java";

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
                
                import %s.repository.AppUserRepository;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.security.core.userdetails.UserDetailsService;
                import org.springframework.security.core.userdetails.UsernameNotFoundException;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                
                /**
                 * Spring Security {@link UserDetailsService} implementation.
                 *
                 * <p>Called by the Spring Security authentication manager on every
                 * authenticated request to load the {@code AppUser} principal from the DB.
                 *
                 * <p>{@code @Transactional(readOnly = true)} ensures the lookup runs inside
                 * a read-optimised transaction, reducing connection-pool pressure under
                 * high authentication load.
                 *
                 * <p>Failed lookups are logged at WARN level — they are security events
                 * that should be visible in audit logs and intrusion-detection systems.
                 */
                @Service
                @Transactional(readOnly = true)
                public class UserDetailsServiceImpl implements UserDetailsService {
                
                    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);
                
                    private final AppUserRepository userRepository;
                
                    public UserDetailsServiceImpl(AppUserRepository userRepository) {
                        this.userRepository = userRepository;
                    }
                
                    /**
                     * Loads a user by username for Spring Security authentication.
                     *
                     * @param username login name from the authentication token
                     * @return the matching {@code AppUser} as a {@link UserDetails}
                     * @throws UsernameNotFoundException if no user with the given username exists
                     */
                    @Override
                    public UserDetails loadUserByUsername(String username)
                            throws UsernameNotFoundException {
                
                        return userRepository.findByUsername(username)
                                .orElseThrow(() -> {
                                    log.warn("Authentication failed — user not found: '{}'", username);
                                    return new UsernameNotFoundException(
                                            "User not found with username: " + username);
                                });
                    }
                }
                """, pkg, base);

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