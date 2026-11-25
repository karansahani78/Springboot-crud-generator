package com.karan.intellijplatformplugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.karan.intellijplatformplugin.model.GenerationPlan;
import com.karan.intellijplatformplugin.model.GenerationTask;
import com.karan.intellijplatformplugin.service.ChangePreviewService;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * Review editor for an editable {@link GenerationPlan}.
 *
 * <p>Every generation task can be toggled, removed, re-added or reordered. The
 * dependency-aware validation is refreshed after each edit and is re-checked
 * before the dialog can be accepted.
 */
public class GenerationPlanDialog extends DialogWrapper {

    private static final String[] COLUMN_NAMES = {
            "Enabled", "Task", "Description", "Order", "Dependencies", "Status"
    };

    private static final List<String> SUPPORTED_IDS = List.of(
            "entity", "dto", "mapper", "repository", "service", "controller",
            "pagination", "validation", "swagger", "exception-handling", "auditing",
            "security", "properties", "dependencies"
    );

    private final Project project;
    private final GenerationPlan plan;
    private final List<ChangePreviewService.TargetFile> targets;
    private final List<GenerationTask> displayed = new ArrayList<>();

    private DefaultTableModel tableModel;
    private JTable table;
    private JBLabel statusLabel;

    public GenerationPlanDialog(
            Project project, GenerationPlan plan, List<ChangePreviewService.TargetFile> targets
    ) {
        super(project, true);
        this.project = project;
        this.plan = plan;
        this.targets = targets == null ? List.of() : targets;
        setTitle("AutoSpring – Review Generation Plan");
        setOKButtonText("Generate Now");
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        tableModel = new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                super.setValueAt(value, row, column);
                if (column == 0 && row >= 0 && row < displayed.size()) {
                    displayed.get(row).setEnabled(Boolean.TRUE.equals(value));
                    updateStatuses();
                }
            }
        };

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setPreferredScrollableViewportSize(new Dimension(920, 340));
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        table.getColumnModel().getColumn(3).setMaxWidth(70);
        table.getColumnModel().getColumn(5).setMaxWidth(180);

        refreshModel();

        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        content.add(new JBScrollPane(table), gbc);

        statusLabel = new JBLabel(" ");
        gbc.gridy = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        content.add(statusLabel, gbc);
        updateStatuses();

        JPanel root = new JPanel(new BorderLayout());
        root.add(content, BorderLayout.CENTER);
        root.add(createButtonPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));

        JButton enable = new JButton("Enable");
        enable.addActionListener(e -> setSelectedEnabled(true));
        panel.add(enable);

        JButton disable = new JButton("Disable");
        disable.addActionListener(e -> setSelectedEnabled(false));
        panel.add(disable);

        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> removeSelected());
        panel.add(remove);

        JButton add = new JButton("Add");
        add.addActionListener(e -> addTask());
        panel.add(add);

        JButton up = new JButton("Move Up");
        up.addActionListener(e -> moveSelected(-1));
        panel.add(up);

        JButton down = new JButton("Move Down");
        down.addActionListener(e -> moveSelected(1));
        panel.add(down);

        JButton reset = new JButton("Reset");
        reset.addActionListener(e -> {
            plan.reset();
            refreshModel();
        });
        panel.add(reset);

        JButton preview = new JButton("Preview Files");
        preview.addActionListener(e -> Messages.showInfoMessage(
                project, ChangePreviewService.formatPreview(targets), "AutoSpring – Predicted Files"));
        panel.add(preview);
        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return table;
    }

    @Override
    protected void doOKAction() {
        List<String> problems = plan.validate();
        if (!problems.isEmpty()) {
            Messages.showErrorDialog(project, String.join("\n", problems), "Invalid Generation Plan");
            updateStatuses();
            return;
        }
        super.doOKAction();
    }

    /** Returns the (possibly edited) plan. */
    public GenerationPlan getPlan() {
        return plan;
    }

    // ---------------------------------------------------------------------------------
    // Editing helpers
    // ---------------------------------------------------------------------------------

    private void refreshModel() {
        displayed.clear();
        displayed.addAll(plan.orderedTasks());
        tableModel.setRowCount(0);
        for (GenerationTask task : displayed) {
            tableModel.addRow(new Object[]{
                    task.isEnabled(),
                    task.getDisplayName(),
                    task.getDescription(),
                    task.getExecutionOrder(),
                    String.join(", ", task.getDependencies()),
                    task.getValidationStatus().name()
            });
        }
        updateStatuses();
    }

    private void updateStatuses() {
        List<String> problems = plan.validate();
        for (int i = 0; i < displayed.size(); i++) {
            String status = displayed.get(i).getValidationStatus().name();
            if (!status.equals(tableModel.getValueAt(i, 5))) {
                tableModel.setValueAt(status, i, 5);
            }
        }
        if (statusLabel != null) {
            statusLabel.setText(problems.isEmpty()
                    ? "Plan is valid. " + targets.size() + " output file(s) predicted."
                    : "Plan problems: " + String.join(" | ", problems));
        }
    }

    private GenerationTask selectedTask() {
        if (table == null) return null;
        int row = table.getSelectedRow();
        if (row < 0 || row >= displayed.size()) return null;
        return displayed.get(row);
    }

    private void setSelectedEnabled(boolean enabled) {
        GenerationTask task = selectedTask();
        if (task == null) return;
        task.setEnabled(enabled);
        refreshModel();
        selectTask(task.getId());
    }

    private void removeSelected() {
        GenerationTask task = selectedTask();
        if (task == null) return;
        if (!task.isRemovable()) {
            Messages.showInfoMessage(project,
                    "The task '" + task.getId() + "' is structural and cannot be removed.",
                    "Task Cannot Be Removed");
            return;
        }
        plan.remove(task.getId());
        refreshModel();
    }

    private void addTask() {
        List<String> missing = new ArrayList<>();
        for (String id : SUPPORTED_IDS) {
            if (plan.find(id) == null) missing.add(id);
        }
        if (missing.isEmpty()) {
            Messages.showInfoMessage(project,
                    "All supported tasks are already present in this plan.", "Add Task");
            return;
        }
        String[] options = missing.toArray(new String[0]);
        int choice = Messages.showChooseDialog(project,
                "Select a supported task to add:", "Add Generation Task",
                Messages.getQuestionIcon(), options, options[0]);
        if (choice < 0 || choice >= options.length) return;
        GenerationTask task = GenerationPlan.supportedTask(options[choice], plan.getEntityName());
        if (task != null && plan.add(task)) {
            refreshModel();
            selectTask(task.getId());
        }
    }

    private void moveSelected(int direction) {
        GenerationTask task = selectedTask();
        if (task == null) return;
        if (!plan.reorder(task.getId(), direction)) {
            Messages.showInfoMessage(project,
                    "Cannot move '" + task.getId() + "' in that direction: "
                            + "dependency order must be preserved.", "Invalid Move");
            return;
        }
        refreshModel();
        selectTask(task.getId());
    }

    private void selectTask(String id) {
        for (int i = 0; i < displayed.size(); i++) {
            if (displayed.get(i).getId().equals(id)) {
                table.setRowSelectionInterval(i, i);
                return;
            }
        }
    }
}
