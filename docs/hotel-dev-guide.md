# 酒店 IPTV 桌面 · 从零开发与布局插件制作完整指南

本文面向「从零开始做一个类似秒开酒店桌面（`mq.tv.grogshop` / MQLauncher）的酒店电视桌面」的开发者与实施人员。
按本文顺序操作，不需要改代码、不需要重新发版，即可完成：

- 开机直接进入酒店桌面（`bootStart` 自启 + 电视系统设为默认桌面）
- 每个酒店/每个界面使用独立的点播、直播、EPG 接口
- 宫格 / 竖排 / 品牌大图三种内置布局
- 通过 **布局插件 JSON** 远程换肤、换入口、换滚动通知
- 滚动通知「整条走完 → 停顿 N 秒 → 再走下一遍」
- 界面切换弹窗正确勾选当前界面
- 与秒开酒店桌面类似的应用入口（拉起/自动下载安装第三方 APK）

配套可直接运行的 demo：

- 主配置：[config.json](./demo/hotel/config.json)
- 布局插件：[layout-grid.json](./demo/hotel/layout-grid.json)、[layout-list.json](./demo/hotel/layout-list.json)、[layout-showcase.json](./demo/hotel/layout-showcase.json)
- 服务端：[php-demo/index.php](./php-demo/index.php) 的 `r=config`（默认 `defaultScreen` 为 `standard` 标准首页，需体验酒店桌面时改为某个 `screens[].id`）与 `r=hotel_layout&id=grid|list|showcase`

---

## 1. 它是怎么跑起来的

```
App 启动
  └─ RemoteConfig.bootstrap()          拉取主接口 JSON
       ├─ apply()                      写入 api / live / epg / notice / screens / defaultScreen
       └─ 配置内容变化 → 版本号 +1       已打开的酒店界面 onResume 自动重绘
HomeActivity
  └─ EntryDispatcher.openStartScreen()
       └─ defaultScreen != standard → HotelActivity
HotelActivity.init()
  ├─ 读取 intent 中的 screen id（缺省 = defaultScreen）
  ├─ RemoteConfig.findScreen(id)
  ├─ applyScreenEndpoints(screen)      应用本屏独立接口；地址变化立即清缓存
  ├─ render()                          标题 / 时钟 / 背景 / Logo / 滚动通知 / 布局
  └─ loadRemoteLayoutIfNeeded()        layoutUrl 插件下载 → 合并 → 再次 render
入口点击
  └─ EntryDispatcher.open()            page / feature / app / vod
```

关键点：

1. **主配置只负责“有哪些桌面”**，外观细节可以再拆到 `layoutUrl` 插件里。
2. 酒店界面的接口在进入时应用；本次已修复“改完接口必须重启 App”的问题：
   接口地址变化会立刻删除点播缓存文件、EPG 缓存与进程内已加载标记，并通知界面重载。
3. 滚动通知优先用界面自己的 `ticker`；没有配置时自动回退到主接口 `notice` 全局公告。

---

## 2. 环境与最小可运行工程

### 2.1 服务端（5 分钟）

1. 把仓库的 `docs/php-demo/` 整个目录放到任意 PHP 5.6+/7.x/8.x 空间或本机 `php -S 0.0.0.0:8080`。
2. 浏览器访问：

```
http://你的服务器/php-demo/index.php?r=config
```

能看到一段包含 `screens` 的 JSON 就说明服务端正常。

3. 也可以直接使用仓库里的静态 demo：

```
http://你的服务器/demo/hotel/config.json
```

### 2.2 客户端

1. 用 Android Studio 打开本仓库，JDK 17，等待 Gradle 同步完成。
2. 修改 `RemoteConfig.CONFIG_URL` 指向你的主配置地址（见 `RemoteConfig.java` 顶部说明）。
3. 编译安装：

```bash
./gradlew :app:assembleGenericNormalDebug
```

4. 首次启动 → 若 `defaultScreen` 指向酒店界面，直接进入酒店桌面。

> 只想验证酒店桌面而不改代码：把 `CONFIG_URL` 指向 `index.php?r=config` 后，
> 把 php-demo 中 `defaultScreen` 改为 `hotel_demo` 重新打包即可（php-demo 默认 `defaultScreen` 为 `standard`）。

---

## 3. 主配置：screens 字段全表

`screens` 是数组，每一项就是一个桌面。字段与 `RemoteConfig.Screen` 一一对应：

