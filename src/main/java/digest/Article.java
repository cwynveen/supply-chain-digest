package digest;

import java.time.Instant;

/** A single news item pulled from a feed. */
record Article(String title, String link, String source, Instant published, String summary) {
}
