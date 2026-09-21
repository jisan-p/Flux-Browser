package com.flux.browser;

import com.flux.browser.controller.BrowserController;
import com.flux.browser.db.DatabaseManager;
import com.flux.browser.feature.FeatureStore;
import com.flux.browser.util.Views;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.*;

/** Real FXML controls, native viewport reflow and persistence in an isolated temporary profile. */
public final class AppearanceUiChecks {
    private static Stage stage;
    private static BrowserController browser;
    private static Parent root;
    public static void main(String[] args) throws Exception {
        Path profile=Files.createTempDirectory("flux-appearance-ui-");
        System.setProperty("flux.profileDir",profile.toString());System.setProperty("flux.session","true");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{byte[] body="<!doctype html><title>Appearance fixture</title><h1>Native viewport</h1>".getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(200,body.length);try(var out=e.getResponseBody()){out.write(body);}});server.start();
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            fx(()->{start();return null;});
            await(()->Files.exists(profile.resolve("session.json")) || fx(()->browser.preferences().appearance.theme.equals("GX Classic")),"initial appearance");
            // Wait for the session read and toast to finish before checking screenshot geometry.
            Thread.sleep(1500);
            fx(()->{browser.easySetup();return null;});
            await(()->fx(()->node("easySetup").isVisible()),"Easy Setup opens");
            fx(()->{browser.closeEasySetup();return null;});Thread.sleep(4200);
            fx(()->{browser.home();return null;});
            screenshot("gx-home");
            polishChecks();
            BrowserChecks.check(fx(()->browser.currentPage()==null),"appearance does not allocate a web engine on Home");
            fx(()->{browser.easySetup();return null;});screenshot("gx-easy-setup");
            fx(()->{
                ((ColorPicker)node("accent")).setValue(Color.web("#965bff"));
                ((ComboBox<String>)node("mode")).setValue("Light");
                ((Slider)node("columns")).setValue(4);
                ((CheckBox)node("clock")).setSelected(true);
                ((CheckBox)node("statusBar")).setSelected(true);
                ((TextField)node("presetName")).setText("Evening");
                ((Button)node("saveAppearancePreset")).fire();return null;
            });
            await(()->fx(()->root.getStyleClass().contains("light-theme")),"light theme applied");
            BrowserChecks.equal(fx(()->browser.preferences().appearance.accent),"#965bff");
            BrowserChecks.equal(fx(()->browser.preferences().appearancePresets.get("Evening").columns),4);
            screenshot("gx-light");
            fx(()->{((ComboBox<String>)node("mode")).setValue("Dark");stage.setWidth(940);stage.setHeight(650);return null;});
            Thread.sleep(300);screenshot("gx-compact");
            BrowserChecks.check(fx(()->node("easySetup").localToScene(node("easySetup").getBoundsInLocal()).getMaxX()<=stage.getScene().getWidth()+1),"drawer stays inside compact window");
            fx(()->{browser.closeEasySetup();stage.setWidth(1400);stage.setHeight(900);browser.navigateTo("http://127.0.0.1:"+server.getAddress().getPort()+"/");return null;});
            await(()->fx(()->browser.currentPage()!=null && browser.currentPage().state.get()==Worker.State.SUCCEEDED),"native page");
            double wide=Double.parseDouble(fx(()->browser.currentPage().evaluate("window.innerWidth")).get());
            fx(()->{browser.easySetup();return null;});
            await(()->Double.parseDouble(fx(()->browser.currentPage().evaluate("window.innerWidth")).get())<wide-250,"native page reflows alongside drawer");
            BrowserChecks.equal(fx(()->browser.currentPage().evaluate("document.title")).get(),"Appearance fixture");
            fx(()->{browser.closeEasySetup();return null;});
            await(()->Double.parseDouble(fx(()->browser.currentPage().evaluate("window.innerWidth")).get())>=wide-2,"native width restored");
            fx(()->{browser.preferences().restore=false;browser.close();stage.close();return null;});
            try(var store=new FeatureStore()) {
                await(()->store.load().get().appearance.accent.equals("#965bff"),"appearance persisted on close");
                BrowserChecks.equal(store.load().get().appearancePresets.get("Evening").columns,4);
            }
            fx(()->{start();return null;});
            await(()->fx(()->browser.preferences().appearance.accent.equals("#965bff")),"appearance restored on relaunch");
            await(()->fx(()->root.getStyle().contains("#965bff")),"restored appearance applied to UI");
            BrowserChecks.check(fx(()->browser.currentPage()==null),"appearance restores even with tab restore disabled");
            System.out.println("AppearanceUiChecks passed: tab and shortcut menus, Focus guards, searchable settings, direct theme controls, presets, compact layout, native viewport reflow, persistence and relaunch. Screenshots: target/screenshots/gx-*.png");
        } finally {fx(()->{if(browser!=null)browser.close();if(stage!=null)stage.close();return null;});server.stop(0);Platform.exit();}
    }
    private static ContextMenu menu(Node owner) {
        var point = owner.localToScreen(20, 20);
        javafx.event.Event.fireEvent(owner, new javafx.scene.input.ContextMenuEvent(
            javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, 20, 20, point.getX(), point.getY(), false, null));
        return (ContextMenu) javafx.stage.Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).findFirst().orElseThrow();
    }
    private static void item(ContextMenu menu, String text) {
        var item = menu.getItems().stream().filter(i -> text.equals(i.getText())).findFirst().orElseThrow();
        BrowserChecks.check(!item.isDisable(), text + " enabled"); item.fire(); menu.hide();
    }
    private static void polishChecks() throws Exception {
        fx(() -> {item(menu(root.lookup(".tab-chip")), "Duplicate Tab"); return null;});
        BrowserChecks.equal(fx(() -> ((javafx.scene.layout.HBox)node("tabHeaders")).getChildren().size()), 2);
        Thread.sleep(200); screenshot("gx-two-tabs");
        fx(() -> {var tabs=((javafx.scene.layout.HBox)node("tabHeaders")).getChildren();
            item(menu(tabs.getFirst()), "Close Tabs to the Right"); return null;});
        BrowserChecks.equal(fx(() -> ((javafx.scene.layout.HBox)node("tabHeaders")).getChildren().size()), 1);
        fx(() -> {item(menu(root.lookup(".tab-chip")), "Reopen Last Closed Tab"); return null;});
        BrowserChecks.equal(fx(() -> ((javafx.scene.layout.HBox)node("tabHeaders")).getChildren().size()), 2);
        fx(() -> {browser.setFocusMode(true); var m=menu(root.lookup(".tab-chip"));
            BrowserChecks.check(m.getItems().stream().filter(i -> "Close Tab".equals(i.getText())).findFirst().orElseThrow().isDisable(), "Focus mode protects tab menu");
            m.hide(); browser.setFocusMode(false); return null;});
        ContextMenu popup=fx(() -> menu(root.lookup(".tab-chip")));
        Thread.sleep(200); screenshotNode("gx-tab-menu", fx(() -> popup.getScene().getRoot()));
        fx(() -> {popup.hide();item(menu(root.lookup(".tab-chip")), "Close Other Tabs"); browser.settings(); return null;});
        fx(() -> {((Button)root.lookup("#themeChoices").lookupAll(".settings-theme").stream().filter(n -> "Frost".equals(n.getUserData())).findFirst().orElseThrow()).fire();
            ((ToggleButton)root.lookup("#modeChoices").lookupAll(".mode-choice").stream().filter(n -> "Light".equals(n.getUserData())).findFirst().orElseThrow()).fire(); return null;});
        await(() -> fx(() -> root.getStyleClass().contains("light-theme")), "settings display mode applied");
        BrowserChecks.equal(fx(() -> browser.preferences().appearance.theme), "Frost");
        fx(() -> {((Button)root.lookup("#themeChoices").lookupAll(".settings-theme").stream().filter(n -> "GX Classic".equals(n.getUserData())).findFirst().orElseThrow()).fire();
            ((ToggleButton)root.lookup("#modeChoices").lookupAll(".mode-choice").stream().filter(n -> "Dark".equals(n.getUserData())).findFirst().orElseThrow()).fire(); return null;});
        await(() -> fx(() -> !root.getStyleClass().contains("light-theme")), "settings dark mode restored");
        screenshot("gx-settings");
        fx(() -> {((TextField)node("settingsSearch")).setText("storage"); return null;});
        BrowserChecks.equal(fx(() -> ((javafx.scene.layout.VBox)node("settingsCards")).getChildren().stream().filter(Node::isVisible).count()), 1L);
        fx(() -> {((TextField)node("settingsSearch")).setText("unmatched-query"); return null;});
        BrowserChecks.check(fx(() -> node("noResults").isVisible()), "empty settings search feedback");
        fx(() -> {((TextField)node("settingsSearch")).clear(); stage.setWidth(940); stage.setHeight(650); return null;});
        await(() -> fx(() -> stage.getScene().getWidth() < 950), "settings window resized");
        screenshot("gx-settings-compact");
        BrowserChecks.check(fx(() -> node("settingsSearch").localToScene(node("settingsSearch").getBoundsInLocal()).getMaxX() <= stage.getScene().getWidth()), "settings fit compact window");
        fx(() -> {((Button)node("openCustomization")).fire(); return null;});
        BrowserChecks.check(fx(() -> node("easySetup").isVisible() && !root.lookup(".settings-root").isVisible()), "compact settings opens Easy Setup without squeezing both panels");
        fx(() -> {browser.closeEasySetup(); stage.setWidth(1400); stage.setHeight(900); browser.dismissPanels();
            var home=root.lookupAll(".home-scroll").stream().filter(Node::isVisible).findFirst().orElseThrow();
            item(menu(home), "Easy Setup"); return null;});
        BrowserChecks.check(fx(() -> node("easySetup").isVisible()), "home menu opens customization");
        fx(() -> {browser.closeEasySetup(); var tile = root.lookup(".dial-tile"); var m=menu(tile);
            BrowserChecks.check(m.getItems().stream().filter(i -> "Remove".equals(i.getText())).findFirst().orElseThrow().isDisable(), "starter shortcut cannot be removed");
            m.hide(); return null;});
    }
    private static void start() {
        var view=Views.<BrowserController>load("BrowserWindow");browser=view.controller();root=view.root();stage=new Stage();
        stage.setScene(new Scene(root,1400,900));browser.configure(stage,new DatabaseManager("jdbc:postgresql://127.0.0.1:1/flux_test","flux",""));stage.show();
    }
    private static Node node(String id) {
        // Easy Setup and the shell both have a statusBar id; select the customization checkbox explicitly.
        if(id.equals("statusBar"))return root.lookup("#easySetup").lookup("#statusBar");
        return root.lookup("#"+id);
    }
    private static void screenshot(String name) throws Exception {
        screenshotNode(name, root);
    }
    private static void screenshotNode(String name, Parent source) throws Exception {
        BufferedImage output=fx(()->{source.applyCss();source.layout();var image=source.snapshot(null,null);int w=(int)image.getWidth(),h=(int)image.getHeight();int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,w,h,pixels,0,w);return result;});
        Path file=Path.of("target/screenshots/"+name+".png");Files.createDirectories(file.getParent());ImageIO.write(output,"png",file.toFile());
    }
    private static <T>T fx(Callable<T> action)throws Exception {var task=new FutureTask<T>(()->{if(root!=null){root.applyCss();root.layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition,String name)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(100);}throw new AssertionError("Timed out: "+name);}
}