| 字段 | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 空 | 界面唯一标识，`defaultScreen` / `screenSwitch` 依赖它 |
| `name` | string | id | 界面名称，显示在切换弹窗 |
| `layout` | string | `standard` | `standard` / `hotel_grid` / `hotel_list` / `hotel_showcase` |
| `title` | string | 空 | 桌面标题（空则用 `name`） |
| `logo` | string | 空 | Logo 图片地址或内置 drawable 名 |
| `background` | string | 空 | 全屏背景图 http(s) 地址 |
| `ticker` | string | 空 | 本屏滚动通知；为空时回退全局 `notice` |
| `columns` | int | 3 | 宫格列数，1~6 |
| `rows` | int | 2 | 宫格行数，1~4；入口数超过 `columns×rows` 时自动增加行，不会丢弃入口 |
| `showClock` | bool | true | 是否显示右上角时钟 |
| `api` | string | 空 | 本屏点播接口；支持相对地址 |
| `live` | string | 空 | 本屏直播源；支持相对地址 |
| `epg` | string | 空 | 本屏 EPG；支持相对地址 |
| `layoutUrl` | string | 空 | 布局插件 JSON 地址（详见第 5 节） |
| `entries` | array | 内置默认 | 入口列表（详见第 4 节） |

顶层相关字段：

| 字段 | 说明 |
| --- | --- |
| `defaultScreen` | 启动进入哪个界面；`standard` 或留空 = 标准首页 |
| `forceOverride` | 是否强制用配置覆盖用户手动设置的接口；支持 `true` / `"api,live,epg"` / 对象 |
| `bootStart` | TVBox 开机自启（是否作为电视默认桌面由电视系统设置决定） |
| `notice` | 全局滚动通知（见第 6 节） |
| `placeholderImage` | 图片下载前的占位图 |
| `theme` | 全局主题：索引 / 中文名 / 英文别名 |

最小酒店配置：

```json
{
  "defaultScreen": "hotel_demo",
  "screens": [
    {
      "id": "hotel_demo",
      "name": "酒店桌面",
      "layout": "hotel_grid",
      "title": "欢迎入住",
      "columns": 3,
      "rows": 2,
      "entries": [
        { "name": "点播", "type": "page", "target": "vod", "icon": "hm_vod" },
        { "name": "直播", "type": "page", "target": "live", "icon": "hm_live" },
        { "name": "切换界面", "type": "feature", "target": "screenSwitch", "icon": "hm_settings" }
      ]
    }
  ]
}
```

---

## 4. 入口 entries：page / feature / app / vod

| type | target 取值 | 行为 |
| --- | --- | --- |
| `page` | `vod` `live` `search` `fastsearch` `history` `collect` `push` `drive` `apps` `hin2n` `system` `donate` | 打开 TVBox 内置界面 |
| `feature` | `screenSwitch` `themeSwitch` `styleSwitch` `siteSwitch` `clearCache` `reloadHome` `wifi` `date` | 执行内置功能 |
| `app` | 包名写在 `pkg` | 已安装直接拉起；未安装且配置 `url` 时自动下载并安装 APK |
| `vod` | 自定义点播接口地址 | 打开独立点播列表界面，可用 `theme` 指定独立主题 |

入口字段：

```json
{
  "name": "爱奇艺",
  "type": "app",
  "pkg": "com.qiyi.video",
  "url": "http://your-server/apk/iqiyi.apk",
  "icon": "http://your-server/icon/iqiyi.png",
  "pic": "http://your-server/pic/iqiyi_big.png",
  "desc": "奇艺影视"
}
```

- `icon`：内置 drawable 名（`hm_vod`、`hm_live`、`hm_search`、`hm_fav`、`hm_history`、`hm_settings`、`hm_drawer`…）或 http(s) 图片。
- `pic`：列表/展示布局右侧预览大图。
- `desc`：副标题。
- `theme`：`vod` 入口可用，索引 / 中文名 / 英文别名，仅作用于该入口及其播放页。

> 秒开酒店桌面常见的「应用市场 + 一键安装」效果，用 `type=app` + `url` 就能实现；
> 电视固件若限制 `REQUEST_INSTALL_PACKAGES`，需在系统层放开或改为预装。

---

## 5. 布局插件（layoutUrl）从零制作

### 5.1 为什么用插件

