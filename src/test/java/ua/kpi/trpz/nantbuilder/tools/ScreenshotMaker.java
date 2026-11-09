package ua.kpi.trpz.nantbuilder.tools;

import com.formdev.flatlaf.FlatLightLaf;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldManager;
import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;
import ua.kpi.trpz.nantbuilder.repository.cache.CacheStatistics;
import ua.kpi.trpz.nantbuilder.repository.cache.CachingBuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcBuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcPropertyRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTargetRepository;
import ua.kpi.trpz.nantbuilder.repository.jdbc.JdbcTaskRepository;
import ua.kpi.trpz.nantbuilder.service.ScriptGenerator;
import ua.kpi.trpz.nantbuilder.service.ScriptService;
import ua.kpi.trpz.nantbuilder.service.SolutionImportService;
import ua.kpi.trpz.nantbuilder.service.SolutionParser;
import ua.kpi.trpz.nantbuilder.ui.CacheStatisticsLabel;
import ua.kpi.trpz.nantbuilder.ui.MainForm;
import ua.kpi.trpz.nantbuilder.ui.ScriptDialog;
import ua.kpi.trpz.nantbuilder.ui.ScriptEditorForm;
import ua.kpi.trpz.nantbuilder.ui.ValidationReportDialog;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.RepaintManager;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Генерує знімки екрана форм для звіту (запуск: {@code gradle screenshots}).
 * Створює свіжу базу даних у {@code build/screenshot-db}, заповнює її через сервіси і
 * записує PNG-файли в {@code docs/screenshots}. Це допоміжний інструмент, а не частина програми.
 */
public final class ScreenshotMaker {

    /**
     * Масштаб інтерфейсу і зображення. Swing вимірює і малює текст в одному масштабі,
     * тому літери не обрізаються, а результат однаковий на будь-якому моніторі.
     */
    private static final int SCALE = 2;

