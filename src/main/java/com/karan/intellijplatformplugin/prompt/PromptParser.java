package com.karan.intellijplatformplugin.prompt;

import com.karan.intellijplatformplugin.model.GenerationOptions;
import com.karan.intellijplatformplugin.prompt.PromptValidationResult.PromptError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, line-based parser for AutoSpring prompts.
 *
 * <p>No AI, network calls, or external parsing libraries are used. The grammar
 * is intentionally small and predictable:
 *
 * <pre>
 * Entity: Employee
 * Package: com.example.demo.entity
 * Fields:
 *     id: Long [id, generated]
 *     name: String [required, max=100]
 * Generate:
 *     entity, dto, mapper, repository, service, controller
 * Database: PostgreSQL
 * Security: JWT
 * </pre>
 *
 * <p>Every error carries the source line number so the UI can show actionable
 * feedback. Parsing never creates files.
 */
public final class PromptParser {

    private static final Pattern HEADER_PATTERN =
            Pattern.compile("^([A-Za-z][A-Za-z ]*?)\\s*:\\s*(.*)$");
    // The name group deliberately accepts digits so an invalid identifier such
    // as "1name" can be reported as an invalid Java field name instead of a
    // generic malformed definition.
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("^\\s*-?\\s*([A-Za-z0-9_$]+)\\s*:\\s*"
                    + "([A-Za-z_$][A-Za-z0-9_$.]*)\\s*(?:\\[([^\\]]*)\\])?\\s*\\.?\\s*$");
    private static final Pattern INLINE_ENTITY_PATTERN =
            Pattern.compile("(?i)^\\s*(generate|create)\\s+crud\\s+for\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.?\\s*$");
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$");

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "record", "sealed", "permits", "yield",
            "var", "true", "false", "null"
    );

    private static final Map<String, String> GEN_OPTION_SYNONYMS = buildGenOptionSynonyms();
    private static final Map<String, String> DATABASE_SYNONYMS = buildDatabaseSynonyms();
    private static final Map<String, String> SECURITY_SYNONYMS = buildSecuritySynonyms();

    private enum Section { NONE, ENTITY, PACKAGE, TABLE, FIELDS, GENERATE, DATABASE, SECURITY, PERSISTENCE }

    private PromptParser() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /** Parses and validates the supplied prompt text. */
    public static PromptValidationResult parse(String prompt) {
        List<PromptError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (prompt == null || prompt.isBlank()) {
            errors.add(new PromptError(1, "Prompt is empty.",
                    "Describe an entity, e.g. 'Entity: Employee' followed by a Fields section."));
            return PromptValidationResult.failure(errors, warnings);
        }

        String[] lines = prompt.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        String entityName = null;
        int entityLine = 0;
        String packageName = null;
        String tableName = null;
        int fieldsHeaderLine = 0;

        List<PromptFieldDefinition> fields = new ArrayList<>();
        Set<String> fieldNames = new HashSet<>();

        boolean generateSectionSeen = false;
        Set<String> requestedOptions = new LinkedHashSet<>();
        Map<String, Integer> optionLines = new HashMap<>();

        String database = "None";
        String security = "None";
        String namespace = PromptSpecification.NAMESPACE_AUTO;

        Section section = Section.NONE;

        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String rawLine = lines[i];
            String trimmed = rawLine.strip();

            if (trimmed.isEmpty()) continue;

            Matcher header = HEADER_PATTERN.matcher(trimmed);
            if (header.matches()) {
                String keyword = header.group(1).trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
                String value = header.group(2) == null ? "" : header.group(2).strip();
                Section recognized = recognizeSection(keyword);
                if (recognized != null) {
                    section = recognized;
                    switch (recognized) {
                        case ENTITY -> {
                            if (entityName != null) {
                                errors.add(new PromptError(lineNumber, "Duplicate Entity declaration."));
                            } else if (!value.isEmpty()) {
                                entityName = stripTrailingPeriod(value);
                                entityLine = lineNumber;
                            }
                        }
                        case PACKAGE -> {
                            if (!value.isEmpty()) packageName = stripTrailingPeriod(value);
                        }
                        case TABLE -> {
                            if (!value.isEmpty()) tableName = stripTrailingPeriod(value);
                        }
                        case FIELDS -> {
                            fieldsHeaderLine = lineNumber;
                            if (!value.isEmpty()) {
                                parseFieldLine(value, lineNumber, fields, fieldNames, errors);
                            }
                        }
                        case GENERATE -> {
                            generateSectionSeen = true;
                            if (!value.isEmpty()) {
                                parseGenOptions(value, lineNumber, requestedOptions, optionLines, errors);
                            }
                        }
                        case DATABASE -> {
                            String resolved = DATABASE_SYNONYMS.get(value.toLowerCase(Locale.ROOT));
                            if (resolved == null) {
                                errors.add(new PromptError(lineNumber,
                                        "Unsupported database: " + value,
                                        "Supported: None, MySQL, PostgreSQL, H2. "
                                                + "MongoDB is not supported in JPA prompt mode."));
                            } else {
                                database = resolved;
                            }
                        }
                        case SECURITY -> {
                            String resolved = SECURITY_SYNONYMS.get(value.toLowerCase(Locale.ROOT));
                            if (resolved == null) {
                                errors.add(new PromptError(lineNumber,
                                        "Unsupported security mode: " + value,
                                        "Supported: None, JWT."));
                            } else {
                                security = resolved;
                            }
                        }
                        case PERSISTENCE -> {
                            String valueLower = value.toLowerCase(Locale.ROOT);
                            if (valueLower.equals("jakarta") || valueLower.equals("javax")) {
                                namespace = valueLower;
                            } else {
                                errors.add(new PromptError(lineNumber,
                                        "Unsupported persistence namespace: " + value,
                                        "Supported: jakarta, javax, auto."));
                            }
                        }
                        default -> { /* NONE has no value handling */ }
                    }
                    continue;
                }
            }

            // Section transitions that do not use a colon (concise syntax).
            Matcher inline = INLINE_ENTITY_PATTERN.matcher(trimmed);
            if (inline.matches()) {
                if (entityName == null) {
                    entityName = inline.group(2);
                    entityLine = lineNumber;
                }
                continue;
            }
            if (trimmed.regionMatches(true, 0, "Include ", 0, 8)
                    || trimmed.regionMatches(true, 0, "Generate ", 0, 9)) {
                section = Section.GENERATE;
                generateSectionSeen = true;
                String value = trimmed.substring(trimmed.indexOf(' ') + 1).strip();
                parseGenOptions(value, lineNumber, requestedOptions, optionLines, errors);
                continue;
            }

            switch (section) {
                case FIELDS -> parseFieldLine(trimmed, lineNumber, fields, fieldNames, errors);
                case GENERATE -> parseGenOptions(trimmed, lineNumber, requestedOptions, optionLines, errors);
                default -> errors.add(new PromptError(lineNumber,
                        "Unrecognised instruction: " + trimmed,
                        "Use a supported section header such as Entity:, Fields:, Generate:, "
                                + "Database: or Security:."));
            }
        }

        // ---- Entity name validation -------------------------------------------------
        if (entityName == null || entityName.isBlank()) {
            errors.add(new PromptError(fieldsHeaderLine > 0 ? fieldsHeaderLine : 1,
                    "Missing entity name.",
                    "Add 'Entity: Employee' or 'Generate CRUD for Employee.'"));
        } else if (!CLASS_NAME_PATTERN.matcher(entityName).matches()
                || JAVA_KEYWORDS.contains(entityName.toLowerCase(Locale.ROOT))) {
            errors.add(new PromptError(entityLine,
                    "Invalid Java class name: " + entityName,
                    "Use a Java identifier starting with a letter, e.g. Employee."));
        }

        // ---- Package validation -----------------------------------------------------
        if (packageName != null && !PACKAGE_PATTERN.matcher(packageName).matches()) {
            errors.add(new PromptError(findLine(lines, "Package:"),
                    "Invalid package name: " + packageName,
                    "Use dot-separated Java identifiers, e.g. com.example.demo.entity."));
        } else if (packageName == null) {
            warnings.add("No Package declared — AutoSpring will detect the application package.");
        }

        // ---- Primary key rules ------------------------------------------------------
        long idCount = fields.stream().filter(PromptFieldDefinition::isPrimaryKey).count();
        if (idCount == 0) {
            errors.add(new PromptError(fieldsHeaderLine > 0 ? fieldsHeaderLine : 1,
                    "Exactly one primary key is required in prompt mode.",
                    "Mark one field with [id], e.g. 'id: Long [id, generated]'."));
        } else if (idCount > 1) {
            for (PromptFieldDefinition field : fields) {
                if (field.isPrimaryKey()) {
                    errors.add(new PromptError(field.getLineNumber(),
                            "Multiple primary keys declared. Only one [id] field is supported.",
                            "Remove [id] from all but one field."));
                }
            }
        }

        if (fields.isEmpty()) {
            errors.add(new PromptError(fieldsHeaderLine > 0 ? fieldsHeaderLine : 1,
                    "No fields declared.",
                    "Add a Fields section with at least an id field."));
        }

        // ---- Generation options -----------------------------------------------------
        GenerationOptions.Builder optionsBuilder = GenerationOptions.builder();
        if (generateSectionSeen) {
            optionsBuilder
                    .generateDto(false)
                    .generateMapper(false)
                    .generateRepository(false)
                    .generateService(false)
                    .generateController(false)
                    .generatePagination(false)
                    .generateValidation(false)
                    .generateSwagger(false)
                    .generateExceptionHandling(false)
                    .generateAuditing(false)
                    .includeSecurity("JWT".equals(security));
            applyGenerationOptions(optionsBuilder, requestedOptions, optionLines, errors, security);
        } else {
            optionsBuilder.includeSecurity("JWT".equals(security));
            warnings.add("No Generate section — using the full default CRUD configuration.");
        }
        // Entity creation is mandatory in prompt mode.
        optionsBuilder.generateEntity(true);

        boolean validationEnabled = !generateSectionSeen || requestedOptions.contains("validation");
        if (!validationEnabled && fields.stream().anyMatch(f -> f.isRequired() || f.isUnique() || f.isEmail())) {
            warnings.add("Field constraints are declared but validation generation is disabled; "
                    + "the entity will still carry deterministic JPA/validation annotations.");
        }

        if (!errors.isEmpty()) {
            return PromptValidationResult.failure(errors, warnings);
        }

        EntityDefinition entityDefinition = new EntityDefinition(
                entityName, packageName, tableName, tableName != null, fields);
        GenerationOptions options = optionsBuilder.build();

        PromptSpecification specification = new PromptSpecification(
                entityDefinition, options, database, security, packageName, namespace, warnings);
        return PromptValidationResult.success(specification, warnings);
    }

    // ---------------------------------------------------------------------------------
    // Field parsing
    // ---------------------------------------------------------------------------------

    private static void parseFieldLine(
            String text,
            int lineNumber,
            List<PromptFieldDefinition> fields,
            Set<String> fieldNames,
            List<PromptError> errors
    ) {
        String cleaned = text.strip();
        if (cleaned.startsWith("-") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1).strip();
        }

        if (looksLikeRelationship(cleaned)) {
            errors.add(new PromptError(lineNumber,
                    "Relationship declarations are not supported in prompt mode: " + cleaned,
                    "Prompt mode supports scalar fields only. Use the existing entity workflow "
                            + "for @OneToMany/@ManyToOne relationships."));
            return;
        }

        Matcher matcher = FIELD_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            errors.add(new PromptError(lineNumber,
                    "Malformed field definition: " + cleaned,
                    "Expected: name: Type [optional flags], e.g. name: String [required, max=100]"));
            return;
        }

        String name = matcher.group(1);
        String rawType = matcher.group(2);
        String optionsText = matcher.group(3) == null ? "" : matcher.group(3);

        if (!CLASS_NAME_PATTERN.matcher(name).matches() || JAVA_KEYWORDS.contains(name.toLowerCase(Locale.ROOT))) {
            errors.add(new PromptError(lineNumber,
                    "Invalid Java field name: " + name,
                    "Use a Java identifier such as name or joiningDate."));
            return;
        }

        String type = FieldTypeResolver.canonical(rawType);
        if (type == null) {
            errors.add(new PromptError(lineNumber,
                    "Unsupported Java type: " + rawType,
                    "Supported alternatives: " + FieldTypeResolver.supportedTypesText()));
            return;
        }

        if (!fieldNames.add(name)) {
            errors.add(new PromptError(lineNumber, "Duplicate field name: " + name));
            return;
        }

        PromptFieldDefinition.Builder builder = PromptFieldDefinition.builder(name, type, lineNumber);
        boolean primaryKey = false;
        boolean generated = false;

        for (String rawOption : optionsText.split(",")) {
            String option = rawOption.trim();
            if (option.isEmpty()) continue;
            String normalized = option.toLowerCase(Locale.ROOT);

            switch (normalized) {
                case "id" -> primaryKey = true;
                case "generated" -> generated = true;
                case "required", "notnull", "not_blank", "notblank" -> builder.required(true);
                case "unique" -> builder.unique(true);
                case "email" -> builder.email(true);
                default -> {
                    if (normalized.startsWith("max=")) {
                        Integer parsed = parsePositiveInteger(normalized.substring(4), lineNumber, errors);
                        if (parsed != null) builder.max(parsed);
                    } else if (normalized.startsWith("min=")) {
                        Integer parsed = parseInteger(normalized.substring(4), lineNumber, errors);
                        if (parsed != null) builder.min(parsed);
                    } else if (normalized.startsWith("size=")) {
                        Integer parsed = parsePositiveInteger(normalized.substring(5), lineNumber, errors);
                        if (parsed != null) builder.size(parsed);
                    } else {
                        errors.add(new PromptError(lineNumber,
                                "Unsupported field option: " + option,
                                "Supported: id, generated, required, unique, email, max=N, min=N, size=N."));
                    }
                }
            }
        }

        if (generated && !primaryKey) {
            errors.add(new PromptError(lineNumber,
                    "'generated' can only be used together with [id]."));
        }
        if (normalizedHas(optionsText, "email") && !FieldTypeResolver.isTextual(type)) {
            errors.add(new PromptError(lineNumber,
                    "@Email is only valid on String fields, not " + type + "."));
        }
        if ((normalizedHas(optionsText, "max=") || normalizedHas(optionsText, "min="))
                && !FieldTypeResolver.isNumeric(type)) {
            errors.add(new PromptError(lineNumber,
                    "@Min/@Max are only valid on numeric fields, not " + type + "."));
        }
        if (normalizedHas(optionsText, "size=") && !FieldTypeResolver.isTextual(type)) {
            errors.add(new PromptError(lineNumber,
                    "@Size is only valid on String fields, not " + type + "."));
        }

        fields.add(builder.primaryKey(primaryKey).generated(generated).build());
    }

    private static boolean normalizedHas(String optionsText, String token) {
        return optionsText.toLowerCase(Locale.ROOT).contains(token);
    }

    private static Integer parsePositiveInteger(String value, int lineNumber, List<PromptError> errors) {
        Integer parsed = parseInteger(value, lineNumber, errors);
        if (parsed != null && parsed <= 0) {
            errors.add(new PromptError(lineNumber, "Value must be greater than zero: " + value));
            return null;
        }
        return parsed;
    }

    private static Integer parseInteger(String value, int lineNumber, List<PromptError> errors) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            errors.add(new PromptError(lineNumber, "Invalid integer value: " + value));
            return null;
        }
    }

    private static boolean looksLikeRelationship(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("many-to-one") || lower.contains("one-to-many")
                || lower.contains("many-to-many") || lower.contains("one-to-one")
                || lower.contains("@manytoone") || lower.contains("@onetomany")
                || lower.contains("@manytomany") || lower.contains("@onetoone")
                || lower.contains("relation:");
    }

    // ---------------------------------------------------------------------------------
    // Generation option parsing
    // ---------------------------------------------------------------------------------

    private static void parseGenOptions(
            String text,
            int lineNumber,
            Set<String> requestedOptions,
            Map<String, Integer> optionLines,
            List<PromptError> errors
    ) {
        String cleaned = text.strip();
        if (cleaned.isEmpty()) return;

        // Normalise documented multi-word synonyms before splitting.
        cleaned = cleaned.replaceAll("(?i)\\bdata transfer object\\b", "dto")
                .replaceAll("(?i)\\brest controller\\b", "controller")
                .replaceAll("(?i)\\bexception handling\\b", "exception-handling")
                .replaceAll("(?i)\\bauthentication\\b", "security");

        cleaned = cleaned.replaceAll("(?i)\\s+and\\s+", ", ");
        cleaned = cleaned.replace("、", ",");

        String[] tokens = cleaned.split("[,;\\n]");
        for (String token : tokens) {
            String normalized = token.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (normalized.isEmpty()) continue;
            normalized = normalized.replaceAll("^[-*\\s]+", "").replaceAll("[.\\s]+$", "");
            if (normalized.isEmpty()) continue;

            // Support whitespace-separated option lines such as "entity dto mapper".
            if (normalized.contains(" ") && !normalized.equals("exception-handling")) {
                for (String part : normalized.split("\\s+")) {
                    addGenOption(part, lineNumber, requestedOptions, optionLines, errors);
                }
            } else {
                addGenOption(normalized, lineNumber, requestedOptions, optionLines, errors);
            }
        }
    }

    private static void addGenOption(
            String rawOption,
            int lineNumber,
            Set<String> requestedOptions,
            Map<String, Integer> optionLines,
            List<PromptError> errors
    ) {
        String normalized = rawOption.strip().toLowerCase(Locale.ROOT).replaceAll("[.\\s]+$", "");
        if (normalized.isEmpty()) return;
        String resolved = GEN_OPTION_SYNONYMS.get(normalized);
        if (resolved == null) {
            errors.add(new PromptError(lineNumber,
                    "Unknown generation option: " + rawOption.strip(),
                    "Supported: entity, dto, mapper, repository, service, controller, pagination, "
                            + "validation, swagger, exception-handling, auditing, security, properties, "
                            + "dependencies."));
            return;
        }
        if (requestedOptions.add(resolved)) {
            optionLines.put(resolved, lineNumber);
        }
    }

    private static void applyGenerationOptions(
            GenerationOptions.Builder builder,
            Set<String> options,
            Map<String, Integer> optionLines,
            List<PromptError> errors,
            String security
    ) {
        for (String option : options) {
            switch (option) {
                case "dto" -> builder.generateDto(true);
                case "mapper" -> builder.generateMapper(true);
                case "repository" -> builder.generateRepository(true);
                case "service" -> builder.generateService(true);
                case "controller" -> builder.generateController(true);
                case "pagination" -> builder.generatePagination(true);
                case "validation" -> builder.generateValidation(true);
                case "swagger" -> builder.generateSwagger(true);
                case "exception-handling" -> builder.generateExceptionHandling(true);
                case "auditing" -> builder.generateAuditing(true);
                case "security" -> builder.includeSecurity(true);
                case "properties" -> builder.generateProperties(true);
                case "dependencies" -> builder.injectDependencies(true);
                case "entity" -> { /* mandatory in prompt mode */ }
                default -> errors.add(new PromptError(
                        optionLines.getOrDefault(option, 1),
                        "Unhandled generation option: " + option));
            }
        }
        if ("JWT".equalsIgnoreCase(security) && !options.contains("security")) {
            builder.includeSecurity(true);
        }
    }

    // ---------------------------------------------------------------------------------
    // Keyword tables (explicit and deterministic)
    // ---------------------------------------------------------------------------------

    private static Section recognizeSection(String keyword) {
        return switch (keyword) {
            case "entity", "model" -> Section.ENTITY;
            case "package" -> Section.PACKAGE;
            case "table" -> Section.TABLE;
            case "fields" -> Section.FIELDS;
            case "generate", "include", "components" -> Section.GENERATE;
            case "database", "db" -> Section.DATABASE;
            case "security", "authentication" -> Section.SECURITY;
            case "persistence", "namespace" -> Section.PERSISTENCE;
            default -> null;
        };
    }

    private static Map<String, String> buildGenOptionSynonyms() {
        Map<String, String> map = new HashMap<>();
        map.put("entity", "entity");
        map.put("model", "entity");
        map.put("dto", "dto");
        map.put("data transfer object", "dto");
        map.put("mapper", "mapper");
        map.put("repository", "repository");
        map.put("repo", "repository");
        map.put("service", "service");
        map.put("controller", "controller");
        map.put("rest controller", "controller");
        map.put("pagination", "pagination");
        map.put("validation", "validation");
        map.put("swagger", "swagger");
        map.put("openapi", "swagger");
        map.put("exception-handling", "exception-handling");
        map.put("exception handling", "exception-handling");
        map.put("exception", "exception-handling");
        map.put("auditing", "auditing");
        map.put("audit", "auditing");
        map.put("security", "security");
        map.put("jwt", "security");
        map.put("properties", "properties");
        map.put("dependencies", "dependencies");
        return Map.copyOf(map);
    }

    private static Map<String, String> buildDatabaseSynonyms() {
        Map<String, String> map = new HashMap<>();
        map.put("none", "None");
        map.put("mysql", "MySQL");
        map.put("postgresql", "PostgreSQL");
        map.put("postgres", "PostgreSQL");
        map.put("h2", "H2");
        return Map.copyOf(map);
    }

    private static Map<String, String> buildSecuritySynonyms() {
        Map<String, String> map = new HashMap<>();
        map.put("none", "None");
        map.put("off", "None");
        map.put("jwt", "JWT");
        map.put("jwt security", "JWT");
        map.put("authentication", "JWT");
        map.put("security", "JWT");
        return Map.copyOf(map);
    }

    private static String stripTrailingPeriod(String value) {
        String stripped = value.strip();
        while (stripped.endsWith(".")) {
            stripped = stripped.substring(0, stripped.length() - 1).strip();
        }
        return stripped;
    }

    private static int findLine(String[] lines, String needle) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().toLowerCase(Locale.ROOT).startsWith(needle.toLowerCase(Locale.ROOT))) {
                return i + 1;
            }
        }
        return 1;
    }
}
