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
 *   index.php?r=live                   直播源（JSON，支持密文，含全部节目类型示例）
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
        // 与 APK 内 JSON 配置一一对应的全量系统配置（所有字段可选，缺省走 App 内置默认）
        $config = [
            // ---- 配置入口 ----
            'remoteConfigUrl' => '',       // 二次远程覆盖地址（空=不启用）
            'useRemoteConfig' => false,    // true 且远程可访问时：远程强制覆盖 settings/bootStart/接口地址
            'secretKey' => '',             // 固定密钥加密的秘钥；空=用客户端 ConfigCrypto.SECRET_KEY
            // ---- 接口地址（未写协议时自动拼主配置地址的域名/IP + 目录） ----
            'api' => tk_abs_url('r=api', $enc),
            'live' => tk_rel_url('r=live', $enc),
            'epg' => tk_rel_url('r=epg', $enc),
            'proxy' => '',
            // ---- 全局 ----
            'bootStart' => true,
            'notice' => '欢迎使用桌面版 TKLauncher（PHP demo）',
            'epgDays' => 3,
            'checkInterval' => 30,
            'loadSuccessDuration' => 3,
            'warningAutoClose' => 0,
            'ua' => '',
            'aliLicenseKey' => '',
            'hin2nEntry' => true,
            'theme' => '',
            // ---- 首页外观 ----
            'home' => [
                'bgImage' => '',
                // 左上角显示的名称：主接口统一定义，首页始终显示此文字（空=显示 App 名称）
                'sourceName' => 'TKLauncher',
                'entryBgColor' => '',
                'iconColor' => '',
                'textColor' => '',
                'alpha' => 100,
                'inputBgColor' => '',
                'buttonBgColor' => '',
                'show' => ['home' => true, 'live' => true, 'vod' => true],
                // 首页推荐数据：由主接口直接提供（配置后不再请求豆瓣/点播接口）。
                // id+sourceKey 存在时点击进详情；仅 name 时点击按名称聚合搜索。
                'recMovies' => [
                    ['name' => '演示影片一', 'pic' => 'https://placehold.co/300x400/png', 'note' => '9.0'],
                    ['name' => '演示影片二', 'pic' => 'https://placehold.co/300x400/png', 'note' => '8.5'],
                    ['name' => '演示影片三', 'pic' => 'https://placehold.co/300x400/png', 'note' => '8.0'],
                ],
            ],
            // ---- tkp2p:// 原生 P2P 直播 ----
            'tkp2p' => [
                'engine' => 'auto',
                'announce' => [],
                'token' => '',
                'zone' => 'cn',
                'iceServers' => [],
            ],
            // ---- 主题（带 style=覆盖内置主题；不带 style=追加自定义主题） ----
            'themes' => [[
                'name' => '默认',
                'alias' => 'default',
                'color' => '#0897D3',
                'bgStart' => '#022D53',
                'bgEnd' => '#000000',
            ]],
            // ---- 全部功能设置（应用内无设置页，本段为唯一来源） ----
            'settings' => [
                'homeShowSource' => true,
                'homeSearchPosition' => false,
                'homeRec' => 1,
                'homeNum' => 4,
                'homeRecStyle' => true,
                'homeDefaultShow' => false,
                'showPreview' => true,
                'playScale' => 0,
                'backgroundPlayType' => 0,
                'playType' => 1,
                'ijkCodec' => '硬解码',
                'ijkCachePlay' => false,
                'exoRenderer' => 0,
                'exoRendererMode' => 1,
                'vodPlayerPreferred' => 0,
                'playRender' => 0,
                'videoPurify' => true,
                'homeLocale' => 0,
                'themeSelect' => 0,
                'searchView' => 1,
                'parseWebview' => true,
                'dohUrl' => 0,
                'fastSearchMode' => false,
                'debugOpen' => false,
            ],
            // ---- 自定义 App 入口（type=app/feature；vod 走 topEntries/homeEntries） ----
            'entries' => [],
            // ---- 顶栏入口（非空数组会覆盖内置默认） ----
            'topEntries' => [
                ['name' => '网络', 'type' => 'feature', 'target' => 'wifi', 'icon' => ''],
                ['name' => '搜索', 'type' => 'page', 'target' => 'search', 'icon' => 'hm_search'],
                ['name' => '单双排', 'type' => 'feature', 'target' => 'styleSwitch', 'icon' => ''],
                ['name' => '主题', 'type' => 'feature', 'target' => 'themeSwitch', 'icon' => 'ic_theme'],
                ['name' => '应用', 'type' => 'page', 'target' => 'apps', 'icon' => 'hm_drawer'],
                ['name' => '设置', 'type' => 'page', 'target' => 'setting', 'icon' => 'hm_settings'],
                ['name' => '组网', 'type' => 'page', 'target' => 'hin2n', 'icon' => 'ic_n2n'],
            ],
            // ---- 首页历史一行入口（非空数组会覆盖内置默认） ----
            'homeEntries' => [
                ['name' => '历史', 'type' => 'page', 'target' => 'history', 'icon' => 'hm_history'],
                ['name' => '点播', 'type' => 'page', 'target' => 'vod', 'icon' => 'hm_vod'],
                ['name' => '直播', 'type' => 'page', 'target' => 'live', 'icon' => 'hm_live'],
                ['name' => '搜索', 'type' => 'page', 'target' => 'search', 'icon' => 'hm_search'],
                ['name' => '收藏', 'type' => 'page', 'target' => 'collect', 'icon' => 'hm_fav'],
                ['name' => '推送', 'type' => 'page', 'target' => 'push', 'icon' => 'hm_push'],
                ['name' => '文件', 'type' => 'page', 'target' => 'drive', 'icon' => 'hm_folder'],
                ['name' => '设置', 'type' => 'page', 'target' => 'setting', 'icon' => 'hm_settings'],
                ['name' => '组网', 'type' => 'page', 'target' => 'hin2n', 'icon' => 'ic_hin2n'],
                ['name' => '系统', 'type' => 'page', 'target' => 'system', 'icon' => 'hm_settings'],
                ['name' => '捐助', 'type' => 'page', 'target' => 'donate', 'icon' => 'hm_fav'],
            ],
            // ---- 应用市场 ----
            'appMarket' => [
                'url' => tk_rel_url('r=market&category={category}&sort={sort}&page={page}&pageSize={pageSize}&keyword={keyword}', $enc),
                'detailUrl' => tk_rel_url('r=detail&pkg={packageName}', $enc),
                'headers' => [],
                'pageSize' => 24,
                'apps' => [],
            ],
            // ---- 升级检查 ----
            'upgrade' => [
                'checkUrl' => tk_rel_url('r=upgrade', $enc),
                'versionCode' => 0,
                'versionName' => '',
                'url' => '',
                'force' => false,
                'notes' => '演示升级接口，按需填写 url/versionCode',
            ],
            // ---- 捐助界面 ----
            'donate' => [
                'title' => '捐助',
                'desc' => '演示配置，可替换为真实收款二维码。',
                'image' => '',
                'tip' => '',
            ],
            'device' => $dev,
        ];
        tk_output(json_encode($config, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        break;

    case 'api':
        $enc = isset($_GET['enc']) ? (string)$_GET['enc'] : 'none';
        $vod = [
            'sites' => [[
                'key' => 'demo',
                'name' => '演示站点',
                'type' => 0,
                'api' => 'https://your-cms.example.com/api.php/provide/vod/',
                'searchable' => 1,
                'quickSearch' => 1,
                'filter' => 1,
                'ext' => '',
            ]],
            'lives' => [[
                'name' => '演示直播',
                'type' => 0,
                'url' => tk_abs_url('r=live', $enc),
                'playerType' => 1,
            ]],
            'parses' => [],
            'flags' => [],
        ];
        tk_output(json_encode($vod, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        break;

    case 'live':
        // 直播源统一返回 JSON（客户端同时兼容 txt/m3u 文本源）：
        //   [ {"group":"分组","channels":[{"name":"频道","urls":["url1","url2$线路2"]}]} ]
        // 同一频道多个播放地址写入 urls 数组；「地址$线路名」可为地址命名。
        // 支持的节目类型（播放地址协议）：
        //   http(s) 直链  ：m3u8/flv/mp4 等常规流媒体（含自定义 UA/Referer 参数追加写法）
        //   rtmp://、rtsp://：传统推流/监控协议
        //   webview://    ：WebView 直接播放网页（页面自带播放器时）
        //   video://      ：WebView 嗅探网页中的视频流播放（无直链的网页直播）
        //   tkp2p://      ：内置 P2P 直播（参数与 http(s) 相同，引擎由主配置 tkp2p 段控制）
        $live = [
            ['group' => '央视', 'channels' => [
                ['name' => 'CCTV-1 综合', 'urls' => [
                    'https://example.com/cctv1.m3u8',
                    'https://example.com/cctv1-2.m3u8$线路2',
                ]],
                ['name' => 'CCTV-13 新闻', 'urls' => ['https://example.com/cctv13.m3u8']],
            ]],
            ['group' => '卫视', 'channels' => [
                ['name' => '湖南卫视', 'urls' => ['https://example.com/hunan.m3u8']],
                ['name' => '浙江卫视', 'urls' => ['https://example.com/zhejiang.m3u8']],
            ]],
            ['group' => '网页源', 'channels' => [
                ['name' => 'WebView 直播', 'urls' => ['webview://https://example.com/live/page.html']],
                ['name' => 'Video 嗅探', 'urls' => ['video://https://example.com/live/video.html']],
            ]],
            ['group' => 'P2P', 'channels' => [
                ['name' => 'TKP2P 频道', 'urls' => ['tkp2p://https://example.com/live/p2p.m3u8']],
            ]],
            ['group' => '其他协议', 'channels' => [
                ['name' => 'RTMP 示例', 'urls' => ['rtmp://example.com/live/stream']],
                ['name' => 'RTSP 示例', 'urls' => ['rtsp://example.com:554/stream']],
            ]],
        ];
        tk_output(json_encode($live, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 'application/json; charset=utf-8');
        break;

    case 'epg':
        // EPG 统一返回 JSON（单日/多日两种写法客户端均可解析）：
        //   {"epg_data":[{"date":"2026-10-22","start":"08:00","end":"12:00","title":"..."}]}
        // 请求参数：ch/name=频道名；date=yyyy-MM-dd；days=天数窗口（含基准日，默认 1，范围 1~9）
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
                ['08:00', '12:00', '上午节目'],
                ['12:00', '18:00', '下午节目'],
                ['18:00', '23:59', '晚间节目'],
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
