package ua.kpi.trpz.nantbuilder.service.validation;

/**
 * Проблема, знайдена обробником ланцюжка перевірки.
 *
 * @param severity рівень проблеми
 * @param element  елемент скрипта, якого вона стосується, наприклад {@code <target> build}
 * @param message  повне повідомлення для користувача
 */
public record ValidationIssue(Severity severity, String element, String message) {

    /** Створює помилку. */
    public static ValidationIssue error(String element, String message) {
        return new ValidationIssue(Severity.ERROR, element, message);
    }

    /** Створює попередження. */
    public static ValidationIssue warning(String element, String message) {
        return new ValidationIssue(Severity.WARNING, element, message);
    }
}
