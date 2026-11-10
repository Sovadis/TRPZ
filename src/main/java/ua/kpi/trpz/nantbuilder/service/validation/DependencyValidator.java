package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.Target;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Конкретний обробник (ConcreteHandler): перевіряє залежності цілей — повторювані, невідомі,
 * залежність від самої себе — і шукає циклічні залежності (пошук у глибину).
 */
public class DependencyValidator extends ValidationHandler {

    @Override
    protected boolean check(BuildScript script, List<ValidationIssue> issues) {
        Set<String> targetNames = new HashSet<>();
        for (Target target : script.getTargets()) {
            targetNames.add(target.getName());
        }

        // граф коректних залежностей (у порядку оголошення цілей) для пошуку циклів
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Target target : script.getTargets()) {
            String name = target.getName();
            String element = "<target> " + name;
            List<String> dependencies = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String dependency : target.getDependsOn()) {
                if (!seen.add(dependency)) {
                    // про невідому ціль чи залежність від самої себе вже повідомлено — повтор не дублюємо
                    if (dependencies.contains(dependency)) {
                        issues.add(ValidationIssue.error(element,
                                "Ціль «" + name + "» двічі залежить від «" + dependency + "»"));
                    }
                } else if (!targetNames.contains(dependency)) {
                    issues.add(ValidationIssue.error(element,
                            "Ціль «" + name + "» залежить від невідомої цілі «" + dependency + "»"));
                } else if (dependency.equals(name)) {
                    issues.add(ValidationIssue.error(element, "Ціль «" + name + "» не може залежати від самої себе"));
                } else {
                    dependencies.add(dependency);
                }
            }
            graph.put(name, dependencies);
        }

        new CycleSearch(graph, issues).run();
        return true;
    }

    /** Пошук у глибину: ребро до цілі, яку ще обходимо, замикає цикл. */
    private static final class CycleSearch {
        private final Map<String, List<String>> graph;
        private final List<ValidationIssue> issues;
        private final Set<String> visited = new HashSet<>();
        private final Set<String> inProgress = new HashSet<>();
        /** Поточний шлях обходу від початкової цілі. */
        private final List<String> path = new ArrayList<>();

        CycleSearch(Map<String, List<String>> graph, List<ValidationIssue> issues) {
            this.graph = graph;
            this.issues = issues;
        }

        void run() {
            for (String name : graph.keySet()) {
                visit(name);
            }
        }

        private void visit(String name) {
            if (visited.contains(name)) {
                return;
            }
            inProgress.add(name);
            path.add(name);
            for (String dependency : graph.get(name)) {
                if (inProgress.contains(dependency)) {
                    List<String> cycle = new ArrayList<>(path.subList(path.indexOf(dependency), path.size()));
                    cycle.add(dependency);
                    issues.add(ValidationIssue.error("<target> " + dependency,
                            "Циклічна залежність цілей: " + String.join(" → ", cycle)));
                } else {
                    visit(dependency);
                }
            }
            path.remove(path.size() - 1);
            inProgress.remove(name);
            visited.add(name);
        }
    }
}
