package com.flux.browser.feature;
import com.google.gson.JsonParser;
import javafx.collections.*;
import java.time.Instant;
import java.util.Locale;
public final class Downloads {
    public record Item(long id,String name,String status,String path,long received,long total,Instant startedAt) {
        public Item(long id,String name,String status,String path,long received,long total) { this(id,name,status,path,received,total,Instant.now()); }
        public boolean active() { return !status.equals("Complete") && !status.equals("Cancelled") && !status.startsWith("Failed"); }
        public String kind() {
            String suffix=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
            return switch (suffix) {
                case "pdf","txt","doc","docx","odt","rtf","csv","xls","xlsx","ppt","pptx" -> "DOCUMENTS";
                case "png","jpg","jpeg","gif","webp","svg","heic","avif","bmp" -> "IMAGES";
                case "mp4","mov","m4v","webm","mkv","avi" -> "VIDEO";
                case "mp3","m4a","wav","flac","ogg","aac" -> "AUDIO";
                case "zip","gz","tar","bz2","xz","7z","rar" -> "ARCHIVES";
                default -> "OTHER";
            };
        }
        @Override public String toString(){return name+" · "+status+(total>0?" · "+Math.min(100,received*100/total)+"%":"");}
    }
    public final ObservableList<Item> items=FXCollections.observableArrayList();
    public void clearFinished() { items.removeIf(item -> !item.active()); }
    public void update(String json){
        var o=JsonParser.parseString(json).getAsJsonObject();
        var item=new Item(o.get("id").getAsLong(),o.get("name").getAsString(),o.get("status").getAsString(),o.get("path").getAsString(),o.get("received").getAsLong(),o.get("total").getAsLong());
        for(int i=0;i<items.size();i++)if(items.get(i).id()==item.id()){var old=items.get(i); items.set(i,new Item(item.id(),item.name(),item.status(),item.path(),item.received(),item.total(),old.startedAt()));return;}
        items.addFirst(item);if(items.size()>200)items.removeLast();
    }
}
