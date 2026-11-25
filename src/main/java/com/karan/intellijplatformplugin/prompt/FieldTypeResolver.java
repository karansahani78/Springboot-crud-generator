package com.karan.intellijplatformplugin.prompt;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic resolver for the Java field types accepted by prompt mode.
 *
 * <p>This class contains no IntelliJ PSI dependencies so it can be unit tested
 * in isolation. It only handles types that the existing metadata models and
 * generators understand ({@code FieldMeta#isScalarType()}).
 */
public final class FieldTypeResolver {

    private static final Map<String, String> IMPORTS = buildImports();

    private static final List<String> SUPPORTED_TYPES = List.of(
            "String",
            "Integer", "int",
            "Long", "long",
            "Double", "double",
            "Float", "float",
            "Boolean", "boolean",
            "Short", "short",
            "Byte", "byte",
            "Character", "char",
            "BigDecimal", "BigInteger",
            "LocalDate", "LocalDateTime", "LocalTime",
            "ZonedDateTime", "OffsetDateTime", "Instant",
            "Date", "UUID"
    );

    private FieldTypeResolver() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    private static Map<String, String> buildImports() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("BigDecimal", "java.math.BigDecimal");
        map.put("BigInteger", "java.math.BigInteger");
        map.put("LocalDate", "java.time.LocalDate");
        map.put("LocalDateTime", "java.time.LocalDateTime");
        map.put("LocalTime", "java.time.LocalTime");
        map.put("ZonedDateTime", "java.time.ZonedDateTime");
        map.put("OffsetDateTime", "java.time.OffsetDateTime");
        map.put("Instant", "java.time.Instant");
        map.put("Date", "java.util.Date");
        map.put("UUID", "java.util.UUID");
        return Map.copyOf(map);
    }

    /** Returns all supported type names, in a stable user-facing order. */
    public static List<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    /** Case-insensitive check for a supported Java type. */
    public static boolean isSupported(String type) {
        return canonical(type) != null;
    }

    /**
     * Returns the canonical spelling of a supported type, or {@code null}.
     * Type matching is case-insensitive for developer convenience but the
     * canonical Java spelling is always returned.
     */
    public static String canonical(String type) {
        if (type == null || type.isBlank()) return null;
        String normalized = type.trim();
        for (String supported : SUPPORTED_TYPES) {
            if (supported.equalsIgnoreCase(normalized)) {
                return supported;
            }
        }
        return null;
    }

    /**
     * Returns the import required for a type, or {@code null} for primitives
     * and {@code java.lang} types.
     */
    public static String importFor(String type) {
        String canonical = canonical(type);
        return canonical == null ? null : IMPORTS.get(canonical);
    }

    /**
     * Returns a sorted set of all imports required by the given fields.
     * Never emits imports for primitives or {@code java.lang} types.
     */
    public static Set<String> importsFor(Collection<PromptFieldDefinition> fields) {
        Set<String> imports = new TreeSet<>();
        if (fields == null) return imports;
        for (PromptFieldDefinition field : fields) {
            if (field == null) continue;
            String imp = importFor(field.getJavaType());
            if (imp != null) imports.add(imp);
        }
        return imports;
    }

    /** Returns true if the type is numeric and can carry {@code @Min}/{@code @Max}. */
    public static boolean isNumeric(String type) {
        String canonical = canonical(type);
        if (canonical == null) return false;
        return switch (canonical) {
            case "Integer", "int", "Long", "long", "Double", "double",
                 "Float", "float", "Short", "short", "Byte", "byte",
                 "BigDecimal", "BigInteger" -> true;
            default -> false;
        };
    }

    /** Returns true if the type is a textual type and can carry {@code @NotBlank}/{@code @Size}. */
    public static boolean isTextual(String type) {
        return "String".equals(canonical(type));
    }

    /** Human-readable list for parser error messages. */
    public static String supportedTypesText() {
        StringBuilder sb = new StringBuilder();
        for (String type : SUPPORTED_TYPES) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(type);
        }
        return sb.toString();
    }

    /** Lowercase helper kept local to avoid locale-sensitive equalsIgnoreCase surprises. */
    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
