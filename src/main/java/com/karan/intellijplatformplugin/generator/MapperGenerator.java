package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

public class MapperGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {

        String pkg = meta.basePackage() + ".mapper";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = """
                package %s;

                import %s.%s;
                import %s.dto.%sDto;

                public class %sMapper {

                    public static %s toEntity(%sDto dto) {
                        %s entity = new %s();
                        return entity;
                    }
                }
                """.formatted(
                pkg,
                meta.getPackageName(),
                meta.getClassName(),
                meta.basePackage(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName()
        );

        dir.add(PsiFileFactory.getInstance(project)
                .createFileFromText(meta.getClassName() + "Mapper.java",
                        JavaFileType.INSTANCE, code));
    }
}
