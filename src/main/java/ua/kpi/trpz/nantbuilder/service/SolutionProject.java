package ua.kpi.trpz.nantbuilder.service;

import java.util.List;

/**
 * Проєкт рішення Visual Studio, прочитаний із файлу .sln.
 *
 * @param name            назва проєкту
 * @param relativePath    шлях до файлу проєкту відносно каталогу рішення (як у .sln)
 * @param guid            GUID проєкту
 * @param dependencyGuids GUID проєктів, від яких залежить цей проєкт
 */
public record SolutionProject(String name, String relativePath, String guid, List<String> dependencyGuids) {
}
