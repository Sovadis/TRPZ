package ua.kpi.trpz.nantbuilder.repository;

import ua.kpi.trpz.nantbuilder.model.BuildScript;

import java.util.Optional;

/**
 * Репозиторій заголовків скриптів збірки (таблиця {@code build_script}).
 * Методи повертають лише заголовок скрипта, без властивостей і цілей.
 */
public interface BuildScriptRepository extends Repository<BuildScript, Long> {

    /** Шукає скрипт за унікальною назвою (лише заголовок). */
    Optional<BuildScript> findByName(String name);
}
