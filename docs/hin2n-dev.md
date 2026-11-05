# hin2n 拆分说明（本仓库仅保留入口）

> hin2n（基于 [switch-iot/hin2n](https://github.com/switch-iot/hin2n)）已拆分为**独立可安装 App**（`applicationId = wang.switchy.hin2n`），源码与构建工作流在独立仓库。
> 本仓库（tvbox-n2n）不再包含 `hin2n/` 目录、`include ':hin2n'`、`hin2n.yml`，也不再包含 n2n native 源码。
> 用户使用说明见 [hin2n.md](file:///f:/tvbox-n2n/docs/hin2n.md)。

---

## 1. 分层与边界

```
tvbox-n2n (:app)                        独立仓库 (:hin2n)
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ n2n/RemoteConfig             │        │ wang.switchy.hin2n            │
│ n2n/EntryDispatcher          │  Intent │ Hin2nCore / N2NService        │
│ n2n/HomeEntry（首页入口）     │ ──────▶ │ n2n native(.so via CMake)     │
│ n2n/ApkDownloader（未安装下载）│ 按包名  │ greenDAO / 配置编辑 UI        │
└──────────────────────────────┘        └──────────────────────────────┘
```

1. `:app` **不能引用任何 `wang.switchy.hin2n` 的内部类**；只通过 `PackageManager`/`Intent` 与包名交互。
2. `:app` 的 `settings.gradle` 只引入 `:app`、`:quickjs`、`:pyramid`；源码包（GitHub Actions `test.yml`）也不再打包 hin2n 目录。
3. hin2n 的版本号、签名、NDK/CMake 版本、发布 tag（`hin2n-latest`）全部由它自己的仓库/工作流负责。

## 2. 本仓库内的相关代码

| 文件 | 职责 |
|------|------|
| [EntryDispatcher.java](file:///f:/tvbox-n2n/app/src/main/java/com/github/tvbox/osc/n2n/EntryDispatcher.java) | `target=hin2n`：已安装按包名拉起；未安装且配置 `hin2n.apkUrl` 时下载安装 |
| [HomeEntry.java](file:///f:/tvbox-n2n/app/src/main/java/com/github/tvbox/osc/n2n/HomeEntry.java) | 首页顶栏/历史行组网入口显隐与点击 |
| [RemoteConfig.java](file:///f:/tvbox-n2n/app/src/main/java/com/github/tvbox/osc/n2n/RemoteConfig.java) | `hin2nEntry` 显隐、`hin2n.apkUrl` 读取 |
| [ApkDownloader.java](file:///f:/tvbox-n2n/app/src/main/java/com/github/tvbox/osc/n2n/ApkDownloader.java) | APK 下载/安装管线（与 app 入口共用） |
| [BootReceiver.java](file:///f:/tvbox-n2n/app/src/main/java/com/github/tvbox/osc/n2n/BootReceiver.java) | 只处理 TVBox 自身开机启动；hin2n 自连由独立 App 处理 |

## 3. 入口配置

```jsonc
{
  "hin2nEntry": true,
  "hin2n": { "apkUrl": "https://.../hin2n.apk" }
}
```

相对地址规则与其他接口一致（优先按主配置来源目录解析，本地配置回退主接口 `api` 的域名/目录）。

## 4. 迁移/升级提醒

- 从旧版（内置 hin2n）升级：TVBox 不再包含组网界面，原内置组网数据不会被本仓库读取；请安装独立 hin2n App 后重新配置。
- 卸载 TVBox 不会删除独立 hin2n App 及其配置。
- 本仓库已删除 `docs/hin2n-dev.md` 中描述的模块源码；如需开发组网功能，请在 hin2n 独立仓库进行。
