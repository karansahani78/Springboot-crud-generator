package com.karan.intellijplatformplugin.service;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.Query;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects Spring Boot project facts needed by prompt generation.
 *
 * <p>Everything is resolved locally from the IntelliJ project model. No cloud
 * services or external AI are involved.
 */
public final class ProjectContextAnalyzer {

    private ProjectContextAnalyzer() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /** Immutable snapshot of detected project context. */
    public static final class Context {
        private final String basePackage;
        private final String entityPackage;
        private final String persistenceNamespace;
        private final boolean springBootDetected;
        private final String buildTool;
        private final List<PsiDirectory> sourceRoots;
        private final List<String> applicationClasses;
        private final List<String> warnings;

        public Context(
                String basePackage,
                String entityPackage,
                String persistenceNamespace,
                boolean springBootDetected,
                String buildTool,
                List<PsiDirectory> sourceRoots,
                List<String> applicationClasses,
                List<String> warnings
        ) {
            this.basePackage = basePackage;
            this.entityPackage = entityPackage;
            this.persistenceNamespace = persistenceNamespace;
            this.springBootDetected = springBootDetected;
            this.buildTool = buildTool;
            this.sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
            this.applicationClasses = applicationClasses == null ? List.of() : List.copyOf(applicationClasses);
            this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public String getBasePackage() { return basePackage; }
        public String getEntityPackage() { return entityPackage; }
        public String getPersistenceNamespace() { return persistenceNamespace; }
        public boolean isSpringBootDetected() { return springBootDetected; }
        public String getBuildTool() { return buildTool; }
        public List<PsiDirectory> getSourceRoots() { return sourceRoots; }
        public List<String> getApplicationClasses() { return applicationClasses; }
        public List<String> getWarnings() { return warnings; }
        public boolean isAmbiguous() { return applicationClasses.size() > 1 || sourceRoots.size() > 1; }
    }

    /** Analyzes the project and returns a context with resolved defaults. */
    public static Context analyze(Project project) {
        if (project == null) throw new IllegalArgumentException("Project cannot be null");

        List<PsiDirectory> sourceRoots = findSourceRoots(project);
        List<String> warnings = new ArrayList<>();

        List<String> applicationClasses = findApplicationClasses(project);
        String basePackage = null;
        if (applicationClasses.size() == 1) {
            basePackage = applicationClasses.get(0);
        } else if (applicationClasses.size() > 1) {
            warnings.add("Multiple @SpringBootApplication classes found. Select the target package explicitly.");
        }

        if (basePackage == null) {
            basePackage = inferPackageFromSourceRoots(sourceRoots);
            if (basePackage != null) {
                warnings.add("No @SpringBootApplication class found; inferred package from existing sources.");
            }
        }

        String namespace = detectPersistenceNamespace(project);
        if ("unknown".equals(namespace)) {
            warnings.add("Could not determine jakarta/javax persistence namespace. Select it manually.");
        }

        return new Context(
                basePackage,
                basePackage == null ? null : basePackage + ".entity",
                namespace,
                !applicationClasses.isEmpty(),
                detectBuildTool(project),
                sourceRoots,
                applicationClasses,
                warnings
        );
    }

    /** Returns all Java source roots (supports multi-module projects). */
    public static List<PsiDirectory> findSourceRoots(Project project) {
        List<PsiDirectory> roots = new ArrayList<>();
        VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile root : contentRoots) {
            if (!root.isDirectory()) continue;
            if (!"java".equals(root.getName()) && !root.getPath().contains("/src/main/java")) continue;
            PsiDirectory directory = psiManager.findDirectory(root);
            if (directory != null && !roots.contains(directory)) roots.add(directory);
        }
        return roots;
    }

    /** Finds packages of all @SpringBootApplication classes in the project. */
    public static List<String> findApplicationClasses(Project project) {
        return ReadAction.compute(() -> {
            List<String> packages = new ArrayList<>();
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiClass annotation = facade.findClass(
                    "org.springframework.boot.autoconfigure.SpringBootApplication", scope);
            if (annotation != null) {
                Query<PsiClass> query = AnnotatedElementsSearch.searchPsiClasses(annotation, scope);
                query.forEach(psiClass -> {
                    String pkg = packageOf(psiClass);
                    if (pkg != null && !packages.contains(pkg) && !pkg.isBlank()) packages.add(pkg);
                    return true;
                });
            }
            return packages;
        });
    }

    /** Detects jakarta/javax from classes available on the project classpath. */
    public static String detectPersistenceNamespace(Project project) {
        return ReadAction.compute(() -> {
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            boolean jakarta = facade.findClass("jakarta.persistence.Entity", scope) != null;
            boolean javax = facade.findClass("javax.persistence.Entity", scope) != null;
            if (jakarta && !javax) return "jakarta";
            if (javax && !jakarta) return "javax";
            return "unknown";
        });
    }

    /** Returns "Gradle", "Maven" or "Unknown". */
    public static String detectBuildTool(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) return "Unknown";
        try {
            if (Files.exists(Path.of(basePath, "build.gradle"))
                    || Files.exists(Path.of(basePath, "build.gradle.kts"))) return "Gradle";
            if (Files.exists(Path.of(basePath, "pom.xml"))) return "Maven";
        } catch (Exception ignored) {
            return "Unknown";
        }
        return "Unknown";
    }

    private static String inferPackageFromSourceRoots(List<PsiDirectory> sourceRoots) {
        for (PsiDirectory root : sourceRoots) {
            PsiDirectory current = root;
            for (int depth = 0; depth < 8; depth++) {
                PsiDirectory[] children = current.getSubdirectories();
                if (children.length == 0) break;
                if (children.length == 1 && !children[0].getName().equals("java")) {
                    current = children[0];
                    continue;
                }
                break;
            }
            String inferred = buildPackage(root, current);
            if (inferred != null && !inferred.isBlank()) return inferred;
        }
        return null;
    }

    private static String buildPackage(PsiDirectory root, PsiDirectory current) {
        if (current == null || current.equals(root)) return null;
        List<String> parts = new ArrayList<>();
        PsiDirectory cursor = current;
        while (cursor != null && !cursor.equals(root)) {
            parts.add(0, cursor.getName());
            cursor = cursor.getParentDirectory();
        }
        return String.join(".", parts);
    }

    private static String packageOf(PsiClass psiClass) {
        PsiFile file = psiClass.getContainingFile();
        return file instanceof PsiJavaFile javaFile ? javaFile.getPackageName() : null;
    }
}
