package ua.kpi.trpz.nantbuilder.repository.cache;

import ua.kpi.trpz.nantbuilder.model.BuildScript;
import ua.kpi.trpz.nantbuilder.repository.BuildScriptRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Кешуючий замісник (Proxy) репозиторію заголовків скриптів.
 * <p>
 * Реалізує той самий інтерфейс {@link BuildScriptRepository}, що й реальний репозиторій, тому клієнти
 * (наприклад, {@code ScriptService}) не змінюються. Перший виклик {@link #findAll()} читає список з
 * реального репозиторію і запам'ятовує його; наступні читання обслуговуються з пам'яті.
 * {@link #save(BuildScript)} і {@link #deleteById(Long)} передаються реальному репозиторію, після чого
 * кеш скидається. Методи читання завжди повертають копії заголовків, тому зміни в отриманих об'єктах
 * не псують кеш.
 * <p>
 * Транзакції: {@code ScriptService} викликає {@code save} лише всередині {@code Database.inTransaction}
 * і після цього в тій самій транзакції заголовки не читає. Тому достатньо скидати кеш під час
 * {@code save}: якщо транзакцію буде відкочено, кеш уже порожній і наступне читання піде до БД, яка
 * поверне стан після відкату.
 */
public class CachingBuildScriptRepository implements BuildScriptRepository {

    private final BuildScriptRepository target;
    private final CacheStatistics statistics;
    /** Список заголовків у порядку реального репозиторію; {@code null} — кеш порожній. */
    private List<BuildScript> cache;

    /**
     * Створює замісник для реального репозиторію.
     *
     * @param target     реальний репозиторій, якому передаються запити (RealSubject)
     * @param statistics лічильники звернень до кешу
     */
    public CachingBuildScriptRepository(BuildScriptRepository target, CacheStatistics statistics) {
        this.target = Objects.requireNonNull(target, "target");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
    }

    /** Повертає всі заголовки: перший раз — з реального репозиторію, далі — з кешу. */
    @Override
    public synchronized List<BuildScript> findAll() {
        if (cache == null) {
            statistics.recordMiss();
            cache = copyAll(target.findAll());
        } else {
            statistics.recordHit();
        }
        return copyAll(cache);
    }

    /** Шукає заголовок у кеші, якщо список уже завантажено, інакше передає запит реальному репозиторію. */
    @Override
    public synchronized Optional<BuildScript> findById(Long id) {
        if (cache == null) {
            statistics.recordMiss();
            return target.findById(id).map(CachingBuildScriptRepository::copyOf);
        }
        statistics.recordHit();
        return findCached(script -> Objects.equals(script.getId(), id));
    }

    /** Шукає заголовок за назвою (з урахуванням регістру, як і запит до БД). */
    @Override
    public synchronized Optional<BuildScript> findByName(String name) {
        if (cache == null) {
            statistics.recordMiss();
            return target.findByName(name).map(CachingBuildScriptRepository::copyOf);
        }
        statistics.recordHit();
        return findCached(script -> Objects.equals(script.getName(), name));
    }

    /**
     * Передає збереження реальному репозиторію і скидає кеш (навіть якщо збереження не вдалося).
     * Повертає результат реального репозиторію — переданий об'єкт зі встановленим id.
     */
    @Override
    public synchronized BuildScript save(BuildScript entity) {
        try {
            return target.save(entity);
        } finally {
            invalidate();
        }
    }

    /** Передає видалення реальному репозиторію і скидає кеш. */
    @Override
    public synchronized void deleteById(Long id) {
        try {
            target.deleteById(id);
        } finally {
            invalidate();
        }
    }

    /** Скидає кеш: наступне читання буде передано реальному репозиторію. */
    public synchronized void invalidate() {
        cache = null;
        statistics.recordInvalidation();
    }

    private Optional<BuildScript> findCached(Predicate<BuildScript> condition) {
        return cache.stream()
                .filter(condition)
                .findFirst()
                .map(CachingBuildScriptRepository::copyOf);
    }

    private static List<BuildScript> copyAll(List<BuildScript> scripts) {
        List<BuildScript> copies = new ArrayList<>(scripts.size());
        for (BuildScript script : scripts) {
            copies.add(copyOf(script));
        }
        return copies;
    }

    /** Копія заголовка скрипта (без властивостей і цілей, як і в реальному репозиторії). */
    private static BuildScript copyOf(BuildScript source) {
        BuildScript copy = new BuildScript();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setDefaultTarget(source.getDefaultTarget());
        copy.setBaseDir(source.getBaseDir());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
