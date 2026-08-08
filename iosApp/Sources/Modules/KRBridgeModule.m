#import "KRBridgeModule.h"
#import "KuiklyRenderViewController.h"
#import "KuiklyContextParam.h"
#import "KuiklyRenderView.h"
#import <Photos/Photos.h>
#import <AVKit/AVKit.h>
#import <SDWebImage/SDWebImageManager.h>
#import <SDWebImage/SDWebImageDownloader.h>
#import <SDWebImage/SDImageCache.h>

@implementation KRBridgeModule

@synthesize hr_rootView;

#pragma mark - Page Navigation

- (void)closePage:(NSDictionary *)args {
    UIViewController *vc = [self viewController];
    [vc.navigationController popViewControllerAnimated:YES];
}

- (void)openPage:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *pageName = params[@"pageName"] ?: params[@"url"];
    NSMutableDictionary *pageData = [params[@"pageData"] mutableCopy] ?: [NSMutableDictionary new];
    KuiklyRenderViewController *vc = [[KuiklyRenderViewController alloc] initWithPageName:pageName pageData:pageData];
    [[self viewController].navigationController pushViewController:vc animated:YES];
}

#pragma mark - Clipboard

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *content = params[@"content"];
    [UIPasteboard generalPasteboard].string = content;
}

#pragma mark - Logging

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender: %@", content);
}

#pragma mark - Image Cache

- (void)getLocalImagePath:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *urlStr = params[@"imageUrl"];
    NSURL *url = [NSURL URLWithString:urlStr];

    [[SDWebImageDownloader sharedDownloader] downloadImageWithURL:url
                                                          options:0
                                                         progress:nil
                                                        completed:^(UIImage * _Nullable image, NSData * _Nullable data, NSError * _Nullable error, BOOL finished) {
        if (image) {
            NSString *key = [[SDWebImageManager sharedManager] cacheKeyForURL:url];
            [[SDImageCache sharedImageCache] storeImage:image imageData:data forKey:key toDisk:YES completion:^{
                NSString *path = [[SDImageCache sharedImageCache] cachePathForKey:key];
                KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
                if (callback) {
                    callback(@{@"localPath": path ?: @""});
                }
            }];
        }
    }];
}

#pragma mark - Asset File

- (void)readAssetFile:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *path = params[@"assetPath"];
    KuiklyContextParam *contextParam = ((KuiklyRenderView *)self.hr_rootView).contextParam;
    NSURL *pathUrl = [contextParam urlForFileName:[path stringByDeletingPathExtension] extension:[path pathExtension]];
    dispatch_async(dispatch_get_global_queue(0, 0), ^{
        NSError *error;
        NSString *jsonStr = [NSString stringWithContentsOfURL:pathUrl encoding:NSUTF8StringEncoding error:&error];
        NSDictionary *result = @{
            @"result": jsonStr ?: @"",
            @"error": error.description ?: @""
        };
        if (callback) {
            callback(result);
        }
    });
}

#pragma mark - Upload (gallery scan / face-group upload)

static NSString *const KR_KEY_UPLOADED_IDS = @"uploaded_photo_ids";
static NSString *const KR_KEY_PHOTO_ID_MAP = @"photo_id_map";
static NSString *const KR_UPLOAD_BASE = @"https://www.zhuyanyou.fun/api/upload";

// ===== 已上传照片 id 本地记录（上传去重：扫描排除 + 上传跳过）=====
- (NSSet<NSNumber *> *)uploadedPhotoIds {
    NSArray *arr = [[NSUserDefaults standardUserDefaults] arrayForKey:KR_KEY_UPLOADED_IDS];
    NSMutableSet *set = [NSMutableSet new];
    for (id v in arr) {
        if ([v isKindOfClass:[NSNumber class]]) [set addObject:v];
    }
    return set;
}

