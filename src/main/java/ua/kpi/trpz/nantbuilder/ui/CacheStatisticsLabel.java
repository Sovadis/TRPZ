package ua.kpi.trpz.nantbuilder.ui;

import ua.kpi.trpz.nantbuilder.repository.cache.CacheStatistics;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/**
 * Мітка рядка стану, що показує роботу кешу скриптів, наприклад «Кеш скриптів: з кешу 12, запитів до БД 3».
 * Підписується на зміни {@link CacheStatistics} і оновлює текст у потоці обробки подій Swing.
 */
public class CacheStatisticsLabel extends JLabel {

    private final CacheStatistics statistics;

    /** Створює мітку і підписує її на зміни лічильників. */
    public CacheStatisticsLabel(CacheStatistics statistics) {
        this.statistics = statistics;
        updateText();
        statistics.addListener(this::onStatisticsChanged);
    }

    /** Лічильники можуть змінюватися в будь-якому потоці, а компоненти Swing — лише в EDT. */
    private void onStatisticsChanged() {
        if (SwingUtilities.isEventDispatchThread()) {
            updateText();
        } else {
            SwingUtilities.invokeLater(this::updateText);
        }
    }

    private void updateText() {
        long hits = statistics.getHits();
        long misses = statistics.getMisses();
        setText("Кеш скриптів: з кешу " + hits + ", запитів до БД " + misses);
        setToolTipText("Читань заголовків скриптів, обслужених з кешу: " + hits
                + "; переданих до бази даних: " + misses
                + "; скидань кешу після змін: " + statistics.getInvalidations());
    }
}
