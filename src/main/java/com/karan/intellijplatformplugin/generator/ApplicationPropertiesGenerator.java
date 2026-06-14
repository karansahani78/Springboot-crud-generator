package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;

import java.io.IOException;
import java.util.List;

/**
 * Appends Spring Boot configuration properties to {@code application.properties}.
 */
public class ApplicationPropertiesGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities,
            boolean includeSecurity
    ) {
        // Overloaded entry point for callers that don't pass a DB selection.
        generate(project, root, meta, allEntities, includeSecurity, "None");
    }

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities,
            boolean includeSecurity,
            String selectedDb
    ) {
        if (project == null || root == null || meta == null) {
            return;
        }

        try {
            PsiDirectory resourcesDir = findResourcesDirectory(root);
            if (resourcesDir == null) return;

            String configurations = buildConfigurations(includeSecurity, selectedDb);

            PsiFile existingFile = resourcesDir.findFile("application.properties");

            if (existingFile != null) {
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

    private static String buildConfigurations(boolean includeSecurity, String selectedDb) {
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

        // ── Datasource — only write the selected DB, uncommented and ready to use ──
        if (selectedDb != null) {
            switch (selectedDb) {
                case "MySQL" -> sb.append("""
                
                # === CRUD Generator: Datasource (MySQL) ===
                spring.datasource.url=jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
                spring.datasource.username=root
                spring.datasource.password=your_password
                spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
                spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
                """);

                case "PostgreSQL" -> sb.append("""
                
                # === CRUD Generator: Datasource (PostgreSQL) ===
                spring.datasource.url=jdbc:postgresql://localhost:5432/your_database
                spring.datasource.username=postgres
                spring.datasource.password=your_password
                spring.datasource.driver-class-name=org.postgresql.Driver
                spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
                """);

                case "MongoDB" -> sb.append("""
                
                # === CRUD Generator: Datasource (MongoDB) ===
                spring.data.mongodb.uri=mongodb://localhost:27017/your_database
                spring.data.mongodb.database=your_database
                """);

                case "H2" -> sb.append("""
                
                # === CRUD Generator: Datasource (H2 In-Memory) ===
                spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
                spring.datasource.driver-class-name=org.h2.Driver
                spring.datasource.username=sa
                spring.datasource.password=
                spring.h2.console.enabled=true
                spring.h2.console.path=/h2-console
                spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
                """);

                default -> sb.append("""
                
                # === CRUD Generator: Datasource ===
                # No database selected — configure manually before running.
                # spring.datasource.url=
                # spring.datasource.username=
                # spring.datasource.password=
                """);
            }
        }

        // ── JPA core settings (skip for MongoDB) ─────────────────────────
        if (!"MongoDB".equals(selectedDb)) {
            sb.append("""
                
                # === CRUD Generator: JPA / Hibernate ===
                # Setting open-in-view=false forces all DB access inside @Transactional
                # boundaries, preventing silent N+1 queries during JSON serialisation.
                spring.jpa.open-in-view=false
                spring.jpa.show-sql=true
                spring.jpa.properties.hibernate.format_sql=true
                spring.jpa.hibernate.ddl-auto=update
                spring.jpa.properties.hibernate.default_batch_fetch_size=20
                """);
        }

        // ── Exception handler support ─────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: Exception Handler Support ===
                spring.mvc.throw-exception-if-no-handler-found=true
                spring.web.resources.add-mappings=false
                """);

        // ── Security + JWT ────────────────────────────────────────────────
        if (includeSecurity) {
            sb.append("""
                
                # === CRUD Generator: JWT Security ===
                # ⚠️  PRODUCTION WARNING: Replace with a securely generated 512-bit key.
                # Generate with: openssl rand -hex 64
                # Use environment variables in production: jwt.secret-key=${JWT_SECRET_KEY}
                jwt.secret-key=6b79e72a3d4f8c1b5a0e9d2f7c3b8a4e1d6f0c9b2a5e8d3f7a1c4b9e2d5f8a0b3c6e9d2f5a8b1e4c7d0f3a6b9e2c5
                jwt.expiration=86400000
                """);
        }

        // ── Server ────────────────────────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: Server ===
                server.port=8080
                server.servlet.session.timeout=30m
                """);

        // ── Logging ───────────────────────────────────────────────────────
        sb.append("""
                
                # === CRUD Generator: Logging ===
                logging.level.org.springframework.web=INFO
                logging.level.org.hibernate.SQL=DEBUG
                logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
                # Set to WARN in production: logging.level.org.hibernate.SQL=WARN
                """);

        return sb.toString();
    }

    // =========================================================================
    // Resources directory resolution
    // =========================================================================

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