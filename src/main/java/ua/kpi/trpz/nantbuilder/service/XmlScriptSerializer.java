package ua.kpi.trpz.nantbuilder.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.model.BuildTask;
import ua.kpi.trpz.nantbuilder.model.ScriptProperty;
import ua.kpi.trpz.nantbuilder.model.Target;
import ua.kpi.trpz.nantbuilder.model.TaskType;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Перетворює скрипт збірки на текст NAnt XML і навпаки.
 */
public class XmlScriptSerializer {

    private static final String INDENT = "  ";

    /** Формує текст NAnt XML (відступ — два пробіли). */
    public String toXml(BuildScript script) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");

        xml.append("<project");
        appendAttribute(xml, "name", script.getName());
        if (isNotBlank(script.getDefaultTarget())) {
            appendAttribute(xml, "default", script.getDefaultTarget());
        }
        appendAttribute(xml, "basedir", isNotBlank(script.getBaseDir()) ? script.getBaseDir() : ".");
        xml.append(">\n");

        if (isNotBlank(script.getDescription())) {
            xml.append(INDENT).append("<description>").append(escape(script.getDescription()))
                    .append("</description>\n");
        }
        for (ScriptProperty property : script.getProperties()) {
            xml.append(INDENT).append("<property");
            appendAttribute(xml, "name", property.getName());
            appendAttribute(xml, "value", property.getValue());
            if (property.isReadOnly()) {
                appendAttribute(xml, "readonly", "true");
            }
            xml.append(" />\n");
        }
        for (Target target : script.getTargets()) {
            appendTarget(xml, target);
        }

        xml.append("</project>\n");
        return xml.toString();
    }

    /**
     * Розбирає текст NAnt XML (DOM) і будує з нього скрипт без ідентифікаторів.
     *
     * @throws ValidationException якщо XML некоректний або містить невідоме завдання
     */
    public BuildScript fromXml(String xml) {
        Element root = parse(xml).getDocumentElement();
        if (!"project".equals(root.getTagName())) {
            throw new ValidationException("Кореневим елементом має бути <project>, а знайдено <" + root.getTagName() + ">");
        }

        BuildScript script = new BuildScript();
        script.setName(requiredAttribute(root, "name"));
        script.setDefaultTarget(optionalAttribute(root, "default"));
        String baseDir = optionalAttribute(root, "basedir");
        script.setBaseDir(baseDir == null ? "." : baseDir);

        for (Element child : childElements(root)) {
            switch (child.getTagName()) {
                case "description" -> script.setDescription(child.getTextContent().trim());
                case "property" -> script.addProperty(readProperty(child));
                case "target" -> script.addTarget(readTarget(child));
                default -> throw new ValidationException(
                        "Елемент <" + child.getTagName() + "> не підтримується всередині <project>");
            }
        }
        return script;
    }

    private void appendTarget(StringBuilder xml, Target target) {
        xml.append(INDENT).append("<target");
        appendAttribute(xml, "name", target.getName());
        if (!target.getDependsOn().isEmpty()) {
            appendAttribute(xml, "depends", String.join(",", target.getDependsOn()));
        }
        if (isNotBlank(target.getDescription())) {
            appendAttribute(xml, "description", target.getDescription());
        }
        if (target.getTasks().isEmpty()) {
            xml.append(" />\n");
            return;
        }
        xml.append(">\n");
        for (BuildTask task : target.getTasks()) {
            xml.append(INDENT).append(INDENT).append('<').append(task.getType().getXmlName());
            for (Map.Entry<String, String> attribute : task.getAttributes().entrySet()) {
                appendAttribute(xml, attribute.getKey(), attribute.getValue());
            }
            xml.append(" />\n");
        }
        xml.append(INDENT).append("</target>\n");
    }

    private ScriptProperty readProperty(Element element) {
        ScriptProperty property = new ScriptProperty(requiredAttribute(element, "name"), element.getAttribute("value"));
        property.setReadOnly("true".equalsIgnoreCase(element.getAttribute("readonly")));
        return property;
    }

    private Target readTarget(Element element) {
        Target target = new Target();
        target.setName(requiredAttribute(element, "name"));
        target.setDescription(optionalAttribute(element, "description"));
        Arrays.stream(element.getAttribute("depends").split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .forEach(target.getDependsOn()::add);
        for (Element child : childElements(element)) {
            target.addTask(readTask(child, target.getName()));
        }
        return target;
    }

    private BuildTask readTask(Element element, String targetName) {
        TaskType type;
        try {
            type = TaskType.fromXmlName(element.getTagName());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                    "Невідоме завдання <" + element.getTagName() + "> у цілі «" + targetName + "»", e);
        }
        if (!childElements(element).isEmpty()) {
            throw new ValidationException("Вкладені елементи в <" + type.getXmlName() + "> не підтримуються");
        }

        BuildTask task = new BuildTask();
        task.setType(type);
        // DOM не гарантує порядок атрибутів, тому спершу йдуть стандартні атрибути типу, потім решта
        for (String name : type.getDefaultAttributes()) {
            if (element.hasAttribute(name)) {
                task.setAttribute(name, element.getAttribute(name));
            }
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            if (task.getAttribute(attribute.getNodeName()) == null) {
                task.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
            }
        }
        return task;
    }

    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setIgnoringComments(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // без власного обробника парсер друкує помилки в System.err
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            throw new ValidationException("Некоректний XML (рядок " + e.getLineNumber() + "): " + e.getMessage(), e);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new ValidationException("Некоректний XML: " + e.getMessage(), e);
        }
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name).trim();
        if (value.isEmpty()) {
            throw new ValidationException("Елемент <" + element.getTagName() + "> повинен мати атрибут " + name);
        }
        return value;
    }

    private static String optionalAttribute(Element element, String name) {
        String value = element.getAttribute(name).trim();
        return value.isEmpty() ? null : value;
    }

    private static void appendAttribute(StringBuilder xml, String name, String value) {
        xml.append(' ').append(name).append("=\"").append(escape(value)).append('"');
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static boolean isNotBlank(String text) {
        return text != null && !text.isBlank();
    }
}
