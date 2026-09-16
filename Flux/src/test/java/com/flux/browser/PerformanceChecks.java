package com.flux.browser;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/** Repeatable local rendering workload; reports latency, never claims a site-independent FPS. */
public final class PerformanceChecks {
    private static Stage stage;
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        if (!"false".equals(System.getenv("FLUX_EXPECT_STORAGE"))) {
            throw new IllegalArgumentException("Use FLUX_EXPECT_STORAGE=false and an unavailable FLUX_DB_URL for this read-only workload.");
        }
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { error.printStackTrace(); failure.compareAndSet(null, error); });
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var http = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(http);
        byte[] html = ("""
                <!doctype html><html><head><title>Flux rendering workload</title><style>
                body{background:#171721;color:white;font:16px system-ui;margin:0;padding:24px}
                #cards{display:grid;grid-template-columns:repeat(6,1fr);gap:12px}
                article{background:linear-gradient(135deg,#843455,#263859);height:110px;border-radius:10px;padding:12px}
                #meter{position:fixed;top:0;left:0;width:80px;height:8px;background:cyan}
                </style></head><body><input placeholder='Typing remains available'><h1>Rendering workload</h1>
                <div id='meter'></div><div id='cards'></div><script>
                cards.innerHTML=Array.from({length:180},(_,i)=>'<article>Card '+i+'<p>Live content</p></article>').join('');
                window.framesSeen=0; function frame(t){framesSeen++;meter.style.transform='translateX('+(t/8%900)+'px)';
                window.scrollTo(0,(Math.sin(t/1800)+1)*900);requestAnimationFrame(frame)}requestAnimationFrame(frame);
                </script></body></html>
                """).getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            try { exchange.sendResponseHeaders(200, html.length); exchange.getResponseBody().write(html); }
            finally { exchange.close(); }
        });
        server.start();
        Platform.startup(() -> Platform.setImplicitExit(false));
        var app = new FluxBrowser();
        List<Double> gaps = new ArrayList<>();
        var pulse = new AnimationTimer() {
            long before;
            @Override public void handle(long now) { if (before != 0) gaps.add((now - before) / 1_000_000.0); before = now; }
        };
        try {
            long startup = System.nanoTime();
            fx(() -> { stage = new Stage(); app.start(stage); stage.setWidth(1280); stage.setHeight(820); return null; });
            System.out.printf("PERF startup=%.1fms arch=%s renderer=%s heapLimit=%dMiB%n", elapsed(startup),
                    System.getProperty("os.arch"), System.getProperty("prism.order"), Runtime.getRuntime().maxMemory() / 1048576);
            List<Double> newTabs = new ArrayList<>();
            int blankEngines = fx(() -> web() == null ? 0 : 1);
            for (int i = 0; i < 11; i++) {
                long start = System.nanoTime();
                fx(() -> { button("newTabButton").fire(); return null; });
                newTabs.add(elapsed(start));
                blankEngines += fx(() -> web() == null ? 0 : 1);
                Thread.sleep(60);
            }
            System.out.printf("PERF blankTabs=12 allocatedWebViews=%d newTabMedian=%.1fms newTabP95=%.1fms%n",
                    blankEngines, percentile(newTabs, .5), percentile(newTabs, .95));
            if (Boolean.getBoolean("flux.check.lazy")) BrowserChecks.equal(blankEngines, 0);
            for (int i = 0; i < 11; i++) fx(() -> { shortcut(KeyCode.W); return null; });
            String address = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            for (int i = 0; i < 3; i++) {
                final int index = i;
                fx(() -> {
                    if (index > 0) button("newTabButton").fire();
                    var bar = (TextField) stage.getScene().lookup("#addressBar");
                    bar.setText(address + "?tab=" + index); bar.fireEvent(new ActionEvent()); return null;
                });
                long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                while (!fx(() -> web() != null && web().getEngine().getLoadWorker().getState() == Worker.State.SUCCEEDED)) {
                    if (System.nanoTime() > deadline) throw new AssertionError("Fixture page did not load");
                    Thread.sleep(50);
                }
            }
            Thread.sleep(1000);
            fx(() -> { pulse.start(); return null; });
            List<Double> input = new ArrayList<>();
            List<Double> queue = new ArrayList<>();
            List<Double> switches = new ArrayList<>();
            List<Double> resizes = new ArrayList<>();
            List<String> slowOperations = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                final int tick = i;
                long start = System.nanoTime();
                var timing = fx(() -> {
                    double queued = elapsed(start), switched = 0, resized = 0;
                    if (tick % 10 == 0) {
                        long began = System.nanoTime();
                        shortcut(switch ((tick / 10) % 3) { case 0 -> KeyCode.DIGIT1; case 1 -> KeyCode.DIGIT2; default -> KeyCode.DIGIT3; });
                        switched = elapsed(began);
                    }
                    if (tick % 20 == 0) {
                        long began = System.nanoTime();
                        stage.setWidth(tick % 40 == 0 ? 1100 : 1280); stage.setHeight(tick % 40 == 0 ? 720 : 820);
                        resized = elapsed(began);
                    }
                    return new Timing(queued, switched, resized);
                });
                double total = elapsed(start);
                input.add(total); queue.add(timing.queued());
                if (tick % 10 == 0) switches.add(timing.switched());
                if (tick % 20 == 0) resizes.add(timing.resized());
                if (Boolean.getBoolean("flux.perf.verbose") && total >= Long.getLong("flux.perf.slowMillis", 50L)) {
                    slowOperations.add(String.format("PERF slow tick=%d action=%s total=%.1fms queue=%.1fms switch=%.1fms resize=%.1fms",
                            tick, tick % 20 == 0 ? "switch+resize" : tick % 10 == 0 ? "switch" : "queue-probe",
                            total, timing.queued(), timing.switched(), timing.resized()));
                }
                Thread.sleep(75);
            }
            fx(() -> { pulse.stop(); return null; });
            System.out.printf("PERF animatedTabs=3 pulses=%d pulseMedian=%.1fms pulseP95=%.1fms pulseMax=%.1fms inputP95=%.1fms inputMax=%.1fms%n",
                    gaps.size(), percentile(gaps, .5), percentile(gaps, .95), percentile(gaps, 1), percentile(input, .95), percentile(input, 1));
            // Queue delay and synchronous command time are distinct; neither measures presentation latency.
            report("queue", queue);
            report("switchExecution", switches);
            report("resizeExecution", resizes);
            slowOperations.forEach(System.out::println);
            BrowserChecks.check(gaps.size() > 30, "Rendering pulses continued under load");
            BrowserChecks.check(fx(() -> ((Number) web().getEngine().executeScript("framesSeen")).intValue()) > 10, "WebKit animation progressed");
            BrowserChecks.check(failure.get() == null, "No uncaught rendering errors: " + failure.get());
            System.out.println("PerformanceChecks passed (local workload; latency values are observations, not guarantees).");
        } finally {
            fx(() -> { pulse.stop(); app.stop(); if (stage != null) stage.close(); return null; });
            Platform.exit(); server.stop(0); http.shutdownNow();
        }
    }

    private record Timing(double queued, double switched, double resized) {}
    private static void report(String label, List<Double> values) {
        System.out.printf("PERF %sSamples=%d %sMedian=%.1fms %sP95=%.1fms %sMax=%.1fms%n",
                label, values.size(), label, percentile(values, .5), label, percentile(values, .95), label, percentile(values, 1));
    }
    private static double elapsed(long since) { return (System.nanoTime() - since) / 1_000_000.0; }
    private static double percentile(List<Double> values, double fraction) {
        var sorted = new ArrayList<>(values); Collections.sort(sorted);
        return sorted.isEmpty() ? 0 : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * fraction) - 1));
    }
    private static Button button(String id) { return (Button) stage.getScene().lookup("#" + id); }
    private static WebView web() {
        return stage.getScene().getRoot().lookupAll(".web-tab").stream().filter(javafx.scene.Node::isVisible)
                .findFirst().map(node -> (WebView) node.lookup("#webView")).orElse(null);
    }
    private static void shortcut(KeyCode code) {
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        stage.getScene().getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, !mac, false, mac));
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
