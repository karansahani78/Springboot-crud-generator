package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates JWT service for token generation and validation.
 */
public class JwtServiceGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".security";
        // CHECK IF FILE ALREADY EXISTS
        if (FileExistsUtil.fileExistsInPackage(root, pkg, "JwtService.java")) {
            System.out.println("JwtService.java already exists, skipping generation.");
            return;
        }
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import io.jsonwebtoken.Claims;
                import io.jsonwebtoken.Jwts;
                import io.jsonwebtoken.SignatureAlgorithm;
                import io.jsonwebtoken.io.Decoders;
                import io.jsonwebtoken.security.Keys;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.security.core.userdetails.UserDetails;
                import org.springframework.stereotype.Service;
                
                import java.security.Key;
                import java.util.Date;
                import java.util.HashMap;
                import java.util.Map;
                import java.util.function.Function;
                
                /**
                 * Service for JWT token operations (generation, validation, extraction).
                 */
                @Service
                public class JwtService {
                    
                    @Value("${jwt.secret-key:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
                    private String secretKey;
                    
                    @Value("${jwt.expiration:86400000}") // 24 hours in milliseconds
                    private long jwtExpiration;
                    
                    /**
                     * Extracts username from JWT token.
                     */
                    public String extractUsername(String token) {
                        return extractClaim(token, Claims::getSubject);
                    }
                    
                    /**
                     * Extracts a specific claim from token.
                     */
                    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
                        final Claims claims = extractAllClaims(token);
                        return claimsResolver.apply(claims);
                    }
                    
                    /**
                     * Generates JWT token for user.
                     */
                    public String generateToken(UserDetails userDetails) {
                        return generateToken(new HashMap<>(), userDetails);
                    }
                    
                    /**
                     * Generates JWT token with extra claims.
                     */
                    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
                        return buildToken(extraClaims, userDetails, jwtExpiration);
                    }
                    
                    /**
                     * Builds the JWT token.
                     */
                    private String buildToken(
                            Map<String, Object> extraClaims,
                            UserDetails userDetails,
                            long expiration
                    ) {
                        return Jwts
                                .builder()
                                .setClaims(extraClaims)
                                .setSubject(userDetails.getUsername())
                                .setIssuedAt(new Date(System.currentTimeMillis()))
                                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                                .compact();
                    }
                    
                    /**
                     * Validates JWT token.
                     */
                    public boolean isTokenValid(String token, UserDetails userDetails) {
                        final String username = extractUsername(token);
                        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
                    }
                    
                    /**
                     * Checks if token is expired.
                     */
                    private boolean isTokenExpired(String token) {
                        return extractExpiration(token).before(new Date());
                    }
                    
                    /**
                     * Extracts expiration date from token.
                     */
                    private Date extractExpiration(String token) {
                        return extractClaim(token, Claims::getExpiration);
                    }
                    
                    /**
                     * Extracts all claims from token.
                     */
                    private Claims extractAllClaims(String token) {
                        return Jwts
                                .parser()
                                .setSigningKey(getSignInKey())
                                .build()
                                .parseClaimsJws(token)
                                .getBody();
                    }
                    
                    /**
                     * Gets signing key for JWT.
                     */
                    private Key getSignInKey() {
                        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
                        return Keys.hmacShaKeyFor(keyBytes);
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "JwtService.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}