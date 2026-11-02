package ua.kpi.trpz.nantbuilder.model;

import java.util.List;

/**
 * Тип завдання NAnt, яке можна додати до цілі.
 * Кожен тип знає свою назву XML-елемента та перелік атрибутів за замовчуванням.
 */
public enum TaskType {
    ECHO("echo", "message"),
    MKDIR("mkdir", "dir"),
    DELETE("delete", "dir"),
    COPY("copy", "file", "todir"),
    MSBUILD("msbuild", "project"),
    CSC("csc", "target", "output"),
    EXEC("exec", "program", "commandline");

    private final String xmlName;
    private final List<String> defaultAttributes;

    TaskType(String xmlName, String... defaultAttributes) {
        this.xmlName = xmlName;
        this.defaultAttributes = List.of(defaultAttributes);
    }

    /** Повертає назву XML-елемента завдання, наприклад {@code echo}. */
    public String getXmlName() {
        return xmlName;
    }

    /** Повертає назви атрибутів, якими заповнюється нове завдання. */
    public List<String> getDefaultAttributes() {
        return defaultAttributes;
    }

    /**
     * Знаходить тип завдання за назвою XML-елемента.
     *
     * @throws IllegalArgumentException якщо такого завдання немає
     */
    public static TaskType fromXmlName(String xmlName) {
        for (TaskType type : values()) {
            if (type.xmlName.equals(xmlName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Невідоме завдання NAnt: " + xmlName);
    }
}
