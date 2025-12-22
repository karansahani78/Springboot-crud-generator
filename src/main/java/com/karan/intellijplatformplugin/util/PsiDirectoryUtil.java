package com.karan.intellijplatformplugin.util;

import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for working with PSI directories and extracting class metadata.
 */
public final class PsiDirectoryUtil {

    private PsiDirectoryUtil() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Finds the source root directory (typically 'java' folder).
     */
    public static PsiDirectory getSourceRoot(PsiFile file) {
        if (file == null) {
            return null;
        }

        PsiDirectory dir = file.getContainingDirectory();
        while (dir != null) {
            String name = dir.getName();
            if ("java".equals(name)) {
                return dir;
            }
            dir = dir.getParentDirectory();
        }
        return null;
    }

    /**
     * Creates package directories if they don't exist.
     */
    public static PsiDirectory createPackageDirs(PsiDirectory root, String pkg) {
        if (root == null) {
            throw new IllegalArgumentException("Root directory cannot be null");
        }
        if (pkg == null || pkg.trim().isEmpty()) {
            return root;
        }

        PsiDirectory current = root;
        for (String part : pkg.split("\\.")) {
            if (part.isEmpty()) continue;

            PsiDirectory next = current.findSubdirectory(part);
            if (next == null) {
                next = current.createSubdirectory(part);
            }
            current = next;
        }
        return current;
    }

    /**
     * Extracts class metadata from a PSI class.
     */
    public static ClassMeta toClassMeta(PsiClass psiClass) {
        if (psiClass == null) {
            throw new IllegalArgumentException("PSI class cannot be null");
        }

        String className = psiClass.getName();
        if (className == null) {
            throw new IllegalStateException("Class name is null");
        }

        PsiFile containingFile = psiClass.getContainingFile();
        if (!(containingFile instanceof PsiJavaFile)) {
            throw new IllegalStateException("Class is not in a Java file");
        }

        String packageName = ((PsiJavaFile) containingFile).getPackageName();
        if (packageName == null || packageName.isEmpty()) {
            throw new IllegalStateException("Package name is empty");
        }

        String idType = "Long";
        List<FieldMeta> fields = new ArrayList<>();

        for (PsiField field : psiClass.getFields()) {
            // Skip static fields
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            String fieldName = field.getName();
            PsiType fieldType = field.getType();

            if (fieldName != null && fieldType != null) {
                fields.add(new FieldMeta(fieldName, fieldType.getPresentableText()));

                // Check for @Id annotation
                if (hasIdAnnotation(field)) {
                    idType = fieldType.getPresentableText();
                }
            }
        }

        return new ClassMeta(className, packageName, idType, fields);
    }

    /**
     * Checks if a field has @Id annotation (supports both javax and jakarta).
     */
    private static boolean hasIdAnnotation(PsiField field) {
        return field.hasAnnotation("jakarta.persistence.Id") ||
                field.hasAnnotation("javax.persistence.Id");
    }
}