package com.karan.intellijplatformplugin.generator;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.karan.intellijplatformplugin.model.ClassMeta;

/**
 * Generates README with Springdoc OpenAPI setup instructions.
 */
public class SwaggerReadmeGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            return;
        }

        PsiDirectory projectRoot = root;
        while (projectRoot.getParentDirectory() != null &&
                !projectRoot.getName().equals("src")) {
            projectRoot = projectRoot.getParentDirectory();
        }
        if (projectRoot.getName().equals("src")) {
            projectRoot = projectRoot.getParentDirectory();
        }

        String readme = String.format("""
                # API Documentation with Springdoc OpenAPI 3.0
                
                ## Generated CRUD API for %s
                
                This project includes complete OpenAPI 3.0 documentation for all REST endpoints.
                
                ## 🚀 Quick Start
                
                1. **Make sure your `pom.xml` has the correct Spring Boot version:**
```xml
                   <parent>
                       <groupId>org.springframework.boot</groupId>
                       <artifactId>spring-boot-starter-parent</artifactId>
                       <version>3.2.0</version> <!-- Use 3.x, NOT 4.x -->
                       <relativePath/>
                   </parent>
```
                
                2. **Start your Spring Boot application:**
```bash
                   mvn spring-boot:run
```
                
                3. **Access Swagger UI:**
```
                   http://localhost:8080/swagger-ui.html
```
                   
                   Or with custom port (check your application.properties):
```
                   http://localhost:${server.port}/swagger-ui.html
```
                
                ## 📋 OpenAPI Specification
                
                Access the raw OpenAPI 3.0 JSON specification at:
```
                http://localhost:8080/v3/api-docs
```
                
                ## 📦 Required Dependencies (Already Added)
                
                ### Maven (`pom.xml`)
```xml
                <dependencies>
                    <!-- Spring Boot Web -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    
                    <!-- Spring Boot Data JPA -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                    
                    <!-- Validation -->
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>
                    
                    <!-- Springdoc OpenAPI UI -->
                    <dependency>
                        <groupId>org.springdoc</groupId>
                        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
                        <version>2.3.0</version>
                    </dependency>
                    
                    <!-- PostgreSQL Driver -->
                    <dependency>
                        <groupId>org.postgresql</groupId>
                        <artifactId>postgresql</artifactId>
                        <scope>runtime</scope>
                    </dependency>
                </dependencies>
```
                
                ### Gradle (`build.gradle`)
```gradle
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web'
                    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                    implementation 'org.springframework.boot:spring-boot-starter-validation'
                    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0'
                    runtimeOnly 'org.postgresql:postgresql'
                }
```
                
                ## ⚙️ Configuration (Already in application.properties)
```properties
                # Application Settings
                spring.application.name=Your-App-Name
                server.port=8080
                
                # Springdoc OpenAPI Configuration
                springdoc.api-docs.path=/v3/api-docs
                springdoc.swagger-ui.path=/swagger-ui.html
                springdoc.swagger-ui.enabled=true
                springdoc.swagger-ui.operations-sorter=method
                springdoc.swagger-ui.tags-sorter=alpha
```
                
                ## 🔗 API Endpoints for %s
                
                | Method | Endpoint | Description |
                |--------|----------|-------------|
                | GET | `/api/%s` | Get all %s |
                | GET | `/api/%s/{id}` | Get %s by ID |
                | POST | `/api/%s` | Create new %s |
                | PUT | `/api/%s/{id}` | Update %s by ID |
                | DELETE | `/api/%s/{id}` | Delete %s by ID |
                | HEAD | `/api/%s/{id}` | Check if %s exists |
                | GET | `/api/%s/count` | Count all %s |
                
                ## 🛡️ Exception Handling
                
                All endpoints return standardized error responses with proper HTTP status codes:
                
                | Status Code | Description |
                |-------------|-------------|
                | 200 | Success |
                | 201 | Created |
                | 204 | No Content (Delete success) |
                | 400 | Bad Request (Invalid input) |
                | 404 | Not Found (Resource doesn't exist) |
                | 409 | Conflict (Duplicate resource) |
                | 500 | Internal Server Error |
                
                ### Example Error Response
```json
                {
                    "timestamp": "2024-12-23T17:28:34",
                    "status": 404,
                    "error": "Not Found",
                    "message": "User not found with id: 123",
                    "path": "/api/user/123",
                    "details": []
                }
```
                
                ## 🧪 Testing with Swagger UI
                
                1. Navigate to Swagger UI (http://localhost:8080/swagger-ui.html)
                2. Find the endpoint you want to test
                3. Click on the endpoint to expand it
                4. Click **"Try it out"**
                5. Fill in the required parameters/body
                6. Click **"Execute"**
                7. View the response below
                
                ## 📝 Customizing the API Documentation
                
                Edit `OpenApiConfig.java` to customize:
```java
                @Configuration
                public class OpenApiConfig {
                    
                    @Bean
                    public OpenAPI customOpenAPI() {
                        return new OpenAPI()
                                .servers(List.of(
                                    new Server()
                                        .url("http://localhost:8080")
                                        .description("Local Development")
                                ))
                                .info(new Info()
                                    .title("Your API Title")
                                    .version("1.0.0")
                                    .description("Your API Description")
                                    .contact(new Contact()
                                        .name("Your Name")
                                        .email("your@email.com"))
                                );
                    }
                }
```
                
                ## 🐛 Troubleshooting
                
                ### Issue: "Failed to load API definition" or 500 error
                
                **Solution 1**: Check Spring Boot version
```xml
                <!-- Use 3.x, NOT 4.x -->
                <version>3.2.0</version>
```
                
                **Solution 2**: Verify correct dependency
```xml
                <!-- CORRECT -->
                <artifactId>spring-boot-starter-web</artifactId>
                
                <!-- WRONG -->
                <artifactId>spring-boot-starter-webmvc</artifactId>
```
                
                **Solution 3**: Check console logs for specific errors
```bash
                mvn spring-boot:run
                # Look for errors in the output
```
                
                ### Issue: Swagger UI not loading
                
                1. Verify the application is running: `http://localhost:8080`
                2. Check the port in `application.properties`
                3. Try accessing: `http://localhost:8080/swagger-ui/index.html`
                4. Clear browser cache
                
                ### Issue: Endpoints not showing
                
                1. Make sure your controllers have `@RestController` annotation
                2. Verify `@RequestMapping` paths are correct
                3. Check that the controller package is scanned by Spring Boot
                
                ## 🔒 Security Note
                
                **In Production**: Disable Swagger UI by adding to `application.properties`:
```properties
                springdoc.swagger-ui.enabled=false
                springdoc.api-docs.enabled=false
```
                
                Or use Spring Profiles:
```properties
                # application-prod.properties
                springdoc.swagger-ui.enabled=false
```
                
                ## 📚 Additional Resources
                
                - [Springdoc OpenAPI Documentation](https://springdoc.org/)
                - [OpenAPI Specification](https://swagger.io/specification/)
                - [Spring Boot Documentation](https://spring.io/projects/spring-boot)
                
                ---
                
                **Generated by Spring Boot CRUD Generator Plugin v1.0.3**
                
                For issues or suggestions, please report on GitHub.
                """,
                meta.getClassName(),
                meta.getClassName(),
                meta.getClassName().toLowerCase(), meta.getClassName(),
                meta.getClassName().toLowerCase(), meta.getClassName(),
                meta.getClassName().toLowerCase(), meta.getClassName(),
                meta.getClassName().toLowerCase(), meta.getClassName(),
                meta.getClassName().toLowerCase(), meta.getClassName(),
                meta.getClassName().toLowerCase(), meta.getClassName(),
                meta.getClassName().toLowerCase(), meta.getClassName()
        );

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText("API_DOCUMENTATION.md", readme);

        if (projectRoot != null && projectRoot.findFile("API_DOCUMENTATION.md") == null) {
            projectRoot.add(file);
        }
    }
}