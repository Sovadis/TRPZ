package ua.kpi.trpz.nantbuilder.service;

import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Імпорт рішення .sln: аналіз проєктів, генерація скрипта збірки та збереження його в базі даних.
 */
public class SolutionImportService {

    private final SolutionParser parser;
    private final ScriptGenerator generator;
    private final ScriptService scriptService;

    /** Створює сервіс імпорту з аналізатора, генератора і сервісу скриптів. */
    public SolutionImportService(SolutionParser parser, ScriptGenerator generator, ScriptService scriptService) {
        this.parser = parser;
        this.generator = generator;
        this.scriptService = scriptService;
    }

    /**
     * Імпортує рішення і зберігає згенерований скрипт під унікальною назвою ("X", "X (2)", ...).
     *
     * @throws IOException         якщо файл не вдалося прочитати
     * @throws ValidationException якщо в рішенні немає проєктів
     */
    public BuildScript importSolution(Path slnFile) throws IOException {
        SolutionInfo solution = parser.parse(slnFile);
        if (solution.projects().isEmpty()) {
            throw new ValidationException("У файлі " + slnFile.getFileName() + " не знайдено жодного проєкту");
        }
        BuildScript script = generator.generate(solution);
        script.setName(uniqueName(solution.name()));
        return scriptService.saveScript(script);
    }

    private String uniqueName(String baseName) {
        Set<String> usedNames = new HashSet<>();
        for (BuildScript script : scriptService.listScripts()) {
            usedNames.add(script.getName());
        }
        String name = baseName;
        for (int counter = 2; usedNames.contains(name); counter++) {
            name = baseName + " (" + counter + ")";
        }
        return name;
    }
}
