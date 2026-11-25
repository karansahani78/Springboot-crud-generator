package com.karan.intellijplatformplugin.prompt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FieldTypeResolverTest {

    @Test
    void allDocumentedTypesAreSupported() {
        for (String type : List.of("String", "Integer", "int", "Long", "long", "Double", "double",
                "Float", "float", "Boolean", "boolean", "Short", "short", "Byte", "byte",
                "Character", "char", "BigDecimal", "BigInteger", "LocalDate", "LocalDateTime",
                "LocalTime", "ZonedDateTime", "OffsetDateTime", "Instant", "Date", "UUID")) {
            assertTrue(FieldTypeResolver.isSupported(type), type);
        }
    }

    @Test
    void unknownTypesRejected() {
        assertFalse(FieldTypeResolver.isSupported("BigDecimalX"));
        assertFalse(FieldTypeResolver.isSupported("List<String>"));
        assertNull(FieldTypeResolver.canonical(null));
    }

    @Test
    void canonicalSpellingAndCaseInsensitivity() {
        assertEquals("BigDecimal", FieldTypeResolver.canonical("bigdecimal"));
        assertEquals("LocalDate", FieldTypeResolver.canonical(" localdate "));
    }

    @Test
    void importsAreCorrectAndDeduplicated() {
        assertEquals("java.math.BigDecimal", FieldTypeResolver.importFor("BigDecimal"));
        assertEquals("java.time.LocalDate", FieldTypeResolver.importFor("LocalDate"));
        assertEquals("java.util.UUID", FieldTypeResolver.importFor("UUID"));
        assertNull(FieldTypeResolver.importFor("String"));
        assertNull(FieldTypeResolver.importFor("int"));

        PromptFieldDefinition id = PromptFieldDefinition.builder("id", "Long", 1).build();
        PromptFieldDefinition date = PromptFieldDefinition.builder("date", "LocalDate", 2).build();
        PromptFieldDefinition otherDate = PromptFieldDefinition.builder("other", "LocalDate", 3).build();
        Set<String> imports = FieldTypeResolver.importsFor(List.of(id, date, otherDate));
        assertEquals(Set.of("java.time.LocalDate"), imports);
    }

    @Test
    void numericAndTextualClassification() {
        assertTrue(FieldTypeResolver.isNumeric("BigDecimal"));
        assertTrue(FieldTypeResolver.isNumeric("int"));
        assertFalse(FieldTypeResolver.isNumeric("String"));
        assertTrue(FieldTypeResolver.isTextual("String"));
        assertFalse(FieldTypeResolver.isTextual("Integer"));
    }
}
