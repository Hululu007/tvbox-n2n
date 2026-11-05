# hin2n 组网入口说明（独立 App）

> hin2n 组网已从 TVBox 中拆分，作为**独立可安装 App**（`applicationId = wang.switchy.hin2n`）维护，源码在独立仓库。
> 本仓库（tvbox-n2n）只保留**首页入口**：按包名拉起已安装的 hin2n，未安装时按主接口 `hin2n.apkUrl` 下载安装，不再内置组网界面与 n2n native 代码。

---

## 1. 入口

有两个入口，行为一致：

1. **首页顶栏的组网图标**（`tvHin2n`）；
2. **首页「历史」入口行的「组网」卡片**。

是否显示由主配置 JSON 顶层 `hin2nEntry` 控制（默认 `true`）。

点击逻辑（`EntryDispatcher`，`target = hin2n`）：

- 已安装 `wang.switchy.hin2n` → 直接按包名拉起；
- 未安装且主配置配置了 `hin2n.apkUrl` → 走统一的 APK 下载安装管线（下载进度、FileProvider 安装、Android 8+ 未知来源授权后自动继续）；
- 未安装且未配置 `hin2n.apkUrl` → 提示先安装 hin2n。

## 2. 主接口配置

```jsonc
{
  "hin2nEntry": true,                           // 首页是否显示组网入口
  "hin2n": { "apkUrl": "https://.../hin2n.apk" } // 未安装时的下载地址（可选）
}
```

- 组网配置（超节点/community/静态路由等）由 **hin2n App 自己管理**，TVBox 不再播种、不再维护这些字段；
- 启动时 TVBox 不再内置任何 hin2n 开机自连逻辑，由独立 App 自行处理。

## 3. 常见问题

1. **点组网没反应**：未安装 hin2n 且主接口没配 `hin2n.apkUrl`，先手动安装 hin2n。
2. **点击后开始下载**：属于正常流程，下载完成后按提示允许安装（Android 8+ 需授予「安装未知应用」）。
3. **想改组网配置**：打开 hin2n App 内配置页操作，与 TVBox 无关。

## 4. 协议

hin2n / n2n 均为 GPL-3.0；hin2n 源码与许可见其独立仓库。
