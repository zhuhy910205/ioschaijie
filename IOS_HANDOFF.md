# ChaijieApp-iOS · 打包交接文档（给新任务）

> 用途：把「通过 GitHub Actions 打包 iOS .ipa」这件事的完整上下文交接给新任务。
> 新任务开始前先读本文件 + `IOS_BUILD_GUIDE.md`，可直接接手继续修。

---

## 一、现状一句话

**iOS 打包工程已独立为 `E:\kuikly_apps\ChaijieApp-iOS\`，已推送 GitHub 仓库 `zhuhy910205/ioschaijie`（公开，main 分支），GitHub Actions workflow 已跑通大部分环节，目前卡在 xcodebuild 编译 shared framework 阶段（第 5 关之后的第 6 关，具体错误待用户贴日志）。**

---

## 二、两个工程的关系（重要，别改乱）

| | 原工程（安卓在用） | iOS 专用副本 |
|---|---|---|
| 路径 | `E:\kuikly_apps\ChaijieApp\` | `E:\kuikly_apps\ChaijieApp-iOS\` |
| 用途 | 安卓构建/调试（HomePage/UploadPage/VideoPage 等全部功能） | **只**做 iOS 打包 |
| Git | 不是仓库 | `git@github.com:zhuhy910205/ioschaijie.git`（main） |
| Podfile | 保留 `use_frameworks!`（原样） | **已移除** `use_frameworks!`（Kuikly 静态链接规范） |

> ⚠️ **规则**：所有 iOS 打包相关的修改只动 `ChaijieApp-iOS`，绝不动 `ChaijieApp`（安卓那边有完整的上传/合并/视频功能在迭代）。
> 安卓侧代码改动如需同步到 iOS，用 tar 复制：
> ```bash
> cd /e/kuikly_apps/ChaijieApp && tar cf - --exclude='.gradle' --exclude='.kotlin' --exclude='.idea' --exclude='build' --exclude='*/build' --exclude='*.log' --exclude='*.apk' --exclude='_cleanup_trash' --exclude='screenshots' --exclude='static_server' --exclude='local.properties' --exclude='.github' . | (cd /e/kuikly_apps/ChaijieApp-iOS && tar xf -)
```

---

## 三、工程结构（iOS 相关）

```
ChaijieApp-iOS/
├── .github/workflows/ios-build.yml   ← 打包 workflow（已修 5 轮）
├── iosApp/
│   ├── Podfile                        ← 无 use_frameworks!；shared: path; OpenKuiklyIOSRender 2.23.2; SDWebImage
│   ├── project.yml                    ← xcodegen 配置（xcodeVersion 16.2；PRODUCT_NAME=iosApp 显式指定）
│   ├── Sources/                       ← AppDelegate.swift + KuiklyRenderViewController(.h/.m) + Modules/
│   └── iosApp-Bridging-Header.h
├── shared/                            ← KMP 共享模块（18 个 kt，Compose DSL）
│   └── build.gradle.kts               ← iosX64/iosArm64/iosSimulatorArm64 + cocoapods 集成（isStatic=true）
├── IOS_BUILD_GUIDE.md                 ← 打包使用说明（步骤/签名/踩坑）
└── .gitignore                         ← 排除 xcodeproj/Pods/build/ipa 等
```

关键配置：
- **shared/build.gradle.kts**：`cocoapods { framework { baseName="shared"; isStatic=true } }`，`podfile = ../iosApp/Podfile`
- **Podfile**：`pod 'shared', :path => '../shared'` + `pod 'OpenKuiklyIOSRender', :git => 'https://github.com/Tencent-TDS/KuiklyUI.git', :tag => '2.23.2'` + `pod 'SDWebImage'`
- Bundle ID：`com.chaijie.app`；deployment target iOS 14.1

---

## 四、workflow 当前流程（ios-build.yml，已修 5 轮）

```
macos-14 runner（90min 超时）
 0. Checkout
 1. chmod +x gradlew（兜底，防 Windows 提交丢权限位）
 2. 自动选最新 Xcode（ls /Applications/Xcode_1*.app | sort -V | tail -1 → sudo xcode-select -s + 写 DEVELOPER_DIR 到 GITHUB_ENV）
 3. Setup JDK 17（temurin）+ gradle cache
 4. 装 XcodeGen 2.38.0（GitHub Release 下载，zip 内路径为 xcodegen/bin/xcodegen！）
 5. xcodegen generate（生成 iosApp.xcodeproj，格式 56）
 6. ./gradlew :shared:generateDummyFramework（Kuikly 必需，否则 SIGABRT）
 7. CocoaPods 固定 1.15.2（sudo gem uninstall 新版 → 装 1.15.2；否则 Pods.xcodeproj 生成格式 77）
 8. pod install --repo-update
 9. xcodebuild build（CODE_SIGNING_ALLOWED=NO 等，-workspace iosApp.xcworkspace -scheme iosApp -destination generic/platform=iOS）
