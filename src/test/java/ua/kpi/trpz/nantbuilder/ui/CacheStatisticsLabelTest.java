package ua.kpi.trpz.nantbuilder.ui;

import org.junit.jupiter.api.Test;
import ua.kpi.trpz.nantbuilder.repository.cache.CacheStatistics;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheStatisticsLabelTest {

    @Test
    void showsCurrentCountersAndFollowsChanges() throws Exception {
        CacheStatistics statistics = new CacheStatistics();
        statistics.recordMiss();
        AtomicReference<CacheStatisticsLabel> label = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> label.set(new CacheStatisticsLabel(statistics)));
        assertEquals("Кеш скриптів: з кешу 0, запитів до БД 1", textOf(label.get()));

        // зміна в потоці обробки подій оновлює мітку одразу
        SwingUtilities.invokeAndWait(() -> {
            statistics.recordHit();
            assertEquals("Кеш скриптів: з кешу 1, запитів до БД 1", label.get().getText());
        });

        // зміни з іншого потоку потрапляють у мітку через чергу подій Swing
        statistics.recordHit();
        statistics.recordMiss();
        statistics.recordInvalidation();
        assertEquals("Кеш скриптів: з кешу 2, запитів до БД 2", textOf(label.get()));
        AtomicReference<String> toolTip = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> toolTip.set(label.get().getToolTipText()));
        assertTrue(toolTip.get().endsWith("скидань кешу після змін: 1"), toolTip.get());
    }

    /** Читає текст у EDT; до цього моменту всі раніше поставлені в чергу оновлення вже виконано. */
    private static String textOf(CacheStatisticsLabel label) throws Exception {
        AtomicReference<String> text = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> text.set(label.getText()));
        return text.get();
    }
}
