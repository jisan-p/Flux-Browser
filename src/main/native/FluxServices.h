// Included by FluxWebKit.m; all registries and callbacks stay on AppKit.
#import <Security/Security.h>
#import <PDFKit/PDFKit.h>
static BOOL secureHosts = YES, phishingWarnings = YES;
static WKContentRuleList *blockingRules;
static BOOL rulesLoading;
static NSUInteger rulesVersion;
static NSMutableArray *pendingLoads;
static NSMutableDictionary<NSNumber *, id> *downloads;
static long long nextDownload;
static NSString *testDestination;

@interface FluxDownload : NSObject <WKDownloadDelegate>
@property(nonatomic) long long identifier;
@property(nonatomic, strong) WKDownload *download;
@property(nonatomic, weak) NSWindow *owner;
@property(nonatomic, copy) NSString *path, *name, *status, *temporaryPath;
@property(nonatomic, strong) NSSavePanel *panel;
@property(nonatomic) BOOL cancelled;
@property(nonatomic) NSTimeInterval lastProgress;
@property(nonatomic) BOOL observing;
- (void)publish;
- (void)stopObserving;
- (NSURL *)stageDestination:(NSURL *)destination;
- (void)cancel;
@end
@implementation FluxDownload
- (NSURL *)stageDestination:(NSURL *)destination {
    self.path = destination.path; self.name = destination.lastPathComponent;
    self.temporaryPath = [[destination URLByDeletingLastPathComponent] URLByAppendingPathComponent:[NSString stringWithFormat:@".flux-download-%@.part",NSUUID.UUID.UUIDString]].path;
    self.status = @"Downloading";
    self.observing = YES; [self.download.progress addObserver:self forKeyPath:@"completedUnitCount" options:0 context:NULL];
    [self publish]; return [NSURL fileURLWithPath:self.temporaryPath];
}
- (void)cancel {
    self.cancelled = YES; [self.panel cancel:nil]; self.panel = nil;
    [self stopObserving]; [self.download cancel:nil]; self.status = @"Cancelled"; [self publish];
}
- (void)publish {
    NSDictionary *value = @{@"id":@(self.identifier), @"name":self.name ?: @"Download", @"path":self.path ?: @"",
        @"status":self.status ?: @"Waiting", @"received":@(self.download.progress.completedUnitCount), @"total":@(self.download.progress.totalUnitCount)};
    NSData *data = [NSJSONSerialization dataWithJSONObject:value options:0 error:nil];
    emit(0,@"download",[[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding],0,0);
}
- (void)download:(WKDownload *)download decideDestinationUsingResponse:(NSURLResponse *)response suggestedFilename:(NSString *)filename completionHandler:(void (^)(NSURL *))done {
    self.name = filename.lastPathComponent; self.status = @"Choose destination"; [self publish];
    if (testDestination) {
        NSURL *destination = [NSURL fileURLWithPath:testDestination]; testDestination = nil;
        done([self stageDestination:destination]); return;
    }
    if (!self.owner) { done(nil); self.status = @"Cancelled"; [self publish]; return; }
    NSSavePanel *panel = [NSSavePanel savePanel]; self.panel = panel; panel.nameFieldStringValue = self.name; panel.canCreateDirectories = YES;
    [panel beginSheetModalForWindow:self.owner completionHandler:^(NSModalResponse result) {
        self.panel = nil;
        if (result != NSModalResponseOK || self.cancelled) { self.status = @"Cancelled"; [self publish]; done(nil); return; }
        done([self stageDestination:panel.URL]);
    }];
}
- (void)observeValueForKeyPath:(NSString *)key ofObject:(id)object change:(NSDictionary *)change context:(void *)context {
    // Progress KVO may arrive off-main. Throttle on AppKit, never flood the FX queue.
    dispatch_async(dispatch_get_main_queue(), ^{
        NSTimeInterval now = NSProcessInfo.processInfo.systemUptime;
        if (now - self.lastProgress < 0.25) return;
        self.lastProgress = now; [self publish];
    });
}
- (void)stopObserving { if (self.observing) { self.observing = NO; [self.download.progress removeObserver:self forKeyPath:@"completedUnitCount"]; } }
- (void)downloadDidFinish:(WKDownload *)download {
    [self stopObserving];
    NSURL *temporary = [NSURL fileURLWithPath:self.temporaryPath], *destination = [NSURL fileURLWithPath:self.path];
    NSFileManager *files = NSFileManager.defaultManager; NSError *error = nil;
    if ([files fileExistsAtPath:self.path]) [files replaceItemAtURL:destination withItemAtURL:temporary backupItemName:nil options:0 resultingItemURL:nil error:&error];
    else [files moveItemAtURL:temporary toURL:destination error:&error];
    self.status = error ? @"Failed to save" : @"Complete";
    if (error) [files removeItemAtURL:temporary error:nil];
    [self publish]; self.download = nil;
}
- (void)download:(WKDownload *)download didFailWithError:(NSError *)error resumeData:(NSData *)resumeData {
    [self stopObserving]; if (self.temporaryPath) [NSFileManager.defaultManager removeItemAtPath:self.temporaryPath error:nil]; self.status = error.code == NSURLErrorCancelled ? @"Cancelled" : @"Failed"; [self publish]; self.download = nil;
}
@end
static void trackDownload(WKDownload *download, NSWindow *owner) {
    if (!downloads) downloads = [NSMutableDictionary new];
    // Retain active downloads independently of their originating tabs; bound retained completed entries.
    if (downloads.count >= 200) {
        NSNumber *oldest = [[downloads.allKeys sortedArrayUsingSelector:@selector(compare:)] firstObject];
        FluxDownload *old = downloads[oldest];
        if (old.download) { [download cancel:nil]; return; }
        [downloads removeObjectForKey:oldest];
    }
    FluxDownload *item = [FluxDownload new]; item.identifier = ++nextDownload; item.owner = owner;
    item.download = download; downloads[@(item.identifier)] = item; download.delegate = item; [item publish];
}
