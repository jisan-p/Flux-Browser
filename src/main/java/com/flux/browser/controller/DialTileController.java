package com.flux.browser.controller;

import com.flux.browser.model.SpeedDial;
import com.flux.browser.util.UrlResolver;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class DialTileController {
    @FXML private VBox root;
    @FXML private Label monogram, titleLabel, domainLabel;
    @FXML private Button openButton, editButton, deleteButton;
    @FXML private javafx.scene.image.ImageView iconView;
    @FXML private javafx.scene.control.ContextMenu tileMenu;
    @FXML private javafx.scene.control.MenuItem editItem, deleteItem, newTabItem;
    @FXML private void contextMenu(javafx.scene.input.ContextMenuEvent event) {
        editItem.setDisable(item.id() < 0 || !browser.storageAvailable());
        deleteItem.setDisable(editItem.isDisable()); newTabItem.setDisable(browser.focusMode());
        com.flux.browser.ui.Menus.show(tileMenu, root, event);
    }
    @FXML private void openNew() { browser.openNewUrl(item.url()); }
    @FXML private void copy() { browser.copyAddress(item.url()); }
    private BrowserController browser;
    private SpeedDial item;

    public void configure(BrowserController browser, SpeedDial item) {
        this.browser = browser;
        this.item = item;
        titleLabel.setText(item.title());
        String initial = item.title() != null && !item.title().isBlank() ? item.title().trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT) : "?";
        monogram.setText(initial);
        
        String host = UrlResolver.host(item.url());
        javafx.scene.image.Image icon = new javafx.scene.image.Image("https://s2.googleusercontent.com/s2/favicons?domain=" + host + "&sz=128", true);
        icon.progressProperty().addListener((o, old, prog) -> {
            if (prog.doubleValue() == 1.0) {
                if (!icon.isError() && icon.getWidth() > 0) {
                    iconView.setImage(icon);
                    iconView.setEffect(new javafx.scene.effect.DropShadow(6, javafx.scene.paint.Color.rgb(0,0,0,0.3)));
                    monogram.setVisible(false);
                    openButton.setStyle("-fx-background-color: transparent;");
                }
            }
        });
        String[] colors={"#6636bb","#23457f","#136c6c","#9c2346","#174e97","#4b3e89"};
        openButton.setStyle("-fx-background-color:linear-gradient(to bottom right,"+colors[Math.floorMod(UrlResolver.host(item.url()).hashCode(),colors.length)]+",#17131f);");
        javafx.animation.ScaleTransition hover=new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120),root);
        javafx.animation.TranslateTransition lift=new javafx.animation.TranslateTransition(javafx.util.Duration.millis(120),root);
        editButton.setVisible(false); deleteButton.setVisible(false);
        root.hoverProperty().addListener((o,before,over)->{
            editButton.setVisible(over); deleteButton.setVisible(over);
            hover.stop();lift.stop();var a=browser.preferences().appearance;
            double scale=over && a.tileEffect.equals("Zoom") ? 1.035 : 1;
            double y=over && a.tileEffect.equals("Lift") ? -4 : 0;
            root.setEffect(over && a.tileEffect.equals("Glow") ? new javafx.scene.effect.DropShadow(12,javafx.scene.paint.Color.web(a.accent)) : null);
            if(a.animations){hover.setToX(scale);hover.setToY(scale);hover.play();lift.setToY(y);lift.play();}
            else {root.setScaleX(scale);root.setScaleY(scale);root.setTranslateY(y);}
        });
        domainLabel.setText(UrlResolver.host(item.url()));
        openButton.setAccessibleText("Open " + item.title());
        editButton.setDisable(item.id() < 0);
        deleteButton.setDisable(item.id() < 0);
        if (item.position() % 2 != 0) root.getStyleClass().add("alternate");
    }
    @FXML private void open() { if (browser != null) browser.navigateTo(item.url()); }
    @FXML private void edit() { if (browser != null) browser.editDial(item); }
    @FXML private void delete() { if (browser != null) browser.deleteDial(item); }
}
