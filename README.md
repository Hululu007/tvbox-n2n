# TKLauncher 桌面版（tvbox-n2n）

![Build](https://shields.io/github/actions/workflow/status/tianku1985/tvbox-n2n/test.yml?branch=main&logo=github&label=Build)

## Credits
This repo relies on the following third-party projects:
- [CatVodTVOfficial/TVBoxOSC](https://github.com/CatVodTVOfficial/TVBoxOSC)
- [takagen99/Box](https://github.com/takagen99/Box) (Vendored: 258a5fef61578869ae905ca230bdde9e99fc19a8)
- [switch-iot/hin2n](https://github.com/switch-iot/hin2n)（独立可安装 App，源码在独立仓库；本仓库仅保留首页入口）
- [ntop/n2n](https://github.com/ntop/n2n) / meyerd n2n（GPL-3.0）

## 结构
本仓库直接内置全部主工程源码，GitHub Actions 只编译本仓库内容（不再克隆上游）：
- 根目录：[takagen99/Box](https://github.com/takagen99/Box) 源码
- `hin2n/` 目录已移除：hin2n 组网是独立可安装 App（`wang.switchy.hin2n`，源码在独立仓库），本仓库只保留首页入口（按包名拉起，未安装时按 `hin2n.apkUrl` 下载安装）

## 定制功能
- **应用名「桌面」+ 独立包名**：generic=`TKLauncher.app`、hisense=`TKLauncher.hisense`，与其它 TVBox 区分，可并存安装
- **内置系统桌面**：清单直接声明 HOME/DEFAULT（参考 MQLauncher），安装后即可作为桌面；已移除应用内「选择桌面/设为桌面」功能与入口
- **hin2n 组网独立 App 入口**：首页「历史」入口行的「组网」卡片或顶栏图标按包名拉起独立 App `wang.switchy.hin2n`，未安装时按主接口 `hin2n.apkUrl` 下载安装；TVBox 本身不再内置 hin2n 代码
- **全部配置由 JSON 下发，应用内无设置页**：APK 内不再打包任何配置 JSON；远程/本地地址在 `RemoteConfig.CONFIG_URL` 代码中手动指定（远程可热更新，本地可用文件），配置示例见 `docs/`
- **点播直接显示分类 + 切源原地重载**：进入点播界面即显示「热播推荐 + 点播分类」并默认加载首屏；切源时复用本地缓存的接口配置重新解析、重载新源的 jar/js/py 插件并强制刷新分类，全程停留在点播界面（不返回首页、不重启界面），旧源的迟到响应会被丢弃
- **点播首分类「热播推荐」**：固定为首个分类并默认选中，数据只取主配置 `home.recMovies`（未配置时显示空态）
- **主接口失败全屏报错**：主配置无法加载/访问且无缓存时直接显示报错页（重试/退出），不再加载其它界面；点播接口提示只在点播界面出现
- **首页推荐默认多行**：`settings.homeRecStyle` 默认 `true`（网格），可配置为单行
- **应用市场 TV 搜索**：搜索弹窗内置 A-Z/0-9 屏幕键盘，遥控器方向键 + OK 输入，支持按首字母搜索
- **接口加密与设备参数**：所有接收接口支持 `ke` 前缀/固定密钥 AES-128-ECB 密文；所有 OkGo 请求统一携带 `X-Device-Mac` / `X-Device-Ip` / `X-OS-Version` / `X-App-Version` 请求头，自有接口 URL 额外拼接 mac/ip/model/brand/device/sdk/release/app
- **相对接口地址**：`live`/`epg`/`proxy`/`upgrade`/`appMarket`/自定义点播未写协议时自动使用主配置（或主接口）的域名/目录；点播接口内的 `./` 相对地址按该接口自身目录解析
- 首页自定义入口、底部滚动公告、全局主题/外观、接口地址均在 JSON 配置
- 静态路由：由独立 hin2n App 的配置编辑页按「目标网段 网关IP」每行一条填写（本仓库不再包含组网界面）
- **Android 4.4 兼容**：`normal` flavor（minSdk19，与旧 `legacy` 合并），drawable 双版本 + 矢量图转 PNG，修复 4.4 黑屏
- **32/64 位独立打包**：每个品牌×模式分别产出 `-v7a`（armeabi-v7a）和 `-arm64`（arm64-v8a）安装包，包内只含对应架构
- **启动时进直播**：JSON `settings.homeDefaultShow` 控制，开启后启动直接进入直播；触摸设备支持轻点换台
- **应用市场**：「应用」页升级为专业市场（精选 / 应用 / 游戏 / 排行 / 管理），支持分类浏览、搜索、详情与截图、下载安装、已安装更新角标与卸载；数据源由 `appMarket` 配置（接口或 apps 数组，APK 内无内置目录）
- **直播 EPG 与节目源解码**：频道列表带当前/后续节目、多天节目单与本地缓存，已结束节目在支持时移的源上可回看；接口可在每个频道/线路下发 `decode` 默认解码（系统 / 硬解 / 软解 / EXO / Ali / 跟随），用户单独设置优先生效；直播接口兼容 JSON / txt / m3u

## 文档
- [用户使用说明（安装/使用/桌面/切源/搜索等）](docs/usage.md)
- [接口、配置与 PHP 对接说明（含点播启动画面 vodSplash）](docs/api-and-config.md)
- [PHP 接口 demo（含加密/设备参数/应用市场/升级）](docs/php-demo/README.md)
- [hin2n 使用说明（独立 App 入口）](docs/hin2n.md)
- [TVBox 开发文档](docs/tvbox-dev.md)
- [配置全量 demo（带注释）](docs/tvbox-config.example.jsonc) / [纯 JSON 版](docs/tvbox-config.example.json)

## 构建
JDK17、Android SDK compileSdk34、NDK 25.2.9519653、CMake 3.22.1：

```powershell
.\gradlew.bat assembleGenericNormalDebug   # Android 4.4 兼容调试包（按 ABI 产出 v7a / arm64 两个 APK）
.\gradlew.bat assembleRelease              # 全部 4 个变体 × 2 ABI = 8 个 APK
```

说明：n2n 与 hin2n 均为 GPL-3.0 协议（hin2n 独立 App 的源码在其独立仓库）。
