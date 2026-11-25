package com.karan.intellijplatformplugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.model.FieldMeta;
import com.karan.intellijplatformplugin.model.GenerationOptions;
import com.karan.intellijplatformplugin.model.GenerationPlan;
import com.karan.intellijplatformplugin.prompt.EntityDefinition;
import com.karan.intellijplatformplugin.prompt.PromptFieldDefinition;
import com.karan.intellijplatformplugin.prompt.PromptParser;
import com.karan.intellijplatformplugin.prompt.PromptSpecification;
import com.karan.intellijplatformplugin.prompt.PromptValidationResult;
import com.karan.intellijplatformplugin.service.ChangePreviewService;
import com.karan.intellijplatformplugin.service.ProjectContextAnalyzer;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Native IntelliJ dialog for the deterministic, prompt-driven generation mode.
 *
 * <p>Prompt parsing happens locally through {@link PromptParser}; no AI or
 * network service is involved. The dialog exposes the parsed specification,
 * the resolved settings and an optional reviewed {@link GenerationPlan}.
 */
public class PromptGenerationDialog extends DialogWrapper {

    private static final String HEADER = "AutoSpring – Prompt Generation";
    private static final String SUBTITLE =
            "Describe your entity and backend requirements. AutoSpring will generate the code "
                    + "directly inside your Spring Boot project.";

    private static final String[] DATABASES = {"None", "MySQL", "PostgreSQL", "H2"};
    private static final String[] NAMESPACES = {"auto", "jakarta", "javax"};

    private static final String[] OPTION_IDS = {
            "dto", "mapper", "repository", "service", "controller", "pagination",
            "validation", "swagger", "exception-handling", "auditing", "security",
            "properties", "dependencies"
    };

    private static final String EXAMPLE_PROMPT = """
            Entity: Employee
            Package: com.example.demo.entity
            Fields:
                id: Long [id, generated]
                name: String [required, max=100]
                email: String [email, required]
                department: String
                salary: BigDecimal [min=0]
            Generate:
                dto, mapper, repository, service, controller, pagination, validation,
                swagger, exception-handling, auditing, security, properties, dependencies
            Database: PostgreSQL
            Security: JWT
            Persistence: jakarta
            """;

    private static final String SYNTAX_HELP = """
            AutoSpring prompt syntax (all lines are parsed locally):

              Entity: Employee                    (or "Generate CRUD for Employee.")
              Package: com.example.demo.entity    (optional)
              Table: employees                    (optional)
              Fields:
                  id: Long [id, generated]        (exactly one [id] is required)
                  name: String [required, max=100]
                  email: String [email]
              Generate:
                  dto, mapper, repository, service, controller, pagination,
                  validation, swagger, exception-handling, auditing, security,
                  properties, dependencies
              Database: None | MySQL | PostgreSQL | H2
              Security: None | JWT
              Persistence: auto | jakarta | javax

            Field flags: id, generated, required, unique, email, max=N, min=N, size=N.
            MongoDB and entity relationships are not supported in prompt mode.
            """;

    private final Project project;
    private final ProjectContextAnalyzer.Context context;

    private JBTextArea promptArea;
    private JBLabel validationLabel;
    private JBTextField entityNameField;
    private JBTextField basePackageField;
    private JBTextField entityPackageField;
    private JComboBox<String> databaseCombo;
    private JComboBox<String> namespaceCombo;
    private JCheckBox securityCheckBox;
    private JComboBox<PsiDirectory> sourceRootCombo;
    private PsiDirectory singleSourceRoot;
    private final Map<String, JCheckBox> optionCheckBoxes = new LinkedHashMap<>();

    private boolean settingsFilledFromPrompt;
    private GenerationPlan reviewedPlan;
    private PromptSpecification specification;

    public PromptGenerationDialog(Project project, ProjectContextAnalyzer.Context context) {
        super(project, true);
        this.project = project;
        this.context = context;
        setTitle(HEADER);
        setOKButtonText("Generate Now");
        buildComponents();
        resetSettings();
        init();
    }

    // ---------------------------------------------------------------------------------
    // UI construction
    // ---------------------------------------------------------------------------------

