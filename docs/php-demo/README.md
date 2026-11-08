# TKLauncher PHP 接口 demo 说明

本目录是一个可直接运行的单文件 PHP 接口示例，覆盖 TKLauncher 的全部接收接口，并演示本机参数与加密下发。

## 1. 环境要求

- PHP 7.4+（7.4 / 8.x 均可）
- 开启 `openssl` 扩展（`php -m | grep openssl`）
- `mbstring` 可选：未安装时 demo 自动退化为字节级小写/UTF-8 首字符截取，应用市场首字母搜索仍可使用

## 2. 部署

把整个 `php-demo` 目录上传到 Web 服务器（Apache / Nginx / php-fpm 均可，无需 rewrite）：

```
https://your-domain/php-demo/index.php
```

修改 App 源码：

[RemoteConfig.java](file:///f:/tvbox-n2n/app/src/main/java/com/github/tvbox/osc/n2n/RemoteConfig.java)

```java
public static final String CONFIG_URL = "http://your-domain/php-demo/index.php?r=config&enc=ke";
```

- `enc=ke`：ke 前缀密文（推荐，无需配置 secretKey，密钥随密文携带）
- `enc=fixed`：固定密钥密文，需 `TK_SECRET_KEY` 环境变量或 `tvbox_crypto.php` 中的秘钥与客户端一致（客户端填 `secretKey`）
- 不带 `enc`：明文直出

## 3. 路由

| URL | 对应 App 配置字段 | 说明 |
|-----|------------------|------|
| `index.php?r=config` | `CONFIG_URL` | 远程主配置 |
| `index.php?r=api` | `api` | 点播源配置（TVBox 标准 JSON） |
| `index.php?r=live` | `live` | 直播源 JSON（可选 `format=txt`；客户端兼容 txt/m3u） |
| `index.php?r=live&format=txt` | `live` | 直播源传统 txt 文本（`分组,#genre#` / `频道,url#url2`） |
| `index.php?r=epg&ch={频道名}&date={yyyy-MM-dd}&days={天数}` | `epg` | 节目单 JSON（支持一次返回多天） |
| `index.php?r=market&category={category}&sort={sort}&page={page}&pageSize={pageSize}&keyword={keyword}` | `appMarket.url` | 应用市场列表 |
| `index.php?r=detail&pkg={packageName}` | `appMarket.detailUrl` | 应用详情 |
| `index.php?r=upgrade` | `upgrade.checkUrl` | 升级检查 |
| `index.php?r=crypto` | — | 明文/ke/固定密钥三种形态联调 |
| `tvbox_crypto.php` | — | 加密工具函数与演示 |

主配置里的接口地址使用**相对地址**（如 `index.php?r=api&enc=ke`），客户端会自动以**主配置 URL 的域名/IP + 所在目录**拼接（配置来自本地文件时回退拼接主接口 api 域名），无需写死域名。

`r=config` 返回完整系统配置：接口地址、`epgDays`、`settings` 全量设置项、首页 `home`、`themes`、`tkp2p`、`topEntries`/`homeEntries`、应用市场、升级与捐助等，字段含义与 [配置示例](file:///f:/tvbox-n2n/docs/tvbox-config.example.jsonc) 一致。

### 3.0 滚动公告（`notice`）

`notice` 字段支持两种写法：

| 写法 | 示例 | 行为 |
|------|------|------|
| 纯字符串 | `"notice": "欢迎使用 TKLauncher"` | 走 Android 系统 marquee；**短于一屏的文本不会滚**（Android 固有行为） |
| 对象（推荐） | 见下方 | 走客户端自定义滚动，**短文本也会滚** |

对象写法：

```json
{
  "notice": {
    "text": "欢迎使用 TKLauncher · 前台电话 xxx-xxxxxxx",
    "speed": 50,
    "pause": 8,
    "textColor": "#FFFFFFFF",
    "bgColor": "#FF000000"
  }
}
```

- `text`（必填）：公告内容；
- `speed`（>0）：滚动速度，单位像素/秒；为 0 或缺失时回退系统 marquee；
- `pause`：整条公告完整走完后停顿的秒数（0=立即走下一遍；只配 `pause` 不配 `speed` 时默认速度 100）；
- `duration` / `fontSize` / `alpha` / `position` / `show`（可选）：自动隐藏秒数、字号、透明度、位置、按首页/直播/点播分别开关；
- `textColor` / `bgColor`（可选）：颜色 `#RRGGBB` 或 `#AARRGGBB`。

**单份文本滚动**：启用 `speed` 后客户端只保留一份文本，先从屏幕右侧外进入，向左完整走出屏幕（含 TextView 内边距）后按 `pause` 停顿，再开始下一遍；短文本不会再重复拼接或同时出现多份副本。

**显示层级（2026-11 起）**：客户端首次运行会引导开启「悬浮窗」权限（`SYSTEM_ALERT_WINDOW`）。授权后公告由前台服务 `NoticeOverlayService` 挂到系统 `WindowManager`（`TYPE_APPLICATION_OVERLAY`）：

- 显示在所有 App 最顶层，切换 TVBox 内部界面或切到其它 App 都不中断；
- 全局只有一个公告 View，切换界面/应用不会重新创建、不会回到起点、不会重新计时；
- 未授权时自动退化为「挂在当前窗口 decorView 最顶层」，同一份 View 跨 Activity 搬移，同样保持滚动状态；
- 公告为空时服务自动停止，不额外占用通知栏。

### 3.0.1 应用推荐（`home.appRecommends`）

`home` 段新增 `appRecommends` 数组：显示在首页推荐影片列表上方，标题固定为「应用推荐」，自动换行，卡片尺寸与推荐海报卡片一致（跟随 `home.recMovies` 使用的 `ImgUtil.Style`）。数组为空/不配置时整块隐藏。

```json
{
  "home": {
    "appRecommends": [
      {
        "name": "示例应用",
        "icon": "https://example.com/icon.png",
        "pkg": "com.example.app",
        "url": "https://example.com/app.apk",
        "desc": "推荐"
      }
    ]
  }
}
```

| 字段 | 说明 |
|------|------|
| `name` | 显示名称（必填；留空时用 `pkg` 兜底） |
| `icon` | 图标：`http(s)` 地址或内置 drawable 名（如 `hm_drawer`）；留空用兜底图标 |
| `pkg` / `packageName` | 应用包名：已安装直接拉起；未安装且配置 `url` 时走后台下载安装 |
| `url` / `downloadUrl` | APK 下载地址（可选，支持相对地址） |
| `desc` / `summary` | 副标题（可选，显示在卡片底部名称上方） |
| `type` / `target` | 可选，兼容 `page`/`feature`/`vod` 入口；留空时按 `app` 处理 |

> demo 的 `r=config` 已把 `ua` 设为非空示例值，需要特定 UA 的源可直接修改该字段。

### 3.1 直播 JSON 格式

请求：`index.php?r=live`

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
      }
    ]
  }
]
```

- 顶层为分组数组（或 `{"groups":[...]}` 对象形式，客户端均可识别）；每个分组含 `group` 与 `channels`；
- 同一频道多个播放地址写入 `urls` 数组，`地址$线路名` 可为地址命名；
- **每频道/线路解码 `decode`（可选）**：`soft`/`hard`/`system`/`exo`/`ali`/`auto`。
  - 写在频道对象上=该频道所有线路默认；写在 urls 对象上=单线路独立解码；
  - 也兼容 `playerType`（-1/0/1/2/3）与平行数组 `decodes`；
  - 客户端仅在用户未手动设置该线路解码时采用，用户播放中调整后以用户设置为准；
- **支持的节目类型（播放地址协议）**：`http(s)` 直链（m3u8/flv/mp4 等）、`rtmp://`、`rtsp://`、`webview://`（WebView 直接播网页）、`video://`（WebView 嗅探网页视频）、`tkp2p://`（内置 P2P，引擎见主配置 `tkp2p` 段）；
- 请求 `index.php?r=live&format=txt` 返回传统 txt（`分组,#genre#` / `频道,url#url2`），客户端同样兼容；txt 不携带 `decode`；
- 客户端同时兼容传统 txt（`分组,#genre#` / `频道,url#url2`）与 m3u（`#EXTM3U`/`#EXTINF`）文本源，按内容自动识别。

