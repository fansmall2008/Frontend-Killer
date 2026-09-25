package com.gamelist.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gamelist.model.Game;
import com.gamelist.model.Platform;

/**
 * 模板表达式引擎。
 * 
 * 支持的表达式语法：
 * - 字段引用：name, desc, releasedate, box-2D, platform.system 等
 * - 回退运算符：name or filename（取第一个非空值）
 * - 字符串拼接：name + ".jpg"
 * - 函数调用：sub(name, 0, 7), upper(name), replace(path, "\", "/") 等
 * - 嵌套调用：trim(replace(name, " ", "_"))
 * - 字符串字面量："hello" 或 'hello'
 * 
 * 运算符优先级（从低到高）：
 * 1. or — 回退（最低优先级，类似 SQL COALESCE）
 * 2. +  — 字符串拼接
 * 
 * 示例：
 * - name or filename — name 为空时使用 filename
 * - box-2D or screenshot or image — 多级媒体回退
 * - (name or filename) + ".jpg" — 先回退再拼接
 * - name or filename + ".jpg" — 等价于 name or (filename + ".jpg")
 * 
 * 内置函数：
 * - sub(str, start[, end]) — 截取子串
 * - upper(str) / lower(str) — 大小写转换
 * - trim(str) — 去首尾空白
 * - replace(str, from, to) — 字符串替换
 * - len(str) — 字符串长度
 * - filename(path) — 从路径提取文件名
 * - stem(path) — 去扩展名
 * - ext(path) — 取扩展名
 * - dir(path) — 取目录部分
 * - default(field, fallback) — 默认值
 * - if(field, trueVal, falseVal) — 条件
 * - coalesce(f1, f2, ...) — 取第一个非空值
 * - dateformat(dateStr, pattern) — 日期格式化
 * - before(str, sep) — 取分隔符之前部分（无分隔符时返回原串）
 * - after(str, sep) — 取分隔符之后部分（无分隔符时返回原串）
 * - concat(f1, f2, ...) — 字符串拼接（跳过空值）
 * - sanitize(str, charset) — 将 charset 中出现的每个字符替换为 _
 * - matches(str, regex) — 正则全匹配，返回 true/false
 * - map(value[, category]) — 方言映射：命中 term_mapping 返回目标词，未命中原样返回
 *
 * map() 方言映射（TODO #10）：
 * - map(value) — 在全部非 system_alias 分类中查找（system_alias 为系统匹配内部用途）；
 *   同一 value 命中多个分类时按分类名字典序取第一个（保证确定性）
 * - map(value, category) — 仅在指定分类中查找
 * - 查找前对 value 做归一化（小写、去空白标点，与 SystemMatcher.normalize 一致），
 *   因此 "Beat'em Up" / "beat em up" / "beatemup" 等价
 * - 未命中时原样返回输入值
 */
public class TemplateExpressionEngine {

    private static final Logger logger = LoggerFactory.getLogger(TemplateExpressionEngine.class);

    /**
     * 方言映射缓存（TODO #10）：category →（归一化 source → target）。
     * 由 TermMappingService 启动时加载、变更时刷新；map() 函数从该缓存查找。
     */
    private static volatile Map<String, Map<String, String>> dialectMaps = new HashMap<>();

    /** 更新方言映射缓存（由 TermMappingService 调用） */
    public static void setDialectMaps(Map<String, Map<String, String>> maps) {
        dialectMaps = maps != null ? maps : new HashMap<>();
    }

    /** 表达式上下文：游戏 + 平台 + 额外变量 */
    public static class Context {
        private final Game game;
        private final Platform platform;
        private final Map<String, String> variables;

        public Context(Game game, Platform platform, Map<String, String> variables) {
            this.game = game;
            this.platform = platform;
            this.variables = variables;
        }

        public Game getGame() { return game; }
        public Platform getPlatform() { return platform; }
        public Map<String, String> getVariables() { return variables; }
    }