主接口通常由所有门店共用；外观、Logo、背景、入口却要按门店区分。
`layoutUrl` 允许每个界面下载一份独立 JSON，**改插件即换肤，无需改主配置、无需发版**。

### 5.2 合并规则

1. 先按主配置里的 `screens[]` 渲染（作为兜底）。
2. 下载 `layoutUrl` 成功 → 用插件中的字段覆盖同名字段 → 重新 `render()`。
3. 下载失败 → 使用本地上次缓存（`filesDir/hotel_layout_<md5(url)>.json`）。
4. 首次就下载失败且无缓存 → 保持主配置内联外观。

插件适合覆盖：`layout` `title` `logo` `background` `ticker` `columns` `rows` `showClock` `entries` `name`。
**接口地址（api/live/epg）请放在主配置 `screens[]` 中**：接口在插件加载前就已应用，放在插件里不会生效。

`layoutUrl` 支持绝对地址和相对主配置目录的地址（如 `layout-grid.json`）；
插件必须返回**纯 JSON**，不要加 `enc=ke` 等加密参数（客户端按明文解析）。

### 5.3 从零写一个插件

第一步：新建 `hotel_a.json`

```json
{
  "id": "hotel_demo",
  "name": "酒店桌面",
  "layout": "hotel_grid",
  "title": "欢迎入住",
  "background": "http://your-server/img/bg_hotel.jpg",
  "logo": "http://your-server/img/logo.png",
  "ticker": "前台电话 8888 · 祝您入住愉快",
  "columns": 3,
  "rows": 2,
  "showClock": true,
  "entries": [
    { "name": "点播", "type": "page", "target": "vod", "icon": "hm_vod", "desc": "海量影视" },
    { "name": "直播", "type": "page", "target": "live", "icon": "hm_live", "desc": "电视直播" },
    { "name": "搜索", "type": "page", "target": "search", "icon": "hm_search", "desc": "找片更快" },
    { "name": "切换界面", "type": "feature", "target": "screenSwitch", "icon": "hm_settings" }
  ]
}
```

第二步：上传到 Web 服务器，确保浏览器可直接打开。

第三步：主配置里只保留：

```json
{
  "id": "hotel_demo",
  "name": "酒店桌面",
  "layoutUrl": "http://your-server/layouts/hotel_a.json"
}
```

第四步：重启 App / 回到酒店界面。日志关键字：

```bash
adb logcat | grep -i hotel_layout
```

### 5.4 三种布局的选择建议

| 布局 | 适用场景 | 特点 |
| --- | --- | --- |
| `hotel_grid` | 入口 4~12 个 | 大卡片宫格，遥控器四向移动最直观 |
| `hotel_list` | 入口 3~6 个 | 左侧竖排菜单，右侧大图预览，适合品牌展示 |
| `hotel_showcase` | 入口 3~5 个 | 顶部大图 + 底部一排入口，适合开机欢迎页 |

`columns` / `rows` 只对 `hotel_grid` 生效（列 1~6、行 1~4）；当入口数量超过 `columns×rows` 时会自动增加行以完整显示，不会丢弃多余入口。

### 5.5 插件调试清单

1. 插件 JSON 不能有注释、不能有尾逗号。
2. 字段名大小写敏感，`layoutUrl` 不是 `layouturl`。
3. 修改插件后如果没变化，检查是否命中了本地缓存：删掉 App 数据或换一个 URL（加 `?v=2`）。
4. 图片建议 1920×1080、封面 16:9，避免首次加载白屏。
5. 插件 `entries` 为空数组时，会使用主配置的 `entries`。

---

## 6. 滚动通知（公告）与「走完停顿」

### 6.1 两种来源

| 来源 | 作用范围 | 优先级 |
| --- | --- | --- |
| `screens[].ticker` | 仅当前酒店界面 | 高 |
| 顶层 `notice` | 首页 / 直播 / 点播 / 酒店（可按界面开关） | 低（ticker 为空时使用） |

### 6.2 notice 完整字段

