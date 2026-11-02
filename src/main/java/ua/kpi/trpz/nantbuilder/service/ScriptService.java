package ua.kpi.trpz.nantbuilder.service;

import ua.kpi.trpz.nantbuilder.db.Database;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.repository.BuildScriptRepository;
import ua.kpi.trpz.nantbuilder.repository.PropertyRepository;
import ua.kpi.trpz.nantbuilder.repository.TargetRepository;
import ua.kpi.trpz.nantbuilder.repository.TaskRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Сервіс роботи зі скриптами збірки: створення, завантаження повного графа об'єктів,
 * збереження в одній транзакції, видалення та експорт у файл .build.
 */
public class ScriptService {

    /** Назва цілі за замовчуванням, яка створюється для кожного нового скрипта. */
    private static final String DEFAULT_TARGET = "build";

    private final Database db;
    private final BuildScriptRepository scripts;
    private final PropertyRepository properties;
    private final TargetRepository targets;
    private final TaskRepository tasks;
    private final XmlScriptSerializer serializer = new XmlScriptSerializer();

    /** Створює сервіс поверх бази даних і репозиторіїв. */
    public ScriptService(Database db, BuildScriptRepository scripts, PropertyRepository properties,
                         TargetRepository targets, TaskRepository tasks) {
        this.db = db;
        this.scripts = scripts;
        this.properties = properties;
        this.targets = targets;
        this.tasks = tasks;
    }

    /** Повертає заголовки всіх скриптів, упорядковані за назвою. */
    public List<BuildScript> listScripts() {
        return scripts.findAll();
    }

    /**
     * Створює новий скрипт із порожньою ціллю за замовчуванням "build".
     *
     * @throws ValidationException якщо назва порожня або вже зайнята
     */
    public BuildScript createScript(String name, String description, String baseDir) {
        String scriptName = name == null ? "" : name.trim();
        checkName(scriptName, null);

        BuildScript script = new BuildScript();
        script.setName(scriptName);
        script.setDescription(blankToNull(description));
        script.setBaseDir(baseDir == null || baseDir.isBlank() ? "." : baseDir.trim());
        script.setDefaultTarget(DEFAULT_TARGET);
        LocalDateTime now = now();
        script.setCreatedAt(now);
        script.setUpdatedAt(now);

        Target buildTarget = new Target();
        buildTarget.setName(DEFAULT_TARGET);
        buildTarget.setPosition(0);
        script.addTarget(buildTarget);

        return db.inTransaction(connection -> {
            scripts.save(script);
            buildTarget.setScriptId(script.getId());
            targets.save(buildTarget);
            return script;
        });
    }

    /**
     * Завантажує повний скрипт: заголовок, властивості, цілі з залежностями та завдання з атрибутами.
     *
     * @throws ValidationException якщо скрипт не знайдено
     */
    public BuildScript loadScript(long id) {
        BuildScript script = scripts.findById(id)
                .orElseThrow(() -> new ValidationException("Скрипт з ідентифікатором " + id + " не знайдено"));
        for (ScriptProperty property : properties.findByScriptId(id)) {
            script.addProperty(property);
        }

        List<Target> scriptTargets = targets.findByScriptId(id);
        Map<Long, String> namesById = new HashMap<>();
        for (Target target : scriptTargets) {
            namesById.put(target.getId(), target.getName());
        }
        for (Target target : scriptTargets) {
            for (Long dependencyId : targets.findDependencyIds(target.getId())) {
                target.getDependsOn().add(namesById.get(dependencyId));
            }
            for (BuildTask task : tasks.findByTargetId(target.getId())) {
                target.addTask(task);
            }
            script.addTarget(target);
        }
        return script;
    }

