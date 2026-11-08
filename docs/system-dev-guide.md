# 系统从零开发制作完整指南（TVBox / TKLauncher）

本文从一台空电脑开始，讲清如何把本项目跑起来、改配置、加功能、出安装包并发布。
适用于需要二次开发酒店桌面、做定制固件配套 App、或从零搭建同类 TV 桌面系统的团队。

> 配套文档：
> [酒店界面与布局插件从零开发](./hotel-dev-guide.md) ·
> [完整使用说明](./usage.md) ·
> [接口与配置字段](./api-and-config.md) ·
> [TVBox 开发文档](./tvbox-dev.md)

---

## 1. 系统组成

```
TVBox/
├── app/            主应用（Java + 少量 Kotlin，Android 4.4 ~ 14）
│   ├── src/main/java/com/github/tvbox/osc/
│   │   ├── api/           点播接口解析、站点/线路/播放地址
│   │   ├── n2n/           远程配置、酒店桌面、公告、入口分发
│   │   ├── player/        播放器（系统 / IJK / Exo / Ali / MX）
│   │   ├── ui/            Activity / Fragment / Dialog
│   │   ├── server/        手机遥控、推送、配置下发
│   │   └── util/          工具（直播源解析、EPG 缓存、WebView 播放等）
│   ├── src/main/assets/   内置配置、tkp2p 页面、JS
│   └── src/main/res/      主题、布局、图标
├── quickjs/        JS 源解析引擎
├── pyramid/        Python 源解析引擎（Chaquopy）
├── docs/          全部文档 + php-demo + 酒店 demo
└── .github/       CI
```

核心运行链路：

```
CONFIG_URL（远程主配置）
  → RemoteConfig.bootstrap/apply   写 Hawk
  → HomeActivity / HotelActivity  读取 screens/defaultScreen 决定首屏
  → ApiConfig.loadConfig           解析点播接口（文件缓存 MD5(接口地址)）
  → LivePlayActivity              解析直播源 + EPG 缓存
  → PlayActivity                  播放（含 webview:// / video:// / tkp2p://）
```

---

## 2. 环境准备（Windows / macOS / Linux）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | **17** | Gradle 7.5 不支持 JDK 18+，不要用 JDK 21/27 |
| Gradle | 7.5 | 项目自带 `gradlew`，无需单独安装 |
| Android SDK Platform | **34** | `compileSdk 34` |
| Build-Tools | 30.0.3+ | AGP 7.4.2 默认 |
| Android Studio | 2022.3+（可选） | 只做开发调试时更方便 |
| Python | 3.8+（可选） | 仅 `python` 变体 / 源解析调试 |

### 2.1 配置 SDK

新建 `local.properties`：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

首次构建若提示缺少 `platforms;android-34`，用 Android Studio 的 SDK Manager 安装
（**Android 14 / API 34**）以及 `build-tools;30.0.3`。

### 2.2 检查环境

```bash
java -version          # 必须是 17.x
./gradlew -v
```

---

## 3. 从零跑起来

### 3.1 编译

```bash
# 只检查 Java 编译（最快，开发时常用）
./gradlew :app:compileGenericNormalDebugJavaWithJavac

# 生成安装包（32 位 + 64 位）
./gradlew :app:assembleGenericNormalDebug
```

产物：

```
app/build/outputs/apk/genericNormal/debug/
  app-generic-normal-debug-armeabi-v7a-*.apk
  app-generic-normal-debug-arm64-v8a-*.apk
```

`python` 变体：

```bash
./gradlew :app:assembleGenericPythonDebug
```

### 3.2 安装与首启

```bash
adb install -r app/build/outputs/apk/genericNormal/debug/xxx.apk
adb shell am start -n tklauncher.app/com.github.tvbox.osc.ui.activity.HomeActivity
```

首次启动会按 `RemoteConfig.CONFIG_URL` 拉取主配置；失败则使用内置/本地配置。

### 3.3 最快的联调方式（不改代码）

1. 本地起 PHP：

```bash
cd docs/php-demo
php -S 0.0.0.0:8080
```

