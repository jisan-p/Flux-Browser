#import <Cocoa/Cocoa.h>
#import <WebKit/WebKit.h>
#include <jni.h>

// All WebKit objects and this registry belong exclusively to AppKit's main thread.
static JavaVM *vm;
static jclass bridge;
static jmethodID callback;
static NSMutableDictionary<NSNumber *, id> *pages;
static long long popupID = -1;
static id keyMonitor;

static NSString *string(JNIEnv *env, jstring value) {
    if (!value) return @"";
    const jchar *chars = (*env)->GetStringChars(env, value, NULL);
    NSString *result = [[NSString alloc] initWithCharacters:(const unichar *)chars length:(*env)->GetStringLength(env, value)];
    (*env)->ReleaseStringChars(env, value, chars);
    return result;
}
static jstring javaString(JNIEnv *env, NSString *value) {
    if (!value) value = @"";
    NSUInteger length = value.length;
    unichar *chars = malloc(MAX((NSUInteger)1, length) * sizeof(unichar));
    [value getCharacters:chars range:NSMakeRange(0, length)];
    jstring result = (*env)->NewString(env, (jchar *)chars, (jsize)length);
    free(chars);
    return result;
}
static void emit(long long identifier, NSString *kind, NSString *value, long long token, double number) {
    JNIEnv *env = NULL;
    // Glass owns the AppKit thread's JVM attachment. A detached thread means toolkit shutdown;
    // never attach/detach that shared UI thread on behalf of a late WebKit callback.
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK) return;
    jstring k = javaString(env, kind), v = javaString(env, value);
    (*env)->CallStaticVoidMethod(env, bridge, callback, (jlong)identifier, k, v, (jlong)token, (jdouble)number);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionDescribe(env); (*env)->ExceptionClear(env); }
    (*env)->DeleteLocalRef(env, k); (*env)->DeleteLocalRef(env, v);
}

#include "FluxServices.h"
#include "FluxInspector.h"
#include "FluxContextMenu.h"
#include "FluxViewport.h"
#include "FluxApplicationMenu.h"

@interface FluxPage : NSObject <WKNavigationDelegate, WKUIDelegate, PDFViewDelegate>
@property(nonatomic) long long identifier;
@property(nonatomic, strong) FluxContextWebView *web;
@property(nonatomic, strong) FluxViewport *viewport;
@property(nonatomic, strong) PDFView *pdf;
@property(nonatomic, copy) NSString *pdfPath;
@property(nonatomic) BOOL shown;
@property(nonatomic) NSUInteger documentVersion;
@property(nonatomic) BOOL positionPdf;
@property(nonatomic, weak) NSWindow *owner;
@property(nonatomic, weak) NSView *glass;
@property(nonatomic) BOOL disposed;
@property(nonatomic) BOOL stateQueued;
@property(nonatomic) BOOL inspectionEnabled;
@property(nonatomic) NSRect viewportFrame;
@property(nonatomic) BOOL focusWhenShown;
@property(nonatomic, strong) WKNavigation *navigation;
@property(nonatomic, strong) WKNavigation *stoppedNavigation;
- (instancetype)initWithID:(long long)identifier configuration:(WKWebViewConfiguration *)configuration;
- (void)attach:(NSWindow *)window;
- (void)publish;
- (void)detachInspector;
- (void)dispose;
@end