    private void buildComponents() {
        promptArea = new JBTextArea(12, 72);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);

        validationLabel = new JBLabel(" ");

        entityNameField = new JBTextField(22);
        basePackageField = new JBTextField(22);
        entityPackageField = new JBTextField(22);

        databaseCombo = new JComboBox<>(new DefaultComboBoxModel<>(DATABASES));
        namespaceCombo = new JComboBox<>(new DefaultComboBoxModel<>(NAMESPACES));

        securityCheckBox = new JCheckBox("Include JWT security");
        optionCheckBoxes.clear();
        GenerationOptions defaults = GenerationOptions.defaults();
        for (String id : OPTION_IDS) {
            optionCheckBoxes.put(id,
                    new JCheckBox(prettyOptionName(id), optionEnabled(defaults, id)));
        }
        // The setting checkbox and the security task checkbox mirror each other.
        JCheckBox securityTaskBox = optionCheckBoxes.get("security");
        securityCheckBox.addActionListener(
                e -> securityTaskBox.setSelected(securityCheckBox.isSelected()));
        securityTaskBox.addActionListener(
                e -> securityCheckBox.setSelected(securityTaskBox.isSelected()));
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 8, 6, 8);

        JBLabel header = new JBLabel(HEADER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        addFullWidth(root, gbc, header, 0, 0);

        JBLabel subtitle = new JBLabel("<html><body>" + escapeHtml(SUBTITLE) + "</body></html>");
        addFullWidth(root, gbc, subtitle, 1, 0);

        addFullWidth(root, gbc, new JBLabel("Prompt:"), 2, 0);
        JBScrollPane promptScroll = new JBScrollPane(promptArea);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        root.add(promptScroll, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        addFullWidth(root, gbc, createButtonPanel(), 4, 0);
        addFullWidth(root, gbc, validationLabel, 5, 0);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 0;
        root.add(createSettingsPanel(), gbc);
        return root;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JButton clear = new JButton("Clear Prompt");
        clear.addActionListener(e -> clearPrompt());
        panel.add(clear);

        JButton reset = new JButton("Reset Settings");
        reset.addActionListener(e -> resetSettings());
        panel.add(reset);

        JButton example = new JButton("Example Prompt");
        example.addActionListener(e -> insertExamplePrompt());
        panel.add(example);

        JButton syntax = new JButton("Supported Syntax");
        syntax.addActionListener(e -> Messages.showInfoMessage(
                project, SYNTAX_HELP, "AutoSpring – Supported Syntax"));
        panel.add(syntax);

        JButton review = new JButton("Review Generation Plan");
        review.addActionListener(e -> reviewPlan());
        panel.add(review);
        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Generation Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.weightx = 0.5;

        addSettingRow(panel, gbc, 0, new JBLabel("Entity name:"), entityNameField,
                new JBLabel("Base package:"), basePackageField);
        addSettingRow(panel, gbc, 1, new JBLabel("Entity package:"), entityPackageField,
                new JBLabel("Database:"), databaseCombo);

        List<PsiDirectory> roots = context == null ? List.of() : context.getSourceRoots();
        if (roots.size() > 1) {
            sourceRootCombo = new JComboBox<>(new DefaultComboBoxModel<>(roots.toArray(PsiDirectory[]::new)));
            sourceRootCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(
                        JList<?> list, Object value, int index, boolean selected, boolean focused
                ) {
                    super.getListCellRendererComponent(list, value, index, selected, focused);
                    if (value instanceof PsiDirectory directory && directory.getVirtualFile() != null) {
                        setText(directory.getVirtualFile().getPath());
                    }
                    return this;
                }
            });
            addSettingRow(panel, gbc, 2, new JBLabel("Source root:"), sourceRootCombo,
                    new JBLabel("Persistence namespace:"), namespaceCombo);
        } else {
            singleSourceRoot = roots.isEmpty() ? null : roots.get(0);
            addSettingRow(panel, gbc, 2, new JBLabel("Persistence namespace:"), namespaceCombo,
                    new JBLabel(""), new JPanel());
        }

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(securityCheckBox, gbc);

        gbc.gridy = 4;
        panel.add(new JBLabel("Components to generate:"), gbc);

        JPanel boxes = new JPanel(new GridBagLayout());
        GridBagConstraints boxGbc = new GridBagConstraints();
        boxGbc.anchor = GridBagConstraints.WEST;
        boxGbc.insets = new Insets(2, 6, 2, 6);
        int index = 0;
        for (String id : OPTION_IDS) {
            boxGbc.gridx = index % 3;
            boxGbc.gridy = index / 3;
            boxes.add(optionCheckBoxes.get(id), boxGbc);
            index++;
        }
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(boxes, gbc);
        return panel;
    }

    private static void addSettingRow(
            JPanel panel, GridBagConstraints gbc, int row,
            JComponent leftLabel, JComponent leftField,
            JComponent rightLabel, JComponent rightField
    ) {
        gbc.gridwidth = 1;
        gbc.gridy = row;
        gbc.gridx = 0;
        panel.add(leftLabel, gbc);
        gbc.gridx = 1;
        panel.add(leftField, gbc);
        gbc.gridx = 2;
        panel.add(rightLabel, gbc);
        gbc.gridx = 3;
        panel.add(rightField, gbc);
    }

    private static void addFullWidth(JPanel panel, GridBagConstraints gbc, JComponent comp, int row, int weightY) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = weightY;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(comp, gbc);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return promptArea;
    }

    // ---------------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------------

    private void clearPrompt() {
        promptArea.setText("");
        specification = null;
        reviewedPlan = null;
        settingsFilledFromPrompt = false;
        validationLabel.setText(" ");
    }

    private void resetSettings() {
        entityNameField.setText("");
        basePackageField.setText(context == null || context.getBasePackage() == null
                ? "" : context.getBasePackage());
        entityPackageField.setText(context == null || context.getEntityPackage() == null
                ? "" : context.getEntityPackage());
        databaseCombo.setSelectedItem("None");

        String detected = context == null ? null : context.getPersistenceNamespace();
        namespaceCombo.setSelectedItem("jakarta".equalsIgnoreCase(detected)
                || "javax".equalsIgnoreCase(detected) ? detected.toLowerCase(Locale.ROOT) : "auto");

        GenerationOptions defaults = GenerationOptions.defaults();
        for (String id : OPTION_IDS) {
            optionCheckBoxes.get(id).setSelected(optionEnabled(defaults, id));
        }
        securityCheckBox.setSelected(false);
        settingsFilledFromPrompt = false;
        reviewedPlan = null;
        specification = null;
        validationLabel.setText(" ");
    }

    private void insertExamplePrompt() {
        promptArea.setText(EXAMPLE_PROMPT);
        PromptValidationResult result = parse(true);
        if (!result.isValid()) {
            Messages.showErrorDialog(project, result.formatErrors(), "Invalid Prompt");
        }
    }

    private void reviewPlan() {
        PromptValidationResult result = parse();
        if (!result.isValid()) {
            Messages.showErrorDialog(project, result.formatErrors(), "Invalid Prompt");
            return;
        }
        PromptSpecification spec = result.getSpecification();
        GenerationOptions options = effectiveOptions(spec);
        GenerationPlan plan = GenerationPlan.from(options, getEntityName());
        PsiDirectory sourceRoot = getSourceRoot();

        List<ChangePreviewService.TargetFile> targets = ChangePreviewService.predictTargets(
                project, sourceRoot, previewMeta(spec), plan);
        GenerationPlanDialog planDialog = new GenerationPlanDialog(project, plan, targets);
        if (planDialog.showAndGet()) {
            reviewedPlan = planDialog.getPlan();
            specification = spec;
            close(OK_EXIT_CODE);
        }
    }

    @Override
    protected void doOKAction() {
        PromptValidationResult result = parse();
        if (!result.isValid()) {
            Messages.showErrorDialog(project, result.formatErrors(), "Invalid Prompt");
            return;
        }
        super.doOKAction();
    }

    // ---------------------------------------------------------------------------------
    // Parsing and settings
    // ---------------------------------------------------------------------------------

    /** Parses the current prompt, shows line-numbered errors and fills settings once. */
    public PromptValidationResult parse() {
        return parse(false);
    }

    private PromptValidationResult parse(boolean forceFill) {
        PromptValidationResult result = PromptParser.parse(getPromptText());
        if (!result.isValid()) {
            validationLabel.setText("<html><body><b>Prompt problems:</b><br>"
                    + escapeHtml(result.formatErrors()).replace("\n", "<br>") + "</body></html>");
            return result;
        }
        specification = result.getSpecification();
        String warnings = specification.getWarnings().isEmpty()
                ? "" : " (" + specification.getWarnings().size() + " warning(s))";
        validationLabel.setText("<html><body>Prompt is valid" + warnings + ".</body></html>");
        if (forceFill || !settingsFilledFromPrompt) {
            fillSettingsFromSpecification(specification);
            settingsFilledFromPrompt = true;
        }
        return result;
    }

    private void fillSettingsFromSpecification(PromptSpecification spec) {
        EntityDefinition definition = spec.getEntityDefinition();
        if (definition != null) {
            if (definition.getClassName() != null && !definition.getClassName().isBlank()) {
                entityNameField.setText(definition.getClassName());
            }
            String packageName = definition.getPackageName();
            if (packageName != null && !packageName.isBlank()) {
                entityPackageField.setText(packageName);
                String derived = deriveBasePackage(packageName);
                if (derived != null && !derived.isBlank()) {
                    basePackageField.setText(derived);
                }
            }
        }

        if (containsIgnoreCase(DATABASES, spec.getDatabaseType())) {
            databaseCombo.setSelectedItem(matchIgnoreCase(DATABASES, spec.getDatabaseType()));
        }
        String namespace = spec.getSourceNamespace();
        if ("jakarta".equalsIgnoreCase(namespace) || "javax".equalsIgnoreCase(namespace)) {
            namespaceCombo.setSelectedItem(namespace.toLowerCase(Locale.ROOT));
        }

        GenerationOptions options = spec.getGenerationOptions();
        for (String id : OPTION_IDS) {
            optionCheckBoxes.get(id).setSelected(optionEnabled(options, id));
        }
        boolean security = spec.isJwtSecurity() || options.isIncludeSecurity();
        securityCheckBox.setSelected(security);
        optionCheckBoxes.get("security").setSelected(security);
    }

    private GenerationOptions effectiveOptions(PromptSpecification spec) {
        GenerationOptions base = spec == null ? GenerationOptions.defaults() : spec.getGenerationOptions();
        GenerationOptions selected = getSelectedGenerationOptions();
        return base.toBuilder()
                .generateEntity(true)
                .generateDto(selected.isGenerateDto())
                .generateMapper(selected.isGenerateMapper())
                .generateRepository(selected.isGenerateRepository())
                .generateService(selected.isGenerateService())
                .generateController(selected.isGenerateController())
                .generatePagination(selected.isGeneratePagination())
                .generateValidation(selected.isGenerateValidation())
                .generateSwagger(selected.isGenerateSwagger())
                .generateExceptionHandling(selected.isGenerateExceptionHandling())
                .generateAuditing(selected.isGenerateAuditing())
                .includeSecurity(selected.isIncludeSecurity())
                .generateProperties(selected.isGenerateProperties())
                .injectDependencies(selected.isInjectDependencies())
                .build();
    }

    private ClassMeta previewMeta(PromptSpecification spec) {
        String entityName = getEntityName();
        String entityPackage = getEntityPackage();
        if ((entityPackage == null || entityPackage.isBlank()) && spec != null
                && spec.getEntityDefinition() != null) {
            entityPackage = spec.getEntityDefinition().getPackageName();
        }
        if (entityPackage == null || entityPackage.isBlank()) {
            String base = getBasePackage();
            entityPackage = (base == null || base.isBlank() ? "com.example.app" : base) + ".entity";
        }

        String idType = "Long";
        List<FieldMeta> fields = new ArrayList<>();
        if (spec != null && spec.getEntityDefinition() != null) {
            EntityDefinition definition = spec.getEntityDefinition();
            if (definition.getIdField() != null) {
                idType = definition.getIdField().getJavaType();
            }
            for (PromptFieldDefinition field : definition.getFields()) {
                fields.add(new FieldMeta(field.getName(), field.getJavaType()));
            }
        }
        return new ClassMeta(entityName, entityPackage, getBasePackage(), idType, fields, List.of());
    }

    // ---------------------------------------------------------------------------------
    // Public getters used by GenerateFromPromptAction
    // ---------------------------------------------------------------------------------

    public String getPromptText() {
        return promptArea.getText();
    }

    public String getEntityName() {
        return entityNameField.getText().trim();
    }

    public String getBasePackage() {
        return basePackageField.getText().trim();
    }

    public String getEntityPackage() {
        return entityPackageField.getText().trim();
    }

    public String getDatabaseType() {
        Object selected = databaseCombo.getSelectedItem();
        return selected == null ? "None" : selected.toString();
    }

    public String getPersistenceNamespace() {
        Object selected = namespaceCombo.getSelectedItem();
        return selected == null ? "auto" : selected.toString().toLowerCase(Locale.ROOT);
    }

    public boolean isSecurityEnabled() {
        return securityCheckBox.isSelected();
    }

    public GenerationOptions getSelectedGenerationOptions() {
        return GenerationOptions.builder()
                .generateEntity(true)
                .generateDto(isSelected("dto"))
                .generateMapper(isSelected("mapper"))
                .generateRepository(isSelected("repository"))
                .generateService(isSelected("service"))
                .generateController(isSelected("controller"))
                .generatePagination(isSelected("pagination"))
                .generateValidation(isSelected("validation"))
                .generateSwagger(isSelected("swagger"))
                .generateExceptionHandling(isSelected("exception-handling"))
                .generateAuditing(isSelected("auditing"))
                .includeSecurity(isSecurityEnabled())
                .generateProperties(isSelected("properties"))
                .injectDependencies(isSelected("dependencies"))
                .build();
    }

    public PsiDirectory getSourceRoot() {
        if (sourceRootCombo != null) return (PsiDirectory) sourceRootCombo.getSelectedItem();
        return singleSourceRoot;
    }

    public GenerationPlan getReviewedPlan() {
        return reviewedPlan;
    }

    public PromptSpecification getSpecification() {
        return specification;
    }

    // ---------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------

    private boolean isSelected(String id) {
        JCheckBox box = optionCheckBoxes.get(id);
        return box != null && box.isSelected();
    }

    private static boolean optionEnabled(GenerationOptions options, String id) {
        return switch (id) {
            case "dto" -> options.isGenerateDto();
            case "mapper" -> options.isGenerateMapper();
            case "repository" -> options.isGenerateRepository();
            case "service" -> options.isGenerateService();
            case "controller" -> options.isGenerateController();
            case "pagination" -> options.isGeneratePagination();
            case "validation" -> options.isGenerateValidation();
            case "swagger" -> options.isGenerateSwagger();
            case "exception-handling" -> options.isGenerateExceptionHandling();
            case "auditing" -> options.isGenerateAuditing();
            case "security" -> options.isIncludeSecurity();
            case "properties" -> options.isGenerateProperties();
            case "dependencies" -> options.isInjectDependencies();
            default -> false;
        };
    }

    private static String prettyOptionName(String id) {
        return switch (id) {
            case "dto" -> "DTO";
            case "exception-handling" -> "Exception handling";
            default -> Character.toUpperCase(id.charAt(0)) + id.substring(1);
        };
    }

    private static String deriveBasePackage(String packageName) {
        if (packageName.endsWith(".entity")) {
            return packageName.substring(0, packageName.length() - ".entity".length());
        }
        if (packageName.endsWith(".entities")) {
            return packageName.substring(0, packageName.length() - ".entities".length());
        }
        return packageName;
    }

    private static boolean containsIgnoreCase(String[] values, String candidate) {
        return matchIgnoreCase(values, candidate) != null;
    }

    private static String matchIgnoreCase(String[] values, String candidate) {
        if (candidate == null) return null;
        for (String value : values) {
            if (value.equalsIgnoreCase(candidate.trim())) return value;
        }
        return null;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
