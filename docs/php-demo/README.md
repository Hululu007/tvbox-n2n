# TKLauncher PHP 接口 demo 说明

本目录是一个可直接运行的单文件 PHP 接口示例，覆盖 TKLauncher 的全部接收接口，并演示本机参数与加密下发。

## 1. 环境要求

- PHP 7.4+（7.4 / 8.x 均可）
- 开启 `openssl` 扩展（`php -m | grep openssl`）
- 建议开启 `mbstring`（应用市场按首字母搜索演示用到）

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
| `index.php?r=live` | `live` | 直播源 txt |
| `index.php?r=epg&ch={频道名}&date={yyyy-MM-dd}&days={天数}` | `epg` | 节目单 JSON（支持一次返回多天） |
| `index.php?r=market&category={category}&sort={sort}&page={page}&pageSize={pageSize}&keyword={keyword}` | `appMarket.url` | 应用市场列表 |
| `index.php?r=detail&pkg={packageName}` | `appMarket.detailUrl` | 应用详情 |
| `index.php?r=upgrade` | `upgrade.checkUrl` | 升级检查 |
| `index.php?r=crypto` | — | 明文/ke/固定密钥三种形态联调 |
| `tvbox_crypto.php` | — | 加密工具函数与演示 |

主配置里的接口地址使用**相对地址**（如 `index.php?r=api&enc=ke`），客户端会自动以主配置 URL 的域名/IP 拼接，无需写死域名。

`r=config` 返回完整系统配置：接口地址、`epgDays`、`settings` 全量设置项、首页 `home`、`themes`、`tkp2p`、`topEntries`/`homeEntries`、应用市场、升级与捐助等，字段含义与 [配置示例](file:///f:/tvbox-n2n/docs/tvbox-config.example.jsonc) 一致。

### 3.1 EPG JSON 格式

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

- 客户端会自动识别明文/密文；**响应内容类型必须是 JSON / 直播 txt**（EPG 也已统一为 JSON），解密后客户端靠首字符 `{`、`[`、`/`、`#EXTM3U`、`#genre#` 判定（`<` 仅兼容旧 XMLTV）；
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
