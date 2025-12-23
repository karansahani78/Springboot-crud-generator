package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.karan.intellijplatformplugin.model.ClassMeta;

/**
 * Generates README with JPA Auditing setup instructions.
 */
public class AuditingReadmeGenerator {

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
                # JPA Auditing Setup Guide
                
                ## Overview
                
                Your entities now support automatic auditing! Every record automatically tracks:
                - **Created At**: When the record was created
                - **Updated At**: When the record was last modified
                - **Created By**: Who created the record
                - **Updated By**: Who last modified the record
                
                ## How It Works
                
                ### 1. Base Audit Entity
                
                All auditable entities should extend `BaseAuditEntity`:
```java
                @Entity
                @Table(name = "users")
                public class User extends BaseAuditEntity {
                    
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                    
                    private String name;
                    private String email;
                    
                    // Your fields...
                    // BaseAuditEntity provides: createdAt, updatedAt, createdBy, updatedBy
                }
```
                
                ### 2. Automatic Population
                
                When you save an entity:
```java
                User user = new User();
                user.setName("John Doe");
                user.setEmail("john@example.com");
                
                userRepository.save(user);
                // Automatically sets:
                // - createdAt = LocalDateTime.now()
                // - createdBy = "system" (or authenticated user)
```
                
                When you update an entity:
```java
                User user = userRepository.findById(1L).orElseThrow();
                user.setName("Jane Doe");
                
                userRepository.save(user);
                // Automatically updates:
                // - updatedAt = LocalDateTime.now()
                // - updatedBy = "system" (or authenticated user)
```
                
                ### 3. Database Schema
                
                The audit fields are automatically added to your database:
```sql
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    created_by VARCHAR(100),
                    updated_by VARCHAR(100)
                );
```
                
                ## Integration with Spring Security
                
                By default, `createdBy` and `updatedBy` are set to "system". To use the actual authenticated user:
                
                ### Step 1: Add Spring Security Dependency
```xml
                <dependency>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-security</artifactId>
                </dependency>
```
                
                ### Step 2: Update JpaAuditingConfig
                
                Replace the `auditorProvider()` method in `JpaAuditingConfig.java`:
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
                
                ### Step 3: Add Import
```java
                import org.springframework.security.core.Authentication;
                import org.springframework.security.core.context.SecurityContextHolder;
```
                
                ## Querying Audit Information
                
                ### Find entities created by a specific user
                
                Add custom query to your repository:
```java
                public interface UserRepository extends JpaRepository<User, Long> {
                    
                    List<User> findByCreatedBy(String createdBy);
                    
                    List<User> findByUpdatedBy(String updatedBy);
                }
```
                
                ### Find entities created within a date range
```java
                public interface UserRepository extends JpaRepository<User, Long> {
                    
                    List<User> findByCreatedAtBetween(
                        LocalDateTime start, 
                        LocalDateTime end
                    );
                    
                    List<User> findByUpdatedAtAfter(LocalDateTime date);
                }
```
                
                ## Exposing Audit Info in API Responses
                
                The audit fields are automatically included in your entity JSON responses:
```json
                {
                  "id": 1,
                  "name": "John Doe",
                  "email": "john@example.com",
                  "createdAt": "2024-12-23T10:30:00",
                  "updatedAt": "2024-12-23T15:45:00",
                  "createdBy": "admin",
                  "updatedBy": "admin"
                }
```
                
                ## Excluding Audit Fields from DTOs
                
                If you don't want to expose audit info in your DTOs, simply don't include them:
```java
                public class UserDto {
                    private String name;
                    private String email;
                    // No audit fields
                }
```
                
                ## Best Practices
                
                ### 1. Always Extend BaseAuditEntity
                
                For new entities that need auditing:
```java
                @Entity
                public class Product extends BaseAuditEntity {
                    // Your fields
                }
```
                
                ### 2. Don't Modify Audit Fields Manually
                
                Let Spring Data JPA handle them automatically. Never do:
```java
                // ❌ DON'T DO THIS
                user.setCreatedAt(LocalDateTime.now());
                user.setCreatedBy("someone");
```
                
                ### 3. Use Audit Info for Tracking Changes
                
                Great for compliance and debugging:
```java
                @GetMapping("/audit/{id}")
                public AuditInfo getAuditInfo(@PathVariable Long id) {
                    User user = userRepository.findById(id).orElseThrow();
                    return new AuditInfo(
                        user.getCreatedAt(),
                        user.getCreatedBy(),
                        user.getUpdatedAt(),
                        user.getUpdatedBy()
                    );
                }
```
                
                ### 4. Add Indexes for Performance
                
                If you frequently query by audit fields:
```java
                @Entity
                @Table(name = "users", indexes = {
                    @Index(name = "idx_created_at", columnList = "created_at"),
                    @Index(name = "idx_created_by", columnList = "created_by")
                })
                public class User extends BaseAuditEntity {
                    // ...
                }
```
                
                ## Troubleshooting
                
                ### Issue: Audit fields are null
                
                **Solution**: Make sure `@EnableJpaAuditing` is present in your configuration:
```java
                @Configuration
                @EnableJpaAuditing(auditorAwareRef = "auditorProvider")
                public class JpaAuditingConfig {
                    // ...
                }
```
                
                ### Issue: createdBy/updatedBy always "system"
                
                **Solution**: Integrate with Spring Security (see above) or create a custom `AuditorAware` implementation.
                
                ### Issue: Existing entities don't have audit fields
                
                **Solution**: 
                1. Make your entity extend `BaseAuditEntity`
                2. Run database migration to add columns
                3. Or use Hibernate's `ddl-auto=update` (development only)
                
                ## Additional Resources
                
                - [Spring Data JPA Auditing Documentation](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#auditing)
                - [Spring Security Integration](https://docs.spring.io/spring-security/reference/index.html)
                
                ---
                
                **Generated by Spring Boot CRUD Generator Plugin v1.0.4**
                """);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("AUDITING_GUIDE.md", readme);

        if (projectRoot != null && projectRoot.findFile("AUDITING_GUIDE.md") == null) {
            projectRoot.add(file);
        }
    }
}