- (void)markUploadedPhotoIds:(NSArray<NSNumber *> *)ids {
    if (ids.count == 0) return;
    NSMutableSet *cur = [[self uploadedPhotoIds] mutableCopy];
    [cur addObjectsFromArray:ids];
    [[NSUserDefaults standardUserDefaults] setObject:[cur allObjects] forKey:KR_KEY_UPLOADED_IDS];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

// ===== photoId <-> PHAsset.localIdentifier 稳定映射（数字 id 供 Kotlin 使用，跨启动稳定）=====
- (long long)photoIdForAsset:(PHAsset *)asset {
    NSString *lid = asset.localIdentifier;
    if (lid.length == 0) return 0;
    NSMutableDictionary *map = [[[NSUserDefaults standardUserDefaults] dictionaryForKey:KR_KEY_PHOTO_ID_MAP] mutableCopy] ?: [NSMutableDictionary new];
    for (NSString *k in map.allKeys) {
        if ([map[k] isEqualToString:lid]) return k.longLongValue;
    }
    long long maxId = 0;
    for (NSString *k in map.allKeys) {
        long long v = k.longLongValue;
        if (v > maxId) maxId = v;
    }
    long long nid = maxId + 1;
    map[[NSString stringWithFormat:@"%lld", nid]] = lid;
    [[NSUserDefaults standardUserDefaults] setObject:map forKey:KR_KEY_PHOTO_ID_MAP];
    [[NSUserDefaults standardUserDefaults] synchronize];
    return nid;
}

- (PHAsset *)assetForPhotoId:(long long)photoId {
    NSDictionary *map = [[NSUserDefaults standardUserDefaults] dictionaryForKey:KR_KEY_PHOTO_ID_MAP];
    NSString *lid = map[[NSString stringWithFormat:@"%lld", photoId]];
    if (lid.length == 0) return nil;
    return [[PHAsset fetchAssetsWithLocalIdentifiers:@[lid] options:nil] firstObject];
}

// ===== 导出 PHAsset 到临时目录（thumb 或原图），返回 file:// 路径 =====
- (NSString *)exportImageForAsset:(PHAsset *)asset
                          photoId:(long long)photoId
                           folder:(NSString *)folder
                         maxPixel:(CGFloat)maxPixel
                          quality:(CGFloat)quality {
    NSString *dir = [NSTemporaryDirectory() stringByAppendingPathComponent:folder];
    [[NSFileManager defaultManager] createDirectoryAtPath:dir withIntermediateDirectories:YES attributes:nil error:nil];
    NSString *path = [dir stringByAppendingPathComponent:[NSString stringWithFormat:@"%lld.jpg", photoId]];
    if ([[NSFileManager defaultManager] fileExistsAtPath:path]) return [@"file://" stringByAppendingString:path];
    __block NSString *result = @"";
    PHImageRequestOptions *opt = [PHImageRequestOptions new];
    opt.synchronous = YES;
    opt.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;
    opt.networkAccessAllowed = YES;
    CGSize targetSize = (maxPixel > 0) ? CGSizeMake(maxPixel, maxPixel) : PHImageManagerMaximumSize;
    [[PHImageManager defaultManager] requestImageForAsset:asset targetSize:targetSize contentMode:PHImageContentModeAspectFill options:opt resultHandler:^(UIImage *image, NSDictionary *info) {
        if (image) {
            NSData *data = UIImageJPEGRepresentation(image, quality);
            if ([data writeToFile:path atomically:YES]) result = [@"file://" stringByAppendingString:path];
        }
    }];
    return result;
}

// ===== multipart/form-data 上传 =====
- (void)multipartUploadToURL:(NSURL *)url
                   fileField:(NSString *)field
                   filePaths:(NSArray<NSString *> *)paths
                 extraFields:(NSDictionary *)extra
                  completion:(void (^)(NSString * _Nullable body, NSError * _Nullable err))completion {
    NSString *boundary = [NSString stringWithFormat:@"Chaijie-%@", [[NSUUID UUID] UUIDString]];
    NSMutableData *body = [NSMutableData data];
    [extra enumerateKeysAndObjectsUsingBlock:^(NSString *k, NSString *v, BOOL *stop) {
        [body appendData:[[NSString stringWithFormat:@"--%@\r\nContent-Disposition: form-data; name=\"%@\"\r\n\r\n%@\r\n", boundary, k, v] dataUsingEncoding:NSUTF8StringEncoding]];
    }];
    for (NSString *p in paths) {
        NSString *local = [p hasPrefix:@"file://"] ? [p substringFromIndex:7] : p;
        NSData *fdata = [NSData dataWithContentsOfFile:local];
        if (fdata.length == 0) continue;
        NSString *fn = [local lastPathComponent];
        [body appendData:[[NSString stringWithFormat:@"--%@\r\nContent-Disposition: form-data; name=\"%@\"; filename=\"%@\"\r\nContent-Type: image/jpeg\r\n\r\n", boundary, field, fn] dataUsingEncoding:NSUTF8StringEncoding]];
        [body appendData:fdata];
        [body appendData:[@"\r\n" dataUsingEncoding:NSUTF8StringEncoding]];
    }
    [body appendData:[[NSString stringWithFormat:@"--%@--\r\n", boundary] dataUsingEncoding:NSUTF8StringEncoding]];
    NSMutableURLRequest *req = [NSMutableURLRequest requestWithURL:url];
    req.HTTPMethod = @"POST";
    req.timeoutInterval = 300;
    [req setValue:[NSString stringWithFormat:@"multipart/form-data; boundary=%@", boundary] forHTTPHeaderField:@"Content-Type"];
    [req setHTTPBody:body];
    [[[NSURLSession sharedSession] dataTaskWithRequest:req completionHandler:^(NSData *data, NSURLResponse *resp, NSError *err) {
        if (err) { completion(nil, err); return; }
        completion([[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding], nil);
    }] resume];
}

// ===== 同步 GET =====
- (NSString *)httpGetSync:(NSString *)urlString {
    __block NSString *result = nil;
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);
    NSMutableURLRequest *req = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:urlString]];
    req.timeoutInterval = 15;
    [req setValue:@"Mozilla/5.0 (ChaijieApp)" forHTTPHeaderField:@"User-Agent"];
    [[[NSURLSession sharedSession] dataTaskWithRequest:req completionHandler:^(NSData *data, NSURLResponse *resp, NSError *err) {
        if (err == nil && data.length > 0) {
            result = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
        }
        dispatch_semaphore_signal(sem);
    }] resume];
    dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, 30 * NSEC_PER_SEC));
    return result;
}

