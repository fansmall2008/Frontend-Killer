package com.gamelist.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gamelist.service.TranslationService;
import com.gamelist.service.TranslationToolService;
import com.gamelist.service.impl.ConfigManager;
import com.gamelist.service.impl.TranslationServiceManager;
import com.gamelist.util.TranslationLanguages;

/**
 * 工具箱后端 API：通用翻译工具（跑批/进度/停止）与翻译引擎管理（增删改/连通测试/设默认）。
 * 页面路由见 {@link PageController#tools}。
 */
@RestController
@RequestMapping("/api/tools/translation")
public class ToolsController {

    private static final Logger logger = LoggerFactory.getLogger(ToolsController.class);

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a professional game localization translator. Translate the user's text into the target language, "
            + "preserving meaning, tone and any game-specific terms. Output ONLY the translated text, with no explanation or extra lines.";

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TranslationToolService translationToolService;

    // ==================== 元数据 ====================

    /** 可译表/字段清单、语种清单、当前有效引擎。 */
    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("game", "name", "游戏名称"));
        fields.add(field("game", "desc", "游戏描述"));
        fields.add(field("rom_db", "game_name", "ROM 游戏名"));
        result.put("success", true);
        result.put("fields", fields);
        result.put("languages", TranslationLanguages.all());
        result.put("targetLanguages", TranslationLanguages.targets());
        try {
            result.put("services", TranslationServiceManager.getInstance().getValidServices());
        } catch (Exception e) {
            result.put("services", new ArrayList<>());
        }
        return result;
    }

    private Map<String, Object> field(String table, String field, String label) {
        Map<String, Object> m = new HashMap<>();
        m.put("srcTable", table);
        m.put("srcField", field);
        m.put("label", label);
        return m;
    }

    // ==================== 跑批 ====================

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String taskId = translationToolService.start(request);
            result.put("success", true);
            result.put("taskId", taskId);
        } catch (Exception e) {
            logger.warn("启动翻译任务失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/progress/{taskId}")
    public Map<String, Object> progress(@PathVariable String taskId) {
        return translationToolService.getProgress(taskId);
    }

    @PostMapping("/stop/{taskId}")
    public Map<String, Object> stop(@PathVariable String taskId) {
        translationToolService.stop(taskId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    // ==================== 引擎管理 ====================

    /** 列出全部引擎（含未配置 Key 的无效项）与默认项。 */
    @GetMapping("/engines")
    public Map<String, Object> listEngines() {
        Map<String, Object> result = new HashMap<>();
        ConfigManager cm = ConfigManager.getInstance();
        List<Map<String, Object>> engines = new ArrayList<>();
        String defaultId = null;
        JsonNode config = cm.getConfig();
        if (config != null && config.has("translation")) {
            JsonNode tr = config.get("translation");
            if (tr.has("defaultService")) defaultId = tr.get("defaultService").asText();
            if (tr.has("services")) {
                Map<String, String> vars = cm.getVariables();
                for (JsonNode s : tr.get("services")) {
                    engines.add(describeService(s, vars));
                }
            }
        }
        result.put("success", true);
        result.put("engines", engines);
        result.put("defaultService", defaultId);
        return result;
    }

    private Map<String, Object> describeService(JsonNode s, Map<String, String> vars) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.has("id") ? s.get("id").asText() : null);
        m.put("name", s.has("name") ? s.get("name").asText() : null);
        m.put("type", s.has("type") ? s.get("type").asText() : "custom");
        m.put("url", s.has("url") ? s.get("url").asText() : null);
        m.put("valid", isValid(s, vars));
        // 展示可编辑字段（非机密：真实 Key 存于 variables，service 内仅 ${var} 占位）
        if (s.has("requestBody") && s.get("requestBody").has("model")) {
            m.put("model", s.get("requestBody").get("model").asText());
        }
        List<String> required = new ArrayList<>();
        List<Map<String, Object>> varStates = new ArrayList<>();
        if (s.has("validation") && s.get("validation").has("requiredVariables")) {
            for (JsonNode rv : s.get("validation").get("requiredVariables")) {
                String key = rv.asText();
                required.add(key);
                Map<String, Object> vs = new HashMap<>();
                String v = vars.get(key);
                vs.put("key", key);
                vs.put("set", v != null && !v.isBlank());
                varStates.add(vs);
            }
        }
        m.put("requiredVariables", required);
        m.put("variableStates", varStates);
        m.put("rawJson", s.toString());
        return m;
    }

    private boolean isValid(JsonNode s, Map<String, String> vars) {
        if (!s.has("validation")) return true;
        JsonNode validation = s.get("validation");
        if (validation.has("requiredVariables")) {
            for (JsonNode rv : validation.get("requiredVariables")) {
                String key = rv.asText();
                String v = vars.get(key);
                if (v == null || v.isBlank()) return false;
            }
        }
        return true;
    }

    /**
     * 新增/更新引擎。
     * body:
     *   mode = "openai"（默认）| "raw"
     *   openai: {id?, name, baseUrl, model, apiKey, systemPrompt?, temperature?}
     *   raw:    {rawJson: "<service json>", variables: {varName: value}}
     */
    @PostMapping("/engines")
    public Map<String, Object> saveEngine(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        ConfigManager cm = ConfigManager.getInstance();
        try {
            String mode = str(body.get("mode"));
            if (mode == null) mode = "openai";
            if ("raw".equals(mode)) {
                JsonNode service = mapper.readTree(str(body.get("rawJson")));
                if (!service.has("id")) throw new IllegalArgumentException("raw JSON 缺少 id");
                cm.upsertService(service);
                ObjectNode vars = (ObjectNode) service.path("validation").path("requiredVariables");
                // 存储用户填的变量值
                @SuppressWarnings("unchecked")
                Map<String, Object> varMap = (Map<String, Object>) body.get("variables");
                if (varMap != null) {
                    for (Map.Entry<String, Object> e : varMap.entrySet()) {
                        if (e.getValue() != null && !e.getValue().toString().isBlank()) {
                            cm.setVariable(e.getKey(), e.getValue().toString());
                        }
                    }
                }
            } else {
                String name = str(body.get("name"));
                String baseUrl = str(body.get("baseUrl"));
                String model = str(body.get("model"));
                String apiKey = str(body.get("apiKey"));
                String systemPrompt = str(body.get("systemPrompt"));
                Double temperature = toDouble(body.get("temperature"));
                if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
                    throw new IllegalArgumentException("Base URL 与 Model 必填");
                }
                String existingId = str(body.get("id"));
                String id = (existingId != null && !existingId.isBlank()) ? existingId : ("llm_" + slug(name != null && !name.isBlank() ? name : model));
                String apiKeyVar = id + "_api_key";
                ObjectNode service = buildOpenAiService(id, (name == null || name.isBlank()) ? model : name,
                        baseUrl, model, apiKeyVar, systemPrompt, temperature);
                cm.upsertService(service);
                if (apiKey != null && !apiKey.isBlank()) {
                    cm.setVariable(apiKeyVar, apiKey);
                }
            }
            TranslationServiceManager.getInstance().reloadServices();
            result.put("success", true);
        } catch (Exception e) {
            logger.warn("保存引擎失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    private ObjectNode buildOpenAiService(String id, String name, String baseUrl, String model,
                                          String apiKeyVar, String systemPrompt, Double temperature) {
        ObjectNode s = mapper.createObjectNode();
        s.put("id", id);
        s.put("name", name);
        s.put("type", "openai-compatible");
        s.put("url", rtrimSlash(baseUrl) + "/chat/completions");
        s.put("method", "POST");
        ObjectNode headers = s.putObject("headers");
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer ${" + apiKeyVar + "}");
        ObjectNode body = s.putObject("requestBody");
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : DEFAULT_SYSTEM_PROMPT);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", "Translate the following text to {targetLang}. Output ONLY the translation:\n{text}");
        body.put("temperature", temperature != null ? temperature.doubleValue() : 0.3);
        body.put("stream", false);
        body.put("enable_thinking", false);
        s.put("responsePath", "choices[0].message.content");
        ObjectNode validation = s.putObject("validation");
        ArrayNode req = validation.putArray("requiredVariables");
        req.add(apiKeyVar);
        return s;
    }

    @DeleteMapping("/engines/{id}")
    public Map<String, Object> deleteEngine(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean removed = ConfigManager.getInstance().removeService(id);
            TranslationServiceManager.getInstance().reloadServices();
            result.put("success", removed);
            if (!removed) result.put("message", "引擎不存在");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /** 连通测试：对已保存引擎发一句探针文本。 */
    @PostMapping("/engines/{id}/test")
    public Map<String, Object> testEngine(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        try {
            TranslationServiceManager.getInstance().reloadServices();
            TranslationService ts = TranslationServiceManager.getInstance().getTranslationService(id);
            if (ts == null) {
                result.put("success", false);
                result.put("message", "引擎不存在或未配置");
                return result;
            }
            String preview = ts.translate("Hello world", "auto", "zh");
            result.put("success", true);
            result.put("preview", preview);
        } catch (Exception e) {
            logger.warn("引擎连通测试失败: id={}, {}", id, e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/engines/default")
    public Map<String, Object> setDefault(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            ConfigManager.getInstance().setDefaultService(str(body.get("id")));
            TranslationServiceManager.getInstance().reloadServices();
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ==================== 辅助 ====================

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String rtrimSlash(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String slug(String name) {
        if (name == null || name.isBlank()) return "engine";
        String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("(^_+|_+$)", "");
        return s.isEmpty() ? "engine" : s;
    }
}
