package ua.kpi.trpz.nantbuilder.repository.cache;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Лічильники роботи кешу; повідомляє слухачів про зміни. Потокобезпечний.
 * Слухачі викликаються синхронно в тому потоці, який змінив лічильник.
 */
public class CacheStatistics {

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** Запит обслужено з кешу. */
    public void recordHit() {
        hits.incrementAndGet();
        fireChanged();
    }

    /** Запит передано реальному репозиторію (БД). */
    public void recordMiss() {
        misses.incrementAndGet();
        fireChanged();
    }

    /** Кеш скинуто після save/deleteById. */
    public void recordInvalidation() {
        invalidations.incrementAndGet();
        fireChanged();
    }

    /** Кількість запитів, обслужених з кешу. */
    public long getHits() {
        return hits.get();
    }

    /** Кількість запитів, переданих реальному репозиторію. */
    public long getMisses() {
        return misses.get();
    }

    /** Кількість скидань кешу. */
    public long getInvalidations() {
        return invalidations.get();
    }

    /** Підписує слухача, який викликається після кожної зміни лічильників. */
    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Відписує слухача. */
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
