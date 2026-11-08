<?php
/**
 * TKLauncher 接口加密 / 解密工具（PHP 7.4+，需要 openssl 扩展）
 *
 * 与客户端 app/src/main/java/com/github/tvbox/osc/n2n/ConfigCrypto.java 完全对应。
 * 客户端只负责“解密”，服务端负责“加密 + 明文直出”。
 *
 * 两种模式：
 *  A. ke 前缀模式（推荐，密钥随密文携带，换密钥无需改客户端）
 *       密文 = ke(19位小写hex) + bin2hex(AES-128-ECB(明文, ke前16位))
 *  B. 固定密钥模式（客户端 json "secretKey" 或 ConfigCrypto.SECRET_KEY 必须一致）
 *       密文 = bin2hex(AES-128-ECB(明文, secret前16位))
 *
 * 直接访问本文件可以看到两种模式的加密演示。
 */

/** ke 前缀模式：生成 19 位小写 hex 密钥（与客户端约定一致） */
function tk_ke_key(): string
{
    $key = md5(time() . 'AD80F93B542B' . '8b14ecf6ae028f9b6d25fa547eabad85');
    return substr($key, 13);
}

/** ke 前缀模式加密 */
function tk_encrypt_ke(string $data): string
{
    $ke = tk_ke_key();
    $cipher = openssl_encrypt($data, 'AES-128-ECB', substr($ke, 0, 16), OPENSSL_RAW_DATA);
    return $ke . bin2hex($cipher);
}

/** ke 前缀模式解密（客户端一般不需要，仅调试用） */
function tk_decrypt_ke(string $payload): string
{
    if (strlen($payload) < 51 || strlen($payload) % 2 !== 1) {
        return '';
    }
    $ke = substr($payload, 0, 19);
    $hex = substr($payload, 19);
    if (!ctype_xdigit($hex)) {
        return '';
    }
    $raw = hex2bin($hex);
    return (string)openssl_decrypt($raw, 'AES-128-ECB', substr($ke, 0, 16), OPENSSL_RAW_DATA);
}

/** 固定密钥模式加密（$secret 取前 16 字节作为 AES-128 密钥） */
function tk_encrypt_fixed(string $data, string $secret): string
{
    $cipher = openssl_encrypt($data, 'AES-128-ECB', substr($secret, 0, 16), OPENSSL_RAW_DATA);
    return bin2hex($cipher);
}

/** 固定密钥模式解密（调试用） */
function tk_decrypt_fixed(string $payload, string $secret): string
{
    if (strlen($payload) % 2 !== 0 || !ctype_xdigit($payload)) {
        return '';
    }
    return (string)openssl_decrypt(hex2bin($payload), 'AES-128-ECB', substr($secret, 0, 16), OPENSSL_RAW_DATA);
}

/**
 * 按请求参数输出：enc=none|ke|fixed，默认 none。
 * 注意：/json、/txt 等明文响应也必须设置正确 Content-Type。
 */
function tk_output(string $plain, string $contentType = 'application/json; charset=utf-8'): void
{
    $enc = isset($_GET['enc']) ? strtolower((string)$_GET['enc']) : 'none';
    if ($enc === 'ke') {
        $body = tk_encrypt_ke($plain);
        $contentType = 'text/plain; charset=utf-8';
    } elseif ($enc === 'fixed') {
        $secret = getenv('TK_SECRET_KEY') ?: 'tkTVBoxDemoKey16'; // 改成与客户端一致的 16 位秘钥
        $body = tk_encrypt_fixed($plain, $secret);
        $contentType = 'text/plain; charset=utf-8';
    } else {
        $body = $plain;
    }
    header('Content-Type: ' . $contentType);
    header('Cache-Control: no-store');
    echo $body;
}

/** 采集客户端本机参数（URL 参数优先，其次请求头），可用于按 MAC 下发不同配置 */
function tk_device(): array
{
    // 请求参数可能是数组（如 ?mac[]=x），统一归一化为去空格字符串，避免 json_encode 输出非字符串
    $s = function ($v) {
        return is_scalar($v) ? trim((string)$v) : '';
    };
    $h = function ($name) use ($s) {
        return $s(isset($_SERVER[$name]) ? $_SERVER[$name] : '');
    };
    $param = function ($name, $header = '') use ($s, $h) {
        $v = isset($_GET[$name]) ? $s($_GET[$name]) : '';
        return $v !== '' ? $v : $h($header);
    };
    return [
        'mac' => $param('mac', 'HTTP_X_DEVICE_MAC'),
        'ip' => $param('ip', 'HTTP_X_DEVICE_IP'),
        'model' => $param('model'),
        'brand' => $param('brand'),
        'device' => $param('device'),
        'sdk' => $param('sdk'),
        'release' => $param('release'),
        'os' => $h('HTTP_X_OS_VERSION'),
        'app' => $param('app', 'HTTP_X_APP_VERSION'),
    ];
}

// 直接访问本文件时给出演示
if (PHP_SAPI !== 'cli' && basename($_SERVER['SCRIPT_FILENAME'] ?? '') === basename(__FILE__)) {
    $demo = json_encode(['ok' => true, 'msg' => 'TKLauncher crypto demo', 'device' => tk_device()], JSON_UNESCAPED_UNICODE);
    header('Content-Type: text/plain; charset=utf-8');
    echo "明文:\n" . $demo . "\n\n";
    echo "ke 前缀密文:\n" . tk_encrypt_ke($demo) . "\n\n";
    echo "固定密钥密文:\n" . tk_encrypt_fixed($demo, 'tkTVBoxDemoKey16') . "\n";
}
