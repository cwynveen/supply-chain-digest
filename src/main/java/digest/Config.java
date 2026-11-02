package digest;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Runtime settings, read from environment variables with sensible defaults. */
record Config(
        int port,
        LocalTime digestTime,
        ZoneId zone,
        int lookbackHours,
        int maxStories,
        int minScore,
        List<Feed> feeds) {

    record Feed(String name, String url) {
    }

    static final List<Feed> DEFAULT_FEEDS = List.of(
            new Feed("Google News", "https://news.google.com/rss/search?q=%22supply+chain+attack%22+OR+%22malicious+package%22+OR+%22malicious+packages%22+OR+typosquatting+when:3d&hl=en-US&gl=US&ceid=US:en"),
            new Feed("The Hacker News", "https://feeds.feedburner.com/TheHackersNews"),
            new Feed("BleepingComputer", "https://www.bleepingcomputer.com/feed/"),
            new Feed("SecurityWeek", "https://www.securityweek.com/feed/"),
            new Feed("Dark Reading", "https://www.darkreading.com/rss.xml"),
            new Feed("Krebs on Security", "https://krebsonsecurity.com/feed/"),
            new Feed("The Register", "https://www.theregister.com/security/headlines.atom"),
            new Feed("GitHub Security Blog", "https://github.blog/security/feed/"),
            new Feed("Socket", "https://socket.dev/api/blog/feed.atom"),
            new Feed("StepSecurity", "https://www.stepsecurity.io/blog/rss.xml"),
            new Feed("Aikido", "https://www.aikido.dev/blog/rss.xml"),
            new Feed("Chainguard", "https://www.chainguard.dev/unchained/rss.xml"));

    static Config fromEnv() {
        String zone = env("DIGEST_ZONE", "");
        return new Config(
                Integer.parseInt(env("PORT", "8080")),
                LocalTime.parse(env("DIGEST_TIME", "07:00")),
                zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone),
                Integer.parseInt(env("LOOKBACK_HOURS", "72")),
                Integer.parseInt(env("MAX_STORIES", "10")),
                Integer.parseInt(env("MIN_SCORE", "8")),
                parseFeeds(env("FEEDS", "")));
    }

    /** FEEDS format: {@code Name|https://url;Other Name|https://other-url}. Empty means use the defaults. */
    private static List<Feed> parseFeeds(String raw) {
        if (raw.isBlank()) {
            return DEFAULT_FEEDS;
        }
        List<Feed> feeds = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length == 2 && !parts[1].isBlank()) {
                feeds.add(new Feed(parts[0].strip(), parts[1].strip()));
            }
        }
        if (feeds.isEmpty()) {
            throw new IllegalArgumentException("FEEDS is set but contains no 'Name|URL' entries");
        }
        return List.copyOf(feeds);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
