#import "KuiklyRenderViewController.h"
#import "KuiklyRenderViewControllerBaseDelegator.h"
#import "KuiklyRenderContextProtocol.h"

@interface KuiklyRenderViewController () <KuiklyRenderViewControllerBaseDelegatorDelegate>
@property (nonatomic, strong) KuiklyRenderViewControllerBaseDelegator *delegator;
@end

@implementation KuiklyRenderViewController {
    NSString *_pageName;
    NSDictionary *_pageData;
}

- (instancetype)initWithPageName:(NSString *)pageName pageData:(NSDictionary *)pageData {
    if (self = [super init]) {
        _pageName = pageName;
        _pageData = pageData ?: @{};
        _delegator = [[KuiklyRenderViewControllerBaseDelegator alloc] initWithPageName:pageName pageData:_pageData];
        _delegator.delegate = self;
    }
    return self;
}

- (instancetype)init {
    return [self initWithPageName:@"HelloWorld" pageData:@{}];
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor whiteColor];
    [_delegator viewDidLoadWithView:self.view];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [_delegator viewDidLayoutSubviews];
}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [_delegator viewWillAppear];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [_delegator viewDidAppear];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [_delegator viewWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [_delegator viewDidDisappear];
}

#pragma mark - KuiklyRenderViewControllerBaseDelegatorDelegate

- (UIView *)createLoadingView {
    UIView *loadingView = [[UIView alloc] init];
    loadingView.backgroundColor = [UIColor whiteColor];
    return loadingView;
}

- (UIView *)createErrorView {
    UIView *errorView = [[UIView alloc] init];
    errorView.backgroundColor = [UIColor whiteColor];
    return errorView;
}

- (void)fetchContextCodeWithPageName:(NSString *)pageName resultCallback:(KuiklyContextCodeCallback)callback {
    if (callback) {
        callback(@"shared", nil);
    }
}

- (NSDictionary<NSString *, NSObject *> *)contextPageData {
    NSMutableDictionary *pageData = [NSMutableDictionary dictionary];
    pageData[@"appId"] = @"1";
    pageData[@"sysLang"] = [[NSLocale currentLocale] languageCode] ?: @"en";
    // Android 引擎会自动注入 statusBarHeight（dp），iOS 不会 —— 这里手动补上，
    // 否则 commonMain 里 `pageData.statusBarHeight.dp` 为 0，内容顶到状态栏/刘海区域。
    // iOS 上 Kuikly density = UIScreen.scale，pt 数值 == dp 数值。
    CGFloat sb = 0;
    if (@available(iOS 13.0, *)) {
        for (UIWindowScene *scene in UIApplication.sharedApplication.connectedScenes) {
            if (![scene isKindOfClass:[UIWindowScene class]]) continue;
            for (UIWindow *win in scene.windows) {
                if (win.windowScene.statusBarManager) {
                    CGFloat h = win.windowScene.statusBarManager.statusBarFrame.size.height;
                    if (h > 0) { sb = h; break; }
                }
                if (win.safeAreaInsets.top > 0) { sb = win.safeAreaInsets.top; break; }
            }
            if (sb > 0) break;
        }
    }
    if (sb == 0) sb = UIApplication.sharedApplication.statusBarFrame.size.height;
    // 兜底值：contextPageData 在 viewDidLoad 阶段调用，window/scene 可能未连接导致读不到真值。
    // 老款 iPhone 状态栏 20pt，刘海屏 44~47pt，Dynamic Island 47~59pt。用 47 保证刘海屏不遮，
    // 老款会顶部多留一点空白（无害）。再低（如 20）刘海屏会被遮，再高（如 60）老款留白过大。
    if (sb <= 0) sb = 47;
    pageData[@"statusBarHeight"] = @(sb);
    return pageData;
}

- (void)dealloc {
    // Clean up
}

@end