### 3.2 EPG JSON 格式

请求：`index.php?r=epg&ch=CCTV-1&date=2026-11-04&days=3`（`days` 省略时为 `1`，范围 1~9；也兼容 `name` 参数）。

```json
{
  "epg_data": [
    { "date": "2026-11-03", "start": "08:00", "end": "12:00", "title": "CCTV-1 上午节目" },
    { "date": "2026-11-04", "start": "08:00", "end": "12:00", "title": "CCTV-1 上午节目" },
    { "date": "2026-11-05", "start": "08:00", "end": "12:00", "title": "CCTV-1 上午节目" }
  ]
}
```

- 每条节目必填 `start` / `end`（`HH:mm`）与 `title`；
- 单日格式可不写 `date`，客户端全部归到请求日期；
- 写了 `date`（`yyyy-MM-dd`）即按天拆分缓存，配合 `days` 一次下发多天，减少请求量。

### 3.3 点播网页源与接口热更新

- 点播源返回的播放地址若为 `webview://` / `video://` / `tkp2p://`，播放页会自动切换到 WebView 播放层；`tkp2p://` 的引擎参数由主配置 `tkp2p` 段控制；
- 点播 / 直播 / EPG 接口地址变更后，客户端会立即删除点播接口磁盘缓存、清空 EPG 缓存并通知正在显示的界面重载，**无需重启 App**；
- 自定义点播（入口 `type=vod`）的搜索只查该界面注入的源，与默认点播搜索互不影响。

## 4. 加密格式（与客户端 ConfigCrypto.java 一致）

### 4.1 ke 前缀模式（推荐）

```php
$key = md5(time() . "AD80F93B542B" . "8b14ecf6ae028f9b6d25fa547eabad85");
$ke  = substr($key, 13);                                  // 19 位小写 hex
$out = $ke . bin2hex(openssl_encrypt(
    $plain,
    'AES-128-ECB',
    substr($ke, 0, 16),
    OPENSSL_RAW_DATA
));
```

