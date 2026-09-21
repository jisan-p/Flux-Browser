package com.flux.browser.controller;

import com.flux.browser.feature.*;
import com.flux.browser.model.HistoryEntry;
import com.flux.browser.util.*;
import com.flux.browser.web.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.*;

public final class FeaturesController {
    @FXML private BorderPane root;
    @FXML private javafx.scene.layout.FlowPane compatibilityPageTools;
    @FXML private TabPane featureTabs;
    @FXML private Label feedback,loginOrigin;
    @FXML private ComboBox<String> workspace,language,passwordProvider;
    @FXML private TextField workspaceName,keyword,providerName,providerUrl,textQuery,loginUser;
    @FXML private PasswordField loginPassword;
    @FXML private CheckBox restore,focus,blocker,https,phishing,fullText;
    @FXML private ListView<FeatureStore.SearchProvider> providers;
    @FXML private ListView<HistoryEntry> textResults;
    @FXML private ListView<Downloads.Item> downloads;
    private BrowserController browser;
    private boolean showing;
    private final ExecutorService work = workers();
    private static ExecutorService workers() {
        var pool = new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"flux-tools");t.setDaemon(true);return t;});
        pool.allowCoreThreadTimeOut(true); return pool;
    }
    public void configure(BrowserController browser){
        this.browser=browser;
        compatibilityPageTools.setVisible(!NativeWebPage.enabled()); compatibilityPageTools.setManaged(!NativeWebPage.enabled());
        language.getItems().setAll(PageActions.LANGUAGES);
        language.valueProperty().addListener((o, before, after) -> { if (!showing && after != null) { browser.preferences().translation = after; browser.saveSession(); } });
        feedback.textProperty().addListener((o, before, after) -> { if (!root.isVisible()) browser.message(after); });
        passwordProvider.getItems().setAll("macOS Keychain","Bitwarden CLI","1Password CLI");
        
        providers.getSelectionModel().selectedItemProperty().addListener((o,a,p)->{if(p!=null){keyword.setText(p.keyword());providerName.setText(p.name());providerUrl.setText(p.template());}});
        downloads.setItems(browser.downloads().items);
        featureTabs.getSelectionModel().selectedItemProperty().addListener((o,before,after)->{
            if (after!=null && after.getText().equals("Downloads") && root.isVisible()) {
                browser.showDownloads();
                featureTabs.getSelectionModel().select(before==null?featureTabs.getTabs().getFirst():before);
            }
        });
        if(!NativeWebPage.enabled()){blocker.setDisable(true);https.setDisable(true);phishing.setDisable(true);}
        textResults.setCellFactory(v->new ListCell<>(){@Override protected void updateItem(HistoryEntry h,boolean empty){super.updateItem(h,empty);setText(empty||h==null?null:h.title()+"\n"+h.url());}});
    }
    public void selectSection(String name) { featureTabs.getTabs().stream().filter(t->t.getText().equals(name)).findFirst().ifPresent(t->featureTabs.getSelectionModel().select(t)); }
    public void show(){
        showing=true;var s=browser.preferences();workspace.getItems().setAll(s.workspaces);workspace.setValue(s.workspace);
        restore.setSelected(s.restore);blocker.setSelected(s.blocker);https.setSelected(s.https);phishing.setSelected(s.phishing);fullText.setSelected(s.fullText);
        focus.setSelected(browser.focusMode());language.setValue(s.translation);passwordProvider.setValue(s.passwordProvider);
        providers.getItems().setAll(s.providers);loginOrigin.setText("Current page: "+browser.currentUrl());loginPassword.clear();showing=false;
    }
    @FXML private void saveOptions(){var s=browser.preferences();s.restore=restore.isSelected();s.blocker=blocker.isSelected();s.https=https.isSelected();s.phishing=phishing.isSelected();s.fullText=fullText.isSelected();browser.saveSession();browser.applyPrivacy();feedback.setText("Preferences saved. Reload a page after changing its blocking rules.");}
    @FXML private void createWorkspace(){try{browser.createWorkspace(workspaceName.getText());workspaceName.clear();show();}catch(Exception e){error(e);}}
    @FXML private void switchWorkspace(){browser.switchWorkspace(workspace.getValue());show();}
    @FXML private void moveTab(){browser.moveCurrentTab(workspace.getValue());show();}
    @FXML private void deleteWorkspace(){if(Dialogs.confirm(browser.window(),"Delete workspace?","Its tabs will move to Default.")){browser.deleteWorkspace(workspace.getValue());show();}}
    @FXML private void focusMode(){browser.setFocusMode(focus.isSelected());}
    @FXML private void reopen(){browser.reopenClosedTab();}
    @FXML private void updateRules(){async(()->ContentRules.update(browser.profileDirectory()),n->{browser.applyPrivacy();feedback.setText("Updated "+n+" domain rules from AdGuard DNS Filter.");});}
    @FXML private void allowSite(){try{browser.preferences().allowedSites.add(currentHost());browser.saveSession();browser.applyPrivacy();feedback.setText("Blocking disabled for this site. Reload the page.");}catch(Exception e){error(e);}}
    @FXML private void blockSite(){try{browser.preferences().allowedSites.remove(currentHost());browser.saveSession();browser.applyPrivacy();feedback.setText("Blocking enabled for this site. Reload the page.");}catch(Exception e){error(e);}}
    @FXML private void clearIndex(){browser.history().clearIndex().whenComplete((v,e)->Platform.runLater(()->{if(e!=null)error(e);else feedback.setText("Page text removed.");}));}
    @FXML private void saveProvider(){try{var p=new FeatureStore.SearchProvider(keyword.getText().trim(),providerName.getText().trim(),providerUrl.getText().trim());SearchTools.validate(p);var list=browser.preferences().providers;if(list.size()>=50 && list.stream().noneMatch(x->x.keyword().equals(p.keyword())))throw new IllegalArgumentException("At most 50 providers");list.removeIf(x->x.keyword().equals(p.keyword()));list.add(p);browser.saveSession();show();}catch(Exception e){error(e);}}
    @FXML private void removeProvider(){var p=providers.getSelectionModel().getSelectedItem();if(p!=null){browser.preferences().providers.remove(p);browser.saveSession();show();}}
    @FXML private void searchText(){browser.history().searchText(textQuery.getText()).whenComplete((rows,e)->Platform.runLater(()->{if(e!=null)error(e);else textResults.getItems().setAll(rows);}));}
    @FXML private void openResult(){var h=textResults.getSelectionModel().getSelectedItem();if(h!=null)browser.openNewUrl(h.url());}
    @FXML public void reader(){
        BrowserPage p=browser.currentPage();if(p==null || p instanceof NativeWebPage n && n.pdf.get()){feedback.setText("Open an article first.");return;}
        String url=browser.currentUrl();
        async(PageText::readerScript,script->p.evaluate(script).whenComplete((json,e)->Platform.runLater(()->{
            if(browser.isClosed() || browser.currentPage()!=p || !url.equals(browser.currentUrl()))return;
            if(e!=null){error(e);return;}if(json==null||json.isBlank()){feedback.setText("No readable article found on this page.");return;}
            try{var article=com.google.gson.JsonParser.parseString(json).getAsJsonObject();var view=Views.<ReaderController>load("Reader");
                Stage window=new Stage();window.initOwner(browser.window());window.setTitle("Reader · Flux");window.setScene(new Scene(view.root()));
                view.controller().configure(article.get("title").getAsString(),article.get("byline").getAsString(),article.get("text").getAsString(),()->{window.close();browser.navigateTo(url);});window.show();
            }catch(Exception failure){error(failure);}
        })));
    }
    @FXML private void translate() { try { browser.openNewUrl(PageActions.translation(browser.currentUrl(), language.getValue())); } catch(Exception e) { error(e); } }
    @FXML private void showDownloads() { browser.showDownloads(); }
    @FXML private void openPdf(){FileChooser chooser=new FileChooser();chooser.setTitle("Open PDF");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF documents","*.pdf"));var file=chooser.showOpenDialog(browser.window());if(file!=null)browser.openPdf(file.toPath());}
    @FXML private void cancelDownload(){var d=downloads.getSelectionModel().getSelectedItem();if(d!=null)NativeWebPage.downloadAction(d.id(),"cancel");}
    @FXML private void revealDownload(){var d=downloads.getSelectionModel().getSelectedItem();if(d!=null && d.status().equals("Complete"))NativeWebPage.downloadAction(d.id(),"reveal");}
    @FXML private void downloadedPdf(){var d=downloads.getSelectionModel().getSelectedItem();if(d!=null && d.status().equals("Complete")&&d.path().toLowerCase().endsWith(".pdf"))browser.openPdf(Path.of(d.path()));}
    @FXML private void saveLogin(){keychain("save");}
    @FXML private void deleteLogin(){if(Dialogs.confirm(browser.window(),"Delete Flux login?","Remove this username for the current HTTPS origin from Flux's Keychain entries?"))keychain("delete");}
    private void keychain(String op){try{String origin=PasswordProviders.origin(browser.currentUrl());String username=loginUser.getText();if(username.isBlank())throw new IllegalArgumentException("Enter a username.");
        if(!(browser.currentPage() instanceof NativeWebPage p))throw new IllegalStateException("macOS native engine required.");
        char[] password=loginPassword.getText().toCharArray();loginPassword.clear();
        p.keychain(op,origin,username,password).whenComplete((v,e)->Platform.runLater(()->{if(e!=null)error(e);else feedback.setText(op.equals("save")?"Login saved in Keychain.":"Login removed.");}));Arrays.fill(password,'\0');
    }catch(Exception e){error(e);}}
    @FXML private void fillLogin(){try{
        String origin=PasswordProviders.origin(browser.currentUrl()),user=loginUser.getText(),provider=passwordProvider.getValue();BrowserPage p=browser.currentPage();
        if(p==null)throw new IllegalArgumentException("Open an HTTPS login page first.");
        browser.preferences().passwordProvider=provider;browser.saveSession();
        CompletableFuture<PasswordProviders.Login> result=provider.equals("macOS Keychain") && p instanceof NativeWebPage nativePage
            ?nativePage.keychain("get",origin,user,new char[0]).thenApply(value->new PasswordProviders.Login(user,value))
            :CompletableFuture.supplyAsync(()->{try{return PasswordProviders.fetch(provider,user,origin);}catch(Exception e){throw new CompletionException(e);}},work);
        result.whenComplete((login,e)->Platform.runLater(()->{
            if(e!=null){error(e);return;}if(browser.currentPage()!=p || !origin.equals(safeOrigin(browser.currentUrl()))){feedback.setText("Page changed; login was not filled.");return;}
            p.evaluate(PasswordProviders.fillScript(origin,login)).whenComplete((filled,error)->Platform.runLater(()->{if(error!=null)error(error);else {feedback.setText("true".equals(filled)?"Login filled; review and submit the form yourself.":"No visible login form found.");if("true".equals(filled))browser.dismissPanels();}}));
        }));
    }catch(Exception e){error(e);}}
    private String currentHost(){String h=java.net.URI.create(UrlResolver.webAddress(browser.currentUrl())).getHost();if(h==null)throw new IllegalArgumentException("Open a website first.");return h;}
    private static String safeOrigin(String url){try{return PasswordProviders.origin(url);}catch(Exception e){return "";}}
    private <T> void async(Callable<T> task,java.util.function.Consumer<T> done){feedback.setText("Working…");try{CompletableFuture.supplyAsync(()->{try{return task.call();}catch(Exception e){throw new CompletionException(e);}},work).whenComplete((v,e)->Platform.runLater(()->{if(browser.isClosed())return;if(e!=null)error(e);else done.accept(v);}));}catch(RejectedExecutionException busy){feedback.setText("Tools are busy; try again when the current task finishes.");}}
    private void error(Throwable error){while((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause()!=null)error=error.getCause();
        String message=error instanceof IllegalArgumentException || error instanceof IllegalStateException ? error.getMessage() : "Action could not be completed. Check availability and try again.";
        feedback.setText(message==null?"Action could not be completed.":message);}
    @FXML private void developerTools(){browser.showDeveloperTools();}
    @FXML private void developerConsole(){browser.developerConsole();}
    @FXML private void dismiss(){browser.dismissPanels();}
    public void close(){loginPassword.clear();work.shutdownNow();}
}
