package digest;

import digest.DigestBuilder.Digest;
import digest.DigestBuilder.Story;
import java.util.Collection;

/** Minimal hand-rolled JSON output for the digest, to avoid pulling in a library. */
final class Json {
    private Json() {
    }

    static String digest(Digest d) {
        if (d == null) {
            return "{\"status\":\"building\"}";
        }
        StringBuilder out = new StringBuilder("{");
        field(out, "generatedAt", d.generatedAt().toString()).append(',');
        field(out, "windowStart", d.windowStart().toString()).append(',');
        out.append("\"articlesScanned\":").append(d.articlesScanned()).append(',');
        out.append("\"failedFeeds\":");
        strings(out, d.failedFeeds());
        out.append(",\"stories\":[");
        for (int i = 0; i < d.stories().size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            story(out, d.stories().get(i));
        }
        return out.append("]}").toString();
    }

    private static void story(StringBuilder out, Story s) {
        out.append('{');
        article(out, s.lead());
        out.append(",\"score\":").append(Math.round(s.score() * 10) / 10.0).append(",\"sources\":");
        strings(out, s.sources());
        out.append(",\"tags\":");
        strings(out, s.tags());
        out.append(",\"coverage\":[");
        for (int i = 0; i < s.coverage().size(); i++) {
            out.append(i > 0 ? ",{" : "{");
            article(out, s.coverage().get(i));
            out.append('}');
        }
        out.append("]}");
    }

    private static void article(StringBuilder out, Article a) {
        field(out, "title", a.title()).append(',');
        field(out, "link", a.link()).append(',');
        field(out, "source", a.source()).append(',');
        field(out, "published", a.published().toString()).append(',');
        field(out, "summary", a.summary());
    }

    private static StringBuilder field(StringBuilder out, String name, String value) {
        return out.append('"').append(name).append("\":").append(quote(value));
    }

    private static void strings(StringBuilder out, Collection<String> values) {
        out.append('[');
        boolean first = true;
        for (String v : values) {
            out.append(first ? "" : ",").append(quote(v));
            first = false;
        }
        out.append(']');
    }

    private static String quote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
