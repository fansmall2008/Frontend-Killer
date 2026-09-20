package com.gamelist.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面路由控制器
 * 负责将 Thymeleaf 模板渲染为 HTML 页面
 * 其他静态页面仍由 Spring Boot 的静态资源服务直接提供
 */
@Controller
public class PageController {

    /**
     * 首页仪表盘
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "DASHBOARD");
        return "index";
    }

    /**
     * 数据导入页面
     */
    @GetMapping("/data-import")
    public String dataImport(Model model) {
        model.addAttribute("pageTitle", "DATA_IMPORT");
        return "data-import";
    }

    /**
     * 平台管理页面
     */
    @GetMapping("/platform-management")
    public String platformManagement(Model model) {
        model.addAttribute("pageTitle", "PLATFORM_MANAGEMENT");
        return "platform-management";
    }

    /**
     * 游戏列表页面
     */
    @GetMapping("/game-list")
    public String gameList(Model model) {
        model.addAttribute("pageTitle", "GAME_LIST");
        return "game-list";
    }

    /**
     * 游戏编辑页面
     */
    @GetMapping("/game-edit")
    public String gameEdit(Model model) {
        model.addAttribute("pageTitle", "GAME_EDIT");
        return "game-edit";
    }

    /**
     * 数据导出页面
     */
    @GetMapping("/export")
    public String exportPage(Model model) {
        model.addAttribute("pageTitle", "EXPORT");
        return "export";
    }

    /**
     * 日志查看器页面
     */
    @GetMapping("/log-viewer")
    public String logViewer(Model model) {
        model.addAttribute("pageTitle", "LOG_VIEWER");
        return "log-viewer";
    }

    /**
     * 媒体下载管理页面
     */
    @GetMapping("/media-download")
    public String mediaDownload(Model model) {
        model.addAttribute("pageTitle", "MEDIA_DOWNLOAD");
        return "media-download";
    }

    /**
     * 合并冲突处理页面
     */
    @GetMapping("/merge-conflicts")
    public String mergeConflicts(Model model) {
        model.addAttribute("pageTitle", "MERGE_CONFLICTS");
        return "merge-conflicts";
    }

    /**
     * 合并报告详情页面
     */
    @GetMapping("/merge-report-details")
    public String mergeReportDetails(Model model) {
        model.addAttribute("pageTitle", "MERGE_REPORT_DETAILS");
        return "merge-report-details";
    }

    /**
     * 合并报告管理页面
     */
    @GetMapping("/merge-reports")
    public String mergeReports(Model model) {
        model.addAttribute("pageTitle", "MERGE_REPORTS");
        return "merge-reports";
    }

    /**
     * 平台详情页面
     */
    @GetMapping("/platform-details")
    public String platformDetails(Model model) {
        model.addAttribute("pageTitle", "PLATFORM_DETAILS");
        return "platform-details";
    }

    /**
     * 平台合并页面
     */
    @GetMapping("/platform-merge")
    public String platformMerge(Model model) {
        model.addAttribute("pageTitle", "PLATFORM_MERGE");
        return "platform-merge";
    }

    /**
     * 刮削系统编辑页面
     */
    @GetMapping("/scraper-system-edit")
    public String scraperSystemEdit(Model model) {
        model.addAttribute("pageTitle", "SCRAPER_SYSTEM_EDIT");
        return "scraper-system-edit";
    }

    /**
     * 刮削系统管理页面
     */
    @GetMapping("/scraper-system-list")
    public String scraperSystemList(Model model) {
        model.addAttribute("pageTitle", "SCRAPER_SYSTEM_LIST");
        return "scraper-system-list";
    }

    /**
     * 系统设置页面
     */
    @GetMapping("/system-settings")
    public String systemSettings(Model model) {
        model.addAttribute("pageTitle", "SYSTEM_SETTINGS");
        return "system-settings";
    }

    /**
     * 任务管理页面
     */
    @GetMapping("/task-management")
    public String taskManagement(Model model) {
        model.addAttribute("pageTitle", "TASK_MANAGEMENT");
        return "task-management";
    }

    /**
     * 临时子集修改页面
     */
    @GetMapping("/temp-subset-edit")
    public String tempSubsetEdit(Model model) {
        model.addAttribute("pageTitle", "TEMP_SUBSET_EDIT");
        return "temp-subset-edit";
    }
}