@implementation FluxPage
- (instancetype)initWithID:(long long)identifier configuration:(WKWebViewConfiguration *)configuration {
    self = [super init];
    if (self) {
        _identifier = identifier;
        // Popup configurations can share their opener's content controller. Keep each page's
        // selection handler separate so opening a popup never steals the opener's messages.
        WKUserContentController *controller = [WKUserContentController new];
        for (WKUserScript *script in configuration.userContentController.userScripts)
            if (![script.source hasPrefix:@"if(!globalThis.fluxContextInstalled)"]) [controller addUserScript:script];
        configuration.userContentController = controller;
        configuration.preferences.fraudulentWebsiteWarningEnabled = phishingWarnings;
        configuration.upgradeKnownHostsToHTTPS = secureHosts;
        if (blockingRules) [configuration.userContentController addContentRuleList:blockingRules];
        _web = [[FluxContextWebView alloc] initWithFrame:NSMakeRect(0, 0, 800, 600) configuration:configuration];
        _web.customUserAgent = @"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15 Flux/1.0";
        _viewport = [[FluxViewport alloc] initWithFrame:NSZeroRect];
        _viewport.page = _web;
        _viewport.hidden = YES;
        _web.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
        [_viewport addSubview:_web];
        _web.fluxIdentifier = identifier;
        _web.navigationDelegate = self; _web.UIDelegate = self;
        _inspectionEnabled = enableInspection(_web); // Frontend still loads only on request.
        inspectorDelegate(fluxInspector(_web), self);
        __weak FluxPage *weakPage = self;
        _web.didInspect = ^{ [weakPage detachInspector]; };
        // Covers first open, cached frontend reuse and docking from the inspector UI.
        // Wait for WebKit to finish attaching before moving its frontend to a window.
        _viewport.inspectorAttached = ^{
            dispatch_async(dispatch_get_main_queue(), ^{ [weakPage detachInspector]; });
        };
        _web.hidden = YES;
        _web.allowsBackForwardNavigationGestures = YES;
        for (NSString *key in @[@"URL", @"title", @"estimatedProgress", @"canGoBack", @"canGoForward"])
            [_web addObserver:self forKeyPath:key options:0 context:NULL];
    }
    return self;
}
// WebKit loads its frontend asynchronously for toolbar, shortcut and native menu actions.
- (void)inspectorFrontendLoaded:(id)inspector {
    __weak FluxPage *weakPage = self;
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *page = weakPage;
        if (!page || page.disposed || page.pdf || !inspectorFlag(inspector,@"isVisible")) return;
        [page detachInspector];
    });
}
- (void)detachInspector {
    if (self.disposed || self.pdf) return;
    id inspector = fluxInspector(self.web);
    if (!inspectorFlag(inspector,@"isVisible")) return;
    inspectorCall(inspector,@"detach");
    self.web.frame = self.viewport.bounds;
    NSWindow *window = inspectorFrontend(inspector).window;
    if (window && window != self.owner) [window makeKeyAndOrderFront:nil];
}
- (void)attach:(NSWindow *)window {
    self.owner = window; self.glass = window.contentView;
    // Only this container owns window coordinates; WebKit lays out within the page.
    [window.contentView addSubview:self.viewport positioned:NSWindowAbove relativeTo:nil];
    [self publish];
}
- (void)publish {
    if (self.disposed) return;
    if (self.pdf) {
        emit(self.identifier, @"url", [NSURL fileURLWithPath:self.pdfPath].absoluteString,0,0);
        emit(self.identifier, @"title", self.pdfPath.lastPathComponent,0,0);
        emit(self.identifier, @"history", @"",0,0); return;
    }
    emit(self.identifier, @"url", self.web.URL.absoluteString, 0, 0);
    emit(self.identifier, @"title", self.web.title, 0, 0);
    emit(self.identifier, @"progress", @"", 0, self.web.estimatedProgress);
    emit(self.identifier, @"history", @"", (self.web.canGoBack ? 1 : 0) | (self.web.canGoForward ? 2 : 0), 0);
}
- (void)observeValueForKeyPath:(NSString *)key ofObject:(id)object change:(NSDictionary *)change context:(void *)context {
    if (self.disposed || self.stateQueued) return;
    self.stateQueued = YES;
    dispatch_async(dispatch_get_main_queue(), ^{ self.stateQueued = NO; [self publish]; });
}
- (void)webView:(WKWebView *)web didStartProvisionalNavigation:(WKNavigation *)navigation {
    if (navigation == self.stoppedNavigation) return;
    [self.web clearContext];
    self.navigation = navigation; [self publish]; emit(self.identifier, @"start", @"", 0, 0);
}
- (void)webView:(WKWebView *)web didFinishNavigation:(WKNavigation *)navigation {
    if (navigation != self.navigation) return;
    [self publish]; emit(self.identifier, @"finish", @"", 0, 0);
}
- (void)failure:(NSError *)error navigation:(WKNavigation *)navigation {
    if (navigation != self.navigation || error.code == NSURLErrorCancelled) return;
    [self publish]; emit(self.identifier, @"error", error.localizedDescription, 0, 0);
}
- (void)webView:(WKWebView *)web didFailNavigation:(WKNavigation *)navigation withError:(NSError *)error { [self failure:error navigation:navigation]; }
- (void)webView:(WKWebView *)web didFailProvisionalNavigation:(WKNavigation *)navigation withError:(NSError *)error { [self failure:error navigation:navigation]; }
- (void)webViewWebContentProcessDidTerminate:(WKWebView *)web {
    emit(self.identifier, @"error", @"The web content process stopped. Reload this tab to recover.", 0, 0);
}
- (void)webView:(WKWebView *)web decidePolicyForNavigationAction:(WKNavigationAction *)action decisionHandler:(void (^)(WKNavigationActionPolicy))decision {
    NSString *scheme = action.request.URL.scheme.lowercaseString;
    // Preserve normal TLS validation. Do not auto-launch external programs from page navigation.
    BOOL allowed = [@[@"http", @"https", @"about", @"blob", @"data"] containsObject:scheme];
    WKNavigationActionPolicy policy = allowed ? (action.shouldPerformDownload ? WKNavigationActionPolicyDownload : WKNavigationActionPolicyAllow) : WKNavigationActionPolicyCancel;
    if (allowed && rulesLoading) {
        if (!pendingLoads) pendingLoads = [NSMutableArray new];
        [pendingLoads addObject:^{ decision(self.disposed ? WKNavigationActionPolicyCancel : policy); }];
    } else decision(policy);
}
- (void)webView:(WKWebView *)web decidePolicyForNavigationResponse:(WKNavigationResponse *)response decisionHandler:(void (^)(WKNavigationResponsePolicy))decision {
    // PDF is downloaded explicitly and can then be opened in Flux's PDFKit viewer.
    decision(response.canShowMIMEType ? WKNavigationResponsePolicyAllow : WKNavigationResponsePolicyDownload);
}
- (void)webView:(WKWebView *)web navigationAction:(WKNavigationAction *)action didBecomeDownload:(WKDownload *)download { trackDownload(download,self.owner); emit(self.identifier,@"finish",@"",0,0); }
- (void)webView:(WKWebView *)web navigationResponse:(WKNavigationResponse *)response didBecomeDownload:(WKDownload *)download { trackDownload(download,self.owner); emit(self.identifier,@"finish",@"",0,0); }
- (WKWebView *)webView:(WKWebView *)web createWebViewWithConfiguration:(WKWebViewConfiguration *)configuration
        forNavigationAction:(WKNavigationAction *)action windowFeatures:(WKWindowFeatures *)features {
    // Returning the configured view preserves POST popups and window.opener; do not re-load the URL.
    FluxPage *child = [[FluxPage alloc] initWithID:popupID-- configuration:configuration];
    pages[@(child.identifier)] = child;
    emit(self.identifier, @"popup", @"", child.identifier, 0);
    return child.web;
}
- (void)webViewDidClose:(WKWebView *)web { emit(self.identifier, @"close", @"", 0, 0); }
- (void)PDFViewWillClickOnLink:(PDFView *)sender withURL:(NSURL *)url {
    if ([@[@"http",@"https"] containsObject:url.scheme.lowercaseString]) emit(self.identifier,@"openURL",url.absoluteString,0,0);
}
- (NSAlert *)alert:(NSString *)message frame:(WKFrameInfo *)frame {
    NSAlert *alert = [NSAlert new];
    alert.messageText = frame.request.URL.host ?: @"Page message";
    alert.informativeText = message ?: @"";
    return alert;
}
- (void)webView:(WKWebView *)web runJavaScriptAlertPanelWithMessage:(NSString *)message initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(void))done {
    if (self.disposed || !self.owner) { done(); return; }
    NSAlert *alert = [self alert:message frame:frame]; [alert addButtonWithTitle:@"OK"];
    [alert beginSheetModalForWindow:self.owner completionHandler:^(NSModalResponse response) { done(); }];
}
- (void)webView:(WKWebView *)web runJavaScriptConfirmPanelWithMessage:(NSString *)message initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(BOOL))done {
    if (self.disposed || !self.owner) { done(NO); return; }
    NSAlert *alert = [self alert:message frame:frame]; [alert addButtonWithTitle:@"OK"]; [alert addButtonWithTitle:@"Cancel"];
    [alert beginSheetModalForWindow:self.owner completionHandler:^(NSModalResponse response) { done(response == NSAlertFirstButtonReturn); }];
}
- (void)webView:(WKWebView *)web runJavaScriptTextInputPanelWithPrompt:(NSString *)prompt defaultText:(NSString *)text initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(NSString *))done {
    if (self.disposed || !self.owner) { done(nil); return; }
    NSAlert *alert = [self alert:prompt frame:frame]; [alert addButtonWithTitle:@"OK"]; [alert addButtonWithTitle:@"Cancel"];
    NSTextField *input = [[NSTextField alloc] initWithFrame:NSMakeRect(0, 0, 320, 24)]; input.stringValue = text ?: @""; alert.accessoryView = input;
    [alert beginSheetModalForWindow:self.owner completionHandler:^(NSModalResponse response) { done(response == NSAlertFirstButtonReturn ? input.stringValue : nil); }];
}
- (void)webView:(WKWebView *)web runOpenPanelWithParameters:(WKOpenPanelParameters *)parameters initiatedByFrame:(WKFrameInfo *)frame completionHandler:(void (^)(NSArray<NSURL *> *))done {
    if (self.disposed || !self.owner) { done(nil); return; }
    NSOpenPanel *panel = [NSOpenPanel openPanel];
    panel.allowsMultipleSelection = parameters.allowsMultipleSelection;
    panel.canChooseDirectories = parameters.allowsDirectories; panel.canChooseFiles = YES;
    [panel beginSheetModalForWindow:self.owner completionHandler:^(NSModalResponse response) { done(response == NSModalResponseOK ? panel.URLs : nil); }];
}
- (void)dispose {
    if (self.disposed) return;
    self.disposed = YES; [self.web clearContext]; self.web.didInspect = nil;
    self.viewport.inspectorAttached = nil;
    inspectorDelegate(fluxInspector(self.web), nil);
    if (self.inspectionEnabled) inspectorCall(fluxInspector(self.web),@"close");
    for (NSString *key in @[@"URL", @"title", @"estimatedProgress", @"canGoBack", @"canGoForward"])
        [self.web removeObserver:self forKeyPath:key];
    self.web.navigationDelegate = nil; self.web.UIDelegate = nil;
    [self.web stopLoading]; [self.web removeFromSuperview]; self.web = nil; [self.pdf removeFromSuperview]; self.pdf.document = nil; self.pdf = nil;
    [self.viewport removeFromSuperview]; self.viewport = nil;
}
@end

