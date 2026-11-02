package ua.kpi.trpz.nantbuilder.repository;

import ua.kpi.trpz.nantbuilder.model.Target;

import java.util.List;

/**
 * Репозиторій цілей (таблиці {@code target} та {@code target_dependency}).
 */
public interface TargetRepository extends Repository<Target, Long> {

    /** Повертає цілі скрипта, впорядковані за позицією (завдання не завантажуються). */
    List<Target> findByScriptId(long scriptId);

    /** Видаляє всі цілі скрипта (разом із їхніми завданнями та залежностями). */
    void deleteByScriptId(long scriptId);

    /** Замінює залежності цілі заданим списком ідентифікаторів цілей. */
    void saveDependencies(long targetId, List<Long> dependsOnIds);

    /** Повертає ідентифікатори цілей, від яких залежить ціль, у порядку збереження. */
    List<Long> findDependencyIds(long targetId);
}
