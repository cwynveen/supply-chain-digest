package digest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** Entry point: starts the daily digest schedule and serves it over HTTP. */
public final class App {
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%6$s%n");
    }

    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private App() {
    }

    public static void main(String[] args) throws IOException {
        Config config = Config.fromEnv();
        DigestService service = new DigestService(config);

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", ex -> {
            if (!ex.getRequestURI().getPath().equals("/")) {
                send(ex, 404, "text/plain", "Not found");
                return;
            }
            send(ex, 200, "text/html", HtmlPage.render(service.latest(), service.isRunning(), service.nextRun(), config.zone()));
        });
        server.createContext("/api/digest", ex -> send(ex, 200, "application/json", Json.digest(service.latest())));
        server.createContext("/refresh", ex -> {
            if (!ex.getRequestMethod().equals("POST")) {
                send(ex, 405, "text/plain", "Use POST");
                return;
            }
            service.refreshNow();
            ex.getResponseHeaders().set("Location", "/");
            ex.sendResponseHeaders(303, -1);
            ex.close();
        });
        server.createContext("/healthz", ex -> send(ex, 200, "text/plain", "ok"));
        server.start();
        LOG.info("Listening on http://localhost:" + config.port() + " (digest daily at "
                + config.digestTime() + " " + config.zone() + ")");

        service.start();
    }

    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
