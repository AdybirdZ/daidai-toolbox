package org.daidai.modules.ballrace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 小球颜色目录与不重复的参赛名单。 */
public final class BallRoster {

    public static final class ColorSpec {
        public final String id;
        public final String resourceKey;
        public final int color;

        private ColorSpec(String id, String resourceKey, int color) {
            this.id = id;
            this.resourceKey = resourceKey;
            this.color = color;
        }
    }

    private static final ArrayList<ColorSpec> COLORS = new ArrayList<>(Arrays.asList(
        color("red_light", 0xFFFCA5A5), color("red", 0xFFEF4444), color("red_dark", 0xFF991B1B),
        color("orange_light", 0xFFFDBA74), color("orange", 0xFFF97316), color("orange_dark", 0xFFC2410C),
        color("yellow_light", 0xFFFDE68A), color("yellow", 0xFFEAB308), color("yellow_dark", 0xFFB7791F),
        color("green_light", 0xFF86EFAC), color("green", 0xFF22C55E), color("green_dark", 0xFF166534),
        color("blue_light", 0xFF93C5FD), color("blue", 0xFF3B82F6), color("blue_dark", 0xFF1E3A8A),
        color("purple_light", 0xFFD8B4FE), color("purple", 0xFFA855F7), color("purple_dark", 0xFF6B21A8),
        color("pink_light", 0xFFF9A8D4), color("pink", 0xFFEC4899), color("pink_dark", 0xFF9D174D),
        color("blue_green_light", 0xFF99F6E4), color("blue_green", 0xFF14B8A6), color("blue_green_dark", 0xFF0F766E),
        color("turquoise_light", 0xFF67E8F9), color("turquoise", 0xFF06B6D4), color("turquoise_dark", 0xFF155E75),
        color("black", 0xFF111827), color("white", 0xFFFFFFFF),
        color("gray_light", 0xFFD1D5DB), color("gray_dark", 0xFF4B5563)
    ));
    private static final List<String> DEFAULT_IDS = Collections.unmodifiableList(Arrays.asList(
        "red", "orange", "yellow", "green", "blue", "purple"));

    private BallRoster() {
        throw new AssertionError();
    }

    private static ColorSpec color(String id, int value) {
        return new ColorSpec(id, "color_" + id, value);
    }

    public static List<ColorSpec> colors() { return Collections.unmodifiableList(COLORS); }

    public static void registerCustom(String id, String name, int color) {
        if (id == null || !id.matches("custom-[A-Za-z0-9_-]{1,56}") || name == null || name.trim().isEmpty()) return;
        for (ColorSpec item : COLORS) if (item.id.equals(id)) return;
        COLORS.add(new ColorSpec(id, "custom:" + name.trim(), color));
    }

    public static List<String> defaultIds() { return DEFAULT_IDS; }

    public static ColorSpec find(String id) {
        if (id == null) return null;
        for (ColorSpec color : COLORS) if (color.id.equals(id)) return color;
        return null;
    }

    public static int complementaryContrastColor(int color) {
        float red = ((color >>> 16) & 255) / 255f;
        float green = ((color >>> 8) & 255) / 255f;
        float blue = (color & 255) / 255f;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        float delta = maximum - minimum;
        float hue;
        if (delta == 0f) hue = 0f;
        else if (maximum == red) hue = 60f * (((green - blue) / delta) % 6f);
        else if (maximum == green) hue = 60f * ((blue - red) / delta + 2f);
        else hue = 60f * ((red - green) / delta + 4f);
        if (hue < 0f) hue += 360f;
        float saturation = maximum == 0f ? 0f : delta / maximum;
        if (saturation < .12f) {
            int gray = Math.round((1f - (red * .299f + green * .587f + blue * .114f)) * 255f);
            return 0xFF000000 | (gray << 16) | (gray << 8) | gray;
        }
        float value = Math.max(1f - maximum, .78f);
        hue = (hue + 180f) % 360f;
        float chroma = value * saturation;
        float section = hue / 60f;
        float secondary = chroma * (1f - Math.abs(section % 2f - 1f));
        float offset = value - chroma;
        float outRed, outGreen, outBlue;
        if (section < 1f) { outRed = chroma; outGreen = secondary; outBlue = 0f; }
        else if (section < 2f) { outRed = secondary; outGreen = chroma; outBlue = 0f; }
        else if (section < 3f) { outRed = 0f; outGreen = chroma; outBlue = secondary; }
        else if (section < 4f) { outRed = 0f; outGreen = secondary; outBlue = chroma; }
        else if (section < 5f) { outRed = secondary; outGreen = 0f; outBlue = chroma; }
        else { outRed = chroma; outGreen = 0f; outBlue = secondary; }
        return 0xFF000000 | (Math.round((outRed + offset) * 255f) << 16)
            | (Math.round((outGreen + offset) * 255f) << 8) | Math.round((outBlue + offset) * 255f);
    }

    public static List<String> normalize(Collection<String> ids) {
        ArrayList<String> result = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                if (find(id) != null && !result.contains(id)) result.add(id);
            }
        }
        return result;
    }

    public static List<String> parse(String value, List<String> fallback) {
        if (value == null || value.trim().isEmpty()) return new ArrayList<>(fallback);
        List<String> result = normalize(Arrays.asList(value.split(",", -1)));
        return result.isEmpty() ? new ArrayList<>(fallback) : result;
    }

    public static String serialize(Collection<String> ids) {
        StringBuilder value = new StringBuilder();
        for (String id : normalize(ids)) {
            if (value.length() > 0) value.append(',');
            value.append(id);
        }
        return value.toString();
    }

    public static boolean add(List<String> ids, String id) {
        if (find(id) == null || ids.contains(id)) return false;
        ids.add(id);
        return true;
    }

    public static boolean remove(List<String> ids, String id) {
        if (ids.size() <= 2) return false;
        return ids.remove(id);
    }
}
