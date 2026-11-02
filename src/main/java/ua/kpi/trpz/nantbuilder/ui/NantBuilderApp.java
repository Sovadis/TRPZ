package ua.kpi.trpz.nantbuilder.ui;

import com.formdev.flatlaf.FlatLightLaf;
import ua.kpi.trpz.nantbuilder.db.DataAccessException;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcBuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcPropertyRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTargetRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTaskRepository;
import ua.kpi.trpz.nantbuilder.service.ScriptGenerator;
import ua.kpi.trpz.nantbuilder.service.ScriptService;
import ua.kpi.trpz.nantbuilder.service.SolutionImportService;
import ua.kpi.trpz.nantbuilder.service.SolutionParser;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

/**
 * Точка входу програми: налаштовує FlatLaf, відкриває базу даних
 * {@code %USERPROFILE%/.nant-script-builder/nantbuilder.db}, з'єднує репозиторії, сервіси та головну форму.
 */
public class NantBuilderApp {

    /** Запускає програму NAnt Script Builder. */
    public static void main(String[] args) {
        FlatLightLaf.setup();
        installUkrainianTexts();

        Path dbFile = Path.of(System.getProperty("user.home"), ".nant-script-builder", "nantbuilder.db");
        Database db;
        try {
            db = Database.openFile(dbFile);
            db.initSchema();
        } catch (DataAccessException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Помилка бази даних", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(db::close));

        ScriptService scriptService = new ScriptService(db,
                new JdbcBuildScriptRepository(db), new JdbcPropertyRepository(db),
                new JdbcTargetRepository(db), new JdbcTaskRepository(db));
        SolutionImportService importService =
                new SolutionImportService(new SolutionParser(), new ScriptGenerator(), scriptService);

        SwingUtilities.invokeLater(() -> new MainForm(scriptService, importService).setVisible(true));
    }

    /** Українські написи стандартних діалогів Swing (кнопки повідомлень і вікна вибору файлу). */
    private static void installUkrainianTexts() {
        UIManager.put("OptionPane.okButtonText", "Гаразд");
        UIManager.put("OptionPane.cancelButtonText", "Скасувати");
        UIManager.put("OptionPane.yesButtonText", "Так");
        UIManager.put("OptionPane.noButtonText", "Ні");
        UIManager.put("FileChooser.openButtonText", "Відкрити");
        UIManager.put("FileChooser.saveButtonText", "Зберегти");
        UIManager.put("FileChooser.cancelButtonText", "Скасувати");
        UIManager.put("FileChooser.openDialogTitleText", "Відкрити");
        UIManager.put("FileChooser.saveDialogTitleText", "Зберегти");
        UIManager.put("FileChooser.lookInLabelText", "Шукати в:");
        UIManager.put("FileChooser.saveInLabelText", "Зберегти в:");
        UIManager.put("FileChooser.fileNameLabelText", "Ім'я файлу:");
        UIManager.put("FileChooser.filesOfTypeLabelText", "Тип файлів:");
        UIManager.put("FileChooser.acceptAllFileFilterText", "Усі файли");
        UIManager.put("FileChooser.upFolderToolTipText", "Вгору");
        UIManager.put("FileChooser.homeFolderToolTipText", "Домашній каталог");
        UIManager.put("FileChooser.newFolderToolTipText", "Створити папку");
        UIManager.put("FileChooser.listViewButtonToolTipText", "Список");
        UIManager.put("FileChooser.detailsViewButtonToolTipText", "Таблиця");
        UIManager.put("FileChooser.fileNameHeaderText", "Ім'я");
        UIManager.put("FileChooser.fileSizeHeaderText", "Розмір");
        UIManager.put("FileChooser.fileDateHeaderText", "Змінено");
    }
}