static void installKeys(void) {
    if (keyMonitor) return;
    keyMonitor = [NSEvent addLocalMonitorForEventsMatchingMask:NSEventMaskKeyDown handler:^NSEvent *(NSEvent *event) {
        for (FluxPage *p in pages.allValues) {
            NSResponder *responder = p.owner.firstResponder;
            NSView *content = p.pdf ?: p.web;
            if (content.hidden || event.window != p.owner || ![responder isKindOfClass:NSView.class]
                || ![(NSView *)responder isDescendantOf:content]) continue;
            BOOL cmd = (event.modifierFlags & NSEventModifierFlagCommand) != 0;
            BOOL shift = (event.modifierFlags & NSEventModifierFlagShift) != 0;
            BOOL control = (event.modifierFlags & NSEventModifierFlagControl) != 0;
            BOOL alt = (event.modifierFlags & NSEventModifierFlagOption) != 0;
            NSString *key = event.charactersIgnoringModifiers.lowercaseString;
            NSString *action = nil;
            if (event.keyCode == 111 || (cmd && (alt || shift) && [key isEqual:@"i"])) action = @"developerTools";
            else if (cmd && alt && [key isEqual:@"c"]) action = @"developerConsole";
            else if (cmd && shift && [key isEqual:@"t"]) action = @"reopen";
            else if (cmd && [@[@"l", @"t", @"w", @"r", @"d", @"y", @"1", @"2", @"3", @"4", @"5", @"6", @"7", @"8", @"9"] containsObject:key]) action = key;
            else if (cmd && shift && [key isEqual:@"b"]) action = @"bookmarks";
            else if (control && event.keyCode == 48) action = shift ? @"previousTab" : @"nextTab";
            else if (alt && event.keyCode == 123) action = @"back";
            else if (alt && event.keyCode == 124) action = @"forward";
            else if (alt && event.keyCode == 115) action = @"home";
            else if (event.keyCode == 96) action = @"r";
            else if (event.keyCode == 53) action = @"stop";
            if (action) { emit(p.identifier, @"shortcut", action, 0, 0); return nil; }
            if (cmd) {
                NSDictionary *edits = @{@"c":@"copy:", @"v":@"paste:", @"x":@"cut:", @"a":@"selectAll:", @"z":shift ? @"redo:" : @"undo:"};
                NSString *selector = edits[key];
                if (selector && [NSApp sendAction:NSSelectorFromString(selector) to:nil from:nil]) return nil;
            }
        }
        return event;
    }];
}
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *machine, void *reserved) {
    vm = machine; JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK) return JNI_ERR;
    jclass local = (*env)->FindClass(env, "com/flux/browser/web/NativeWebPage");
    bridge = (*env)->NewGlobalRef(env, local); (*env)->DeleteLocalRef(env, local);
    callback = (*env)->GetStaticMethodID(env, bridge, "event", "(JLjava/lang/String;Ljava/lang/String;JD)V");
    return callback ? JNI_VERSION_1_8 : JNI_ERR;
}
#define JNI(name) Java_com_flux_browser_web_NativeWebPage_##name
JNIEXPORT void JNICALL JNI(applicationMenuCommand)(JNIEnv *env, jclass cls, jlong token, jstring operation, jstring value) {
    NSString *op = string(env, operation), *argument = string(env, value);
    dispatch_async(dispatch_get_main_queue(), ^{
        NSString *result = @"ok";
        if ([op isEqual:@"install"]) {
            NSDictionary *labels = [NSJSONSerialization JSONObjectWithData:[argument dataUsingEncoding:NSUTF8StringEncoding] options:0 error:nil];
            installApplicationEntries(labels);
        } else if ([op isEqual:@"edit"]) result = editNativeResponder(argument) ? @"true" : @"false";
        else if ([op isEqual:@"state"]) {
            NSData *data = [NSJSONSerialization dataWithJSONObject:applicationMenuState(NSApp.mainMenu) options:0 error:nil];
            result = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
        } else if ([op isEqual:@"activate"]) {
            NSMenuItem *item = applicationMenuItem(argument);
            result = item && item.enabled && item.action && [NSApp sendAction:item.action to:item.target from:item] ? @"true" : @"false";
        }
        emit(0, @"service", result, token, 0);
    });
}
JNIEXPORT void JNICALL JNI(create)(JNIEnv *env, jclass cls, jlong identifier, jlong handle) {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (!pages) pages = [NSMutableDictionary new];
        installKeys();
        WKWebViewConfiguration *config = [WKWebViewConfiguration new];
        if (@available(macOS 14.0, *)) {
            // Stable Flux-specific profile; avoid sharing the generic Java host's website storage.
            config.websiteDataStore = [WKWebsiteDataStore dataStoreForIdentifier:[[NSUUID alloc] initWithUUIDString:@"F10C578C-0615-4CE5-8D22-710A4F311001"]];
        } else config.websiteDataStore = WKWebsiteDataStore.defaultDataStore;
        config.applicationNameForUserAgent = @"Flux/1.0";
        // Keep audible autoplay user-initiated; muted video remains eligible for autoplay.
        config.mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeAudio;
        FluxPage *p = [[FluxPage alloc] initWithID:identifier configuration:config];
        pages[@(identifier)] = p;
        [p attach:(__bridge NSWindow *)(void *)handle];
    });
}
JNIEXPORT void JNICALL JNI(attach)(JNIEnv *env, jclass cls, jlong identifier, jlong handle) {
    dispatch_async(dispatch_get_main_queue(), ^{ [pages[@(identifier)] attach:(__bridge NSWindow *)(void *)handle]; });
}
JNIEXPORT void JNICALL JNI(frame)(JNIEnv *env, jclass cls, jlong identifier, jdouble x, jdouble y, jdouble w, jdouble h, jboolean visible) {
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)]; if (!p || p.disposed) return;
        NSView *container = p.owner.contentView;
        CGFloat top = container.isFlipped ? y : container.bounds.size.height - y - h;
        p.shown = visible;
        p.viewportFrame = NSMakeRect(x, top, MAX(0, w), MAX(0, h));
        p.viewport.frame = p.viewportFrame; p.viewport.hidden = !visible;
        p.web.frame = p.viewport.bounds; p.web.hidden = !visible || p.pdf != nil;
        p.pdf.frame = p.viewport.bounds; p.pdf.hidden = !visible;
        if (visible && p.pdf && p.positionPdf) {
            p.positionPdf = NO; [p.pdf layoutDocumentView];
            PDFPage *first = [p.pdf.document pageAtIndex:0];
            [p.pdf goToDestination:[[PDFDestination alloc] initWithPage:first atPoint:NSMakePoint(0, NSMaxY([first boundsForBox:kPDFDisplayBoxMediaBox]))]];
        }
        if (visible && p.focusWhenShown) { p.focusWhenShown = NO; [p.owner makeFirstResponder:p.pdf ?: p.web]; }
    });
}
JNIEXPORT void JNICALL JNI(command)(JNIEnv *env, jclass cls, jlong identifier, jstring name, jstring value) {
    NSString *op = string(env, name), *argument = string(env, value);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)]; if (!p || p.disposed) return;
        if ([op isEqual:@"pdfOpen"]) {
            if (p.inspectionEnabled) inspectorCall(fluxInspector(p.web),@"close");
            NSUInteger version = ++p.documentVersion;
            [p.web stopLoading];
            // File parsing runs outside the UI thread; only PDFView creation/attachment is on AppKit.
            dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED,0), ^{
                PDFDocument *doc = [[PDFDocument alloc] initWithURL:[NSURL fileURLWithPath:argument]];
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (p.disposed || version != p.documentVersion) return;
                    if (!doc) { emit(identifier,@"error",@"Could not open PDF",0,0); return; }
                    [p.pdf removeFromSuperview];
                    p.pdf = [[PDFView alloc] initWithFrame:p.viewport.bounds]; p.pdf.document = doc; p.pdf.delegate = p; p.pdf.autoScales = YES;
                    p.pdf.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;
                    p.pdf.displayMode = kPDFDisplaySinglePageContinuous; p.pdfPath = argument; p.positionPdf = YES;
                    p.web.hidden = YES; p.pdf.hidden = !p.shown;
                    [p.viewport addSubview:p.pdf positioned:NSWindowAbove relativeTo:nil];
                    [p.pdf layoutDocumentView]; [p.pdf goToFirstPage:nil];
                    if (p.shown) [p.owner makeFirstResponder:p.pdf];
                    emit(identifier,@"document",@"",1,0); [p publish]; emit(identifier,@"finish",@"",0,0);
                });
            });
        } else if ([op isEqual:@"pdfPrevious"]) [p.pdf goToPreviousPage:nil];
        else if ([op isEqual:@"pdfNext"]) [p.pdf goToNextPage:nil];
        else if ([op isEqual:@"pdfZoomIn"]) [p.pdf zoomIn:nil];
        else if ([op isEqual:@"pdfZoomOut"]) [p.pdf zoomOut:nil];
        else if ([op isEqual:@"download"]) {
            NSURL *url = [NSURL URLWithString:argument];
            if ([@[@"https",@"http"] containsObject:url.scheme.lowercaseString]) {
                [p.web startDownloadUsingRequest:[NSURLRequest requestWithURL:url] completionHandler:^(WKDownload *d) { trackDownload(d,p.owner); }];
            }
        } else if ([op isEqual:@"load"]) {
            p.documentVersion++;
            [p.pdf removeFromSuperview]; p.pdf.document = nil; p.pdf = nil; p.pdfPath = nil; p.web.hidden = !p.shown;
            emit(identifier,@"document",@"",0,0);
            NSURL *url = [NSURL URLWithString:argument];
            if (url) p.navigation = [p.web loadRequest:[NSURLRequest requestWithURL:url]];
            else emit(identifier, @"error", @"Invalid address", 0, 0);
        } else if ([op isEqual:@"testForeground"]) {
            [NSApp activateIgnoringOtherApps:YES];
            [p.owner makeKeyAndOrderFront:nil]; [p.owner makeFirstResponder:p.web];
        } else if ([op isEqual:@"testKey"]) {
            [NSApp activateIgnoringOtherApps:YES];
            [p.owner makeKeyAndOrderFront:nil]; [p.owner makeFirstResponder:p.web];
            BOOL inspectorShortcut = [argument isEqual:@"developerTools"];
            NSString *characters = inspectorShortcut ? @"i" : argument;
            NSEvent *key = [NSEvent keyEventWithType:NSEventTypeKeyDown location:NSZeroPoint
                modifierFlags:NSEventModifierFlagCommand | (inspectorShortcut ? NSEventModifierFlagOption : 0) timestamp:NSProcessInfo.processInfo.systemUptime
                windowNumber:p.owner.windowNumber context:nil characters:characters
                charactersIgnoringModifiers:characters isARepeat:NO keyCode:37];
            [NSApp postEvent:key atStart:NO];
        } else if ([op isEqual:@"back"]) p.navigation = [p.web goBack];
        else if ([op isEqual:@"forward"]) p.navigation = [p.web goForward];
        else if ([op isEqual:@"reload"]) { if (p.pdf) { [p publish]; emit(identifier,@"finish",@"",0,0); } else p.navigation = [p.web reload]; }
        else if ([op isEqual:@"stop"]) { p.documentVersion++; p.stoppedNavigation = p.navigation; [p.web stopLoading]; p.navigation = nil; }
        else if ([op isEqual:@"zoom"]) p.web.pageZoom = argument.doubleValue;
        else if ([op isEqual:@"focus"]) { NSView *v = p.pdf ?: p.web; p.focusWhenShown = v.hidden; if (!v.hidden) [p.owner makeFirstResponder:v]; }
        else if ([op isEqual:@"blur"]) {
            p.focusWhenShown = NO;
            if ([p.owner.firstResponder isKindOfClass:NSView.class] && ([(NSView *)p.owner.firstResponder isDescendantOf:p.web] || (p.pdf && [(NSView *)p.owner.firstResponder isDescendantOf:p.pdf])))
                [p.owner makeFirstResponder:p.glass];
        } else if ([op isEqual:@"hide"]) {
            [p.web clearContext]; p.shown = NO; p.viewport.hidden = YES; p.web.hidden = YES; p.pdf.hidden = YES; p.focusWhenShown = NO;
            if ([p.owner.firstResponder isKindOfClass:NSView.class] && ([(NSView *)p.owner.firstResponder isDescendantOf:p.web] || (p.pdf && [(NSView *)p.owner.firstResponder isDescendantOf:p.pdf])))
                [p.owner makeFirstResponder:p.glass];
        }
    });
}
JNIEXPORT void JNICALL JNI(evaluate)(JNIEnv *env, jclass cls, jlong identifier, jlong token, jstring source) {
    NSString *script = string(env, source);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)];
        if (!p || p.disposed) { emit(identifier, @"scriptError", @"Tab closed", token, 0); return; }
        [p.web evaluateJavaScript:script completionHandler:^(id result, NSError *error) {
            if (p.disposed) return;
            NSString *value;
            if ([result isKindOfClass:NSString.class]) value = result;
            else if ([result isKindOfClass:NSNumber.class]) {
                value = CFGetTypeID((__bridge CFTypeRef)result) == CFBooleanGetTypeID() ? ([result boolValue] ? @"true" : @"false") : [result stringValue];
            } else value = result ? [result description] : @"undefined";
            emit(identifier, error ? @"scriptError" : @"script", error ? error.localizedDescription : value, token, 0);
        }];
    });
}
JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *env, jclass cls, jlong identifier) {
    dispatch_async(dispatch_get_main_queue(), ^{
        [pages[@(identifier)] dispose]; [pages removeObjectForKey:@(identifier)];
        if (pages.count == 0 && keyMonitor) { [NSEvent removeMonitor:keyMonitor]; keyMonitor = nil; }
    });
}

