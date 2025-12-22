package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.*;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

public class DtoGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {

        String pkg = meta.basePackage() + ".dto";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        StringBuilder fields = new StringBuilder();
        for (FieldMeta f : meta.getFields()) {
            if (!f.getName().equalsIgnoreCase("id")) {
                fields.append("""
                        @jakarta.validation.constraints.NotNull
                        private %s %s;
                        """.formatted(f.getType(), f.getName()));
            }
        }

        String code = """
                package %s;

                public class %sDto {
                    %s
                }
                """.formatted(pkg, meta.getClassName(), fields);

        dir.add(PsiFileFactory.getInstance(project)
                .createFileFromText(meta.getClassName() + "Dto.java",
                        JavaFileType.INSTANCE, code));
    }
}