2. 手机/电视与电脑同一局域网，访问 `http://电脑IP:8080/index.php?r=config` 确认可打开。
3. 把源码里的 `CONFIG_URL` 改成该地址，重新打包。
4. 之后改 PHP 或 JSON 即可热更新，无需重新安装（配置检查间隔默认 30 分钟，
   也可回到首页触发刷新或重启 App）。

---

## 4. 配置体系

### 4.1 配置来源优先级

1. 远程主配置（`CONFIG_URL`）
2. 上次成功拉取的远程配置缓存（断网可用）
3. `assets` 内置配置 / 本地配置

接口地址支持：

- 完整 `http(s)://...`
- 相对地址：自动拼接主配置所在目录
- 加密：`enc=ke` / `enc=fixed`（见 `docs/php-demo/tvbox_crypto.php`）

### 4.2 主配置模块

| 段 | 作用 |
| --- | --- |
| `api` / `live` / `epg` / `proxy` | 各接口地址 |
| `notice` | 首页/直播/点播/酒店滚动通知（`speed` / `pause` …） |
| `home` | 首页背景、名称、入口样式、推荐影片 |
| `settings` | 播放器、搜索、主题、语言等全部开关 |
| `themes` | 内置主题覆盖 / 自定义主题 |
| `screens` | 酒店 IPTV 桌面（每屏独立接口 + 布局插件） |
| `entries` / `topEntries` / `homeEntries` | 自定义入口 |
| `appMarket` | 应用市场接口 |
| `upgrade` | 在线升级 |
| `tkp2p` | P2P 直播引擎参数 |
| `donate` / `hin2n` | 捐助页、组网 App 下载地址 |

字段逐项说明见 [api-and-config.md](./api-and-config.md)，
完整可运行示例见 [php-demo/index.php](./php-demo/index.php)。

### 4.3 配置立即生效机制（本次新增）

修改「点播 / 直播 / EPG」接口后，不再需要重启 App：

1. 接口地址变化时删除点播缓存 `filesDir/<md5(旧接口)>` 与新接口同名旧缓存；
2. 清空 EPG 缓存；
3. `ApiConfig.loaded = false`，下次进入点播重新解析；
4. 广播 `RefreshEvent.TYPE_ENDPOINT_CHANGE`：
   - `VodActivity` → 立即重载新接口
   - `LivePlayActivity` → 重载直播源 / EPG
   - `HotelActivity` → 重新读取屏幕配置并重绘

入口覆盖：App 内设置弹窗、手机遥控下发、主配置 `screens[]` 切换三条路径都走同一套逻辑。

---

## 5. 播放体系

### 5.1 直播协议

| 协议 | 处理 |
| --- | --- |
| `http(s)://*.m3u8/flv/mp4` | IJK / Exo / 系统播放器 |
| `rtmp://` `rtsp://` | IJK / 系统播放器 |
| `webview://` | WebView 加载网页，由页面自带播放器播放 |
| `video://` | WebView 打开页面，自动嗅探 `<video>` 视频流 |
| `tkp2p://` | 内置 P2P 页面（`assets/tkp2p`，参数由主配置 `tkp2p` 段下发） |

### 5.2 点播网页源（本次新增）

点播播放地址若为以下协议，会自动切换到 WebView 播放层：

- `webview://https://...`：直接打开网页播放
- `video://https://...`：网页视频嗅探
- `tkp2p://https://...`：P2P 播放

实现要点：

- `PlayActivity.startPlayUrl()` 识别协议 → 复用 `LiveWebViewPlayer`，挂到播放页根布局；
- 返回键优先退出 H5 全屏，再退出播放页；
- 播放页销毁时同步销毁 WebView，避免内存泄漏；
- 播放地址切换为非网页源时自动移除 WebView 层。

因此，自定义源（Spider）只要返回 `webview://` / `video://` / `tkp2p://` 地址即可，
无需再单独实现播放器。

### 5.3 播放器选择

- 全局默认：`settings.playType`（0=系统 1=IJK 2=Exo 3=MX）
- 点播首选：`settings.vodPlayerPreferred`
- 直播线路独立解码：直播 JSON 频道/线路上的 `decode`（soft/hard/system/exo/ali/auto）

