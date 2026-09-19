package com.flux.browser.ui;

import com.flux.browser.feature.Appearance;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.util.function.Consumer;

/** Optional shell audio. Clips never overlap themselves; background music is streamed by MediaPlayer. */
public final class Soundscape implements AutoCloseable {
    private AudioClip click, type;
    private MediaPlayer player;
    private String source = "";
    private Appearance settings = new Appearance();
    private final Consumer<String> error;
    public Soundscape(Consumer<String> error) { this.error = error; }
    public void configure(Appearance value) {
        settings = value.copy();
        String next = settings.musicEnabled ? settings.music : "";
        if(!next.equals(source)) {
            if(player!=null) { player.dispose(); player=null; }
            source=next;
            if(!next.isEmpty()) try {
                player=new MediaPlayer(new Media(next)); player.setCycleCount(MediaPlayer.INDEFINITE);
                player.setOnError(()->error.accept("This music file could not be played. Choose a supported local audio file."));
                player.setVolume(settings.volume); player.play();
            } catch(RuntimeException failure) { error.accept("This music file could not be played."); }
        }
        if(player!=null)player.setVolume(settings.volume);
    }
    public void click(boolean typing) {
        if(typing ? !settings.typingSounds : !settings.sounds) return;
        try {
            AudioClip clip;
            if(typing) { if(type==null)type=new AudioClip(getClass().getResource("/com/flux/browser/audio/type.wav").toExternalForm()); clip=type; }
            else { if(click==null)click=new AudioClip(getClass().getResource("/com/flux/browser/audio/click.wav").toExternalForm()); clip=click; }
            if(!clip.isPlaying())clip.play(settings.volume);
        } catch(RuntimeException ignored) { }
    }
    public void paused(boolean paused) { if(player!=null) { if(paused)player.pause();else player.play(); } }
    public void close() { if(click!=null)click.stop();if(type!=null)type.stop();if(player!=null)player.dispose(); }
}
