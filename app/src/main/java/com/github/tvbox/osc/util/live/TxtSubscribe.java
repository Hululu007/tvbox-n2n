package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {

    private static final Pattern NAME_PATTERN = Pattern.compile(".*,(.+?)$");
    private static final Pattern GROUP_PATTERN = Pattern.compile("group-title=\"(.*?)\"");

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        if (str == null) {
            return;
        }
        String trimmed = str.trim();
        // JSON 直播源（与 EPG 的 JSON 格式对齐）：数组或 {"groups":[...]} 均支持，
        // 解析成功直接返回；结构不符则回退到 txt/m3u 文本解析
        if ((trimmed.startsWith("[") || trimmed.startsWith("{")) && parseJson(linkedHashMap, trimmed)) {
            return;
        }
        if (str.startsWith("#EXTM3U")) {
            parseM3u(linkedHashMap, str);
        } else {
            parseTxt(linkedHashMap, str);
        }
    }

    /**
     * JSON 直播格式（与 {@link #live2JsonArray} 输出结构互逆）：
     * <pre>
     * [ {"group":"分组名","channels":[{"name":"频道名","urls":["url1","url2$线路2"]}]} ]
     * 或 {"groups":[ ...同上... ]}
     * </pre>
     * url 支持 http(s)/rtsp/rtmp 直链及 webview://、video://、tkp2p:// 三种特殊协议，
     * 「url$线路名」可为多线路地址命名（与 txt 源「#」分隔多链接等价）。
     *
     * @return true=识别并解析出有效分组；false=不是预期 JSON 结构，调用方继续走文本解析
     */
    private static boolean parseJson(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        try {
            JsonElement root = JsonParser.parseString(str);
            JsonArray groups = null;
            if (root.isJsonArray()) {
                groups = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("groups")) {
                JsonElement ge = root.getAsJsonObject().get("groups");
                if (ge != null && ge.isJsonArray()) {
                    groups = ge.getAsJsonArray();
                }
            }
            if (groups == null) {
                return false;
            }
            for (JsonElement groupEle : groups) {
                if (!groupEle.isJsonObject()) continue;
                JsonObject g = groupEle.getAsJsonObject();
                String groupName = optString(g, "group");
                if (groupName.isEmpty()) groupName = optString(g, "name");
                if (groupName.isEmpty()) groupName = "未分组";
                LinkedHashMap<String, ArrayList<String>> channel = linkedHashMap.get(groupName);
                if (channel == null) {
                    channel = new LinkedHashMap<>();
                }
                JsonElement channelsEle = g.get("channels");
                if (channelsEle != null && channelsEle.isJsonArray()) {
                    for (JsonElement chEle : channelsEle.getAsJsonArray()) {
                        if (!chEle.isJsonObject()) continue;
                        JsonObject c = chEle.getAsJsonObject();
                        String name = optString(c, "name");
                        if (name.isEmpty()) name = "未命名";
                        ArrayList<String> urls = channel.get(name);
                        if (urls == null) {
                            urls = new ArrayList<>();
                        }
                        JsonElement urlsEle = c.get("urls");
                        if (urlsEle != null && urlsEle.isJsonArray()) {
                            for (JsonElement uEle : urlsEle.getAsJsonArray()) {
                                if (!uEle.isJsonPrimitive()) continue;
                                String url = uEle.getAsString().trim();
                                if (!url.isEmpty() && !urls.contains(url)) urls.add(url);
                            }
                        } else {
                            // 兼容单链接写法 {"url":"..."}
                            String url = optString(c, "url");
                            if (!url.isEmpty() && !urls.contains(url)) urls.add(url);
                        }
                        if (!urls.isEmpty()) {
                            channel.put(name, urls);
                        }
                    }
                }
                if (!channel.isEmpty()) {
                    linkedHashMap.put(groupName, channel);
                }
            }
            return !linkedHashMap.isEmpty();
        } catch (Throwable th) {
            return false;
        }
    }

    private static String optString(JsonObject obj, String key) {
        JsonElement ele = obj == null ? null : obj.get(key);
        return ele != null && ele.isJsonPrimitive() ? ele.getAsString().trim() : "";
    }

    private static void parseM3u(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        ArrayList<String> urls;
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            LinkedHashMap<String, ArrayList<String>> channel = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> channelTemp = channel;
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.equals("")) continue;
                if (line.startsWith("#EXTM3U")) continue;
                if (line.startsWith("#EXTINF")) {
                    String name = getStrByRegex(NAME_PATTERN, line);
                    String group = getStrByRegex(GROUP_PATTERN, line);
                    // 此时再读取一行，就是对应的 url 链接了
                    String url = bufferedReader.readLine().trim();
                    if (linkedHashMap.containsKey(group)) {
                        channelTemp = linkedHashMap.get(group);
                    } else {
                        channelTemp = new LinkedHashMap<>();
                        linkedHashMap.put(group, channelTemp);
                    }
                    if (null != channelTemp && channelTemp.containsKey(name)) {
                        urls = channelTemp.get(name);
                    } else {
                        urls = new ArrayList<>();
                        channelTemp.put(name, urls);
                    }
                    if (null != urls && !urls.contains(url)) urls.add(url);
                }
            }
            bufferedReader.close();
            if (channel.isEmpty()) return;
            linkedHashMap.put("未分组", channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getStrByRegex(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) return matcher.group(1);
        return pattern.pattern().equals(GROUP_PATTERN.pattern()) ? "未分组" : "未命名";
    }

    private static void parseTxt(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap, String str) {
        ArrayList<String> arrayList;
        try {
            BufferedReader bufferedReader = new BufferedReader(new StringReader(str));
            String readLine = bufferedReader.readLine();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = new LinkedHashMap<>();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap3 = linkedHashMap2;
            while (readLine != null) {
                if (readLine.trim().isEmpty()) {
                    readLine = bufferedReader.readLine();
                } else {
                    String[] split = readLine.split(",", 2);
                    if (split.length < 2) {
                        readLine = bufferedReader.readLine();
                    } else {
                        if (readLine.contains("#genre#")) {
                            String trim = split[0].trim();
                            if (!linkedHashMap.containsKey(trim)) {
                                linkedHashMap3 = new LinkedHashMap<>();
                                linkedHashMap.put(trim, linkedHashMap3);
                            } else {
                                linkedHashMap3 = linkedHashMap.get(trim);
                            }
                        } else {
                            String trim2 = split[0].trim();
                            for (String str2 : split[1].trim().split("#")) {
                                String trim3 = str2.trim();
                                if (!trim3.isEmpty() && (trim3.startsWith("http") || trim3.startsWith("rtsp") || trim3.startsWith("rtmp")
                                        || trim3.startsWith("webview://") || trim3.startsWith("video://") || trim3.startsWith("tkp2p://"))) {
                                    if (!linkedHashMap3.containsKey(trim2)) {
                                        arrayList = new ArrayList<>();
                                        linkedHashMap3.put(trim2, arrayList);
                                    } else {
                                        arrayList = linkedHashMap3.get(trim2);
                                    }
                                    if (!arrayList.contains(trim3)) {
                                        arrayList.add(trim3);
                                    }
                                }
                            }
                        }
                        readLine = bufferedReader.readLine();
                    }
                }
            }
            bufferedReader.close();
            if (linkedHashMap2.isEmpty()) {
                return;
            }
            linkedHashMap.put("未分组", linkedHashMap2);
        } catch (Throwable unused) {
        }
    }

    /**
     * 解析 JSON 直播源并保留频道的 decode/playerType 等扩展字段（供 ApiConfig.loadLives 使用）。
     * 返回 null 表示不是可识别的 JSON 直播结构，调用方继续走 txt/m3u 文本解析。
     */
    public static JsonArray jsonLivesArray(String str) {
        if (str == null) return null;
        String trimmed = str.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) return null;
        try {
            JsonElement root = JsonParser.parseString(trimmed);
            JsonArray groups = null;
            if (root.isJsonArray()) {
                groups = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("groups")) {
                JsonElement ge = root.getAsJsonObject().get("groups");
                if (ge != null && ge.isJsonArray()) groups = ge.getAsJsonArray();
            }
            if (groups == null) return null;
            // 至少有一个分组对象带 channels 数组才认为是直播 JSON；空数组也视为有效空源
            if (groups.size() == 0) return groups;
            for (JsonElement groupEle : groups) {
                if (!groupEle.isJsonObject()) continue;
                JsonElement channels = groupEle.getAsJsonObject().get("channels");
                if (channels != null && channels.isJsonArray()) return groups;
            }
            return null;
        } catch (Throwable th) {
            return null;
        }
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap) {
        JsonArray jsonarr = new JsonArray();
        for (String str : linkedHashMap.keySet()) {
            JsonArray jsonarr2 = new JsonArray();
            LinkedHashMap<String, ArrayList<String>> linkedHashMap2 = linkedHashMap.get(str);
            if (!linkedHashMap2.isEmpty()) {
                for (String str2 : linkedHashMap2.keySet()) {
                    ArrayList<String> arrayList = linkedHashMap2.get(str2);
                    if (!arrayList.isEmpty()) {
                        JsonArray jsonarr3 = new JsonArray();
                        for (int i = 0; i < arrayList.size(); i++) {
                            jsonarr3.add(arrayList.get(i));
                        }
                        JsonObject jsonobj = new JsonObject();
                        try {
                            jsonobj.addProperty("name", str2);
                            jsonobj.add("urls", jsonarr3);
                        } catch (Throwable e) {
                        }
                        jsonarr2.add(jsonobj);
                    }
                }
                JsonObject jsonobj2 = new JsonObject();
                try {
                    jsonobj2.addProperty("group", str);
                    jsonobj2.add("channels", jsonarr2);
                } catch (Throwable e) {
                }
                jsonarr.add(jsonobj2);
            }
        }
        return jsonarr;
    }
}
