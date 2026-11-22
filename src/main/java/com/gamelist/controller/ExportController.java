package com.gamelist.controller;

import com.gamelist.export.action.ExportAction;
import com.gamelist.export.action.ExportActionCatalog;
import com.gamelist.export.action.ExportActionPlanner;
import com.gamelist.model.ExportRequest;
import com.gamelist.model.Platform;
import com.gamelist.model.TemplateV3;
import com.gamelist.service.ExportRuleService;
import com.gamelist.service.ExportService;
import com.gamelist.service.PlatformService;
import com.gamelist.variant.ArtifactKind;
import com.gamelist.variant.SystemPipelineLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {
    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);

    @Autowired
    private ExportService exportService;

    @Autowired
    private ExportRuleService exportRuleService;

    @Autowired
    private ExportActionPlanner actionPlanner;

    @Autowired
    private ExportActionCatalog actionCatalog;

    @Autowired
    private SystemPipelineLibrary pipelineLibrary;

    @Autowired
    private PlatformService platformService;

    /**
     * 导出平台
     */
    @PostMapping("/platform")
    public ResponseEntity<Map<String, Object>> exportPlatform(@RequestBody ExportRequest request) {
        try {
            logger.info("Export platform request: {}", request);
            String missingErr = validateTemplateVariables(request.getFrontend(), request.getTemplateVariables());
            if (missingErr != null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", missingErr
                ));
            }
            String bindErr = validateActionConstraints(request.getFrontend(),
                    request.getPlatformId() != null ? List.of(request.getPlatformId()) : request.getPlatformIds());
            if (bindErr != null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", bindErr
                ));
            }
            Map<String, Object> result = exportService.exportPlatform(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Export platform failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Export failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量导出平台（单任务顺序执行，避免线程爆炸）
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchExport(@RequestBody ExportRequest request) {
        try {
            List<Long> platformIds = request.getPlatformIds();
            if (platformIds == null || platformIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "No platforms selected"
                ));
            }

            String missingErr = validateTemplateVariables(request.getFrontend(), request.getTemplateVariables());
            if (missingErr != null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", missingErr
                ));
            }

            String bindErr = validateActionConstraints(request.getFrontend(), platformIds);
            if (bindErr != null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", bindErr
                ));
            }

            logger.info("Batch export request: {} platforms, frontend={}", platformIds.size(), request.getFrontend());

            // 委托给 Service 层的批量导出方法（单任务顺序执行）
            Map<String, Object> batchResult = exportService.batchExport(platformIds, request);

            if (Boolean.TRUE.equals(batchResult.get("success"))) {
                return ResponseEntity.ok(batchResult);
            } else {
                return ResponseEntity.badRequest().body(batchResult);
            }
        } catch (Exception e) {
            logger.error("Batch export failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Batch export failed: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取导出规则列表（v3）
     */
    @GetMapping("/rules")
    public ResponseEntity<Object> getExportRules() {
        try {
            exportRuleService.loadRules();

            List<Map<String, Object>> ruleList = new ArrayList<>();

            for (String frontend : exportRuleService.getV3FrontendKeys()) {
                TemplateV3 v3 = exportRuleService.getV3RuleByFrontend(frontend);
                if (v3 == null) continue;

                Map<String, Object> item = new HashMap<>();
                item.put("frontend", frontend);
                String fullDesc = (v3.getTemplateInfo() != null && v3.getTemplateInfo().getDescription() != null)
                        ? v3.getTemplateInfo().getDescription() : frontend;
                item.put("name", fullDesc);
                // 短名称：取 description 中第一个 " — " 之前的部分（即英文名），回退到 " / "
                String shortName = fullDesc.contains(" — ") ? fullDesc.substring(0, fullDesc.indexOf(" — "))
                        : fullDesc.contains(" / ") ? fullDesc.substring(0, fullDesc.indexOf(" / "))
                        : fullDesc;
                item.put("shortName", shortName);
                item.put("version", 3);

                if (v3.getOutput() != null && v3.getOutput().getExportOptions() != null) {
                    TemplateV3.ExportOptions opts = v3.getOutput().getExportOptions();
                    Map<String, Object> exportOptions = new HashMap<>();
                    exportOptions.put("gameFiles", opts.isGameFiles());
                    exportOptions.put("mediaFiles", opts.isMediaFiles());
                    exportOptions.put("gameListFile", opts.isGameListFile());
                    exportOptions.put("showWarning", opts.isShowWarning());
                    item.put("exportOptions", exportOptions);
                }

                // 模板声明的媒体类型列表（源 nomcourt），供刮削模态框"从模板预选"使用
                List<String> mediaTypes = new ArrayList<>();
                if (v3.getOutput() != null && v3.getOutput().getMedia() != null
                        && v3.getOutput().getMedia().getRules() != null) {
                    for (Map.Entry<String, TemplateV3.MediaOutputRule> entry
                            : v3.getOutput().getMedia().getRules().entrySet()) {
                        String nomcourt = entry.getKey();
                        TemplateV3.MediaOutputRule rule = entry.getValue();
                        // key 为源 nomcourt；key 缺失语义时回退到 rule.source
                        if (nomcourt == null || nomcourt.isBlank()) {
                            nomcourt = (rule != null) ? rule.getSource() : null;
                        }
                        if (nomcourt != null && !nomcourt.isBlank() && !mediaTypes.contains(nomcourt)) {
                            mediaTypes.add(nomcourt);
                        }
                    }
                }
                item.put("mediaTypes", mediaTypes);

                // 模板声明的执行前变量（供前端渲染变量设定弹窗）
                List<Map<String, Object>> varDecls = new ArrayList<>();
                for (TemplateV3.TemplateVariable var : v3.getValidVariables()) {
                    varDecls.add(variableToMap(var));
                }
                item.put("variables", varDecls);

                // 动作绑定约束（供前端选模板前置检查：单平台/适用系统）
                item.put("actionConstraints", constraintsToMap(actionPlanner.constraints(v3)));

                ruleList.add(item);
            }

            return ResponseEntity.ok(ruleList);
        } catch (Exception e) {
            logger.error("Get export rules failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Failed to get export rules: " + e.getMessage()
            ));
        }
    }

    /**
     * 校验模板声明的必填变量是否已由用户填写。
     * @return 缺失时返回错误信息（含缺失变量名），全部满足时返回 null
     */
    private String validateTemplateVariables(String frontend, Map<String, String> userVars) {
        if (frontend == null || frontend.isEmpty()) return null;
        TemplateV3 v3 = exportRuleService.getV3RuleByFrontend(frontend);
        if (v3 == null) return null;
        List<String> missing = new ArrayList<>();
        for (TemplateV3.TemplateVariable var : v3.getValidVariables()) {
            if (!var.isRequired()) continue;
            String value = userVars != null ? userVars.get(var.getName()) : null;
            if (value == null || value.trim().isEmpty()) {
                missing.add(var.getLabel() != null && !var.getLabel().isEmpty() ? var.getLabel() : var.getName());
            }
        }
        if (!missing.isEmpty()) {
            return "请先填写必需的模板变量: " + String.join(", ", missing);
        }
        return null;
    }

    /** 将变量声明转为前端可读的 Map（default 键避免 Java 关键字冲突） */
    private Map<String, Object> variableToMap(TemplateV3.TemplateVariable var) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", var.getName());
        m.put("label", var.getLabel() != null ? var.getLabel() : var.getName());
        m.put("description", var.getDescription());
        String type = var.getType() != null ? var.getType() : "text";
        m.put("type", type);
        m.put("default", var.getDefaultValue());
        m.put("required", var.isRequired());
        // 绑定导出动作：控件形态与作用域由动作目录权威定义
        if (var.hasAction()) {
            String actionId = var.getAction().trim();
            m.put("action", actionId);
            ExportAction action = actionCatalog.get(actionId);
            if (action != null) {
                m.put("type", action.variableType());
                m.put("actionNote", action.note());
                if (action.optionsFromPipeline()) {
                    m.put("optionsSource", "pipeline");
                }
            }
        }
        return m;
    }

    /** 动作绑定约束转前端可读 Map */
    private Map<String, Object> constraintsToMap(ExportActionPlanner.ActionConstraints c) {
        Map<String, Object> m = new HashMap<>();
        m.put("hasActions", c.hasActions());
        m.put("singlePlatform", c.requiresSinglePlatform());
        m.put("supportedSystemIds", c.supportedSystemIds() == null
                ? null : new ArrayList<>(c.supportedSystemIds()));
        return m;
    }

    /**
     * 获取某系统可用的目标格式候选（供导出时格式转换的 select 控件使用）。
     * <p>
     * 来源为该系统产线声明的 enabled 目标格式；前端据此渲染下拉选项。
     */
    @GetMapping("/format-options")
    public ResponseEntity<Object> getFormatOptions(@RequestParam("systemId") Integer systemId) {
        List<Map<String, String>> options = new ArrayList<>();
        if (systemId != null) {
            for (SystemPipelineLibrary.Target target : pipelineLibrary.targets(systemId)) {
                if (!target.enabled()) {
                    continue;
                }
                ArtifactKind kind = SystemPipelineLibrary.targetKind(target.format());
                String label = kind != null ? kind.display() : target.format().toUpperCase();
                Map<String, String> opt = new HashMap<>();
                opt.put("value", target.format());
                opt.put("label", label);
                options.add(opt);
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "options", options));
    }

    /**
     * 后端兜底绑定校验：有分片/转换动作的模板必须单平台，且平台 systemId 须在动作作用范围内。
     * @return 不适用时返回错误信息（阻止导出），通过时返回 null
     */
    private String validateActionConstraints(String frontend, List<Long> platformIds) {
        if (frontend == null || frontend.isEmpty() || platformIds == null || platformIds.isEmpty()) {
            return null;
        }
        TemplateV3 v3 = exportRuleService.getV3RuleByFrontend(frontend);
        if (v3 == null) {
            return null;
        }
        ExportActionPlanner.ActionConstraints c = actionPlanner.constraints(v3);
        if (!c.hasActions()) {
            return null;
        }
        if (c.requiresSinglePlatform() && platformIds.size() > 1) {
            return "该模板含分片/格式转换动作，只能对单个平台导出，请只选择一个平台";
        }
        for (Long pid : platformIds) {
            Platform platform = platformService.getPlatformById(pid);
            if (platform == null) {
                continue;
            }
            int systemId = platform.getSystemId() == null ? 0 : platform.getSystemId();
            if (!c.allowsSystem(systemId)) {
                String name = platform.getName() != null ? platform.getName() : ("ID=" + pid);
                return "模板绑定的导出动作不适用于平台「" + name + "」（该系统不在动作作用范围），请更换平台";
            }
        }
        return null;
    }
}
