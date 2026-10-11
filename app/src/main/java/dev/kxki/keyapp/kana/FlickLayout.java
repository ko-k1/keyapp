package dev.kxki.keyapp.kana;

/**
 * Abstraction over 12-key flick arrangements.
 *
 * <p>{@link FlickMap} is the shipped Japanese arrangement. Future layouts (e.g. a
 * Godan or symbol flick panel) implement this interface and are injected where a
 * static {@code FlickMap} call used to sit, so new languages do not require touching
 * the view.
 *
 * <p>Instance method names avoid clashing with {@link FlickMap}'s legacy static
 * helpers ({@code flickRow}/{@code flickIndex}/{@code resolveFlick}).
 */
public interface FlickLayout {
    /** Row for {@code base}, or {@code null} when the key has no flick variants. */
    String[] row(String base);

    int index(float dx, float dy, float thresholdPx);

    default String resolve(String base, float dx, float dy, float thresholdPx) {
        String[] row = row(base);
        return row == null ? base : row[index(dx, dy, thresholdPx)];
    }
}