- 密文总长为奇数且 ≥ 51；
- 客户端取前 19 位为 `ke`，前 16 位为 AES-128 密钥，解剩余 hex；
- 客户端无需配置任何 secretKey。

### 4.2 固定密钥模式

```php
$out = bin2hex(openssl_encrypt($plain, 'AES-128-ECB', substr($secret, 0, 16), OPENSSL_RAW_DATA));
```

- 服务端 `$secret` 前 16 字节作为 AES-128 密钥；
- 客户端 `secretKey`（JSON）或 `ConfigCrypto.SECRET_KEY`（Java 常量）必须一致；
- 密文总长为偶数且 ≥ 32。

### 4.3 注意事项

- 客户端会自动识别明文/密文；**响应内容类型必须是 JSON / 直播 txt / m3u**，解密后客户端靠首字符 `{`、`[`、`/`、`#EXTM3U`、`#genre#` 判定（`<` 仅兼容旧 XMLTV）；`r=live` 推荐用 JSON（可携带逐频道/线路 `decode`），也支持 `format=txt` 返回传统文本源；
- 加密输出时 `Content-Type` 改为 `text/plain`，不要把密文包在 JSON 字符串里；
- 换密钥后旧密文立即失效，客户端会自动回退到明文/缓存（视接口而定）。

## 5. 本机参数

客户端在 **所有 OkGo 请求** 上加请求头：

```
X-Device-Mac
X-Device-Ip
X-OS-Version
X-App-Version
```

自有接口还会在 URL 上拼参数：

```
?mac=...&ip=...&model=...&brand=...&device=...&sdk=...&release=...&app=...
```

PHP 读取示例：

```php
$mac = $_GET['mac'] ?? ($_SERVER['HTTP_X_DEVICE_MAC'] ?? '');
$app = $_GET['app'] ?? ($_SERVER['HTTP_X_APP_VERSION'] ?? '');
```

demo 中的 `tk_device()` 已封装，并会在部分响应里回显 `device`，方便联调。

## 6. 常见问题

**Q：返回密文后 App 显示空白/解析失败？**
A：确认 `enc` 参数生效、`openssl` 扩展可用，且响应没有被网关再包一层 HTML；用 `index.php?r=crypto` 对比密文长度与格式。

**Q：应用市场搜索没有结果？**
A：demo 的 `tk_market_list()` 只含两条演示数据；确认传入 `keyword` 并被 URL 解码。

**Q：升级接口为什么没弹窗？**
A：demo 默认 `versionCode=0`，需把它改成大于当前客户端的版本号，并填 `url`（APK 下载地址）。

**Q：如何按 MAC 下发不同配置？**
A：在 `r=config` 分支里读取 `tk_device()['mac']`，按 MAC 返回不同 JSON 即可；客户端无需改动。

## 7. 界面（酒店 IPTV 桌面）与新增配置字段

`index.php?r=config` 已包含以下演示字段，可直接修改：

| 字段 | 说明 |
| --- | --- |
| `placeholderImage` | 图片下载完成前显示的预设占位图 |
| `forceOverride` | 接口强制覆盖：`false`=用户手动配置优先；`true`=全部覆盖；也可对象逐项控制 |
| `ua` | 全局 UA（demo 已填非空示例值；影响 OkHttp 请求与 webview/tkp2p 直播页） |
| `home.appRecommends` | 首页「应用推荐」卡片数组（名称/图标/包名/下载地址/副标题），自动换行 |
| `defaultScreen` | 启动加载的界面 id：`standard` 或 `screens` 中的 id |
| `screens[]` | 界面列表：`layout`（hotel_grid/hotel_list/hotel_showcase）、独立 `api/live/epg`、标题/背景/公告、`layoutUrl` 布局插件、`entries` 入口 |

按 MAC 返回不同酒店桌面的示例：

```php
$hotel = $dev['mac'] === 'AA:BB:CC:DD:EE:FF';
$config['defaultScreen'] = $hotel ? 'hotel_a' : 'standard';
$config['screens'] = [[
    'id'       => 'hotel_a',
    'name'     => 'A 酒店桌面',
    'layout'   => 'hotel_grid',
    'title'    => '欢迎入住 A 酒店',
    'ticker'   => '前台电话 8888',
    'api'      => 'http://hotel-a.example.com/vod.json',
    'live'     => 'http://hotel-a.example.com/live.json',
    'epg'      => 'http://hotel-a.example.com/epg.json',
    'columns'  => 3,
    'rows'     => 2,
    'entries'  => [
        ['name' => '点播', 'type' => 'page', 'target' => 'vod', 'icon' => 'hm_vod'],
        ['name' => '直播', 'type' => 'page', 'target' => 'live', 'icon' => 'hm_live'],
        ['name' => '切换界面', 'type' => 'feature', 'target' => 'screenSwitch', 'icon' => 'hm_settings'],
    ],
]];
```

完整教程（含布局插件与小白步骤）见 [../tvbox-dev.md](../tvbox-dev.md) 第 11 章。