// ===== 相册权限 =====
- (BOOL)ensureGalleryPermission {
    if (@available(iOS 14, *)) {
        PHAuthorizationStatus st = [PHPhotoLibrary authorizationStatusForAccessLevel:PHAccessLevelReadWrite];
        return (st == PHAuthorizationStatusAuthorized || st == PHAuthorizationStatusLimited);
    }
    return [PHPhotoLibrary authorizationStatus] == PHAuthorizationStatusAuthorized;
}

- (void)requestGalleryPermission {
    if (@available(iOS 14, *)) {
        [PHPhotoLibrary requestAuthorizationForAccessLevel:PHAccessLevelReadWrite handler:^(PHAuthorizationStatus status) {}];
    } else {
        [PHPhotoLibrary requestAuthorization:^(PHAuthorizationStatus status) {}];
    }
}

// ===== 同步：扫描相册最近 N 张（跳过已上传），返回带缩略图路径的列表 =====
- (NSString *)scanGallery:(NSDictionary *)args {
    if (![self ensureGalleryPermission]) {
        [self requestGalleryPermission];
        return @"{\"success\":false,\"needPermission\":true}";
    }
    NSDictionary *params = [self parseParams:args];
    NSInteger limit = [params[@"limit"] integerValue];
    if (limit <= 0) limit = 300;
    NSSet *exclude = [self uploadedPhotoIds];
    PHFetchOptions *fo = [PHFetchOptions new];
    fo.sortDescriptors = @[[NSSortDescriptor sortDescriptorWithKey:@"creationDate" ascending:NO]];
    PHFetchResult *result = [PHAsset fetchAssetsWithMediaType:PHAssetMediaTypeImage options:fo];
    NSMutableArray *photos = [NSMutableArray new];
    NSInteger count = 0;
    for (NSInteger i = 0; i < result.count && count < limit; i++) {
        PHAsset *asset = [result objectAtIndex:i];
        long long pid = [self photoIdForAsset:asset];
        if ([exclude containsObject:@(pid)]) continue;
        NSString *thumb = [self exportImageForAsset:asset photoId:pid folder:@"thumbs" maxPixel:600 quality:0.8];
        if (thumb.length == 0) continue;
        NSMutableDictionary *o = [NSMutableDictionary new];
        o[@"id"] = @(pid);
        o[@"date"] = asset.creationDate ? @(asset.creationDate.timeIntervalSince1970 * 1000) : @0;
        o[@"thumb"] = thumb;
        o[@"width"] = @((NSInteger)asset.pixelWidth);
        o[@"height"] = @((NSInteger)asset.pixelHeight);
        [photos addObject:o];
        count++;
    }
    NSDictionary *res = @{
        @"success": @YES,
        @"count": @(count),
        @"excluded_uploaded": @(exclude.count),
        @"photos": photos,
    };
    NSData *data = [NSJSONSerialization dataWithJSONObject:res options:0 error:nil];
    return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] ?: @"{\"success\":false,\"error\":\"serialize\"}";
}

