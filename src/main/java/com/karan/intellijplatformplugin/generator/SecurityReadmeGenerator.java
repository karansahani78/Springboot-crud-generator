package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.karan.intellijplatformplugin.model.ClassMeta;

import java.util.List;

/**
 * Generates comprehensive security documentation.
 *
 * <p>Multi-entity change: accepts {@code List<ClassMeta> allEntities} — standard
 * contract. Security setup documentation is project-wide and does not vary
 * per-entity, so the list is intentionally unused in the output.
 */
public class SecurityReadmeGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities   // ← standard contract; unused in content
    ) {
        if (project == null || root == null || meta == null) {
            return;
        }

        PsiDirectory projectRoot = root;
        while (projectRoot.getParentDirectory() != null &&
                !projectRoot.getName().equals("src")) {
            projectRoot = projectRoot.getParentDirectory();
        }
        if (projectRoot.getName().equals("src")) {
            projectRoot = projectRoot.getParentDirectory();
        }

        String readme = String.format("""
                # Spring Security Setup Guide
                
                ## Overview
                
                Your application now includes complete JWT-based authentication and authorization! 🔒
                
                ### Features
                - ✅ JWT (JSON Web Token) authentication
                - ✅ User registration endpoint
                - ✅ User login endpoint
                - ✅ Role-based authorization (USER, ADMIN, MODERATOR)
                - ✅ Password encryption with BCrypt (strength 12)
                - ✅ Stateless session management
                - ✅ Protected API endpoints
                - ✅ JSON error responses (401 / 403) — no HTML white-label pages
                - ✅ CORS configured for localhost:3000 and localhost:4200
                - ✅ Swagger UI accessible without authentication
                
                ---
                
                ## Quick Start
                
                ### 1. Add Required Dependencies
                
                Add these to your `pom.xml`:
```xml
                <!-- Spring Security -->
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-security</artifactId>
                </dependency>
                
                <!-- JWT -->
                <dependency>
                    <groupId>io.jsonwebtoken</groupId>
                    <artifactId>jjwt-api</artifactId>
                    <version>0.12.3</version>
                </dependency>
                <dependency>
                    <groupId>io.jsonwebtoken</groupId>
                    <artifactId>jjwt-impl</artifactId>
                    <version>0.12.3</version>
                    <scope>runtime</scope>
                </dependency>
                <dependency>
                    <groupId>io.jsonwebtoken</groupId>
                    <artifactId>jjwt-jackson</artifactId>
                    <version>0.12.3</version>
                    <scope>runtime</scope>
                </dependency>
```
                
                ### 2. Configure JWT Settings
                
                Add to `application.properties`:
```properties
                jwt.secret-key=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
                jwt.expiration=86400000
                # 86400000 ms = 24 hours — override in production
```
                
                ### 3. Database Setup
                
```sql
                CREATE TABLE users (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    email VARCHAR(255) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL DEFAULT 'USER',
                    enabled BOOLEAN NOT NULL DEFAULT true,
                    account_non_expired BOOLEAN NOT NULL DEFAULT true,
                    account_non_locked BOOLEAN NOT NULL DEFAULT true,
                    credentials_non_expired BOOLEAN NOT NULL DEFAULT true,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    created_by VARCHAR(100),
                    updated_by VARCHAR(100)
                );
```
                
                Or use Hibernate auto-update (development only):
```properties
                spring.jpa.hibernate.ddl-auto=update
```
                
                ---
                
                ## API Endpoints
                
                ### Public (No Authentication Required)
                
                #### Register
```bash
                POST /api/auth/register
                Content-Type: application/json
                
                {
                  "username": "john.doe",
                  "email": "john.doe@example.com",
                  "password": "SecurePass123!"
                }
                
                Response:
                {
                  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                  "type": "Bearer"
                }
```
                
                #### Login
```bash
                POST /api/auth/login
                Content-Type: application/json
                
                {
                  "username": "john.doe",
                  "password": "SecurePass123!"
                }
```
                
                ### Protected Endpoints
                
                All other endpoints require a Bearer token:
```bash
                GET /api/%s
                Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```
                
                ---
                
                ## Role-Based Authorization
                
```java
                @PreAuthorize("hasRole('ADMIN')")
                @GetMapping("/admin/users")
                public List<AppUser> getAllUsers() { ... }
                
                @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
                @DeleteMapping("/admin/users/{id}")
                public void deleteUser(@PathVariable Long id) { ... }
```
                
                ---
                
                ## Security Best Practices
                
                ### 1. Generate Strong JWT Secret
```bash
                openssl rand -base64 32
```
                
                ### 2. Use Environment Variables
```properties
                jwt.secret-key=${JWT_SECRET_KEY}
                jwt.expiration=${JWT_EXPIRATION:86400000}
```
                
                ### 3. Enable HTTPS in Production
```properties
                server.ssl.enabled=true
                server.ssl.key-store=classpath:keystore.p12
                server.ssl.key-store-password=your-password
                server.ssl.key-store-type=PKCS12
```
                
                ### 4. Restrict CORS in Production
                
                Update `SecurityConfig.corsConfigurationSource()` to list only your production domain:
```java
                config.setAllowedOrigins(List.of("https://your-production-domain.com"));
```
                
                ---
                
                ## Testing with Swagger UI
                
                1. Open `http://localhost:8080/swagger-ui.html`
                2. Use **POST /api/auth/register** → copy the token
                3. Click "Authorize" 🔓 → enter `Bearer YOUR_TOKEN`
                4. Test protected endpoints
                
                ---
                
                ## Troubleshooting
                
                | Issue | Solution |
                |-------|----------|
                | 403 on all endpoints | Include `Authorization: Bearer <token>` header |
                | 401 after login | Token expired (24h default) or wrong secret key |
                | "Bad credentials" | Wrong username/password or BCrypt misconfigured |
                | CORS errors in browser | Add your frontend origin to `corsConfigurationSource()` |
                
                ---
                
                ## Resources
                
                - [Spring Security Documentation](https://docs.spring.io/spring-security/reference/index.html)
                - [JWT.io Debugger](https://jwt.io/)
                - [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
                
                ---
                
                **Generated by Spring Boot CRUD Generator Plugin v1.0.5**
                """, meta.getClassName().toLowerCase());

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("SECURITY_GUIDE.md", readme);

        if (projectRoot != null && projectRoot.findFile("SECURITY_GUIDE.md") == null) {
            projectRoot.add(file);
        }
    }
}