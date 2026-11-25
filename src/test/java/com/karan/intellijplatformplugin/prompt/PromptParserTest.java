package com.karan.intellijplatformplugin.prompt;

import com.karan.intellijplatformplugin.model.GenerationOptions;
import com.karan.intellijplatformplugin.prompt.PromptValidationResult.PromptError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptParserTest {

    private static final String BASIC = """
            Entity: Employee
            Package: com.example.demo.entity
            Fields:
                id: Long [id, generated]
                name: String [required, size=100]
                email: String [required, unique, email]
                age: Integer
                salary: BigDecimal
                joiningDate: LocalDate
                active: Boolean
            Generate:
                entity
                dto
                mapper
                repository
                service
                controller
                pagination
                validation
                swagger
                exception-handling
                auditing
            Database: PostgreSQL
            Security: JWT
            """;

    @Test
    void validBasicEntityPrompt() {
        PromptValidationResult result = PromptParser.parse(BASIC);
        assertTrue(result.isValid(), () -> result.formatErrors());
        PromptSpecification spec = result.getSpecification();
        assertEquals("Employee", spec.getEntityDefinition().getClassName());
        assertEquals("com.example.demo.entity", spec.getEntityDefinition().getPackageName());
        assertEquals(7, spec.getEntityDefinition().getFields().size());
        assertEquals("PostgreSQL", spec.getDatabaseType());
        assertEquals("JWT", spec.getSecurityMode());
        assertTrue(spec.getGenerationOptions().isGenerateAuditing());
        assertTrue(spec.getGenerationOptions().isIncludeSecurity());
    }

    @Test
    void validGenerationOptionsAndDatabaseSelection() {
        String prompt = """
                Entity: Product
                Fields:
                    id: Long [id, generated]
                    name: String [required]
                Generate:
                    entity, dto, repository
                Database: MySQL
                Security: None
                """;
        PromptValidationResult result = PromptParser.parse(prompt);
        assertTrue(result.isValid(), () -> result.formatErrors());
        GenerationOptions options = result.getSpecification().getGenerationOptions();
        assertTrue(options.isGenerateDto());
        assertTrue(options.isGenerateRepository());
        assertFalse(options.isGenerateController());
        assertFalse(options.isGenerateService());
        assertFalse(options.isIncludeSecurity());
    }

    @Test
    void validConciseSyntaxWithSynonyms() {
        String prompt = """
                Generate CRUD for Product.

                Fields:
                - id: Long [id, generated]
                - name: String [required]
                - sku: String [unique]

                Include DTO, Mapper, Repository, Service,
                Controller, Pagination, Validation and Swagger.

                Database: MySQL
                Security: None
                """;
        PromptValidationResult result = PromptParser.parse(prompt);
        assertTrue(result.isValid(), () -> result.formatErrors());
        PromptSpecification spec = result.getSpecification();
        assertEquals("Product", spec.getEntityDefinition().getClassName());
        assertTrue(spec.getGenerationOptions().isGenerateSwagger());
        assertTrue(spec.getGenerationOptions().isGenerateValidation());
        assertFalse(spec.getGenerationOptions().isGenerateAuditing());
    }

    @Test
    void extraWhitespaceAndCaseInsensitiveKeywords() {
        String prompt = """
                  entity  :   employee
                  PACKAGE  :   com . example . demo . entity
                  fields  :
                    id   :   long  [  id , generated ]
                    name : string [ required ]
                  generate : entity , dto , service
                  database : h2
                  security : jwt
                """;
        PromptValidationResult result = PromptParser.parse(prompt);
        // dotted package with spaces is intentionally invalid; use simple case test instead
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Invalid package")));
    }

    @Test
    void keywordsAreCaseInsensitiveWithValidPackage() {
        String prompt = """
                  ENTITY  :   Employee
                  PACKAGE  :   com.example.demo.entity
                  FIELDS  :
                    ID   :   LONG  [  ID , GENERATED ]
                    NAME : STRING [ REQUIRED ]
                  GENERATE : ENTITY , DTO , SERVICE
                  DATABASE : H2
                  SECURITY : JWT
                """;
        PromptValidationResult result = PromptParser.parse(prompt);
        assertTrue(result.isValid(), () -> result.formatErrors());
        assertEquals("H2", result.getSpecification().getDatabaseType());
        assertTrue(result.getSpecification().getGenerationOptions().isGenerateService());
    }

    @Test
    void missingEntityName() {
        PromptValidationResult result = PromptParser.parse("""
                Fields:
                    id: Long [id]
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Missing entity name")));
    }

    @Test
    void invalidJavaClassName() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: 123Employee
                Fields:
                    id: Long [id]
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Invalid Java class name")));
    }

    @Test
    void invalidFieldName() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                    1name: String
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Invalid Java field name")));
    }

    @Test
    void unsupportedJavaTypeReportsLineNumber() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                    salary: BigDecimalX
                """);
        assertFalse(result.isValid());
        PromptError error = result.getErrors().get(0);
        assertEquals(4, error.getLineNumber());
        assertTrue(error.getMessage().contains("BigDecimalX"));
        assertTrue(error.getSuggestion().contains("BigDecimal"));
    }

    @Test
    void duplicateFieldsRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                    name: String
                    name: String
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Duplicate field name")));
    }

    @Test
    void missingPrimaryKeyRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    name: String
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("primary key")));
    }

    @Test
    void multiplePrimaryKeysRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                    code: String [id]
                """);
        assertFalse(result.isValid());
        assertEquals(2, result.getErrors().stream()
                .filter(e -> e.getMessage().contains("Multiple primary keys")).count());
    }

    @Test
    void invalidPackageRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Package: com..example
                Fields:
                    id: Long [id]
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Invalid package name")));
    }

    @Test
    void unknownGenerationOptionRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                Generate:
                    entity, blockchain
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Unknown generation option")));
    }

    @Test
    void malformedFieldDefinitionRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    name String
                    id: Long [id]
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Malformed field definition")));
    }

    @Test
    void unsupportedRelationshipSyntaxRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                    department: Department [many-to-one]
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("Relationship declarations")));
    }

    @Test
    void emptyPromptRejected() {
        PromptValidationResult result = PromptParser.parse("   \n  ");
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().get(0).getLineNumber());
    }

    @Test
    void mongodbRejectedWithExplanation() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                Database: MongoDB
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.getMessage().contains("Unsupported database")
                        && e.getSuggestion().contains("not supported")));
    }

    @Test
    void invalidConstraintTypeCombinationsRejected() {
        PromptValidationResult result = PromptParser.parse("""
                Entity: Employee
                Fields:
                    id: Long [id]
                    age: Integer [email]
                """);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getMessage().contains("@Email")));
    }
}
