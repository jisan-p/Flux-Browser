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

@interface FluxPage : NSObject <WKNavigationDelegate, WKUIDelegate>
@property(nonatomic) long long identifier;
@property(nonatomic, strong) WKWebView *web;
@property(nonatomic, weak) NSWindow *owner;
@property(nonatomic, weak) NSView *glass;
@property(nonatomic) BOOL disposed;
@property(nonatomic) BOOL stateQueued;
@property(nonatomic) BOOL focusWhenShown;
@property(nonatomic, strong) WKNavigation *navigation;
@property(nonatomic, strong) WKNavigation *stoppedNavigation;
- (instancetype)initWithID:(long long)identifier configuration:(WKWebViewConfiguration *)configuration;
- (void)attach:(NSWindow *)window;
- (void)publish;
- (void)dispose;
@end

@implementation FluxPage
- (instancetype)initWithID:(long long)identifier configuration:(WKWebViewConfiguration *)configuration {
    self = [super init];
    if (self) {
        _identifier = identifier;
        _web = [[WKWebView alloc] initWithFrame:NSMakeRect(0, 0, 800, 600) configuration:configuration];
        _web.navigationDelegate = self; _web.UIDelegate = self;
        _web.hidden = YES;
        _web.allowsBackForwardNavigationGestures = YES;
        for (NSString *key in @[@"URL", @"title", @"estimatedProgress", @"canGoBack", @"canGoForward"])
            [_web addObserver:self forKeyPath:key options:0 context:NULL];
    }
    return self;
}
- (void)attach:(NSWindow *)window {
    self.owner = window; self.glass = window.contentView;
    // The view is a sibling above Glass, inside the SAME window: no floating window, no pixel copy.
    [window.contentView addSubview:self.web positioned:NSWindowAbove relativeTo:nil];
    [self publish];
}
- (void)publish {
    if (self.disposed) return;
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
    decision(allowed ? WKNavigationActionPolicyAllow : WKNavigationActionPolicyCancel);
}
- (WKWebView *)webView:(WKWebView *)web createWebViewWithConfiguration:(WKWebViewConfiguration *)configuration
        forNavigationAction:(WKNavigationAction *)action windowFeatures:(WKWindowFeatures *)features {
    // Returning the configured view preserves POST popups and window.opener; do not re-load the URL.
    FluxPage *child = [[FluxPage alloc] initWithID:popupID-- configuration:configuration];
    pages[@(child.identifier)] = child;
    emit(self.identifier, @"popup", @"", child.identifier, 0);
    return child.web;
}
- (void)webViewDidClose:(WKWebView *)web { emit(self.identifier, @"close", @"", 0, 0); }
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
    self.disposed = YES;
    for (NSString *key in @[@"URL", @"title", @"estimatedProgress", @"canGoBack", @"canGoForward"])
        [self.web removeObserver:self forKeyPath:key];
    self.web.navigationDelegate = nil; self.web.UIDelegate = nil;
    [self.web stopLoading]; [self.web removeFromSuperview]; self.web = nil;
}
@end

static void installKeys(void) {
    if (keyMonitor) return;
    keyMonitor = [NSEvent addLocalMonitorForEventsMatchingMask:NSEventMaskKeyDown handler:^NSEvent *(NSEvent *event) {
        for (FluxPage *p in pages.allValues) {
            NSResponder *responder = p.owner.firstResponder;
            if (p.web.hidden || event.window != p.owner || ![responder isKindOfClass:NSView.class]
                || ![(NSView *)responder isDescendantOf:p.web]) continue;
            BOOL cmd = (event.modifierFlags & NSEventModifierFlagCommand) != 0;
            BOOL shift = (event.modifierFlags & NSEventModifierFlagShift) != 0;
            BOOL control = (event.modifierFlags & NSEventModifierFlagControl) != 0;
            BOOL alt = (event.modifierFlags & NSEventModifierFlagOption) != 0;
            NSString *key = event.charactersIgnoringModifiers.lowercaseString;
            NSString *action = nil;
            if (cmd && [@[@"l", @"t", @"w", @"r", @"d", @"y", @"1", @"2", @"3", @"4", @"5", @"6", @"7", @"8", @"9"] containsObject:key]) action = key;
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
        p.web.frame = NSMakeRect(x, top, MAX(0, w), MAX(0, h)); p.web.hidden = !visible;
        if (visible && p.focusWhenShown) { p.focusWhenShown = NO; [p.owner makeFirstResponder:p.web]; }
    });
}
JNIEXPORT void JNICALL JNI(command)(JNIEnv *env, jclass cls, jlong identifier, jstring name, jstring value) {
    NSString *op = string(env, name), *argument = string(env, value);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)]; if (!p || p.disposed) return;
        if ([op isEqual:@"load"]) {
            NSURL *url = [NSURL URLWithString:argument];
            if (url) p.navigation = [p.web loadRequest:[NSURLRequest requestWithURL:url]];
            else emit(identifier, @"error", @"Invalid address", 0, 0);
        } else if ([op isEqual:@"testKey"]) {
            [p.owner makeKeyAndOrderFront:nil]; [p.owner makeFirstResponder:p.web];
            NSEvent *key = [NSEvent keyEventWithType:NSEventTypeKeyDown location:NSZeroPoint
                modifierFlags:NSEventModifierFlagCommand timestamp:NSProcessInfo.processInfo.systemUptime
                windowNumber:p.owner.windowNumber context:nil characters:argument
                charactersIgnoringModifiers:argument isARepeat:NO keyCode:37];
            [NSApp postEvent:key atStart:NO];
        } else if ([op isEqual:@"back"]) p.navigation = [p.web goBack];
        else if ([op isEqual:@"forward"]) p.navigation = [p.web goForward];
        else if ([op isEqual:@"reload"]) p.navigation = [p.web reload];
        else if ([op isEqual:@"stop"]) { p.stoppedNavigation = p.navigation; [p.web stopLoading]; p.navigation = nil; }
        else if ([op isEqual:@"zoom"]) p.web.pageZoom = argument.doubleValue;
        else if ([op isEqual:@"focus"]) { p.focusWhenShown = p.web.hidden; if (!p.web.hidden) [p.owner makeFirstResponder:p.web]; }
        else if ([op isEqual:@"blur"]) {
            p.focusWhenShown = NO;
            if ([p.owner.firstResponder isKindOfClass:NSView.class] && [(NSView *)p.owner.firstResponder isDescendantOf:p.web])
                [p.owner makeFirstResponder:p.glass];
        } else if ([op isEqual:@"hide"]) {
            p.web.hidden = YES; p.focusWhenShown = NO;
            if ([p.owner.firstResponder isKindOfClass:NSView.class] && [(NSView *)p.owner.firstResponder isDescendantOf:p.web])
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

JNIEXPORT void JNICALL JNI(snapshot)(JNIEnv *env, jclass cls, jlong identifier, jlong token, jstring destination) {
    NSString *path = string(env, destination);
    dispatch_async(dispatch_get_main_queue(), ^{
        FluxPage *p = pages[@(identifier)];
        if (!p || p.disposed) { emit(identifier, @"scriptError", @"Tab closed", token, 0); return; }
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
