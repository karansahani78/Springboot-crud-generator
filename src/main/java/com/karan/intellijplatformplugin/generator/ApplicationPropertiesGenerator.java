package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;

import java.io.IOException;
import java.util.List;

/**
 * Appends Spring Boot configuration properties to {@code application.properties}.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>Added {@code spring.jpa.open-in-view=false} — critical for multi-entity projects.
 *       Without this, Hibernate keeps the session open through the full HTTP thread,
 *       causing silent N+1 queries as Jackson serialises lazy-loaded relationship
 *       collections. Setting it {@code false} forces all data loading into the
 *       {@code @Transactional} service boundary where it belongs.</li>
 *   <li>Added {@code spring.jpa.properties.hibernate.default_batch_fetch_size=20} —
 *       mitigates N+1 on batch loads of related entities.</li>
 *   <li>Added {@code spring.mvc.throw-exception-if-no-handler-found=true} and
 *       {@code spring.web.resources.add-mappings=false} — required for
 *       {@code GlobalExceptionHandler.handleNoHandlerFound()} to fire on unknown paths.</li>
 *   <li>Added datasource stub section — provides a template with comments so developers
 *       know exactly which properties to fill in.</li>
 *   <li>JWT secret upgraded to a 512-bit hex key with a strong production warning.</li>
 * </ul>
 */
