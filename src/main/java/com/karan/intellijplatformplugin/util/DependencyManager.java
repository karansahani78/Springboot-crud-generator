package com.karan.intellijplatformplugin.util;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DependencyManager {

    public static void injectDependencies(Project project, boolean includeSecurity) {
        try {
            Path basePath = Path.of(project.getBasePath());

            Path gradleFile = basePath.resolve("build.gradle");
            Path mavenFile = basePath.resolve("pom.xml");

            if (Files.exists(gradleFile)) {
                injectGradleDependencies(gradleFile, includeSecurity);
            } else if (Files.exists(mavenFile)) {
                injectMavenDependencies(mavenFile, includeSecurity);
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
    private static void injectGradleDependencies(Path gradleFile, boolean includeSecurity) throws IOException {
        String content = Files.readString(gradleFile);
        StringBuilder dependencies = new StringBuilder();

        // Marker
        if (!content.contains("// 🔥 Auto-added by CRUD Generator")) {
            dependencies.append("\n\n// 🔥 Auto-added by CRUD Generator\n");
        }

        // ✅ JPA (NEW - IMPORTANT)
        if (!content.contains("spring-boot-starter-data-jpa")) {
            dependencies.append("implementation 'org.springframework.boot:spring-boot-starter-data-jpa'\n");
        }

        // Base deps
        if (!content.contains("spring-boot-starter-validation")) {
            dependencies.append("implementation 'org.springframework.boot:spring-boot-starter-validation'\n");
        }

        if (!content.contains("springdoc-openapi-starter-webmvc-ui")) {
            dependencies.append("implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'\n");
        }

        if (!content.contains("lombok")) {
            dependencies.append("""
                    compileOnly 'org.projectlombok:lombok'
                    annotationProcessor 'org.projectlombok:lombok'
                    """);
        }

        // Security deps
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
    private static void injectMavenDependencies(Path pomFile, boolean includeSecurity) throws IOException {
        String content = Files.readString(pomFile);
        StringBuilder dependencies = new StringBuilder();

        // Marker
        if (!content.contains("<!-- 🔥 Auto-added by CRUD Generator -->")) {
            dependencies.append("\n\n<!-- 🔥 Auto-added by CRUD Generator -->\n");
        }

        // ✅ JPA (NEW - IMPORTANT)
        if (!content.contains("spring-boot-starter-data-jpa")) {
            dependencies.append("""
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                    """);
        }

        // Base deps
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

        if (!content.contains("<artifactId>lombok</artifactId>")) {
            dependencies.append("""
                    <dependency>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <optional>true</optional>
                    </dependency>
                    """);
        }

        // Security deps
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