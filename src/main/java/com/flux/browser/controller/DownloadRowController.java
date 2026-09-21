package com.flux.browser.controller;

import com.flux.browser.feature.Downloads;
import com.flux.browser.web.NativeWebPage;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import java.nio.file.Path;
import java.util.Locale;

public final class DownloadRowController {
    @FXML private Label kind,name,detail;
    @FXML private Button openButton,revealButton,cancelButton;
    @FXML private ProgressBar progress;
    private BrowserController browser;
    private Downloads.Item item;
    public void configure(BrowserController browser, Downloads.Item item) {
        this.browser=browser; this.item=item; name.setText(item.name());
        kind.setText(item.kind().equals("DOCUMENTS")?"DOC":item.kind());
        detail.setText(item.status()+" · "+bytes(item.received())+(item.total()>0?" / "+bytes(item.total()):""));
        boolean complete=item.status().equals("Complete");
        show(openButton,complete && item.path().toLowerCase(Locale.ROOT).endsWith(".pdf"));
        show(revealButton,complete); show(cancelButton,item.active()); show(progress,item.active());
        progress.setProgress(item.total()>0?Math.min(1,(double)item.received()/item.total()):-1);
    }
    private static String bytes(long value) {
        if(value<1024)return Math.max(0,value)+" B";
        if(value<1024*1024)return String.format(Locale.ROOT,"%.1f KB",value/1024.0);
        return String.format(Locale.ROOT,"%.1f MB",value/(1024.0*1024));
    }
    private static void show(Node n,boolean visible) { n.setVisible(visible); n.setManaged(visible); }
    @FXML private void open() { if(item.status().equals("Complete"))browser.openPdf(Path.of(item.path())); }
    @FXML private void reveal() { if(item.status().equals("Complete"))NativeWebPage.downloadAction(item.id(),"reveal"); }
    @FXML private void cancel() { if(item.active())NativeWebPage.downloadAction(item.id(),"cancel"); }
}
