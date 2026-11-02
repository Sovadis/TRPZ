package ua.kpi.trpz.nantbuilder.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Скрипт збірки NAnt ({@code <project>}): заголовок, властивості та цілі.
 */
public class BuildScript {
    private Long id;
    private String name;
    private String description;
    private String defaultTarget;
    private String baseDir = ".";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<ScriptProperty> properties = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();

    /** Додає властивість у кінець списку властивостей. */
    public void addProperty(ScriptProperty p) {
        properties.add(p);
    }

    /** Додає ціль у кінець списку цілей. */
    public void addTarget(Target t) {
        targets.add(t);
    }

    /** Шукає ціль за назвою. */
    public Optional<Target> findTarget(String name) {
        return targets.stream()
                .filter(target -> target.getName().equals(name))
                .findFirst();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getDefaultTarget() {
        return defaultTarget;
    }

    public void setDefaultTarget(String defaultTarget) {
        this.defaultTarget = defaultTarget;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Повертає «живий» список властивостей скрипта. */
    public List<ScriptProperty> getProperties() {
        return properties;
    }

    /** Повертає «живий» список цілей скрипта. */
    public List<Target> getTargets() {
        return targets;
    }
}