```json
"notice": {
  "text": "欢迎入住 · 前台电话 8888",
  "speed": 80,
  "pause": 10,
  "duration": 0,
  "fontSize": 24,
  "fontColor": "#FFD54F",
  "bgColor": "#66000000",
  "alpha": 100,
  "position": "bottom",
  "expire": "2026-12-31 23:59:59",
  "timeStart": "08:00",
  "timeEnd": "22:00",
  "show": {
    "home": true,
    "live": true,
    "vod": true,
    "hotel": true
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `speed` | 自定义滚动速度，像素/秒；`0` 表示使用系统 marquee。`>0` 时单份文本完整走一遍，短文本不会重复追加副本 |
| `pause` | **整条通知完整走完一遍后停顿的秒数**；`0`=立即走下一遍；只配 `pause` 不配 `speed` 时默认速度 100 |
| `duration` | 公告自动隐藏秒数；`0` 表示常驻 |
| `fontSize` | 字号（mm） |
| `fontColor` / `bgColor` / `alpha` | 文字颜色、背景色、不透明度 |
| `position` | `top` / `bottom`（酒店 ticker 固定在布局内，不受此字段影响） |
| `expire` | 到期后不再显示 |
| `timeStart` / `timeEnd` | 每日显示时段 |
| `show` | 按界面开关；`hotel` 控制酒店界面是否显示全局公告（默认 true） |

### 6.3 “走完停顿”的行为

- 所有 `speed > 0` 的情况都只保留一份文本：从右侧完全进入 → 向左完整走出一遍（右边缘完全离开屏幕）→ 下一遍。
  短文本不会像旧版那样先复制多份再整体平移，因此不会出现「没走完就追加显示下一份」。
- `pause = 0`：上一遍完全走出屏幕后立即开始下一遍。
- `pause > 0`：走完一遍后**停顿 N 秒**再走下一遍。
- 修改通知文本 / 速度 / 停顿时间后，界面重新渲染会自动重启滚动。
- 滚动时只有文字在移动，**公告背景固定不动**（首页 / 直播 / 点播统一用 `scrollX` 滚动内容实现）。
- 公告条挂在窗口最顶层的 `decorView` 上，**始终位于各界面所有内容之上**，不会被页面布局遮挡；
  首页布局自带的 `tvNotice` 会隐藏，避免同一条公告重复显示。
- 切换界面（首页 ↔ 直播 ↔ 点播）时公告**沿用上一界面的滚动位置继续滚动**，不会回到屏幕右侧重新加载。

---

## 7. 界面切换（screenSwitch）

在任意酒店界面放一个入口：

```json
{ "name": "切换界面", "type": "feature", "target": "screenSwitch", "icon": "hm_settings" }
```

点击后弹出 `screens` 中的所有界面。本次已修复：

- 弹窗会**正确勾选当前所在界面**（原实现固定选中第 0 项）。
- 切换后立即应用新界面的 `api/live/epg`，并清空旧接口缓存，无需重启 App。
- 新界面的滚动通知 / ticker 会立即显示。

---

## 8. 每屏独立接口与“立即生效”

主配置：

```json
{
  "id": "hotel_a",
  "api": "http://hotel-a/vod.json",
  "live": "http://hotel-a/live.txt",
  "epg": "http://hotel-a/epg.xml"
}
```

生效规则与修复后的行为：

1. 进入酒店界面时应用该屏接口（尊重用户手动配置与 `forceOverride`）。
2. 接口地址变化时立即：
   - 删除点播接口磁盘缓存 `filesDir/<md5(api)>`
   - 清空 EPG 缓存
   - 将进程内 `ApiConfig` 标记为未加载
   - 广播 `TYPE_ENDPOINT_CHANGE`，点播 / 直播 / 酒店界面收到后立即重载
3. 因此配置接口后**不再需要重启 App**。

---

## 9. 从零做一个「类似秒开酒店桌面」的完整示例

参考 APK：`MQLauncher_804.apk`（`mq.tv.grogshop`，versionName 1.804，targetSdk 29，
申请了 `INSTALL_PACKAGES` / `CHANGE_WIFI_MULTICAST_STATE` / `LOCAL_MAC_ADDRESS` 等权限，
是一个典型的酒店电视 Launcher）。本项目不复制其代码，只用公开配置能力实现同类效果。

### 9.1 目标效果

- 开机自动进入全屏酒店桌面，无系统桌面干扰
- 顶部：酒店 Logo、欢迎语、时钟
- 中部：点播 / 直播 / 应用 / 服务指南 四大入口
- 底部：滚动通知（整条走完后停 10 秒）
- 应用入口未安装时自动下载安装
- 多门店共用一套 APK，通过 MAC / 门店 ID 下发不同配置

### 9.2 完整主配置

见 [config.json](./demo/hotel/config.json)。核心片段：

```json
{
  "defaultScreen": "hotel_demo",
  "bootStart": true,
  "notice": { "text": "欢迎入住 · 前台电话 8888", "speed": 80, "pause": 10 },
  "screens": [
    {
      "id": "hotel_demo",
      "layout": "hotel_grid",
      "title": "欢迎入住",
      "layoutUrl": "index.php?r=hotel_layout&id=grid",
      "entries": [
        { "name": "点播", "type": "page", "target": "vod", "icon": "hm_vod" },
        { "name": "直播", "type": "page", "target": "live", "icon": "hm_live" },
        { "name": "应用", "type": "page", "target": "apps", "icon": "hm_drawer" },
        { "name": "切换界面", "type": "feature", "target": "screenSwitch", "icon": "hm_settings" }
      ]
    }
  ]
}
```

### 9.3 部署步骤

1. 部署 `php-demo/`，确认 `?r=config` 与 `?r=hotel_layout&id=grid` 均可访问。
2. 把 `CONFIG_URL` 改成 `.../php-demo/index.php?r=config`。
3. 打包安装，首次启动进入酒店桌面。
4. 在电视系统设置中把本 App 设为默认桌面（HOME），并在系统里允许自启动。
5. 修改 `screens` / 插件 JSON → 回到桌面或等待配置检查间隔 → 自动更新。

### 9.4 秒开类桌面的额外能力如何对应

| 秒开桌面常见能力 | 本项目实现方式 |
| --- | --- |
| 开机直达桌面 | `bootStart` + 电视系统设为默认桌面 |
| 门店独立桌面 | `screens[]` + `layoutUrl` |
| 应用市场 / 一键安装 | `entries[].type=app` + `pkg` + `url`（`ApkDownloader`） |
| 滚动字幕 / 通知 | `notice` / `screens[].ticker` + `speed` / `pause` |
| 多语言欢迎语 | 每个 `screens[].title` / `ticker` 独立配置 |
| 遥控器大焦点 | `hotel_grid` / `hotel_list` 内置大卡片焦点 |
| 公告定时展示 | `notice.timeStart` / `timeEnd` / `expire` |
| 在线升级 | 顶层 `upgrade` 段 |

> 组播直播：需要直播源本身支持（如 `udp://` / `rtp://`）并由播放内核/代理处理；
> 酒店网络须开启 IGMP，必要时使用项目内的 UDP 代理配置。