// Test input is scoped to Flux's own native viewport and menu. Java checks flux.testInput.
static NSArray *fluxMenuState(NSMenu *menu) {
    NSMutableArray *items = [NSMutableArray new];
    for (NSMenuItem *item in menu.itemArray) {
        [items addObject:@{@"id":item.identifier ?: @"", @"title":item.title, @"enabled":@(item.enabled), @"children":fluxMenuState(item.submenu)}];
    }
    return items;
}
static NSMenuItem *fluxFindMenuItem(NSMenu *menu, NSString *identifier, NSString *language) {
    for (NSMenuItem *item in menu.itemArray) {
        if ([item.identifier isEqual:identifier] && !item.submenu
            && (!language || [item.representedObject[@"language"] isEqual:language])) return item;
        NSMenuItem *child = fluxFindMenuItem(item.submenu, identifier, language); if (child) return child;
    }
    return nil;
}
JNIEXPORT void JNICALL JNI(contextMenuTest)(JNIEnv *env, jclass cls, jlong identifier, jlong token, jstring operation, jstring value) {
    NSString *op = string(env, operation), *argument = string(env, value);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)];
        if (!p || p.disposed) { emit(identifier,@"scriptError",@"Tab closed",token,0); return; }
        if ([op isEqual:@"show"]) {
            NSArray *point = [argument componentsSeparatedByString:@","];
            if (point.count != 2 || p.web.hidden) { emit(identifier,@"scriptError",@"Visible webpage required",token,0); return; }
            CGFloat x = [point[0] doubleValue], y = [point[1] doubleValue];
            NSPoint location = [p.web convertPoint:NSMakePoint(x, p.web.flipped ? y : p.web.bounds.size.height-y) toView:nil];
            [NSApp activateIgnoringOtherApps:YES]; [p.owner makeKeyAndOrderFront:nil];
            for (NSNumber *type in @[@(NSEventTypeRightMouseDown), @(NSEventTypeRightMouseUp)]) {
                NSEvent *event = [NSEvent mouseEventWithType:type.unsignedIntegerValue location:location modifierFlags:0 timestamp:NSProcessInfo.processInfo.systemUptime windowNumber:p.owner.windowNumber context:nil eventNumber:0 clickCount:1 pressure:1];
                [NSApp postEvent:event atStart:NO];
            }
            emit(identifier,@"script",@"posted",token,0);
        } else if ([op isEqual:@"inspectorState"]) {
            WKWebView *frontend = inspectorFrontend(fluxInspector(p.web));
            NSWindow *window = frontend.window;
            NSView *chromeHit = [p.owner.contentView hitTest:NSMakePoint(20, p.owner.contentView.bounds.size.height-25)];
            NSDictionary *state = @{@"detached":(window && window != p.owner && window.visible ? @YES : @NO),
                @"actualFrame":NSStringFromRect([p.web convertRect:p.web.bounds toView:p.owner.contentView]), @"expectedFrame":NSStringFromRect(p.viewportFrame),
                @"window":@(window.windowNumber), @"viewport":(NSEqualRects(p.viewport.frame,p.viewportFrame) && NSEqualRects(p.web.frame,p.viewport.bounds) ? @YES : @NO), @"pageVisible":(!p.web.hidden && !p.viewport.hidden ? @YES : @NO),
                @"chromeClear":([chromeHit isDescendantOf:p.viewport] ? @NO : @YES)};
            NSData *data = [NSJSONSerialization dataWithJSONObject:state options:0 error:nil];
            emit(identifier,@"script",[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding],token,0);
        } else if ([op isEqual:@"dockInspector"]) {
            inspectorCall(fluxInspector(p.web),@"attach");
            if ([argument isEqual:@"close"]) inspectorCall(fluxInspector(p.web),@"close");
            emit(identifier,@"script",@"attached",token,0);
        } else if ([op isEqual:@"closeInspectorWindow"]) {
            NSWindow *window = inspectorFrontend(fluxInspector(p.web)).window;
            if (window && window != p.owner) [window performClose:nil];
            emit(identifier,@"script",@"closed",token,0);
        } else if ([op isEqual:@"state"]) {
            NSData *data = [NSJSONSerialization dataWithJSONObject:fluxMenuState(p.web.fluxMenu) options:0 error:nil];
            emit(identifier,@"script",[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding],token,0);
        } else if ([op isEqual:@"dismiss"]) {
            [p.web clearContext]; emit(identifier,@"script",@"closed",token,0);
        } else if ([op isEqual:@"activate"]) {
            NSArray *parts = [argument componentsSeparatedByString:@":"];
            NSMenuItem *item = fluxFindMenuItem(p.web.fluxMenu, parts.firstObject, parts.count > 1 ? parts[1] : nil);
            if (!item || ![p.web validateMenuItem:item]) { emit(identifier,@"scriptError",@"Menu action unavailable",token,0); return; }
            [NSApp sendAction:item.action to:item.target from:item]; [p.web clearContext]; emit(identifier,@"script",@"activated",token,0);
        } else emit(identifier,@"scriptError",@"Unknown menu test action",token,0);
    });
}

