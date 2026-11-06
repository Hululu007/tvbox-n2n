# TKLauncher 接口、配置与接入说明

本文档汇总项目**全部接收接口**、请求携带的本机参数、加密规则、相对地址规则，并给出可运行的配置示例与 PHP 服务端 demo。

- 配置示例：[tvbox-config.example.jsonc](file:///f:/tvbox-n2n/docs/tvbox-config.example.jsonc)（带注释）、[tvbox-config.example.json](file:///f:/tvbox-n2n/docs/tvbox-config.example.json)（纯 JSON）
- PHP demo：[docs/php-demo/README.md](file:///f:/tvbox-n2n/docs/php-demo/README.md)
- 开发细节：[tvbox-dev.md](file:///f:/tvbox-n2n/docs/tvbox-dev.md)

---

## 1. 配置入口

主配置来自 JSON；点播界面右上角齿轮、直播界面「直播源配置」提供接口地址与手机扫码配置入口。
这些手动配置默认优先于主接口，除非主接口设置 `forceOverride`。编译前修改：

```java
// app/src/main/java/com/github/tvbox/osc/n2n/RemoteConfig.java
public static final String CONFIG_URL = "http://your-domain/php-demo/index.php?r=config&enc=ke";
```

支持的写法：

| 写法 | 说明 |
|------|------|
| `http://` / `https://` | 远程配置，后台拉取，断网用上次缓存 |
| `/sdcard/TKLauncher.app/config.json` | 设备本地文件（可加 `file:///`） |
| `assets/xxx.json` | APK 内资源（可选，默认不打包） |
| 留空 | 全部使用代码默认值 |

主配置里的 `remoteConfigUrl` 可选做二次远程覆盖；`useRemoteConfig` 仅在二次远程场景控制是否强制覆盖设置项。

---

## 2. 全部接收接口

| # | 接口 | 配置字段 | 可加密 | 相对地址 | 返回形态 |
|---|------|----------|--------|----------|----------|
| 1 | 主配置（远程/本地） | `CONFIG_URL` | ✅ | — | JSON / JSONC |
| 2 | 二次远程配置 | `remoteConfigUrl` | ✅ | ✅ | JSON |
| 3 | 点播源配置 | `api` | ✅ | —（主接口本身） | TVBox 标准 JSON |
| 4 | 直播源 | `live` | ✅ | ✅ | txt / m3u / JSON |
| 5 | 节目单 EPG | `epg` | ✅ | ✅ | JSON（支持多天） |
| 6 | 播放代理 | `proxy` | — | ✅ | 本地代理地址，不是服务器接口 |
| 7 | 自定义点播 | `entries`/`topEntries` 中 `type=vod` 的 `target` | ✅ | ✅ | TVBox 标准 JSON |
| 8 | 应用市场列表 | `appMarket.url` | ✅ | ✅ | JSON |
| 9 | 应用市场详情 | `appMarket.detailUrl` | ✅ | ✅ | JSON |
| 10 | 升级检查 | `upgrade.checkUrl` | ✅ | ✅ | JSON |
| 11 | 升级包下载 | `upgrade.url` | —（APK 二进制） | ✅* | APK 文件 |
| 12 | 应用市场 APK 下载 | 条目 `downloadUrl` | —（APK 二进制） | ✅* | APK 文件 |
| 13 | P2P 直播 | `tkp2p://...` | 由直播源决定 | — | P2P 协议 |
| 14 | 图片预设占位图 | `placeholderImage` | — | ✅ | 图片 URL |
| 15 | 界面布局插件 | `screens[].layoutUrl` | ✅ | ✅ | 布局 JSON |
| 16 | 界面独立接口 | `screens[].api/live/epg` | ✅ | ✅ | 同点播/直播/EPG |

> *下载地址建议直接写完整 URL；相对地址支持由服务端/客户端场景决定，**接口数据**字段优先使用完整地址更稳妥。

### 2.1 主配置 JSON 示例（最小可用）

```jsonc
{
  "api": "http://your-domain/index.php?r=api&enc=ke",
  "live": "index.php?r=live&enc=ke",          // 相对地址 → 自动拼主配置地址的域名+目录
  "epg": "index.php?r=epg&enc=ke",
  "proxy": "",
  "placeholderImage": "",                 // 图片下载完成前显示的预设占位图
  "forceOverride": false,                 // false=用户手动配置优先；true=主接口强制覆盖；
                                          // 也可对象逐项控制：{"api":true,"live":true,"epg":false,"proxy":false}
  "defaultScreen": "standard",            // standard=标准首页；其它值必须与下方 screens[].id 精确一致，不存在会自动回退
  "screens": [],                          // 酒店 IPTV 等桌面布局（见 2.1.1）
  "bootStart": true,
  "notice": {                             // 滚动公告：纯字符串=系统 marquee（短文本不滚）；对象=自定义滚动（短文本也会滚，推荐）
    "text": "欢迎使用 TKLauncher",
    "speed": 50,                          // 像素/秒，>0 才启用自定义滚动
    "pause": 8,                           // 整条公告完整走完一遍后停顿的秒数（>0 生效；只配 pause 时默认速度 100）
    "duration": 0,                        // 自动隐藏秒数，0=常驻
    "fontSize": 24,
    "textColor": "#FFFFFFFF",
    "bgColor": "#FF000000",
    "alpha": 100,
    "position": "bottom",                 // top / bottom（酒店 ticker 固定在布局内，不受此字段影响）
    "show": { "home": true, "live": true, "vod": true, "hotel": true }
  },
  "settings": { "homeRecStyle": true },
  "home": {
    "sourceName": "TKLauncher",          // 左上角显示的名称（主接口统一定义；空=显示 App 名称）
    "recMovies": [                        // 推荐影片（主接口提供）：首页推荐 + 点播界面「热播推荐」共用；
      { "name": "演示影片一", "pic": "https://placehold.co/300x400/png", "note": "9.0" }
      // 未配置时回退豆瓣热播；settings.homeRec=2 时使用观看历史
    ]
  },
  "appMarket": {
    "url": "index.php?r=market&category={category}&sort={sort}&page={page}&pageSize={pageSize}&keyword={keyword}&enc=ke",
    "detailUrl": "index.php?r=detail&pkg={packageName}&enc=ke",
    "pageSize": 24
  },
  "upgrade": {
    "checkUrl": "index.php?r=upgrade&enc=ke",
    "versionCode": 0,
    "url": ""
  }
}
```

### 2.1.1 界面 / 酒店 IPTV 桌面（`screens`）

```jsonc
"defaultScreen": "hotel_demo",
"screens": [
  {
    "id": "hotel_demo",
    "name": "酒店桌面",
    "layout": "hotel_grid",             // hotel_grid / hotel_list / hotel_showcase
    "api": "http://hotel.example.com/vod.json",   // 可选：本界面独立接口
    "live": "http://hotel.example.com/live.json",
    "epg": "http://hotel.example.com/epg.json",
    "title": "欢迎入住",
    "logo": "https://hotel.example.com/logo.png",
    "background": "https://hotel.example.com/bg.jpg",
    "ticker": "前台电话 8888    祝您入住愉快",
    "columns": 3,
    "rows": 2,
    "showClock": true,
    "layoutUrl": "",                    // 可选：布局插件 JSON 地址（按需下载并缓存本地）
    "entries": [
      { "name": "点播", "type": "page", "target": "vod",  "icon": "hm_vod" },
      { "name": "直播", "type": "page", "target": "live", "icon": "hm_live" },
      { "name": "切换界面", "type": "feature", "target": "screenSwitch", "icon": "hm_settings" }
    ]
  }
]
```

- `defaultScreen` 填不存在的 id 会自动回退 `standard`。
- `screens[].api/live/epg` 在该界面启动时应用；用户手动配置过且 `forceOverride=false` 时保留用户配置。
- **接口立即生效**：接口地址变化会立即删除点播接口磁盘缓存、清空 EPG 缓存并把 `ApiConfig` 标记为未加载，
  同时广播 `TYPE_ENDPOINT_CHANGE`，点播/直播/酒店界面立即重载，无需重启 App。
- `layoutUrl` 是布局插件 JSON 地址：仅覆盖外观与入口（`layout/title/logo/background/ticker/columns/rows/showClock/entries`），
  **接口地址请写在 `screens[]` 中**（插件加载时接口已应用）。
- `layoutUrl` 下载失败回退上次缓存，首次无缓存回退主接口内联配置。
- 滚动通知：`screens[].ticker` 优先；为空时回退顶层 `notice`（`notice.show.hotel` 控制是否显示）。
- 从零教程与完整 demo：[hotel-dev-guide.md](file:///f:/tvbox-n2n/docs/hotel-dev-guide.md)、`docs/demo/hotel/`。
- 更多示例（含“小白教程”）见 [tvbox-dev.md](file:///f:/tvbox-n2n/docs/tvbox-dev.md#11-小白教程新增一个界面--酒店-iptv-桌面布局)。

### 2.2 点播源（`api`）示例

```json
{
  "sites": [
    {
      "key": "demo",
      "name": "演示站点",
      "type": 0,
      "api": "https://your-cms.example.com/api.php/provide/vod/",
      "searchable": 1,
      "quickSearch": 1,
      "filter": 1
    }
  ],
  "lives": [
    { "name": "演示直播", "type": 0, "url": "http://your-domain/index.php?r=live", "playerType": 1 }
  ],
  "parses": []
}
```

### 2.3 直播源（`live`）示例

推荐 JSON 格式（客户端同时兼容传统 txt / m3u 文本源，按内容自动识别）：

```json
[
  {
    "group": "央视",
    "channels": [
      {
        "name": "CCTV-1 综合",
        "decode": "soft",
        "urls": [
          "https://example.com/cctv1.m3u8",
          { "url": "https://example.com/cctv1-2.m3u8", "name": "线路2", "decode": "hard" }
        ]
      },
      { "name": "CCTV-13 新闻", "urls": ["https://example.com/cctv13.m3u8"] }
    ]
  },
  {
    "group": "网页源",
    "channels": [
      { "name": "WebView 直播", "urls": ["webview://https://example.com/live/page.html"] },
      { "name": "Video 嗅探", "urls": ["video://https://example.com/live/video.html"] }
    ]
  },
  {
    "group": "P2P",
    "channels": [
      { "name": "TKP2P 频道", "urls": ["tkp2p://https://example.com/live/p2p.m3u8"] }
    ]
  }
]
```

- 顶层为分组数组（或 `{"groups":[...]}` 对象形式）；每个频道含 `name` 与 `urls`，`地址$线路名` 可为地址命名；
- **支持的节目类型（播放地址协议）**：`http(s)` 直链（m3u8/flv/mp4 等）、`rtmp://`、`rtsp://`、`webview://`（WebView 直接播网页）、`video://`（WebView 嗅探网页视频）、`tkp2p://`（内置 P2P）；
- 兼容的文本格式：`分组,#genre#` + `频道,url#url2`（txt）、`#EXTM3U` + `#EXTINF`（m3u）；`live` 直接填 `.txt` / `.m3u` 地址同样可用；
- **每频道/线路解码 `decode`（可选）**：取值 `soft`（软解）、`hard`（硬解）、`system`（系统）、`exo`、`ali`、`auto`（跟随全局）。
  - 写在频道对象上：该频道所有线路默认使用；
  - urls 写成 `[{ "url": "...", "name": "线路名", "decode": "hard" }]`：为单条线路设置独立解码；
  - 兼容 `playerType`（-1/0/1/2/3）及平行数组 `decodes`（如 `["soft","hard"]`）；
  - 客户端仅在用户未手动设置该线路解码时采用接口默认值；用户在播放中调整后始终以用户设置为准。
  - txt 文本源不携带 `decode`，需要逐频道/线路解码时请使用 JSON 格式。

### 2.4 EPG（`epg`）示例

请求：`epg?ch={频道名}&date={yyyy-MM-dd}&days={天数}`（`days` 缺省为 1，范围 1~9；也兼容 `name` 参数）。

```json
{
  "epg_data": [
    { "date": "2026-11-04", "start": "08:00", "end": "12:00", "title": "演示节目" },
    { "date": "2026-11-04", "start": "12:00", "end": "18:00", "title": "演示节目 2" },
    { "date": "2026-11-05", "start": "08:00", "end": "12:00", "title": "次日节目" }
  ]
}
```

- `start` / `end` 为 `HH:mm`，`title` 为节目名，`date` 为 `yyyy-MM-dd`；
- 单日服务端可不写 `date`（全部归到请求日期）；带 `date` 即多天格式，客户端按天拆分并缓存，配合 `days` 一次下发多天。

### 2.5 应用市场列表（`appMarket.url`）

模板占位符：`{category}`（all/app/game）、`{sort}`（recommend/download/update）、`{page}`、`{pageSize}`、`{keyword}`。

> TV 端搜索用屏幕键盘输入 A-Z/0-9（无需中文输入法），`{keyword}` 会 URL 编码后原样传给服务端。**按首字母搜索由服务端实现**：把应用名的拼音首字母（如 微信→WX）与关键词比较。PHP demo 演示了首字符/包含匹配，生产环境建议用拼音库（如 `overtrue/pinyin`）生成首字母后再匹配。

```json
{
  "data": {
    "total": 1,
    "page": 1,
    "pageSize": 24,
    "list": [
      {
        "packageName": "com.demo.player",
        "name": "Demo 播放器",
        "versionName": "1.0.0",
        "versionCode": 1,
        "icon": "https://placehold.co/180x180/png",
        "size": "12MB",
        "downloads": 12345,
        "category": "app",
        "summary": "演示应用",
        "description": "演示详情",
        "screenshots": [],
        "downloadUrl": "https://your-domain/demo-player.apk",
        "developer": "TKLauncher",
        "updateTime": "2026-01-01"
      }
    ]
  }
}
```

兼容根数组、`{"data":[...]}`、`{"list":[...]}`、`{"apps":[...]}`。

### 2.6 应用详情（`appMarket.detailUrl`）

占位符 `{packageName}`，返回单个应用对象（字段同上）；留空则直接使用列表字段。

### 2.7 升级检查（`upgrade.checkUrl`）

```json
{
  "versionCode": 120,
  "versionName": "1.2.0",
  "url": "https://your-domain/TKLauncher-1.2.0.apk",
  "force": false,
  "notes": "1. 修复切源刷新\n2. 应用市场 TV 搜索优化"
}
```

`versionCode` 大于客户端当前版本才提示；`force=true` 时不可取消。

### 2.8 自定义点播入口

```json
{
  "topEntries": [
    { "name": "影视A", "type": "vod", "target": "index.php?r=api&enc=ke" },
    { "name": "直播", "type": "page", "target": "live" },
    { "name": "系统设置", "type": "page", "target": "system" }
  ]
}
```

`setting` 页面入口已移除，配置为 `setting` 将被忽略。

**点播接口加载与切源行为**（主点播界面与自定义点播界面一致）：

- 点播接口（主配置 `api` 指向的 TVBox 标准 JSON / 自定义 `target`）只在**点播界面**加载；首页不再加载点播接口字段，也不提示「点播接口加载成功」，所有点播相关提示（免责声明、切源提示、加载失败等）仅在点播界面出现。
- 进入点播界面时先显示启动等待画面（主配置可下发 `vodSplash`，见 §2.9），接口配置与当前源的 jar/js/py 插件就绪后自动加载分类数据并关闭等待画面。
- 点播界面切换源时，会丢弃该源旧的 Spider 实例并**重新加载新源对应的 jar / js / py 插件**（`type=3` 的 `.jar`、`.js`、`.py`、纯类名站点分别处理），再强制刷新分类列表；不再依赖首页预加载全局 jar。
- 点播界面的第一个分类固定为「热播推荐」：**只取主配置 `home.recMovies`**（未配置时显示空态，不再回退点播接口 `homeContent` 或豆瓣）；默认选中并自动加载。
- 若主接口（主配置）无法加载/访问且本地无缓存，客户端直接显示全屏报错页（重试/退出），不再进入首页等其它界面。

### 2.9 点播启动等待画面（`vodSplash`）

主配置顶层（或 `home` 内）可选字段，点播界面加载接口/切换源时展示：

```json
{
  "vodSplash": {
    "image": "./splash.png",        // 可选：背景图，相对地址按主配置地址解析
    "text": "正在加载点播接口…",      // 可选：文案，缺省用内置文案
    "backgroundColor": "#FF0D0D12", // 可选：背景色（#RRGGBB / #AARRGGBB）
    "duration": 0                   // 可选：最短展示毫秒数 0~5000，0=加载完成立即关闭
  }
}
```

---

## 3. 本机参数（所有请求）

### 3.1 请求头（OkGo 全局）

```
X-Device-Mac     例：A4:56:02:1B:2C:3D
X-Device-Ip      例：192.168.1.20
X-OS-Version     例：14 (API 34)
X-App-Version    例：1.0.0
```

### 3.2 URL 参数（自有接口额外携带）

```
mac / ip / model / brand / device / sdk / release / app
```

已存在同名参数不会重复添加；`assets/`、`file://` 等本地地址不拼接。

PHP 读取：

```php
$mac = $_GET['mac'] ?? ($_SERVER['HTTP_X_DEVICE_MAC'] ?? '');
```

---

## 4. 加密下发

所有接收接口都支持密文。客户端 `ConfigCrypto.maybeDecrypt` 自动识别：

- 明文：去空格后以 `{`、`[`、`/` 开头，或含 `#EXTM3U` / `#genre#`（EPG 已统一为 JSON；`<` 仅兼容旧 XMLTV）；
- `2423` 前缀：TVBox 原生 CBC，不在本系统处理；
- ke 前缀模式：19 位小写 hex 密钥 + AES-128-ECB hex；总长奇数且 ≥ 51；
- 固定密钥模式：AES-128-ECB hex；总长偶数且 ≥ 32；密钥取 `secretKey`（JSON）或 `ConfigCrypto.SECRET_KEY`。

服务端 PHP（详见 [tvbox_crypto.php](file:///f:/tvbox-n2n/docs/php-demo/tvbox_crypto.php)）：

```php
$key = md5(time() . "AD80F93B542B" . "8b14ecf6ae028f9b6d25fa547eabad85");
$ke  = substr($key, 13);
echo $ke . bin2hex(openssl_encrypt($json, 'AES-128-ECB', substr($ke, 0, 16), OPENSSL_RAW_DATA));
```

固定密钥：

```php
echo bin2hex(openssl_encrypt($json, 'AES-128-ECB', substr($secret, 0, 16), OPENSSL_RAW_DATA));
```

---

## 5. 相对地址规则

`live` / `epg` / `proxy` / `upgrade.checkUrl` / `appMarket.url` / `appMarket.detailUrl` / 自定义点播 `target` 未写协议时，**优先以主配置来源地址（`CONFIG_URL` / `remoteConfigUrl` 的实际拉取地址）的协议 + 域名 + 目录拼接**；配置来自本地文件/assets 时回退以主接口 `api` 的协议 + 域名 + 目录拼接。

例：主配置地址 `CONFIG_URL = http://1.2.3.4/php-demo/index.php?r=config`：

| 填写 | 解析结果 |
|------|----------|
| `/live.txt` | `http://1.2.3.4/live.txt` |
| `live.txt` | `http://1.2.3.4/php-demo/live.txt` |
| `index.php?r=live` | `http://1.2.3.4/php-demo/index.php?r=live` |
| `https://cdn.example.com/live.txt` | 原样 |
| `clan://...` / `assets/...` / `file://...` | 原样 |

配置为本地文件/assets 时以上规则不变，仅基准地址换成主接口 `api` 的协议 + 域名 + 目录。

### 5.1 点播接口内部的相对地址

点播接口（TVBox 标准 JSON）里的 `spider` / 站点 `api` / `jar` / `playUrl` / `ext` 同样支持相对地址，**以该点播接口自身的 URL 目录为基准**（不是主配置目录）：

| 填写（接口地址为 `http://cdn.example.com/vod/api.php`） | 解析结果 |
|------|----------|
| `./kjtw.jar` | `http://cdn.example.com/vod/kjtw.jar` |
| `./js/旺旺.js` | `http://cdn.example.com/vod/js/旺旺.js` |
| `./py/周六影库.py` | `http://cdn.example.com/vod/py/周六影库.py` |
| `/files/x.jar` | `http://cdn.example.com/files/x.jar` |
| `http://...` / `https://...` / `clan://` / `assets/...` / `file://...` | 原样 |

`ext` 为 JSON 字符串时，其中的 `"./xxx.json"` 会按同一基准批量替换；`jar`/`api` 带 `;md5;` 后缀时只解析地址部分。

各类源的插件加载规则（切源时会重新加载对应插件）：

| 站点类型 | `api` 形态 | 加载方式 |
|------|------|------|
| 0 / 1 / 4 | http(s) 接口 | XML / JSON / JSON 扩展筛选，无需插件 |
| 3 | `*.jar` 或纯类名 `csp_xxx` | JarLoader（Java 插件）；纯类名站点用接口级 `spider` jar |
| 3 | `*.js` / `*.js?...` | QuickJS 脚本插件 |
| 3 | `*.py` | Python 插件（仅 python flavor APK） |

---

## 6. PHP demo 快速接入

1. 上传 `docs/php-demo/` 到服务器；
2. 访问 `https://your-domain/php-demo/index.php?r=config&enc=ke` 确认返回密文字符串；
3. 把该地址填入 `RemoteConfig.CONFIG_URL`；
4. 重新编译安装，App 启动后自动加载配置；
5. 联调各接口：`?r=api`、`?r=live`、`?r=epg`、`?r=market`、`?r=upgrade`、`?r=crypto`。

部署与加密细节见 [PHP demo 说明](file:///f:/tvbox-n2n/docs/php-demo/README.md)。