// ===== 异步：上传缩略图 → 后端识别，回调返回 taskId =====
- (void)scanUpload:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSArray *files = params[@"files"];
    if (![files isKindOfClass:[NSArray class]] || files.count == 0) {
        if (callback) callback(@{@"success": @NO, @"error": @"no files"});
        return;
    }
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        [self multipartUploadToURL:[NSURL URLWithString:[KR_UPLOAD_BASE stringByAppendingString:@"/scan"]]
                         fileField:@"photos"
                        filePaths:files
                       extraFields:nil
                        completion:^(NSString *body, NSError *err) {
            NSString *taskId = @"";
            BOOL ok = NO;
            if (err == nil && body.length > 0) {
                NSData *d = [body dataUsingEncoding:NSUTF8StringEncoding];
                NSDictionary *jo = [NSJSONSerialization JSONObjectWithData:d options:0 error:nil];
                if ([jo isKindOfClass:[NSDictionary class]]) {
                    taskId = jo[@"task_id"] ?: @"";
                    ok = taskId.length > 0;
                }
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) callback(@{@"success": @(ok), @"taskId": taskId ?: @""});
            });
        }];
    });
}

// ===== 同步：轮询识别任务状态，直接返回后端 body JSON =====
- (NSString *)scanPoll:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *taskId = params[@"taskId"] ?: @"";
    if (taskId.length == 0) return @"{\"success\":false,\"error\":\"no taskId\"}";
    NSString *body = [self httpGetSync:[NSString stringWithFormat:@"%@/scan/%@", KR_UPLOAD_BASE, taskId]];
    return body ?: @"{\"success\":false,\"error\":\"poll failed\"}";
}

// ===== 同步：把相册原图拷贝到本地 cache/originals/{id}.jpg，返回 file:// 路径 =====
- (NSString *)copyOriginal:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    long long pid = [params[@"id"] longLongValue];
    if (pid <= 0) return @"";
    PHAsset *asset = [self assetForPhotoId:pid];
    if (!asset) return @"";
    return [self exportImageForAsset:asset photoId:pid folder:@"originals" maxPixel:0 quality:0.95];
}

