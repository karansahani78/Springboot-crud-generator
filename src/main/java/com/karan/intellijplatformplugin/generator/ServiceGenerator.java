package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;
public class ServiceGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {

        String pkg = meta.basePackage() + ".service";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = """
                package %s;

                import %s.%s;
                import %s.repository.%sRepository;
                import org.springframework.stereotype.Service;
                import java.util.List;

                @Service
                public class %sService {

                    private final %sRepository repository;

                    public %sService(%sRepository repository) {
                        this.repository = repository;
                    }

                    public List<%s> findAll() {
                        return repository.findAll();
                    }

                    public %s findById(%s id) {
                        return repository.findById(id).orElseThrow();
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
                meta.getClassName(),
                meta.getClassName(),
                meta.getIdType()
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        meta.getClassName() + "Service.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}