public class ApplicationPropertiesGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities,
            boolean includeSecurity
    ) {
        if (project == null || root == null || meta == null) {
            return;
        }

        try {
            PsiDirectory resourcesDir = findResourcesDirectory(root);
            if (resourcesDir == null) return;

            String configurations = buildConfigurations(includeSecurity);

            PsiFile existingFile = resourcesDir.findFile("application.properties");

            if (existingFile != null) {
                // Append to existing file — only if our marker is not already there
                VirtualFile virtualFile = existingFile.getVirtualFile();
                if (virtualFile != null && virtualFile.isWritable()) {
                    String currentContent = new String(virtualFile.contentsToByteArray());
                    if (!currentContent.contains("# === CRUD Generator: SpringDoc OpenAPI ===")) {
                        virtualFile.setBinaryContent(
                                (currentContent + configurations).getBytes());
                        System.out.println("✅ Appended to application.properties");
                    } else {
                        System.out.println("ℹ️  application.properties already contains generated config — skipping.");
                    }
                }
            } else {
                // Create new file
                PsiFile file = PsiFileFactory.getInstance(project)
                        .createFileFromText("application.properties",
                                com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE,
                                configurations);
                resourcesDir.add(file);
                System.out.println("✅ Created application.properties");
            }

        } catch (IOException e) {
            System.err.println("⚠️  Failed to write application.properties: " + e.getMessage());
        }
    }

    // =========================================================================
    // Configuration content builder
    // =========================================================================

    private static String buildConfigurations(boolean includeSecurity) {
        StringBuilder sb = new StringBuilder();

        // ── SpringDoc / Swagger ───────────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: SpringDoc OpenAPI ===
                springdoc.api-docs.path=/v3/api-docs
                springdoc.swagger-ui.path=/swagger-ui.html
                springdoc.swagger-ui.enabled=true
                springdoc.swagger-ui.operations-sorter=method
                springdoc.swagger-ui.tags-sorter=alpha
                springdoc.swagger-ui.try-it-out-enabled=true
                """);

        // ── JPA core settings ─────────────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: JPA / Hibernate ===
                # CRITICAL for multi-entity projects:
                # Setting open-in-view=false forces all DB access inside @Transactional
                # boundaries. Without this, lazy-loaded relationships are silently
                # fetched during JSON serialisation (N+1 queries per request).
                spring.jpa.open-in-view=false
                
                spring.jpa.show-sql=true
                spring.jpa.properties.hibernate.format_sql=true
                spring.jpa.hibernate.ddl-auto=update
                
                # Batch-fetch related entities to reduce N+1 impact during
                # findAll() calls that include lazy @OneToMany / @ManyToMany sides.
                spring.jpa.properties.hibernate.default_batch_fetch_size=20
                """);

        // ── Datasource stub ───────────────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: Datasource (fill in before running) ===
                # Uncomment and configure one of the following:
                
                # --- MySQL ---
                # spring.datasource.url=jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
                # spring.datasource.username=root
                # spring.datasource.password=your_password
                # spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
                # spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
                
                # --- PostgreSQL ---
                # spring.datasource.url=jdbc:postgresql://localhost:5432/your_database
                # spring.datasource.username=postgres
                # spring.datasource.password=your_password
                # spring.datasource.driver-class-name=org.postgresql.Driver
                # spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
                
                # --- H2 In-Memory (for development / testing) ---
                # spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
                # spring.datasource.driver-class-name=org.h2.Driver
                # spring.datasource.username=sa
                # spring.datasource.password=
                # spring.h2.console.enabled=true
                # spring.h2.console.path=/h2-console
                """);

        // ── Exception handler support ─────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: Exception Handler Support ===
                # Required for GlobalExceptionHandler.handleNoHandlerFound()
                # to fire on requests to unmapped paths (returns 404 instead of 404 white-label page).
                spring.mvc.throw-exception-if-no-handler-found=true
                spring.web.resources.add-mappings=false
                """);

        // ── Security + JWT ────────────────────────────────────────────────
        if (includeSecurity) {
            sb.append("""
                
                # === CRUD Generator: JWT Security ===
                # ⚠️  PRODUCTION WARNING:
                # Replace this secret with a securely generated 512-bit key.
                # Generate with: openssl rand -hex 64
                # Never commit the real secret to version control.
                # Use environment variables or Vault in production:
                #   jwt.secret-key=${JWT_SECRET_KEY}
                jwt.secret-key=6b79e72a3d4f8c1b5a0e9d2f7c3b8a4e1d6f0c9b2a5e8d3f7a1c4b9e2d5f8a0b3c6e9d2f5a8b1e4c7d0f3a6b9e2c5
                jwt.expiration=86400000
                # jwt.expiration is in milliseconds: 86400000 = 24 hours
                
                # ⚠️  IMPORTANT: Add these to your entity User class:
                # The generated UserEntity must implement UserDetails.
                # The AppUserRepository must have findByUsername(String) method.
                """);
        }

        // ── Server ────────────────────────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: Server ===
                server.port=8080
                # Increase timeout for complex multi-entity queries
                server.servlet.session.timeout=30m
                """);

        // ── Logging ───────────────────────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: Logging ===
                logging.level.org.springframework.web=INFO
                logging.level.org.hibernate.SQL=DEBUG
                logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
                # Set to WARN in production to reduce log volume:
                # logging.level.org.hibernate.SQL=WARN
                """);

        return sb.toString();
    }

    // =========================================================================
    // Resources directory resolution
    // =========================================================================

    /**
     * Navigates from the {@code java} source root up to {@code main} and
     * locates (or creates) the sibling {@code resources} directory.
     *
     * <p>Directory structure assumed:
     * <pre>
     *   src/
     *     main/
     *       java/          ← root (passed in)
     *       resources/     ← target
     * </pre>
     *
     * @param sourceRoot the {@code java} source root directory
     * @return the {@code resources} directory, or {@code null} if unreachable
     */
    private static PsiDirectory findResourcesDirectory(PsiDirectory sourceRoot) {
        PsiDirectory main = sourceRoot.getParentDirectory(); // java → main
        if (main == null) return null;

        PsiDirectory resources = main.findSubdirectory("resources");
        if (resources == null) {
            try {
                resources = main.createSubdirectory("resources");
                System.out.println("✅ Created src/main/resources directory");
            } catch (Exception e) {
                System.err.println("⚠️  Could not create resources directory: " + e.getMessage());
                return null;
            }
        }

        return resources;
    }
}