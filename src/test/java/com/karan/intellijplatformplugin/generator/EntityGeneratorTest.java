package com.karan.intellijplatformplugin.generator;

import com.karan.intellijplatformplugin.model.GenerationOptions;
import com.karan.intellijplatformplugin.prompt.EntityDefinition;
import com.karan.intellijplatformplugin.prompt.PromptFieldDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityGeneratorTest {

    private static EntityDefinition definition() {
        return new EntityDefinition("Employee", "com.example.demo.entity", "employees", true, List.of(
                PromptFieldDefinition.builder("id", "Long", 3).primaryKey(true).generated(true).build(),
                PromptFieldDefinition.builder("name", "String", 4).required(true).max(100).build(),
                PromptFieldDefinition.builder("email", "String", 5).required(true).unique(true).email(true).build(),
                PromptFieldDefinition.builder("salary", "BigDecimal", 6).min(0).max(999999).build(),
                PromptFieldDefinition.builder("joiningDate", "LocalDate", 7).build()
        ));
    }

    @Test
    void generatesPackageImportsAnnotationsFieldsAndAccessors() {
        String source = EntityGenerator.buildEntitySource(
                definition(), GenerationOptions.defaults(), "jakarta", "com.example.demo");

        assertTrue(source.contains("package com.example.demo.entity;"));
        assertTrue(source.contains("import jakarta.persistence.Entity;"));
        assertTrue(source.contains("import jakarta.persistence.GeneratedValue;"));
        assertTrue(source.contains("import jakarta.persistence.Table;"));
        assertTrue(source.contains("import java.math.BigDecimal;"));
        assertTrue(source.contains("import java.time.LocalDate;"));
        assertTrue(source.contains("import jakarta.validation.constraints.Email;"));
        assertTrue(source.contains("import jakarta.validation.constraints.NotBlank;"));
        assertTrue(source.contains("@Table(name = \"employees\")"));
        assertTrue(source.contains("@Entity"));
        assertTrue(source.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"));
        assertTrue(source.contains("@Column(nullable = false, unique = true)"));
        assertTrue(source.contains("@Min(0)"));
        assertTrue(source.contains("@Max(999999)"));
        assertTrue(source.contains("private Long id;"));
        assertTrue(source.contains("public String getName()"));
        assertTrue(source.contains("public void setName(String name)"));
        assertTrue(source.contains("public Employee()"));
    }

    @Test
    void javaxNamespaceAndValidationDisabled() {
        String source = EntityGenerator.buildEntitySource(
                definition(), GenerationOptions.builder().generateValidation(false).build(),
                "javax", "com.example.demo");

        assertTrue(source.contains("import javax.persistence.Entity;"));
        assertFalse(source.contains("jakarta.validation.constraints"));
        assertFalse(source.contains("@NotBlank"));
    }

    @Test
    void manuallyAssignedIdHasNoGeneratedValue() {
        EntityDefinition manual = new EntityDefinition("Product", "com.example.entity", null, false, List.of(
                PromptFieldDefinition.builder("sku", "String", 2).primaryKey(true).build()
        ));
        String source = EntityGenerator.buildEntitySource(
                manual, GenerationOptions.defaults(), "jakarta", "com.example");
        assertFalse(source.contains("@GeneratedValue"));
        assertTrue(source.contains("@Id"));
        assertFalse(source.contains("@Table"));
    }

    @Test
    void missingIdIsRejected() {
        EntityDefinition noId = new EntityDefinition("Broken", "com.example.entity", null, false, List.of(
                PromptFieldDefinition.builder("name", "String", 2).build()
        ));
        assertThrows(IllegalArgumentException.class, () ->
                EntityGenerator.buildEntitySource(noId, GenerationOptions.defaults(), "jakarta", "com.example"));
    }
}
