package ua.kpi.trpz.nantbuilder.repository.cache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheStatisticsTest {

    private final CacheStatistics statistics = new CacheStatistics();

    @Test
    void countersStartAtZero() {
        assertEquals(0, statistics.getHits());
        assertEquals(0, statistics.getMisses());
        assertEquals(0, statistics.getInvalidations());
    }

    @Test
    void eachRecordMethodIncrementsOnlyItsCounter() {
        statistics.recordHit();
        statistics.recordHit();
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordMiss();
        statistics.recordInvalidation();

        assertEquals(3, statistics.getHits());
        assertEquals(2, statistics.getMisses());
        assertEquals(1, statistics.getInvalidations());
    }

    @Test
    void listenersAreNotifiedAfterEveryChange() {
        List<Long> hitsSeenByListener = new ArrayList<>();
        AtomicInteger secondListenerCalls = new AtomicInteger();
        statistics.addListener(() -> hitsSeenByListener.add(statistics.getHits()));
        statistics.addListener(secondListenerCalls::incrementAndGet);

        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordInvalidation();

        // слухач бачить уже оновлене значення лічильника
        assertEquals(List.of(1L, 1L, 1L), hitsSeenByListener);
        assertEquals(3, secondListenerCalls.get());
    }

    @Test
    void removedListenerIsNotNotified() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;
        statistics.addListener(listener);
        statistics.recordHit();

        statistics.removeListener(listener);
        statistics.recordHit();
        statistics.recordMiss();

        assertEquals(1, calls.get());
        assertEquals(2, statistics.getHits());
    }

    @Test
    void nullListenerIsRejected() {
        assertThrows(NullPointerException.class, () -> statistics.addListener(null));
    }

    @Test
    void countersAreThreadSafe() throws InterruptedException {
        int threadCount = 8;
        int iterations = 5_000;
        AtomicInteger notifications = new AtomicInteger();
        statistics.addListener(notifications::incrementAndGet);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                for (int j = 0; j < iterations; j++) {
                    statistics.recordHit();
                    statistics.recordMiss();
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals((long) threadCount * iterations, statistics.getHits());
        assertEquals((long) threadCount * iterations, statistics.getMisses());
        assertEquals(2 * threadCount * iterations, notifications.get());
    }
}