10. 找 .app → 打未签名 ipa（Payload 结构）→ upload-artifact
    （build_type=archive 时改出 .xcarchive）
```

**workflow_dispatch 参数**：`build_type`（unsigned_ipa 默认 / archive）；`push 到 main` 也会自动触发。

---

## 五、已解决的坑（踩坑记录，新任务别再踩）

| # | 失败现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | `./gradlew: Permission denied`（21s 退出） | Windows 提交 gradlew 时 Git 可执行位 100644（丢失 100755） | `git update-index --chmod=+x gradlew` + workflow 加 chmod 兜底 |
| 2 | `future Xcode project file format (77)` 打不开工程 | workflow 硬编码 DEVELOPER_DIR=Xcode_15.4，但 xcodegen 2.42 + 新版 CocoaPods 生成格式 77（Xcode16） | 删硬编码；xcodegen 固定 2.38.0、CocoaPods 固定 1.15.2（生成格式 56）；自动选最新 Xcode |
| 3 | `xcode-select: --switch must be run as root` | xcode-select -s 需 root | 加 `sudo` |
| 4 | `cp: /tmp/xcodegen-bin/usr/local/bin/xcodegen: No such file` | xcodegen 2.38.0 的 zip 结构是 `xcodegen/bin/xcodegen`（不是 usr/local/bin） | 修正拷贝路径 |
| 5 | `Multiple commands produce '.../Release-iphoneos/.app'` | xcodegen 2.38 生成 application target 时 PRODUCT_NAME 为空 → .app 空名冲突 | project.yml 显式 `PRODUCT_NAME: iosApp`；xcodeVersion 15.0→16.2 |
| 6 | `shared:compileKotlinIosArm64` 失败：`PlacePage.kt:1133:23 Unresolved reference 'format'` | commonMain 里用了 JVM-only 的 `String.format("%.4f...")`，Kotlin/Native 无此 API | 用纯 Kotlin 实现 `fmtFixed4()`（`kotlin.math.round` + `padStart` 补零）替换（commit f13d7d1） |
| 7 | xcodebuild exit 65：`KRBridgeModule.m:11:13 cannot synthesize weak property in file using manual reference counting` | 工程没开 ARC，而 Kuikly 协议（KRBaseModule.h）声明了 `weak` 属性（`hr_rootView`），MRC 下无法 synthesize | **project.yml** target settings 加 `CLANG_ENABLE_OBJC_ARC: YES`（整 target 开 ARC；所有 .m 无手动 retain/release，安全）。⚠️ 工程用 xcodegen，改 project.yml 而非 pbxproj（会被重新生成覆盖）（commit 88faeab） |

**✅ 当前状态（2026-08-08 已全绿）**：1~7 关全过。最新 run **31204433530（commit 88faeab）conclusion=success**：`BUILD SUCCEEDED` → `ChaijieApp-unsigned.ipa`（5.3MB）→ artifact `ChaijieApp-unsigned-ipa`（ID 9004358954）上传成功。**ipa 已拉回本机 `E:/kuikly_apps/ChaijieApp-iOS/dist/ChaijieApp-unsigned.ipa`**，下一步 Sideloadly 签名安装真机验证。

> ⚠️ 本机查 Actions 的坑：hosts 把 `api.github.com` 钉到 `20.205.243.166`（错误 IP，返回 301 改写/404），正确 IP 是 DNS 的 `20.205.243.168`。所有 GitHub API 调用需加 `--resolve api.github.com:443:20.205.243.168`（不要改 hosts）。

---

## 六、当前卡住的问题（新任务首要任务）

**✅ 已全部解决（2026-08-08）**：iOS 打包链路已打通，最新 workflow run **31204433530 全绿**（`BUILD SUCCEEDED` + ipa 产出 + artifact 上传）。剩余工作只有**真机验证**（Sideloadly 签名安装 `dist/ChaijieApp-unsigned.ipa`）和可选的付费证书签名。

> 历史症状（保留备查）：日志可见 OpenKuiklyIOSRender 编译成功（Libtool → libOpenKuiklyIOSRender.a），随后执行 `[CP-User] Build shared`（gradle 编 Kotlin/Native framework），然后失败。日志尾部只有 note/warning，真正的 error 行已拿到（见上）。

**高概率根因预判**（按可能性）：
1. **`[CP-User] Build shared` 脚本内 gradle 编译失败**——脚本由 KMP cocoapods 插件生成，调用 `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 或类似任务；在 CI 上常见问题：
   - gradle 下载依赖慢/超时（首次要拉 Kotlin/Native 编译器 ~几百 MB）
   - KMP cocoapods 插件与 Xcode 16 的 `SDKROOT`/`ARCHS` 环境不匹配（`-destination generic/platform=iOS` 会设置 arm64，脚本可能拿不到正确 ARCHS）
   - 需要给该步骤加长超时 / 提前手动跑 `./gradlew :shared:linkDebugFrameworkIosArm64`（在 xcodebuild 前）让 framework 先编好，`[CP-User]` 脚本检测到已存在则跳过
