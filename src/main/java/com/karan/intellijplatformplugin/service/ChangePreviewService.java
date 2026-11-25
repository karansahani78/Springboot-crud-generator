package com.karan.intellijplatformplugin.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.GenerationPlan;
import com.karan.intellijplatformplugin.model.GenerationTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Predicts which files a generation plan would create or modify and asks the
 * developer how to handle conflicts. Existing user code is never silently
 * overwritten.
 */
public final class ChangePreviewService {

    private ChangePreviewService() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    public enum Decision { GENERATE, SKIP, REPLACE }

    /** One predicted output path. */
    public static final class TargetFile {
        private final String taskId;
        private final String displayPath;
        private final boolean exists;
        private final boolean shared;

        public TargetFile(String taskId, String displayPath, boolean exists, boolean shared) {
            this.taskId = taskId;
            this.displayPath = displayPath;
            this.exists = exists;
            this.shared = shared;
        }

        public String getTaskId() { return taskId; }
        public String getDisplayPath() { return displayPath; }
        public boolean exists() { return exists; }
        public boolean isShared() { return shared; }

        @Override
        public String toString() {
            return (exists ? "MODIFY " : "CREATE  ") + displayPath;
        }
    }

    /** Predicts all output files for the given plan. */
    public static List<TargetFile> predictTargets(
            Project project,
            PsiDirectory sourceRoot,
            ClassMeta meta,
            GenerationPlan plan
    ) {
        List<TargetFile> targets = new ArrayList<>();
        if (project == null || sourceRoot == null || meta == null || plan == null) return targets;

        String base = meta.basePackage();
        String entity = meta.getClassName();

        for (GenerationTask task : plan.getTasks()) {
            if (!task.isEnabled()) continue;
            for (Location location : locationsForTask(task.getId(), base, entity)) {
                if (location.shared) {
                    continue; // shared build/properties files are handled below
                }
                PsiDirectory dir = location.rootFile
                        ? findProjectRoot(sourceRoot)
                        : findPackageDir(sourceRoot, location.packageName);
                boolean exists = dir != null && dir.findFile(location.fileName) != null;
                String display = location.rootFile
                        ? location.fileName
                        : location.packageName.replace('.', '/') + "/" + location.fileName;
                targets.add(new TargetFile(task.getId(), display, exists, false));
            }
        }

        if (isEnabled(plan, "properties")) {
            PsiDirectory resources = findResourcesDir(sourceRoot);
            boolean exists = resources != null && resources.findFile("application.properties") != null;
            targets.add(new TargetFile("properties", "src/main/resources/application.properties", exists, true));
        }
        if (isEnabled(plan, "dependencies")) {
            String basePath = project.getBasePath();
            if (basePath != null) {
                boolean gradle = Files.exists(Path.of(basePath, "build.gradle"));
                boolean maven = Files.exists(Path.of(basePath, "pom.xml"));
                if (gradle) targets.add(new TargetFile("dependencies", "build.gradle", true, true));
                if (maven) targets.add(new TargetFile("dependencies", "pom.xml", true, true));
            }
        }
        return targets;
    }

    /**
     * Shows a conflict preview when files already exist.
     *
     * @return per-task decision map; never null unless the developer cancels
     */
    public static Map<String, Decision> resolveDecisions(
            Project project,
            List<TargetFile> targets
    ) {
        Map<String, Decision> decisions = new LinkedHashMap<>();
        List<TargetFile> conflicts = new ArrayList<>();
        for (TargetFile target : targets) {
            if (target.exists()) conflicts.add(target);
            else decisions.put(target.getTaskId(), Decision.GENERATE);
        }
        if (conflicts.isEmpty()) return decisions;

        StringBuilder message = new StringBuilder(
                "The following files already exist. AutoSpring will not overwrite them silently.\n\n");
        for (TargetFile conflict : conflicts) {
            message.append("• ").append(conflict.getDisplayPath())
                    .append(conflict.isShared() ? "  (shared file — will be appended/updated)" : "")
                    .append('\n');
        }

        String[] options = {"Skip existing (recommended)", "Replace existing", "Cancel generation"};
        int choice = Messages.showChooseDialog(
                project, message.toString(), "AutoSpring — File Conflict Preview",
                Messages.getQuestionIcon(), options, options[0]);
        if (choice == 2 || choice == -1) return null;

        Decision decision = choice == 1 ? Decision.REPLACE : Decision.SKIP;
        for (TargetFile conflict : conflicts) {
            decisions.put(conflict.getTaskId(), decision);
        }
        if (decision == Decision.REPLACE) {
                int confirm = Messages.showYesNoDialog(
                        project,
                        "Replace all listed existing files? This cannot be undone except through "
                                + "IntelliJ Local History.",
                        "Confirm Replace", Messages.getWarningIcon());
                if (confirm != Messages.YES) return null;
        }
        return decisions;
    }

    /** Formats a target list for the plan editor's preview action. */
    public static String formatPreview(List<TargetFile> targets) {
        StringBuilder sb = new StringBuilder("AutoSpring expected output files:\n\n");
        for (TargetFile target : targets) {
            sb.append(target).append('\n');
        }
        if (targets.isEmpty()) sb.append("(no output files predicted)\n");
        return sb.toString();
    }

