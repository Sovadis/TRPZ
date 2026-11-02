package ua.kpi.trpz.nantbuilder.ui;

import ua.kpi.trpz.nantbuilder.db.DataAccessException;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.service.ScriptService;
import ua.kpi.trpz.nantbuilder.service.ValidationException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.Optional;

/**
 * Діалог створення нового скрипта збірки (сценарій «Створити скрипт збірки»).
 * Помилки перевірки показуються в самому діалозі, і він залишається відкритим.
 */
public class ScriptDialog extends JDialog {

    private final ScriptService scriptService;
    private final JTextField nameField = new JTextField(30);
    private final JTextField descriptionField = new JTextField(30);
    private final JTextField baseDirField = new JTextField(".", 30);
    private final JLabel errorLabel = new JLabel(" ");
    private BuildScript createdScript;

    /** Створює модальний діалог над головною формою. */
    public ScriptDialog(Frame owner, ScriptService scriptService) {
        super(owner, "Новий скрипт збірки", true);
        this.scriptService = scriptService;
        nameField.setName("nameField");
        descriptionField.setName("descriptionField");
        baseDirField.setName("baseDirField");

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        content.add(createFieldsPanel(), BorderLayout.CENTER);
        content.add(createButtonsPanel(), BorderLayout.SOUTH);
        setContentPane(content);

        // Esc закриває діалог без створення скрипта
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        pack();
    }

    /**
     * Показує діалог і чекає, доки користувач його закриє.
     *
     * @return створений і збережений скрипт або порожнє значення, якщо користувач скасував дію
     */
    public Optional<BuildScript> showDialog() {
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return Optional.ofNullable(createdScript);
    }

    private JPanel createFieldsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        addRow(panel, 0, "Назва", nameField);
        addRow(panel, 1, "Опис", descriptionField);
        addRow(panel, 2, "Базова директорія", baseDirField);

        JLabel hint = new JLabel("До скрипта буде додано порожню ціль «build» за замовчуванням.");
        hint.setForeground(Color.GRAY);
        errorLabel.setForeground(new Color(0xC6, 0x28, 0x28));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, 0, 0, 0);
        c.gridy = 3;
        panel.add(hint, c);
        c.gridy = 4;
        panel.add(errorLabel, c);
        return panel;
    }

    private static void addRow(JPanel panel, int row, String label, JTextField field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.insets = new Insets(4, 0, 4, 10);
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.insets = new Insets(4, 0, 4, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(field, c);
    }

    private JPanel createButtonsPanel() {
        JButton createButton = new JButton("Створити");
        createButton.addActionListener(e -> createScript());
        JButton cancelButton = new JButton("Скасувати");
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(createButton);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.add(createButton);
        panel.add(cancelButton);
        return panel;
    }

    private void createScript() {
        try {
            createdScript = scriptService.createScript(
                    nameField.getText(), descriptionField.getText(), baseDirField.getText());
            dispose();
        } catch (ValidationException | DataAccessException e) {
            errorLabel.setText(e.getMessage());
            pack();
            nameField.requestFocusInWindow();
            nameField.selectAll();
        }
    }
}