// ===== 检查本地是否已有原图缓存（夜间预缓存 remote_originals / 本机相册 originals）=====
- (NSString *)checkLocalOriginal:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    long long pid = [params[@"id"] longLongValue];
    if (pid <= 0) return @"";
    NSString *dir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
    // 1) 云端夜间预缓存
    NSString *remoteDir = [dir stringByAppendingPathComponent:@"remote_originals"];
    NSString *remoteFile = [remoteDir stringByAppendingPathComponent:[NSString stringWithFormat:@"%lld.jpg", pid]];
    if ([[NSFileManager defaultManager] fileExistsAtPath:remoteFile]) {
        return [NSString stringWithFormat:@"file://%@", remoteFile];
    }
    // 2) 本机相册直读缓存（copyOriginal 拷贝）
    NSString *localDir = [dir stringByAppendingPathComponent:@"originals"];
    NSString *localFile = [localDir stringByAppendingPathComponent:[NSString stringWithFormat:@"%lld.jpg", pid]];
    if ([[NSFileManager defaultManager] fileExistsAtPath:localFile]) {
        return [NSString stringWithFormat:@"file://%@", localFile];
    }
    return @"";
}

// ===== 异步：批量上传原图入库（photos: [{id, filename}]，可选 groups）=====
- (void)batchUpload:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSArray *photos = params[@"photos"];
    if (![photos isKindOfClass:[NSArray class]] || photos.count == 0) {
        if (callback) callback(@{@"success": @NO, @"error": @"no photos"});
        return;
    }
    NSSet *already = [self uploadedPhotoIds];
    NSMutableArray *paths = [NSMutableArray new];
    NSMutableArray *uploadedIds = [NSMutableArray new];
    for (id raw in photos) {
        if (![raw isKindOfClass:[NSDictionary class]]) continue;
        NSDictionary *o = (NSDictionary *)raw;
        long long pid = [o[@"id"] longLongValue];
        if (pid <= 0) continue;
        if ([already containsObject:@(pid)]) continue;
        PHAsset *asset = [self assetForPhotoId:pid];
        if (!asset) continue;
        NSString *path = [self exportImageForAsset:asset photoId:pid folder:@"originals" maxPixel:0 quality:0.95];
        if (path.length == 0) continue;
        [paths addObject:path];
        [uploadedIds addObject:@(pid)];
    }
    if (paths.count == 0) {
        if (callback) callback(@{@"success": @YES, @"uploaded": @0, @"skipped": @(uploadedIds.count), @"failed": @0});
        return;
    }
    NSMutableDictionary *extra = [NSMutableDictionary new];
    if ([params[@"groups"] isKindOfClass:[NSArray class]] && ((NSArray *)params[@"groups"]).count > 0) {
        NSData *gd = [NSJSONSerialization dataWithJSONObject:params[@"groups"] options:0 error:nil];
        if (gd) extra[@"groups"] = [[NSString alloc] initWithData:gd encoding:NSUTF8StringEncoding];
    }
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        [self multipartUploadToURL:[NSURL URLWithString:[KR_UPLOAD_BASE stringByAppendingString:@"/batch"]]
                         fileField:@"photos"
                        filePaths:paths
                       extraFields:extra
                        completion:^(NSString *body, NSError *err) {
            NSMutableDictionary *result = [NSMutableDictionary new];
            if (err == nil && body.length > 0) {
                NSData *d = [body dataUsingEncoding:NSUTF8StringEncoding];
                id jo = [NSJSONSerialization JSONObjectWithData:d options:0 error:nil];
                if ([jo isKindOfClass:[NSDictionary class]]) {
                    [result addEntriesFromDictionary:(NSDictionary *)jo];
                    [self markUploadedPhotoIds:uploadedIds];
                }
            }
            if (result.count == 0) {
                result[@"success"] = @NO;
                result[@"error"] = err ? err.localizedDescription : @"upload failed";
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) callback(result);
            });
        }];
    });
}

#pragma mark - Video

