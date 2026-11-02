package ua.kpi.trpz.nantbuilder.repository;

import ua.kpi.trpz.nantbuilder.model.ScriptProperty;

import java.util.List;

/**
 * Репозиторій властивостей скриптів (таблиця {@code script_property}).
 */
public interface PropertyRepository extends Repository<ScriptProperty, Long> {

    /** Повертає властивості скрипта в порядку додавання. */
    List<ScriptProperty> findByScriptId(long scriptId);

    /** Видаляє всі властивості скрипта. */
    void deleteByScriptId(long scriptId);
}