JNIEXPORT void JNICALL JNI(snapshot)(JNIEnv *env, jclass cls, jlong identifier, jlong token, jstring destination) {
    NSString *path = string(env, destination);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)];
        if (!p || p.disposed) { emit(identifier, @"scriptError", @"Tab closed", token, 0); return; }
        if (p.pdf) {
            // PDFKit tiles are compositor-backed and cacheDisplay omits their content.
            // Export the current PDF page through PDFKit's supported rasterization API.
            CGFloat scale = p.owner.backingScaleFactor;
            NSImage *pageImage = [p.pdf.currentPage thumbnailOfSize:NSMakeSize(p.pdf.bounds.size.width*scale,p.pdf.bounds.size.height*scale) forBox:kPDFDisplayBoxMediaBox];
            NSBitmapImageRep *bitmap = pageImage ? [NSBitmapImageRep imageRepWithData:pageImage.TIFFRepresentation] : nil;
            BOOL saved = [[bitmap representationUsingType:NSBitmapImageFileTypePNG properties:@{}] writeToFile:path atomically:YES];
            emit(identifier,saved ? @"script" : @"scriptError",saved ? path : @"Snapshot unavailable",token,0); return;
        }
        [p.web takeSnapshotWithConfiguration:nil completionHandler:^(NSImage *image, NSError *error) {
            if (p.disposed) return;
            NSBitmapImageRep *bitmap = image ? [NSBitmapImageRep imageRepWithData:image.TIFFRepresentation] : nil;
            NSData *png = [bitmap representationUsingType:NSBitmapImageFileTypePNG properties:@{}];
            NSError *writeError = nil;
            BOOL saved = png && [png writeToFile:path options:NSDataWritingAtomic error:&writeError];
            emit(identifier, saved ? @"script" : @"scriptError", saved ? path : (error.localizedDescription ?: writeError.localizedDescription ?: @"Snapshot unavailable"), token, 0);
        }];
    });
}

