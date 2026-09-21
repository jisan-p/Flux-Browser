// WebKit exposes remote inspection publicly, but its in-app inspector controls are SPI.
// Keep dynamic selectors in one place and check availability before every invocation.
#import <objc/message.h>
static id fluxInspector(WKWebView *web) {
    SEL selector = NSSelectorFromString(@"_inspector");
    return [web respondsToSelector:selector] ? ((id (*)(id,SEL))objc_msgSend)(web,selector) : nil;
}
static BOOL inspectorCall(id inspector, NSString *name) {
    SEL selector = NSSelectorFromString(name);
    if (![inspector respondsToSelector:selector]) return NO;
    ((void (*)(id,SEL))objc_msgSend)(inspector,selector); return YES;
}
static BOOL inspectorFlag(id inspector, NSString *name) {
    SEL selector = NSSelectorFromString(name);
    return [inspector respondsToSelector:selector] && ((BOOL (*)(id,SEL))objc_msgSend)(inspector,selector);
}
static BOOL enableInspection(WKWebView *web) {
    if (@available(macOS 13.3,*)) web.inspectable = YES;
    WKPreferences *preferences = web.configuration.preferences;
    SEL setter = NSSelectorFromString(@"_setDeveloperExtrasEnabled:");
    if (![preferences respondsToSelector:setter]) return NO;
    ((void (*)(id,SEL,BOOL))objc_msgSend)(preferences,setter,YES); return YES;
}

static WKWebView *inspectorFrontend(id inspector) {
    SEL getter = NSSelectorFromString(@"inspectorWebView");
    return [inspector respondsToSelector:getter] ? ((id (*)(id,SEL))objc_msgSend)(inspector,getter) : nil;
}
static void inspectorDelegate(id inspector, id delegate) {
    SEL setter = NSSelectorFromString(@"setDelegate:");
    if ([inspector respondsToSelector:setter]) ((void (*)(id,SEL,id))objc_msgSend)(inspector,setter,delegate);
}

// Preserve WebKit's hit-tested Inspect Element action, then apply Flux's window policy.
@interface FluxInspectAction : NSObject
@property(nonatomic, strong) NSMenuItem *original;
@property(nonatomic, copy) void (^didInspect)(void);
- (void)inspect:(NSMenuItem *)sender;
@end
@implementation FluxInspectAction
- (void)inspect:(NSMenuItem *)sender {
    [NSApp sendAction:self.original.action to:self.original.target from:self.original];
    if (self.didInspect) self.didInspect();
}
@end
static void prepareInspectionMenu(NSMenu *menu, void (^didInspect)(void)) {
    for (NSMenuItem *item in menu.itemArray) {
        if ([item.identifier isEqual:@"WKMenuItemIdentifierInspectElement"] && ![item.target isKindOfClass:FluxInspectAction.class]) {
            FluxInspectAction *action = [FluxInspectAction new]; action.original = [item copy]; action.didInspect = didInspect;
            item.target = action; item.action = @selector(inspect:); item.representedObject = action;
        }
    }
}
