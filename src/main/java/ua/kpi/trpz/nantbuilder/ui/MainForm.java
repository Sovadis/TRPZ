package ua.kpi.trpz.nantbuilder.ui;

import ua.kpi.trpz.nantbuilder.db.DataAccessException;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.service.ScriptService;
import ua.kpi.trpz.nantbuilder.service.SolutionImportService;
import ua.kpi.trpz.nantbuilder.service.ValidationException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Головна форма: список скриптів збірки з бази даних і основні дії над ними.
 */
public class MainForm extends JFrame {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String[] COLUMNS = {"Назва", "Опис", "Ціль за замовчуванням", "Змінено"};

    private final ScriptService scriptService;
    private final SolutionImportService importService;

    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JButton openButton = new JButton("Відкрити");
    private final JButton deleteButton = new JButton("Видалити");
    private final JLabel statusLabel = new JLabel(" ");
    /** Скрипти в тому самому порядку, що й рядки таблиці. */
    private List<BuildScript> scripts = new ArrayList<>();

    /** Створює головну форму і завантажує список скриптів з бази даних. */
    public MainForm(ScriptService scriptService, SolutionImportService importService) {
        super("NAnt Script Builder");
        this.scriptService = scriptService;
        this.importService = importService;

        add(createToolBar(), BorderLayout.NORTH);
        add(createTablePane(), BorderLayout.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        add(statusLabel, BorderLayout.SOUTH);

        // після повернення з редактора список оновлюється (могли змінитися назва або дата)
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                refreshScripts();
            }
        });

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        refreshScripts();
    }

    /** Перечитує список скриптів з бази даних і оновлює таблицю (виділення зберігається). */
    public void refreshScripts() {
        Long selectedId = selectedScript().map(BuildScript::getId).orElse(null);
        try {
            scripts = scriptService.listScripts();
        } catch (DataAccessException e) {
            // не діалог, бо оновлення викликається і при активації вікна
            statusLabel.setText("Помилка читання бази даних: " + e.getMessage());
            return;
        }
        tableModel.setRowCount(0);
        for (BuildScript script : scripts) {
            tableModel.addRow(new Object[]{
                    script.getName(),
                    nullToEmpty(script.getDescription()),
                    nullToEmpty(script.getDefaultTarget()),
                    script.getUpdatedAt() == null ? "" : DATE_FORMAT.format(script.getUpdatedAt())
            });
        }
        selectScript(selectedId);
        statusLabel.setText(scripts.isEmpty()
                ? "У базі даних ще немає скриптів. Створіть новий скрипт або імпортуйте рішення .sln."
                : "Скриптів у базі даних: " + scripts.size() + ". Подвійне клацання відкриває редактор.");
    }

    /** Панель інструментів із кнопками основних дій. */
    private JPanel createToolBar() {
        JButton newButton = new JButton("Новий скрипт");
        newButton.addActionListener(e -> createScript());
        openButton.addActionListener(e -> openSelectedScript());
        JButton importButton = new JButton("Імпорт .sln");
        importButton.addActionListener(e -> importSolution());
        deleteButton.addActionListener(e -> deleteSelectedScript());

        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        toolBar.add(newButton);
        toolBar.add(openButton);
        toolBar.add(importButton);
        toolBar.add(deleteButton);
        return toolBar;
    }

    private JScrollPane createTablePane() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(430);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    openSelectedScript();
                }
            }
        });
        return new JScrollPane(table);
    }

    /** Сценарій «Створити скрипт збірки». */
    private void createScript() {
        new ScriptDialog(this, scriptService).showDialog().ifPresent(created -> {
            refreshScripts();
            selectScript(created.getId());
        });
    }

    /** Сценарій «Імпортувати рішення .sln». */
    private void importSolution() {
        JFileChooser chooser = new JFileChooser(startDirectory());
        chooser.setDialogTitle("Імпорт рішення Visual Studio");
        chooser.setFileFilter(new FileNameExtensionFilter("Рішення Visual Studio (*.sln)", "sln"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path slnFile = chooser.getSelectedFile().toPath();
        try {
            BuildScript imported = importService.importSolution(slnFile);
            refreshScripts();
            selectScript(imported.getId());
            openEditor(imported.getId());
        } catch (IOException e) {
            showError("Не вдалося прочитати рішення " + slnFile.getFileName() + ":\n" + e.getMessage());
        } catch (ValidationException | DataAccessException e) {
            showError(e.getMessage());
        }
    }

    private void openSelectedScript() {
        selectedScript().ifPresent(script -> openEditor(script.getId()));
    }

    private void openEditor(long scriptId) {
        try {
            BuildScript script = scriptService.loadScript(scriptId);
            ScriptEditorForm editor = new ScriptEditorForm(scriptService, script);
            editor.setLocationRelativeTo(this);
            editor.setVisible(true);
        } catch (ValidationException | DataAccessException e) {
            showError(e.getMessage());
        }
    }

    private void deleteSelectedScript() {
        Optional<BuildScript> selected = selectedScript();
        if (selected.isEmpty()) {
            return;
        }
        BuildScript script = selected.get();
        Object[] options = {"Так", "Ні"};
        int answer = JOptionPane.showOptionDialog(this,
                "Видалити скрипт «" + script.getName() + "» з бази даних?", "Видалення скрипта",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        if (answer != 0) {
            return;
        }
        try {
            scriptService.deleteScript(script.getId());
        } catch (DataAccessException e) {
            showError(e.getMessage());
        }
        refreshScripts();
    }

    private Optional<BuildScript> selectedScript() {
        int row = table.getSelectedRow();
        return row >= 0 && row < scripts.size() ? Optional.of(scripts.get(row)) : Optional.empty();
    }

    private void selectScript(Long id) {
        for (int row = 0; row < scripts.size(); row++) {
            if (scripts.get(row).getId().equals(id)) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                break;
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean selected = table.getSelectedRow() >= 0;
        openButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
    }

    /** Діалог вибору файлу відкривається в каталозі samples, якщо програму запущено з каталогу проєкту. */
    private static File startDirectory() {
        Path samples = Path.of("samples").toAbsolutePath();
        return Files.isDirectory(samples) ? samples.toFile() : new File(System.getProperty("user.home"));
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Помилка", JOptionPane.ERROR_MESSAGE);
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
