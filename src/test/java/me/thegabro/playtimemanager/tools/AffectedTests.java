package me.thegabro.playtimemanager.tools;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Decides which tests Maven runs, so that a build does not run all of them every time.
 * <p>
 * It remembers the hash of every source file from the last green run
 * ({@code .affected-tests/state.properties}, outside target/ so that "clean" does not erase it). On the next run the files that differ are mapped
 * to test tags ({@link #RULES}) and only the tests with those tags run. Without a remembered state
 * (first run, after {@code mvn clean}), or when a changed file has no rule, every test runs.
 * <p>
 * Used by the pom with two modes:
 * <ul>
 *   <li>{@code select}: writes {@code target/affected-tests/selection.properties} with the surefire
 *       {@code groups} expression (empty = all tests)</li>
 *   <li>{@code record}: after the tests passed, remembers the current state</li>
 * </ul>
 * {@code -Dtests.all=true} forces every test; {@code -DskipTests} runs none and records nothing.
 */
public final class AffectedTests {
    static final String ALL = "*";

    /** Where the state of the last green run is kept (git-ignored). */
    private static final String STATE_DIR = ".affected-tests";

    /** Tags that always run when something runs: a quick check that the plugin still starts. */
    private static final Set<String> ALWAYS = Set.of("smoke");

    private record Rule(Pattern path, Set<String> tags) {}

    private static Rule rule(String regex, String... tags) {
        return new Rule(Pattern.compile(regex), Set.of(tags));
    }

    /**
     * Changed file (path from the project root, with /) -> tags of the tests to run. Every matching
     * rule counts; {@link #ALL} means everything. A source file nothing matches also means everything.
     */
    private static final List<Rule> RULES = List.of(
            // Build, configuration and the test infrastructure itself
            rule("pom\\.xml", ALL),
            rule("src/(main|test)/resources/.*", ALL),
            rule("src/test/java/.*/(PluginTestBase|TestDatabases|RequiresDocker)\\.java", ALL),
            rule("src/test/java/.*/tools/.*", ALL),
            // The core everything else stands on
            rule("src/main/java/.*/(PlayTimeManager|Utils|Configuration)\\.java", ALL),
            rule("src/main/java/.*/(Users|Events|Customizations)/.*", ALL),
            rule("src/main/java/.*/Database/(DAOs/(?!JournalDAO)|Database|DatabaseHandler|DatabaseFactory|DatabaseTypes/|DatabaseBackupUtility|Error|Errors|LogFilter).*", ALL),
            rule("src/main/java/.*/ExternalPluginSupport/LuckPerms/.*", ALL),
            // Features
            rule("src/main/java/.*/(Journal/.*|Database/DAOs/JournalDAO|Database/JournalTables|Commands/PlaytimeJournal)\\.java",
                    "journal", "database"),
            rule("src/main/java/.*/Database/Migration/.*", "migration", "database"),
            rule("src/main/java/.*/Updates/.*", "updater", "journal"),
            rule("src/main/java/.*/(Goals/.*|GUIs/Goals/.*|Commands/PlaytimeGoal)\\.java", "goals", "commands"),
            rule("src/main/java/.*/(JoinStreaks/.*|GUIs/JoinStreak/.*|GUIs/Player/RewardsInfoGui|Commands/(PlayTimeJoinStreak|ClaimRewards))\\.java",
                    "joinstreak", "commands"),
            rule("src/main/java/.*/ExternalPluginSupport/PlaceHolders/.*", "placeholders"),
            rule("src/main/java/.*/ExternalPluginSupport/(AFKManager|AFKSyncManager|AFKPlus|AntiAFKPlus|EssentialsX|Purpur|JetsAntiAFKPro|genericAFKPlaceholder)(/.*|\\.java)",
                    "afk", "accounting"),
            rule("src/main/java/.*/(Commands|GUIs)/.*", "commands")
    );

    private static final Pattern TAG = Pattern.compile("@Tag\\(\"([a-z]+)\"\\)");

    private AffectedTests() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        Path root = Path.of(args[1]);
        Path dir = root.resolve("target/affected-tests");
        Files.createDirectories(dir);
        // The state lives outside target/: an IDE build usually runs "clean", which would erase it and
        // make every run start from scratch
        Path stateDir = root.resolve(STATE_DIR);
        Files.createDirectories(stateDir);

        if (mode.equals("select")) {
            select(root, dir, stateDir);
        } else if (mode.equals("record")) {
            record(root, stateDir);
        } else {
            throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    // -------------------------------------------------------------------------

    private static void select(Path root, Path dir, Path stateDir) throws IOException {
        String groups = groupsFor(root, stateDir);
        Properties out = new Properties();
        // "groups" is surefire's own parameter: it is read when the tests start, after this has run
        // (a property declared in the pom would already have been replaced when the pom was read)
        out.setProperty("groups", groups);
        try (Writer w = Files.newBufferedWriter(dir.resolve("selection.properties"), StandardCharsets.UTF_8)) {
            out.store(w, "written by AffectedTests");
        }
    }

    /** The surefire {@code groups} expression: empty for all tests, a tag expression, or a tag nothing has. */
    private static String groupsFor(Path root, Path stateDir) throws IOException {
        if (Boolean.getBoolean("tests.all")) {
            log("tests.all=true: running every test");
            return "";
        }
        Path state = stateDir.resolve("state.properties");
        if (!Files.exists(state)) {
            log("no previous green run: running every test");
            return "";
        }

        Map<String, String> before = new TreeMap<>();
        Properties saved = new Properties();
        try (Reader r = Files.newBufferedReader(state, StandardCharsets.UTF_8)) {
            saved.load(r);
        }
        saved.forEach((k, v) -> before.put((String) k, (String) v));
        Map<String, String> now = snapshot(root);

        Set<String> changed = new TreeSet<>();
        for (var e : now.entrySet()) {
            if (!e.getValue().equals(before.get(e.getKey()))) changed.add(e.getKey());
        }
        for (String path : before.keySet()) {
            if (!now.containsKey(path)) changed.add(path);
        }

        if (changed.isEmpty()) {
            log("nothing changed since the last green run: no tests to run");
            return "none-selected";
        }

        Set<String> tags = new TreeSet<>();
        for (String path : changed) {
            Set<String> pathTags = tagsFor(root, path);
            if (pathTags.contains(ALL)) {
                log("changed: " + path + " -> running every test");
                return "";
            }
            tags.addAll(pathTags);
        }
        tags.addAll(ALWAYS);
        String expression = String.join(" | ", tags);
        log(changed.size() + " changed file(s) -> tests tagged: " + expression);
        return expression;
    }

    private static Set<String> tagsFor(Path root, String path) throws IOException {
        Set<String> tags = new TreeSet<>();
        boolean matched = false;
        for (Rule rule : RULES) {
            if (rule.path().matcher(path).matches()) {
                tags.addAll(rule.tags());
                matched = true;
            }
        }
        if (matched) return tags;

        // A changed test class: run it (through its own tags)
        if (path.startsWith("src/test/java/") && Files.exists(root.resolve(path))) {
            Matcher m = TAG.matcher(Files.readString(root.resolve(path)));
            while (m.find()) tags.add(m.group(1));
            if (!tags.isEmpty()) return tags;
        }
        // A deleted file or one nothing knows about: be safe
        return Set.of(ALL);
    }

    private static void record(Path root, Path stateDir) throws IOException {
        if (Boolean.getBoolean("skipTests") || Boolean.getBoolean("maven.test.skip")) {
            log("tests were skipped: the state is not updated");
            return;
        }
        Properties out = new Properties();
        out.putAll(snapshot(root));
        try (Writer w = Files.newBufferedWriter(stateDir.resolve("state.properties"), StandardCharsets.UTF_8)) {
            out.store(w, "hash of every source file at the last green run");
        }
        log("state updated");
    }

    /** Path (from the root, with /) -> SHA-1 of the content, for the sources and the pom. */
    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        List<Path> files = new ArrayList<>();
        files.add(root.resolve("pom.xml"));
        for (String dir : List.of("src/main", "src/test")) {
            try (Stream<Path> walk = Files.walk(root.resolve(dir))) {
                walk.filter(Files::isRegularFile).forEach(files::add);
            }
        }
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            for (Path file : files) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                hashes.put(relative, HexFormat.of().formatHex(sha1.digest(Files.readAllBytes(file))));
            }
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return hashes;
    }

    private static void log(String message) {
        System.out.println("[affected-tests] " + message);
    }
}
