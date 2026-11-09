package ua.kpi.trpz.nantbuilder.ui;

import ua.kpi.trpz.nantbuilder.service.validation.Severity;
import ua.kpi.trpz.nantbuilder.service.validation.ValidationIssue;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Діалог «Перевірка скрипта»: таблиця всіх помилок і попереджень, знайдених ланцюжком перевірок,
 * та підсумок їх кількості.
 */
public class ValidationReportDialog extends JDialog {

    private static final Color ERROR_COLOR = new Color(0xC6, 0x28, 0x28);
    private static final Color WARNING_COLOR = new Color(0xB2, 0x6A, 0x00);
    private static final Color OK_COLOR = new Color(0x2E, 0x7D, 0x32);
    private static final String[] COLUMNS = {"Рівень", "Елемент", "Повідомлення"};
    private static final int MIN_VISIBLE_ROWS = 4;
    private static final int MAX_VISIBLE_ROWS = 12;

    /** Створює модальний діалог зі списком проблем у порядку обробників ланцюжка. */
    public ValidationReportDialog(Window owner, List<ValidationIssue> issues) {
        super(owner, "Перевірка скрипта", ModalityType.APPLICATION_MODAL);
        long errors = issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();

        JLabel summary = new JLabel(summaryOf(issues));
        summary.setName("summaryLabel");
        summary.setFont(summary.getFont().deriveFont(Font.BOLD));
        summary.setForeground(issues.isEmpty() ? OK_COLOR : errors > 0 ? ERROR_COLOR : WARNING_COLOR);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        content.add(summary, BorderLayout.NORTH);
        content.add(new JScrollPane(createTable(issues)), BorderLayout.CENTER);
        content.add(createFooter(errors > 0, issues.isEmpty()), BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Підсумок перевірки: «Помилок: N, попереджень: M» або «Помилок не знайдено». */
    static String summaryOf(List<ValidationIssue> issues) {
        if (issues.isEmpty()) {
            return "Помилок не знайдено";
        }
        long errors = issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        return "Помилок: " + errors + ", попереджень: " + (issues.size() - errors);
    }

    private static JTable createTable(List<ValidationIssue> issues) {
        JTable table = new JTable(new IssueTableModel(issues));
        table.setName("issueTable");
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new IssueCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(115);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setPreferredWidth(560);
        // висота таблиці — за кількістю проблем (плюс один вільний рядок), далі прокрутка
        int visibleRows = Math.max(MIN_VISIBLE_ROWS, Math.min(issues.size() + 1, MAX_VISIBLE_ROWS));
        table.setPreferredScrollableViewportSize(new Dimension(865, table.getRowHeight() * visibleRows));
        return table;
    }

    private JPanel createFooter(boolean hasErrors, boolean noIssues) {
        JLabel hint = new JLabel(hasErrors ? "Скрипт із помилками не можна зберегти та експортувати."
                : noIssues ? "Скрипт можна зберегти та експортувати."
                : "Попередження не заважають збереженню та експорту.");
        hint.setForeground(Color.GRAY);

        JButton closeButton = new JButton("Закрити");
        closeButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(closeButton);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.add(hint, BorderLayout.CENTER);
        footer.add(closeButton, BorderLayout.EAST);
        return footer;
    }

    /** Рівень проблеми українською. */
    private static String label(Severity severity) {
        return severity == Severity.ERROR ? "Помилка" : "Попередження";
    }

    /** Модель таблиці (лише для читання): рівень, елемент і повідомлення кожної проблеми. */
    private static class IssueTableModel extends AbstractTableModel {
        private final List<ValidationIssue> issues;

        IssueTableModel(List<ValidationIssue> issues) {
            this.issues = List.copyOf(issues);
        }

        @Override
        public int getRowCount() {
            return issues.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            ValidationIssue issue = issues.get(row);
            return switch (column) {
                case 0 -> issue.severity();
                case 1 -> issue.element();
                default -> issue.message();
            };
        }
    }

    /** Помилки — червоним, попередження — помаранчевим; повний текст клітинки — у підказці. */
    private static class IssueCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean hasFocus, int row, int column) {
            Object text = value instanceof Severity severity ? label(severity) : value;
            super.getTableCellRendererComponent(table, text, selected, hasFocus, row, column);
            setToolTipText(text == null ? null : text.toString());
            if (value instanceof Severity severity && !selected) {
                setForeground(severity == Severity.ERROR ? ERROR_COLOR : WARNING_COLOR);
            } else if (!selected) {
                setForeground(table.getForeground());
            }
            return this;
        }
    }
}
