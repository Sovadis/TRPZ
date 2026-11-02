package ua.kpi.trpz.nantbuilder.ui;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import ua.kpi.trpz.nantbuilder.db.DataAccessException;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;
import ua.kpi.trpz.nantbuilder.service.ScriptService;
import ua.kpi.trpz.nantbuilder.service.ValidationException;
import ua.kpi.trpz.nantbuilder.service.XmlScriptSerializer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Редактор скрипта збірки. Вкладка «Конструктор»: палітра елементів (джерело перетягування),
 * дерево скрипта (ціль перетягування) і таблиця атрибутів вибраного вузла.
 * Вкладка «XML»: текст скрипта з підсвічуванням синтаксису, згортанням коду та автодоповненням.
 */
public class ScriptEditorForm extends JFrame {

    private static final String TITLE_PREFIX = "Редактор скрипта — ";
    private static final String PALETTE_TARGET = "Ціль";
    private static final String PALETTE_PROPERTY = "Властивість";
    private static final String PROPERTIES_NODE = "Властивості";
    private static final String TARGETS_NODE = "Цілі";
    private static final int DESIGNER_TAB = 0;
    private static final int XML_TAB = 1;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ScriptService scriptService;
    private final XmlScriptSerializer serializer = new XmlScriptSerializer();
    /** Робоча копія скрипта, яку змінюють конструктор і вкладка XML. */
    private BuildScript script;

    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode(), true);
    private final JTree tree = new JTree(treeModel);
    private final AttributeTableModel attributeModel = new AttributeTableModel();
    private final JTable attributeTable = new JTable(attributeModel);
    private final JLabel attributesTitle = new JLabel("Атрибути");
    private final RSyntaxTextArea xmlArea = new RSyntaxTextArea(25, 90);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel modifiedLabel = new JLabel(" ");

    /** Є зміни, яких ще немає в базі даних. */
    private boolean modified;
    /** Текст XML змінено вручну, але ще не застосовано до моделі. */
    private boolean xmlEdited;
    /** Текст XML зараз змінює сама програма (а не користувач). */
    private boolean updatingXml;
    private int lastSelectedTab = DESIGNER_TAB;

    /** Створює редактор для повністю завантаженого скрипта (див. {@link ScriptService#loadScript(long)}). */
    public ScriptEditorForm(ScriptService scriptService, BuildScript script) {
        super(TITLE_PREFIX + script.getName());
        this.scriptService = scriptService;
        this.script = script;

        tabs.addTab("Конструктор", createDesignerPanel());
        tabs.addTab("XML", createXmlPanel());
        tabs.addChangeListener(e -> onTabChanged());

        add(createToolBar(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> saveScript(),
                KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeEditor();
            }
        });

        rebuildTree();
        tree.setSelectionRow(0);
        refreshXml();
        setModified(false);
        setStatus("Скрипт завантажено з бази даних");
        setSize(1100, 700);
    }

    // ---------------------------------------------------------------- побудова форми

    /** Панель інструментів із кнопками дій редактора. */
    private JPanel createToolBar() {
        JButton saveButton = new JButton("Зберегти");
        saveButton.setToolTipText("Зберегти скрипт у базі даних (Ctrl+S)");
        saveButton.addActionListener(e -> saveScript());
        JButton applyButton = new JButton("Застосувати XML");
        applyButton.setToolTipText("Розібрати текст на вкладці XML і оновити дерево");
        applyButton.addActionListener(e -> applyXml());
        JButton exportButton = new JButton("Експорт .build…");
        exportButton.setToolTipText("Записати скрипт NAnt у файл");
        exportButton.addActionListener(e -> exportScript());
        JButton deleteButton = new JButton("Видалити елемент");
        deleteButton.setToolTipText("Видалити вибрану властивість, ціль або завдання (Delete)");
        deleteButton.addActionListener(e -> deleteSelectedElement());

        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        toolBar.add(saveButton);
        toolBar.add(applyButton);
        toolBar.add(exportButton);
        toolBar.add(deleteButton);
        return toolBar;
    }

    private JComponent createDesignerPanel() {
        JSplitPane treeAndAttributes = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createTreePanel(), createAttributesPanel());
        treeAndAttributes.setResizeWeight(0.55);
        treeAndAttributes.setDividerLocation(500);

        JSplitPane designer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createPalettePanel(), treeAndAttributes);
        designer.setDividerLocation(190);
        return designer;
    }

    private JComponent createPalettePanel() {
        DefaultListModel<String> items = new DefaultListModel<>();
        items.addElement(PALETTE_TARGET);
        items.addElement(PALETTE_PROPERTY);
        for (TaskType type : TaskType.values()) {
            items.addElement(type.getXmlName());
        }
        JList<String> palette = new JList<>(items);
        palette.setName("palette");
        palette.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        palette.setDragEnabled(true);
        palette.setTransferHandler(new PaletteTransferHandler());
        palette.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && palette.getSelectedValue() != null) {
                    addToSelectedNode(palette.getSelectedValue());
                }
            }
        });

        JLabel hint = new JLabel("<html>Перетягніть елемент на вузол дерева або двічі клацніть по ньому.</html>");
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return section(new JLabel("Палітра"), new JScrollPane(palette), hint);
    }

    private JComponent createTreePanel() {
        tree.setName("scriptTree");
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ScriptTreeRenderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> showAttributes());
        tree.setDropMode(DropMode.ON);
        tree.setTransferHandler(new TreeDropHandler());
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteElement");
        tree.getActionMap().put("deleteElement", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedElement();
            }
        });
        return section(new JLabel("Структура скрипта"), new JScrollPane(tree), null);
    }

    private JComponent createAttributesPanel() {
        attributeTable.setName("attributeTable");
        attributeTable.setFillsViewportHeight(true);
        attributeTable.setSurrendersFocusOnKeystroke(true);
        attributeTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        attributeTable.getTableHeader().setReorderingAllowed(false);
        attributeTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        attributeTable.getColumnModel().getColumn(1).setPreferredWidth(300);

        JLabel hint = new JLabel("<html>Клацніть двічі по значенню, щоб змінити його.</html>");
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return section(attributesTitle, new JScrollPane(attributeTable), hint);
    }

    private JComponent createXmlPanel() {
        xmlArea.setName("xmlArea");
        xmlArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_XML);
        xmlArea.setCodeFoldingEnabled(true);
        xmlArea.setAntiAliasingEnabled(true);
        xmlArea.setTabSize(2);
        xmlArea.setTabsEmulated(true);
        xmlArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                xmlTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                xmlTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // зміна атрибутів тексту (підсвічування) не є редагуванням
            }
        });

        AutoCompletion autoCompletion = new AutoCompletion(NantCompletionProvider.create());
        autoCompletion.setAutoActivationEnabled(true);
        autoCompletion.setAutoActivationDelay(300);
        autoCompletion.setParameterAssistanceEnabled(true);
        autoCompletion.setChoicesWindowSize(380, 240);
        autoCompletion.install(xmlArea);

        RTextScrollPane scrollPane = new RTextScrollPane(xmlArea);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setFoldIndicatorEnabled(true);

        JLabel hint = new JLabel("Ctrl+Space — автодоповнення (після «<» підказки з'являються самі). "
                + "Щоб перенести зміни в дерево, натисніть «Застосувати XML».");
        hint.setForeground(Color.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        statusBar.add(statusLabel, BorderLayout.CENTER);
        statusBar.add(modifiedLabel, BorderLayout.EAST);
        return statusBar;
    }

    /** Панель із заголовком, основним вмістом і (необов'язково) підказкою знизу. */
    private static JComponent section(JLabel title, JComponent content, JComponent footer) {
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(title, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        if (footer != null) {
            panel.add(footer, BorderLayout.SOUTH);
        }
        panel.setMinimumSize(new Dimension(150, 100));
        return panel;
    }

    // ---------------------------------------------------------------- дерево і атрибути

    /** Перебудовує дерево з робочої копії скрипта і розгортає всі вузли. */
    private void rebuildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(script, true);
        DefaultMutableTreeNode propertiesNode = new DefaultMutableTreeNode(PROPERTIES_NODE, true);
        for (ScriptProperty property : script.getProperties()) {
            propertiesNode.add(new DefaultMutableTreeNode(property, false));
        }
        DefaultMutableTreeNode targetsNode = new DefaultMutableTreeNode(TARGETS_NODE, true);
        for (Target target : script.getTargets()) {
            DefaultMutableTreeNode targetNode = new DefaultMutableTreeNode(target, true);
            for (BuildTask task : target.getTasks()) {
                targetNode.add(new DefaultMutableTreeNode(task, false));
            }
            targetsNode.add(targetNode);
        }
        root.add(propertiesNode);
        root.add(targetsNode);
        treeModel.setRoot(root);
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    /** Виділяє вузол, що відображає заданий об'єкт моделі (або корінь, якщо такого немає). */
    private void selectObject(Object userObject) {
        Enumeration<TreeNode> nodes = ((DefaultMutableTreeNode) treeModel.getRoot()).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() == userObject) {
                TreePath path = new TreePath(node.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
        tree.setSelectionRow(0);
    }

    private DefaultMutableTreeNode selectedNode() {
        TreePath path = tree.getSelectionPath();
        return path == null ? null : (DefaultMutableTreeNode) path.getLastPathComponent();
    }

    private void showAttributes() {
        stopTableEditing();
        DefaultMutableTreeNode node = selectedNode();
        Object object = node == null ? null : node.getUserObject();
        attributeModel.setRows(rowsFor(object));
        attributesTitle.setText(switch (object) {
            case BuildScript s -> "Атрибути: <project> " + s.getName();
            case ScriptProperty p -> "Атрибути: <property> " + p.getName();
            case Target t -> "Атрибути: <target> " + t.getName();
            case BuildTask task -> "Атрибути: <" + task.getType().getXmlName() + "> у цілі " + targetFor(node).getName();
            case null, default -> "Атрибути";
        });
    }

    /** Рядки таблиці атрибутів для вибраного об'єкта моделі. */
    private List<AttributeRow> rowsFor(Object object) {
        List<AttributeRow> rows = new ArrayList<>();
        if (object instanceof BuildScript s) {
            rows.add(new AttributeRow("name", s::getName, s::setName));
            rows.add(new AttributeRow("default", s::getDefaultTarget, value -> s.setDefaultTarget(blankToNull(value))));
            rows.add(new AttributeRow("basedir", s::getBaseDir, s::setBaseDir));
            rows.add(new AttributeRow("description", s::getDescription, value -> s.setDescription(blankToNull(value))));
        } else if (object instanceof ScriptProperty p) {
            rows.add(new AttributeRow("name", p::getName, value -> p.setName(value.trim())));
            rows.add(new AttributeRow("value", p::getValue, p::setValue));
            rows.add(new AttributeRow("readonly", () -> String.valueOf(p.isReadOnly()),
                    value -> p.setReadOnly(Boolean.parseBoolean(value.trim()))));
        } else if (object instanceof Target t) {
            rows.add(new AttributeRow("name", t::getName, value -> renameTarget(t, value.trim())));
            rows.add(new AttributeRow("depends", () -> String.join(",", t.getDependsOn()),
                    value -> setDependsOn(t, value)));
            rows.add(new AttributeRow("description", t::getDescription, value -> t.setDescription(blankToNull(value))));
        } else if (object instanceof BuildTask task) {
            for (String name : task.getAttributes().keySet()) {
                rows.add(new AttributeRow(name, () -> task.getAttribute(name), value -> task.setAttribute(name, value)));
            }
        }
        return rows;
    }

    /** Викликається після зміни значення в таблиці атрибутів. */
    private void attributeChanged() {
        Enumeration<TreeNode> nodes = ((DefaultMutableTreeNode) treeModel.getRoot()).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            treeModel.nodeChanged(nodes.nextElement());
        }
        refreshXml();
        setModified(true);
    }

    private void renameTarget(Target target, String newName) {
        String oldName = target.getName();
        target.setName(newName);
        for (Target other : script.getTargets()) {
            other.getDependsOn().replaceAll(name -> name.equals(oldName) ? newName : name);
        }
        if (oldName.equals(script.getDefaultTarget())) {
            script.setDefaultTarget(newName);
        }
    }

    private static void setDependsOn(Target target, String commaSeparated) {
        target.getDependsOn().clear();
        for (String name : commaSeparated.split(",")) {
            if (!name.isBlank()) {
                target.getDependsOn().add(name.trim());
            }
        }
    }

    // ---------------------------------------------------------------- перетягування з палітри

    private boolean canAdd(String item, DefaultMutableTreeNode node) {
        Object object = node.getUserObject();
        return switch (item) {
            case PALETTE_TARGET -> object instanceof BuildScript || TARGETS_NODE.equals(object);
            case PALETTE_PROPERTY -> object instanceof BuildScript || PROPERTIES_NODE.equals(object)
                    || object instanceof ScriptProperty;
            default -> targetFor(node) != null;
        };
    }

    /** Додає елемент палітри до вузла дерева; повертає false, якщо сюди його додати не можна. */
    private boolean addPaletteItem(String item, DefaultMutableTreeNode node) {
        if (node == null || !canAdd(item, node)) {
            return false;
        }
        stopTableEditing();
        Object added;
        if (PALETTE_TARGET.equals(item)) {
            Set<String> names = new HashSet<>();
            script.getTargets().forEach(target -> names.add(target.getName()));
            Target target = new Target();
            target.setName(uniqueName("target-", names));
            script.addTarget(target);
            added = target;
        } else if (PALETTE_PROPERTY.equals(item)) {
            Set<String> names = new HashSet<>();
            script.getProperties().forEach(property -> names.add(property.getName()));
            ScriptProperty property = new ScriptProperty(uniqueName("property-", names), "");
            script.addProperty(property);
            added = property;
        } else {
            BuildTask task = BuildTask.withDefaults(TaskType.fromXmlName(item));
            targetFor(node).addTask(task);
            added = task;
        }
        rebuildTree();
        selectObject(added);
        refreshXml();
        setModified(true);
        setStatus("Додано елемент «" + item + "»");
        return true;
    }

    private void addToSelectedNode(String item) {
        if (!addPaletteItem(item, selectedNode())) {
            setStatus(PALETTE_TARGET.equals(item) || PALETTE_PROPERTY.equals(item)
                    ? "Виберіть у дереві корінь або вузол «" + (PALETTE_TARGET.equals(item) ? TARGETS_NODE : PROPERTIES_NODE) + "»"
                    : "Виберіть у дереві ціль, до якої додати завдання «" + item + "»");
        }
    }

    /** Ціль, до якої належить вузол: сама ціль або ціль-батько завдання. */
    private static Target targetFor(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof Target target) {
            return target;
        }
        if (node.getUserObject() instanceof BuildTask
                && node.getParent() instanceof DefaultMutableTreeNode parent
                && parent.getUserObject() instanceof Target target) {
            return target;
        }
        return null;
    }

    private static String uniqueName(String prefix, Set<String> usedNames) {
        int number = 1;
        while (usedNames.contains(prefix + number)) {
            number++;
        }
        return prefix + number;
    }

    private void deleteSelectedElement() {
        DefaultMutableTreeNode node = selectedNode();
        if (node == null) {
            return;
        }
        stopTableEditing();
        Object object = node.getUserObject();
        Object parentObject = node.getParent() instanceof DefaultMutableTreeNode parent
                ? parent.getUserObject() : null;
        if (object instanceof ScriptProperty property) {
            script.getProperties().remove(property);
        } else if (object instanceof Target target) {
            script.getTargets().remove(target);
            for (Target other : script.getTargets()) {
                other.getDependsOn().remove(target.getName());
            }
            if (target.getName().equals(script.getDefaultTarget())) {
                script.setDefaultTarget(null);
            }
        } else if (object instanceof BuildTask task && parentObject instanceof Target target) {
            target.getTasks().remove(task);
        } else {
            setStatus("Цей вузол видалити не можна");
            return;
        }
        rebuildTree();
        selectObject(parentObject);
        refreshXml();
        setModified(true);
        setStatus("Елемент видалено");
    }

    // ---------------------------------------------------------------- XML, збереження, експорт

    /** Записує в редактор XML актуальний текст робочої копії скрипта. */
    private void refreshXml() {
        updatingXml = true;
        try {
            int caret = xmlArea.getCaretPosition();
            xmlArea.setText(serializer.toXml(script));
            xmlArea.setCaretPosition(Math.min(caret, xmlArea.getDocument().getLength()));
        } finally {
            updatingXml = false;
        }
        xmlEdited = false;
    }

    private void xmlTextChanged() {
        if (!updatingXml) {
            xmlEdited = true;
            setModified(true);
        }
    }

    /** Розбирає текст XML і замінює ним вміст робочої копії скрипта. */
    private boolean applyXml() {
        BuildScript parsed;
        try {
            parsed = serializer.fromXml(xmlArea.getText());
        } catch (ValidationException e) {
            tabs.setSelectedIndex(XML_TAB);
            showError("XML не застосовано:\n" + e.getMessage());
            return false;
        }
        stopTableEditing();
        script.setName(parsed.getName());
        script.setDescription(parsed.getDescription());
        script.setDefaultTarget(parsed.getDefaultTarget());
        script.setBaseDir(parsed.getBaseDir());
        script.getProperties().clear();
        script.getProperties().addAll(parsed.getProperties());
        script.getTargets().clear();
        script.getTargets().addAll(parsed.getTargets());

        rebuildTree();
        tree.setSelectionRow(0);
        refreshXml();
        setStatus("XML застосовано до структури скрипта");
        return true;
    }

    /** Під час переходу з вкладки XML незастосовані зміни застосовуються автоматично. */
    private void onTabChanged() {
        int selected = tabs.getSelectedIndex();
        boolean leavingXml = lastSelectedTab == XML_TAB && selected != XML_TAB;
        lastSelectedTab = selected;
        if (leavingXml && xmlEdited) {
            applyXml();
        }
    }

    /** Сценарій «Зберегти скрипт»: запис у базу даних, повторне читання і оновлення форми. */
    private void saveScript() {
        stopTableEditing();
        if (xmlEdited && !applyXml()) {
            return;
        }
        int selectedRow = tree.getMinSelectionRow();
        try {
            scriptService.saveScript(script);
            script = scriptService.loadScript(script.getId());
        } catch (ValidationException | DataAccessException e) {
            showError("Скрипт не збережено:\n" + e.getMessage());
            return;
        }
        setTitle(TITLE_PREFIX + script.getName());
        rebuildTree();
        tree.setSelectionRow(Math.max(0, Math.min(selectedRow, tree.getRowCount() - 1)));
        refreshXml();
        setModified(false);
        setStatus("Скрипт збережено в базі даних о " + LocalTime.now().format(TIME_FORMAT));
    }

    private void exportScript() {
        stopTableEditing();
        if (xmlEdited && !applyXml()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Експорт скрипта NAnt");
        chooser.setFileFilter(new FileNameExtensionFilter("Скрипти NAnt (*.build)", "build"));
        chooser.setSelectedFile(new File(script.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".build"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().endsWith(".build")) {
            file = file.resolveSibling(file.getFileName() + ".build");
        }
        if (Files.exists(file) && !confirm("Файл " + file.getFileName() + " уже існує. Перезаписати його?")) {
            return;
        }
        try {
            scriptService.exportScript(script, file);
            setStatus("Скрипт експортовано у файл " + file);
        } catch (UncheckedIOException e) {
            showError(e.getMessage());
        }
    }

    private void closeEditor() {
        stopTableEditing();
        if (modified && !confirm("Є незбережені зміни. Закрити редактор без збереження?")) {
            return;
        }
        dispose();
    }

    // ---------------------------------------------------------------- допоміжні методи

    private void stopTableEditing() {
        if (attributeTable.isEditing()) {
            attributeTable.getCellEditor().stopCellEditing();
        }
    }

    private void setModified(boolean modified) {
        this.modified = modified;
        modifiedLabel.setText(modified ? "Є незбережені зміни" : "Усі зміни збережено");
        modifiedLabel.setForeground(modified ? new Color(0xB2, 0x6A, 0x00) : new Color(0x2E, 0x7D, 0x32));
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Помилка", JOptionPane.ERROR_MESSAGE);
    }

    private boolean confirm(String message) {
        Object[] options = {"Так", "Ні"};
        return JOptionPane.showOptionDialog(this, message, "Підтвердження", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == 0;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String escapeHtml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------------------------------------------------------------- вкладені класи

    /** Рядок таблиці атрибутів: назва та способи прочитати й змінити значення в моделі. */
    private record AttributeRow(String name, Supplier<String> getter, Consumer<String> setter) {
    }

    /** Модель таблиці атрибутів вибраного вузла; зміни відразу записуються в модель скрипта. */
    private class AttributeTableModel extends AbstractTableModel {
        private List<AttributeRow> rows = List.of();

        void setRows(List<AttributeRow> newRows) {
            rows = newRows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Атрибут" : "Значення";
        }

        @Override
        public Object getValueAt(int row, int column) {
            AttributeRow attribute = rows.get(row);
            if (column == 0) {
                return attribute.name();
            }
            String value = attribute.getter().get();
            return value == null ? "" : value;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            String newValue = value == null ? "" : value.toString();
            if (newValue.equals(getValueAt(row, column))) {
                return;
            }
            rows.get(row).setter().accept(newValue);
            fireTableRowsUpdated(row, row);
            attributeChanged();
        }
    }

    /** Відображення вузлів дерева: назва елемента і короткий підсумок його атрибутів. */
    private class ScriptTreeRenderer extends DefaultTreeCellRenderer {
        private static final int MAX_DETAILS = 48;

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            Object object = ((DefaultMutableTreeNode) value).getUserObject();
            return super.getTreeCellRendererComponent(tree, labelFor(object, selected), selected, expanded,
                    leaf, row, hasFocus);
        }

        private String labelFor(Object object, boolean selected) {
            return switch (object) {
                case BuildScript s -> html(s.getName(), s.getDefaultTarget() == null
                        ? "проєкт" : "проєкт, ціль за замовчуванням: " + s.getDefaultTarget(), selected);
                case ScriptProperty p -> html(p.getName(), "= " + p.getValue(), selected);
                case Target t -> html(t.getName(), t.getDependsOn().isEmpty()
                        ? "" : shorten("→ " + String.join(", ", t.getDependsOn())), selected);
                case BuildTask task -> html(task.getType().getXmlName(), shorten(attributesSummary(task)), selected);
                case String group when PROPERTIES_NODE.equals(group) ->
                        group + " (" + script.getProperties().size() + ")";
                case String group when TARGETS_NODE.equals(group) -> group + " (" + script.getTargets().size() + ")";
                case null, default -> String.valueOf(object);
            };
        }

        private String attributesSummary(BuildTask task) {
            StringBuilder details = new StringBuilder();
            task.getAttributes().forEach((name, value) -> details.append(name).append("=\"").append(value).append("\" "));
            return details.toString().trim();
        }

        /** Довгі підсумки обрізаються, щоб дерево не потребувало горизонтальної прокрутки. */
        private String shorten(String text) {
            return text.length() > MAX_DETAILS ? text.substring(0, MAX_DETAILS) + "…" : text;
        }

        private String html(String name, String details, boolean selected) {
            String color = selected ? "" : " color='#6E6E6E'";
            return "<html><b>" + escapeHtml(name) + "</b>&nbsp;&nbsp;<font" + color + ">"
                    + escapeHtml(details) + "</font></html>";
        }
    }

    /** Джерело перетягування: передає назву вибраного елемента палітри як текст. */
    private static class PaletteTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent component) {
            return COPY;
        }

        @Override
        protected Transferable createTransferable(JComponent component) {
            Object value = ((JList<?>) component).getSelectedValue();
            return value == null ? null : new StringSelection(value.toString());
        }
    }

    /**
     * Ціль перетягування: приймає елементи палітри на відповідні вузли дерева.
     * Також підтримує вставку (Ctrl+C у палітрі, Ctrl+V у дереві) — тоді береться вибраний вузол.
     */
    private class TreeDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return false;
            }
            DefaultMutableTreeNode node = dropNode(support);
            String item = readItem(support);
            // якщо дані ще недоступні (залежить від платформи), остаточна перевірка буде в importData
            return node != null && (item == null || canAdd(item, node));
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            String item = readItem(support);
            return item != null && addPaletteItem(item, dropNode(support));
        }

        private DefaultMutableTreeNode dropNode(TransferSupport support) {
            if (!support.isDrop()) {
                return selectedNode();
            }
            TreePath path = ((JTree.DropLocation) support.getDropLocation()).getPath();
            return path == null ? null : (DefaultMutableTreeNode) path.getLastPathComponent();
        }

        private String readItem(TransferSupport support) {
            try {
                return (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException | IOException | InvalidDnDOperationException e) {
                return null;
            }
        }
    }
}
