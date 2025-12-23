package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates OpenAPI 3.0 configuration (Springdoc) with proper server setup.
 */
public class SwaggerConfigGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".config";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

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
                        // Create server configuration
                        Server localServer = new Server();
                        localServer.setUrl("http://localhost:" + serverPort);
                        localServer.setDescription("Local Development Server");
                        
                        // Create contact information
                        Contact contact = new Contact();
                        contact.setName("API Support Team");
                        contact.setEmail("support@example.com");
                        contact.setUrl("https://www.example.com");
                        
                        // Create license information
                        License license = new License();
                        license.setName("MIT License");
                        license.setUrl("https://opensource.org/licenses/MIT");
                        
                        // Create API information
                        Info info = new Info()
                                .title(applicationName + " API Documentation")
                                .version("1.0.0")
                                .description("RESTful API documentation for " + applicationName + 
                                           ". This API provides comprehensive CRUD operations for managing resources.")
                                .contact(contact)
                                .license(license);
                        
                        // Build and return OpenAPI configuration
                        return new OpenAPI()
                                .servers(List.of(localServer))
                                .info(info);
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "OpenApiConfig.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}