package com.flux.browser.feature;
import com.google.gson.JsonParser;
import javafx.collections.*;
public final class Downloads {
    public record Item(long id,String name,String status,String path,long received,long total) {
        @Override public String toString(){return name+" · "+status+(total>0?" · "+Math.min(100,received*100/total)+"%":"");}
    }
    public final ObservableList<Item> items=FXCollections.observableArrayList();
    public void update(String json){
        var o=JsonParser.parseString(json).getAsJsonObject();
        var item=new Item(o.get("id").getAsLong(),o.get("name").getAsString(),o.get("status").getAsString(),o.get("path").getAsString(),o.get("received").getAsLong(),o.get("total").getAsLong());
        for(int i=0;i<items.size();i++)if(items.get(i).id()==item.id()){items.set(i,item);return;}
        items.addFirst(item);if(items.size()>200)items.removeLast();
    }
}
