package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.karan.intellijplatformplugin.model.ClassMeta;

/**
 * Generates comprehensive security documentation.
 */
public class SecurityReadmeGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
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
                - ✅ Password encryption with BCrypt
                - ✅ Stateless session management
                - ✅ Protected API endpoints
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
                # JWT Configuration
                jwt.secret-key=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
                jwt.expiration=86400000
                # 86400000 ms = 24 hours
                
                # Note: Generate your own secret key in production!
                # Use at least 256 bits (32 characters)
```
                
                ### 3. Database Setup
                
                Create the users table:
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
                
                Or use Hibernate auto-update:
```properties
                spring.jpa.hibernate.ddl-auto=update
```
                
                ---
                
                ## API Endpoints
                
                ### Public Endpoints (No Authentication Required)
                
                #### Register New User
```bash
                POST /api/auth/register
                Content-Type: application/json
                
                {
                  "username": "john.doe",
                  "email": "john.doe@example.com",
                  "password": "SecurePass123!"
                }
                
                # Response:
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
                
                # Response:
                {
                  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                  "type": "Bearer"
                }
```
                
                ### Protected Endpoints (Authentication Required)
                
                All other API endpoints require authentication. Include the JWT token in the Authorization header:
```bash
                GET /api/%s
                Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```
                
                ---
                
                ## Usage Examples
                
                ### Example 1: Register and Login
```bash
                # 1. Register
                curl -X POST http://localhost:8080/api/auth/register \\
                  -H "Content-Type: application/json" \\
                  -d '{
                    "username": "alice",
                    "email": "alice@example.com",
                    "password": "password123"
                  }'
                
                # Response:
                # {
                #   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhbGljZSIsImlhdCI6MTYzNjU...",
                #   "type": "Bearer"
                # }
                
                # 2. Use the token for protected endpoints
                curl -X GET http://localhost:8080/api/user \\
                  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```
                
                ### Example 2: Using Postman
                
                1. **Register/Login:**
                   - Method: POST
                   - URL: `http://localhost:8080/api/auth/register`
                   - Body (JSON):
```json
                     {
                       "username": "testuser",
                       "email": "test@example.com",
                       "password": "password123"
                     }
```
                   - Copy the `token` from response
                
                2. **Access Protected Endpoint:**
                   - Method: GET
                   - URL: `http://localhost:8080/api/user`
                   - Headers:
                     - Key: `Authorization`
                     - Value: `Bearer YOUR_TOKEN_HERE`
                
                ### Example 3: JavaScript/Fetch
```javascript
                // Register
                const register = async () => {
                  const response = await fetch('http://localhost:8080/api/auth/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                      username: 'john',
                      email: 'john@example.com',
                      password: 'password123'
                    })
                  });
                  
                  const data = await response.json();
                  localStorage.setItem('token', data.token);
                  return data.token;
                };
                
                // Use token for protected requests
                const getUsers = async () => {
                  const token = localStorage.getItem('token');
                  
                  const response = await fetch('http://localhost:8080/api/user', {
                    headers: {
                      'Authorization': `Bearer ${token}`
                    }
                  });
                  
                  return await response.json();
                };
```
                
                ---
                
                ## Role-Based Authorization
                
                ### Default Roles
                
                - **USER**: Regular user (default role)
                - **ADMIN**: Administrator with full access
                - **MODERATOR**: Limited administrative access
                
                ### Protecting Endpoints with Roles
                
                Add `@PreAuthorize` to controller methods:
```java
                @RestController
                @RequestMapping("/api/admin")
                public class AdminController {
                    
                    // Only ADMIN role can access
                    @PreAuthorize("hasRole('ADMIN')")
                    @GetMapping("/users")
                    public List<AppUser> getAllUsers() {
                        return userService.findAll();
                    }
                    
                    // ADMIN or MODERATOR can access
                    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
                    @DeleteMapping("/users/{id}")
                    public void deleteUser(@PathVariable Long id) {
                        userService.delete(id);
                    }
                }
```
                
                ### Creating Admin User Programmatically
```java
                @Component
                public class DatabaseSeeder implements CommandLineRunner {
                    
                    @Autowired
                    private AppUserRepository userRepository;
                    
                    @Autowired
                    private PasswordEncoder passwordEncoder;
                    
                    @Override
                    public void run(String... args) {
                        if (!userRepository.existsByUsername("admin")) {
                            AppUser admin = new AppUser(
                                "admin",
                                "admin@example.com",
                                passwordEncoder.encode("admin123"),
                                Role.ADMIN
                            );
                            userRepository.save(admin);
                            System.out.println("Admin user created: admin/admin123");
                        }
                    }
                }
```
                
                ---
                
                ## Security Best Practices
                
                ### 1. Generate Strong JWT Secret
                
                Never use the default secret in production! Generate a secure key:
