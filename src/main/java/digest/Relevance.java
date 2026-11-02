package digest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keyword scoring that decides whether an article is about a software supply chain or
 * open source attack. Title matches count double. An article only qualifies if it hits a
 * "core" term, or names a package ecosystem alongside attack language.
 */
final class Relevance {

    enum Kind { CORE, ECOSYSTEM, CONTEXT }

    record Term(String label, Pattern pattern, int weight, Kind kind) {
        Term(String label, String regex, int weight, Kind kind) {
            this(label, Pattern.compile(regex), weight, kind);
        }
    }

    record Score(int value, List<String> tags) {
    }

    private static final List<Term> TERMS = List.of(
            new Term("supply chain", "\\bsupply[- ]chains?\\b", 4, Kind.CORE),
            new Term("malicious packages", "\\bmalicious (?:\\w+ ){0,2}(?:packages?|librar(?:y|ies)|extensions?|crates?|gems?|container images?|docker images?)\\b", 5, Kind.CORE),
            new Term("compromised packages", "\\b(?:compromis\\w*|hijack\\w*|backdoor\\w*|trojani[sz]\\w*|poison\\w*) (?:\\w+ ){0,2}(?:packages?|librar(?:y|ies)|maintainers?|extensions?|repositor(?:y|ies)|actions?|dependenc(?:y|ies)|releases?|updates?)\\b", 5, Kind.CORE),
            new Term("typosquatting", "\\btypo-?squat\\w*", 5, Kind.CORE),
            new Term("slopsquatting", "\\bslopsquat\\w*", 5, Kind.CORE),
            new Term("dependency confusion", "\\bdependency confusion\\b", 5, Kind.CORE),
            new Term("protestware", "\\bprotestware\\b", 5, Kind.CORE),
            new Term("repojacking", "\\b(?:repo|star)jacking\\b", 5, Kind.CORE),
            new Term("worm", "\\bself-(?:propagating|replicating)\\b", 4, Kind.CORE),

            new Term("npm", "\\bnpm\\b", 3, Kind.ECOSYSTEM),
            new Term("PyPI", "\\bpypi\\b", 3, Kind.ECOSYSTEM),
            new Term("RubyGems", "\\brubygems\\b", 3, Kind.ECOSYSTEM),
            new Term("crates.io", "\\bcrates\\.io\\b", 3, Kind.ECOSYSTEM),
            new Term("Maven", "\\bmaven\\b", 3, Kind.ECOSYSTEM),
            new Term("NuGet", "\\bnuget\\b", 3, Kind.ECOSYSTEM),
            new Term("Packagist", "\\bpackagist\\b", 3, Kind.ECOSYSTEM),
            new Term("Go modules", "\\bgo modules?\\b", 3, Kind.ECOSYSTEM),
            new Term("Docker Hub", "\\bdocker hub\\b", 3, Kind.ECOSYSTEM),
            new Term("GitHub Actions", "\\bgithub actions?\\b", 3, Kind.ECOSYSTEM),
            new Term("IDE extensions", "\\b(?:vs ?code|open vsx|visual studio marketplace)\\b", 3, Kind.ECOSYSTEM),
            new Term("Hugging Face", "\\bhugging ?face\\b", 3, Kind.ECOSYSTEM),

            new Term("open source", "\\bopen[- ]source\\b", 2, Kind.CONTEXT),
            new Term("maintainers", "\\bmaintainers?\\b", 2, Kind.CONTEXT),
            new Term("backdoor", "\\bbackdoor\\w*", 2, Kind.CONTEXT),
            new Term("CI/CD", "\\b(?:ci/cd|build pipelines?|build systems?)\\b", 2, Kind.CONTEXT),
            new Term("credential theft", "\\b(?:credential[- ]steal\\w*|infostealers?|token theft|stolen tokens?|secrets? exfiltrat\\w*)\\b", 2, Kind.CONTEXT),
            new Term("worm", "\\bworm\\b", 2, Kind.CONTEXT));

    private static final Pattern ATTACK = Pattern.compile(
            "\\b(?:malicious|malware|compromis\\w*|backdoor\\w*|hijack\\w*|poison\\w*|stealer|worm|trojan\\w*|exfiltrat\\w*)");

    private Relevance() {
    }

    static Score score(Article a) {
        String title = a.title().toLowerCase(Locale.ROOT);
        String body = a.summary().toLowerCase(Locale.ROOT);
        int total = 0;
        boolean core = false;
        boolean ecosystem = false;
        List<String> tags = new ArrayList<>();
        for (Term t : TERMS) {
            int points = t.pattern().matcher(title).find() ? t.weight() * 2
                    : t.pattern().matcher(body).find() ? t.weight() : 0;
            if (points == 0) {
                continue;
            }
            total += points;
            core |= t.kind() == Kind.CORE;
            ecosystem |= t.kind() == Kind.ECOSYSTEM;
            if (!tags.contains(t.label())) {
                tags.add(t.label());
            }
        }
        boolean qualifies = core || (ecosystem && ATTACK.matcher(title + " " + body).find());
        return qualifies ? new Score(total, tags) : new Score(0, List.of());
    }
}
