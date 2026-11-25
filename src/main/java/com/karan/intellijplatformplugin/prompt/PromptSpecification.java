package com.karan.intellijplatformplugin.prompt;

import com.karan.intellijplatformplugin.model.GenerationOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Complete typed result of parsing a developer prompt. */
public final class PromptSpecification {

    public static final String NAMESPACE_AUTO = "auto";

    private final EntityDefinition entityDefinition;
    private final GenerationOptions generationOptions;
    private final String databaseType;
    private final String securityMode;
    private final String targetPackage;
    private final String sourceNamespace;
    private final List<String> warnings;

    public PromptSpecification(
            EntityDefinition entityDefinition,
            GenerationOptions generationOptions,
            String databaseType,
            String securityMode,
            String targetPackage,
            String sourceNamespace,
            List<String> warnings
    ) {
        this.entityDefinition = Objects.requireNonNull(entityDefinition, "entityDefinition");
        this.generationOptions = generationOptions == null ? GenerationOptions.defaults() : generationOptions;
        this.databaseType = databaseType == null || databaseType.isBlank() ? "None" : databaseType;
        this.securityMode = securityMode == null || securityMode.isBlank() ? "None" : securityMode;
        this.targetPackage = targetPackage;
        this.sourceNamespace = sourceNamespace == null ? NAMESPACE_AUTO : sourceNamespace;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public EntityDefinition getEntityDefinition() { return entityDefinition; }
    public GenerationOptions getGenerationOptions() { return generationOptions; }
    public String getDatabaseType() { return databaseType; }
    public String getSecurityMode() { return securityMode; }
    public String getTargetPackage() { return targetPackage; }
    public String getSourceNamespace() { return sourceNamespace; }
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }
    public boolean isJwtSecurity() { return "JWT".equalsIgnoreCase(securityMode); }

    public PromptSpecification withResolvedSettings(
            String resolvedPackage,
            String resolvedNamespace,
            boolean securityEnabled
    ) {
        GenerationOptions options = generationOptions.toBuilder()
                .includeSecurity(securityEnabled)
                .build();
        return new PromptSpecification(
                entityDefinition, options, databaseType,
                securityEnabled ? "JWT" : "None",
                resolvedPackage, resolvedNamespace, new ArrayList<>(warnings)
        );
    }

    @Override
    public String toString() {
        return "PromptSpecification{entity=" + entityDefinition.getClassName()
                + ", database=" + databaseType + ", security=" + securityMode + '}';
    }
}