- (void)playVideo:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSArray *urlsArr = params[@"urls"];
    NSMutableArray<NSString *> *urls = [NSMutableArray new];
    for (id u in urlsArr) {
        if ([u isKindOfClass:[NSString class]] && [(NSString *)u length] > 0) [urls addObject:u];
    }
    if (urls.count == 0) return;
    NSInteger index = [params[@"index"] integerValue];
    if (index < 0 || index >= (NSInteger)urls.count) index = 0;
    NSString *urlStr = urls[index];
    UIViewController *vc = [self viewController];
    if (!vc) return;
    dispatch_async(dispatch_get_main_queue(), ^{
        AVPlayerViewController *playerVC = [[AVPlayerViewController alloc] init];
        playerVC.modalPresentationStyle = UIModalPresentationFullScreen;
        AVPlayer *player = [AVPlayer playerWithURL:[NSURL URLWithString:urlStr]];
        playerVC.player = player;
        [vc presentViewController:playerVC animated:YES completion:^{
            [player play];
        }];
    });
}

#pragma mark - Helpers

- (NSDictionary *)parseParams:(NSDictionary *)args {
    id param = args[KR_PARAM_KEY];
    if ([param isKindOfClass:[NSDictionary class]]) {
        return param;
    }
    if ([param isKindOfClass:[NSString class]]) {
        NSData *data = [(NSString *)param dataUsingEncoding:NSUTF8StringEncoding];
        if (data) {
            id json = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
            if ([json isKindOfClass:[NSDictionary class]]) {
                return json;
            }
        }
    }
    return @{};
}

- (UIViewController *)viewController {
    UIView *view = self.hr_rootView;
    UIResponder *responder = view;
    while (responder) {
        if ([responder isKindOfClass:[UIViewController class]]) {
            return (UIViewController *)responder;
        }
        responder = [responder nextResponder];
    }
    return nil;
}


#pragma mark - Remote Image Cache (original/thumb download)

// ===== 同步：下载云端原图到本地 cache/remote_originals/{photoId}.jpg，返回 JSON {path, cached} =====
- (NSString *)cacheRemoteOriginal:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *url = params[@"url"] ?: @"";
    NSString *photoId = params[@"photoId"] ?: @"p";
    if (url.length == 0) return @"";
    NSString *ext = @".jpg";
    if ([url containsString:@".png"]) ext = @".png";
    else if ([url containsString:@".webp"]) ext = @".webp";
    NSString *dir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
    NSString *folder = [dir stringByAppendingPathComponent:@"remote_originals"];
    [[NSFileManager defaultManager] createDirectoryAtPath:folder withIntermediateDirectories:YES attributes:nil error:nil];
    NSString *outPath = [folder stringByAppendingPathComponent:[photoId stringByAppendingString:ext]];
    if ([[NSFileManager defaultManager] fileExistsAtPath:outPath]) {
        return [NSString stringWithFormat:@"{\"path\":\"file://%@\",\"cached\":true}", outPath];
    }
    NSData *data = [NSData dataWithContentsOfURL:[NSURL URLWithString:url] options:0 error:nil];
    if (data.length > 0) {
        [data writeToFile:outPath atomically:YES];
        return [NSString stringWithFormat:@"{\"path\":\"file://%@\",\"cached\":false}", outPath];
    }
    return @"";
}

// ===== 异步：下载原图（子线程 + 主线程回调），加载动画期间不阻塞 UI =====
- (void)asyncCacheRemoteOriginal:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *url = params[@"url"] ?: @"";
    NSString *photoId = params[@"photoId"] ?: @"p";
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    if (url.length == 0) {
        if (callback) callback(@{@"path": @"", @"cached": @NO});
        return;
    }
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        NSString *ext = @".jpg";
        if ([url containsString:@".png"]) ext = @".png";
        else if ([url containsString:@".webp"]) ext = @".webp";
        NSString *dir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
        NSString *folder = [dir stringByAppendingPathComponent:@"remote_originals"];
        [[NSFileManager defaultManager] createDirectoryAtPath:folder withIntermediateDirectories:YES attributes:nil error:nil];
        NSString *outPath = [folder stringByAppendingPathComponent:[photoId stringByAppendingString:ext]];
        NSString *path = @"";
        BOOL cached = NO;
        if ([[NSFileManager defaultManager] fileExistsAtPath:outPath]) {
            path = [NSString stringWithFormat:@"file://%@", outPath];
            cached = YES;
        } else {
            NSData *data = [NSData dataWithContentsOfURL:[NSURL URLWithString:url] options:0 error:nil];
            if (data.length > 0) {
                [data writeToFile:outPath atomically:YES];
                path = [NSString stringWithFormat:@"file://%@", outPath];
            }
        }
        dispatch_async(dispatch_get_main_queue(), ^{
            if (callback) callback(@{@"path": path, @"cached": @(cached)});
        });
    });
}

