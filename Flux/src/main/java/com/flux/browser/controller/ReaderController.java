package com.flux.browser.controller;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
public final class ReaderController {
    @FXML private Label title,byline,article;private Runnable open;private int size=18;
    public void configure(String heading,String author,String text,Runnable open){title.setText(heading);byline.setText(author);article.setText(text);this.open=open;}
    @FXML private void larger(){size=Math.min(32,size+2);resize();}
    @FXML private void smaller(){size=Math.max(12,size-2);resize();}
    private void resize(){article.setStyle("-fx-padding:24;-fx-font-size:"+size+";");}
    @FXML private void original(){open.run();}
}
