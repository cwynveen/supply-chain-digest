package digest;

import digest.DigestBuilder.Digest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Builds the digest on startup, then every morning at the configured time. Keeps the latest in memory. */
final class DigestService {
    private static final Logger LOG = Logger.getLogger(DigestService.class.getName());

    private final Config config;
    private final FeedFetcher fetcher = new FeedFetcher();
    private final DigestBuilder builder;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Digest latest;
    private volatile Instant nextRun;

    DigestService(Config config) {
        this.config = config;
        this.builder = new DigestBuilder(config);
    }

    void start() {
        refreshNow();
        scheduleNext();
    }

    /** Starts a refresh in the background unless one is already in progress. */
    void refreshNow() {
        if (running.compareAndSet(false, true)) {
            scheduler.execute(this::run);
        }
    }

    Digest latest() {
        return latest;
    }

    Instant nextRun() {
        return nextRun;
    }

    boolean isRunning() {
        return running.get();
    }

    private void scheduleNext() {
        ZonedDateTime now = ZonedDateTime.now(config.zone());
        ZonedDateTime next = now.with(config.digestTime());
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        nextRun = next.toInstant();
        LOG.info("Next digest scheduled for " + next);
        scheduler.schedule(() -> {
            // Skip if a manual refresh is already queued; it will produce a fresh digest anyway.
            if (running.compareAndSet(false, true)) {
                run();
            }
            scheduleNext();
        }, Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS);
    }

    private void run() {
        try {
            LOG.info("Building digest from " + config.feeds().size() + " feeds");
            FeedFetcher.Result fetched = fetcher.fetchAll(config.feeds());
            latest = builder.build(fetched, Instant.now());
            LOG.info("Digest ready: " + latest.stories().size() + " stories from "
                    + fetched.articles().size() + " articles");
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Digest build failed", e);
        } finally {
            running.set(false);
        }
    }
}
