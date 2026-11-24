package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates {@code AuthenticationService} handling register and login flows.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>{@code RuntimeException} replaced with {@code DuplicateResourceException}
 *       and {@code ResourceNotFoundException} — consistent with rest of the app.</li>
 *   <li>{@code authenticate()} marked {@code @Transactional(readOnly = true)} so the
 *       {@code findByUsername()} call runs inside an existing session.</li>
 *   <li>{@code AuthenticationResponse} now populated with {@code expiresIn} from
 *       {@code JwtService.getExpiration()} — matches the updated response DTO.</li>
 *   <li>Logger added — auth events (register/login success/fail) are security-critical
 *       and must be auditable.</li>
 * </ul>
 */
public class AuthenticationServiceGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".service";
        String fileName = "AuthenticationService.java";

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
                
                import %s.dto.AuthenticationRequest;
                import %s.dto.AuthenticationResponse;
                import %s.dto.RegisterRequest;
                import %s.entity.AppUser;
                import %s.entity.Role;
                import %s.exception.DuplicateResourceException;
                import %s.exception.ResourceNotFoundException;
                import %s.repository.AppUserRepository;
                import %s.security.JwtService;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.security.authentication.AuthenticationManager;
                import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
                import org.springframework.security.crypto.password.PasswordEncoder;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                
                /**
                 * Handles user registration and JWT-based login.
                 *
                 * <p>All write operations are {@code @Transactional}. Read-only queries
                 * (login flow) are {@code @Transactional(readOnly = true)} so they share
                 * a read-optimised connection from the pool.
                 */
                @Service
                public class AuthenticationService {
                
                    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
                
                    private final AppUserRepository userRepository;
                    private final PasswordEncoder passwordEncoder;
                    private final JwtService jwtService;
                    private final AuthenticationManager authenticationManager;
                
                    public AuthenticationService(
                            AppUserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService,
                            AuthenticationManager authenticationManager
                    ) {
                        this.userRepository        = userRepository;
                        this.passwordEncoder       = passwordEncoder;
                        this.jwtService            = jwtService;
                        this.authenticationManager = authenticationManager;
                    }
                
                    // ── Register ──────────────────────────────────────────────────────
                
                    /**
                     * Creates a new user account and issues a JWT token.
                     *
                     * @param request registration payload (validated by caller)
                     * @return JWT response with token and expiry
                     * @throws DuplicateResourceException if username or email already exists
                     */
                    @Transactional
                    public AuthenticationResponse register(RegisterRequest request) {
                        log.info("Registering new user: username={}, email={}",
                                request.getUsername(), request.getEmail());
                
                        // Duplicate username check
                        if (userRepository.existsByUsername(request.getUsername())) {
                            log.warn("Registration failed — username already exists: {}",
                                    request.getUsername());
                            throw new DuplicateResourceException("User", "username", request.getUsername());
                        }
                
                        // Duplicate email check
                        if (userRepository.existsByEmail(request.getEmail())) {
                            log.warn("Registration failed — email already exists: {}",
                                    request.getEmail());
                            throw new DuplicateResourceException("User", "email", request.getEmail());
                        }
                
                        AppUser user = new AppUser(
                                request.getUsername(),
                                request.getEmail(),
                                passwordEncoder.encode(request.getPassword()),
                                Role.USER
                        );
                
                        userRepository.save(user);
                        log.info("User registered successfully: id={}, username={}", user.getId(), user.getUsername());
                
                        String token = jwtService.generateToken(user);
                        return new AuthenticationResponse(token, jwtService.getExpiration());
                    }
                
                    // ── Authenticate ──────────────────────────────────────────────────
                
                    /**
                     * Authenticates credentials and issues a JWT token.
                     *
                     * <p>{@code authenticationManager.authenticate()} throws
                     * {@code BadCredentialsException} if credentials are wrong — Spring
                     * Security handles this before this method proceeds.
                     *
                     * @param request login payload
                     * @return JWT response with token and expiry
                     * @throws ResourceNotFoundException if user record is missing after auth
                     *         (should never happen in practice — defensive guard)
                     */
                    @Transactional(readOnly = true)
                    public AuthenticationResponse authenticate(AuthenticationRequest request) {
                        log.info("Authentication attempt for username={}", request.getUsername());
                
                        authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                        request.getUsername(),
                                        request.getPassword()
                                )
                        );
                
                        AppUser user = userRepository.findByUsername(request.getUsername())
                                .orElseThrow(() -> new ResourceNotFoundException(
                                        "User", "username", request.getUsername()));
                
                        log.info("Authentication successful for username={}", user.getUsername());
                
                        String token = jwtService.generateToken(user);
                        return new AuthenticationResponse(token, jwtService.getExpiration());
                    }
                }
                """,
                pkg,
                base, base, base, base, base, base, base, base, base, base
        );

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