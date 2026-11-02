package ua.kpi.trpz.nantbuilder.service;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Генерує скрипт збірки NAnt за результатом аналізу рішення .sln.
 * Для кожного проєкту створюється ціль {@code build-<Проєкт>}, що залежить від цілей
 * проєктів, на які він посилається; цілі впорядковуються так, щоб залежності йшли першими.
 */
public class ScriptGenerator {

    private static final String CLEAN_TARGET = "clean";
    private static final String BUILD_TARGET = "build";
    private static final String TARGET_PREFIX = "build-";

    /** Будує новий (ще не збережений) скрипт для рішення. */
    public BuildScript generate(SolutionInfo solution) {
        BuildScript script = new BuildScript();
        script.setName(solution.name());
        script.setDescription("Згенеровано з " + solution.name() + ".sln");
        script.setBaseDir(".");
        script.setDefaultTarget(BUILD_TARGET);

        script.addProperty(new ScriptProperty("configuration", "Release"));
        script.addProperty(new ScriptProperty("build.dir", "build"));

        Target clean = newTarget(CLEAN_TARGET, "Очистити каталог збірки");
        BuildTask delete = BuildTask.withDefaults(TaskType.DELETE);
        delete.setAttribute("dir", "${build.dir}");
        clean.addTask(delete);
        script.addTarget(clean);

        Map<String, SolutionProject> projectsByGuid = new LinkedHashMap<>();
        for (SolutionProject project : solution.projects()) {
            projectsByGuid.put(project.guid(), project);
        }

        Target build = newTarget(BUILD_TARGET, null);
        for (SolutionProject project : sortByDependencies(solution.projects(), projectsByGuid)) {
            Target projectTarget = newTarget(TARGET_PREFIX + project.name(), null);
            for (String guid : project.dependencyGuids()) {
                SolutionProject referenced = projectsByGuid.get(guid);
                if (referenced != null) {
                    projectTarget.getDependsOn().add(TARGET_PREFIX + referenced.name());
                }
            }
            if (projectTarget.getDependsOn().isEmpty()) {
                projectTarget.getDependsOn().add(CLEAN_TARGET);
            }

            BuildTask exec = BuildTask.withDefaults(TaskType.EXEC);
            exec.setAttribute("program", "msbuild");
            exec.setAttribute("commandline", project.relativePath() + " /p:Configuration=${configuration}");
            projectTarget.addTask(exec);

            script.addTarget(projectTarget);
            build.getDependsOn().add(projectTarget.getName());
        }
        script.addTarget(build);
        return script;
    }

    /**
     * Топологічне сортування проєктів (пошук у глибину): кожен проєкт іде після тих, від яких залежить.
     * За відсутності залежностей зберігається порядок із .sln.
     */
    private List<SolutionProject> sortByDependencies(List<SolutionProject> projects,
                                                     Map<String, SolutionProject> projectsByGuid) {
        List<SolutionProject> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> inProgress = new HashSet<>();
        for (SolutionProject project : projects) {
            visit(project, projectsByGuid, visited, inProgress, sorted);
        }
        return sorted;
    }

    private void visit(SolutionProject project, Map<String, SolutionProject> projectsByGuid,
                       Set<String> visited, Set<String> inProgress, List<SolutionProject> sorted) {
        if (visited.contains(project.guid())) {
            return;
        }
        if (!inProgress.add(project.guid())) {
            throw new ValidationException("Циклічна залежність між проєктами, зокрема «" + project.name() + "»");
        }
        for (String guid : project.dependencyGuids()) {
            SolutionProject dependency = projectsByGuid.get(guid);
            if (dependency != null) {
                visit(dependency, projectsByGuid, visited, inProgress, sorted);
            }
        }
        inProgress.remove(project.guid());
        visited.add(project.guid());
        sorted.add(project);
    }

    private static Target newTarget(String name, String description) {
        Target target = new Target();
        target.setName(name);
        target.setDescription(description);
        return target;
    }
}
