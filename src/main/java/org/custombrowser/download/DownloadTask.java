package org.custombrowser.download;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class DownloadTask {

    public enum Status {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private final long id;
    private final URI source;
    private final Path destination;
    private final ObjectProperty<Status> status =
            new SimpleObjectProperty<>(Status.QUEUED);
    private final LongProperty bytesDownloaded = new SimpleLongProperty();
    private final LongProperty totalBytes = new SimpleLongProperty(-1);
    private final DoubleProperty progress = new SimpleDoubleProperty(-1);
    private final StringProperty failureMessage = new SimpleStringProperty();
    final AtomicBoolean cancellationRequested = new AtomicBoolean();
    volatile InputStream activeStream;
    volatile Future<?> activeFuture;

    DownloadTask(long id, URI source, Path destination) {
        this.id = id;
        this.source = source;
        this.destination = destination;
    }

    public long id() {
        return id;
    }

    public URI source() {
        return source;
    }

    public Path destination() {
        return destination;
    }

    public ObjectProperty<Status> statusProperty() {
        return status;
    }

    public LongProperty bytesDownloadedProperty() {
        return bytesDownloaded;
    }

    public LongProperty totalBytesProperty() {
        return totalBytes;
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public StringProperty failureMessageProperty() {
        return failureMessage;
    }

    public Status status() {
        return status.get();
    }

    void update(Status next, long bytes, long total, String failure) {
        status.set(next);
        bytesDownloaded.set(bytes);
        totalBytes.set(total);
        progress.set(total > 0 ? Math.min(1.0, (double) bytes / total) : -1.0);
        failureMessage.set(failure);
    }

    void releaseRuntimeReferences() {
        activeStream = null;
        activeFuture = null;
    }

    @Override
    public String toString() {
        long total = totalBytes.get();
        String size = total > 0
                ? bytesDownloaded.get() + " / " + total + " bytes"
                : bytesDownloaded.get() + " bytes";
        return destination.getFileName() + "  ·  " + status.get()
                + "\n" + size;
    }
}