---

## 6. 界面与主题

| 界面 | 说明 |
| --- | --- |
| `HomeActivity` | 标准首页（推荐、历史、顶栏入口） |
| `HotelActivity` | 酒店 IPTV 桌面（screens / 布局插件） |
| `VodActivity` | 点播分类/列表 |
| `CustomVodActivity` | 自定义点播入口（可独立主题） |
| `LivePlayActivity` | 直播播放 + 频道列表 + EPG |
| `PlayActivity` | 点播播放页（含网页源） |
| `SearchActivity` / `FastSearchActivity` | 搜索 / 聚合搜索 |
| `AppsActivity` | 已安装应用 |

主题来源：`themes` 段；支持“索引覆盖内置主题”与“追加自定义主题”。
入口可单独指定 `theme`，退出时自动恢复全局主题。

滚动通知实现：`n2n/NoticeBar.java`

- `speed > 0`：自定义像素速度滚动；只保留一份文本，从右向左完整走一遍，短文本不会追加副本
- `pause > 0`：整条通知完整走完一遍后停顿 N 秒再走下一遍（`0`=立即下一遍）
- 酒店界面：`screens[].ticker` 优先，空则回退全局 `notice`

---

## 7. 调试

### 7.1 日志

```bash
adb logcat -s TVBox:* RemoteConfig:* HotelActivity:* NoticeBar:*
adb logcat | grep -iE "hotel|screens|endpoint|notice|tkp2p"
```

### 7.2 WebView 调试

代码已开启 WebView 调试（debug 包）。Chrome 打开 `chrome://inspect` 即可调试
`webview://` / `video://` / `tkp2p://` 页面。

### 7.3 配置不生效排查

1. `CONFIG_URL` 是否可访问、返回是否为合法 JSON；
2. 是否命中 `_manual` 手动配置保护（主配置加 `"forceOverride": true`）；
3. 是否被缓存：改 URL 加 `?v=时间戳` 或清除 App 数据；
4. 接口地址变化后会立即清缓存；若仍未更新，看 logcat 是否有 `TYPE_ENDPOINT_CHANGE`；
5. 酒店布局插件：确认 `layoutUrl` 可访问、JSON 合法、没有被旧缓存命中。

### 7.4 真机常见问题

| 现象 | 处理 |
| --- | --- |
| 安装失败 `INSTALL_FAILED_OLDER_SDK` | 选 `normal` 变体（minSdk 19）或升级设备 |
| Android 4.4 闪退 | 必须使用 `normal` 变体（矢量图 PNG 兜底） |
| 网页源黑屏 | 检查 WebView 版本、是否允许 cleartext HTTP、页面是否自动播放 |
| 组播直播无法播放 | 酒店交换机开启 IGMP，确认播放器/代理支持 udp/rtp |
| 应用无法自动安装 | 系统限制 `REQUEST_INSTALL_PACKAGES`，需固件放开或预装 |

---

## 8. 新增功能的标准流程

以“新增一个配置字段 + 界面效果 + 文档 + PHP demo”为例：

1. **定义字段**：在 `RemoteConfig` 顶部注释与 `apply()` 中解析，写入 `HawkConfig`；
2. **界面消费**：在对应 Activity/Fragment 读取（注意主线程与空值兜底）；
3. **兼容旧配置**：字段缺省时保持原行为；
4. **配置示例**：同步 `docs/tvbox-config.example.jsonc`、`docs/php-demo/index.php`；
5. **文档**：更新 `usage.md` / `api-and-config.md` / 相关专题文档；
6. **编译自检**：

```bash
./gradlew :app:compileGenericNormalDebugJavaWithJavac
```

7. **真机验证**：至少覆盖首页、直播、点播、酒店桌面四条链路；
8. **提交**：一个功能一个 commit，说明“为什么改”。

### 8.1 新增酒店布局

