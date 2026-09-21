// JavaFX exports the FXML menus. AppKit owns the application menu and native responders.
@interface FluxApplicationMenuTarget : NSObject
- (void)invoke:(NSMenuItem *)sender;
@end
@implementation FluxApplicationMenuTarget
- (void)invoke:(NSMenuItem *)sender { emit(0, @"applicationMenu", sender.representedObject, 0, 0); }
@end
static FluxApplicationMenuTarget *applicationMenuTarget;

static void installApplicationEntries(NSDictionary *labels) {
    NSMenuItem *application = NSApp.mainMenu.itemArray.firstObject;
    NSMenu *menu = application.submenu;
    if (!menu) return;
    if (!applicationMenuTarget) applicationMenuTarget = [FluxApplicationMenuTarget new];
    application.title = labels[@"name"]; menu.title = labels[@"name"];
    for (NSMenuItem *item in menu.itemArray.copy)
        if ([item.identifier hasPrefix:@"flux.application."]) [menu removeItem:item];
    NSUInteger index = 0;
    for (NSString *action in @[@"about", @"settings"]) {
        NSMenuItem *item = [[NSMenuItem alloc] initWithTitle:labels[action] action:@selector(invoke:) keyEquivalent:[action isEqual:@"settings"] ? @"," : @""];
        item.identifier = [@"flux.application." stringByAppendingString:action];
        item.target = applicationMenuTarget; item.representedObject = action;
        item.keyEquivalentModifierMask = NSEventModifierFlagCommand;
        [menu insertItem:item atIndex:index++];
    }
    NSMenuItem *separator = NSMenuItem.separatorItem; separator.identifier = @"flux.application.separator";
    [menu insertItem:separator atIndex:index];
    // Retain the system Hide/Show All entries and replace Quit's action with Flux cleanup.
    for (NSMenuItem *item in menu.itemArray) {
        if ([item.keyEquivalent isEqual:@"q"]) { item.title = labels[@"quit"]; item.target = applicationMenuTarget; item.action = @selector(invoke:); item.representedObject = @"quit"; }
        if ([item.keyEquivalent isEqual:@"h"] && item.keyEquivalentModifierMask == NSEventModifierFlagCommand)
            item.title = [@"Hide " stringByAppendingString:labels[@"name"]];
    }
}
static BOOL editNativeResponder(NSString *operation) {
    NSDictionary *actions = @{@"undo":@"undo:", @"redo":@"redo:", @"cut":@"cut:", @"copy":@"copy:", @"paste":@"paste:", @"selectAll":@"selectAll:"};
    NSString *selector = actions[operation];
    if (!selector) return NO;
    NSResponder *responder = NSApp.keyWindow.firstResponder;
    if (![responder isKindOfClass:NSView.class]) return NO;
    for (NSView *view = (NSView *)responder; view; view = view.superview) {
        if ([view isKindOfClass:WKWebView.class] || [view isKindOfClass:PDFView.class]) {
            [NSApp sendAction:NSSelectorFromString(selector) to:nil from:nil];
            // An unavailable action in WebKit must never edit a stale JavaFX focus owner.
            return YES;
        }
    }
    return NO;
}
static NSArray *applicationMenuState(NSMenu *menu) {
    NSMutableArray *items = [NSMutableArray new];
    for (NSMenuItem *item in menu.itemArray) {
        if (item.separatorItem) continue;
        [items addObject:@{@"title":item.title, @"enabled":(item.enabled ? @YES : @NO), @"key":item.keyEquivalent,
            @"items":item.submenu ? applicationMenuState(item.submenu) : @[]}];
    }
    return items;
}
static NSMenuItem *applicationMenuItem(NSString *path) {
    NSMenu *menu = NSApp.mainMenu; NSMenuItem *item = nil;
    for (NSString *title in [path componentsSeparatedByString:@"/"]) {
        item = [menu itemWithTitle:title]; if (!item) return nil;
        menu = item.submenu;
    }
    return item;
}
