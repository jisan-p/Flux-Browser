package org.custombrowser.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.custombrowser.application.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import com.sun.net.httpserver.HttpServer;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

@TestMethodOrder(OrderAnnotation.class)
class BrowserUiSmokeTest {

    private static final Duration UI_TIMEOUT = Duration.ofSeconds(15);
    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean();

    @BeforeAll
    static void startJavaFx() throws Exception {
        Assumptions.assumeTrue(
                graphicalEnvironmentAvailable(),
                "JavaFX UI smoke tests require a graphical desktop session");

        if (TOOLKIT_STARTED.compareAndSet(false, true)) {
            CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            assertTrue(started.await(UI_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        }
    }

    @AfterAll
    static void stopJavaFx() {
        if (TOOLKIT_STARTED.get()) {
            Platform.exit();
        }
    }

    @Test
    @Order(1)
    void browserFxmlLoadsWithCompositionRoot() throws Exception {
        try (ApplicationContext context = ApplicationContext.createDefault()) {
            LoadedFxml loaded = callOnJavaFxThread(() -> {
                FXMLLoader loader = new FXMLLoader(
                        BrowserWindow.class.getResource("browser.fxml"));
                loader.setControllerFactory(context::createController);
                Parent root = loader.load();
                return new LoadedFxml(root, loader.getController());
            });

            assertInstanceOf(StackPane.class, loaded.root());
            assertInstanceOf(BrowserController.class, loaded.controller());
            assertNotNull(loaded.root().lookup(".title-bar"));
            assertNotNull(loaded.root().lookup(".navigation-bar"));
            assertNotNull(loaded.root().lookup(".sidebar"));
            assertNotNull(loaded.root().lookup(".start-page"));
            assertNotNull(loaded.root().lookup(".easy-setup-panel"));
        }
    }

    @Test
    @Order(2)
    void webViewLoadsPageFromLocalHttpServer() throws Exception {
        HttpServer server = createLocalServer();
        server.start();

        try {
            String pageUrl = "http://127.0.0.1:"
                    + server.getAddress().getPort()
                    + "/";
            CountDownLatch finished = new CountDownLatch(1);
            AtomicReference<Worker.State> finalState = new AtomicReference<>();

            WebView webView = callOnJavaFxThread(WebView::new);
            callOnJavaFxThread(() -> {
                webView.getEngine().getLoadWorker().stateProperty()
                        .addListener((observable, oldState, newState) -> {
                            if (newState == Worker.State.SUCCEEDED
                                    || newState == Worker.State.FAILED
                                    || newState == Worker.State.CANCELLED) {
                                finalState.set(newState);
                                finished.countDown();
                            }
                        });
                webView.getEngine().load(pageUrl);
                return null;
            });

            assertTrue(
                    finished.await(UI_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "Local test page did not finish loading");
            assertEquals(Worker.State.SUCCEEDED, finalState.get());
            assertEquals(
                    "Flux smoke test",
                    callOnJavaFxThread(() -> webView.getEngine()
                            .executeScript("document.title")));
            assertEquals(
                    "Local JavaFX WebView is working",
                    callOnJavaFxThread(() -> webView.getEngine()
                            .executeScript(
                                    "document.querySelector('h1').textContent")));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer createLocalServer() throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = """
                    <!doctype html>
                    <html>
                      <head><title>Flux smoke test</title></head>
                      <body><h1>Local JavaFX WebView is working</h1></body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(
                    "Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        return server;
    }

    private static boolean graphicalEnvironmentAvailable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (!osName.contains("linux")) {
            return true;
        }
        return nonBlank(System.getenv("DISPLAY"))
                || nonBlank(System.getenv("WAYLAND_DISPLAY"));
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> T callOnJavaFxThread(Callable<T> task) throws Exception {
        assertNotNull(task);
        if (Platform.isFxApplicationThread()) {
            return task.call();
        }

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                result.set(task.call());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });

        assertTrue(
                completed.await(UI_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                "JavaFX operation timed out");
        if (failure.get() != null) {
            if (failure.get() instanceof Exception exception) {
                throw exception;
            }
            throw new AssertionError(failure.get());
        }
        return result.get();
    }

    private record LoadedFxml(Parent root, Object controller) {
    }
}