    /**
     * Зберігає весь скрипт в одній транзакції: оновлює заголовок (updatedAt = зараз)
     * і повністю замінює властивості, цілі, залежності та завдання.
     *
     * @throws ValidationException якщо дані скрипта некоректні
     */
    public BuildScript saveScript(BuildScript script) {
        validate(script);
        LocalDateTime now = now();
        if (script.getCreatedAt() == null) {
            script.setCreatedAt(now);
        }
        script.setUpdatedAt(now);

        return db.inTransaction(connection -> {
            scripts.save(script);
            long scriptId = script.getId();

            // старі дочірні записи видаляємо (цілі — каскадно із завданнями та залежностями)
            properties.deleteByScriptId(scriptId);
            targets.deleteByScriptId(scriptId);

            for (ScriptProperty property : script.getProperties()) {
                property.setId(null);
                property.setScriptId(scriptId);
                properties.save(property);
            }

            Map<String, Long> targetIdsByName = new HashMap<>();
            int targetPosition = 0;
            for (Target target : script.getTargets()) {
                target.setId(null);
                target.setScriptId(scriptId);
                target.setPosition(targetPosition++);
                targets.save(target);
                targetIdsByName.put(target.getName(), target.getId());
                saveTasks(target);
            }

            // залежності зберігаємо після всіх цілей, бо потрібні їхні нові id
            for (Target target : script.getTargets()) {
                List<Long> dependencyIds = new ArrayList<>();
                for (String dependencyName : target.getDependsOn()) {
                    dependencyIds.add(targetIdsByName.get(dependencyName));
                }
                targets.saveDependencies(target.getId(), dependencyIds);
            }
            return script;
        });
    }

    /** Видаляє скрипт разом з усіма властивостями, цілями та завданнями. */
    public void deleteScript(long id) {
        scripts.deleteById(id);
    }

    /** Записує скрипт у файл .build (NAnt XML у кодуванні UTF-8). */
    public void exportScript(BuildScript script, Path file) {
        try {
            Files.writeString(file, serializer.toXml(script), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Не вдалося записати файл " + file, e);
        }
    }

    private void saveTasks(Target target) {
        int taskPosition = 0;
        for (BuildTask task : target.getTasks()) {
            task.setId(null);
            task.setTargetId(target.getId());
            task.setPosition(taskPosition++);
            tasks.save(task);
        }
    }

    private void validate(BuildScript script) {
        String name = script.getName() == null ? "" : script.getName().trim();
        checkName(name, script.getId());
        script.setName(name);

        Set<String> propertyNames = new HashSet<>();
        for (ScriptProperty property : script.getProperties()) {
            if (property.getName() == null || property.getName().isBlank()) {
                throw new ValidationException("Назва властивості не може бути порожньою");
            }
            if (!propertyNames.add(property.getName())) {
                throw new ValidationException("Властивість «" + property.getName() + "» оголошено двічі");
            }
            if (property.getValue() == null) {
                property.setValue("");
            }
        }

        Set<String> targetNames = new HashSet<>();
        for (Target target : script.getTargets()) {
            if (target.getName() == null || target.getName().isBlank()) {
                throw new ValidationException("Назва цілі не може бути порожньою");
            }
            if (!targetNames.add(target.getName())) {
                throw new ValidationException("Ціль «" + target.getName() + "» оголошено двічі");
            }
        }
        for (Target target : script.getTargets()) {
            Set<String> seen = new HashSet<>();
            for (String dependencyName : target.getDependsOn()) {
                if (!targetNames.contains(dependencyName)) {
                    throw new ValidationException("Ціль «" + target.getName()
                            + "» залежить від невідомої цілі «" + dependencyName + "»");
                }
                if (dependencyName.equals(target.getName())) {
                    throw new ValidationException("Ціль «" + target.getName() + "» не може залежати від самої себе");
                }
                if (!seen.add(dependencyName)) {
                    throw new ValidationException("Ціль «" + target.getName()
                            + "» двічі залежить від «" + dependencyName + "»");
                }
            }
        }
    }

    /** Перевіряє, що назва не порожня і не зайнята іншим скриптом. */
    private void checkName(String name, Long ownId) {
        if (name.isEmpty()) {
            throw new ValidationException("Назва скрипта не може бути порожньою");
        }
        Optional<BuildScript> sameName = scripts.findByName(name);
        if (sameName.isPresent() && !sameName.get().getId().equals(ownId)) {
            throw new ValidationException("Скрипт з назвою «" + name + "» уже існує");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }
}