// ===== 同步：下载缩略图到本地 cache/remote_thumbs/{photoId}.jpg，返回 file:// 路径 =====
- (NSString *)cacheRemoteThumb:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *url = params[@"url"] ?: @"";
    NSString *photoId = params[@"photoId"] ?: @"p";
    if (url.length == 0) return @"";
    NSString *ext = @".jpg";
    if ([url containsString:@".png"]) ext = @".png";
    else if ([url containsString:@".webp"]) ext = @".webp";
    NSString *dir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
    NSString *folder = [dir stringByAppendingPathComponent:@"remote_thumbs"];
    [[NSFileManager defaultManager] createDirectoryAtPath:folder withIntermediateDirectories:YES attributes:nil error:nil];
    NSString *outPath = [folder stringByAppendingPathComponent:[photoId stringByAppendingString:ext]];
    if ([[NSFileManager defaultManager] fileExistsAtPath:outPath]) {
        return [NSString stringWithFormat:@"file://%@", outPath];
    }
    NSData *data = [NSData dataWithContentsOfURL:[NSURL URLWithString:url] options:0 error:nil];
    if (data.length > 0) {
        [data writeToFile:outPath atomically:YES];
        return [NSString stringWithFormat:@"file://%@", outPath];
    }
    return @"";
}

#pragma mark - Upload Queue (import / flush / skipped)

// ===== 上传队列：把相册原图拷贝到 cache/pending_upload/{id}.jpg（秒收），返回 {imported, skipped} =====
- (NSString *)importToUploadQueue:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSArray *photoIds = params[@"photoIds"];
    NSSet *already = [self uploadedPhotoIds];
    NSString *dir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
    NSString *qDir = [dir stringByAppendingPathComponent:@"pending_upload"];
    [[NSFileManager defaultManager] createDirectoryAtPath:qDir withIntermediateDirectories:YES attributes:nil error:nil];
    int imported = 0, skipped = 0;
    for (id raw in photoIds) {
        long long pid = [raw longLongValue];
        if (pid <= 0) { skipped++; continue; }
        if ([already containsObject:@(pid)]) { skipped++; continue; }
        NSString *outPath = [qDir stringByAppendingPathComponent:[NSString stringWithFormat:@"%lld.jpg", pid]];
        if ([[NSFileManager defaultManager] fileExistsAtPath:outPath]) { imported++; continue; }
        PHAsset *asset = [self assetForPhotoId:pid];
        if (!asset) { skipped++; continue; }
        NSString *path = [self exportImageForAsset:asset photoId:pid folder:@"pending_upload" maxPixel:0 quality:0.95];
        if (path.length > 0) imported++; else skipped++;
    }
    return [NSString stringWithFormat:@"{\"success\":true,\"imported\":%d,\"skipped\":%d}", imported, skipped];
}

