# ChaijieApp-iOS · GitHub Actions 打包 .ipa 说明

本工程是 **iOS 专用副本**（与安卓工程 `ChaijieApp` 分开，互不影响）。
安卓构建/调试仍在原工程 `E:\kuikly_apps\ChaijieApp` 进行。

## 一、GitHub Actions 打包流程

`.github/workflows/ios-build.yml` 已配置好，核心步骤：

```
macOS runner (macos-14)
  → setup JDK 17（Kuikly KMP 必需）
  → xcodegen generate（project.yml → xcodeproj）
  → ./gradlew :shared:generateDummyFramework  ★ 必需，否则运行时 SIGABRT
  → pod install（装 OpenKuiklyIOSRender 2.23.2 + shared + SDWebImage）
  → xcodebuild build（禁用签名）
  → 打包未签名 .ipa（Payload 结构） 或 .xcarchive
  → 上传为 Actions artifact（保留 14 天）
```

### 使用步骤

1. **推送代码到 GitHub**：
   ```bash
   git remote add origin git@github.com:<你的用户名>/ChaijieApp-iOS.git
   git push -u origin main
   ```
   （或在 GitHub 网页新建仓库后推送）

2. **触发构建**：仓库页面 → **Actions** → **iOS Build (.ipa)** → **Run workflow**
   - `build_type` 选 `unsigned_ipa`（默认，免费 Apple ID 场景）

3. **下载产物**：构建完成后，Actions 页面 → 本次运行 → **Artifacts** → 下载 `ChaijieApp-unsigned-ipa`

## 二、免费 Apple ID 如何安装（重要）

> ⚠️ **GitHub Actions 是云端 Mac，无法用免费 Apple ID 签名**（免费签名只能在你的本机 Xcode 里做）。
> 所以 Actions 打出的是**未签名 .ipa**，需要用工具在本地签名后安装。

**安装工具（任选）**：
- **Sideloadly**（Windows/Mac，推荐）：https://sideloadly.io/
  1. iPhone 连电脑，安装 Apple ID 登录 Sideloadly
  2. 把下载的 `ChaijieApp-unsigned.ipa` 拖进去 → 填 Apple ID → Start
  3. 装到 iPhone 后，到「设置 → 通用 → VPN与设备管理」信任开发者证书
  4. ⚠️ 免费签名 **7 天过期**，需重签（Sideloadly 可开自动重签）
- **AltStore**（需常驻电脑）或 **爱思助手**（同原理）

**iPhone 侧**：iOS 17+ 需开启「设置 → 隐私与安全性 → 开发者模式」才能装非商店应用。

## 三、以后买了付费开发者账号（99$/年）怎么改真签名

1. 在 Apple Developer 后台生成：
   - 分发证书 `.p12`（导出时设密码）
   - Ad Hoc 描述文件 `.mobileprovision`（包含你 iPhone 的 UDID）
2. 把两个文件 base64 后存入仓库 Secrets：
   - `IOS_CERT_P12`、`IOS_CERT_P12_PASSWORD`、`IOS_PROVISION_PROFILE`
   - 再准备 `exportOptions.plist`（method: ad-hoc）
3. 在 workflow 里加「安装证书 + 签名 + exportArchive 导出真 ipa」步骤（当前 workflow 已留 archive 分支）

## 四、关键注意事项（踩坑记录）

- **Podfile 不要加 `use_frameworks!`** —— Kuikly 用静态链接，开启会 SIGABRT（官方 iosApp Podfile 就是注释掉的）
- **`generateDummyFramework` 必须最先跑** —— shared podspec 的 vendored_frameworks 指向 gradle 产物，不先生成 stub 则 pod install 的 linker flags 错误 → 运行时崩溃 `SharedKuiklyCoreEntry` 未链接
- iOS 入口是 **Objective-C**（`KuiklyRenderViewController.h/m`），不是 Swift
- Bundle ID 从 `Info.plist` 读取（当前 `com.chaijie.app`）
- iOS 默认页在 `AppDelegate.swift` 的 `KUIKLY_PAGE` 环境变量，默认 `HelloWorld`

## 五、本副本与原工程的差异

| 文件 | 原工程 ChaijieApp | iOS 副本 ChaijieApp-iOS |
|------|------------------|------------------------|
| Podfile | 保留 `use_frameworks!`（未验证 iOS） | 移除（符合 Kuikly 规范） |
| .github/workflows | 无 | ios-build.yml |
| .gitignore | 无 | 有（排除构建产物） |
| Git | 否 | 是（main 分支，已提交） |
