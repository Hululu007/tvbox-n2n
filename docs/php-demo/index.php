<?php
/**
 * TKLauncher 服务端接口 demo（单文件路由）
 *
 * 部署：把 docs/php-demo/ 整个目录上传到 Web 服务器（需 PHP 7.4+、openssl 扩展）。
 * 然后在 App 源码 RemoteConfig.CONFIG_URL 中填写：
 *   http://your-domain/php-demo/index.php?r=config&enc=ke
 *
 * 路由一览（enc 可选 none|ke|fixed，默认 none）：
 *   index.php?r=config                 远程主配置（App 入口，定义左上角名称/首页推荐）
 *   index.php?r=api                    点播源配置（TVBox 标准 JSON）
 *   index.php?r=live                   直播源（JSON，支持密文；可选 format=txt 输出传统文本源）
 *   index.php?r=live&format=txt        直播源（传统 txt 文本，分组,#genre# / 频道,url#url2）
 *   index.php?r=epg                    节目单（JSON，支持密文，可多天）
 *   index.php?r=market                 应用市场列表（模板占位符）
 *   index.php?r=detail&pkg=xxx         应用市场详情
 *   index.php?r=upgrade                升级检查
 *   index.php?r=crypto                 加密演示/联调
 *
 * 所有响应都可通过 enc 参数切换为 ke 前缀密文或固定密钥密文。
 */

require_once __DIR__ . '/tvbox_crypto.php';

$route = isset($_GET['r']) ? strtolower(trim((string)$_GET['r'])) : '';
$dev = tk_device();

/** 生成带当前加密方式的相对接口地址（未带协议，客户端自动以主配置地址所在目录拼接） */
function tk_rel_url(string $query, string $enc): string
{
    $url = 'index.php?' . $query;
    if ($enc !== 'none' && $enc !== '') {
        $url .= '&enc=' . rawurlencode($enc);
    }
    return $url;
}

/** 当前部署目录的绝对地址（用于必须带域名/IP 的接口，例如点播 api 内的 lives[].url） */
function tk_base_url(): string
{
    $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || (($_SERVER['SERVER_PORT'] ?? '') === '443');
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
    return ($https ? 'https://' : 'http://') . $host . $dir . '/';
}

/** 绝对接口地址（同样可带 enc） */
function tk_abs_url(string $query, string $enc): string
{
    $url = tk_base_url() . 'index.php?' . $query;
    if ($enc !== 'none' && $enc !== '') {
        $url .= '&enc=' . rawurlencode($enc);
    }
    return $url;
}

/** 取直播 URL 条目中的地址文本：兼容 "地址$线路名" 字符串与 ['url'=>..., 'name'=>..., 'decode'=>...] 对象 */
function tk_live_url_text($item): string
{
    if (is_string($item)) {
        return trim($item);
    }
    if (is_array($item)) {
        $url = trim((string)($item['url'] ?? $item['src'] ?? ''));
        $name = trim((string)($item['name'] ?? ''));
        return ($url !== '' && $name !== '') ? $url . '$' . $name : $url;
    }
    return '';
}

/**
 * 把 JSON 直播结构转成传统 txt 文本源（分组,#genre# / 频道,url#url2）。
 * 注意：txt 格式本身不携带 decode 字段，需要逐频道/线路设置解码方式时请使用 JSON 格式。
 */
function tk_live_to_txt(array $live): string
{
    $lines = [];
    foreach ($live as $group) {
        if (!is_array($group)) continue;
        $groupName = trim((string)($group['group'] ?? $group['name'] ?? '未分组'));
        if ($groupName === '') $groupName = '未分组';
        $lines[] = $groupName . ',#genre#';
        $channels = $group['channels'] ?? [];
        if (!is_array($channels)) continue;
        foreach ($channels as $channel) {
            if (!is_array($channel)) continue;
            $channelName = trim((string)($channel['name'] ?? ''));
            if ($channelName === '') continue;
            $urls = [];
            $items = $channel['urls'] ?? [];
            if (!is_array($items)) {
                $items = isset($channel['url']) ? [$channel['url']] : [];
            }
            foreach ($items as $item) {
                $url = tk_live_url_text($item);
                if ($url !== '') $urls[] = $url;
            }
            if (empty($urls)) continue;
            $lines[] = $channelName . ',' . implode('#', $urls);
        }
    }
    return implode("\n", $lines) . (empty($lines) ? '' : "\n");
}

