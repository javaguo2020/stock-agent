package com.ruoyi.system.agent.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 大模型返回内容的 JSON 提取与安全取值工具（容错解析，保证流程不因格式问题中断）。
 */
public final class PromptUtils {

    private PromptUtils() {
    }

    /**
     * 从模型返回文本中提取 JSON 对象（取第一个 '{' 到最后一个 '}' 之间的内容）。
     *
     * @return 解析失败返回 null
     */
    public static JSONObject extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return JSONObject.parseObject(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    public static String getStr(JSONObject obj, String key, String def) {
        if (obj == null) {
            return def;
        }
        String v = obj.getString(key);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    public static double getDouble(JSONObject obj, String key, double def) {
        if (obj == null) {
            return def;
        }
        try {
            Double v = obj.getDouble(key);
            return v == null ? def : v;
        } catch (Exception e) {
            return def;
        }
    }

    public static int getInt(JSONObject obj, String key, int def) {
        if (obj == null) {
            return def;
        }
        Integer v = obj.getInteger(key);
        return v == null ? def : v;
    }

    /**
     * 提取字符串数组字段，元素统一为非空字符串。
     */
    public static java.util.List<String> getStrList(JSONObject obj, String key) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (obj == null) {
            return out;
        }
        try {
            JSONArray arr = obj.getJSONArray(key);
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.size(); i++) {
                String s = arr.getString(i);
                if (s != null && !s.trim().isEmpty()) {
                    out.add(s.trim());
                }
            }
        } catch (Exception ignore) {
            // 解析失败返回空列表
        }
        return out;
    }

    /**
     * 限制多行文本长度（超长截断），防止异常输出撑爆后续 prompt。
     */
    public static String limit(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "…(截断)";
    }
}
