package com.flux.browser;

import com.flux.browser.feature.*;
import com.flux.browser.util.UrlResolver;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** Deterministic checks use a temporary profile, never the user's browsing data or vault. */
public final class FeatureChecks {
    public static void run() throws Exception {
        var state = new FeatureStore.State();
        BrowserChecks.equal(SearchTools.resolve("!gh Java FX",state.providers),"https://github.com/search?q=Java+FX");
        BrowserChecks.equal(SearchTools.resolve("github.com",state.providers),"github.com");
        BrowserChecks.equal(SearchTools.answer("github.com"),"");
        BrowserChecks.equal(SearchTools.answer("= (12 + 8) / 4"),"= 5");
        BrowserChecks.equal(SearchTools.answer("= -2 * (3.5 + 1)"),"= -9");
        BrowserChecks.check(SearchTools.answer("=1/0").startsWith("Use"),"division by zero handled");
        for (String template : List.of("javascript:{query}","https://user:secret@example.com/{query}","http://example.com/{query}")) {
            reject(() -> SearchTools.validate(new FeatureStore.SearchProvider("!bad","Bad",template)));
        }
        BrowserChecks.equal(PasswordProviders.origin("https://EXAMPLE.com:443/login?x=1"),"https://example.com");
        BrowserChecks.equal(PasswordProviders.origin("https://example.com:444/login"),"https://example.com:444");
        reject(() -> PasswordProviders.origin("http://example.com/login"));
        reject(() -> PasswordProviders.origin("https://example.com:0/login"));
        reject(() -> PasswordProviders.origin("https://example.com:65536/login"));
        reject(() -> PasswordProviders.origin("https://user:password@example.com/"));
        var rules = JsonParser.parseString(ContentRules.build(List.of("ads.example.com","invalid/host"),List.of("trusted.example.com"))).getAsJsonArray();
        BrowserChecks.equal(rules.size(),2);
        String pattern = rules.get(0).getAsJsonObject().getAsJsonObject("trigger").get("url-filter").getAsString();
        BrowserChecks.check(java.util.regex.Pattern.compile(pattern).matcher("https://sub.ads.example.com/a.js").find(),"subdomain block");
        BrowserChecks.check(!java.util.regex.Pattern.compile(pattern).matcher("https://ads.example.com.evil.test/a.js").find(),"domain boundary");
        BrowserChecks.check(PageText.readerScript().contains("new Readability"),"bundled reader loads");
        Path directory = Files.createTempDirectory("flux-feature-check-");
        String old = System.getProperty("flux.profileDir");
        System.setProperty("flux.profileDir",directory.toString());
        try (var store = new FeatureStore()) {
            state.workspaces.add("Research"); state.workspace="Research"; state.selectedTab=1;
            state.tabs.add(new FeatureStore.SavedTab("Default",UrlResolver.HOME,"Speed Dial",1));
            state.tabs.add(new FeatureStore.SavedTab("Research","https://example.com/","Example",1.25));
            store.save(state).get();
            var loaded=store.load().get();
            BrowserChecks.equal(loaded.tabs,state.tabs);
            BrowserChecks.equal(loaded.selectedTab,1);
            BrowserChecks.equal(loaded.passwordProvider,"macOS Keychain");
            BrowserChecks.check(!loaded.fullText,"full text requires opt-in");
            var saved=store.save(state); state.workspace="Later"; saved.get();
            BrowserChecks.equal(store.load().get().workspace,"Research");
            state.tabs.add(new FeatureStore.SavedTab("Default","javascript:alert(1)","Unsafe",1));
            state.tabs.add(new FeatureStore.SavedTab("Default","file:///etc/passwd","Unsafe file",1));
            state.tabs.add(new FeatureStore.SavedTab("Default","https://user:secret@example.com/","Unsafe URL",1));
            store.save(state).get(); BrowserChecks.equal(store.load().get().tabs.size(),2);
            try(var files=Files.list(directory)){BrowserChecks.equal(files.filter(p -> p.toString().endsWith(".tmp")).count(),0L);}
            Files.writeString(directory.resolve("session.json"),"{bad json");
            try {store.load().get();throw new AssertionError("Corrupt session accepted");}catch(java.util.concurrent.ExecutionException expected) { }
        } finally {
            if(old==null)System.clearProperty("flux.profileDir");else System.setProperty("flux.profileDir",old);
            try(var files=Files.list(directory)){for(Path file:files.toList())Files.delete(file);}Files.delete(directory);
        }
        System.out.println("FeatureChecks passed: session round-trip/validation, search plugins, arithmetic, origins, reader asset, and domain rules.");
    }
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Unsafe value accepted");}catch(IllegalArgumentException expected){}}
}