/** 应用市场列表（演示数据，可按 MAC/关键词下发） */
function tk_market_list(array $dev): array
{
    $category = isset($_GET['category']) ? (string)$_GET['category'] : 'all';
    $sort = isset($_GET['sort']) ? (string)$_GET['sort'] : 'recommend';
    $page = max(1, (int)($_GET['page'] ?? 1));
    $pageSize = min(100, max(1, (int)($_GET['pageSize'] ?? 24)));
    $keyword = trim((string)($_GET['keyword'] ?? ''));

    $apps = [
        [
            'packageName' => 'com.demo.player',
            'name' => 'Demo 播放器',
            'versionName' => '1.0.0',
            'versionCode' => 1,
            'icon' => 'https://placehold.co/180x180/png',
            'size' => '12MB',
            'downloads' => 12345,
            'category' => 'app',
            'summary' => '演示应用：支持 TV 遥控器操作',
            'description' => '这是 TKLauncher 应用市场接口演示数据，请替换为真实下载地址。',
            'screenshots' => [],
            'downloadUrl' => 'https://your-domain/demo-player.apk',
            'developer' => 'TKLauncher',
            'updateTime' => '2026-01-01',
        ],
        [
            'packageName' => 'com.demo.game',
            'name' => 'Demo 游戏',
            'versionName' => '2.0.0',
            'versionCode' => 2,
            'icon' => 'https://placehold.co/180x180/png',
            'size' => '30MB',
            'downloads' => 888,
            'category' => 'game',
            'summary' => '演示游戏',
            'description' => '示例游戏数据。',
            'screenshots' => [],
            'downloadUrl' => 'https://your-domain/demo-game.apk',
            'developer' => 'TKLauncher',
            'updateTime' => '2026-02-01',
        ],
    ];

    if ($category === 'app' || $category === 'game') {
        $apps = array_values(array_filter($apps, function ($a) use ($category) {
            return $a['category'] === $category;
        }));
    }
    if ($keyword !== '') {
        $kw = mb_strtolower($keyword, 'UTF-8');
        $apps = array_values(array_filter($apps, function ($a) use ($kw) {
            $name = mb_strtolower($a['name'], 'UTF-8');
            $pkg = mb_strtolower($a['packageName'], 'UTF-8');
            // 演示用简单匹配：首字符相等或包含关键词。
            // 生产环境建议接入拼音库（如 overtrue/pinyin），把 name 转成首字母串（微信→WX）后再匹配。
            return mb_substr($name, 0, 1, 'UTF-8') === $kw
                || mb_substr($pkg, 0, 1, 'UTF-8') === $kw
                || strpos($name, $kw) !== false
                || strpos($pkg, $kw) !== false;
        }));
    }
    if ($sort === 'download') {
        usort($apps, function ($a, $b) {
            return $b['downloads'] - $a['downloads'];
        });
    }

    $total = count($apps);
    $list = array_slice($apps, ($page - 1) * $pageSize, $pageSize);

    return [
        'data' => [
            'total' => $total,
            'page' => $page,
            'pageSize' => $pageSize,
            'list' => $list,
        ],
        'device' => $dev, // 演示：服务端可按 $_GET['mac'] 返回不同内容
    ];
}

