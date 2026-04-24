package com.karan.intellijplatformplugin.util;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DependencyManager {

    public static void injectDependencies(Project project, boolean includeSecurity, String dbType) {
        try {
            Path basePath = Path.of(project.getBasePath());

            Path gradleFile = basePath.resolve("build.gradle");
            Path mavenFile = basePath.resolve("pom.xml");

            if (Files.exists(gradleFile)) {
                injectGradleDependencies(gradleFile, includeSecurity, dbType);
            } else if (Files.exists(mavenFile)) {
                injectMavenDependencies(mavenFile, includeSecurity, dbType);
            } else {
                System.out.println("⚠️ No build file found (Gradle/Maven)");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =========================
    // 🔹 GRADLE SUPPORT
    // =========================
    private static void injectGradleDependencies(Path gradleFile, boolean includeSecurity, String dbType) throws IOException {
        String content = Files.readString(gradleFile);
        StringBuilder dependencies = new StringBuilder();

        if (!content.contains("// 🔥 Auto-added by CRUD Generator")) {
            dependencies.append("\n\n// 🔥 Auto-added by CRUD Generator\n");
        }

        boolean isMongo = "MongoDB".equalsIgnoreCase(dbType);

        if (!isMongo && !content.contains("spring-boot-starter-data-jpa")) {
            dependencies.append("implementation 'org.springframework.boot:spring-boot-starter-data-jpa'\n");
        }

        if (isMongo && !content.contains("spring-boot-starter-data-mongodb")) {
            dependencies.append("implementation 'org.springframework.boot:spring-boot-starter-data-mongodb'\n");
        }

        if (!content.contains("spring-boot-starter-validation")) {
            dependencies.append("implementation 'org.springframework.boot:spring-boot-starter-validation'\n");
        }

        if (!content.contains("springdoc-openapi-starter-webmvc-ui")) {
            dependencies.append("implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'\n");
        }

        // FIXED: explicit Lombok version 1.18.30
        if (!content.contains("lombok")) {
            dependencies.append("""
                    compileOnly 'org.projectlombok:lombok:1.18.30'
                    annotationProcessor 'org.projectlombok:lombok:1.18.30'
                    """);
        }

        switch (dbType) {
            case "MySQL" -> {
                if (!content.contains("mysql-connector-j")) {
                    dependencies.append("runtimeOnly 'com.mysql:mysql-connector-j'\n");
                }
            }
            case "PostgreSQL" -> {
                if (!content.contains("postgresql")) {
                    dependencies.append("runtimeOnly 'org.postgresql:postgresql'\n");
                }
            }
            case "H2" -> {
                if (!content.contains("h2")) {
                    dependencies.append("runtimeOnly 'com.h2database:h2'\n");
                }
            }
        }

        if (includeSecurity) {
            if (!content.contains("spring-boot-starter-security")) {
                dependencies.append("implementation 'org.springframework.boot:spring-boot-starter-security'\n");
            }
            if (!content.contains("jjwt-api")) {
                dependencies.append("implementation 'io.jsonwebtoken:jjwt-api:0.11.5'\n");
            }
            if (!content.contains("jjwt-impl")) {
                dependencies.append("runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'\n");
            }
            if (!content.contains("jjwt-jackson")) {
                dependencies.append("runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'\n");
            }
        }

        if (dependencies.isEmpty()) {
            System.out.println("⚠️ Gradle: All dependencies already exist");
            return;
        }

        int index = content.indexOf("dependencies {");
        if (index == -1) {
            System.out.println("⚠️ Gradle: No dependencies block found");
            return;
        }

        int insertPosition = index + "dependencies {".length();
        content = content.substring(0, insertPosition)
                + "\n" + dependencies
                + content.substring(insertPosition);

        Files.writeString(gradleFile, content);
        System.out.println("✅ Gradle dependencies injected successfully!");
    }

    // =========================
    // 🔹 MAVEN SUPPORT
    // =========================
    private static void injectMavenDependencies(Path pomFile, boolean includeSecurity, String dbType) throws IOException {
        String content = Files.readString(pomFile);
        StringBuilder dependencies = new StringBuilder();

        if (!content.contains("<!-- 🔥 Auto-added by CRUD Generator -->")) {
            dependencies.append("\n\n<!-- 🔥 Auto-added by CRUD Generator -->\n");
        }

        boolean isMongo = "MongoDB".equalsIgnoreCase(dbType);

        if (!isMongo && !content.contains("spring-boot-starter-data-jpa")) {
            dependencies.append("""
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                    """);
        }

        if (isMongo && !content.contains("spring-boot-starter-data-mongodb")) {
            dependencies.append("""
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-data-mongodb</artifactId>
                    </dependency>
                    """);
        }

        if (!content.contains("spring-boot-starter-validation")) {
            dependencies.append("""
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>
                    """);
        }

        if (!content.contains("springdoc-openapi-starter-webmvc-ui")) {
            dependencies.append("""
                    <dependency>
                        <groupId>org.springdoc</groupId>
                        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                        <version>2.5.0</version>
                    </dependency>
                    """);
        }

        // FIXED: explicit Lombok version 1.18.30
        if (!content.contains("<artifactId>lombok</artifactId>")) {
            dependencies.append("""
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>1.18.30</version>
                        <optional>true</optional>
                    </dependency>
                    """);
        }

        switch (dbType) {
            case "MySQL" -> dependencies.append("""
                    <dependency>
                        <groupId>com.mysql</groupId>
                        <artifactId>mysql-connector-j</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                    """);
            case "PostgreSQL" -> dependencies.append("""
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                    """);
            case "H2" -> dependencies.append("""
                    <dependency>
                        <groupId>com.h2database</groupId>
                        <artifactId>h2</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                    """);
        }

        if (includeSecurity) {
            if (!content.contains("spring-boot-starter-security")) {
                dependencies.append("""
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-security</artifactId>
                        </dependency>
                        """);
            }
            if (!content.contains("jjwt-api")) {
                dependencies.append("""
                        <dependency>
                            <groupId>io.jsonwebtoken</groupId>
                            <artifactId>jjwt-api</artifactId>
                            <version>0.11.5</version>
                        </dependency>
                        """);
            }
            if (!content.contains("jjwt-impl")) {
                dependencies.append("""
                        <dependency>
                            <groupId>io.jsonwebtoken</groupId>
                            <artifactId>jjwt-impl</artifactId>
                            <version>0.11.5</version>
                            <scope>runtime</scope>
                        </dependency>
                        """);
            }
            if (!content.contains("jjwt-jackson")) {
                dependencies.append("""
                        <dependency>
                            <groupId>io.jsonwebtoken</groupId>
                            <artifactId>jjwt-jackson</artifactId>
                            <version>0.11.5</version>
                            <scope>runtime</scope>
                        </dependency>
                        """);
            }
        }

        if (dependencies.isEmpty()) {
            System.out.println("⚠️ Maven: All dependencies already exist");
            return;
        }

        int index = content.indexOf("<dependencies>");
        if (index == -1) {
            System.out.println("⚠️ Maven: No <dependencies> block found");
            return;
        }

        int insertPosition = index + "<dependencies>".length();
        content = content.substring(0, insertPosition)
                + "\n" + dependencies
                + content.substring(insertPosition);

        Files.writeString(pomFile, content);
        System.out.println("✅ Maven dependencies injected successfully!");
    }
}