package ua.kpi.trpz.nantbuilder.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Результат аналізу рішення .sln: назва, каталог і проєкти із залежностями.
 *
 * @param name      назва рішення (ім'я файлу без розширення)
 * @param directory каталог, у якому лежить файл .sln
 * @param projects  проєкти в порядку їх появи у .sln
 */
public record SolutionInfo(String name, Path directory, List<SolutionProject> projects) {
}