    /**
     * 判断字符串是否为表达式（包含函数调用、运算符或引号）。
     * 简单的纯字段名（如 "name"、"desc"）不算表达式。
     */
    public static boolean isExpression(String expr) {
        if (expr == null || expr.isEmpty()) return false;
        String trimmed = expr.trim();
        // 包含函数调用括号
        if (trimmed.contains("(") && trimmed.contains(")")) return true;
        // 包含拼接运算符
        if (trimmed.contains("+")) return true;
        // 包含 or 回退运算符（前后必须有空格或位于首尾，避免匹配 color/order 等字段名）
        if (containsOrOperator(trimmed)) return true;
        // 以引号开头
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) return true;
        return false;
    }

    /**
     * 检查表达式中是否包含顶层 or 运算符。
     * 跳过引号内和括号内的 or，避免误匹配。
     */
    private static boolean containsOrOperator(String expr) {
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (inQuote) {
                if (c == quoteChar) inQuote = false;
                continue;
            }
            if (c == '"' || c == '\'') { inQuote = true; quoteChar = c; continue; }
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }
            if (depth == 0 && isOrKeyword(expr, i)) return true;
        }
        return false;
    }

    /**
     * 检查位置 i 处是否为独立的 or 关键字（前后必须是空格或字符串边界）。
     */
    private static boolean isOrKeyword(String expr, int i) {
        if (i + 2 >= expr.length()) return false;
        // 检查 "or" 两个字符
        char c1 = expr.charAt(i);
        char c2 = expr.charAt(i + 1);
        if (!((c1 == 'o' || c1 == 'O') && (c2 == 'r' || c2 == 'R'))) return false;
        // 前面必须是空格或字符串开头
        if (i > 0 && expr.charAt(i - 1) != ' ') return false;
        // 后面必须是空格或字符串结尾
        int afterOr = i + 2;
        if (afterOr < expr.length() && expr.charAt(afterOr) != ' ') return false;
        return true;
    }

    /**
     * 计算表达式值。
     * 如果 expr 不是表达式（纯字段名），直接通过 GameFieldAccessor 获取值。
     */
    public static String evaluate(String expr, Context ctx) {
        if (expr == null || expr.isEmpty()) return null;
        try {
            return evalExpr(expr.trim(), ctx);
        } catch (Exception e) {
            logger.warn("表达式计算失败: {}, 错误: {}", expr, e.getMessage());
            return null;
        }
    }

    /**
     * 便捷方法：不需要 Platform 时。
     */
    public static String evaluate(String expr, Game game, Map<String, String> variables) {
        return evaluate(expr, new Context(game, null, variables));
    }

    // ==================== 核心解析 ====================

    private static String evalExpr(String expr, Context ctx) {
        expr = expr.trim();
        if (expr.isEmpty()) return null;

        // 0. 处理 or 回退运算符（最低优先级，先于 + 拆分）
        List<String> orParts = splitByOr(expr);
        if (orParts.size() > 1) {
            return evalOrExpr(orParts, ctx);
        }

        // 1. 处理字符串拼接（+ 运算符），注意跳过函数调用内部和引号内的 +
        List<String> parts = splitByConcat(expr);
        if (parts.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                String val = evalExpr(part, ctx);
                if (val != null) sb.append(val);
            }
            return sb.toString();
        }

        // 2. 字符串字面量
        if ((expr.startsWith("\"") && expr.endsWith("\"")) ||
            (expr.startsWith("'") && expr.endsWith("'"))) {
            return expr.substring(1, expr.length() - 1);
        }

        // 3. 函数调用
        int parenOpen = expr.indexOf('(');
        if (parenOpen > 0 && expr.endsWith(")")) {
            String funcName = expr.substring(0, parenOpen).trim().toLowerCase();
            String argsStr = expr.substring(parenOpen + 1, expr.length() - 1);
            List<String> args = splitArgs(argsStr);
            return callFunction(funcName, args, ctx);
        }

        // 4. 平台变量引用：platform.xxx
        if (expr.startsWith("platform.")) {
            String fieldName = expr.substring("platform.".length());
            return getPlatformFieldValue(ctx.getPlatform(), fieldName);
        }

        // 5. 变量引用
        if (ctx.getVariables() != null && ctx.getVariables().containsKey(expr)) {
            return ctx.getVariables().get(expr);
        }

        // 6. 游戏字段引用（通过 GameFieldAccessor）
        if (ctx.getGame() != null) {
            String value = GameFieldAccessor.getValue(ctx.getGame(), expr);
            if (value != null) return value;
        }

        // 7. 变量兜底
        if (ctx.getVariables() != null) {
            String value = ctx.getVariables().get(expr);
            if (value != null) return value;
        }

        return null;
    }

    // ==================== or 回退运算符 ====================

    /**
     * 按顶层 or 关键字拆分表达式。
     * 跳过引号内和括号内的 or。
     */
    private static List<String> splitByOr(String expr) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        int start = 0;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (inQuote) {
                if (c == quoteChar) inQuote = false;
                continue;
            }
            if (c == '"' || c == '\'') { inQuote = true; quoteChar = c; continue; }
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }

            if (depth == 0 && isOrKeyword(expr, i)) {
                String part = expr.substring(start, i).trim();
                if (!part.isEmpty()) parts.add(part);
                start = i + 2; // 跳过 "or"
            }
        }
        String last = expr.substring(start).trim();
        if (!last.isEmpty()) parts.add(last);
        return parts;
    }

    /**
     * 计算 or 表达式链，返回第一个非空值。
     * 类似 SQL COALESCE 语义。
     */
    private static String evalOrExpr(List<String> alternatives, Context ctx) {
        for (String alt : alternatives) {
            String val = evalExpr(alt, ctx);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    // ==================== 拼接运算符 ====================

    /**
     * 按 + 运算符拆分表达式，但跳过引号内和括号内的 +。
     */
    private static List<String> splitByConcat(String expr) {
        List<String> parts = new ArrayList<>();
        int depth = 0;       // 括号深度
        boolean inQuote = false;
        char quoteChar = 0;
        int start = 0;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (inQuote) {
                if (c == quoteChar) inQuote = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
                continue;
            }
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }

            if (c == '+' && depth == 0) {
                String part = expr.substring(start, i).trim();
                if (!part.isEmpty()) parts.add(part);
                start = i + 1;
            }
        }
        String last = expr.substring(start).trim();
        if (!last.isEmpty()) parts.add(last);

        return parts;
    }

    /**
     * 按逗号拆分函数参数，但跳过引号内和括号内的逗号。
     */
    private static List<String> splitArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        int start = 0;

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);

            if (inQuote) {
                if (c == quoteChar) inQuote = false;
                continue;
            }
            if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
                continue;
            }
            if (c == '(') { depth++; continue; }
            if (c == ')') { depth--; continue; }

            if (c == ',' && depth == 0) {
                args.add(argsStr.substring(start, i).trim());
                start = i + 1;
            }
        }
        String last = argsStr.substring(start).trim();
        if (!last.isEmpty()) args.add(last);

        return args;
    }

    // ==================== 内置函数 ====================

    private static String callFunction(String funcName, List<String> args, Context ctx) {
        // 先计算所有参数值（惰性求值的函数除外）
        switch (funcName) {
            // --- 惰性求值 ---
            case "default":
                return funcDefault(args, ctx);
            case "if":
                return funcIf(args, ctx);
            case "coalesce":
                return funcCoalesce(args, ctx);

            // --- 字符串操作 ---
            case "sub":
                return funcSub(args, ctx);
            case "upper":
                return funcUpper(args, ctx);
            case "lower":
                return funcLower(args, ctx);
            case "trim":
                return funcTrim(args, ctx);
            case "replace":
                return funcReplace(args, ctx);
            case "before":
                return funcBefore(args, ctx);
            case "after":
                return funcAfter(args, ctx);
            case "concat":
                return funcConcat(args, ctx);
            case "sanitize":
                return funcSanitize(args, ctx);
            case "matches":
                return funcMatches(args, ctx);
            case "len":
                return funcLen(args, ctx);

            // --- 方言映射 ---
            case "map":
                return funcMap(args, ctx);
            case "mapfirst":
                return funcMapFirst(args, ctx);

            // --- 路径操作 ---
            case "filename":
                return funcFilename(args, ctx);
            case "stem":
                return funcStem(args, ctx);
            case "ext":
                return funcExt(args, ctx);
            case "dir":
                return funcDir(args, ctx);

            // --- 日期操作 ---
            case "dateformat":
                return funcDateformat(args, ctx);

            default:
                logger.warn("未知函数: {}", funcName);
                return null;
        }
    }

    // --- 字符串操作函数 ---

    /** sub(str, start[, end]) — 截取子串 */
    private static String funcSub(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String str = evalExpr(args.get(0), ctx);
        if (str == null) return null;
        int start = toInt(evalExpr(args.get(1), ctx), 0);
        if (args.size() >= 3) {
            int end = toInt(evalExpr(args.get(2), ctx), str.length());
            start = Math.max(0, Math.min(start, str.length()));
            end = Math.max(start, Math.min(end, str.length()));
            return str.substring(start, end);
        } else {
            start = Math.max(0, Math.min(start, str.length()));
            return str.substring(start);
        }
    }

    /** upper(str) — 转大写 */
    private static String funcUpper(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String str = evalExpr(args.get(0), ctx);
        return str != null ? str.toUpperCase() : null;
    }

    /** lower(str) — 转小写 */
    private static String funcLower(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String str = evalExpr(args.get(0), ctx);
        return str != null ? str.toLowerCase() : null;
    }

    /** trim(str) — 去首尾空白 */
    private static String funcTrim(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String str = evalExpr(args.get(0), ctx);
        return str != null ? str.trim() : null;
    }

    /** replace(str, from, to) — 字符串替换 */
    private static String funcReplace(List<String> args, Context ctx) {
        if (args.size() < 3) return null;
        String str = evalExpr(args.get(0), ctx);
        if (str == null) return null;
        String from = evalExpr(args.get(1), ctx);
        String to = evalExpr(args.get(2), ctx);
        if (from == null) return str;
        return str.replace(from, to != null ? to : "");
    }

    /** len(str) — 字符串长度 */
    private static String funcLen(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String str = evalExpr(args.get(0), ctx);
        return str != null ? String.valueOf(str.length()) : null;
    }

    /** before(str, sep) — 取分隔符之前部分；无分隔符时返回原串 */
    private static String funcBefore(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String str = evalExpr(args.get(0), ctx);
        if (str == null) return null;
        String sep = evalExpr(args.get(1), ctx);
        if (sep == null || sep.isEmpty()) return str;
        int idx = str.indexOf(sep);
        return idx >= 0 ? str.substring(0, idx) : str;
    }

    /** after(str, sep) — 取分隔符之后部分；无分隔符时返回原串 */
    private static String funcAfter(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String str = evalExpr(args.get(0), ctx);
        if (str == null) return null;
        String sep = evalExpr(args.get(1), ctx);
        if (sep == null || sep.isEmpty()) return str;
        int idx = str.indexOf(sep);
        return idx >= 0 ? str.substring(idx + sep.length()) : str;
    }

    /** concat(f1, f2, ...) — 拼接所有非空参数 */
    private static String funcConcat(List<String> args, Context ctx) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            String val = evalExpr(arg, ctx);
            if (val != null) sb.append(val);
        }
        return sb.toString();
    }

    /** sanitize(str, charset) — 将 charset 中出现的每个字符替换为 _ */
    private static String funcSanitize(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String str = evalExpr(args.get(0), ctx);
        if (str == null) return null;
        String charset = evalExpr(args.get(1), ctx);
        if (charset == null || charset.isEmpty()) return str;
        StringBuilder sb = new StringBuilder(str.length());
        for (char c : str.toCharArray()) {
            sb.append(charset.indexOf(c) >= 0 ? '_' : c);
        }
        return sb.toString();
    }

    /** matches(str, regex) — 正则全匹配，返回 "true"/"false" */
    private static String funcMatches(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String str = evalExpr(args.get(0), ctx);
        String regex = evalExpr(args.get(1), ctx);
        if (str == null || regex == null) return "false";
        try {
            return Pattern.matches(regex, str) ? "true" : "false";
        } catch (Exception e) {
            logger.warn("正则表达式无效: {}", regex);
            return "false";
        }
    }

    /**
     * map(value[, category]) — 方言映射（TODO #10）。
     * 命中 term_mapping 表返回 target；未命中原样返回 value。
     * 不指定 category 时排除 system_alias（系统匹配内部用途），在其余分类中查找。
     * 
     * 多值处理：输入含逗号时逐段映射，默认用 "-" 连接所有结果。
     * 若只需第一个映射结果，使用 mapFirst(value[, category])。
     */
    private static String funcMap(List<String> args, Context ctx) {
        return evalMap(args, ctx, false);
    }

    /**
     * mapFirst(value[, category]) — 方言映射，只返回第一个非空映射结果。
     * 适用于目录名等需要单一值的场景。
     */
    private static String funcMapFirst(List<String> args, Context ctx) {
        return evalMap(args, ctx, true);
    }

    /**
     * 方言映射核心逻辑。
     * @param firstOnly true=只返回第一个映射结果；false=映射所有值并用 "-" 连接
     */
    private static String evalMap(List<String> args, Context ctx, boolean firstOnly) {
        if (args.isEmpty()) return null;
        String value = evalExpr(args.get(0), ctx);
        if (value == null) return value;
        String category = args.size() >= 2 ? evalExpr(args.get(1), ctx) : null;

        System.out.println("[DEBUG evalMap] firstOnly=" + firstOnly + ", value='" + value + "', category=" + category);

        // 多值处理：输入含逗号时
        if (value.indexOf(',') >= 0) {
            if (firstOnly) {
                // mapFirst: 先尝试整体映射（如 "Racing, Driving" 是完整分类名），
                // 失败后再按逗号拆分取第一个有效映射
                String whole = mapSingle(value, category);
                System.out.println("[DEBUG evalMap] mapFirst whole='" + whole + "', equalsValue=" + whole.equals(value));
                if (whole != null && !whole.isEmpty() && !whole.equals(value)) {
                    return whole; // 整体映射成功
                }
                // 整体未命中，按逗号拆分取第一个
                for (String piece : value.split(",")) {
                    String mapped = mapSingle(piece.trim(), category);
                    System.out.println("[DEBUG evalMap] mapFirst piece='" + piece.trim() + "' -> mapped='" + mapped + "'");
                    if (mapped != null && !mapped.isEmpty()) {
                        return mapped;
                    }
                }
                return value; // 全部未命中，返回原值
            } else {
                // map: 逐段映射，用 "-" 连接
                java.util.LinkedHashSet<String> mappedParts = new java.util.LinkedHashSet<>();
                for (String piece : value.split(",")) {
                    String mapped = mapSingle(piece.trim(), category);
                    if (mapped != null && !mapped.isEmpty()) {
                        mappedParts.add(mapped);
                    }
                }
                String result = mappedParts.isEmpty() ? value : String.join("-", mappedParts);
                System.out.println("[DEBUG evalMap] map result='" + result + "'");
                return result;
            }
        }
        String singleResult = mapSingle(value, category);
        System.out.println("[DEBUG evalMap] single value='" + value + "' -> '" + singleResult + "'");
        return singleResult;
    }

    /** 单值方言映射：归一化后查表，未命中返回原值 */
    private static String mapSingle(String value, String category) {
        Map<String, Map<String, String>> maps = dialectMaps;
        String key = SystemMatcher.normalize(value);
        if (key.isEmpty()) return value;

        if (category != null && !category.trim().isEmpty()) {
            Map<String, String> m = maps.get(category.trim());
            String target = m != null ? m.get(key) : null;
            return target != null ? target : value;
        }

        // 无 category：全部非 system_alias 分类中查找，冲突时按分类名字典序取第一个
        String bestCategory = null;
        String bestTarget = null;
        for (Map.Entry<String, Map<String, String>> entry : maps.entrySet()) {
            if ("system_alias".equals(entry.getKey())) continue;
            String target = entry.getValue().get(key);
            if (target != null && (bestCategory == null || entry.getKey().compareTo(bestCategory) < 0)) {
                bestCategory = entry.getKey();
                bestTarget = target;
            }
        }
        return bestTarget != null ? bestTarget : value;
    }

    // --- 路径操作函数 ---

    /** filename(path) — 从路径提取文件名（含扩展名） */
    private static String funcFilename(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String path = evalExpr(args.get(0), ctx);
        if (path == null) return null;
        return new File(path).getName();
    }

    /** stem(path) — 去扩展名 */
    private static String funcStem(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String path = evalExpr(args.get(0), ctx);
        if (path == null) return null;
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** ext(path) — 取扩展名（含点号） */
    private static String funcExt(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String path = evalExpr(args.get(0), ctx);
        if (path == null) return null;
        String name = new File(path).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    /** dir(path) — 取目录部分 */
    private static String funcDir(List<String> args, Context ctx) {
        if (args.isEmpty()) return null;
        String path = evalExpr(args.get(0), ctx);
        if (path == null) return null;
        return new File(path).getParent();
    }

    // --- 条件/默认值函数 ---

    /** default(field, fallback) — 字段为空时使用默认值 */
    private static String funcDefault(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String value = evalExpr(args.get(0), ctx);
        if (value == null || value.isEmpty()) {
            return evalExpr(args.get(1), ctx);
        }
        return value;
    }

    /** if(condition, trueVal, falseVal) — 条件判断（"false" 视为假，与 matches 的返回值约定一致） */
    private static String funcIf(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String condition = evalExpr(args.get(0), ctx);
        boolean truthy = condition != null && !condition.isEmpty()
                && !"false".equalsIgnoreCase(condition);
        if (truthy) {
            return evalExpr(args.get(1), ctx);
        } else if (args.size() >= 3) {
            return evalExpr(args.get(2), ctx);
        }
        return null;
    }

    /** coalesce(f1, f2, ...) — 取第一个非空值 */
    private static String funcCoalesce(List<String> args, Context ctx) {
        for (String arg : args) {
            String value = evalExpr(arg, ctx);
            if (value != null && !value.isEmpty()) return value;
        }
        return null;
    }

    // --- 日期操作函数 ---

    /** dateformat(dateStr, pattern) — 日期格式化 */
    private static String funcDateformat(List<String> args, Context ctx) {
        if (args.size() < 2) return null;
        String dateStr = evalExpr(args.get(0), ctx);
        if (dateStr == null) return null;
        String pattern = evalExpr(args.get(1), ctx);
        if (pattern == null) return dateStr;

        try {
            // 尝试多种输入格式
            String[] inputFormats = {
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "yyyyMMdd",
                "yyyy",
                "MM/dd/yyyy",
                "dd/MM/yyyy"
            };
            Date date = null;
            for (String fmt : inputFormats) {
                try {
                    date = new SimpleDateFormat(fmt).parse(dateStr);
                    break;
                } catch (Exception ignored) {}
            }
            if (date != null) {
                return new SimpleDateFormat(pattern).format(date);
            }
        } catch (Exception e) {
            logger.warn("日期格式化失败: date={}, pattern={}", dateStr, pattern);
        }
        return dateStr;
    }

    // ==================== 工具方法 ====================

    private static int toInt(String str, int defaultValue) {
        if (str == null || str.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String getPlatformFieldValue(Platform platform, String fieldName) {
        if (platform == null || fieldName == null) return null;
        switch (fieldName) {
            case "system": return platform.getSystem();
            case "name": return platform.getName();
            case "launch": return platform.getLaunch();
            case "software": return platform.getSoftware();
            case "database": return platform.getDatabase();
            case "web": return platform.getWeb();
            case "folderPath": return platform.getFolderPath();
            default: return null;
        }
    }
}
