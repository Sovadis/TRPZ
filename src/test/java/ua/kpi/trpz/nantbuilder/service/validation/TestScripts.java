package ua.kpi.trpz.nantbuilder.service.validation;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import java.util.ArrayList;
import java.util.List;

/** Допоміжні методи тестів ланцюжка перевірки. */
final class TestScripts {

    private TestScripts() {
    }

    /** Коректний скрипт без жодних проблем: clean ← compile ← build. */
    static BuildScript validScript() {
        BuildScript script = new BuildScript();
        script.setName("MyLibrary");
        script.setBaseDir(".");
        script.setDefaultTarget("build");
        script.addProperty(new ScriptProperty("configuration", "Debug"));

        Target clean = target("clean");
        clean.addTask(task(TaskType.DELETE, "dir", "bin"));
        Target compile = target("compile", "clean");
        BuildTask csc = task(TaskType.CSC, "target", "library");
        csc.setAttribute("output", "bin/MyLibrary.dll");
        compile.addTask(csc);
        Target build = target("build", "compile");
        build.addTask(task(TaskType.ECHO, "message", "Збірку завершено"));
        script.getTargets().addAll(List.of(clean, compile, build));
        return script;
    }

    static Target target(String name, String... dependsOn) {
        Target target = new Target();
        target.setName(name);
        target.getDependsOn().addAll(List.of(dependsOn));
        return target;
    }

    static BuildTask task(TaskType type, String attribute, String value) {
        BuildTask task = BuildTask.withDefaults(type);
        task.setAttribute(attribute, value);
        return task;
    }

    /** Запускає один обробник (без наступних) і повертає знайдені проблеми. */
    static List<ValidationIssue> run(ValidationHandler handler, BuildScript script) {
        List<ValidationIssue> issues = new ArrayList<>();
        handler.handle(script, issues);
        return issues;
    }

    static List<String> messages(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::message).toList();
    }

    /** Обробник-заглушка, що лише запам'ятовує, чи дійшов до нього запит. */
    static final class RecordingHandler extends ValidationHandler {
        int calls;

        @Override
        protected boolean check(BuildScript script, List<ValidationIssue> issues) {
            calls++;
            return true;
        }
    }
}
