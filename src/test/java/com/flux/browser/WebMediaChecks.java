package com.flux.browser;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import com.flux.browser.web.BrowserPage;
import com.flux.browser.web.NativeWebPage;
import javafx.stage.Stage;

/** Actual WebKit media counters and navigation timing, not an inferred video FPS from FX pulses. */
public final class WebMediaChecks {
    private static Stage stage;
    private static final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    static final String INSTRUMENT = """
            (() => {
              if (window.__fluxProbe) window.__fluxProbe.stop();
              const p = window.__fluxProbe = {start:performance.now(), raf:[], videos:[], active:true};
              p.visibility = [{time:0, state:document.visibilityState}];
              const visibilityChanged=()=>p.visibility.push({time:performance.now()-p.start,state:document.visibilityState});
              document.addEventListener('visibilitychange', visibilityChanged);
              let before=0, rafId;
              function attach() {
                document.querySelectorAll('video').forEach(v => {
                  if (p.videos.some(x => x.element === v)) return;
                  const q = typeof v.getVideoPlaybackQuality === 'function' ? v.getVideoPlaybackQuality() : null;
                  const x = {element:v, startTime:v.currentTime, waiting:0, stalled:0, playing:0,
                    callbacks:0, presentedFrames:null, mediaTime:null, listeners:[],
                    qualityAvailable:!!q, totalStart:q ? q.totalVideoFrames : null,
                    droppedStart:q ? q.droppedVideoFrames : null,
                    decodedStart:typeof v.webkitDecodedFrameCount === 'number' ? v.webkitDecodedFrameCount : null,
                    rvfcAvailable:typeof v.requestVideoFrameCallback === 'function'};
                  ['waiting','stalled','playing'].forEach(name => {
                    const listener=()=>x[name]++; v.addEventListener(name,listener); x.listeners.push([name,listener]);
                  });
                  if (x.rvfcAvailable) {
                    const frame=(time,metadata)=>{if(!p.active)return; x.callbacks++;
                      x.presentedFrames=metadata.presentedFrames; x.mediaTime=metadata.mediaTime;
                      x.callbackId=v.requestVideoFrameCallback(frame);};
                    x.callbackId=v.requestVideoFrameCallback(frame);
                  }
                  p.videos.push(x);
                });
              }
              function frame(now) {if(!p.active)return; if(before)p.raf.push(now-before); before=now;
                rafId=requestAnimationFrame(frame);}
              attach(); const discover=setInterval(attach,250); rafId=requestAnimationFrame(frame);
              p.stop=()=>{document.removeEventListener('visibilitychange', visibilityChanged);p.active=false; cancelAnimationFrame(rafId); clearInterval(discover);
                p.videos.forEach(x=>{x.listeners.forEach(([n,f])=>x.element.removeEventListener(n,f));
                  if(x.rvfcAvailable && typeof x.element.cancelVideoFrameCallback==='function')
                    x.element.cancelVideoFrameCallback(x.callbackId);});};
              return true;
            })()
            """;
    static final String SNAPSHOT = """
            (() => {
              const p=window.__fluxProbe, nav=performance.getEntriesByType('navigation')[0];
              const pct=(a,f)=>{const s=a.slice().sort((a,b)=>a-b); return s.length?s[Math.ceil(s.length*f)-1]:null;};
              const elapsed=p?performance.now()-p.start:0;
              const hiddenMs=p?p.visibility.reduce((sum,event,i)=>sum+(event.state==='hidden'
                ?(p.visibility[i+1]?.time??elapsed)-event.time:0),0):null;
              const status=v=>({currentTime:v.currentTime, duration:Number.isFinite(v.duration)?v.duration:null,
                paused:v.paused, ended:v.ended, muted:v.muted, readyState:v.readyState, networkState:v.networkState,
                width:v.videoWidth, height:v.videoHeight, playbackRate:v.playbackRate,
                buffered:Array.from({length:v.buffered.length},(_,i)=>[v.buffered.start(i),v.buffered.end(i)]),
                error:v.error?{code:v.error.code,message:v.error.message}:null});
              return JSON.stringify({url:location.href,title:document.title,readyState:document.readyState,
                visibility:document.visibilityState,hiddenMs,visibilityEvents:p?p.visibility:null,resources:performance.getEntriesByType('resource').length,
                navigation:nav?{responseStart:nav.responseStart,responseEnd:nav.responseEnd,
                  domInteractive:nav.domInteractive,domContentLoadedEnd:nav.domContentLoadedEventEnd,
                  loadEnd:nav.loadEventEnd,duration:nav.duration,transferSize:nav.transferSize}:null,
                viewport:{width:innerWidth,height:innerHeight,pixelRatio:devicePixelRatio},
                mediaSupport:{mediaSource:typeof MediaSource!=='undefined',
                  h264:document.createElement('video').canPlayType('video/mp4; codecs="avc1.42E01E"'),
                  vp9:document.createElement('video').canPlayType('video/webm; codecs="vp9"'),
                  videoFrameCallback:typeof HTMLVideoElement.prototype.requestVideoFrameCallback==='function'},
                playerMessage:document.querySelector('.ytp-error-content-wrap')?.innerText||null,
                elapsed:p?performance.now()-p.start:null,raf:p?{count:p.raf.length,
                  median:pct(p.raf,.5),p95:pct(p.raf,.95),max:pct(p.raf,1)}:null,
                videos:Array.from(document.querySelectorAll('video')).map(status),
                observed:p?p.videos.map(x=>{const v=x.element,
                  q=x.qualityAvailable?v.getVideoPlaybackQuality():null;
                  return {...status(v),initialTime:x.startTime,waiting:x.waiting,stalled:x.stalled,playing:x.playing,
                    videoFrameCallbackAvailable:x.rvfcAvailable,videoFrameCallbacks:x.callbacks,
                    lastPresentedFrames:x.presentedFrames,lastMediaTime:x.mediaTime,
                    playbackQualityAvailable:x.qualityAvailable,
                    totalFramesDelta:q?q.totalVideoFrames-x.totalStart:null,
                    droppedFramesDelta:q?q.droppedVideoFrames-x.droppedStart:null,
                    webkitDecodedFramesDelta:x.decodedStart===null?null:v.webkitDecodedFrameCount-x.decodedStart};}):[],
                autoplayError:window.__fluxAutoplayError||null});
            })()
            """;

