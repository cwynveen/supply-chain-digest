package digest;

import digest.Config.Feed;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Downloads RSS and Atom feeds in parallel and turns them into {@link Article}s. */
final class FeedFetcher {
    private static final Logger LOG = Logger.getLogger(FeedFetcher.class.getName());
    private static final String USER_AGENT = "supply-chain-digest/1.0";

    record Result(List<Article> articles, List<String> failedFeeds) {
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    Result fetchAll(List<Feed> feeds) {
        List<Article> articles = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<Article>>> futures = new ArrayList<>();
            for (Feed feed : feeds) {
                futures.add(pool.submit(() -> fetch(feed)));
            }
            for (int i = 0; i < feeds.size(); i++) {
                Feed feed = feeds.get(i);
                try {
                    List<Article> items = futures.get(i).get();
                    LOG.info(() -> feed.name() + ": " + items.size() + " items");
                    articles.addAll(items);
                } catch (ExecutionException e) {
                    LOG.warning(feed.name() + " failed: " + e.getCause());
                    failed.add(feed.name());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed.add(feed.name());
                }
            }
        }
        return new Result(articles, failed);
    }

    private List<Article> fetch(Feed feed) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feed.url()))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9")
                .GET()
                .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return parse(newParser().parse(body), feed.name());
        }
    }

    /** Feed content is untrusted, so DTDs and external entities are disabled (prevents XXE). */
    private static DocumentBuilder newParser() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder();
    }

    static List<Article> parse(Document doc, String feedName) {
        List<Article> out = new ArrayList<>();
        NodeList items = doc.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < items.getLength(); i++) {
            out.add(fromRss((Element) items.item(i), feedName));
        }
        NodeList entries = doc.getElementsByTagNameNS("*", "entry");
        for (int i = 0; i < entries.getLength(); i++) {
            out.add(fromAtom((Element) entries.item(i), feedName));
        }
        out.removeIf(a -> a.title().isEmpty() || a.link().isEmpty());
        return out;
    }

    private static Article fromRss(Element item, String feedName) {
        String title = Text.clean(childText(item, "title"));
        String source = feedName;
        // Aggregators such as Google News name the original publisher in <source>.
        String publisher = Text.clean(childText(item, "source"));
        if (!publisher.isEmpty()) {
            source = publisher;
            if (title.endsWith(" - " + publisher)) {
                title = title.substring(0, title.length() - publisher.length() - 3).strip();
            }
        }
        String summary = childText(item, "description");
        if (summary.isBlank()) {
            summary = childText(item, "encoded");
        }
        String date = childText(item, "pubDate");
        if (date.isBlank()) {
            date = childText(item, "date");
        }
        return article(title, childText(item, "link"), source, Text.parseDate(date), summary);
    }

    private static Article fromAtom(Element entry, String feedName) {
        String link = "";
        for (Element el : children(entry, "link")) {
            String rel = el.getAttribute("rel");
            if (rel.isEmpty() || rel.equals("alternate")) {
                link = el.getAttribute("href");
                break;
            }
        }
        String summary = childText(entry, "summary");
        if (summary.isBlank()) {
            summary = childText(entry, "content");
        }
        String date = childText(entry, "published");
        if (date.isBlank()) {
            date = childText(entry, "updated");
        }
        return article(Text.clean(childText(entry, "title")), link, feedName, Text.parseDate(date), summary);
    }

    private static Article article(String title, String link, String source, Instant published, String summaryHtml) {
        String summary = Text.clean(summaryHtml);
        // Google News descriptions just repeat the headline; drop those.
        if (summary.startsWith(title)) {
            summary = "";
        }
        return new Article(title, link.strip(), source, published, Text.truncate(summary, 320));
    }

    private static String childText(Element parent, String localName) {
        List<Element> matches = children(parent, localName);
        return matches.isEmpty() ? "" : matches.get(0).getTextContent();
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                out.add(el);
            }
        }
        return out;
    }
}
