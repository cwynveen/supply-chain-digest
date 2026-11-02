package digest;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small text helpers: cleaning feed HTML, escaping output, and parsing feed dates. */
final class Text {
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"),
            Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
            Map.entry("hellip", "…"), Map.entry("mdash", "—"), Map.entry("ndash", "–"),
            Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"));
    private static final List<DateTimeFormatter> RSS_DATES = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm zzz", Locale.ENGLISH));

    private Text() {
    }

    /** Strips tags and entities from feed HTML and collapses whitespace. */
    static String clean(String html) {
        if (html == null) {
            return "";
        }
        String text = unescape(TAGS.matcher(html).replaceAll(" "));
        return SPACE.matcher(text).replaceAll(" ").strip();
    }

    static String unescape(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String entity = m.group(1);
            String replacement = m.group(0);
            try {
                if (entity.startsWith("#x") || entity.startsWith("#X")) {
                    replacement = Character.toString(Integer.parseInt(entity.substring(2), 16));
                } else if (entity.startsWith("#")) {
                    replacement = Character.toString(Integer.parseInt(entity.substring(1)));
                } else {
                    replacement = NAMED.getOrDefault(entity, replacement);
                }
            } catch (IllegalArgumentException ignored) {
                // Out-of-range code point: leave the entity as written.
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int cut = s.lastIndexOf(' ', max);
        return s.substring(0, cut > max / 2 ? cut : max).strip() + "…";
    }

    /** Escapes text for safe inclusion in HTML element content and attribute values. */
    static String html(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Only http(s) links are rendered as hrefs, so a feed can't inject javascript: URLs. */
    static boolean isWebUrl(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    static Instant parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String value = s.strip();
        for (DateTimeFormatter f : RSS_DATES) {
            try {
                return ZonedDateTime.parse(value, f).toInstant();
            } catch (DateTimeException ignored) {
                // try the next format
            }
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeException ignored) {
            // fall through
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException ignored) {
            return null;
        }
    }
}
