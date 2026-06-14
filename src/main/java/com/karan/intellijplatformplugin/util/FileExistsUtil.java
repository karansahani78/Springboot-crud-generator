package com.karan.intellijplatformplugin.util;

import com.intellij.psi.PsiDirectory;

/**
 * Utility to check if files already exist.
 */
public class FileExistsUtil {

    /**
     * Checks if a file already exists in the directory.
     */
    public static boolean fileExists(PsiDirectory directory, String fileName) {
        if (directory == null || fileName == null) {
            return false;
        }
        return directory.findFile(fileName) != null;
    }

    /**
     * Checks if a file exists in a package directory.
     */
    public static boolean fileExistsInPackage(PsiDirectory root, String packageName, String fileName) {
        PsiDirectory dir = navigateToPackage(root, packageName);
        if (dir == null) {
            return false;
        }
        return fileExists(dir, fileName);
    }

    /**
     * Navigate to a package directory without creating it.
     */
    private static PsiDirectory navigateToPackage(PsiDirectory root, String packageName) {
        String[] parts = packageName.split("\\.");
        PsiDirectory current = root;

        for (String part : parts) {
            PsiDirectory sub = current.findSubdirectory(part);
            if (sub == null) {
                return null;
            }
            current = sub;
        }

        return current;
    }
}