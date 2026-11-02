package ua.kpi.trpz.nantbuilder.repository;

import java.util.List;
import java.util.Optional;

/**
 * Узагальнений репозиторій (шаблон Repository): ізолює логіку програми від способу зберігання даних.
 *
 * @param <T>  тип сутності
 * @param <ID> тип ідентифікатора
 */
public interface Repository<T, ID> {

    /** Шукає сутність за ідентифікатором. */
    Optional<T> findById(ID id);

    /** Повертає всі сутності. */
    List<T> findAll();

    /** Зберігає сутність: INSERT, якщо id == null (і встановлює згенерований id), інакше UPDATE. */
    T save(T entity);

    /** Видаляє сутність за ідентифікатором. */
    void deleteById(ID id);
}
