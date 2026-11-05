# TVBox-n2n 五项需求实现计划

## Context（背景）

TVBox-n2n（基于 takagen99 Box 深度定制）需要落地 5 项新需求：
1. **默认设为系统桌面 + 开机启动**——开机启动已默认 true（BootReceiver 已就绪，零改动）；设为桌面当前默认 false，需改默认 true 并对老设备重放一次默认值。
2. **系统配置入口**——首页入口打开安卓系统设置，json 可自定义。
3. **捐助界面入口 + 捐助界面**——json 配置标题/介绍/二维码图片，入口可配置。
4. **接口加密**——本地内置配置 + 所有远程接收接口（远程配置/升级检查/点播/直播/EPG/自定义点播）支持密文下发：内容是 json 直接解析，不是 json 先解密再加载；秘钥可自定义。算法对应用户给的 PHP：AES-128-ECB + PKCS7，ke=substr(md5(time+固定串),13)（19字符）作前缀，密钥=ke 前16字符，输出=ke+hex。
5. **重新生成使用文档、开发文档、带全注释的对接 json 示例**。

项目约束：本机不可编译，只能 GitHub Actions CI 验证；推送走 GitHub Git Data API（node fetch 脚本，gh_ref.txt 做 remote-sha guard）；禁止 `git reset --hard`。

## 关键设计决策

- **老设备 setAsLauncher 重放（方案 b）**：现有 `K_LAUNCHER_DEFAULT_SET` 一次性守卫导致改 json 默认值对老用户不生效。新增守卫键 `K_LAUNCHER_DEFAULT_SET_V2`，全量重放一次默认 true；用户仍可在设置页关闭，useRemoteConfig 远程覆盖通路不变。
- **秘钥自定义（Java 侧）**：`ConfigCrypto.java` 顶部定义常量 `SECRET_KEY`（默认空串），这就是「秘钥自定义修改位置」，注释醒目标注；开发者改这一处重新编译即可。json 顶层 `"secretKey"` 作为可选运行时覆盖（非空时优先于 Java 常量，便于远程下发轮换；留空则用 Java 常量）。两者都为空时仅支持 ke 前缀模式（PHP 原算法，密钥自带在数据中，无需任何配置）。
- **密文判定规则**（ConfigCrypto）：trim 后以 `{`/`[`/`/` 开头 → 明文原样；以 `2423` 开头 → 原样（TVBox 原生 CBC 格式，防御性排除）；**奇数长度 ≥51 且全小写 hex** → ke 前缀模式（ke=前19字符，aesKey=ke 前16字符）；**偶数长度 ≥32 且全小写 hex** → 有效秘钥（json 覆盖值或 Java SECRET_KEY）非空时试固定密钥模式（key=秘钥前16字节）。解密成功判据：结果 trim 后以 `{`/`[` 开头（json），或以 `#EXTM3U` 开头 / 含 `#genre#`（直播 txt）。不满足返回原文。
- **assets 时序约定**：assets/tvbox-config.json 只允许明文或 ke 前缀模式加密（禁止 secretKey 模式——解密发生在 secretKey 写入 Hawk 之前）。K_REMOTE_RAW 缓存存解密后明文，与旧版本缓存格式兼容。
- **接口请求附加本机参数**：新建 `n2n/DeviceParams.java`，静态 `append(String url)` 给所有远程接口 URL 拼接查询参数（assets/file 本地路径跳过；已有同名参数不重复）：`mac`（遍历 NetworkInterface 取第一个非回环接口的硬件地址，Android 6+ WifiInfo 已失效；兜底 ANDROID_ID）、`ip`（第一个非回环 IPv4）、`model`/`brand`/`device`（Build 机型品牌）、`sdk`/`release`（Android 系统版本）、`app`（app versionName）。值 URLEncoder 编码，每次请求现取（IP 随网络切换变化），全程 try/catch 失败静默跳过不影响请求。挂载点与解密相同 6 处 + ApiConfig 点播配置请求构造处，在发起请求前对 url 调 append()。服务端 PHP 可用 $_GET['mac'] 做 getMiniData($mac) 之类的按机下发。
- **解密挂载点（6 处）**：①RemoteConfig.bootstrap 读 assets 后；②RemoteConfig.fetchAndApply httpGet 后；③ApiConfig.FindResult else 分支；④CustomVodActivity.applyCustomJson 首行（一处覆盖 http/assets/file 三分支）；⑤LivePlayActivity.loadProxyLives onSuccess（TxtSubscribe.parse 前）+ EPG StringCallback onSuccess（L875 附近，new JSONObject 前）；⑥UpgradeChecker.onSuccess。

## 文件级修改清单（按实施顺序）

