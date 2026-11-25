package com.karan.intellijplatformplugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural regression guard: both entry points must converge on the shared
 * generator orchestration, and the orchestration must still call all existing
 * generators. No IntelliJ runtime is required for this check.
 */
class WorkflowConvergenceTest {

    private static final Path ROOT = Path.of("src", "main", "java", "com", "karan", "intellijplatformplugin");

    @Test
    void legacyActionDelegatesToSharedService() throws IOException {
        String source = read(ROOT.resolve("action/GenerateCrudAction.java"));
        assertTrue(source.contains("CrudGenerationService.generateLegacy("),
                "Original entity workflow must delegate to CrudGenerationService");
    }

    @Test
    void promptModeUsesSharedService() throws IOException {
        String source = read(ROOT.resolve("service/PromptGenerationService.java"));
        assertTrue(source.contains("CrudGenerationService.generate("),
                "Prompt workflow must use CrudGenerationService");
    }

    @Test
    void sharedServiceCallsAllCoreGenerators() throws IOException {
        String source = read(ROOT.resolve("service/CrudGenerationService.java"));
        for (String generator : List.of("DtoGenerator", "MapperGenerator", "RepositoryGenerator",
                "ServiceGenerator", "ControllerGenerator", "PaginationGenerator",
                "SwaggerConfigGenerator", "ExceptionGenerator", "ErrorResponseGenerator",
                "GlobalExceptionHandlerGenerator", "ApplicationPropertiesGenerator",
                "BaseAuditEntityGenerator", "JpaAuditingConfigGenerator",
                "SecurityConfigGenerator", "JwtServiceGenerator", "JwtAuthenticationFilterGenerator",
                "UserEntityGenerator", "RoleEnumGenerator", "AppUserRepositoryGenerator",
                "UserDetailsServiceImplGenerator", "AuthenticationServiceGenerator",
                "AuthControllerGenerator", "AuthDtoGenerator")) {
            assertTrue(source.contains(generator + ".generate("), "Missing generator: " + generator);
        }
    }

    @Test
    void noExternalAiDependencies() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        for (String forbidden : List.of("openai", "anthropic", "langchain", "huggingface", "gemini")) {
            assertTrue(!build.toLowerCase().contains(forbidden),
                    "External AI dependency detected: " + forbidden);
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
