// Uses AppKit's public contextual-menu hook; WebKit keeps ownership of its built-in items.
@interface FluxContextSelection : NSObject <WKScriptMessageHandler>
@property(nonatomic, weak) id receiver;
@end
@interface FluxContextWebView : WKWebView <NSMenuItemValidation>
@property(nonatomic) long long fluxIdentifier;
@property(nonatomic, copy) void (^didInspect)(void);
@property(nonatomic, copy) NSString *selectedText;
@property(nonatomic, weak) NSMenu *fluxMenu;
@property(nonatomic, weak) NSMenuItem *searchItem;
- (void)receiveSelection:(WKScriptMessage *)message;
- (void)clearContext;
- (void)fluxPageAction:(NSMenuItem *)item;
@end
@implementation FluxContextSelection
- (void)userContentController:(WKUserContentController *)controller didReceiveScriptMessage:(WKScriptMessage *)message {
    [self.receiver receiveSelection:message];
}
@end
@implementation FluxContextWebView
- (instancetype)initWithFrame:(NSRect)frame configuration:(WKWebViewConfiguration *)configuration {
    self = [super initWithFrame:frame configuration:configuration];
    if (self) {
        // Isolated world prevents page scripts from calling our handler. Capture only a real
        // context-menu gesture, including selections in child frames, never password inputs.
        WKContentWorld *world = [WKContentWorld worldWithName:@"FluxContextMenu"];
        FluxContextSelection *handler = [FluxContextSelection new]; handler.receiver = self;
        [self.configuration.userContentController removeScriptMessageHandlerForName:@"fluxSelection" contentWorld:world];
        [self.configuration.userContentController addScriptMessageHandler:handler contentWorld:world name:@"fluxSelection"];
        NSString *script = @"if(!globalThis.fluxContextInstalled){globalThis.fluxContextInstalled=true;addEventListener('contextmenu',e=>{if(!e.isTrusted)return;let t=e.composedPath()[0],s='';if(t instanceof HTMLInputElement){if(['text','search','url','tel','email'].includes(t.type))s=t.value.slice(t.selectionStart||0,t.selectionEnd||0);}else if(t instanceof HTMLTextAreaElement){s=t.value.slice(t.selectionStart||0,t.selectionEnd||0);}else{s=String(getSelection()||'');}webkit.messageHandlers.fluxSelection.postMessage(s.slice(0,2000));},true);}";
        [self.configuration.userContentController addUserScript:[[WKUserScript alloc] initWithSource:script injectionTime:WKUserScriptInjectionTimeAtDocumentStart forMainFrameOnly:NO inContentWorld:world]];
    }
    return self;
}
- (void)rightMouseDown:(NSEvent *)event { self.selectedText = nil; [super rightMouseDown:event]; }
- (void)receiveSelection:(WKScriptMessage *)message {
    if (message.webView != self || ![message.body isKindOfClass:NSString.class]) return;
    self.selectedText = [(NSString *)message.body substringToIndex:MIN((NSUInteger)2000, [(NSString *)message.body length])];
    if (self.searchItem) {
        NSMutableDictionary *payload = [self.searchItem.representedObject mutableCopy]; payload[@"text"] = self.selectedText;
        self.searchItem.representedObject = payload; self.searchItem.enabled = self.selectedText.length > 0;
    }
}
- (NSMenuItem *)item:(NSString *)title action:(NSString *)action language:(NSString *)language {
    NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:title action:@selector(fluxPageAction:) keyEquivalent:@""];
    item.target = self; item.identifier = [@"flux." stringByAppendingString:action];
    item.representedObject = @{@"action":action, @"url":self.URL.absoluteString ?: @"", @"text":self.selectedText ?: @"", @"language":language ?: @""};
    item.enabled = [self validateMenuItem:item];
    return item;
}
- (void)willOpenMenu:(NSMenu *)menu withEvent:(NSEvent *)event {
    [super willOpenMenu:menu withEvent:event];
    self.fluxMenu = menu;
    prepareInspectionMenu(menu, self.didInspect);
    if (![ @[@"http", @"https"] containsObject:self.URL.scheme.lowercaseString]) return;
    [menu addItem:NSMenuItem.separatorItem];
    NSMenuItem *search = [self item:@"Search Selection in New Tab" action:@"search" language:nil];
    [menu addItem:search]; self.searchItem = search;
    NSMenuItem *translate = [self item:@"Translate Page" action:@"translate" language:nil];
    NSMenu *languages = [[NSMenu alloc] initWithTitle:@"Translate Page"];
    [languages addItem:[self item:@"Translate to Preferred Language" action:@"translate" language:nil]];
    [languages addItem:NSMenuItem.separatorItem];
    NSArray *codes = @[@"en",@"bn",@"es",@"fr",@"de",@"ar",@"hi",@"ja",@"ko",@"zh-CN"];
    NSArray *names = @[@"English",@"Bangla",@"Spanish",@"French",@"German",@"Arabic",@"Hindi",@"Japanese",@"Korean",@"Chinese (Simplified)"];
    for (NSUInteger i=0; i<codes.count; i++) [languages addItem:[self item:names[i] action:@"translate" language:codes[i]]];
    translate.submenu = languages; [menu addItem:translate];
    [menu addItem:[self item:@"Reader Mode" action:@"reader" language:nil]];
    [menu addItem:[self item:@"Save Page As…" action:@"save" language:nil]];
}
- (BOOL)validateMenuItem:(NSMenuItem *)item {
    if (item.action == @selector(fluxPageAction:)) {
        NSDictionary *payload = item.representedObject;
        return !self.hidden && [payload[@"url"] isEqual:self.URL.absoluteString]
            && (![payload[@"action"] isEqual:@"search"] || [payload[@"text"] length] > 0);
    }
    return YES;
}
- (void)fluxPageAction:(NSMenuItem *)item {
    if (![self validateMenuItem:item]) return;
    NSData *data = [NSJSONSerialization dataWithJSONObject:item.representedObject options:0 error:nil];
    emit(self.fluxIdentifier, @"pageAction", [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding], 0, 0);
}
- (void)didCloseMenu:(NSMenu *)menu withEvent:(NSEvent *)event {
    [super didCloseMenu:menu withEvent:event];
    self.selectedText = nil; self.searchItem = nil;
}
- (void)clearContext { [self.fluxMenu cancelTracking]; self.fluxMenu = nil; self.selectedText = nil; self.searchItem = nil; }
@end