| # | 文件 | 改动 |
|---|------|------|
| 1 | `app/.../n2n/ConfigCrypto.java` | 新建。`maybeDecrypt(String raw)` 静态方法，按上述判定规则；顶部 `SECRET_KEY` 常量=秘钥自定义修改位置；内部读 `RemoteConfig.secretKey()` 覆盖；全程不抛异常 |
| 1b | `app/.../n2n/DeviceParams.java` | 新建。`append(String url)` 静态方法给 URL 拼 mac/ip/model/brand/device/sdk/release/app 参数；本地路径跳过；同名参数不重复；全程 try/catch |
| 2 | `app/.../n2n/RemoteConfig.java` | 新增 `K_SECRET`/`K_DONATE`/`K_LAUNCHER_DEFAULT_SET_V2`；`secretKey()`/`donate()` 读取方法；apply() 加 secretKey、donate 段处理（本地+远程均应用）；bootstrap/fetchAndApply 挂解密、fetchAndApply 请求前 `DeviceParams.append(url)`；`launcherEnabled()` 缺省改 true + V2 守卫重放；类注释更新 |
| 3 | `app/.../api/ApiConfig.java` | FindResult else 分支 `json = ConfigCrypto.maybeDecrypt(content)`；fetchConfig 请求点播 api url 前 `DeviceParams.append(url)` |
| 4 | `app/.../ui/activity/CustomVodActivity.java` | applyCustomJson 首行解密；loadCustomConfig http 分支请求前 `DeviceParams.append(url)` |
| 5 | `app/.../ui/activity/LivePlayActivity.java` | loadProxyLives + EPG 回调解密；两处请求前 `DeviceParams.append(url)` |
| 6 | `app/.../n2n/UpgradeChecker.java` | onSuccess 解密后再 new JSONObject；checkUrl 请求前 `DeviceParams.append(checkUrl)` |
| 7 | `app/.../n2n/EntryDispatcher.java` | openPage 加 target `"system"`（Settings.ACTION_SETTINGS，try/catch + Toast 兜底，部分 TV 固件无此 Activity）和 `"donate"`；注释更新 |
| 8 | `app/.../ui/activity/DonateActivity.java` | 新建。继承 BaseActivity；读 RemoteConfig.donate() 填 title/desc/image；desc 空用内置默认项目介绍；图片三态加载：http(s) 走 Glide、assets/ 走 BitmapFactory.decodeStream、其余按 drawable 名解析；tvBack 点击 finish |
| 9 | `app/src/main/res/layout/activity_donate.xml` | 新建。仿 activity_vod.xml 顶栏（主题色竖条 + tvBack(button_dialog_vod+hm_home) + tvName），下方 ScrollView：介绍 TextView + 居中二维码 ImageView；遥控器焦点链 nextFocusDown |
| 10 | `app/src/main/AndroidManifest.xml` | 注册 DonateActivity（照 CustomVodActivity 模板：sensorLandscape + exported=false + configChanges） |
| 11 | `app/src/main/assets/tvbox-config.json` | `setAsLauncher: true`；顶层加 `"secretKey": ""`、`"donate": {"title":"支持我们","desc":"","image":""}`（desc 留空=用代码内置介绍）；homeEntries 末尾加 `{"name":"系统","type":"page","target":"system","icon":"hm_settings"}` 和 `{"name":"捐助","type":"page","target":"donate","icon":"hm_fav"}`（图标均现有，无需新增资源）；注释同步 |
| 12 | `docs/usage.md` | 重写：安装、开机自启/设为桌面（含无 root 系统限制说明）、入口配置（含新 system/donate 类型）、捐助配置、接口加密配置（点播/直播/EPG/远程配置/升级/自定义点播全部支持密文；秘钥自定义位置 = ConfigCrypto.java 的 SECRET_KEY 常量，json "secretKey" 可选覆盖；PHP 端用法） |
| 13 | `docs/tvbox-dev.md` | 重写：架构、关键类清单（含 ConfigCrypto/DonateActivity）、加密协议全文（判定规则+6 处挂载点表+完整 PHP 加密端示例代码）、构建/发布流程 |
| 14 | `docs/tvbox-config.example.jsonc` | 重写全量带注释示例（含 secretKey/donate/system/donate 入口）；`docs/tvbox-config.example.json` 同步（纯 json 版） |

## 复用的现有实现

- `AES.ECB/toBytes/rightPadding`（util/AES.java）——ConfigCrypto 内部复用
- `EntryDispatcher.loadIcon` 图标三态模式 → DonateActivity 图片加载参照
- `activity_vod.xml` 顶栏布局模式（tvBack/tvName/主题色竖条）
- `RemoteConfig.parseLoose` jsonc 解析——解密后统一走它
- BaseActivity 自动主题 + HomeEntry.applyBackground——DonateActivity 继承即得主题背景

## 验证（本机不可编译，走 CI）

1. node fetch 脚本走 Git Data API 推送（推前核对 gh_ref.txt remote-sha，推后更新）
2. push main 触发 .github/workflows/test.yml
3. CI 产物 APK 实测清单：①新装首启弹桌面选择器/root 自动接管、设置页开关可关；②重启自启；③首页历史行出现「系统/捐助」入口、点击行为正确；④捐助页 http/assets/drawable 三种图片来源；⑤PHP 加密端分别加密远程配置/点播/直播 txt/自定义点播/EPG/升级接口验证解密；⑥secretKey 空与自定义两形态回归
