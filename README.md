# 轻级浏览器（QingJi Browser）

一款功能对标 Chrome 的 Android 浏览器，使用 Kotlin + Jetpack Compose（Material 3）开发，UI 完整适配最新 Android 版本。

- **应用名称**：轻级浏览器
- **包名**：`com.lightbrowser`
- **minSdk 29（Android 10）/ targetSdk 36（Android 16）/ compileSdk 36**
- **当前版本**：v1.0.3（versionCode 4）

## 下载

安装包（APK，debug 签名，可直接安装）见 [Releases](../../releases) 页面；也可以按下文指引从源码自行构建。

## 功能一览（对标 Chrome）

| 模块 | 功能 |
| --- | --- |
| 多标签 | 网格标签切换器、新建/关闭/全部关闭、会话持久化（重启恢复）、`target=_blank` 自动开新标签 |
| 地址栏 | Chrome 风格圆角地址栏，URL/搜索词智能识别，加载进度条，停止/刷新 |
| 搜索联想 | 百度/必应在线联想 + 本地历史/书签联想（可切换搜索引擎：百度/必应/谷歌/DuckDuckGo） |
| 无痕模式 | 无痕标签不记历史、禁用缓存，标签切换器深色卡片标识 |
| 书签 | 添加/移除（菜单星标）、列表管理、点击直达 |
| 历史记录 | 按「今天/昨天/日期」分组、单条删除、一键清空 |
| 下载 | 系统 DownloadManager 接管，下载页实时进度轮询、点击打开文件 |
| 主页 | Logo + 搜索框 + 常用网站速拨（自动取最常访问，不足补预设站点） |
| 网页能力 | 页内查找（匹配计数/上一个/下一个）、长按链接/图片菜单、桌面版 UA 切换、文字缩放 50%–200% |
| 媒体与硬件 | 视频全屏（沉浸式）、文件上传、摄像头/麦克风/定位权限桥接 |
| 安全隐私 | 广告域名拦截（hosts 规则）、HTTPS 锁标识、SSL 证书错误确认弹窗、第三方 Cookie 开关、退出时清数据 |
| 其它 | 分享、复制链接、`intent://` 外链跳转 App、JS alert/confirm/prompt 对话框、渲染进程崩溃自动恢复 |

## Android 版本适配

- **Material You 动态取色**：Android 12+ 跟随壁纸配色，可关闭
- **边到边（Edge-to-Edge）**：状态栏/导航栏沉浸式，图标颜色随深浅主题切换
- **预测性返回手势**：`enableOnBackInvokedCallback`，配合 Navigation 转场
- **SplashScreen API**：启动画面
- **网页深色模式**：`WebSettingsCompat` 算法级网页调暗
- **自适应图标 + 单色图标**：适配 Android 13+ 主题图标

## 技术栈

Kotlin 2.1 · Jetpack Compose（Material 3, BOM 2025.06）· Navigation Compose · Room（书签/历史/标签持久化）· DataStore（设置）· Android System WebView + androidx.webkit · Coroutines/Flow · AGP 8.11 / Gradle 8.13

## 源码构建

要求：JDK 17、Android SDK（platform android-36 + build-tools）。

```bash
# 方式一：Android Studio Hedgehog 及以上直接打开本仓库根目录
# 方式二：命令行
./gradlew :app:assembleDebug
# 若提示缺少 gradle-wrapper.jar（本仓库未提交该二进制文件）：
# 先执行一次 gradle wrapper 生成，或直接使用本机 Gradle 8.13：gradle :app:assembleDebug
```

构建产物：`app/build/outputs/apk/debug/app-debug.apk`

## 目录结构

```
app/src/main/java/com/lightbrowser/
├── BrowserApplication.kt      # 应用入口：广告拦截预载、WebView 调试开关
├── MainActivity.kt            # 单 Activity：导航、权限桥接、文件选择、视频全屏
├── browser/
│   ├── TabManager.kt          # 多标签管理 + WebView 配置/生命周期 + WebViewClient/ChromeClient
│   ├── BrowserTab.kt          # 标签页状态模型（Compose State）
│   ├── AdBlocker.kt           # 域名后缀匹配广告拦截
│   ├── SearchEngines.kt       # 搜索引擎定义（百度/必应/谷歌/DuckDuckGo）
│   ├── SuggestionFetcher.kt   # 在线搜索联想
│   ├── DownloadHelper.kt      # 系统下载接管
│   └── WebCallbacks.kt        # 需要 Activity 能力的回调接口
├── data/
│   ├── Entities.kt / Daos.kt / AppDatabase.kt   # Room：书签/历史/打开的标签
│   └── SettingsRepository.kt                    # DataStore 设置
└── ui/
    ├── BrowserScreen.kt       # 浏览器主界面（地址栏/内容区/查找栏/菜单）
    ├── TabSwitcherScreen.kt   # 标签页网格切换器
    ├── HomeContent.kt         # 新标签页主页
    ├── BookmarksScreen.kt / HistoryScreen.kt / DownloadsScreen.kt / SettingsScreen.kt
    ├── BrowserViewModel.kt    # 联想查询、书签/历史状态
    └── theme/Theme.kt         # Material You 主题
```

## 版本记录

### v1.0.3（2026-09-14）
- 修复：地址栏输入时联想列表导致应用闪退。原因：历史记录中存在多条相同标题/网址的记录时，联想结果出现重复文本，建议列表 LazyColumn 的内容 key 冲突抛出异常。修复：联想结果按展示文本去重，建议列表不再使用内容 key。
- 优化：点击搜索框进入编辑态的过渡体验。顶栏背景在编辑态下与页面同色（不再出现突兀的方形色块）并带颜色过渡动画；地址栏内容切换增加淡入淡出动画；搜索建议面板改为始终覆盖编辑态（无建议时显示空白页底）并带淡入+展开动画。

### v1.0.2（2026-09-13）
- 修复：部分网页渲染异常（页面只显示一半高度、下方大片空白），典型如 QQ 官网等依赖视口高度布局的页面。原因：输入网址时软键盘压缩 WebView 容器高度，键盘收起后部分机型（MIUI/HyperOS 等）上 WebView 不跟随容器恢复尺寸。修复：监听网页容器尺寸变化并强制 WebView 按新尺寸重排；固定 WebView 为 MATCH_PARENT 布局参数。

### v1.0.1（2026-09-13）
- 修复：顶部地址栏在部分机型上不可见。原因：主页在 Compose 界面下层挂载了空白 WebView 原生视图，部分系统上该视图越界绘制遮挡顶栏。修复：主页不再创建/挂载 WebView；浏览网页时 WebView 在 AndroidView 工厂回调中创建；内容区与 WebView 容器增加边界裁剪；切换标签页按标签 ID 强制重建 WebView 容器。

### v1.0.0（2026-09-13）
- 首个版本，功能对标 Chrome。

## 已知限制

- 无痕标签的 Cookie 与常规标签共享存储（Android WebView 全局 CookieManager 限制），无痕仅保证不写入历史、不持久化缓存；
- `blob:` / `data:` 协议的网页内下载暂不支持；
- 广告拦截为内置域名列表，非完整 EasyList 规则。