见 [hotel-dev-guide.md 第 10 节](./hotel-dev-guide.md#10-二次开发新增一个原生布局)。

### 8.2 新增入口类型

1. `EntryDispatcher.open()` 增加分支；
2. `RemoteConfig.Entry` 增加字段（如需）；
3. 酒店/首页入口渲染无需改动（统一走 `createTile` / `EntryDispatcher`）。

---

## 9. 打包与发布

### 9.1 签名

1. 准备 keystore（仓库 `docs/TVBoxOSC.jks` 仅为示例，生产请自建并保密）；
2. 在 `app/build.gradle` 的 `signingConfigs` 中配置，或在 CI 中通过环境变量注入。

### 9.2 变体选择

| 变体 | 用途 |
| --- | --- |
| `genericNormal` | 通用、兼容 Android 4.4，最常用 |
| `genericPython` | 需要 Python 源解析 |
| `hisenseNormal` / `hisensePython` | 海信定制 |

### 9.3 CI

仓库已有 `.github/workflows/test.yml`。发布时建议：

```bash
./gradlew clean :app:assembleGenericNormalRelease
```

并把 APK 上传到 Release / 自有 CDN，再通过主配置 `upgrade` 段下发版本号与下载地址。

### 9.4 灰度与回滚

- 配置与 APK 分离：先改 JSON 验证，再发 APK；
- 保留上一版 APK 下载地址；
- 接口异常时可让 `api/live/epg` 回退到备用地址，客户端会立即清缓存重载。

---

## 10. 安全与运维建议

1. 生产配置使用 `enc=ke` 或 `enc=fixed` 加密，避免接口被直接抓取；
2. 管理后台/php-demo 不要暴露默认密码；
3. `forceOverride` 上线后建议关闭，允许现场微调；
4. 定期清理 `filesDir` 下的旧点播缓存与 `epg_cache`；
5. 酒店多门店用 `screens[]` 区分，不要复制多套 APK；
6. 滚动通知涉及消防/应急文案时，建议 `duration=0` 常驻并保留 `speed/pause` 可读性。

---

## 11. 常见问题（FAQ）

**Q：为什么必须用 JDK 17？**
A：项目使用 Gradle 7.5，官方支持最高 JDK 17；JDK 21/27 会直接报 “Unsupported class file major version”。

**Q：`compileSdk 34` 但电视是 Android 4.4，能装吗？**
A：能。`compileSdk` 只影响编译；实际最低版本由 `minSdk 19` 决定，请使用 `normal` 变体。

**Q：改了接口一定要重启吗？**
A：不需要。本次修复后，接口地址变化会立即清缓存并通知界面重载。

**Q：点播地址是网页怎么办？**
A：返回 `webview://`、`video://` 或 `tkp2p://` 地址即可，播放页会自动使用 WebView 播放层。

**Q：酒店桌面如何换肤？**
A：改 `screens[].layoutUrl` 指向的 JSON，客户端会重新下载并缓存，无需发版。

**Q：滚动通知如何“走完停一会”？**
A：`notice.pause` 设为秒数；`speed` 控制滚动速度。

---

## 12. 目录速查

| 需求 | 文件 |
| --- | --- |
| 主配置常量 / 解析 | `app/src/main/java/com/github/tvbox/osc/n2n/RemoteConfig.java` |
| 通知栏滚动 | `app/src/main/java/com/github/tvbox/osc/n2n/NoticeBar.java` |
| 酒店桌面 | `app/src/main/java/com/github/tvbox/osc/ui/activity/HotelActivity.java` |
| 入口分发 | `app/src/main/java/com/github/tvbox/osc/n2n/EntryDispatcher.java` |
| 接口缓存清理 | `app/src/main/java/com/github/tvbox/osc/n2n/EndpointCache.java` |
| 点播播放页 | `app/src/main/java/com/github/tvbox/osc/ui/activity/PlayActivity.java` |
| 网页/P2P 播放 | `app/src/main/java/com/github/tvbox/osc/util/live/LiveWebViewPlayer.java` |
| 直播页 | `app/src/main/java/com/github/tvbox/osc/ui/activity/LivePlayActivity.java` |
| 配置示例 | `docs/php-demo/index.php`、`docs/tvbox-config.example.jsonc` |
| 酒店 demo | `docs/demo/hotel/` |