JNIEXPORT void JNICALL JNI(configureServices)(JNIEnv *env, jclass cls, jlong token, jstring rules, jboolean https, jboolean phishing) {
    NSString *source = string(env,rules);
    dispatch_async(dispatch_get_main_queue(), ^{
        secureHosts = https; phishingWarnings = phishing;
        NSUInteger version = ++rulesVersion; rulesLoading = YES;
        void (^apply)(WKContentRuleList *,NSError *) = ^(WKContentRuleList *list,NSError *error) {
            if (version != rulesVersion) { emit(0,@"service",@"Superseded",token,0); return; }
            if (!error) {
                blockingRules = list;
                for (FluxPage *p in pages.allValues) {
                    [p.web.configuration.userContentController removeAllContentRuleLists];
                    if (list) [p.web.configuration.userContentController addContentRuleList:list];
                }
            }
            rulesLoading = NO;
            NSArray *waiting = pendingLoads.copy; [pendingLoads removeAllObjects];
            for (void (^resume)(void) in waiting) resume();
            emit(0,error ? @"serviceError" : @"service",error ? @"WebKit could not compile the blocking rules" : @"Applied",token,0);
        };
        if (source.length == 0) apply(nil,nil);
        else [[WKContentRuleListStore defaultStore] compileContentRuleListForIdentifier:@"FluxDomains" encodedContentRuleList:source completionHandler:apply];
    });
}
JNIEXPORT void JNICALL JNI(serviceAction)(JNIEnv *env, jclass cls, jlong identifier, jstring operation) {
    NSString *op = string(env,operation);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxDownload *d = downloads[@(identifier)];
        if ([op isEqual:@"cancel"]) { [d cancel]; }
        else if ([op isEqual:@"reveal"] && [d.status isEqual:@"Complete"]) [[NSWorkspace sharedWorkspace] activateFileViewerSelectingURLs:@[[NSURL fileURLWithPath:d.path]]];
        else if ([op isEqual:@"shutdown"]) {
            for (FluxDownload *item in downloads.allValues) { [item cancel]; item.download.delegate = nil; if (item.temporaryPath) [NSFileManager.defaultManager removeItemAtPath:item.temporaryPath error:nil]; }
            [downloads removeAllObjects];
        }
    });
}
JNIEXPORT void JNICALL JNI(credential)(JNIEnv *env, jclass cls, jlong identifier, jlong token, jstring operation, jstring origin, jstring username, jcharArray password) {
    NSString *op = string(env,operation), *site = string(env,origin), *user = string(env,username);
    jsize count = (*env)->GetArrayLength(env,password);
    jchar *chars = (*env)->GetCharArrayElements(env,password,NULL);
    NSString *secret = [[NSString alloc] initWithCharacters:(const unichar *)chars length:count];
    (*env)->ReleaseCharArrayElements(env,password,chars,JNI_ABORT);
    static dispatch_queue_t keychainQueue;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ keychainQueue = dispatch_queue_create("com.flux.browser.keychain",DISPATCH_QUEUE_SERIAL); });
    dispatch_async(keychainQueue, ^{
        @autoreleasepool {
            NSDictionary *query = @{(__bridge id)kSecClass:(__bridge id)kSecClassGenericPassword,
                (__bridge id)kSecAttrService:@"com.flux.browser.passwords",
                (__bridge id)kSecAttrAccount:[NSString stringWithFormat:@"%@\n%@",site,user]};
            OSStatus status = errSecParam; NSString *result = @"";
            if ([op isEqual:@"save"]) {
                NSData *data = [secret dataUsingEncoding:NSUTF8StringEncoding];
                status = SecItemUpdate((__bridge CFDictionaryRef)query,(__bridge CFDictionaryRef)@{(__bridge id)kSecValueData:data});
                if (status == errSecItemNotFound) {
                    NSMutableDictionary *item = query.mutableCopy;
                    item[(__bridge id)kSecValueData] = data;
                    item[(__bridge id)kSecAttrLabel] = [NSString stringWithFormat:@"Flux · %@ · %@",site,user];
                    item[(__bridge id)kSecAttrAccessible] = (__bridge id)kSecAttrAccessibleWhenUnlockedThisDeviceOnly;
                    status = SecItemAdd((__bridge CFDictionaryRef)item,NULL);
                }
            } else if ([op isEqual:@"get"]) {
                NSMutableDictionary *request = query.mutableCopy;
                request[(__bridge id)kSecReturnData] = @YES; request[(__bridge id)kSecMatchLimit] = (__bridge id)kSecMatchLimitOne;
                CFTypeRef data = NULL; status = SecItemCopyMatching((__bridge CFDictionaryRef)request,&data);
                if (status == errSecSuccess) result = [[NSString alloc] initWithData:CFBridgingRelease(data) encoding:NSUTF8StringEncoding] ?: @"";
            } else if ([op isEqual:@"delete"]) status = SecItemDelete((__bridge CFDictionaryRef)query);
            NSString *message = status == errSecItemNotFound ? @"No Flux login found for this origin and username." : @"Keychain action failed or was cancelled. Unlock Keychain and retry.";
            dispatch_async(dispatch_get_main_queue(), ^{ emit(identifier,status == errSecSuccess ? @"script" : @"scriptError",status == errSecSuccess ? result : message,token,0); });
        }
    });
}

