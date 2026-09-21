// WebKit's attached inspector resizes the inspected view to its superview's bounds.
// Give it a page-sized parent so it can never claim the JavaFX window chrome.
@interface FluxViewport : NSView
@property(nonatomic, copy) void (^inspectorAttached)(void);
@property(nonatomic, weak) WKWebView *page;
@end
@implementation FluxViewport
- (void)didAddSubview:(NSView *)subview {
    [super didAddSubview:subview];
    if (subview != self.page && [subview isKindOfClass:WKWebView.class] && self.inspectorAttached)
        self.inspectorAttached();
}
@end
