package ua.kpi.trpz.nantbuilder.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Аналізатор рішень Visual Studio: читає рядки {@code Project(...)} і секції
 * {@code ProjectDependencies} файлу .sln, а також {@code <ProjectReference>} у кожному файлі .csproj.
 */
public class SolutionParser {

    /** Project("{тип}") = "Назва", "шлях\до\проєкту.csproj", "{GUID}" */
    private static final Pattern PROJECT_LINE = Pattern.compile(
            "^Project\\(\"\\{[^}]+}\"\\)\\s*=\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*\"\\{([^}]+)}\"");
    /** {GUID} = {GUID} усередині ProjectSection(ProjectDependencies) */
    private static final Pattern DEPENDENCY_LINE = Pattern.compile("^\\{([^}]+)}\\s*=\\s*\\{[^}]+}");

    /**
     * Читає файл рішення та файли його проєктів.
     *
     * @throws IOException якщо файл рішення або проєкту не вдалося прочитати
     */
    public SolutionInfo parse(Path slnFile) throws IOException {
        Path solutionFile = slnFile.toAbsolutePath();
        Path directory = solutionFile.getParent();
        List<SolutionProject> projects = readSolutionFile(solutionFile);

        // шлях до файлу проєкту -> GUID, щоб розпізнати ProjectReference
        Map<Path, String> guidsByPath = new HashMap<>();
        for (SolutionProject project : projects) {
            guidsByPath.put(resolveProjectFile(directory, project), project.guid());
        }
        for (SolutionProject project : projects) {
            Path projectFile = resolveProjectFile(directory, project);
            for (Path referencedFile : readProjectReferences(projectFile)) {
                String guid = guidsByPath.get(referencedFile);
                if (guid != null && !project.dependencyGuids().contains(guid)) {
                    project.dependencyGuids().add(guid);
                }
            }
        }

        String fileName = solutionFile.getFileName().toString();
        String name = fileName.toLowerCase().endsWith(".sln") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return new SolutionInfo(name, directory, projects);
    }

    private List<SolutionProject> readSolutionFile(Path solutionFile) throws IOException {
        List<SolutionProject> projects = new ArrayList<>();
        SolutionProject current = null;
        boolean inDependencies = false;

        for (String rawLine : Files.readAllLines(solutionFile, StandardCharsets.UTF_8)) {
            String line = rawLine.replace("\uFEFF", "").trim();
            Matcher projectMatcher = PROJECT_LINE.matcher(line);
            if (projectMatcher.find()) {
                String path = projectMatcher.group(2);
                // папки рішення (solution folders) теж записані як Project(...), але не мають файлу *proj
                current = path.toLowerCase().endsWith("proj")
                        ? new SolutionProject(projectMatcher.group(1), path, projectMatcher.group(3).toUpperCase(),
                                              new ArrayList<>())
                        : null;
                if (current != null) {
                    projects.add(current);
                }
            } else if (line.startsWith("ProjectSection(ProjectDependencies)")) {
                inDependencies = true;
            } else if (line.equals("EndProjectSection")) {
                inDependencies = false;
            } else if (line.equals("EndProject")) {
                current = null;
                inDependencies = false;
            } else if (inDependencies && current != null) {
                Matcher dependencyMatcher = DEPENDENCY_LINE.matcher(line);
                if (dependencyMatcher.find()) {
                    current.dependencyGuids().add(dependencyMatcher.group(1).toUpperCase());
                }
            }
        }
        return projects;
    }

    /** Повертає нормалізовані шляхи файлів, на які посилаються елементи ProjectReference. */
    private List<Path> readProjectReferences(Path projectFile) throws IOException {
        List<Path> result = new ArrayList<>();
        if (!Files.isRegularFile(projectFile)) {
            return result;   // файлу проєкту немає — залежності беремо лише з .sln
        }
        NodeList references = parseXml(projectFile).getElementsByTagName("ProjectReference");
        for (int i = 0; i < references.getLength(); i++) {
            String include = ((Element) references.item(i)).getAttribute("Include");
            if (!include.isBlank()) {
                result.add(projectFile.getParent().resolve(toLocalPath(include)).normalize());
            }
        }
        return result;
    }

    private Document parseXml(Path file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(file.toFile());
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("Не вдалося розібрати файл проєкту " + file + ": " + e.getMessage(), e);
        }
    }

    private static Path resolveProjectFile(Path directory, SolutionProject project) {
        return directory.resolve(toLocalPath(project.relativePath())).normalize();
    }

    /** У .sln і .csproj використовується роздільник '\', перетворюємо його на роздільник поточної ОС. */
    private static String toLocalPath(String path) {
        return path.replace('\\', '/');
    }
}
