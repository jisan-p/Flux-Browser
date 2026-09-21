package com.flux.browser;

import com.flux.browser.controller.BrowserController;
import com.flux.browser.db.DatabaseManager;
import com.flux.browser.util.Views;
import com.flux.browser.web.NativeWebPage;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.flux.browser.ui.WindowGeometry;

/** A disposable local page: no database, external sites, or user session required. */
public final class DeveloperToolsChecks {
    private static Stage stage;
    private static BrowserController browser;
    public static void main(String[] args)throws Exception {
        System.setProperty("flux.session","false"); System.setProperty("flux.testInput","true");
        System.setProperty("flux.profileDir",Files.createTempDirectory("flux-inspector-check-").toString());
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{
            byte[] body="<!doctype html><title>Flux Inspector Fixture</title><style>h1{color:blue}</style><h1 id='fixture'>Inspect this element</h1><script>window.fixtureValue=42;console.log('Flux inspector fixture');</script>".getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type","text/html;charset=utf-8");e.sendResponseHeaders(200,body.length);try(var out=e.getResponseBody()){out.write(body);}
        });server.start();
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            fx(()->{var view=Views.<BrowserController>load("BrowserWindow");browser=view.controller();stage=new Stage();stage.initStyle(StageStyle.UNDECORATED);stage.setScene(new Scene(view.root(),1100,700));browser.configure(stage,new DatabaseManager("jdbc:postgresql://127.0.0.1:1/flux_test","flux",""));stage.show();browser.developerTools();return null;});
            BrowserChecks.check(fx(()->browser.currentPage()==null),"Home does not allocate an inspector");
            fx(()->{browser.navigateTo("http://127.0.0.1:"+server.getAddress().getPort()+"/");return null;});
            await(()->fx(()->browser.currentPage()!=null && browser.currentPage().state.get()==Worker.State.SUCCEEDED),"fixture load");
            if (fx(()->browser.currentPage() instanceof com.flux.browser.web.JavaFxPage)) { checkCompatibility(); return; }
            var page=fx(()->(NativeWebPage)browser.currentPage());
            BrowserChecks.equal(fx(()->page.developerTools("status")).get(),"closed");
            fx(()->{browser.features();((Button)stage.getScene().lookup("#developerToolsButton")).fire();return null;});
            await(()->fx(()->page.developerTools("status")).get().equals("visible"),"Tools button opens inspector");
            await(()->{
                try{return fx(()->page.inspectorFrontendForTesting("typeof WI + ':' + document.readyState")).get().equals("object:complete");}
                catch(ExecutionException e){return false;}
            },"Web Inspector frontend loads");
            System.out.println("Inspector geometry: " + inspectorState(page));
            awaitDetached(page);
            fx(()->{stage.setWidth(940);stage.setHeight(650);return null;});
            await(()->fx(()->Math.abs(stage.getScene().getWidth()-940)<2),"small window resized with inspector open");
            awaitViewport(page);
            fx(()->{((Button)stage.getScene().lookup("#maximizeButton")).fire();return null;});
            await(()->fx(()->stage.isMaximized() && Math.abs(stage.getWidth()-WindowGeometry.screen(stage).getWidth())<2),"maximize with inspector open");
            awaitViewport(page);
            fx(()->{((Button)stage.getScene().lookup("#maximizeButton")).fire();return null;});
            await(()->fx(()->!stage.isMaximized() && Math.abs(stage.getWidth()-940)<2),"restore with inspector open");
            awaitViewport(page);
            long window = inspectorState(page).get("window").getAsLong();
            fx(()->{browser.features();((Button)stage.getScene().lookup("#developerToolsButton")).fire();return null;});
            awaitDetached(page);
            BrowserChecks.equal(inspectorState(page).get("window").getAsLong(),window);
            String tabs=fx(()->page.inspectorFrontendForTesting("document.body.innerText")).get();
            BrowserChecks.check(tabs.contains("Elements") && tabs.contains("Network") && tabs.contains("Console"),"real inspector panels available");
            System.out.println("Web Inspector frontend: Elements, Network, Console available.");
            // Ask Web Inspector's Console to evaluate in the fixture's actual JS execution context.
            String evaluation="window.fluxTestResult='pending'; WI.runtimeManager.evaluateInInspectedWindow('window.fixtureValue + 1', {objectGroup:'flux-test',includeCommandLineAPI:false,returnByValue:true}, (object,wasThrown,result)=>{window.fluxTestResult=JSON.stringify({object:object?.value,wasThrown,result});}); 'requested'";
            System.out.println("Inspector console probe: "+fx(()->page.inspectorFrontendForTesting(evaluation)).get());
            await(()->!fx(()->page.inspectorFrontendForTesting("window.fluxTestResult")).get().equals("pending"),"console evaluation");
            String evaluated=fx(()->page.inspectorFrontendForTesting("window.fluxTestResult")).get();
            BrowserChecks.check(evaluated.contains("43"),"console evaluates in inspected page: "+evaluated);
            fx(()->{browser.developerTools();return null;});
            await(()->fx(()->page.developerTools("status")).get().equals("closed"),"toggle closes inspector");
            awaitViewport(page);
            fx(()->{page.postShortcutForTesting("developerTools");return null;});
            await(()->fx(()->page.developerTools("status")).get().equals("visible"),"native Cmd+Option+I");
            fx(()->page.developerTools("close")).get();
            fx(()->{stage.getScene().getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F12,false,false,false,false));return null;});
            await(()->fx(()->page.developerTools("status")).get().equals("visible"),"FXML F12 shortcut");
            awaitDetached(page);
            // Simulate WebKit retaining a docked layout, then enter through its own Inspect Element menu.
            // Close during the attach operation, before Flux's queued detach. This recreates
            // WebKit's destructive close-while-docked layout without relying on timing.
            fx(()->page.contextMenuForTesting("dockInspector", "close")).get();
            await(()->fx(()->page.developerTools("status")).get().equals("closed"),"close while docked");
            awaitViewport(page);
            Thread.sleep(350);
            fx(()->page.contextMenuForTesting("show", "40,30")).get();
            await(()->fx(()->page.contextMenuForTesting("state", "")).get().contains("WKMenuItemIdentifierInspectElement"),"native Inspect Element menu");
            fx(()->page.contextMenuForTesting("activate", "WKMenuItemIdentifierInspectElement")).get();
            awaitDetached(page);
            await(()->{try{return fx(()->page.inspectorFrontendForTesting("document.body.innerText")).get().contains("fixture");}catch(ExecutionException e){return false;}},"Inspect Element shows fixture DOM");
            fx(()->page.contextMenuForTesting("closeInspectorWindow", "")).get();
            await(()->fx(()->page.developerTools("status")).get().equals("closed"),"native window close button");
            awaitViewport(page);
            BrowserChecks.equal(fx(()->page.evaluate("document.title")).get(),"Flux Inspector Fixture");
            fx(()->{browser.showDeveloperTools();return null;}); awaitDetached(page);
            fx(()->{browser.newTab();browser.navigateTo("http://127.0.0.1:"+server.getAddress().getPort()+"/second");return null;});
            await(()->fx(()->browser.currentPage().state.get()==Worker.State.SUCCEEDED),"second tab");
            BrowserChecks.equal(fx(()->((NativeWebPage)browser.currentPage()).developerTools("status")).get(),"closed");
            // The first inspector remains bound to its original page, never retargeted silently.
            BrowserChecks.equal(fx(()->page.developerTools("status")).get(),"visible");
            fx(()->{browser.close();return null;});
            BrowserChecks.check(fx(()->page.developerTools("show")).isCompletedExceptionally(),"closed tabs reject inspection");
            System.out.println("DeveloperToolsChecks passed: separate native window, repeat-open reuse, right-click Inspect Element after docking, preserved viewport, window close/reopen, real frontend/console, shortcuts and disposal.");
        } finally {fx(()->{if(browser!=null)browser.close();if(stage!=null)stage.close();return null;});Platform.exit();server.stop(0);}
    }
    private static com.google.gson.JsonObject inspectorState(NativeWebPage page) throws Exception {
        return com.google.gson.JsonParser.parseString(fx(()->page.contextMenuForTesting("inspectorState", "")).get()).getAsJsonObject();
    }
    private static void awaitDetached(NativeWebPage page) throws Exception {
        await(()->{var state=inspectorState(page);return state.get("detached").getAsBoolean() && state.get("viewport").getAsBoolean() && state.get("pageVisible").getAsBoolean() && state.get("chromeClear").getAsBoolean();},"separate inspector window with original webpage viewport");
    }
    private static void awaitViewport(NativeWebPage page) throws Exception {
        await(()->{
            var state=inspectorState(page);
            double width=fx(()->page.view().getLayoutBounds().getWidth());
            double height=fx(()->page.view().getLayoutBounds().getHeight());
            String[] size=fx(()->page.evaluate("innerWidth+','+innerHeight")).get().split(",");
            return state.get("viewport").getAsBoolean() && state.get("chromeClear").getAsBoolean()
                && Math.abs(Double.parseDouble(size[0])-width)<2 && Math.abs(Double.parseDouble(size[1])-height)<2;
        },"native page matches FXML viewport and leaves window controls exposed");
    }
    private static void checkCompatibility() throws Exception {
        var page = fx(()->browser.currentPage());
        BrowserChecks.equal(fx(()->page.evaluate("typeof window.__fluxDevTools")).get(),"undefined");
        fx(()->{browser.features();((Button)stage.getScene().lookup("#developerToolsButton")).fire();return null;});
        await(()->fx(()->page.developerTools("status")).get().equals("visible"),"Eruda opens");
        BrowserChecks.equal(fx(()->page.evaluate("['console','elements','network','resources','sources'].every(x=>!!window.__fluxDevTools.get(x))")).get(),"true");
        BrowserChecks.equal(fx(()->page.evaluate("typeof window.eruda")).get(),"undefined");
        fx(()->page.evaluate("console.log('Flux console check');window.__fluxDevTools.get('elements').select(document.getElementById('fixture'));window.__fluxDevTools.show('console');true")).get();
        String consoleKeys=fx(()->page.evaluate("Object.keys(window.__fluxDevTools.get('console')).join(',')")).get();
        System.out.println("Eruda console components: "+consoleKeys);
        fx(()->{page.reload();return null;});
        await(()->fx(()->page.state.get()==Worker.State.SUCCEEDED) && fx(()->page.developerTools("status")).get().equals("visible"),"Eruda restored on reload");
        fx(()->page.developerTools("close")).get();
        BrowserChecks.equal(fx(()->page.evaluate("typeof window.__fluxDevTools")).get(),"undefined");
        fx(()->{stage.getScene().getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.F12,false,false,false,false));return null;});
        await(()->fx(()->page.developerTools("status")).get().equals("visible"),"Eruda F12 shortcut");
        fx(()->page.developerTools("close")).get();
        fx(()->{page.reload();return null;});
        await(()->fx(()->page.state.get()==Worker.State.SUCCEEDED),"reload after closing tools");
        BrowserChecks.equal(fx(()->page.evaluate("typeof window.__fluxDevTools")).get(),"undefined");
        System.out.println("DeveloperToolsChecks passed: bundled Eruda tools, DOM selection, reload, toggle, shortcut and cleanup.");
    }
    private static <T>T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(stage!=null){stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition,String name)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<until){if(condition.call())return;Thread.sleep(100);}throw new AssertionError("Timed out: "+name);}
}
