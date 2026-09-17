package com.flux.browser.feature;

import com.google.gson.Gson;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Atomic, ordered local session/settings writes; no passwords or form bodies are persisted here. */
public final class FeatureStore implements AutoCloseable {
    public record SavedTab(String workspace, String url, String title, double zoom) {}
    public record SearchProvider(String keyword, String name, String template) {
        @Override public String toString() { return keyword + " · " + name; }
    }
    public static final class State {
        public List<SavedTab> tabs = new ArrayList<>();
        public List<String> workspaces = new ArrayList<>(List.of("Default"));
        public String workspace = "Default";
        public int selectedTab;
        public Appearance appearance = new Appearance();
        public Map<String, Appearance> appearancePresets = new LinkedHashMap<>();
        public boolean restore = true, blocker = true, https = true, phishing = true, fullText;
        public String translation = "en", passwordProvider = "macOS Keychain";
        public Set<String> allowedSites = new HashSet<>();
        public List<SearchProvider> providers = new ArrayList<>(List.of(
            new SearchProvider("!w", "Wikipedia", "https://en.wikipedia.org/w/index.php?search={query}"),
            new SearchProvider("!gh", "GitHub", "https://github.com/search?q={query}"),
            new SearchProvider("!yt", "YouTube", "https://www.youtube.com/results?search_query={query}")));
    }
    public static final Gson JSON = new Gson();
    private final ExecutorService disk = Executors.newSingleThreadExecutor(r -> new Thread(r, "flux-session-writer"));
    private final Path directory;
    public FeatureStore() {
        directory = Path.of(System.getProperty("flux.profileDir", Path.of(System.getProperty("user.home"),
                "Library", "Application Support", "Flux").toString()));
    }
    private static boolean safeTab(SavedTab t) {
        if (t.url().equals(com.flux.browser.util.UrlResolver.HOME)) return true;
        try { var u = java.net.URI.create(t.url());
            return t.url().length() <= 2048 && u.getUserInfo() == null &&
                ((List.of("http","https").contains(u.getScheme()) && u.getHost() != null) ||
                 ("file".equals(u.getScheme()) && (u.getHost() == null || u.getHost().isEmpty()) && u.getPath().toLowerCase(Locale.ROOT).endsWith(".pdf")));
        } catch (Exception e) { return false; }
    }
    public Path directory() { return directory; }
    public CompletableFuture<State> load() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path path=directory.resolve("session.json");
                if (!Files.exists(path)) return new State();
                if(Files.size(path)>2_000_000) throw new IllegalStateException("Session file exceeds 2 MB");
                State state=JSON.fromJson(Files.readString(path), State.class);
                if(state==null || state.tabs==null || state.workspaces==null || state.providers==null || state.allowedSites==null
                    || state.workspaces.size()>50 || state.tabs.size()>200 || state.providers.size()>50) throw new IllegalStateException("Invalid session file");
                state.tabs.removeIf(t -> t == null || t.url()==null || t.workspace()==null);
                state.workspaces.removeIf(w -> w == null || w.isBlank() || w.length() > 40);
                state.workspaces = new ArrayList<>(new LinkedHashSet<>(state.workspaces));
                if(!state.workspaces.contains("Default"))state.workspaces.addFirst("Default");
                state.tabs.removeIf(t -> !state.workspaces.contains(t.workspace()) || !safeTab(t) || !Double.isFinite(t.zoom()));
                state.allowedSites.removeIf(Objects::isNull);
                state.providers.removeIf(p -> { try { SearchTools.validate(p); return false; } catch(Exception e) { return true; } });
                if (!List.of("macOS Keychain","Bitwarden CLI","1Password CLI").contains(state.passwordProvider == null ? "" : state.passwordProvider)) state.passwordProvider = "macOS Keychain";
                if (state.translation == null || !state.translation.matches("[a-z]{2}(-[A-Z]{2})?")) state.translation = "en";
                if(state.appearance==null)state.appearance=new Appearance();
                state.appearance.normalize();
                if(state.appearancePresets==null)state.appearancePresets=new LinkedHashMap<>();
                state.appearancePresets.entrySet().removeIf(e -> e.getKey()==null || e.getKey().isBlank() || e.getKey().length()>40 || e.getValue()==null);
                if(state.appearancePresets.size()>20)state.appearancePresets.clear();
                state.appearancePresets.values().forEach(Appearance::normalize);
                if(!state.workspaces.contains(state.workspace))state.workspace=state.workspaces.getFirst();
                return state;
            } catch (Exception e) { throw new CompletionException(e); }
        }, disk);
    }
    public CompletableFuture<String> rules(boolean enabled, List<String> exceptions) {
        return CompletableFuture.supplyAsync(() -> { try { return enabled ? ContentRules.build(ContentRules.load(directory), exceptions) : ""; }
            catch (Exception e) { throw new CompletionException(e); } }, disk);
    }
    public CompletableFuture<Void> save(State state) {
        String snapshot=JSON.toJson(state); // Immutable snapshot before dispatch.
        return CompletableFuture.runAsync(() -> {
            try { writeAtomic(directory.resolve("session.json"), snapshot); }
            catch(Exception e){throw new CompletionException(e);}
        }, disk);
    }
    public static void writeAtomic(Path path, String text) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(), ".flux-", ".tmp");
        try {
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try { Files.setPosixFilePermissions(temp,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
            catch(UnsupportedOperationException ignored){}
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public void close(){disk.shutdown();}
}
