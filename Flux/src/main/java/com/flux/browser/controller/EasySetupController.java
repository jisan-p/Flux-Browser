package com.flux.browser.controller;

import com.flux.browser.feature.Appearance;
import com.flux.browser.feature.FeatureStore;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

/** Instant visual settings with debounced persistence owned by BrowserController. */
public final class EasySetupController implements AutoCloseable {
    @FXML private ComboBox<String> mode, wallpaper, layout, font, preset, tileEffect, dialPosition;
    @FXML private ScrollPane root;
    @FXML private ColorPicker accent, base;
    @FXML private Slider opacity, brightness, blur, vignette, fontSize, columns, volume;
    @FXML private CheckBox wallpapers, sidebar, search, dials, clock, tileTitles, statusBar, elementBackgrounds, animations, sounds, typingSounds, musicEnabled, bigTiles;
    @FXML private Label feedback, imageName, musicName;
    @FXML private TextField presetName;
    private BrowserController browser;
    private boolean updating, closed;
    private final ExecutorService files = new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->new Thread(r,"flux-appearance-files"),new ThreadPoolExecutor.AbortPolicy());
    public void configure(BrowserController browser) {
        this.browser=browser;
        mode.getItems().setAll("Light","Auto","Dark"); wallpaper.getItems().setAll("Waves","Aurora","Grid","Custom");
        tileEffect.getItems().setAll("None","Zoom","Glow","Lift");dialPosition.getItems().setAll("Auto","Top","Center","Bottom");
        layout.getItems().setAll("Compact","Comfortable","Spacious");
        font.getItems().setAll(Font.getFamilies().stream().filter(s->s.matches("[\\p{L}\\p{N} _-]{1,64}")).toList());
        if(!font.getItems().contains("System"))font.getItems().addFirst("System");
        for(var combo:List.of(mode,wallpaper,layout,font,tileEffect,dialPosition))combo.valueProperty().addListener(o->change());
        for(var slider:List.of(opacity,brightness,blur,vignette,fontSize,columns,volume))slider.valueProperty().addListener(o->change());
        for(var check:List.of(wallpapers,sidebar,search,dials,clock,tileTitles,statusBar,elementBackgrounds,animations,sounds,typingSounds,musicEnabled,bigTiles))check.selectedProperty().addListener(o->change());
        accent.valueProperty().addListener(o->change());base.valueProperty().addListener(o->change());show();
    }
    public void show() {
        if(browser==null)return;
        updating=true; var a=browser.preferences().appearance;
        tileEffect.setValue(a.tileEffect);dialPosition.setValue(a.dialPosition);bigTiles.setSelected(a.bigTiles);
        for(var node:root.lookupAll(".theme-swatch"))node.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"),a.theme.equals(String.valueOf(node.getUserData())));
        mode.setValue(a.mode);wallpaper.setValue(a.wallpaper);layout.setValue(a.layout);font.setValue(a.font);
        accent.setValue(Color.web(a.accent));base.setValue(Color.web(a.base));
        opacity.setValue(a.opacity*100);brightness.setValue(a.wallpaperBrightness*100);blur.setValue(a.blur);vignette.setValue(a.vignette*100);
        fontSize.setValue(a.fontSize);columns.setValue(a.columns);volume.setValue(a.volume*100);
        wallpapers.setSelected(a.wallpapers);sidebar.setSelected(a.sidebar);search.setSelected(a.search);dials.setSelected(a.dials);clock.setSelected(a.clock);
        tileTitles.setSelected(a.tileTitles);statusBar.setSelected(a.statusBar);elementBackgrounds.setSelected(a.elementBackgrounds);animations.setSelected(a.animations);
        sounds.setSelected(a.sounds);typingSounds.setSelected(a.typingSounds);musicEnabled.setSelected(a.musicEnabled);
        imageName.setText(fileName(a.image,"No custom image selected"));musicName.setText(fileName(a.music,"No music file selected"));
        preset.getItems().setAll(browser.preferences().appearancePresets.keySet());
        updating=false;
    }
    private void change() {
        if(updating || browser==null || closed)return;
        var a=browser.preferences().appearance;
        a.tileEffect=tileEffect.getValue();a.dialPosition=dialPosition.getValue();a.bigTiles=bigTiles.isSelected();
        a.mode=mode.getValue();a.wallpaper=wallpaper.getValue();a.layout=layout.getValue();a.font=font.getValue();
        String next=color(accent.getValue());if(!next.equals(a.accent))a.theme="Custom";a.accent=next;a.base=color(base.getValue());
        a.opacity=opacity.getValue()/100;a.wallpaperBrightness=brightness.getValue()/100;a.blur=blur.getValue();a.vignette=vignette.getValue()/100;
        a.fontSize=fontSize.getValue();a.columns=(int)Math.round(columns.getValue());a.volume=volume.getValue()/100;
        a.wallpapers=wallpapers.isSelected();a.sidebar=sidebar.isSelected();a.search=search.isSelected();a.dials=dials.isSelected();a.clock=clock.isSelected();
        a.tileTitles=tileTitles.isSelected();a.statusBar=statusBar.isSelected();a.elementBackgrounds=elementBackgrounds.isSelected();a.animations=animations.isSelected();
        a.sounds=sounds.isSelected();a.typingSounds=typingSounds.isSelected();a.musicEnabled=musicEnabled.isSelected();
        browser.appearanceChanged();
    }
    @FXML private void theme(ActionEvent event) { browser.preferences().appearance.theme(String.valueOf(((Control)event.getSource()).getUserData()));browser.appearanceChanged();show(); }
    @FXML private void reset() { browser.preferences().appearance=new Appearance();browser.appearanceChanged();show();feedback.setText("Default appearance restored."); }
    @FXML private void savePreset() {
        String name=presetName.getText().trim();var presets=browser.preferences().appearancePresets;
        if(name.isBlank() || name.length()>40 || !presets.containsKey(name) && presets.size()>=20) {feedback.setText("Use a name of 1–40 characters. Up to 20 presets.");return;}
        presets.put(name,browser.preferences().appearance.copy());browser.saveSession();show();preset.setValue(name);feedback.setText("Preset saved: "+name);
    }
    @FXML private void loadPreset() { var a=browser.preferences().appearancePresets.get(preset.getValue());if(a!=null){browser.preferences().appearance=a.copy();browser.appearanceChanged();show();} }
    @FXML private void deletePreset() { String name=preset.getValue();if(name!=null){browser.preferences().appearancePresets.remove(name);browser.saveSession();show();feedback.setText("Preset removed: "+name);} }
    @FXML private void chooseImage() { chooseAsset(false); }
    @FXML private void chooseMusic() { chooseAsset(true); }
    private void chooseAsset(boolean music) {
        var chooser=new FileChooser();chooser.setTitle(music?"Choose background music":"Choose wallpaper");
        chooser.getExtensionFilters().add(music?new FileChooser.ExtensionFilter("Audio","*.mp3","*.m4a","*.wav","*.aiff"):new FileChooser.ExtensionFilter("Images","*.png","*.jpg","*.jpeg","*.gif"));
        var file=chooser.showOpenDialog(browser.window());if(file==null)return;
        work(()->{ if(Files.size(file.toPath())>(music?200_000_000L:32_000_000L))throw new IllegalArgumentException(music?"Choose an audio file smaller than 200 MB.":"Choose an image smaller than 32 MB.");return file.toURI().toString();},uri->{
            var a=browser.preferences().appearance;if(music)a.music=uri;else{a.image=uri;a.wallpaper="Custom";a.wallpapers=true;}
            browser.appearanceChanged();show();feedback.setText(music?"Music selected. Enable Background music to play.":"Wallpaper selected.");
        });
    }
    @FXML private void exportPreset() {
        var chooser=new FileChooser();chooser.setTitle("Export appearance");chooser.setInitialFileName("flux-appearance.json");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Flux appearance","*.json"));
        var file=chooser.showSaveDialog(browser.window());if(file==null)return;
        var a=browser.preferences().appearance.copy();a.image="";a.music="";a.musicEnabled=false;
        String json=FeatureStore.JSON.toJson(a);
        work(()->{FeatureStore.writeAtomic(file.toPath(),json);return "Appearance exported. Local media files are not included.";},feedback::setText);
    }
    @FXML private void importPreset() {
        var chooser=new FileChooser();chooser.setTitle("Import appearance");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Flux appearance","*.json"));var file=chooser.showOpenDialog(browser.window());if(file==null)return;
        work(()->{if(Files.size(file.toPath())>64_000)throw new IllegalArgumentException("Appearance file exceeds 64 KB.");
            var a=FeatureStore.JSON.fromJson(Files.readString(file.toPath()),Appearance.class);if(a==null || a.version!=1)throw new IllegalArgumentException("Unsupported appearance file.");
            a.normalize();a.image="";a.music="";a.musicEnabled=false;return a;
        },a->{browser.preferences().appearance=a;browser.appearanceChanged();show();feedback.setText("Appearance imported.");});
    }
    private <T> void work(Callable<T> action, java.util.function.Consumer<T> apply) {
        try { CompletableFuture.supplyAsync(()->{try{return action.call();}catch(Exception e){throw new CompletionException(e);}},files).whenComplete((value,error)->Platform.runLater(()->{
            if(closed)return;if(error!=null)feedback.setText("Could not use that file. Check its format, size and permissions.");else apply.accept(value);
        })); } catch(RejectedExecutionException e) {feedback.setText("Please wait for the current file operation to finish.");}
    }
    private static String fileName(String uri,String fallback) {try{return Path.of(java.net.URI.create(uri)).getFileName().toString();}catch(Exception e){return fallback;} }
    private static String color(Color c) {return String.format("#%02x%02x%02x",Math.round(c.getRed()*255),Math.round(c.getGreen()*255),Math.round(c.getBlue()*255));}
    @FXML private void closePanel() { browser.closeEasySetup(); }
    @FXML private void allSettings() { browser.closeEasySetup();browser.settings(); }
    public void close() {closed=true;files.shutdownNow();}
}
