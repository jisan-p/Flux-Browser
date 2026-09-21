package com.flux.browser.feature;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** One locally bundled, version-pinned script shared by all compatibility tabs. */
public final class ErudaScript {
    private static CompletableFuture<String> bundle;
    private ErudaScript() { }
    public static synchronized CompletableFuture<String> load() {
        if (bundle == null) bundle = CompletableFuture.supplyAsync(() -> {
            try (var in = ErudaScript.class.getResourceAsStream("/com/flux/browser/script/eruda.min.js")) {
                if (in == null) throw new IllegalStateException("Bundled developer tools are missing");
                byte[] bytes = in.readNBytes(1_000_001);
                if (bytes.length > 1_000_000) throw new IllegalStateException("Developer tools bundle exceeds its size limit");
                // Use the bundle's CommonJS export to avoid replacing a website's own window.eruda.
                return "(() => { const module={exports:{}}; const exports=module.exports; const define=undefined;\n"
                    + new String(bytes,StandardCharsets.UTF_8)
                    + "\nconst tool=module.exports;tool.init({tool:['console','elements','network','resources','sources','info'],useShadowDom:true,defaults:{theme:'Dark',displaySize:55}});"
                    + "tool.get('console').config.set('maxLogNum','200');"
                    + "Object.defineProperty(window,'__fluxDevTools',{value:tool,configurable:true});tool.show();return true;})()";
            } catch (Exception error) { throw new CompletionException(error); }
        });
        return bundle;
    }
}
