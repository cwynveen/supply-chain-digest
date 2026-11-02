# Supply Chain Attack Digest

A small Java web app that checks security news every morning for the biggest software supply chain and open source attacks, and shows a ranked summary with links to the full stories.

It uses only the JDK (built-in HTTP server, HTTP client and XML parser), so there are no third-party dependencies and no Maven/Gradle build.

## Run it

```sh
docker build -t supply-chain-digest .
docker run -d --name scd -p 8080:8080 -e DIGEST_ZONE=America/Chicago supply-chain-digest
open http://localhost:8080
```

The first digest is built a few seconds after startup. After that it rebuilds daily at `DIGEST_TIME`. The **Refresh now** button rebuilds on demand.

Stop it with `docker rm -f scd`.

## How it works

1. **Fetch.** It pulls about a dozen RSS and Atom feeds in parallel: a Google News search for supply chain terms, general security news (The Hacker News, BleepingComputer, SecurityWeek, Dark Reading, Krebs, The Register), and research blogs that often break these stories (Socket, StepSecurity, Aikido, GitHub Security, Chainguard).
2. **Filter** ([Relevance.java](src/main/java/digest/Relevance.java)). Keyword scoring keeps articles about supply chain attacks, malicious or compromised packages, typosquatting, dependency confusion and similar, plus ecosystem names (npm, PyPI, GitHub Actions, …) when they appear next to attack language. Title matches count double.
3. **Group and rank** ([DigestBuilder.java](src/main/java/digest/DigestBuilder.java)). Headlines about the same incident are merged. They're matched on rare shared words, which are usually the name of the affected project or campaign. A story covered by more outlets ranks higher, which is how the "biggest" attacks reach the top. Newer stories get a small boost.

## Endpoints

| Path | |
|---|---|
| `GET /` | The digest as a web page |
| `GET /api/digest` | The same data as JSON |
| `POST /refresh` | Rebuild now |
| `GET /healthz` | Liveness check |

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `DIGEST_TIME` | `07:00` | Daily rebuild time (24h) |
| `DIGEST_ZONE` | container zone (UTC) | Time zone for `DIGEST_TIME` and page timestamps, e.g. `America/New_York` |
| `LOOKBACK_HOURS` | `72` | How far back to look. 72h covers weekends for a Monday digest. |
| `MAX_STORIES` | `10` | Stories shown |
| `MIN_SCORE` | `8` | Relevance threshold. Lower it to see more borderline stories. |
| `FEEDS` | built-in list | Replace the feed list: `Name\|https://url;Other\|https://url2` |
| `PORT` | `8080` | Listen port |

## Notes

- The digest is held in memory, so restarting the container rebuilds it.
- Feed content is untrusted. XML parsing disables DTDs and external entities, all text is HTML-escaped, and only `http(s)` links are rendered.
- Google News results link through `news.google.com` redirect URLs, which open the original article in a browser.
