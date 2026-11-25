package com.karan.intellijplatformplugin.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenerationPlanTest {

    @Test
    void defaultPlanIsFullCrud() {
        GenerationPlan plan = GenerationPlan.defaults("Employee");
        GenerationOptions options = plan.toGenerationOptions();
        assertTrue(options.isGenerateEntity());
        assertTrue(options.isGenerateDto());
        assertTrue(options.isGenerateController());
        assertTrue(options.isGenerateSwagger());
        assertTrue(options.isGenerateAuditing());
        assertFalse(options.isIncludeSecurity());
        assertTrue(plan.validate().isEmpty(), () -> String.join(", ", plan.validate()));
    }

    @Test
    void selectiveGenerationDisablesOtherTasks() {
        GenerationPlan plan = GenerationPlan.from(GenerationOptions.builder()
                .generateDto(false).generateMapper(false).generateRepository(false)
                .generateService(false).generateController(false).generatePagination(false)
                .generateValidation(false).generateSwagger(false).generateExceptionHandling(false)
                .generateAuditing(false).generateProperties(false).injectDependencies(false)
                .build(), "Employee");
        GenerationOptions options = plan.toGenerationOptions();
        assertTrue(options.isGenerateEntity());
        assertFalse(options.isGenerateController());
        assertFalse(options.isGenerateSwagger());
    }

    @Test
    void dependencyValidationDetectsDisabledMapper() {
        GenerationPlan plan = GenerationPlan.defaults("Employee");
        plan.setEnabled("mapper", false);
        List<String> problems = plan.validate();
        assertFalse(problems.isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Mapper") || p.toLowerCase().contains("mapper")));
    }

    @Test
    void removedOptionalTaskBreaksDependents() {
        GenerationPlan plan = GenerationPlan.defaults("Employee");
        assertTrue(plan.remove("repository"));
        assertFalse(plan.validate().isEmpty());
        assertNull(plan.find("repository"));
    }

    @Test
    void resetRestoresOriginalState() {
        GenerationPlan plan = GenerationPlan.defaults("Employee");
        plan.remove("swagger");
        plan.setEnabled("controller", false);
        plan.reset();
        assertNotNull(plan.find("swagger"));
        assertTrue(plan.find("controller").isEnabled());
        assertTrue(plan.validate().isEmpty());
    }

    @Test
    void reorderRespectsDependencies() {
        GenerationPlan plan = GenerationPlan.defaults("Employee");
        int controllerOrder = plan.find("controller").getExecutionOrder();
        assertFalse(plan.reorder("controller", -1),
                "Controller must not move before its Service dependency");
        assertEquals(controllerOrder, plan.find("controller").getExecutionOrder());

        assertTrue(plan.reorder("dto", -1));
        assertTrue(plan.validate().isEmpty(), () -> String.join(", ", plan.validate()));
    }

    @Test
    void addSupportedTask() {
        GenerationPlan plan = GenerationPlan.defaults("Employee");
        assertTrue(plan.remove("swagger"));
        assertNull(plan.find("swagger"));
        assertTrue(plan.add(GenerationPlan.supportedTask("swagger", "Employee")));
        assertNotNull(plan.find("swagger"));
        assertTrue(plan.find("swagger").isEnabled());
        assertTrue(plan.validate().isEmpty(), () -> String.join(", ", plan.validate()));
    }

    @Test
    void entityTaskCannotBeRemoved() {
        GenerationPlan plan = GenerationPlan.defaults("Employee");
        assertFalse(plan.remove("entity"));
        assertNotNull(plan.find("entity"));
    }
}
