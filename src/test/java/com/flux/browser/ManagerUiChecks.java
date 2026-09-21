package com.flux.browser;

import com.flux.browser.controller.BrowserController;
import com.flux.browser.db.*;
import com.flux.browser.feature.*;
import com.flux.browser.util.Views;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.PixelFormat;
import javafx.stage.Stage;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.*;

/** Live FXML + disposable PostgreSQL: no configured database or personal profile is used. */
public final class ManagerUiChecks {
    private static Parent root;
    private static Stage stage;
    private static BrowserController browser;
    public static void main(String[] args) throws Exception {
        String url=System.getenv("FLUX_TEST_DB_URL");
        if(url==null || !url.matches("jdbc:postgresql://[^/]+/flux_test(?:\\?.*)?"))throw new IllegalArgumentException("A disposable flux_test database is required");
        System.setProperty("flux.profileDir",Files.createTempDirectory("flux-managers-").toString());
        System.setProperty("flux.session","false");
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{var bytes="<!doctype html><title>Manager layout fixture</title><h1>Page stays open</h1>".getBytes();e.sendResponseHeaders(200,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});server.start();
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            fx(()->{var view=Views.<BrowserController>load("BrowserWindow");root=view.root();browser=view.controller();stage=new Stage();stage.setScene(new Scene(root,1400,900));browser.configure(stage,new DatabaseManager(url,"flux",""));stage.show();return null;});
            await(()->fx(()->browser.storageAvailable()),"database online");
            Thread.sleep(5200); // Let the connection notice clear before reference screenshots.
            var dao=fx(()->browser.history());
            dao.deleteHistory().get();
            // More than one result page today must not hide yesterday after filtering.
            try(var db=new DatabaseManager(url,"flux","")) {
                db.initialize().get();
                db.write(c->{try(var s=c.prepareStatement("INSERT INTO history(title,url,visit_timestamp) VALUES (?,?,?)")) {
                    for(int i=0;i<505;i++){s.setString(1,"Today fixture "+i);s.setString(2,"https://example.com/today/"+i);s.setTimestamp(3,java.sql.Timestamp.from(Instant.now()));s.addBatch();}s.executeBatch();
                    s.setString(1,"Yesterday reference");s.setString(2,"https://example.com/yesterday");s.setTimestamp(3,java.sql.Timestamp.from(LocalDate.now().minusDays(1).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant()));s.executeUpdate();
                }return null;}).get();
            }
            BrowserChecks.equal(dao.getHistory("").get().size(),500);
            BrowserChecks.equal(dao.getHistory("",BrowsingPeriod.YESTERDAY.range()).get().size(),1);
            BrowserChecks.equal(dao.getHistory("Yesterday",BrowsingPeriod.TODAY.range()).get().size(),0);
            fx(()->{browser.showLibrary(false);return null;});
            await(()->fx(()->entries().getItems().size()==500),"full history cap");
            fx(()->{toggle(root.lookup(".library-page"),"YESTERDAY");return null;});
            await(()->fx(()->entries().getItems().size()==1),"yesterday UI filter queries before limit");
            snapshot("manager-history");
            fx(()->{((TextField)root.lookup("#filterField")).setText("no-match-here");return null;});
            await(()->fx(()->entries().getItems().isEmpty()),"history text filter");
            fx(()->{browser.features();
                var tabs=(TabPane)root.lookup("#featureTabs");
                tabs.getSelectionModel().select(tabs.getTabs().stream().filter(t->t.getText().equals("Downloads")).findFirst().orElseThrow());
                BrowserChecks.check(root.lookup(".downloads-page").isVisible(),"Tools routes to Downloads page");
                var list=browser.downloads().items;
                list.add(new Downloads.Item(9001,"Design reference.pdf","Complete","/tmp/flux-fixture.pdf",20480,20480));
                list.add(new Downloads.Item(9002,"Holiday photo.PNG","Complete","/tmp/flux-fixture.png",50000,50000));
                list.add(new Downloads.Item(9003,"Video preview.mp4","Downloading","/tmp/flux-fixture.mp4",1024,8192));return null;});
            BrowserChecks.equal(fx(()->downloads().getItems().size()),3);
            snapshot("manager-downloads");
            fx(()->{toggle(root.lookup(".downloads-page"),"IMAGES");return null;});
            BrowserChecks.equal(fx(()->downloads().getItems().size()),1);
            fx(()->{((TextField)root.lookup("#downloadSearch")).setText("absent");return null;});
            BrowserChecks.equal(fx(()->downloads().getItems().size()),0);
            snapshot("manager-downloads-empty");
            fx(()->{((TextField)root.lookup("#downloadSearch")).clear();toggle(root.lookup(".downloads-page"),"ALL");((Button)root.lookup("#clearDownloadsButton")).fire();return null;});
            BrowserChecks.equal(fx(()->browser.downloads().items.size()),1);
            BrowserChecks.check(fx(()->browser.downloads().items.getFirst().active()),"clear keeps active download");
            // Refresh a live native download event without duplicating it or changing its date.
            fx(()->{browser.downloads().update("{\"id\":9003,\"name\":\"Video preview.mp4\",\"status\":\"Complete\",\"path\":\"/tmp/flux-fixture.mp4\",\"received\":8192,\"total\":8192}");return null;});
            BrowserChecks.equal(fx(()->downloads().getItems().size()),1);
            fx(()->{stage.setWidth(940);stage.setHeight(650);return null;});
            await(()->fx(()->stage.getScene().getWidth()<950),"compact window resized");
            snapshot("manager-downloads-compact");
            BrowserChecks.check(fx(()->root.lookup("#downloadEntries").localToScene(root.lookup("#downloadEntries").getBoundsInLocal()).getMaxX()<=stage.getScene().getWidth()),"compact downloads fit");
            fx(()->{stage.setWidth(1400);stage.setHeight(900);browser.navigateTo("http://127.0.0.1:"+server.getAddress().getPort()+"/");return null;});
            await(()->fx(()->browser.currentPage()!=null && browser.currentPage().state.get()==Worker.State.SUCCEEDED),"native fixture");
            double wide=Double.parseDouble(fx(()->browser.currentPage().evaluate("window.innerWidth")).get());
            fx(()->{browser.openHistoryPanel();return null;});
            await(()->Double.parseDouble(fx(()->browser.currentPage().evaluate("window.innerWidth")).get())<wide-250,"history panel reflows native page");
            await(()->fx(()->((ListView<?>)root.lookup("#recentEntries")).getItems().size()==500),"history panel results");
            BrowserChecks.equal(fx(()->browser.currentPage().evaluate("document.title")).get(),"Manager layout fixture");
            snapshot("manager-history-sidebar");
            fx(()->{((Button)root.lookup("#openFullHistoryButton")).fire();return null;});
            BrowserChecks.check(fx(()->root.lookup(".library-page").isVisible() && !root.lookup(".history-panel").isVisible()),"expand sidebar to full history");
            fx(()->{browser.dismissPanels();return null;});
            await(()->Double.parseDouble(fx(()->browser.currentPage().evaluate("window.innerWidth")).get())>=wide-2,"native viewport restored");
            System.out.println("ManagerUiChecks passed: SQL date filtering before limit, history search, download types/search/live updates/clear, compact layout, history sidebar and native viewport restore.");
        } finally {fx(()->{if(browser!=null)browser.close();if(stage!=null)stage.close();return null;});server.stop(0);Platform.exit();}
    }
    private static ListView<?> entries(){return (ListView<?>)root.lookup("#entries");}
    private static ListView<?> downloads(){return (ListView<?>)root.lookup("#downloadEntries");}
    private static void toggle(Node parent,String text){((ToggleButton)parent.lookupAll(".toggle-button").stream().filter(n->n instanceof ToggleButton t && t.getText().equals(text)).findFirst().orElseThrow()).fire();}
    private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<T>(()->{if(root!=null){root.applyCss();root.layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition,String name)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(100);}throw new AssertionError("Timed out: "+name);}
    private static void snapshot(String name)throws Exception {
        BufferedImage output=fx(()->{root.applyCss();root.layout();var im=root.snapshot(null,null);int w=(int)im.getWidth(),h=(int)im.getHeight();int[] pixels=new int[w*h];im.getPixelReader().getPixels(0,0,w,h,PixelFormat.getIntArgbInstance(),pixels,0,w);var result=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);result.setRGB(0,0,w,h,pixels,0,w);return result;});
        Path file=Path.of("target/screenshots/"+name+".png");Files.createDirectories(file.getParent());ImageIO.write(output,"png",file.toFile());
    }
}
