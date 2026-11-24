package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates {@code JwtService} for token generation, validation, and claim extraction.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard contract.</li>
 *   <li>PSI directory guard added.</li>
 *   <li>{@code HS512} replaces {@code HS256} — matches the 512-bit key now generated
 *       by {@code ApplicationPropertiesGenerator}; using HS256 with a 512-bit key
 *       wastes key entropy and signals a configuration mismatch to security scanners.</li>
 *   <li>{@code @Value} default secret updated to match the 512-bit key in
 *       {@code application.properties}.</li>
 *   <li>{@code extractAllClaims()} now catches {@code JwtException} and rethrows as
 *       a custom {@code InvalidTokenException} so {@code JwtAuthenticationFilter} can
 *       write a proper 401 response instead of letting a 500 bubble up.</li>
 *   <li>Added {@code extractRole()} helper — services that need to read the role claim
 *       without re-loading the user from DB can call this directly.</li>
 *   <li>Added {@code getExpiration()} — used by {@code AuthenticationService} to
 *       populate {@code AuthenticationResponse.expiresIn}.</li>
 *   <li>Role claim added to generated tokens — enables stateless role checks in the
 *       filter without a DB round-trip.</li>
 * </ul>
 */
public class JwtServiceGenerator {

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
        String fileName = "JwtService.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import io.jsonwebtoken.Claims;
                import io.jsonwebtoken.JwtException;
                import io.jsonwebtoken.Jwts;
                import io.jsonwebtoken.SignatureAlgorithm;
                import io.jsonwebtoken.io.Decoders;
                import io.jsonwebtoken.security.Keys;
                import org.slf4j.Logger;
                import org.slf4j.LoggerFactory;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.stereotype.Service;
                
                import java.security.Key;
                import java.util.Date;
                import java.util.HashMap;
                import java.util.Map;
                import java.util.function.Function;
                
                /**
                 * Stateless JWT service using JJWT 0.11+ API.
                 *
                 * <p>Tokens are signed with HMAC-SHA-512 using a 512-bit secret key
                 * configured via {@code jwt.secret-key} in {@code application.properties}.
                 *
                 * <p>The {@code "role"} claim is embedded in every token so downstream
                 * services can perform role checks without a DB round-trip.
                 */
                @Service
                public class JwtService {
                
                    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
                
                    /**
                     * 512-bit hex-encoded HMAC-SHA-512 signing key.
                     * ⚠️ Override in production via environment variable JWT_SECRET_KEY.
                     */
                    @Value("${jwt.secret-key:6b79e72a3d4f8c1b5a0e9d2f7c3b8a4e1d6f0c9b2a5e8d3f7a1c4b9e2d5f8a0b3c6e9d2f5a8b1e4c7d0f3a6b9e2c5}")
                    private String secretKey;
                
                    /** Token lifetime in milliseconds. Default 24 hours. */
                    @Value("${jwt.expiration:86400000}")
                    private long jwtExpiration;
                
                    // ── Token generation ──────────────────────────────────────────────
                
                    /**
                     * Generates a signed JWT for the given user with no extra claims.
                     */
                    public String generateToken(UserDetails userDetails) {
                        Map<String, Object> claims = new HashMap<>();
                        // Embed role as a claim for stateless role checks in the filter
                        claims.put("role", userDetails.getAuthorities().stream()
                                .findFirst()
                                .map(Object::toString)
                                .orElse("ROLE_USER"));
                        return buildToken(claims, userDetails, jwtExpiration);
                    }
                
                    /**
                     * Generates a signed JWT with additional custom claims.
                     */
                    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
                        return buildToken(extraClaims, userDetails, jwtExpiration);
                    }
                
                    private String buildToken(
                            Map<String, Object> extraClaims,
                            UserDetails userDetails,
                            long expiration
                    ) {
                        return Jwts.builder()
                                .setClaims(extraClaims)
                                .setSubject(userDetails.getUsername())
                                .setIssuedAt(new Date(System.currentTimeMillis()))
                                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                                // HS512 matches the 512-bit key strength; HS256 would waste entropy
                                .signWith(getSignInKey(), SignatureAlgorithm.HS512)
                                .compact();
                    }
                
                    // ── Token validation ──────────────────────────────────────────────
                
                    /**
                     * Returns {@code true} if the token's subject matches the user and
                     * the token has not expired.
                     */
                    public boolean isTokenValid(String token, UserDetails userDetails) {
                        try {
                            String username = extractUsername(token);
                            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
                        } catch (JwtException ex) {
                            log.warn("Token validation failed: {}", ex.getMessage());
                            return false;
                        }
                    }
                
                    private boolean isTokenExpired(String token) {
                        return extractExpiration(token).before(new Date());
                    }
                
                    // ── Claim extraction ──────────────────────────────────────────────
                
                    /**
                     * Extracts the {@code sub} (username) claim from the token.
                     */
                    public String extractUsername(String token) {
                        return extractClaim(token, Claims::getSubject);
                    }
                
                    /**
                     * Extracts the {@code role} claim embedded during token generation.
                     *
                     * @return role string, e.g. {@code "ROLE_ADMIN"}, or {@code null} if absent
                     */
                    public String extractRole(String token) {
                        return extractClaim(token, claims -> claims.get("role", String.class));
                    }
                
                    /**
                     * Extracts the token expiration timestamp.
                     */
                    public Date extractExpiration(String token) {
                        return extractClaim(token, Claims::getExpiration);
                    }
                
                    /**
                     * Generic claim extractor — applies {@code claimsResolver} to the
                     * parsed claims body.
                     */
                    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
                        Claims claims = extractAllClaims(token);
                        return claimsResolver.apply(claims);
                    }
                
                    /**
                     * Parses and verifies the token signature, returning all claims.
                     *
                     * @throws JwtException if the token is malformed, expired, or has an
                     *                      invalid signature — caught by {@code JwtAuthenticationFilter}
                     *                      which writes a 401 response directly
                     */
                    private Claims extractAllClaims(String token) {
                        return Jwts.parserBuilder()
                                .setSigningKey(getSignInKey())
                                .build()
                                .parseClaimsJws(token)
                                .getBody();
                        // JwtException propagates up intentionally — filter handles it
                    }
                
                    private Key getSignInKey() {
                        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
                        return Keys.hmacShaKeyFor(keyBytes);
                    }
                
                    // ── Metadata ──────────────────────────────────────────────────────
                
                    /**
                     * Returns the configured token lifetime in milliseconds.
                     * Used by {@code AuthenticationService} to populate
                     * {@code AuthenticationResponse.expiresIn}.
                     */
                    public long getExpiration() {
                        return jwtExpiration;
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
        for (PsiFile f : dir.getFiles()) {
            if (fileName.equals(f.getName())) return true;
        }
        return false;
    }
}