2. **Undefined symbol / framework 找不到**：shared.framework 路径不对（xcodegen 的 `-derivedDataPath build` 与 pod 脚本预期的路径不一致）
3. **链接期错误**：KuiklyCoreEntry 等符号未链接（若 generateDummyFramework 没真正产出）

**建议的下一步动作（拿到 error 后）**：
- 若 `[CP-User] Build shared` 失败：在 workflow 的 pod install 之后、xcodebuild 之前**手动加一步** `./gradlew :shared:linkDebugFrameworkIosArm64 --console=plain`（或 `linkReleaseFrameworkIosArm64`），提前编译并缓存，xcodebuild 里脚本会跳过重复编译；同时给 gradle 加 `org.gradle.jvmargs=-Xmx4g`、`--no-daemon`
- 若路径问题：核对 pod 脚本生成的 framework 搜索路径与 `-derivedDataPath` 是否一致（把 `-derivedDataPath build` 去掉，用默认 DerivedData 可能更兼容）
- 若需诊断：在 Build app 步骤加 `set -o pipefail; xcodebuild ... 2>&1 | tee build.log`，并 `grep -E "error:|BUILD FAILED"` 输出，方便定位

---

## 七、签名方案（免费 Apple ID 现实约束）

- **GitHub Actions 是云端 Mac，无法用免费 Apple ID 签名**（免费签名只能本机 Xcode 操作）
- workflow 产出**未签名 .ipa**，下载后用 **Sideloadly**（Windows，https://sideloadly.io/）登录 Apple ID 签名安装
- 免费签名 **7 天过期**，Sideloadly 可开自动重签
- iPhone 需开「开发者模式」（设置→隐私与安全性→开发者模式）并信任证书（设置→通用→VPN与设备管理）
- **以后有付费证书（99$/年）**：生成 p12 + Ad Hoc profile → 存 GitHub Secrets（IOS_CERT_P12 / IOS_CERT_P12_PASSWORD / IOS_PROVISION_PROFILE）→ workflow 加签名+exportArchive 步骤（参考官方 `iosApp/ExportOptions.plist`：enterprise/ad-hoc，`signingStyle: manual`）

---

## 八、环境/凭据信息

- **GitHub 仓库**：`https://github.com/zhuhy910205/ioschaijie`（公开）
- **推送 token**：`ghp_...`（用户提供，有 repo+workflow 权限；**不要写明文到仓库文件**，GitHub Push Protection 会拦截；用完后可让用户撤销，撤销后需新 token）
- **推送命令**（一次性 URL 带 token，不写本地配置）：
  ```bash
  cd /e/kuikly_apps/ChaijieApp-iOS
  git add -A
  git -c user.name="chaijie" -c user.email="chaijie@local" commit -m "..."
  git push "https://<TOKEN>@github.com/zhuhy910205/ioschaijie.git" main
  ```
  > ⚠️ token 从对话历史获取，或让用户重新提供；**禁止把 token 写进仓库内任何文件**（含 md/yml）
- **GitHub API 从本机访问不稳定**（api.github.com 间歇 404），查 Actions 状态优先用网页，或让用户贴日志
- **后端服务器**（与本任务无关，但背景）：160.202.231.11:56514 root（密码见工作区记忆，**勿写明文进仓库**）

---

## 九、新任务建议的验收标准

1. workflow 全绿跑通，产出 `ChaijieApp-unsigned-ipa` artifact
2. 下载 ipa 后 Sideloadly 能签名安装到 iPhone
3. App 打开显示 Kuikly 首页（默认页 HelloWorld；AppDelegate 里 `KUIKLY_PAGE` 环境变量控制）
4. 若用户提供付费证书，能出真签名 ipa

---

## 十、重要背景（用户当前在用的安卓功能，别误伤）

用户最近在迭代：上传功能（扫描相册→人脸识别分组→合并→批量上传）、视频抖音式播放器、方隅照片网格、聚类精度优化、上传去重。这些都在 `ChaijieApp`（安卓）侧，**iOS 副本只复制了当前快照**。iOS 打包跑通后，若需同步最新安卓代码，按第二节的 tar 命令复制。