---

## 10. 二次开发：新增一个原生布局

只有 JSON 表达不了的特殊布局才需要改代码：

1. 在 `HotelActivity#render()` 的分支链中增加：
   `else if ("hotel_mine".equals(layout)) buildMineLayout();`
2. 仿照 `buildGridLayout()` / `buildListLayout()` / `buildShowcaseLayout()` 实现 `buildMineLayout()`；
   入口统一用 `createTile(entry, false)`，点击统一走 `EntryDispatcher.open(this, entry)`。
3. 需要新字段：在 `RemoteConfig.Screen` 增加字段，并在 `Screen.from()` 解析。
4. 主配置写 `"layout": "hotel_mine"`。
5. 编译自检：

```bash
./gradlew :app:compileGenericNormalDebugJavaWithJavac
```

---

## 11. 常见问题

| 现象 | 排查 |
| --- | --- |
| 启动还是标准首页 | `defaultScreen` 是否等于某个 `screens[].id`；JSON 是否合法 |
| 酒店界面空白 | `entries` 是否为空且无默认；看 logcat 是否有 `HotelActivity` 异常 |
| 插件不生效 | URL 能否直接访问；是否命中旧缓存；`layoutUrl` 拼写 |
| 滚动通知不显示 | `notice.show.hotel` 是否为 true；文本是否为空；`expire` 是否已过期 |
| 切换界面没勾选 | 升级到包含本次修复的版本 |
| 改接口仍要重启 | 确认接口写在 `screens[]`（不是插件）且版本包含本次修复 |
| 图片不显示 | 使用 http(s) 直链；Android 9+ 注意 cleartext 配置；先用 `placeholderImage` 兜底 |

---

## 12. 相关文档

- [系统从零开发制作完整指南](./system-dev-guide.md)
- [完整使用说明](./usage.md)
- [接口与配置字段说明](./api-and-config.md)
- [TVBox 开发文档](./tvbox-dev.md)
- [PHP demo 说明](./php-demo/README.md)