switch ($route) {
    case 'config':
        $enc = isset($_GET['enc']) ? (string)$_GET['enc'] : 'none';
        /**
         * ======================================================================
         * 主配置（TKLauncher 全量系统配置）
         * ======================================================================
         *
         * 本配置是 App 的「唯一配置来源」——应用内已无设置页，
         * 所有功能开关/接口地址/主题/入口等均由本文件下发。
         *
         * 【接口地址规则】
         *   - api（点播接口）：必须是完整 URL，返回 TVBox 标准 JSON
         *   - live / epg / proxy / upgrade.checkUrl / appMarket.url：
         *     未写 http(s):// 时自动拼接主配置所在服务器的域名+目录
         *     （例如本文件在 http://yaochi.nps.tianku.net:8080/php-demo/ 下，
         *      live 填 "index.php?r=live" → 自动解析为完整地址）
         *
         * 【加密】
         *   - enc=none  明文（默认）
         *   - enc=ke    ke 前缀模式（推荐，零配置）
         *   - enc=fixed 固定密钥模式（需在 secretKey 字段填写密钥）
         *
         * 【所有字段均可选】
         *   未配置的字段保持 App 内置默认值
         *
         * 【本机参数自动注入】
         *   所有接口请求自动附加 mac/ip/model/brand/device/sdk/release/app 参数
         *   （服务端可用 $_GET['mac'] 读取）
         * ======================================================================
         */
        $config = [
            // ==================================================================
            // 【二次远程配置（可选）】
            //   若主配置为本地文件，可填这里实现二次覆盖；
            //   主配置本身已经是远程时，建议留空，避免重复请求。
            'remoteConfigUrl' => '',
            'useRemoteConfig' => false,  // true=强制覆盖 settings/bootStart（需主配置在远程可访问）
            'secretKey' => '',           // 固定密钥加密的秘钥；空=用客户端 ConfigCrypto.SECRET_KEY
            // ==================================================================

            // ==================================================================
            // 【接口地址】
            //   说明：相对地址自动拼主配置服务器域名+目录
            'api'   => tk_abs_url('r=api', $enc),   // 点播源（TVBox 标准 JSON，含 sites/lives/parses）
            'live'  => tk_rel_url('r=live', $enc),  // 直播源（JSON / txt / m3u 均可）
            'epg'   => tk_rel_url('r=epg', $enc),   // EPG 节目单（JSON，支持多天）
            'proxy' => '',                            // 代理地址（本地代理用，非服务器接口）
            // 图片占位图：所有海报/背景图下载完成前先显示该图（为空=内置占位图）
            'placeholderImage' => '',
            // 接口强制覆盖：false/不配置=用户手动配置的接口优先；true=全部强制覆盖；
            // 也可用对象逐项控制： ['api'=>true,'live'=>false,'epg'=>false,'proxy'=>false]
            'forceOverride' => false,
            // 默认加载的界面 id：standard=当前标准首页；其它值对应下方 screens 中的 id
            'defaultScreen' => 'standard',
            // ==================================================================

            // ==================================================================
            // 【全局设置】
            'bootStart'            => true,          // TVBox 开机自启
            'notice'               => '欢迎使用 TKLauncher',  // 滚动公告（字符串/对象均可）
            'epgDays'              => 3,             // EPG 显示天数（1~9，默认3=昨天/今天/明天）
            'checkInterval'        => 30,            // 配置检查间隔（分钟）
            'loadSuccessDuration'  => 3,             // 点播接口加载成功通知自动关闭秒数（0~60）
            'warningAutoClose'     => 0,             // 免责声明弹窗自动关闭秒数（0=需手动点确定）
            'ua'                   => '',            // 全局 UA（空=内置浏览器 UA）
            'aliLicenseKey'        => '',            // 阿里云播放器 SDK License Key（5.5+ 必配，否则弹提示）
            'hin2nEntry'           => true,          // 首页是否显示组网固定入口
            'theme'                => '',            // 默认主题（索引/中文名/英文 alias，空=跟随用户选择）
            // ==================================================================

            // ==================================================================
            // 【首页外观】
            'home' => [
                'bgImage'       => '',               // 全局背景图（所有页面生效）
                'sourceName'    => 'TKLauncher',      // 首页左上角固定名称（空=显示 App 名称）
                'entryBgColor'  => '',               // 顶栏/入口卡片底色
                'iconColor'     => '',               // 图标着色（空=白色）
                'textColor'     => '',               // 顶栏文字颜色（空=白色）
                'alpha'         => 100,              // 入口卡片透明度 0~100
                'inputBgColor'  => '',               // 输入框/按钮底色
                'buttonBgColor'  => '',
                'show'          => ['home' => true, 'live' => true, 'vod' => true],
                // 首页推荐影片（配置后不再请求豆瓣）：
                //  name=片名（必填） pic=封面 note=角标 id=影片ID sourceKey=站点key
                //  配了 id+sourceKey 点击进详情；仅 name 时点击按片名搜索
                'recMovies'     => [
                    // ['name' => '示例影片', 'pic' => 'https://example.com/pic.jpg', 'note' => '9.0'],
                ],
            ],
            // ==================================================================

            // ==================================================================
            // 【tkp2p:// 原生 P2P 直播】
            'tkp2p' => [
                'engine'    => 'auto',   // auto / hls / media
                'announce'  => [],       // WebRTC tracker 列表
                'token'     => '',       // SwarmCloud token（media 引擎用）
                'zone'      => 'cn',     // tracker 区域：eu / hk / us / cn
                'iceServers' => [],      // WebRTC ICE servers
            ],
            // ==================================================================

            // ==================================================================
            // 【主题】
            //   带 "style":0..13 → 覆盖对应内置主题（索引不变，用户已选主题不错位）
            //   不带 style → 追加自定义主题（color 必填）
            'themes' => [[
                'name'   => '默认蓝色',
                'alias'  => 'default',
                'color'  => '#0897D3',
                'bgStart' => '#022D53',
                'bgEnd'  => '#000000',
            ]],
            // ==================================================================

            // ==================================================================
            // 【全部功能设置】（应用内无设置页，本段为唯一来源，每次加载强制生效）
            'settings' => [
                // ---- 首页 ----
                'homeShowSource'     => true,    // 显示当前源名称
                'homeSearchPosition' => false,   // 搜索按钮位置：true=上方顶栏 false=下方历史行
                'homeRec'            => 1,       // 首页推荐：0/1=主接口 home.recMovies（首页未配置回退豆瓣；点播首屏固定显示该字段，未配置为空态） 2=观看历史
                'homeNum'            => 4,       // 历史条数档位：0=20 1=40 2=60 3=80 4=100
                'homeRecStyle'       => true,    // true=多行网格 false=单行横滑
                'homeDefaultShow'    => false,   // true=启动直接进直播
                'showPreview'        => true,    // 小窗预览
                // ---- 播放器 ----
                'playScale'          => 0,       // 画面缩放：0=默认 1=16:9 2=4:3 3=填充 4=原始 5=裁剪
                'backgroundPlayType' => 0,       // 后台播放：0=关闭 1=开启 2=画中画
                'playType'           => 1,       // 默认播放器：0=系统 1=IJK 2=Exo 3=MX
                'ijkCodec'           => '硬解码', // IJK 解码："硬解码" / "软解码"
                'ijkCachePlay'       => false,   // IJK 边播边缓存
                'exoRenderer'        => 0,       // Exo 扩展渲染器索引
                'exoRendererMode'    => 1,       // Exo 渲染器模式索引
                'vodPlayerPreferred' => 0,       // 点播首选播放器索引
                'playRender'         => 0,       // 渲染方式：0=TextureView 1=SurfaceView
                'videoPurify'        => true,    // 视频去广告（嗅探净化）
                // ---- 系统 ----
                'homeLocale'         => 0,       // 语言：0=中文 1=英文
                'themeSelect'        => 0,       // 默认主题序号（0~13，对应 themes 清单）
                'searchView'         => 1,       // 搜索展示：0=文字列表 1=缩略图
                'parseWebview'       => true,    // 嗅探内核：true=系统 WebView false=XWalkView
                'dohUrl'             => 0,       // 安全 DNS：0=关闭 1=腾讯 2=阿里 3=360 4=Google 5=AdGuard 6=Quad9
                'fastSearchMode'     => false,   // 聚合搜索（快搜）
                'debugOpen'          => false,   // 调试模式
            ],
            // ==================================================================

            // ==================================================================
            // 【自定义 App 入口】（type=app/feature；vod 走 topEntries/homeEntries）
            'entries' => [],
            // ==================================================================

            // ==================================================================
            // 【顶栏入口】（非空数组会覆盖内置默认；空数组/删除则回退内置）
            //   name=显示名 type=page/feature/app/vod target=目标 icon=图标
            'topEntries' => [
                ['name' => '网络', 'type' => 'feature', 'target' => 'wifi',        'icon' => ''],
                ['name' => '搜索', 'type' => 'page',    'target' => 'search',      'icon' => 'hm_search'],
                ['name' => '单双排', 'type' => 'feature', 'target' => 'styleSwitch', 'icon' => ''],
                ['name' => '主题', 'type' => 'feature', 'target' => 'themeSwitch', 'icon' => 'ic_theme'],
                ['name' => '应用', 'type' => 'page',    'target' => 'apps',        'icon' => 'hm_drawer'],
                ['name' => '设置', 'type' => 'page',    'target' => 'setting',     'icon' => 'hm_settings'],
                ['name' => '组网', 'type' => 'page',    'target' => 'hin2n',       'icon' => 'ic_n2n'],
            ],
            // ==================================================================

            // ==================================================================
            // 【首页历史一行入口】（非空数组会覆盖内置默认；空数组/删除则回退内置）
            'homeEntries' => [
                ['name' => '历史', 'type' => 'page', 'target' => 'history', 'icon' => 'hm_history'],
                ['name' => '点播', 'type' => 'page', 'target' => 'vod',     'icon' => 'hm_vod'],
                ['name' => '直播', 'type' => 'page', 'target' => 'live',    'icon' => 'hm_live'],
                ['name' => '搜索', 'type' => 'page', 'target' => 'search',  'icon' => 'hm_search'],
                ['name' => '收藏', 'type' => 'page', 'target' => 'collect', 'icon' => 'hm_fav'],
                ['name' => '推送', 'type' => 'page', 'target' => 'push',    'icon' => 'hm_push'],
                ['name' => '文件', 'type' => 'page', 'target' => 'drive',   'icon' => 'hm_folder'],
                ['name' => '设置', 'type' => 'page', 'target' => 'setting', 'icon' => 'hm_settings'],
                ['name' => '组网', 'type' => 'page', 'target' => 'hin2n',   'icon' => 'ic_hin2n'],
                ['name' => '系统', 'type' => 'page', 'target' => 'system',  'icon' => 'hm_settings'],
                ['name' => '捐助', 'type' => 'page', 'target' => 'donate',  'icon' => 'hm_fav'],
            ],
            // ==================================================================

            // ==================================================================
            // 【界面（屏幕）/ 酒店 IPTV 桌面】
            //  每个界面可单独配置接口；layout 支持 hotel_grid / hotel_list / hotel_showcase
            //  layoutUrl=布局插件 JSON 地址（按需下载缓存本地，便于只改 JSON 开发新界面）
            //  entries 非空时覆盖默认入口（name/type/target/icon/pic/desc）
            'screens' => [
                // [
                //     'id' => 'hotel_demo',
                //     'name' => '酒店桌面示例',
                //     'layout' => 'hotel_grid',
                //     'api' => '', 'live' => '', 'epg' => '',
                //     'title' => '欢迎入住',
                //     'logo' => '',
                //     'background' => '',
                //     'ticker' => '前台电话 8888    祝您入住愉快',
                //     'columns' => 3,
                //     'rows' => 2,
                //     'showClock' => true,
                //     'layoutUrl' => '',
                //     'entries' => [
                //         ['name' => '点播', 'type' => 'page', 'target' => 'vod', 'icon' => 'hm_vod'],
                //         ['name' => '直播', 'type' => 'page', 'target' => 'live', 'icon' => 'hm_live'],
                //         ['name' => '切换界面', 'type' => 'feature', 'target' => 'screenSwitch', 'icon' => 'hm_settings'],
                //     ],
                // ],
            ],
            // ==================================================================

            // ==================================================================
            // 【应用市场】
            //   url 模板支持 {category}(all/app/game) {sort}{page}{pageSize}{keyword}
            //   空=仅使用下方 apps 数组
            'appMarket' => [
                'url'       => tk_rel_url('r=market&category={category}&sort={sort}&page={page}&pageSize={pageSize}&keyword={keyword}', $enc),
                'detailUrl' => tk_rel_url('r=detail&pkg={packageName}', $enc),
                'headers'   => [],
                'pageSize'  => 24,
                'apps'      => [],
            ],
            // ==================================================================

            // ==================================================================
            // 【升级检查】
            //   checkUrl 留空则用内联 versionCode/url；versionCode>当前版本才提示
            'upgrade' => [
                'checkUrl'   => tk_rel_url('r=upgrade', $enc),
                'versionCode' => 0,
                'versionName' => '',
                'url'        => '',
                'force'      => false,
                'notes'      => '演示升级接口：把 versionCode 调大于当前客户端版本即可触发升级提示',
            ],
            // ==================================================================

            // ==================================================================
            // 【捐助界面】
            'donate' => [
                'title' => '支持我们',
                'desc'  => '替换为你的项目介绍',
                'image' => '',
                'tip'   => '',
            ],
            // ==================================================================

            'device' => $dev,  // 示例：服务端可按 $_GET['mac'] 返回不同内容
        ];
        tk_output(json_encode($config, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        break;

    case 'api':
        $enc = isset($_GET['enc']) ? (string)$_GET['enc'] : 'none';
        /**
         * ======================================================================
         * 点播源配置（TVBox 标准 JSON 格式）
         * ======================================================================
         *
         * 【核心参数说明】
         *   type=0  → XML 格式（老 MacCMS 接口，少见）
         *   type=1  → JSON 格式（标准 MacCMS JSON API，最通用，公开源大多是这个）
         *   type=3  → CatVod Spider（jar/js/py 实现，本项目主力类型）
         *
         *   api 字段根据 type 不同：
         *     type=0/1：填接口 URL，支持 ac=class（分类）、ac=list（列表）、
         *               ac=search（搜索）、ac=detail（详情）、ac=videolist（详情别名）等
         *     type=3  ：填 Spider 文件路径（.jar/.js/.py）或类名（需 spider 字段指向 jar）
         *
         *   其他字段：
         *   searchable=1  可搜索（0=不可搜索）
         *   quickSearch=1 快速搜索（输入时实时搜；0=回车才搜）
         *   filter=1      有分类筛选（0=没有）
         *   changeable=1  允许切源/切线路（0=固定源）
         *   playerType    -1=跟随全局设置 0=系统 1=Ijk 2=Exo 10=MX
         *   ext           Spider 扩展参数（字符串或 JSON 对象）
         *
         * 【替换指引】
         *   1. 把 "电影天堂（公开测试源）" 的 api 替换为你自己的 MacCMS 接口；
         *   2. 或添加 CatVod Spider（type=3），api 填 Spider 文件的远程 URL；
         *   3. 保留本 demo 的 lives 字段，它会指向下方 case 'live' 返回的直播源。
         *
         * 【常用公开源（type=1）】
         *   - 电影天堂：http://caiji.dyttzyapi.com/api.php/provide/vod/from/dyttm3u8/at/json/
         *   - 更多公开源请在网络搜索 "MacCMS api 公开"
         * ======================================================================
         */
        $vod = [
            'sites' => [
                [
                    'key' => 'dytt',
                    'name' => '电影天堂（公开测试源）',
                    'type' => 1,
                    'api' => 'http://caiji.dyttzyapi.com/api.php/provide/vod/from/dyttm3u8/at/json/',
                    'searchable' => 1,
                    'quickSearch' => 1,
                    'filter' => 1,
                    'ext' => '',
                    'changeable' => 0,
                    'playerType' => -1,
                ],
                [
                    'key' => 'your-source',
                    'name' => '你的点播源（替换这里）',
                    'type' => 1,
                    'api' => 'https://your-cms.example.com/api.php/provide/vod/',
                    'searchable' => 1,
                    'quickSearch' => 1,
                    'filter' => 1,
                    'ext' => '',
                    'changeable' => 1,
                    'playerType' => -1,
                ],
            ],
            'lives' => [[
                'name' => '直播源',
                'type' => 0,
                'url' => tk_abs_url('r=live', $enc),
                'playerType' => 1,
            ]],
            'parses' => [
                [
                    'name' => '通用 JSON 解析（示例）',
                    'type' => 0,
                    'url' => 'http://jx.abplayer.top/?url=',
                    'ext' => [
                        'flag' => ['youku', 'qq', 'iqiyi', 'qiyi', 'letv', 'sohu', 'mgtv'],
                    ],
                ],
            ],
            'flags' => ['youku', 'qq', 'iqiyi', 'qiyi', 'letv', 'sohu', 'tudou', 'pptv', 'mgtv', 'wasu', 'bilibili'],
        ];
        tk_output(json_encode($vod, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        break;

    case 'live':
        /**
         * ======================================================================
         * 直播源配置（推荐 JSON 格式；客户端同时兼容 txt/m3u 文本源）
         * ======================================================================
         *
         * 【JSON 格式说明】
         *   顶层是分组数组，每个分组含频道列表：
         *   [
         *     {"group": "分组名", "channels": [
         *       {"name": "频道名", "decode": "soft", "urls": [
         *           "直链1",
         *           {"url": "直链2", "name": "线路名", "decode": "hard"}
         *       ]}
         *     ]}
         *   ]
         *   - urls 数组可放多个播放地址，"地址$线路名" 格式可为地址命名
         *   - 也可写成 {"groups": [...]} 对象形式
         *
         * 【每频道/线路解码方式 decode（可选）】
         *   decode 取值：soft=软解 hard=硬解 system=系统 exo=Exo ali=AliPlayer auto=跟随全局
         *   - 频道对象上写 "decode":"soft"：该频道下所有线路默认使用该解码方式；
         *   - urls 写成 [{"url":"地址","name":"线路名","decode":"hard"}]：为单条线路设置独立解码；
         *   - 也兼容 "playerType":-1/0/1/2/3（-1=跟随全局 0=系统 1=Ijk 2=Exo 3=Ali）与平行数组 "decodes":["soft","hard"]；
         *   - 客户端仅在用户未手动设置该线路解码时采用该默认值，播放中手动调整后以用户设置为准。
         *
         * 【txt 文本输出】
         *   请求 index.php?r=live&format=txt 可返回传统文本源（分组,#genre# / 频道,地址#地址）：
         *   客户端同样兼容，但 txt 格式本身不携带 decode 字段；需要逐频道/线路解码请使用 JSON 格式。
         *
         * 【支持的播放协议】
         *   http(s)://   直链 m3u8/flv/mp4 等常规流媒体（可含 ?UA=.../Referer=... 参数）
         *   rtmp://、rtsp:// 传统推流/监控协议
         *   webview://   WebView 直接播放网页（页面自带播放器时）
         *   video://     WebView 嗅探网页中的视频流播放（无直链的网页直播）
         *   tkp2p://     内置 P2P 直播（引擎由主配置 tkp2p 段控制）
         *
         * 【替换指引】
         *   1. 把示例 URL 替换为你自己能用的直播直链（可以从网络搜索 IPTV M3U）；
         *   2. 或直接用外部 M3U 文本源：主配置 live 字段填 http://你的域名/tv.m3u
         *      客户端会自动解析 M3U 格式（无需改本文件）；
         *   3. 频道名用于匹配 EPG 节目单（EPG 接口按 ch/name 参数返回节目表）。
         *
         * 【常用公开直播源】
         *   - 移动 IPTV：http://iptv.ruyitv.cc/m3u/tv.txt
         *   - 其他公开源请搜索 "IPTV m3u 公开"
         * ======================================================================
         */
        $live = [
            ['group' => '央视', 'channels' => [
                // 频道级 decode：该频道所有线路默认软解；线路对象里的 decode 可单独覆盖
                ['name' => 'CCTV-1 综合', 'decode' => 'soft', 'urls' => [
                    'http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226214/index.m3u8',
                    ['url' => 'https://example.com/cctv1-2.m3u8', 'name' => '备用线路', 'decode' => 'hard'],
                ]],
                ['name' => 'CCTV-2 财经', 'urls' => ['http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226204/index.m3u8']],
                ['name' => 'CCTV-13 新闻', 'urls' => ['http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226357/index.m3u8']],
                ['name' => 'CCTV-5 体育', 'urls' => ['http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226207/index.m3u8']],
            ]],
            ['group' => '卫视', 'channels' => [
                ['name' => '湖南卫视', 'decode' => 'hard', 'urls' => ['http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226224/index.m3u8']],
                ['name' => '浙江卫视', 'urls' => ['http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226225/index.m3u8']],
                ['name' => '东方卫视', 'urls' => ['http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226226/index.m3u8']],
                ['name' => '北京卫视', 'urls' => ['http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226227/index.m3u8']],
            ]],
            ['group' => '示例协议', 'channels' => [
                ['name' => 'WebView 直播', 'urls' => ['webview://https://example.com/live/page.html']],
                ['name' => 'Video 嗅探', 'urls' => ['video://https://example.com/live/video.html']],
                ['name' => 'TKP2P P2P直播', 'urls' => ['tkp2p://https://example.com/live/p2p.m3u8']],
                ['name' => 'RTMP 示例', 'urls' => ['rtmp://example.com/live/stream']],
                ['name' => 'RTSP 示例', 'urls' => ['rtsp://example.com:554/stream']],
            ]],
        ];
        // format=txt → 返回传统文本源（客户端兼容；txt 不携带 decode）
        $format = strtolower(trim((string)($_GET['format'] ?? 'json')));
        if ($format === 'txt') {
            tk_output(tk_live_to_txt($live), 'text/plain; charset=utf-8');
        } else {
            tk_output(json_encode($live, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 'application/json; charset=utf-8');
        }
        break;

    case 'epg':
        /**
         * ======================================================================
         * EPG 节目单接口（JSON 格式，支持多天下发）
         * ======================================================================
         *
         * 【请求参数】
         *   ch / name  = 频道名（如 "CCTV-1"），客户端发起请求时自动附加
         *   date       = 日期 yyyy-MM-dd（缺省用今天）
         *   days       = 天数窗口 1~9（缺省 1）
         *                客户端会以今天为中心请求多天下发，一次返回窗口内全部日期，
         *                客户端按天拆分并缓存到本地，大幅降低重复请求。
         *
         * 【返回格式】
         *   {"epg_data": [
         *     {"date":"yyyy-MM-dd", "start":"HH:mm", "end":"HH:mm", "title":"节目名"},
         *     ...
         *   ]}
         *
         * 【替换指引】
         *   1. 本 demo 返回示例数据（上午/下午/晚间三段），实际生产应接入真实
         *      EPG 数据源，例如：
         *        - https://epg.112114.eu.org/?ch={name}&date={date}
         *        - https://epg.112114.xyz/（若可用）
         *   2. 接入真实 EPG 时，主配置的 epg 字段直接填外部接口地址即可，
         *      无需改本文件（客户端自动解析 JSON）；
         *   3. 历史日期的节目单客户端会永久缓存，未来日期仅当天内有效，
         *      跨天自动重新拉取。
         * ======================================================================
         */
        $ch = trim((string)($_GET['ch'] ?? $_GET['name'] ?? ''));
        if ($ch === '') {
            $ch = 'CCTV-1';
        }
        $dateStr = trim((string)($_GET['date'] ?? ''));
        $base = strtotime($dateStr . ' 00:00:00');
        if ($base === false) {
            $base = strtotime(date('Y-m-d') . ' 00:00:00');
        }
        $days = (int)($_GET['days'] ?? 1);
        $days = max(1, min(9, $days));
        // 与客户端日期列表口径一致：以基准日为中心，之前的日期数 = (days-1)/2
        $before = (int)(($days - 1) / 2);
        $epgData = [];
        for ($i = -$before; $i < $days - $before; $i++) {
            $day = date('Y-m-d', strtotime(($i >= 0 ? '+' : '') . $i . ' day', $base));
            foreach ([
                ['08:00', '12:00', '上午节目（示例）'],
                ['12:00', '18:00', '下午节目（示例）'],
                ['18:00', '23:59', '晚间节目（示例）'],
            ] as $prog) {
                $epgData[] = [
                    'date' => $day,
                    'start' => $prog[0],
                    'end' => $prog[1],
                    'title' => $ch . ' ' . $prog[2],
                ];
            }
        }
        tk_output(json_encode(['epg_data' => $epgData], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 'application/json; charset=utf-8');
        break;

    case 'market':
        tk_output(json_encode(tk_market_list($dev), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        break;

    case 'detail':
        $pkg = (string)($_GET['pkg'] ?? $_GET['packageName'] ?? '');
        $detail = [
            'packageName' => $pkg,
            'name' => '应用详情演示',
            'versionName' => '1.0.0',
            'versionCode' => 1,
            'icon' => 'https://placehold.co/180x180/png',
            'size' => '12MB',
            'downloads' => 100,
            'category' => 'app',
            'summary' => '详情接口演示',
            'description' => '根据 packageName 返回详情。',
            'screenshots' => [],
            'downloadUrl' => 'https://your-domain/demo.apk',
            'developer' => 'TKLauncher',
            'updateTime' => '2026-01-01',
            'device' => $dev,
        ];
        tk_output(json_encode($detail, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        break;

    case 'upgrade':
        $upgrade = [
            'versionCode' => 0,
            'versionName' => '',
            'url' => '',
            'force' => false,
            'notes' => '演示：把 versionCode 调大于当前客户端版本即可触发升级提示。',
            'device' => $dev,
        ];
        tk_output(json_encode($upgrade, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        break;

    case 'crypto':
        $sample = json_encode([
            'mac' => $dev['mac'],
            'msg' => 'TKLauncher crypto demo',
        ], JSON_UNESCAPED_UNICODE);
        tk_output(json_encode([
            'plain' => $sample,
            'ke' => tk_encrypt_ke($sample),
            'fixed' => tk_encrypt_fixed($sample, getenv('TK_SECRET_KEY') ?: 'tkTVBoxDemoKey16'),
        ], JSON_UNESCAPED_UNICODE), 'application/json; charset=utf-8');
        break;

    default:
        header('Content-Type: text/html; charset=utf-8');
        echo '<h3>TKLauncher PHP demo</h3><ul>'
            . '<li><a href="?r=config">远程主配置</a></li>'
            . '<li><a href="?r=api">点播源配置</a></li>'
            . '<li><a href="?r=live">直播源</a></li>'
            . '<li><a href="?r=epg">EPG</a></li>'
            . '<li><a href="?r=market">应用市场</a></li>'
            . '<li><a href="?r=detail&amp;pkg=com.demo.player">应用详情</a></li>'
            . '<li><a href="?r=upgrade">升级检查</a></li>'
            . '<li><a href="?r=crypto">加密演示</a></li>'
            . '<li>加密输出：在任意地址后追加 &amp;enc=ke 或 &amp;enc=fixed</li>'
            . '</ul>';
        break;
}