    /** Скрипт WebApi після невдалого редагування XML: цикл, невідомі цілі, порожні значення. */
    private static final String BROKEN_WEB_API_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <project name="WebApi" default="build" basedir="src/WebApi">
              <description>REST API інтернет-магазину</description>
              <property name="configuration" value="Release" />
              <property name="api.version" value="" />
              <target name="clean" description="Очистити каталог збірки">
                <delete dir="bin" />
              </target>
              <target name="compile" depends="clean,test">
                <msbuild project="WebApi.csproj" />
              </target>
              <target name="test" depends="compile">
                <exec program="dotnet" commandline="test ../WebApi.Tests" />
              </target>
              <target name="publish" depends="test,deploy">
                <exec program="dotnet" commandline="publish -c ${configuration}" />
                <echo message="" />
              </target>
            </project>
            """;

    private ScreenshotMaker() {
    }

    /** Точка входу; перший аргумент — каталог проєкту. */
    public static void main(String[] args) throws Exception {
        // властивості читаються під час ініціалізації AWT, тому задаються першими
        System.setProperty("sun.java2d.uiScale", String.valueOf(SCALE));
        System.setProperty("awt.useSystemAAFontSettings", "on");

        Path projectDir = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path dbFile = projectDir.resolve("build/screenshot-db/nantbuilder.db");
        Path outDir = projectDir.resolve("docs/screenshots");
        Files.deleteIfExists(dbFile);
        Files.createDirectories(outDir);

        onEdt(() -> {
            FlatLightLaf.setup();
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
            return null;
        });

        try (Database db = Database.openFile(dbFile)) {
            db.initSchema();
            // з'єднання таке саме, як у NantBuilderApp: заголовки скриптів читаються через кешуючий замісник
            CacheStatistics cacheStatistics = new CacheStatistics();
            ScriptService scriptService = new ScriptService(db,
                    new CachingBuildScriptRepository(new JdbcBuildScriptRepository(db), cacheStatistics),
                    new JdbcPropertyRepository(db), new JdbcTargetRepository(db), new JdbcTaskRepository(db));
            SolutionImportService importService =
                    new SolutionImportService(new SolutionParser(), new ScriptGenerator(), scriptService);

            createMyLibrary(scriptService);
            BuildScript demoShop = importService.importSolution(projectDir.resolve("samples/DemoShop/DemoShop.sln"));

            // 1. Головна форма зі списком скриптів (вибрано DemoShop)
            MainForm mainForm = onEdt(() -> {
                MainForm form = new MainForm(scriptService, importService);
                form.setLocation(40, 40);
                form.setVisible(true);
                return form;
            });
            settle(800);
            onEdt(() -> {
                JTable table = find(mainForm, JTable.class, null);
                table.setRowSelectionInterval(0, 0);
                table.requestFocusInWindow();
                return null;
            });
            capture(mainForm, outDir.resolve("01-main-form.png"));

            // 2. Заповнений діалог створення скрипта
            ScriptDialog dialog = onEdt(() -> {
                ScriptDialog d = new ScriptDialog(mainForm, scriptService);
                fill(d, "nameField", "WebApi");
                fill(d, "descriptionField", "REST API інтернет-магазину");
                fill(d, "baseDirField", "src/WebApi");
                d.setModalityType(Dialog.ModalityType.MODELESS);
                d.setLocationRelativeTo(mainForm);
                d.setVisible(true);
                return d;
            });
            settle(600);
            capture(dialog, outDir.resolve("02-script-dialog.png"));
            onEdt(() -> {
                dialog.dispose();
                return null;
            });

            // 3. Редактор: дерево імпортованого рішення, вибрано завдання exec
            ScriptEditorForm editor = onEdt(() -> {
                ScriptEditorForm form = new ScriptEditorForm(scriptService, scriptService.loadScript(demoShop.getId()));
                form.setLocation(60, 60);
                form.setVisible(true);
                return form;
            });
            settle(800);
            onEdt(() -> {
                selectTask(find(editor, JTree.class, null), "build-DemoShop.Web");
                return null;
            });
            capture(editor, outDir.resolve("03-editor-tree.png"));

            // 4. Вкладка XML: підсвічування, згорнута ціль clean і підказки автодоповнення після '<'
            onEdt(() -> {
                find(editor, JTabbedPane.class, null).setSelectedIndex(1);
                RSyntaxTextArea xmlArea = find(editor, RSyntaxTextArea.class, null);
                collapseFoldStartingWith(xmlArea, "<target name=\"clean\"");
                String text = xmlArea.getText();
                int lineEnd = text.indexOf('\n', text.indexOf("DemoShop.Web\\DemoShop.Web.csproj"));
                xmlArea.requestFocusInWindow();
                xmlArea.setCaretPosition(lineEnd);
                xmlArea.replaceSelection("\n    ");
                // автоактивація спрацьовує лише на один введений символ, як під час набору
                xmlArea.replaceSelection("<");
                return null;
            });
            settle(1500);   // спливаюче вікно автодоповнення відкривається із затримкою
            capture(editor, outDir.resolve("04-editor-xml.png"));

            // 5. Головна форма з індикатором кешу (шаблон Proxy) у рядку стану після кількох оновлень списку
            onEdt(() -> {
                editor.dispose();
                mainForm.addStatusWidget(new CacheStatisticsLabel(cacheStatistics));
                // користувач по черзі відкриває скрипти в редакторі й повертається до списку
                for (BuildScript script : scriptService.listScripts()) {
                    scriptService.loadScript(script.getId());
                    mainForm.refreshScripts();
                }
                find(mainForm, JTable.class, null).setRowSelectionInterval(0, 0);
                mainForm.toFront();
                return null;
            });
            capture(mainForm, outDir.resolve("05-main-form-cache.png"));

            // 6. Звіт «Перевірка скрипта» над редактором (ЛР №5, ланцюжок перевірок)
            captureValidationReport(scriptService, outDir.resolve("06-validation-dialog.png"));

            onEdt(() -> {
                mainForm.dispose();
                return null;
            });
        }
        System.out.println("Screenshots written to " + outDir);
        System.exit(0);
    }

    /** Створює скрипт MyLibrary через сервіс так само, як це робить користувач. */
    private static void createMyLibrary(ScriptService scriptService) {
        BuildScript created = scriptService.createScript("MyLibrary", "Бібліотека класів із модульними тестами", ".");
        BuildScript script = scriptService.loadScript(created.getId());
        script.addProperty(new ScriptProperty("configuration", "Debug"));
        script.addProperty(new ScriptProperty("build.dir", "build"));

        Target clean = target("clean", "Видалити результати попередньої збірки");
        clean.addTask(task(TaskType.DELETE, "dir", "${build.dir}"));
        Target compile = target("compile", "Скомпілювати бібліотеку");
        compile.getDependsOn().add("clean");
        compile.addTask(task(TaskType.MKDIR, "dir", "${build.dir}"));
        BuildTask csc = task(TaskType.CSC, "target", "library");
        csc.setAttribute("output", "${build.dir}/MyLibrary.dll");
        compile.addTask(csc);
        Target test = target("test", "Запустити модульні тести");
        test.getDependsOn().add("compile");
        BuildTask exec = task(TaskType.EXEC, "program", "nunit3-console");
        exec.setAttribute("commandline", "${build.dir}/MyLibrary.Tests.dll");
        test.addTask(exec);

        Target build = script.findTarget("build").orElseThrow();
        build.getDependsOn().add("test");
        build.addTask(task(TaskType.ECHO, "message", "Збірку MyLibrary завершено"));
        script.getTargets().clear();
        script.getTargets().addAll(List.of(clean, compile, test, build));
        scriptService.saveScript(script);
    }

    /**
     * Відкриває редактор нового скрипта WebApi, вносить помилки через вкладку XML (як користувач)
     * і натискає «Перевірити»; знімок містить редактор і діалог звіту поверх нього.
     */
    private static void captureValidationReport(ScriptService scriptService, Path file) throws Exception {
        BuildScript webApi = scriptService.createScript("WebApi", "REST API інтернет-магазину", "src/WebApi");
        ScriptEditorForm editor = onEdt(() -> {
            ScriptEditorForm form = new ScriptEditorForm(scriptService, scriptService.loadScript(webApi.getId()));
            form.setLocation(60, 60);
            form.setVisible(true);
            return form;
        });
        settle(800);
        onEdt(() -> {
            // зміни тексту XML застосовуються під час повернення на вкладку «Конструктор»
            JTabbedPane tabs = find(editor, JTabbedPane.class, null);
            tabs.setSelectedIndex(1);
            find(editor, RSyntaxTextArea.class, null).setText(BROKEN_WEB_API_XML);
            tabs.setSelectedIndex(0);
            return null;
        });
        settle(400);
        // діалог модальний, тому кнопка натискається без очікування завершення обробника
        SwingUtilities.invokeLater(() -> find(editor, JButton.class, "checkButton").doClick());
        settle(800);
        ValidationReportDialog report = onEdt(() -> {
            for (Window owned : editor.getOwnedWindows()) {
                if (owned instanceof ValidationReportDialog dialog && dialog.isShowing()) {
                    // діалог зсунуто вниз (над рядком стану), щоб у кадрі лишилися цілі з циклом у дереві
                    dialog.setLocation(editor.getX() + (editor.getWidth() - dialog.getWidth()) / 2,
                            editor.getY() + editor.getHeight() - dialog.getHeight() - 36);
                    return dialog;
                }
            }
            throw new IllegalStateException("Validation report dialog is not shown");
        });
        capture(editor, report, file);
        onEdt(() -> {
            report.dispose();
            editor.dispose();
            return null;
        });
    }

    private static Target target(String name, String description) {
        Target target = new Target();
        target.setName(name);
        target.setDescription(description);
        return target;
    }

    private static BuildTask task(TaskType type, String attribute, String value) {
        BuildTask task = BuildTask.withDefaults(type);
        task.setAttribute(attribute, value);
        return task;
    }

    private static void fill(Container root, String fieldName, String text) {
        JTextField field = find(root, JTextField.class, fieldName);
        field.putClientProperty("JTextField.selectAllOnFocusPolicy", "never");
        field.setText(text);
    }

    /** Виділяє перше завдання вказаної цілі в дереві редактора. */
    private static void selectTask(JTree tree, String targetName) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getPathForRow(row).getLastPathComponent();
            if (node.getUserObject() instanceof BuildTask
                    && ((DefaultMutableTreeNode) node.getParent()).getUserObject() instanceof Target target
                    && target.getName().equals(targetName)) {
                tree.setSelectionRow(row);
                tree.requestFocusInWindow();
                return;
            }
        }
        throw new IllegalStateException("Task of target " + targetName + " not found");
    }

    private static void collapseFoldStartingWith(RSyntaxTextArea area, String lineStart) throws Exception {
        FoldManager folds = area.getFoldManager();
        folds.reparse();
        for (int i = 0; i < folds.getFoldCount(); i++) {
            Fold fold = folds.getFold(i);
            for (int j = 0; j < fold.getChildCount(); j++) {
                Fold child = fold.getChild(j);
                int start = child.getStartOffset();
                int length = Math.min(lineStart.length() + 4, area.getDocument().getLength() - start);
                if (area.getText(start, length).strip().startsWith(lineStart)) {
                    child.setCollapsed(true);
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- рендеринг вікон

    /**
     * Малює кореневу панель вікна (разом із заголовком FlatLaf) у зображення і записує PNG.
     * Використовується paint(), а не printAll(): у режимі друку JTable не показує виділений рядок.
     */
    private static void capture(Window window, Path file) throws Exception {
        capture(window, null, file);
    }

    /** Те саме, але поверх вікна в його поточній позиції малюється ще й діалог (з легкою тінню). */
    private static void capture(Window window, Window dialog, Path file) throws Exception {
        settle(400);
        BufferedImage image = onEdt(() -> {
            JRootPane root = ((RootPaneContainer) window).getRootPane();
            root.validate();
            BufferedImage img = new BufferedImage(root.getWidth() * SCALE, root.getHeight() * SCALE,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.scale(SCALE, SCALE);
            RepaintManager repaintManager = RepaintManager.currentManager(root);
            repaintManager.setDoubleBufferingEnabled(false);
            try {
                root.paint(g);
                // спливаючі вікна (підказки автодоповнення) малюються поверх у своїй позиції
                Point origin = root.getLocationOnScreen();
                for (Window owned : window.getOwnedWindows()) {
                    if (owned.isShowing() && !(owned instanceof Dialog) && owned instanceof RootPaneContainer popup) {
                        Point location = popup.getRootPane().getLocationOnScreen();
                        Graphics2D child = (Graphics2D) g.create(location.x - origin.x, location.y - origin.y,
                                owned.getWidth(), owned.getHeight());
                        popup.getRootPane().paint(child);
                        child.dispose();
                    }
                }
                if (dialog != null) {
                    paintDialog(g, origin, dialog);
                }
            } finally {
                repaintManager.setDoubleBufferingEnabled(true);
                g.dispose();
            }
            // тонка рамка, щоб вікно не зливалося з білим тлом сторінки звіту
            Graphics2D border = img.createGraphics();
            border.setColor(new Color(0x9E, 0x9E, 0x9E));
            border.drawRect(0, 0, img.getWidth() - 1, img.getHeight() - 1);
            border.dispose();
            return img;
        });
        ImageIO.write(image, "png", file.toFile());
        System.out.println("  " + file.getFileName() + " (" + image.getWidth() + "x" + image.getHeight() + ")");
    }

    /** Малює діалог у його позиції відносно вікна-власника, з тінню і рамкою. */
    private static void paintDialog(Graphics2D g, Point origin, Window dialog) {
        JRootPane dialogRoot = ((RootPaneContainer) dialog).getRootPane();
        Point location = dialogRoot.getLocationOnScreen();
        int x = location.x - origin.x;
        int y = location.y - origin.y;
        int width = dialogRoot.getWidth();
        int height = dialogRoot.getHeight();
        for (int i = 8; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 10));
            g.fillRoundRect(x - i + 2, y - i + 4, width + 2 * i - 4, height + 2 * i - 4, 2 * i, 2 * i);
        }
        Graphics2D child = (Graphics2D) g.create(x, y, width, height);
        dialogRoot.paint(child);
        child.dispose();
        g.setColor(new Color(0x9E, 0x9E, 0x9E));
        g.drawRect(x, y, width - 1, height - 1);
    }

    /** Дає Swing час показати вікна і завершити компонування. */
    private static void settle(long millis) throws Exception {
        Thread.sleep(millis);
        for (int i = 0; i < 3; i++) {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    /** Шукає в ієрархії компонентів перший компонент заданого типу (і, за потреби, з заданим іменем). */
    private static <T extends Component> T find(Container root, Class<T> type, String name) {
        T found = findOrNull(root, type, name);
        if (found == null) {
            throw new IllegalStateException("Component " + type.getSimpleName() + " " + name + " not found");
        }
        return found;
    }

    private static <T extends Component> T findOrNull(Container root, Class<T> type, String name) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component) && (name == null || name.equals(component.getName()))) {
                return type.cast(component);
            }
            if (component instanceof Container container) {
                T found = findOrNull(container, type, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
