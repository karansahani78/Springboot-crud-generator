package com.karan.intellijplatformplugin.model;

import java.util.Objects;

/**
 * The set of backend components selected for generation.
 *
 * <p>Prompt mode always includes the entity because the developer may not
 * have created one yet. Defaults represent a complete CRUD backend; security
 * remains opt-in.
 */
public final class GenerationOptions {

    private final boolean generateEntity;
    private final boolean generateDto;
    private final boolean generateMapper;
    private final boolean generateRepository;
    private final boolean generateService;
    private final boolean generateController;
    private final boolean generatePagination;
    private final boolean generateValidation;
    private final boolean generateSwagger;
    private final boolean generateExceptionHandling;
    private final boolean generateAuditing;
    private final boolean includeSecurity;
    private final boolean generateProperties;
    private final boolean injectDependencies;

    private GenerationOptions(Builder builder) {
        this.generateEntity = builder.generateEntity;
        this.generateDto = builder.generateDto;
        this.generateMapper = builder.generateMapper;
        this.generateRepository = builder.generateRepository;
        this.generateService = builder.generateService;
        this.generateController = builder.generateController;
        this.generatePagination = builder.generatePagination;
        this.generateValidation = builder.generateValidation;
        this.generateSwagger = builder.generateSwagger;
        this.generateExceptionHandling = builder.generateExceptionHandling;
        this.generateAuditing = builder.generateAuditing;
        this.includeSecurity = builder.includeSecurity;
        this.generateProperties = builder.generateProperties;
        this.injectDependencies = builder.injectDependencies;
    }

    public boolean isGenerateEntity() { return generateEntity; }
    public boolean isGenerateDto() { return generateDto; }
    public boolean isGenerateMapper() { return generateMapper; }
    public boolean isGenerateRepository() { return generateRepository; }
    public boolean isGenerateService() { return generateService; }
    public boolean isGenerateController() { return generateController; }
    public boolean isGeneratePagination() { return generatePagination; }
    public boolean isGenerateValidation() { return generateValidation; }
    public boolean isGenerateSwagger() { return generateSwagger; }
    public boolean isGenerateExceptionHandling() { return generateExceptionHandling; }
    public boolean isGenerateAuditing() { return generateAuditing; }
    public boolean isIncludeSecurity() { return includeSecurity; }
    public boolean isGenerateProperties() { return generateProperties; }
    public boolean isInjectDependencies() { return injectDependencies; }

    /** Complete CRUD defaults (security off). */
    public static GenerationOptions defaults() {
        return builder().build();
    }

    /** All core options enabled, including security — used by the legacy workflow. */
    public static GenerationOptions allWithSecurity() {
        return builder().includeSecurity(true).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .generateEntity(generateEntity)
                .generateDto(generateDto)
                .generateMapper(generateMapper)
                .generateRepository(generateRepository)
                .generateService(generateService)
                .generateController(generateController)
                .generatePagination(generatePagination)
                .generateValidation(generateValidation)
                .generateSwagger(generateSwagger)
                .generateExceptionHandling(generateExceptionHandling)
                .generateAuditing(generateAuditing)
                .includeSecurity(includeSecurity)
                .generateProperties(generateProperties)
                .injectDependencies(injectDependencies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GenerationOptions other)) return false;
        return generateEntity == other.generateEntity
                && generateDto == other.generateDto
                && generateMapper == other.generateMapper
                && generateRepository == other.generateRepository
                && generateService == other.generateService
                && generateController == other.generateController
                && generatePagination == other.generatePagination
                && generateValidation == other.generateValidation
                && generateSwagger == other.generateSwagger
                && generateExceptionHandling == other.generateExceptionHandling
                && generateAuditing == other.generateAuditing
                && includeSecurity == other.includeSecurity
                && generateProperties == other.generateProperties
                && injectDependencies == other.injectDependencies;
    }

    @Override
    public int hashCode() {
        return Objects.hash(generateEntity, generateDto, generateMapper, generateRepository,
                generateService, generateController, generatePagination, generateValidation,
                generateSwagger, generateExceptionHandling, generateAuditing, includeSecurity,
                generateProperties, injectDependencies);
    }

    @Override
    public String toString() {
        return "GenerationOptions{entity=" + generateEntity + ", dto=" + generateDto
                + ", mapper=" + generateMapper + ", repository=" + generateRepository
                + ", service=" + generateService + ", controller=" + generateController
                + ", security=" + includeSecurity + '}';
    }

    public static final class Builder {
        private boolean generateEntity = true;
        private boolean generateDto = true;
        private boolean generateMapper = true;
        private boolean generateRepository = true;
        private boolean generateService = true;
        private boolean generateController = true;
        private boolean generatePagination = true;
        private boolean generateValidation = true;
        private boolean generateSwagger = true;
        private boolean generateExceptionHandling = true;
        private boolean generateAuditing = true;
        private boolean includeSecurity = false;
        private boolean generateProperties = true;
        private boolean injectDependencies = true;

        public Builder generateEntity(boolean value) { this.generateEntity = value; return this; }
        public Builder generateDto(boolean value) { this.generateDto = value; return this; }
        public Builder generateMapper(boolean value) { this.generateMapper = value; return this; }
        public Builder generateRepository(boolean value) { this.generateRepository = value; return this; }
        public Builder generateService(boolean value) { this.generateService = value; return this; }
        public Builder generateController(boolean value) { this.generateController = value; return this; }
        public Builder generatePagination(boolean value) { this.generatePagination = value; return this; }
        public Builder generateValidation(boolean value) { this.generateValidation = value; return this; }
        public Builder generateSwagger(boolean value) { this.generateSwagger = value; return this; }
        public Builder generateExceptionHandling(boolean value) { this.generateExceptionHandling = value; return this; }
        public Builder generateAuditing(boolean value) { this.generateAuditing = value; return this; }
        public Builder includeSecurity(boolean value) { this.includeSecurity = value; return this; }
        public Builder generateProperties(boolean value) { this.generateProperties = value; return this; }
        public Builder injectDependencies(boolean value) { this.injectDependencies = value; return this; }

        public GenerationOptions build() {
            return new GenerationOptions(this);
        }
    }
}
