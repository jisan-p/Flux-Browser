package com.flux.browser.ui;

import com.flux.browser.feature.Appearance;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.paint.*;

/** Original procedural artwork; one coalesced redraw on resize/theme change, no idle rendering loop. */
public final class WallpaperCanvas extends Canvas {
    private Appearance settings = new Appearance();
    private Image image;
    private String imageUrl = "";
    private boolean light;
    private final AnimationTimer redraw = new AnimationTimer() { @Override public void handle(long now) { stop(); paint(); } };
    public WallpaperCanvas() {
        setMouseTransparent(true);
        widthProperty().addListener(o -> redraw.start()); heightProperty().addListener(o -> redraw.start());
    }
    public void configure(Appearance value, boolean light) {
        this.settings = value.copy(); this.light = light;
        String url = settings.wallpapers && settings.wallpaper.equals("Custom") ? settings.image : "";
        if (!url.equals(imageUrl)) {
            if (image != null) image.cancel();
            imageUrl = url; image = url.isEmpty() ? null : new Image(url, 2048, 1280, true, true, true);
            if (image != null) { image.progressProperty().addListener(o -> redraw.start()); image.errorProperty().addListener(o -> redraw.start()); }
        }
        setEffect(settings.blur > 0 ? new GaussianBlur(settings.blur) : null);
        redraw.start();
    }
    private void paint() {
        double w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
        var g = getGraphicsContext2D();
        Color base = light ? Color.web("#eeeaf3") : Color.web(settings.base).deriveColor(0,0.75,0.35,1);
        Color accent = Color.web(settings.accent);
        g.setGlobalAlpha(1); g.setFill(base); g.fillRect(0,0,w,h);
        if (!settings.wallpapers) return;
        g.setGlobalAlpha(settings.wallpaperBrightness);
        if (image != null && image.getProgress() == 1 && !image.isError()) {
            double scale = Math.max(w/image.getWidth(), h/image.getHeight());
            g.drawImage(image, (w-image.getWidth()*scale)/2,(h-image.getHeight()*scale)/2,image.getWidth()*scale,image.getHeight()*scale);
        } else {
            g.setFill(new RadialGradient(0,0,w*.8,h*.85,w*.8,false,CycleMethod.NO_CYCLE,
                new Stop(0,accent.deriveColor(0,0.6,0.22,0.7)),new Stop(1,Color.TRANSPARENT)));
            g.fillRect(0,0,w,h);
            if (settings.wallpaper.equals("Grid")) {
                g.setStroke(accent.deriveColor(0,1,0.7,0.35)); g.setLineWidth(1);
                for (int i=-12;i<=22;i++) g.strokeLine(w*.55,h*.43,i*w/10,h);
                for (int i=0;i<20;i++) { double y=h*.43+h*.57*Math.pow(i/19.0,2); g.strokeLine(0,y,w,y); }
            } else {
                boolean aurora=settings.wallpaper.equals("Aurora");
                for (int line=0;line<160;line++) {
                    double t=line/159.0;
                    boolean neon=line%41<5;
                    Color strand=neon ? accent : (light ? Color.web("#6f6c86") : Color.web("#516174"));
                    g.setStroke(strand.deriveColor(0,1,0.35+0.65*Math.pow(Math.sin(t*43),2),neon?.9:.75));
                    g.setLineWidth(neon?2.1:1.4); g.beginPath();
                    for (int point=0;point<=110;point++) {
                        double x=point/110.0;
                        double wave=Math.sin(x*9+t*3)*.075+Math.sin(x*21-t*5)*.018;
                        double y=aurora ? .65+Math.sin(x*5+t*2)*.22+t*.23 : .88-x*.34+wave+t*t*.46;
                        if (point==0) g.moveTo(x*w,y*h); else g.lineTo(x*w,y*h);
                    }
                    g.stroke();
                }
            }
        }
        g.setGlobalAlpha(1);
        if(settings.vignette>0) {
            g.setFill(new RadialGradient(0,0,.5,.45,.72,true,CycleMethod.NO_CYCLE,
                new Stop(0,Color.TRANSPARENT),new Stop(1,(light ? Color.WHITE : Color.BLACK).deriveColor(0,1,1,settings.vignette))));
            g.fillRect(0,0,w,h);
        }
    }
    public void dispose() { redraw.stop(); if(image!=null)image.cancel(); }
}
