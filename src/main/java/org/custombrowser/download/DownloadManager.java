package org.custombrowser.download;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.custombrowser.download.DownloadTask.Status;
import org.custombrowser.persistence.PersistenceModels.Download;
import org.custombrowser.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Streams downloads off the JavaFX thread into a temporary .part file.
 */
public final class DownloadManager implements AutoCloseable {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DownloadManager.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);
    static final int MAX_RETAINED_TASKS = 100;

    private final PersistenceService persistenceService;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final ObservableList<DownloadTask> tasks =
            FXCollections.observableArrayList();
    private Path lastDirectory;
    private volatile boolean closed;

    public DownloadManager(PersistenceService persistenceService) {
        this.persistenceService = Objects.requireNonNull(
                persistenceService, "persistenceService");
        executor = Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "flux-download");
            thread.setDaemon(true);
            return thread;
        });
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String savedDirectory = persistenceService.startupState()
                .settings().get("download_directory");
        if (savedDirectory != null && !savedDirectory.isBlank()) {
            try {
                Path candidate = Path.of(savedDirectory);
                if (Files.isDirectory(candidate)) {
                    lastDirectory = candidate;
                }
            } catch (RuntimeException ignored) {
                // Ignore an invalid path saved by a previous environment.
            }
        }
    }

    public ObservableList<DownloadTask> tasks() {
        return FXCollections.unmodifiableObservableList(tasks);
    }

    public boolean chooseAndStart(URI source, Window owner) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException(
                    "The download chooser must run on the JavaFX thread");
        }
        if (closed || !DownloadDetector.isLikelyDownload(source)) {
            return false;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save download");
        chooser.setInitialFileName(DownloadDetector.suggestedFileName(source));
        if (lastDirectory != null && Files.isDirectory(lastDirectory)) {
            chooser.setInitialDirectory(lastDirectory.toFile());
        }
        java.io.File selected = chooser.showSaveDialog(owner);
        if (selected == null) {
            return false;
        }
        Path destination = DownloadDetector.uniqueDestination(selected.toPath());
        lastDirectory = destination.getParent();
        persistenceService.saveSetting(
                "download_directory",
                lastDirectory.toString());
        start(source, destination);
        return true;
    }

    public void cancel(DownloadTask task) {
        if (task == null
                || (task.status() != Status.RUNNING
                && task.status() != Status.QUEUED)) {
            return;
        }
        boolean wasQueued = task.status() == Status.QUEUED;
        task.cancellationRequested.set(true);
        closeQuietly(task.activeStream);
        if (task.activeFuture != null) {
            task.activeFuture.cancel(true);
        }
        if (wasQueued) {
            updateOnFx(task, Status.CANCELLED, 0, -1, null);
            persist(task, Status.CANCELLED, 0, null, null);
        }
    }

    public void retry(DownloadTask task) {
        if (task == null
                || (task.status() != Status.FAILED
                && task.status() != Status.CANCELLED)
                || closed) {
            return;
        }
        task.cancellationRequested.set(false);
        updateOnFx(task, Status.QUEUED, 0, -1, null);
        execute(task);
    }

    public void open(DownloadTask task) {
        if (task != null && task.status() == Status.COMPLETED) {
            openPath(task.destination());
        }
    }

    public void reveal(DownloadTask task) {
        if (task != null && task.destination().getParent() != null) {
            openPath(task.destination().getParent());
        }
    }

    @Override
    public void close() {
        closed = true;
        List.copyOf(tasks).stream()
                .filter(task -> task.status() == Status.RUNNING
                        || task.status() == Status.QUEUED)
                .forEach(this::cancel);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException error) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void start(URI source, Path destination) {
        persistenceService.createDownload(
                        source.toString(),
                        destination.getFileName().toString(),
                        destination.toString())
                .thenAccept(download -> Platform.runLater(() -> {
                    if (closed) {
                        persistenceService.updateDownload(
                                download.id(),
                                Status.CANCELLED.name(),
                                0,
                                null,
                                Instant.now(),
                                "Browser closed before transfer started");
                        return;
                    }
                    DownloadTask task = new DownloadTask(
                            download.id(), source, destination);
                    tasks.add(task);
                    execute(task);
                }));
    }

    private void execute(DownloadTask task) {
        task.activeFuture = executor.submit(() -> transfer(task));
    }

    private void transfer(DownloadTask task) {
        Path part = DownloadDetector.partPath(task.destination());
        long bytes = 0;
        long total = -1;
        try {
            Files.createDirectories(task.destination().getParent());
            HttpRequest request = HttpRequest.newBuilder(task.source())
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "FluxBrowser/0.4")
                    .GET()
                    .build();
            updateOnFx(task, Status.RUNNING, 0, -1, null);
            persist(task, Status.RUNNING, 0, null, null);
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("Server returned HTTP "
                        + response.statusCode());
            }
            total = response.headers().firstValueAsLong("Content-Length")
                    .orElse(-1);
            task.activeStream = response.body();
            long lastUiUpdate = 0;
            long lastDatabaseUpdate = 0;
            try (InputStream input = task.activeStream;
                    OutputStream output = Files.newOutputStream(
                            part,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (task.cancellationRequested.get()
                            || Thread.currentThread().isInterrupted()) {
                        throw new DownloadCancelledException();
                    }
                    output.write(buffer, 0, read);
                    bytes += read;
                    long now = System.nanoTime();
                    if (now - lastUiUpdate >= 100_000_000L) {
                        updateOnFx(task, Status.RUNNING, bytes, total, null);
                        lastUiUpdate = now;
                    }
                    if (now - lastDatabaseUpdate >= 500_000_000L) {
                        persist(task, Status.RUNNING, bytes,
                                total < 0 ? null : total, null);
                        lastDatabaseUpdate = now;
                    }
                }
            } finally {
                task.activeStream = null;
            }
            moveCompleted(part, task.destination());
            updateOnFx(task, Status.COMPLETED, bytes, total, null);
            persist(task, Status.COMPLETED, bytes,
                    total < 0 ? null : total, null);
        } catch (DownloadCancelledException error) {
            deletePart(part);
            updateOnFx(task, Status.CANCELLED, bytes, total, null);
            persist(task, Status.CANCELLED, bytes,
                    total < 0 ? null : total, null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            deletePart(part);
            updateOnFx(task, Status.CANCELLED, bytes, total, null);
            persist(task, Status.CANCELLED, bytes,
                    total < 0 ? null : total, null);
        } catch (Exception error) {
            deletePart(part);
            if (task.cancellationRequested.get()) {
                updateOnFx(task, Status.CANCELLED, bytes, total, null);
                persist(task, Status.CANCELLED, bytes,
                        total < 0 ? null : total, null);
                return;
            }
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            updateOnFx(task, Status.FAILED, bytes, total, message);
            persist(task, Status.FAILED, bytes,
                    total < 0 ? null : total, message);
        }
    }

    private void persist(
            DownloadTask task,
            Status status,
            long bytes,
            Long total,
            String failure) {
        persistenceService.updateDownload(
                task.id(),
                status.name(),
                bytes,
                total,
                status == Status.COMPLETED
                        || status == Status.CANCELLED
                        || status == Status.FAILED
                        ? Instant.now()
                        : null,
                failure);
    }

    private void updateOnFx(
            DownloadTask task,
            Status status,
            long bytes,
            long total,
            String failure) {
        Platform.runLater(() -> {
            task.update(status, bytes, total, failure);
            if (isTerminal(status)) {
                task.releaseRuntimeReferences();
                pruneTerminalTasks();
            }
        });
    }

    private void pruneTerminalTasks() {
        while (tasks.size() > MAX_RETAINED_TASKS) {
            DownloadTask removable = tasks.stream()
                    .filter(task -> isTerminal(task.status()))
                    .findFirst()
                    .orElse(null);
            if (removable == null) {
                return;
            }
            tasks.remove(removable);
        }
    }

    private static boolean isTerminal(Status status) {
        return status == Status.COMPLETED
                || status == Status.CANCELLED
                || status == Status.FAILED;
    }

    private void openPath(Path path) {
        executor.execute(() -> {
            try {
                if (!Desktop.isDesktopSupported()) {
                    throw new IOException("Desktop integration is unavailable");
                }
                Desktop.getDesktop().open(path.toFile());
            } catch (IOException | UnsupportedOperationException error) {
                LOGGER.warn("Unable to open {}: {}", path, error.getMessage());
            }
        });
    }

    private static void moveCompleted(Path part, Path destination)
            throws IOException {
        try {
            Files.move(
                    part,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(part, destination);
        }
    }

    private static void deletePart(Path part) {
        try {
            Files.deleteIfExists(part);
        } catch (IOException error) {
            LOGGER.warn("Unable to remove partial download {}: {}",
                    part, error.getMessage());
        }
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class DownloadCancelledException extends IOException {
    }
}
