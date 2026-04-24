package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates OpenAPI 3.0 configuration (Springdoc) with optional JWT security.
 *
 * <p>Multi-entity change: accepts {@code List<ClassMeta> allEntities} — standard
 * contract. The generated {@code OpenApiConfig} itself is a singleton bean that
 * does not vary per-entity, so the list is intentionally unused in the output.
 */
public class SwaggerConfigGenerator {

    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities,  // ← standard contract; unused in output
            boolean includeSecurity
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg      = meta.basePackage() + ".config";
        String fileName = "OpenApiConfig.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists — skipping generation.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String securityImports = includeSecurity ? """
                import io.swagger.v3.oas.models.Components;
                import io.swagger.v3.oas.models.security.SecurityScheme;
                import io.swagger.v3.oas.models.security.SecurityRequirement;
                """ : "";

        String securityConfiguration = includeSecurity ? """
                        
                        // JWT Security Scheme
                        SecurityScheme securityScheme = new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization");
                        
                        Components components = new Components()
                                .addSecuritySchemes("bearerAuth", securityScheme);
                        
                        SecurityRequirement securityRequirement = new SecurityRequirement()
                                .addList("bearerAuth");
                        
                        return new OpenAPI()
                                .servers(List.of(localServer))
                                .info(info)
                                .components(components)
                                .addSecurityItem(securityRequirement);
                """ : """
                        
                        return new OpenAPI()
                                .servers(List.of(localServer))
                                .info(info);
                """;

        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.models.OpenAPI;
                import io.swagger.v3.oas.models.info.Contact;
                import io.swagger.v3.oas.models.info.Info;
                import io.swagger.v3.oas.models.info.License;
                import io.swagger.v3.oas.models.servers.Server;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;
                %s
                import java.util.List;
                
                /**
                 * OpenAPI 3.0 configuration for API documentation.
                 * Access documentation at: http://localhost:${server.port}/swagger-ui.html
                 */
                @Configuration
                public class OpenApiConfig {
                    
                    @Value("${server.port:8080}")
                    private String serverPort;
                    
                    @Value("${spring.application.name:Spring Boot Application}")
                    private String applicationName;
                    
                    @Bean
                    public OpenAPI customOpenAPI() {
                        Server localServer = new Server();
                        localServer.setUrl("http://localhost:" + serverPort);
                        localServer.setDescription("Local Development Server");
                        
                        Contact contact = new Contact();
                        contact.setName("API Support Team");
                        contact.setEmail("support@example.com");
                        contact.setUrl("https://www.example.com");
                        
                        License license = new License();
                        license.setName("MIT License");
                        license.setUrl("https://opensource.org/licenses/MIT");
                        
                        Info info = new Info()
                                .title(applicationName + " API Documentation")
                                .version("1.0.0")
                                .description("RESTful API documentation for " + applicationName +
                                           ". Provides comprehensive CRUD operations.")
                                .contact(contact)
                                .license(license);
                %s
                    }
                }
                """, pkg, securityImports, securityConfiguration);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }
}