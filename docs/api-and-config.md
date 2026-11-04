# TKLauncher 接口、配置与接入说明

本文档汇总项目**全部接收接口**、请求携带的本机参数、加密规则、相对地址规则，并给出可运行的配置示例与 PHP 服务端 demo。

- 配置示例：[tvbox-config.example.jsonc](file:///f:/tvbox-n2n/docs/tvbox-config.example.jsonc)（带注释）、[tvbox-config.example.json](file:///f:/tvbox-n2n/docs/tvbox-config.example.json)（纯 JSON）
- PHP demo：[docs/php-demo/README.md](file:///f:/tvbox-n2n/docs/php-demo/README.md)
- 开发细节：[tvbox-dev.md](file:///f:/tvbox-n2n/docs/tvbox-dev.md)

---

## 1. 配置入口

App 内无设置页，所有配置来自 JSON。编译前修改：

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

> *下载地址建议直接写完整 URL；相对地址支持由服务端/客户端场景决定，**接口数据**字段优先使用完整地址更稳妥。

### 2.1 主配置 JSON 示例（最小可用）

```jsonc
{
  "api": "http://your-domain/index.php?r=api&enc=ke",
  "live": "index.php?r=live&enc=ke",          // 相对地址 → 自动拼主配置地址的域名+目录
  "epg": "index.php?r=epg&enc=ke",
  "proxy": "",
  "bootStart": true,
  "settings": { "homeRecStyle": true },
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

```
#EXTM3U
央视,#genre#
CCTV-1 综合,https://example.com/cctv1.m3u8
CCTV-13 新闻,https://example.com/cctv13.m3u8
卫视,#genre#
湖南卫视,https://example.com/hunan.m3u8
```

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

---

## 6. PHP demo 快速接入

1. 上传 `docs/php-demo/` 到服务器；
2. 访问 `https://your-domain/php-demo/index.php?r=config&enc=ke` 确认返回密文字符串；
3. 把该地址填入 `RemoteConfig.CONFIG_URL`；
4. 重新编译安装，App 启动后自动加载配置；
5. 联调各接口：`?r=api`、`?r=live`、`?r=epg`、`?r=market`、`?r=upgrade`、`?r=crypto`。

部署与加密细节见 [PHP demo 说明](file:///f:/tvbox-n2n/docs/php-demo/README.md)。
