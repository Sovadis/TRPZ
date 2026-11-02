package ua.kpi.trpz.nantbuilder.service;

/**
 * Помилка перевірки даних користувача (порожня або повторювана назва, некоректний XML тощо).
 * Повідомлення винятку показується користувачу.
 */
public class ValidationException extends RuntimeException {

    /** Створює виняток із повідомленням для користувача. */
    public ValidationException(String message) {
        super(message);
    }

    /** Створює виняток із повідомленням для користувача і причиною. */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
