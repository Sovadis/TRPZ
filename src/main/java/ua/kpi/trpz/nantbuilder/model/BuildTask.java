package ua.kpi.trpz.nantbuilder.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Завдання NAnt усередині цілі (наприклад {@code <echo message="..."/>}).
 * Атрибути зберігаються в порядку додавання.
 */
public class BuildTask {
    private Long id;
    private Long targetId;
    private TaskType type;
    private int position;
    private final Map<String, String> attributes = new LinkedHashMap<>();

    /** Створює завдання заданого типу, всі атрибути за замовчуванням мають порожнє значення. */
    public static BuildTask withDefaults(TaskType type) {
        BuildTask task = new BuildTask();
        task.setType(type);
        for (String attribute : type.getDefaultAttributes()) {
            task.setAttribute(attribute, "");
        }
        return task;
    }

    /** Повертає значення атрибута або {@code null}, якщо атрибута немає. */
    public String getAttribute(String name) {
        return attributes.get(name);
    }

    /** Встановлює (або додає) атрибут завдання. */
    public void setAttribute(String name, String value) {
        attributes.put(name, value);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    /** Повертає «живу» мапу атрибутів (зміни відразу потрапляють у завдання). */
    public Map<String, String> getAttributes() {
        return attributes;
    }
}
