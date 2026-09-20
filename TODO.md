# 备忘录 — 待实现想法清单

> 记录开发过程中的想法与待办，不保证一定会实现，仅作备忘。
> 最后更新：2026-09-20
>
> **当前焦点：第 6、7 条**（刮削媒体类型模板预选、文件夹类游戏平台方案），其余条目暂缓。

---

## 1. 数据导入：线程数选择功能

**现状**：页面上的"线程数"选择在数据库插入阶段基本无效——`importGamesInBatches` 中虽然按 threadCount 分 chunk 并行，但 `synchronized (GameServiceImpl.class)` 锁导致实际串行执行。媒体发现阶段用固定 4 线程（不受此参数控制），已经真正并行。

**想法**：
- 去掉 `synchronized` 锁，改用真正的多线程批量插入
- 或研究 H2 的批量写入 API（`PreparedStatement.addBatch` / 多值 INSERT）
- 让页面线程数选择真正生效，或干脆从 UI 上移除该选项避免误导

**来源**：用户问"线程的选择功能现在有用吗？"后的调查结论，暂缓处理。

---

## 2. 静态页面 → Thymeleaf 迁移（已完成）

**已完成**：全部 18 个页面迁移完毕，`static/` 目录下旧页面已清理（无残留）。
index（首页）、data-import（数据导入）、platform-management（平台管理）、game-list（游戏列表）、game-edit（游戏编辑）、export（数据导出）、media-download（媒体下载）、task-management（任务管理）、log-viewer（日志查看）、system-settings（系统设置）、platform-merge（数据合并）、scraper-system-list / scraper-system-edit（刮削系统）、platform-details（平台详情）、merge-reports / merge-report-details / merge-conflicts（合并报告）、temp-subset-edit（临时子集编辑）。

**迁移踩坑经验（留档）**：
- 页面自有 CSS 类名要与 theme.css 隔离，避免 `.modal`、`.btn` 等通用类名冲突（platform-management 用 `pm-` 前缀解决）
- 模态框结构：遮罩层 `overflow: hidden` + 内容层 `overflow-y: auto` + `margin: 5vh auto` 居中，避免双层滚动条
- 模态框打开时锁定 body 滚动（MutationObserver 监听），关闭时恢复

---

## 3. 通知模态框类名统一

**现状**：`fragments/layout.html` 的通知模态框 fragment 仍用 `.modal` / `.modal-content` 类名（theme.css 体系）。本批风格统一改造时，各页面 CSS 均已补 `#notificationModal.modal` 特判样式（已铺开到全部页面），但 fragment 类名本身尚未统一。

**想法**：把 fragment 通知模态框改用 `.modal-overlay` + `.modal` 结构（与 index.html 一致），或统一重命名类，消除各页面散落的特判样式。

---

## 4. 导入性能（已完成，留档）

- 茎名索引（去扩展名 HashMap 查找）✅
- 预构建索引共享（每游戏 1 次而非 17 次）✅
- 4 线程并行媒体路径处理 ✅
- 结果：548 游戏从 10+ 分钟 → 1-2 分钟

**剩余空间**：数据库插入阶段（见第 1 条）。

---

## 5. 音效系统与效果包

**想法**：
- 给每个动作（点击、保存、删除、刮削完成、任务结束等）增加音效
- 本地软件无需考虑网络，音效资源直接内置或从本地目录读取
- 远期：做成完整的效果包概念，把配色主题 + 音效打包，支持一键切换整套视听风格

**待讨论**：
- 音效文件来源与格式（WAV/MP3）、触发时机、音量控制开关
- 效果包结构设计（目录规范、manifest、主题 token 与音效的对应关系）

---

## 6. 刮削媒体类型从模板预选（当前优先）

**想法**：platform-management 的刮削按钮增加一个选项——从导入/导出模板（v3 模板的 mediaDiscovery / media 配置）中读取所需的媒体类型列表，自动勾选刮削范围，避免用户手动一个个选。

**现状**：刮削模态框需要用户手动勾选游戏信息/媒体文件及具体媒体类型。

**思路**：
- 读取 `rules/import/` 或 `rules/export/` 下的 v3 模板
- 解析模板中声明的媒体类型（如 boxFront、screenshot、video 等）
- 在刮削模态框中提供下拉/按钮：选择某模板 → 自动勾选对应媒体类型

---

## 7. 文件夹类游戏平台完整方案（当前优先，需详细讨论）

**背景**：部分平台的游戏以文件夹形式存在（一个游戏 = 一个目录，内含多个文件，而非单个 ROM 文件）。

**待梳理的问题**：
- 文件夹类平台的识别规则（哪些平台属于此类）
- 导入扫描时如何判断一个文件夹是一个游戏（而非嵌套目录）
- ROM 文件定位、CRC 计算策略（文件夹内多个文件时取哪个？）
- 媒体文件在文件夹内的存放与发现规则
- 导出时如何处理文件夹结构
- 与现有单文件 ROM 流程的统一与差异

**状态**：需要详细讨论和整理后形成方案。

---

## 8. 其他零散想法

- 刮削模态框"选择语种"等选项与刮削结果联动验证
- 静态资源缓存问题：HTML 更新后浏览器 Ctrl+F5 可能不生效，需关标签页重开（后续可在 Spring 配置里加 no-cache 头或版本号参数）

---

## 9. 页面风格统一改造（已完成，留档）

- 全站统一紫色系设计令牌（`--accent-primary: #8b5cf6` 等，theme.css）
- 以 task-management 为样板，铺开其余 12 个页面：删除大发光横幅、旧导航按钮行（返回首页）、底部 footer
- 容器类统一 `.pg-container`，内联样式全部转 CSS 类，按钮体系规范化（btn-primary/success/danger/ghost）
- 补齐各页面弹窗遮罩（#notificationModal.modal、#editModal.modal、.modal-overlay 系列）
- 顺手修复存量 bug：temp-subset-edit 通知已读逻辑（unreadCount 永不递减）、platform-details 删除 banner 后的 JS 空引用
- 存量缺口（未处理）：/api/merge-reports 后端 API 未实现（merge-reports 系列页面暂不可用）

---

## 10. 已导入数据去重与删除已导出文件（需慎重讨论）

**需求来源**：用户提出希望能对已导入的信息进行去重，以及删除已导出的文件。

**需慎重考虑的点**：
- **安全**：删除类操作不可逆，必须有二次确认、防误删机制（回收站/备份/白名单）
- **审查**：去重判据如何定义（按 ROM 名？CRC？路径？显示名？多平台同名游戏是否算重复？），去重结果是否需要人工复核
- **范围**：去重是自动还是提供预览工具？删除已导出文件是单个文件还是整目录？

**状态**：暂缓，待讨论后明确方案再实施。
