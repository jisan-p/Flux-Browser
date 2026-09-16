package com.flux.browser;

import com.flux.browser.controller.BrowserController;
import com.flux.browser.db.DatabaseManager;
import com.flux.browser.feature.*;
import com.flux.browser.util.*;
import com.flux.browser.web.*;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Local fixture checks for new tools. Uses an isolated profile and no external password vault. */
public final class FeatureUiChecks {
    private static BrowserController browser;
    private static Stage stage;
    private static final AtomicReference<Throwable> failure=new AtomicReference<>();
    public static void main(String[] args)throws Exception {
        Path temp=Files.createTempDirectory("flux-feature-ui-");
        System.setProperty("flux.profileDir",temp.toString());System.setProperty("flux.session","true");System.setProperty("flux.testInput","true");
        AtomicInteger backgroundRequests=new AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var workers=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(workers);
        String paragraph="The observatory studies distant stars and planets. Researchers measure light carefully and compare the results across many nights. ";
        byte[] pdf=pdfFixture();
        server.createContext("/",e->{
            String path=e.getRequestURI().getPath();
            if(path.equals("/background"))backgroundRequests.incrementAndGet();
            byte[] body=path.equals("/file")?pdf:("<!doctype html><html><head><title>Observatory article</title></head><body><article><h1>Observatory article</h1><p>"+paragraph.repeat(50)+"</p></article></body></html>").getBytes(StandardCharsets.UTF_8);
            e.getResponseHeaders().set("Content-Type",path.equals("/file")?"application/octet-stream":"text/html;charset=UTF-8");
            if(path.equals("/file"))e.getResponseHeaders().set("Content-Disposition","attachment; filename=observatory.pdf");
            e.sendResponseHeaders(200,body.length);try(var out=e.getResponseBody()){out.write(body);}finally{e.close();}
        });server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{e.printStackTrace();failure.set(e);});
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            if ("true".equals(System.getenv("FLUX_CHECK_FILTERS"))) {
                int domains=ContentRules.update(temp);
                BrowserChecks.check(domains>=100 && domains<=30000,"bounded live filter update");
                System.out.println("AdGuard domain import passed: "+domains+" rules in temporary profile.");
            }
            // Three restored tabs, only one selected: the background URL must not be fetched.
            try(var store=new FeatureStore()){
                var state=new FeatureStore.State();state.workspaces.add("Study");state.workspace="Study";state.selectedTab=1;
                state.tabs.add(new FeatureStore.SavedTab("Default",UrlResolver.HOME,"Home",1));
                state.tabs.add(new FeatureStore.SavedTab("Study",base+"/article","Article",1));
                state.tabs.add(new FeatureStore.SavedTab("Study",base+"/background","Background",1));
                store.save(state).get();
            }
            fx(()->{start();return null;});
            await("restored selected tab",()->browser.currentPage()!=null && browser.currentPage().state.get()==Worker.State.SUCCEEDED);
            BrowserChecks.equal(backgroundRequests.get(),0);
            BrowserChecks.equal(fx(()->count()),3);
            BrowserChecks.equal(fx(()->browser.preferences().workspace),"Study");
            fx(()->{browser.navigateTo("= (12+8)/4");return null;});
            BrowserChecks.equal(fx(()->((Label)root().lookup("#statusText")).getText()),"= 5");
            fx(()->{browser.features();return null;});
            snapshot(temp.resolve("tools-workspaces.png"));
            fx(()->{((TabPane)root().lookup(".tab-pane")).getSelectionModel().select(1);return null;});
            snapshot(temp.resolve("tools-privacy.png"));
            fx(()->{stage.setWidth(940);stage.setHeight(650);return null;});
            snapshot(temp.resolve("tools-compact.png"));
            fx(()->{stage.setWidth(1280);stage.setHeight(820);browser.setFocusMode(true);browser.newTab();return null;});
            BrowserChecks.equal(fx(()->count()),3);
            fx(()->{browser.setFocusMode(false);browser.moveCurrentTab("Default");return null;});
            BrowserChecks.equal(fx(()->browser.preferences().workspace),"Default");
            fx(()->{browser.features();press("Reader mode");return null;});
            await("reader window",()->Window.getWindows().stream().anyMatch(w->w!=stage && w.isShowing()));
            BrowserChecks.check(fx(()->Window.getWindows().stream().filter(w->w!=stage).anyMatch(w->w.getScene().lookup("#article") instanceof Label l && l.getText().contains("observatory"))),"reader extraction");
            fx(()->{for(Window w:List.copyOf(Window.getWindows()))if(w!=stage)w.hide();return null;});
            fx(()->{browser.features();press("Translate page ↗");return null;});
            BrowserChecks.equal(fx(()->count()),4);
            BrowserChecks.check(fx(()->browser.currentUrl().startsWith("https://translate.google.com/translate?sl=auto&tl=en&u=")),"translation new-tab destination");
            fx(()->{browser.currentPage().stop();browser.navigateTo(base+"/article");return null;});
            await("local article",()->browser.currentPage().state.get()==Worker.State.SUCCEEDED && browser.currentUrl().startsWith(base));
            if(fx(()->browser.currentPage() instanceof NativeWebPage)){
                if ("true".equals(System.getenv("FLUX_CHECK_KEYCHAIN"))) {
                    String user="flux-test-"+UUID.randomUUID(), origin="https://flux-feature-test.invalid";
                    NativeWebPage page=fx(()->(NativeWebPage)browser.currentPage());
                    try {
                        fx(()->page.keychain("save",origin,user,"temporary-check-value".toCharArray())).get(95,TimeUnit.SECONDS);
                        BrowserChecks.equal(fx(()->page.keychain("get",origin,user,new char[0])).get(95,TimeUnit.SECONDS),"temporary-check-value");
                        fx(()->page.keychain("save",origin,user,"updated-check-value".toCharArray())).get(95,TimeUnit.SECONDS);
                        BrowserChecks.equal(fx(()->page.keychain("get",origin,user,new char[0])).get(95,TimeUnit.SECONDS),"updated-check-value");
                    } finally { fx(()->page.keychain("delete",origin,user,new char[0])).get(95,TimeUnit.SECONDS); }
                    try { fx(()->page.keychain("get",origin,user,new char[0])).get(95,TimeUnit.SECONDS);throw new AssertionError("Deleted test login still exists"); }
                    catch(ExecutionException expected) { }
                    System.out.println("Native Keychain save/read/update/delete passed; disposable login removed.");
                }
                // Verify WebKit accepts compiled content rules, and actually blocks a fixture resource.
                String rule="[{\"trigger\":{\"url-filter\":\".*/blocked-resource.*\"},\"action\":{\"type\":\"block\"}}]";
                fx(()->NativeWebPage.configurePrivacy(rule,true,true)).get(30,TimeUnit.SECONDS);
                // evaluateJavaScript does not await Promises: use an explicit probe variable instead.
                script("window.blockProbe='pending';fetch('/blocked-resource').then(()=>blockProbe='allowed',()=>blockProbe='blocked');true");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(!script("window.blockProbe").equals("blocked") && System.nanoTime()<deadline)Thread.sleep(100);
                BrowserChecks.equal(script("window.blockProbe"),"blocked");
                fx(()->NativeWebPage.configurePrivacy("",true,true)).get(30,TimeUnit.SECONDS);
                Path destination=temp.resolve("downloaded.pdf");
                Files.writeString(destination,"previous fixture content");
                fx(()->{NativeWebPage.downloadDestinationForTesting(destination);((NativeWebPage)browser.currentPage()).action("download",base+"/file");return null;});
                await("native download",()->browser.downloads().items.stream().anyMatch(d->d.status().equals("Complete")));
                BrowserChecks.check(Arrays.equals(Files.readAllBytes(destination),pdf),"download bytes preserved");
                fx(()->{browser.openPdf(destination);return null;});
                await("PDFKit document",()->browser.currentPage() instanceof NativeWebPage n && n.pdf.get() && n.state.get()==Worker.State.SUCCEEDED);
                BrowserChecks.check(fx(()->root().lookup("#documentBar").isVisible()),"PDF toolbar visible");
                Thread.sleep(1200);
                if ("true".equals(System.getenv("FLUX_CHECK_PDF_SCREEN"))) {
                    fx(()->{((NativeWebPage)browser.currentPage()).foregroundForTesting();return null;});Thread.sleep(500);
                    var rectangle=fx(()->new java.awt.Rectangle((int)stage.getX(),(int)stage.getY(),(int)stage.getWidth(),(int)stage.getHeight()));
                    javax.imageio.ImageIO.write(new java.awt.Robot().createScreenCapture(rectangle),"png",temp.resolve("pdf-screen.png").toFile());
                }
                fx(()->((NativeWebPage)browser.currentPage()).snapshot(temp.resolve("pdf-native.png"))).get(20,TimeUnit.SECONDS);
                var rendered=javax.imageio.ImageIO.read(temp.resolve("pdf-native.png").toFile());
                int ink=0;
                for(int y=rendered.getHeight()/10;y<rendered.getHeight()*4/5;y++)for(int x=rendered.getWidth()/20;x<rendered.getWidth()*19/20;x++){
                    int rgb=rendered.getRGB(x,y);if(((rgb>>16)&255)<100 && ((rgb>>8)&255)<100 && (rgb&255)<100)ink++;
                }
                BrowserChecks.check(ink>300,"current PDF page raster includes text");
                fx(()->{((NativeWebPage)browser.currentPage()).action("pdfNext","");browser.features();return null;});
                BrowserChecks.check(fx(()->!browser.currentPage().view().isVisible()),"tools hide native PDF");
                fx(()->{browser.dismissPanels();browser.navigateTo(base+"/article");return null;});
                await("web after PDF",()->browser.currentPage().state.get()==Worker.State.SUCCEEDED && !((NativeWebPage)browser.currentPage()).pdf.get());
            }
            fx(()->{browser.createWorkspace("Research");browser.navigateTo(base+"/article");return null;});
            await("workspace navigation",()->browser.currentPage().state.get()==Worker.State.SUCCEEDED);
            fx(()->{browser.close();stage.close();return null;});
            // Store drains on shutdown; wait for its final snapshot before opening another browser.
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<until){try(var store=new FeatureStore()){if(store.load().get().workspace.equals("Research"))break;}Thread.sleep(100);}
            fx(()->{start();return null;});
            await("restart restores workspace",()->browser.preferences().workspace.equals("Research") && browser.currentPage()!=null && browser.currentPage().state.get()==Worker.State.SUCCEEDED);
            BrowserChecks.equal(backgroundRequests.get(),0);
            BrowserChecks.check(failure.get()==null,"no uncaught FX exception");
            System.out.println("FeatureUiChecks passed: lazy session/restart, workspaces/focus, arithmetic, reader, translation destination, WebKit rule enforcement, download bytes, PDFKit and web/PDF switching. Screenshots: "+temp);
        } finally { fx(()->{if(browser!=null)browser.close();if(stage!=null)stage.close();return null;});Platform.exit();server.stop(0);workers.shutdownNow(); }
    }
    private static void start(){var v=Views.<BrowserController>load("BrowserWindow");browser=v.controller();stage=new Stage();stage.setScene(new Scene(v.root(),1280,820));browser.configure(stage,new DatabaseManager("jdbc:postgresql://127.0.0.1:1/flux_test","flux",""));stage.show();}
    private static Parent root(){return stage.getScene().getRoot();}
    private static int count(){return ((HBox)root().lookup("#tabHeaders")).getChildren().size();}
    private static void press(String name){Button b=root().lookupAll(".button").stream().filter(n->n instanceof Button a && name.equals(a.getText())).map(n->(Button)n).findFirst().orElseThrow();b.fire();}
    private static String script(String source)throws Exception{return fx(()->browser.currentPage().evaluate(source)).get(20,TimeUnit.SECONDS);}
    private static <T>T fx(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(()->{if(stage!=null){root().applyCss();root().layout();}return action.call();});Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(String name,Callable<Boolean> condition)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<until){if(failure.get()!=null)throw new AssertionError(failure.get());if(fx(condition))return;Thread.sleep(100);}throw new AssertionError("Timed out: "+name);}
    private static void snapshot(Path path)throws Exception{
        Thread.sleep(200);var image=fx(()->stage.getScene().snapshot(null));int w=(int)image.getWidth(),h=(int)image.getHeight();
        int[] pixels=new int[w*h];image.getPixelReader().getPixels(0,0,w,h,javafx.scene.image.PixelFormat.getIntArgbInstance(),pixels,0,w);
        var output=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);output.setRGB(0,0,w,h,pixels,0,w);javax.imageio.ImageIO.write(output,"png",path.toFile());
    }
    private static byte[] pdfFixture(){
        var out=new StringBuilder("%PDF-1.4\n");var positions=new ArrayList<Integer>();
        String stream="BT /F1 24 Tf 60 700 Td (Flux PDF verification) Tj ET\n";
        var objects=List.of("<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 6 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>","<< /Length "+stream.length()+" >>\nstream\n"+stream+"endstream");
        for(int i=0;i<objects.size();i++){positions.add(out.length());out.append(i+1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");}
        int xref=out.length();out.append("xref\n0 7\n0000000000 65535 f \n");for(int pos:positions)out.append(String.format(Locale.ROOT,"%010d 00000 n \n",pos));
        out.append("trailer\n<< /Size 7 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");return out.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
