package digest;

import static digest.Text.html;

import digest.DigestBuilder.Digest;
import digest.DigestBuilder.Story;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Renders the digest as a single self-contained HTML page. All feed text is escaped. */
final class HtmlPage {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a z", Locale.US);

    private static final String CSS = """
            :root { color-scheme: light dark; --fg:#1d1d1f; --muted:#6e6e73; --bg:#f5f5f7; --card:#fff; --accent:#5b3df5; --border:#e3e3e8; }
            @media (prefers-color-scheme: dark) { :root { --fg:#f2f2f7; --muted:#a1a1a6; --bg:#111114; --card:#1c1c21; --accent:#9d8cff; --border:#2c2c33; } }
            * { box-sizing: border-box; }
            body { margin:0; font:16px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; color:var(--fg); background:var(--bg); }
            main { max-width: 860px; margin: 0 auto; padding: 32px 20px 64px; }
            header { display:flex; justify-content:space-between; align-items:flex-end; gap:16px; flex-wrap:wrap; margin-bottom:24px; }
            h1 { margin:0; font-size:28px; }
            .sub, .meta, footer { color:var(--muted); font-size:14px; }
            button { font:inherit; padding:8px 14px; border-radius:8px; border:1px solid var(--border); background:var(--card); color:var(--fg); cursor:pointer; }
            button:hover { border-color: var(--accent); }
            .story { display:grid; grid-template-columns: 40px 1fr; gap:12px; background:var(--card); border:1px solid var(--border); border-radius:12px; padding:18px 20px; margin-bottom:14px; }
            .rank { font-size:22px; font-weight:700; color:var(--accent); }
            h2 { margin:0 0 4px; font-size:18px; line-height:1.35; }
            a { color:inherit; } h2 a { text-decoration:none; } h2 a:hover { text-decoration:underline; }
            p { margin:10px 0; }
            .tags span { display:inline-block; font-size:12px; padding:2px 8px; margin:0 6px 6px 0; border-radius:999px; background:color-mix(in srgb, var(--accent) 14%, transparent); color:var(--accent); }
            details { font-size:14px; } summary { cursor:pointer; color:var(--muted); }
            details li { margin:4px 0; }
            .empty { background:var(--card); border:1px dashed var(--border); border-radius:12px; padding:32px; text-align:center; color:var(--muted); }
            """;

    private HtmlPage() {
    }

    static String render(Digest digest, boolean refreshing, Instant nextRun, ZoneId zone) {
        StringBuilder out = new StringBuilder(16_384);
        out.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        if (refreshing) {
            out.append("<meta http-equiv=\"refresh\" content=\"5\">");
        }
        out.append("<title>Supply Chain Attack Digest</title><style>").append(CSS).append("</style></head><body><main>");

        out.append("<header><div><h1>Supply Chain Attack Digest</h1><div class=\"sub\">");
        if (digest != null) {
            out.append("Updated ").append(html(format(digest.generatedAt(), zone)))
                    .append(" &middot; last ").append(Duration.between(digest.windowStart(), digest.generatedAt()).toHours())
                    .append(" hours");
        }
        if (nextRun != null) {
            out.append(digest != null ? " &middot; " : "").append("next run ").append(html(format(nextRun, zone)));
        }
        out.append("</div></div><form method=\"post\" action=\"/refresh\"><button type=\"submit\"")
                .append(refreshing ? " disabled>Refreshing…" : ">Refresh now")
                .append("</button></form></header>");

        if (digest == null) {
            out.append("<div class=\"empty\">Building the first digest… this page will reload automatically.</div>");
        } else if (digest.stories().isEmpty()) {
            out.append("<div class=\"empty\">No supply chain or open source attack stories found in this window.</div>");
        } else {
            int rank = 1;
            for (Story story : digest.stories()) {
                renderStory(out, story, rank++, digest.generatedAt());
            }
        }

        if (digest != null) {
            out.append("<footer>Scanned ").append(digest.articlesScanned()).append(" articles from ")
                    .append(digest.feedCount() - digest.failedFeeds().size()).append(" of ")
                    .append(digest.feedCount()).append(" feeds.");
            if (!digest.failedFeeds().isEmpty()) {
                out.append(" Unreachable: ").append(html(String.join(", ", digest.failedFeeds()))).append('.');
            }
            out.append(" JSON at <a href=\"/api/digest\">/api/digest</a>.</footer>");
        }
        return out.append("</main></body></html>").toString();
    }

    private static void renderStory(StringBuilder out, Story story, int rank, Instant now) {
        Article lead = story.lead();
        out.append("<article class=\"story\"><div class=\"rank\">").append(rank).append("</div><div>");
        out.append("<h2>").append(link(lead.link(), lead.title())).append("</h2>");
        out.append("<div class=\"meta\">").append(html(lead.source())).append(" &middot; ")
                .append(age(lead.published(), now));
        if (story.sources().size() > 1) {
            out.append(" &middot; covered by ").append(story.sources().size()).append(" outlets");
        }
        out.append("</div>");
        if (!lead.summary().isEmpty()) {
            out.append("<p>").append(html(lead.summary())).append("</p>");
        }
        if (!story.tags().isEmpty()) {
            out.append("<div class=\"tags\">");
            story.tags().forEach(t -> out.append("<span>").append(html(t)).append("</span>"));
            out.append("</div>");
        }
        if (!story.coverage().isEmpty()) {
            out.append("<details><summary>More coverage (").append(story.coverage().size()).append(")</summary><ul>");
            for (Article a : story.coverage()) {
                out.append("<li>").append(link(a.link(), a.title())).append(" &mdash; ")
                        .append(html(a.source())).append("</li>");
            }
            out.append("</ul></details>");
        }
        out.append("</div></article>");
    }

    private static String link(String url, String text) {
        if (!Text.isWebUrl(url)) {
            return html(text);
        }
        return "<a href=\"" + html(url) + "\" target=\"_blank\" rel=\"noopener noreferrer\">" + html(text) + "</a>";
    }

    private static String age(Instant published, Instant now) {
        long hours = Math.max(0, Duration.between(published, now).toHours());
        return hours < 1 ? "just now" : hours < 48 ? hours + "h ago" : (hours / 24) + "d ago";
    }

    private static String format(Instant instant, ZoneId zone) {
        return WHEN.format(instant.atZone(zone));
    }
}
