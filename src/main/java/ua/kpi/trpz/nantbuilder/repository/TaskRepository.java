package ua.kpi.trpz.nantbuilder.repository;

import ua.kpi.trpz.nantbuilder.model.BuildTask;

import java.util.List;

/**
 * Репозиторій завдань (таблиці {@code task} та {@code task_attribute}).
 */
public interface TaskRepository extends Repository<BuildTask, Long> {

    /** Повертає завдання цілі, впорядковані за позицією, разом з атрибутами. */
    List<BuildTask> findByTargetId(long targetId);
}
