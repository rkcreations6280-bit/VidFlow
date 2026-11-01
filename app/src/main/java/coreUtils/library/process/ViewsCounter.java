package coreUtils.library.process;

import java.util.Objects;
import java.util.TreeMap;

public class ViewsCounter {

    private static final TreeMap<Long, String> SUFFIXES = new TreeMap<>();

    static {
        SUFFIXES.put(1_000L, "K");
        SUFFIXES.put(1_000_000L, "M");
        SUFFIXES.put(1_000_000_000L, "B");
        SUFFIXES.put(1_000_000_000_000L, "T");
    }

    public static String formatViewsCount(long count) {
        if (count < 1000) return String.valueOf(count);
        var entry = SUFFIXES.floorEntry(count);
        Long divider = Objects.requireNonNull(entry).getKey();

        String suffix = entry.getValue();
        long value = count / divider;
        long remainder = (count % divider) / (divider / 10);

        if (remainder == 0) return value + suffix;
        return value + "." + remainder + suffix;
    }
}