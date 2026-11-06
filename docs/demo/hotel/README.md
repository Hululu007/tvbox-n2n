# 酒店桌面 demo 文件说明

本目录是 [酒店界面与布局插件从零开发完整指南](../../hotel-dev-guide.md) 的配套可运行示例。

| 文件 | 用途 |
| --- | --- |
| `config.json` | 完整主配置：3 个酒店界面 + 全局滚动通知（`speed` / `pause`）+ 每屏接口字段 |
| `layout-grid.json` | 布局插件：宫格大卡片 |
| `layout-list.json` | 布局插件：左侧竖排菜单 |
| `layout-showcase.json` | 布局插件：顶部大图 + 底部入口 |

## 快速使用

### 方式 A：直接部署静态文件

1. 把整个 `docs/demo/hotel/` 上传到 Web 服务器（`config.json` 与三个 `layout-*.json` 保持同目录）；
2. `config.json` 里的 `layoutUrl` 已经是相对地址（`layout-grid.json` 等），客户端会相对主配置目录解析；
3. 把 App 的 `RemoteConfig.CONFIG_URL` 指向 `http://your-server/hotel/config.json`；
4. 重新打包安装，启动即进入 `hotel_demo` 桌面。

### 方式 B：使用 PHP demo（推荐，可动态下发）

部署 `docs/php-demo/` 后，主配置直接使用：

```
http://your-server/php-demo/index.php?r=config
```

其中 `screens[].layoutUrl` 已指向：

```
?r=hotel_layout&id=grid     宫格
?r=hotel_layout&id=list     竖排
?r=hotel_layout&id=showcase 品牌大图
```

修改 `index.php` 中 `hotel_layout` 分支即可远程换肤，无需重新安装 App。

## 可观察到的功能

- 启动直接进入酒店桌面（`defaultScreen=hotel_demo`）
- 底部滚动通知整条走完后停顿 10 秒再走（`notice.pause=10`）
- 未配置 `ticker` 的界面显示全局公告
- 「切换界面」入口打开界面列表，并自动勾选当前界面
- 三套布局插件仅需修改 JSON 即可切换外观与入口

## 相关文档

- [酒店界面与布局插件从零开发完整指南](../../hotel-dev-guide.md)
- [系统从零开发制作完整指南](../../system-dev-guide.md)
- [PHP demo 说明](../../php-demo/README.md)