// ===== 后台慢慢上传队列：逐个传 pending_upload/ 里文件，成功后删除 =====
- (void)flushUploadQueue:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSString *dir = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
    NSString *qDir = [dir stringByAppendingPathComponent:@"pending_upload"];
    NSArray *files = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:qDir error:nil];
    NSMutableArray *paths = [NSMutableArray new];
    for (NSString *f in files) {
        if ([f hasSuffix:@".jpg"]) [paths addObject:[qDir stringByAppendingPathComponent:f]];
    }
    if (paths.count == 0) {
        if (callback) callback(@{@"success": @YES, @"uploaded": @0, @"failed": @0, @"total": @0});
        return;
    }
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        [self multipartUploadToURL:[NSURL URLWithString:[KR_UPLOAD_BASE stringByAppendingString:@"/batch"]]
                         fileField:@"photos"
                        filePaths:paths
                       extraFields:nil
                        completion:^(NSString *body, NSError *err) {
            int uploaded = 0, failed = 0;
            if (err == nil && body.length > 0) {
                NSData *d = [body dataUsingEncoding:NSUTF8StringEncoding];
                id jo = [NSJSONSerialization JSONObjectWithData:d options:0 error:nil];
                if ([jo isKindOfClass:[NSDictionary class]]) {
                    uploaded = [jo[@"uploaded"] intValue];
                    for (NSString *p in paths) [[NSFileManager defaultManager] removeItemAtPath:p error:nil];
                }
            }
            failed = (int)paths.count - uploaded;
            dispatch_async(dispatch_get_main_queue(), ^{
                if (callback) callback(@{@"success": @YES, @"uploaded": @(uploaded), @"failed": @(failed), @"total": @(paths.count)});
            });
        }];
    });
}

// ===== 跳过记录：用户取消上传的照片（下次扫描不再提示）=====
static NSString *const KR_KEY_SKIPPED_IDS = @"skipped_upload_photo_ids";

- (NSSet<NSNumber *> *)skippedUploadIds {
    NSArray *arr = [[NSUserDefaults standardUserDefaults] arrayForKey:KR_KEY_SKIPPED_IDS];
    NSMutableSet *set = [NSMutableSet new];
    for (id v in arr) { if ([v isKindOfClass:[NSNumber class]]) [set addObject:v]; }
    return set;
}

- (NSString *)markSkippedUploads:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSArray *ids = params[@"photoIds"];
    NSMutableSet *cur = [[self skippedUploadIds] mutableCopy];
    for (id raw in ids) {
        long long pid = [raw longLongValue];
        if (pid > 0) [cur addObject:@(pid)];
    }
    [cur minusSet:[self uploadedPhotoIds]];
    [[NSUserDefaults standardUserDefaults] setObject:[cur allObjects] forKey:KR_KEY_SKIPPED_IDS];
    [[NSUserDefaults standardUserDefaults] synchronize];
    return [NSString stringWithFormat:@"{\"success\":true,\"marked\":%lu}", (unsigned long)ids.count];
}

- (NSString *)getSkippedUploads:(NSDictionary *)args {
    NSArray *sorted = [[[self skippedUploadIds] allObjects] sortedArrayUsingSelector:@selector(compare:)];
    NSData *d = [NSJSONSerialization dataWithJSONObject:sorted options:0 error:nil];
    return [[NSString alloc] initWithData:d encoding:NSUTF8StringEncoding];
}

- (NSString *)clearSkippedUploads:(NSDictionary *)args {
    [[NSUserDefaults standardUserDefaults] removeObjectForKey:KR_KEY_SKIPPED_IDS];
    [[NSUserDefaults standardUserDefaults] synchronize];
    return @"{\"success\":true}";
}

#pragma mark - Toast

- (void)toast:(NSDictionary *)args {
    NSDictionary *params = [self parseParams:args];
    NSString *msg = params[@"message"] ?: @"";
    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *vc = [self viewController];
        if (!vc) return;
        UILabel *label = [[UILabel alloc] initWithFrame:CGRectZero];
        label.text = msg;
        label.textColor = UIColor.whiteColor;
        label.backgroundColor = [UIColor colorWithWhite:0 alpha:0.75];
        label.font = [UIFont systemFontOfSize:14];
        label.textAlignment = NSTextAlignmentCenter;
        label.layer.cornerRadius = 8;
        label.clipsToBounds = YES;
        label.numberOfLines = 0;
        [label sizeToFit];
        CGRect f = label.frame;
        f.size.width += 32; f.size.height += 18;
        label.frame = f;
        label.center = CGPointMake(CGRectGetMidX(vc.view.bounds), CGRectGetMidY(vc.view.bounds));
        [vc.view addSubview:label];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [label removeFromSuperview];
        });
    });
}

@end
