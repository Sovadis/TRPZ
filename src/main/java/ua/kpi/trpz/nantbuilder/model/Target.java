package ua.kpi.trpz.nantbuilder.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ціль NAnt ({@code <target>}): іменований набір завдань, який може залежати від інших цілей.
 */
public class Target {
    private Long id;
    private Long scriptId;
    private String name;
    private String description;
    private int position;
    private final List<String> dependsOn = new ArrayList<>();
    private final List<BuildTask> tasks = new ArrayList<>();

    /** Додає завдання в кінець цілі. */
    public void addTask(BuildTask t) {
        tasks.add(t);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getScriptId() {
        return scriptId;
    }

    public void setScriptId(Long scriptId) {
        this.scriptId = scriptId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    /** Повертає «живий» список назв цілей того ж скрипта, від яких залежить ця ціль. */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    /** Повертає «живий» список завдань цілі. */
    public List<BuildTask> getTasks() {
        return tasks;
    }
}