    public static void main(String[] args) throws Exception {
        String database = System.getenv("FLUX_DB_URL");
        if (!"false".equals(System.getenv("FLUX_EXPECT_STORAGE")) || database == null
                || !database.matches("jdbc:postgresql://[^/]+/flux_test(?:\\?.*)?")) {
            throw new IllegalArgumentException("Use FLUX_EXPECT_STORAGE=false and an unavailable FLUX_DB_URL ending /flux_test.");
        }
        String file = System.getenv("FLUX_MEDIA_FILE");
        if (file == null || !Files.isRegularFile(Path.of(file)) || Files.size(Path.of(file)) == 0) {
            throw new IllegalArgumentException("Set FLUX_MEDIA_FILE to a local H.264 MP4 fixture (at least 12 seconds recommended).");
        }
        Path media = Path.of(file);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var http = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(http);
        server.createContext("/clip.mp4", exchange -> serveVideo(exchange, media));
        byte[] page = """
                <!doctype html><html><head><title>Flux local H.264 playback</title><style>
                body{margin:0;background:#15151a;color:white;font:16px system-ui}header{padding:10px}
                video{display:block;width:100%;max-height:calc(100vh - 70px);object-fit:contain;background:black}
                </style></head><body><header>Local H.264 video · muted autoplay · actual decoder counters</header>
                <video src='/clip.mp4' controls autoplay muted playsinline loop preload='auto'></video><script>
                const v=document.querySelector('video');v.muted=true;
                const play=v.play();if(play)play.catch(e=>window.__fluxAutoplayError=String(e));
                </script></body></html>
                """.getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            try { exchange.sendResponseHeaders(200, page.length); exchange.getResponseBody().write(page); }
            finally { exchange.close(); }
        });
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { error.printStackTrace(); uncaught.compareAndSet(null, error); });
        server.start();
        Platform.startup(() -> Platform.setImplicitExit(false));
        var app = new FluxBrowser();
        try {
            fx(() -> {
                stage = new Stage(); app.start(stage); stage.setWidth(1280); stage.setHeight(820); stage.toFront();
                double scale = Double.parseDouble(System.getProperty("flux.renderScale", "0"));
                if (scale > 0) { stage.setRenderScaleX(scale); stage.setRenderScaleY(scale); }
                return null;
            });
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (fx(() -> ((Label) stage.getScene().lookup("#databaseStatus")).getText().contains("CONNECTING"))) {
                if (System.nanoTime() > deadline) throw new AssertionError("Storage initialization did not finish");
                Thread.sleep(100);
            }
            BrowserChecks.check(fx(() -> ((Label) stage.getScene().lookup("#databaseStatus")).getText().contains("OFFLINE")),
                    "Media observations require unavailable storage so navigation does not write history");
            System.out.println("WEB_MEDIA runtime=" + fx(() -> "{\"java\":" + quote(System.getProperty("java.version"))
                    + ",\"renderer\":" + quote(System.getProperty("prism.order") + "/" + System.getProperty("flux.engine")) + ",\"arch\":" + quote(System.getProperty("os.arch"))
                    + ",\"renderScaleX\":" + stage.getRenderScaleX() + ",\"renderScaleY\":" + stage.getRenderScaleY()
                    + ",\"outputScaleX\":" + stage.getOutputScaleX() + ",\"heapMiB\":" + Runtime.getRuntime().maxMemory()/1048576 + "}"));
            navigate("local", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            BrowserChecks.check(fx(() -> web().state.get() == Worker.State.SUCCEEDED), "Local video document loaded");
            if (fx(() -> web() instanceof com.flux.browser.web.NativeWebPage)) {
                BrowserChecks.equal(js("typeof MediaSource !== 'undefined' && typeof HTMLVideoElement.prototype.requestVideoFrameCallback === 'function'"), "true");
            }
            sample("local");
            BrowserChecks.check("true".equals(js("!!document.querySelector('video') && document.querySelector('video').currentTime > 0.1 && !document.querySelector('video').error")),
                    "Local H.264 playback advanced without a media error");
            if ("true".equals(System.getenv("FLUX_CHECK_SITES"))) {
                if (!"true".equals(System.getenv("FLUX_YOUTUBE_ONLY"))) {
                navigate("github", System.getenv().getOrDefault("FLUX_GITHUB_URL", "https://github.com/"));
                sample("github");
                navigate("youtube-search", "https://www.youtube.com/results?search_query=nature");
                sample("youtube-search");
                }
                navigate("youtube", System.getenv().getOrDefault("FLUX_YOUTUBE_URL", "https://www.youtube.com/watch?v=jNQXAC9IVRw"));
                prepareYouTube();
                sample("youtube");
            }
            BrowserChecks.check(uncaught.get() == null, "No uncaught JavaFX errors: " + uncaught.get());
            System.out.println("WebMediaChecks passed. Navigation DOM readiness, FX pulses, JS animation callbacks and decoded/presented video counters are distinct; none proves physical display FPS. Public sites may block or require consent/playback input.");
        } finally {
            try { fx(() -> { app.stop(); if (stage != null) stage.close(); return null; }); }
            finally { Platform.exit(); server.stop(0); http.shutdownNow(); }
        }
    }

    private static void navigate(String name, String url) throws Exception {
        long started = System.nanoTime();
        if (fx(WebMediaChecks::web) != null) {
            try { js("document.__fluxPrevious=true; if(window.__fluxProbe)window.__fluxProbe.stop(); true"); }
            catch (Exception ignored) { }
        }
        fx(() -> {
            stage.toFront(); stage.requestFocus();
            var bar = (TextField) stage.getScene().lookup("#addressBar");
            bar.setText(url); bar.fireEvent(new ActionEvent()); return null;
        });
        foreground();
        double firstDom = -1, success = -1;
        Worker.State state;
        do {
            state = fx(() -> web().state.get());
            if (firstDom < 0) {
                try { if ("true".equals(js("!document.__fluxPrevious && document.readyState !== 'loading' && !!document.body"))) firstDom = elapsed(started); }
                catch (Exception ignored) { }
            }
            if (state == Worker.State.SUCCEEDED) { success = elapsed(started); break; }
            if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) break;
            Thread.sleep(100);
        } while (elapsed(started) < 30_000);
        System.out.printf("WEB_MEDIA navigation={\"site\":%s,\"requestedUrl\":%s,\"elapsedMs\":%.1f,\"firstDomReadyObservedMs\":%.1f,\"workerSucceededObservedMs\":%.1f,\"workerState\":%s,\"page\":%s}%n",
                quote(name), quote(url), elapsed(started), firstDom, success, quote(state.name()), snapshot());
    }

    private static void foreground() throws Exception {
        if (!"true".equals(System.getenv("FLUX_CHECK_FOREGROUND"))) return;
        fx(() -> { if (web() instanceof NativeWebPage p) p.foregroundForTesting();
            else { stage.toFront(); stage.requestFocus(); } return null; });
        long start=System.nanoTime();
        while (elapsed(start)<5000) {
            if ("visible".equals(js("document.visibilityState"))) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Foreground test requires Flux's page to be visible; window activation did not succeed");
    }

    private static void prepareYouTube() throws Exception {
        foreground();
        String quality=System.getenv().getOrDefault("FLUX_YOUTUBE_QUALITY", "auto");
        if (!quality.matches("auto|hd1080|hd720")) throw new IllegalArgumentException("Use auto, hd720 or hd1080 for FLUX_YOUTUBE_QUALITY");
        long start=System.nanoTime();
        // Test-only request through player capabilities. Actual decoded dimensions determine the result.
        // Never assume the player honored a quality request or change production playback preferences.
        boolean ready=false;
        do {
            js("(() => {const p=document.getElementById('movie_player'),v=document.querySelector('video');"
                    + "if(!v)return false; v.muted=true; if(p && typeof p.mute==='function')p.mute();"
                    + "const quality="+quote(quality)+";"
                    + "if(quality!=='auto' && p && typeof p.getAvailableQualityLevels==='function'"
                    + " && p.getAvailableQualityLevels().includes(quality) && typeof p.setPlaybackQualityRange==='function')"
                    + "p.setPlaybackQualityRange(quality,quality);"
                    + "if(v.paused)v.play().catch(e=>window.__fluxAutoplayError=String(e)); return true;})()");
            ready="true".equals(js("(() => {const v=document.querySelector('video'); return !!v && !v.paused"
                    + " && v.readyState>=3 && v.currentTime>0.1 && v.videoHeight>="
                    + (quality.equals("hd1080")?1080:quality.equals("hd720")?720:1) + ";})()"));
            if (ready) break;
            Thread.sleep(500);
        } while(elapsed(start)<30000);
        System.out.printf("WEB_MEDIA playbackReady={\"requestedQuality\":%s,\"observed\":%s,\"waitAfterNavigationMs\":%.1f,\"page\":%s}%n",
                quote(quality), ready, elapsed(start), snapshot());
        if (!ready && !quality.equals("auto")) throw new AssertionError("YouTube did not start at requested quality " + quality);
    }

    private static void sample(String name) throws Exception {
        foreground();
        List<Double> pulses = new ArrayList<>(), queue = new ArrayList<>();
        var timer = new AnimationTimer() {
            long previous;
            @Override public void handle(long now) { if (previous != 0) pulses.add((now-previous)/1_000_000.0); previous=now; }
        };
        boolean instrumented;
        try { instrumented = "true".equals(js(INSTRUMENT)); } catch (Exception e) { instrumented = false; }
        long start = System.nanoTime();
        fx(() -> { timer.start(); return null; });
        while (elapsed(start) < 10_000) {
            long submitted = System.nanoTime(); queue.add(fx(() -> elapsed(submitted))); Thread.sleep(100);
        }
        fx(() -> { timer.stop(); return null; });
        String details = snapshot();
        boolean remainedVisible="true".equals(js("!!window.__fluxProbe && window.__fluxProbe.visibility.every(e=>e.state==='visible')"));
        if (instrumented) try { js("if(window.__fluxProbe)window.__fluxProbe.stop(); true"); } catch (Exception ignored) { }
        System.out.printf("WEB_MEDIA sample={\"site\":%s,\"elapsedMs\":%.1f,\"instrumented\":%s,\"fxPulse\":%s,\"fxQueue\":%s,\"page\":%s}%n",
                quote(name), elapsed(start), instrumented, statistics(pulses), statistics(queue), details);
        if ("true".equals(System.getenv("FLUX_CHECK_FOREGROUND"))) {
            BrowserChecks.check(instrumented && remainedVisible, "Foreground sample invalid: " + name + " became hidden (see visibilityEvents)");
        }
    }

    private static String snapshot() {
        try { return js(SNAPSHOT); }
        catch (Exception unavailable) { return "{\"scriptUnavailable\":" + quote(unavailable.toString()) + "}"; }
    }

    /** Single byte ranges cover WebKit's seek/read requests without loading the MP4 into the Java heap. */
    private static void serveVideo(HttpExchange exchange, Path file) throws java.io.IOException {
        try {
            long size=Files.size(file), start=0, end=size-1;
            String range=exchange.getRequestHeaders().getFirst("Range");
            var headers=exchange.getResponseHeaders();
            headers.set("Content-Type", "video/mp4"); headers.set("Accept-Ranges", "bytes");
            headers.set("Cache-Control", "no-store");
            if (range != null) {
                try {
                    if (!range.matches("bytes=\\d*-\\d*")) throw new IllegalArgumentException();
                    String[] parts=range.substring(6).split("-", -1);
                    if (parts[0].isEmpty()) {
                        long suffix=Long.parseLong(parts[1]);
                        if (suffix <= 0) throw new IllegalArgumentException();
                        start=Math.max(0, size-suffix);
                    } else {
                        start=Long.parseLong(parts[0]);
                        if (!parts[1].isEmpty()) end=Math.min(end, Long.parseLong(parts[1]));
                    }
                    if (start > end || start >= size) throw new IllegalArgumentException();
                } catch (IllegalArgumentException invalid) {
                    headers.set("Content-Range", "bytes */" + size); exchange.sendResponseHeaders(416,-1); return;
                }
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + size);
            }
            long remaining=end-start+1;
            headers.set("Content-Length", Long.toString(remaining));
            boolean head=exchange.getRequestMethod().equals("HEAD");
            exchange.sendResponseHeaders(range == null ? 200 : 206, head ? -1 : remaining);
            if (!head) try (var channel=FileChannel.open(file, StandardOpenOption.READ)) {
                channel.position(start); var buffer=ByteBuffer.allocate(64*1024);
                while (remaining > 0) {
                    buffer.clear(); buffer.limit((int)Math.min(buffer.capacity(),remaining));
                    int count=channel.read(buffer); if(count < 0)break;
                    exchange.getResponseBody().write(buffer.array(),0,count); remaining-=count;
                }
            }
        } finally { exchange.close(); }
    }

    private static String statistics(List<Double> values) {
        var sorted=new ArrayList<>(values); Collections.sort(sorted);
        return String.format(java.util.Locale.ROOT, "{\"count\":%d,\"medianMs\":%.2f,\"p95Ms\":%.2f,\"maxMs\":%.2f}",
                sorted.size(), percentile(sorted,.5), percentile(sorted,.95), percentile(sorted,1));
    }
    private static double percentile(List<Double> sorted, double fraction) {
        return sorted.isEmpty()?0:sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*fraction)-1));
    }
    private static double elapsed(long since) { return (System.nanoTime()-since)/1_000_000.0; }
    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    private static BrowserPage web() {
        return stage.getScene().getRoot().lookupAll(".web-tab").stream().filter(javafx.scene.Node::isVisible)
                .findFirst().map(node -> (BrowserPage)node.getProperties().get("browserPage")).orElse(null);
    }
    private static String js(String script) throws Exception { return fx(() -> web().evaluate(script)).get(20, TimeUnit.SECONDS); }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task=new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
