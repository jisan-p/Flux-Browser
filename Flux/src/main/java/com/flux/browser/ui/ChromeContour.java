package com.flux.browser.ui;

import javafx.animation.AnimationTimer;
import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.*;

/** Foreground window accent. Repaints only after geometry or theme changes. */
public final class ChromeContour extends Canvas {
    private Node selected;
    private Color accent = Color.web("#fa1e4e");
    private final AnimationTimer refresh = new AnimationTimer() {
        @Override public void handle(long now) { stop(); paint(); }
    };
    private final InvalidationListener geometry = o -> refresh.start();
    public ChromeContour() {
        setMouseTransparent(true);
        widthProperty().addListener(geometry); heightProperty().addListener(geometry);
    }
    public void select(Node tab) {
        if (selected != null) {
            selected.localToSceneTransformProperty().removeListener(geometry);
            selected.boundsInLocalProperty().removeListener(geometry);
        }
        selected = tab;
        if (tab != null) {
            tab.localToSceneTransformProperty().addListener(geometry);
            tab.boundsInLocalProperty().addListener(geometry);
        }
        refresh.start();
    }
    public void accent(String value) { accent = Color.web(value); refresh.start(); }
    private void paint() {
        var g = getGraphicsContext2D(); double w = getWidth(), h = getHeight();
        g.clearRect(0, 0, w, h);
        if (w < 40 || h < 40) return;
        double left = 54, right = 244;
        if (selected != null && selected.getScene() != null) {
            var bounds = sceneToLocal(selected.localToScene(selected.getBoundsInLocal()));
            left = Math.max(20, Math.min(w - 30, bounds.getMinX()));
            right = Math.max(left + 8, Math.min(w - 15, bounds.getMaxX()));
        }
        g.setLineWidth(1.2);
        g.setStroke(new LinearGradient(0, 0, w, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0, accent), new Stop(.65, accent.deriveColor(0,1,1,.7)), new Stop(1, Color.TRANSPARENT)));
        g.beginPath(); g.moveTo(.6, 15); g.lineTo(15, .6);
        g.lineTo(left - 4, .6); g.lineTo(left, 3.6); g.lineTo(right, 3.6);
        g.lineTo(right + 4, .6); g.lineTo(w - 12, .6); g.stroke();
        g.setStroke(new LinearGradient(0, 12, 0, Math.min(h, 400), false, CycleMethod.NO_CYCLE,
                new Stop(0, accent), new Stop(.35, accent.deriveColor(0,1,1,.35)), new Stop(1, Color.TRANSPARENT)));
        g.strokeLine(.6, 15, .6, Math.min(h, 400));
    }
    public void dispose() { select(null); refresh.stop(); }
}