JNIEXPORT void JNICALL JNI(testDownloadPath)(JNIEnv *env, jclass cls, jstring path) {
    NSString *destination = string(env,path);
    dispatch_async(dispatch_get_main_queue(), ^{ testDestination = destination; });
}

JNIEXPORT void JNICALL JNI(inspect)(JNIEnv *env, jclass cls, jlong identifier, jlong token, jstring operation) {
    NSString *op = string(env,operation);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)];
        if (!p || p.disposed || p.pdf) { emit(identifier,@"scriptError",@"No inspectable web page",token,0); return; }
        @try {
            // Querying status or closing must not open the inspector frontend.
            id inspector = p.inspectionEnabled ? fluxInspector(p.web) : nil;
            if ([op isEqual:@"status"]) {
                NSString *state = !p.inspectionEnabled ? @"disabled" : inspectorFlag(inspector,@"isVisible") ? @"visible" : @"closed";
                emit(identifier,@"script",state,token,0); return;
            }
            if ([op isEqual:@"close"] || ([op isEqual:@"toggle"] && inspectorFlag(inspector,@"isVisible"))) {
                inspectorCall(inspector,@"close");
                p.web.frame = p.viewport.bounds;
                emit(identifier,@"script",@"Developer Tools closed",token,0); return;
            }
            BOOL local = enableInspection(p.web); p.inspectionEnabled = YES;
            inspector = fluxInspector(p.web);
            if (local && inspectorCall(inspector,[op isEqual:@"console"] ? @"showConsole" : @"show")) {
                // A separate native window leaves the JavaFX viewport's geometry under Flux ownership.
                [p detachInspector];
                emit(identifier,@"script",@"Developer Tools opened for this tab",token,0);
            } else {
                emit(identifier,@"script",@"Inspection enabled. In Safari, use Develop → this Mac → Flux/Java to inspect this page.",token,0);
            }
        } @catch (NSException *exception) {
            emit(identifier,@"scriptError",@"This macOS WebKit version could not open its local inspector",token,0);
        }
    });
}

JNIEXPORT void JNICALL JNI(inspectFrontend)(JNIEnv *env, jclass cls, jlong identifier, jlong token, jstring source) {
    NSString *script = string(env,source);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)];
        id inspector = p.inspectionEnabled ? fluxInspector(p.web) : nil;
        WKWebView *frontend = inspectorFrontend(inspector);
        if (!p || p.disposed || !frontend || frontend == p.web) { emit(identifier,@"scriptError",@"Inspector frontend unavailable",token,0); return; }
        [frontend evaluateJavaScript:script completionHandler:^(id result,NSError *error) {
            if (p.disposed) return;
            emit(identifier,error ? @"scriptError" : @"script",error ? @"Inspector frontend evaluation failed" : [result description],token,0);
        }];
    });
}
