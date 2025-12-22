package com.karan.intellijplatformplugin.util;

import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.*;

import java.util.ArrayList;
import java.util.List;

public final class PsiDirectoryUtil {

    private PsiDirectoryUtil() {}

    public static PsiDirectory getSourceRoot(PsiFile file) {
        PsiDirectory dir = file.getContainingDirectory();
        while (dir != null) {
            if ("java".equals(dir.getName())) return dir;
            dir = dir.getParentDirectory();
        }
        return null;
    }

    public static PsiDirectory createPackageDirs(PsiDirectory root, String pkg) {
        PsiDirectory current = root;
        for (String part : pkg.split("\\.")) {
            PsiDirectory next = current.findSubdirectory(part);
            if (next == null) next = current.createSubdirectory(part);
            current = next;
        }
        return current;
    }

    public static ClassMeta toClassMeta(PsiClass psiClass) {

        String className = psiClass.getName();
        String packageName =
                ((PsiJavaFile) psiClass.getContainingFile()).getPackageName();

        String idType = "Long";
        List<FieldMeta> fields = new ArrayList<>();

        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;

            fields.add(new FieldMeta(
                    field.getName(),
                    field.getType().getPresentableText()
            ));

            if (field.hasAnnotation("jakarta.persistence.Id")) {
                idType = field.getType().getPresentableText();
            }
        }

        return new ClassMeta(className, packageName, idType, fields);
    }
}
