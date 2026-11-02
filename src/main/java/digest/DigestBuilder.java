package digest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns raw articles into a ranked digest. Articles about the same incident are grouped
 * into one {@link Story}; stories covered by more outlets rank higher, which is how the
 * "biggest" attacks rise to the top.
 */
final class DigestBuilder {

    record Story(Article lead, List<Article> coverage, Set<String> sources, List<String> tags, double score) {
    }

    record Digest(Instant generatedAt, Instant windowStart, List<Story> stories,
                  int articlesScanned, int feedCount, List<String> failedFeeds) {
    }

    private record Scored(Article article, Relevance.Score relevance, Set<String> tokens) {
    }

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "into", "over", "after", "via", "are", "was",
            "has", "have", "its", "new", "how", "why", "what", "who", "more", "than", "use", "used", "using",
            "you", "your", "their", "they", "says", "said", "report", "researchers", "hackers", "attackers",
            "attack", "attacks", "supply", "chain", "malicious", "package", "packages", "malware", "open", "source",
            "software", "security", "campaign", "targets", "targeting", "warn", "warns", "found", "discovered");

    private static final int RARE_WORD_MAX_HEADLINES = 8;

    private final Config config;

    DigestBuilder(Config config) {
        this.config = config;
    }

    Digest build(FeedFetcher.Result fetched, Instant now) {
        Instant windowStart = now.minus(Duration.ofHours(config.lookbackHours()));
        List<Scored> candidates = new ArrayList<>();
        Set<String> seenLinks = new HashSet<>();
        Set<String> seenTitles = new HashSet<>();
        for (Article a : fetched.articles()) {
            if (a.published() == null || a.published().isBefore(windowStart) || a.published().isAfter(now.plus(Duration.ofHours(1)))) {
                continue;
            }
            if (!seenLinks.add(a.link()) || !seenTitles.add(a.title().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Relevance.Score score = Relevance.score(a);
            if (score.value() >= config.minScore()) {
                candidates.add(new Scored(a, score, tokens(a.title())));
            }
        }
        candidates.sort(Comparator.comparingInt((Scored s) -> s.relevance().value()).reversed());

        // How many headlines (across everything fetched) use each word. Rare words are
        // usually names of the affected project or campaign, e.g. "litellm" or "shai-hulud".
        Map<String, Integer> headlineCounts = new HashMap<>();
        for (String title : seenTitles) {
            tokens(title).forEach(t -> headlineCounts.merge(t, 1, Integer::sum));
        }

        List<List<Scored>> clusters = new ArrayList<>();
        for (Scored s : candidates) {
            List<Scored> best = null;
            int bestStrength = 0;
            for (List<Scored> c : clusters) {
                for (Scored member : c) {
                    int strength = matchStrength(member.tokens(), s.tokens(), headlineCounts);
                    if (strength > bestStrength) {
                        bestStrength = strength;
                        best = c;
                    }
                }
            }
            if (best != null) {
                best.add(s);
            } else {
                clusters.add(new ArrayList<>(List.of(s)));
            }
        }

        List<Story> stories = clusters.stream()
                .map(c -> toStory(c, now))
                .sorted(Comparator.comparingDouble(Story::score).reversed())
                .limit(config.maxStories())
                .toList();
        return new Digest(now, windowStart, stories, fetched.articles().size(),
                config.feeds().size(), fetched.failedFeeds());
    }

    private static Story toStory(List<Scored> cluster, Instant now) {
        Scored lead = cluster.get(0);
        Set<String> sources = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        Instant newest = lead.article().published();
        List<Article> coverage = new ArrayList<>();
        for (Scored s : cluster) {
            sources.add(s.article().source());
            tags.addAll(s.relevance().tags());
            if (s.article().published().isAfter(newest)) {
                newest = s.article().published();
            }
            if (s != lead) {
                coverage.add(s.article());
            }
        }
        Article headline = lead.article();
        if (headline.summary().isEmpty()) {
            String fallback = coverage.stream().map(Article::summary)
                    .max(Comparator.comparingInt(String::length)).orElse("");
            headline = new Article(headline.title(), headline.link(), headline.source(), headline.published(), fallback);
        }
        long ageHours = Duration.between(newest, now).toHours();
        double recency = ageHours <= 24 ? 3 : ageHours <= 48 ? 1 : 0;
        double score = lead.relevance().value() + 4.0 * (sources.size() - 1) + recency;
        return new Story(headline, coverage, sources, tags.stream().limit(5).toList(), score);
    }

    /**
     * How strongly two headlines look like the same incident; 0 means they don't. They match
     * if they share a rare name (a long word few headlines use), a rare word plus another
     * word, or a large fraction of their words. Rarer shared words make a stronger match.
     */
    private static int matchStrength(Set<String> a, Set<String> b, Map<String, Integer> headlineCounts) {
        int shared = 0;
        int rare = 0;
        boolean rareName = false;
        for (String t : a) {
            if (!b.contains(t)) {
                continue;
            }
            shared++;
            int count = headlineCounts.getOrDefault(t, 0);
            if (count <= RARE_WORD_MAX_HEADLINES) {
                rare += RARE_WORD_MAX_HEADLINES + 1 - count;
                rareName |= t.length() >= 6;
            }
        }
        boolean matches = rareName
                || (rare > 0 && shared >= 2)
                || (shared >= 3 && shared >= 0.5 * Math.min(a.size(), b.size()));
        return matches ? rare * 10 + shared : 0;
    }

    private static Set<String> tokens(String title) {
        Set<String> out = new HashSet<>();
        for (String raw : title.toLowerCase(Locale.ROOT).split("[^a-z0-9.]+")) {
            String t = raw.replaceAll("^\\.+|\\.+$", "");
            if (t.length() >= 3 && !STOPWORDS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }
}