```bash
                # Generate 256-bit key
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
                
                ### 4. Implement Token Refresh
                
                Consider adding refresh token functionality for better UX:
```java
                @PostMapping("/refresh")
                public AuthenticationResponse refreshToken(@RequestHeader("Authorization") String token) {
                    // Extract username from expired token
                    // Generate new token
                    // Return new token
                }
```
                
                ### 5. Add Rate Limiting
                
                Protect against brute force attacks on login endpoint.
                
                ### 6. Implement Account Lockout
                
                Lock accounts after N failed login attempts.
                
                ---
                
                ## Integrating with Auditing
                
                The `JpaAuditingConfig` automatically uses the authenticated user:
```java
                @Bean
                public AuditorAware<String> auditorProvider() {
                    return () -> {
                        Authentication authentication = SecurityContextHolder
                            .getContext()
                            .getAuthentication();
                        
                        if (authentication == null || !authentication.isAuthenticated()) {
                            return Optional.of("anonymous");
                        }
                        
                        return Optional.of(authentication.getName());
                    };
                }
```
                
                Now all audit fields (`createdBy`, `updatedBy`) will show the actual username!
                
                ---
                
                ## Testing with Swagger UI
                
                1. Open Swagger UI: `http://localhost:8080/swagger-ui.html`
                2. Find "Authentication" section
                3. Click on **POST /api/auth/register**
                4. Click "Try it out"
                5. Enter user details and execute
                6. Copy the `token` from response
                7. Click "Authorize" button (🔓 icon at top)
                8. Enter: `Bearer YOUR_TOKEN_HERE`
                9. Click "Authorize"
                10. Now you can test protected endpoints!
                
                ---
                
                ## Troubleshooting
                
                ### Issue: 403 Forbidden on all endpoints
                
                **Solution**: Make sure you're including the token in Authorization header:
```
                Authorization: Bearer YOUR_TOKEN_HERE
```
                
                ### Issue: 401 Unauthorized after login
                
                **Solutions**:
                1. Check if token is expired (default: 24 hours)
                2. Verify JWT secret key matches
                3. Check if user account is enabled
                
                ### Issue: "Bad credentials" on login
                
                **Solutions**:
                1. Verify username and password are correct
                2. Check if password was encrypted during registration
                3. Ensure BCrypt is configured correctly
                
                ### Issue: Token validation fails
                
                **Solutions**:
                1. Check JWT secret key in application.properties
                2. Verify token format: `Bearer <token>`
                3. Check token expiration time
                
                ---
                
                ## Advanced Configuration
                
                ### Custom Token Expiration
```properties
                jwt.expiration=3600000  # 1 hour
                jwt.expiration=604800000  # 7 days
```
                
                ### Multiple Authentication Providers
```java
                @Bean
                public AuthenticationProvider ldapAuthenticationProvider() {
                    // LDAP authentication
                }
                
                @Bean
                public AuthenticationProvider customAuthenticationProvider() {
                    // Custom authentication logic
                }
```
                
                ### Add Remember Me Functionality
```java
                http.rememberMe()
                    .key("uniqueAndSecret")
                    .tokenValiditySeconds(86400);
```
                
                ---
                
                ## Testing Security
                
                ### Unit Test Example
```java
                @SpringBootTest
                @AutoConfigureMockMvc
                class SecurityTest {
                    
                    @Autowired
                    private MockMvc mockMvc;
                    
                    @Test
                    void accessProtectedEndpointWithoutToken_ShouldReturn403() throws Exception {
                        mockMvc.perform(get("/api/user"))
                                .andExpect(status().isForbidden());
                    }
                    
                    @Test
                    void registerAndLogin_ShouldReturnToken() throws Exception {
                        // Test registration and login flow
                    }
                }
```
                
                ---
                
                ## Migration from Existing Apps
                
                If you have existing user data:
                
                1. **Add security columns to existing users table**
                2. **Encrypt existing passwords**:
```java
                   users.forEach(user -> {
                       String encrypted = passwordEncoder.encode(user.getPassword());
                       user.setPassword(encrypted);
                       userRepository.save(user);
                   });
```
                3. **Set default roles**
                4. **Test authentication with existing users**
                
                ---
                
                ## Resources
                
                - [Spring Security Documentation](https://docs.spring.io/spring-security/reference/index.html)
                - [JWT.io - JWT Debugger](https://jwt.io/)
                - [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
                
                ---
                
                **Generated by Spring Boot CRUD Generator Plugin v1.0.5**
                
                For issues or questions, please refer to the documentation or create an issue on GitHub.
                """, meta.getClassName().toLowerCase());

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("SECURITY_GUIDE.md", readme);

        if (projectRoot != null && projectRoot.findFile("SECURITY_GUIDE.md") == null) {
            projectRoot.add(file);
        }
    }
}