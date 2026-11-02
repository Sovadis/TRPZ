package ua.kpi.trpz.nantbuilder.ui;

import org.fife.ui.autocomplete.Completion;
import org.fife.ui.autocomplete.CompletionCellRenderer;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.TemplateCompletion;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Постачальник автодоповнення для XML-редактора NAnt.
 * Після символу {@code <} пропонує теги NAnt (із шаблонами атрибутів),
 * усередині тегу — атрибути саме цього тегу. Виклик вручну — Ctrl+Space.
 */
public final class NantCompletionProvider {

    /** Опис атрибутів, які показуються у списку підказок. */
    private static final Map<String, String> ATTRIBUTE_HINTS = Map.ofEntries(
            Map.entry("name", "назва"),
            Map.entry("value", "значення"),
            Map.entry("readonly", "лише для читання (true/false)"),
            Map.entry("default", "ціль за замовчуванням"),
            Map.entry("basedir", "базова директорія"),
            Map.entry("depends", "цілі-залежності через кому"),
            Map.entry("description", "опис"),
            Map.entry("message", "текст повідомлення"),
            Map.entry("dir", "каталог"),
            Map.entry("file", "файл"),
            Map.entry("todir", "каталог призначення"),
            Map.entry("project", "файл проєкту"),
            Map.entry("target", "тип результату (exe, library)"),
            Map.entry("output", "вихідний файл"),
            Map.entry("program", "програма, яку запустити"),
            Map.entry("commandline", "аргументи командного рядка"),
            Map.entry("failonerror", "зупинити збірку при помилці (true/false)"));

    private NantCompletionProvider() {
    }

    private static String taskHint(TaskType type) {
        return switch (type) {
            case ECHO -> "вивести повідомлення";
            case MKDIR -> "створити каталог";
            case DELETE -> "видалити файли або каталог";
            case COPY -> "копіювати файли";
            case MSBUILD -> "зібрати проєкт MSBuild";
            case CSC -> "скомпілювати код C#";
            case EXEC -> "запустити програму";
        };
    }

    /** Створює постачальника підказок для тегів і атрибутів NAnt. */
    public static CompletionProvider create() {
        return new ContextCompletionProvider();
    }

    /**
     * Постачальник, що враховує контекст курсора: назва тегу чи атрибут усередині тегу.
     */
    private static class ContextCompletionProvider extends DefaultCompletionProvider {

        private final List<Completion> tagCompletions = new ArrayList<>();
        private final Map<String, List<Completion>> attributeCompletions = new LinkedHashMap<>();

        ContextCompletionProvider() {
            // показує в списку не лише назву, а й короткий опис підказки
            setListCellRenderer(new CompletionCellRenderer());
            addTag("project", "кореневий елемент скрипта",
                    "project name=\"${name}\" default=\"${build}\" basedir=\".\">\n  ${cursor}\n</project>",
                    "name", "default", "basedir");
            addTag("description", "опис скрипта", "description>${cursor}</description>");
            addTag("property", "властивість скрипта",
                    "property name=\"${name}\" value=\"${value}\" />${cursor}",
                    "name", "value", "readonly");
            addTag("target", "ціль збірки",
                    "target name=\"${name}\" depends=\"${depends}\">\n  ${cursor}\n</target>",
                    "name", "depends", "description");
            for (TaskType type : TaskType.values()) {
                StringBuilder template = new StringBuilder(type.getXmlName());
                for (String attribute : type.getDefaultAttributes()) {
                    template.append(' ').append(attribute).append("=\"${").append(attribute).append("}\"");
                }
                template.append(" />${cursor}");
                List<String> attributes = new ArrayList<>(type.getDefaultAttributes());
                attributes.add("failonerror");
                addTag(type.getXmlName(), "завдання: " + taskHint(type), template.toString(),
                        attributes.toArray(new String[0]));
            }
        }

        private void addTag(String tag, String hint, String template, String... attributes) {
            tagCompletions.add(new TemplateCompletion(this, tag, tag, template, hint, null));
            List<Completion> forTag = new ArrayList<>();
            for (String attribute : attributes) {
                forTag.add(new TemplateCompletion(this, attribute, attribute, attribute + "=\"${cursor}\"",
                        ATTRIBUTE_HINTS.getOrDefault(attribute, "атрибут"), null));
            }
            attributeCompletions.put(tag, forTag);
        }

        /** Спливаюче вікно відкривається автоматично після '<' та після пробілу всередині тегу. */
        @Override
        public boolean isAutoActivateOkay(JTextComponent comp, int offset) {
            try {
                char typed = comp.getDocument().getText(offset, 1).charAt(0);
                return typed == '<' || (typed == ' ' && currentTagName(textBefore(comp, offset + 1)) != null);
            } catch (BadLocationException e) {
                return false;
            }
        }

        @Override
        protected List<Completion> getCompletionsImpl(JTextComponent comp) {
            String entered = getAlreadyEnteredText(comp);
            String before = textBefore(comp, comp.getCaretPosition() - entered.length());

            List<Completion> candidates;
            if (before.endsWith("<")) {
                candidates = tagCompletions;
            } else {
                String tag = currentTagName(before);
                candidates = tag == null ? List.of() : attributeCompletions.getOrDefault(tag, List.of());
            }

            List<Completion> result = new ArrayList<>();
            for (Completion completion : candidates) {
                if (completion.getInputText().regionMatches(true, 0, entered, 0, entered.length())) {
                    result.add(completion);
                }
            }
            return result;
        }

        /**
         * Повертає назву тегу, всередині якого (між назвою та '>') стоїть курсор,
         * або null, якщо курсор поза тегом чи всередині значення атрибута.
         */
        private static String currentTagName(String before) {
            int open = before.lastIndexOf('<');
            if (open < 0 || before.lastIndexOf('>') > open) {
                return null;
            }
            String inside = before.substring(open + 1);
            long quotes = inside.chars().filter(ch -> ch == '"').count();
            if (quotes % 2 != 0 || inside.isEmpty() || !Character.isWhitespace(inside.charAt(inside.length() - 1))) {
                return null;
            }
            String[] parts = inside.trim().split("\\s+", 2);
            return parts[0].isEmpty() ? null : parts[0];
        }

        private static String textBefore(JTextComponent comp, int end) {
            try {
                return comp.getDocument().getText(0, Math.max(0, end));
            } catch (BadLocationException e) {
                return "";
            }
        }
    }
}