    private static boolean isEnabled(GenerationPlan plan, String taskId) {
        GenerationTask task = plan.find(taskId);
        return task != null && task.isEnabled();
    }

    /** Returns the PSI files that already exist for one task (for REPLACE decisions). */
    public static List<PsiFile> existingFilesForTask(
            Project project,
            PsiDirectory sourceRoot,
            ClassMeta meta,
            String taskId
    ) {
        List<PsiFile> files = new ArrayList<>();
        if (sourceRoot == null || meta == null) return files;
        for (Location location : locationsForTask(taskId, meta.basePackage(), meta.getClassName())) {
            if (location.shared) continue;
            PsiDirectory dir = location.rootFile
                    ? findProjectRoot(sourceRoot)
                    : findPackageDir(sourceRoot, location.packageName);
            if (dir == null) continue;
            PsiFile file = dir.findFile(location.fileName);
            if (file != null) files.add(file);
        }
        return files;
    }

    private record Location(String packageName, String fileName, boolean rootFile, boolean shared) {}

    private static List<Location> locationsForTask(String taskId, String base, String entity) {
        List<Location> locations = new ArrayList<>();
        switch (taskId) {
            case "entity" -> locations.add(new Location(base + ".entity", entity + ".java", false, false));
            case "dto" -> locations.add(new Location(base + ".dto", entity + "Dto.java", false, false));
            case "mapper" -> locations.add(new Location(base + ".mapper", entity + "Mapper.java", false, false));
            case "repository" -> locations.add(new Location(base + ".repository", entity + "Repository.java", false, false));
            case "service" -> locations.add(new Location(base + ".service", entity + "Service.java", false, false));
            case "controller" -> locations.add(new Location(base + ".controller", entity + "Controller.java", false, false));
            case "pagination" -> {
                locations.add(new Location(base + ".dto", "PageResponse.java", false, false));
                locations.add(new Location(base + ".dto", "SortDirection.java", false, false));
            }
            case "swagger" -> {
                locations.add(new Location(base + ".config", "OpenApiConfig.java", false, false));
                locations.add(new Location("", "API_DOCUMENTATION.md", true, false));
            }
            case "exception-handling" -> {
                locations.add(new Location(base + ".exception", "ResourceNotFoundException.java", false, false));
                locations.add(new Location(base + ".exception", "BadRequestException.java", false, false));
                locations.add(new Location(base + ".exception", "DuplicateResourceException.java", false, false));
                locations.add(new Location(base + ".exception", "GlobalExceptionHandler.java", false, false));
                locations.add(new Location(base + ".dto", "ErrorResponse.java", false, false));
            }
            case "auditing" -> {
                locations.add(new Location(base + ".entity", "BaseAuditEntity.java", false, false));
                locations.add(new Location(base + ".config", "JpaAuditingConfig.java", false, false));
                locations.add(new Location("", "AUDITING_GUIDE.md", true, false));
            }
            case "security" -> {
                locations.add(new Location(base + ".config", "SecurityConfig.java", false, false));
                locations.add(new Location(base + ".security", "JwtService.java", false, false));
                locations.add(new Location(base + ".security", "JwtAuthenticationFilter.java", false, false));
                locations.add(new Location(base + ".entity", "AppUser.java", false, false));
                locations.add(new Location(base + ".entity", "Role.java", false, false));
                locations.add(new Location(base + ".repository", "AppUserRepository.java", false, false));
                locations.add(new Location(base + ".security", "UserDetailsServiceImpl.java", false, false));
                locations.add(new Location(base + ".service", "AuthenticationService.java", false, false));
                locations.add(new Location(base + ".controller", "AuthenticationController.java", false, false));
                locations.add(new Location(base + ".dto", "AuthenticationRequest.java", false, false));
                locations.add(new Location(base + ".dto", "RegisterRequest.java", false, false));
                locations.add(new Location(base + ".dto", "AuthenticationResponse.java", false, false));
                locations.add(new Location("", "SECURITY_GUIDE.md", true, false));
            }
            case "properties" -> locations.add(new Location("", "application.properties", false, true));
            case "dependencies" -> locations.add(new Location("", "build.gradle / pom.xml", false, true));
            default -> { /* validation has no files */ }
        }
        return locations;
    }

    private static PsiDirectory findPackageDir(PsiDirectory root, String packageName) {
        PsiDirectory current = root;
        for (String part : packageName.split("\\.")) {
            if (part.isEmpty()) continue;
            current = current.findSubdirectory(part);
            if (current == null) return null;
        }
        return current;
    }

    private static PsiDirectory findResourcesDir(PsiDirectory sourceRoot) {
        PsiDirectory main = sourceRoot.getParentDirectory();
        if (main == null || !"main".equals(main.getName())) return null;
        PsiDirectory module = main.getParentDirectory();
        if (module == null) return null;
        return module.findSubdirectory("resources");
    }

    private static PsiDirectory findProjectRoot(PsiDirectory sourceRoot) {
        PsiDirectory current = sourceRoot;
        while (current != null) {
            if ("src".equals(current.getName())) {
                return current.getParentDirectory();
            }
            current = current.getParentDirectory();
        }
        return null;
    }
}
