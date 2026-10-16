# 任务管理功能优化实施指南

## 已完成的后端修改

### 1. 数据库迁移
- ✅ 创建 `V1.0.20__task_management_enhancements.sql`
  - 添加 `cancellable` 字段到 `background_task` 表
  - 添加索引优化查询性能

### 2. 模型层
- ✅ `BackgroundTask.java`
  - 添加 `cancellable` 字段及 getter/setter

### 3. MyBatis 映射
- ✅ `BackgroundTaskMapper.xml`
  - 更新 INSERT/UPDATE 语句包含 `cancellable` 字段
  - 更新 ResultMap 映射

### 4. Service 层
- ✅ `TaskService.java` 接口
  - 新增 `createTask(type, description, cancellable)` 重载方法
  - 新增 `deleteTasks(List<Long>)` 批量删除方法
  - 新增 `cancelTask(Long)` 取消任务方法
  - 增强 `deleteTask()` 防止删除运行中任务

- ✅ `TaskServiceImpl.java` 实现
  - 实现所有新方法
  - 添加状态检查逻辑

### 5. Controller 层
- ✅ `TaskController.java`
  - `/api/tasks/{id}` DELETE - 返回 JSON 响应，处理异常
  - `/api/tasks/batch` DELETE - 批量删除任务
  - `/api/tasks/{id}/cancel` POST - 取消任务

### 6. 业务服务
- ✅ `ExportServiceImpl.java` - 导出任务标记为可取消
- ✅ `ScanController.java` - 导入任务标记为可取消

---

## 待完成的前端修改

### 需要修改的文件
`src/main/resources/templates/task-management.html`

### 修改内容

#### 1. 任务卡片渲染逻辑（约第 293-365 行）

**当前代码：**
```javascript
<div class="task-actions">
    <button class="btn btn-danger" onclick="deleteTask('${task.id}')">
        ${getI18nText('delete')}
    </button>
</div>
```

**修改为：**
```javascript
<div class="task-actions">
    ${task.status === 'RUNNING' && task.cancellable ? `
        <button class="btn btn-warning" onclick="cancelTask('${task.id}')">
            ${getI18nText('stop') || '停止'}
        </button>
    ` : ''}
    
    ${task.status !== 'RUNNING' ? `
        <button class="btn btn-danger" onclick="deleteTask('${task.id}')">
            ${getI18nText('delete')}
        </button>
    ` : `
        <button class="btn btn-danger" disabled title="${getI18nText('cannot_delete_running') || '无法删除运行中的任务'}">
            ${getI18nText('delete')}
        </button>
    `}
</div>
```

#### 2. 添加批量操作工具栏（在任务列表上方）

在 `<div id="taskList">` 之前添加：
```html
<div class="batch-actions" style="display: none; margin-bottom: 16px;">
    <button id="batchDeleteBtn" class="btn btn-danger" onclick="batchDeleteSelected()">
        ${getI18nText('batch_delete') || '批量删除'}
    </button>
    <span id="selectedCount" style="margin-left: 12px; color: #888;"></span>
</div>
```

#### 3. 为每个任务卡片添加复选框

修改任务卡片模板（约第 294 行）：
```javascript
<div class="task-card" data-task-id="${task.id}">
    <div class="task-checkbox">
        <input type="checkbox" 
               onchange="toggleTaskSelection('${task.id}')" 
               ${task.status === 'RUNNING' ? 'disabled' : ''}>
    </div>
    <div class="task-card-header">
        ...
```

#### 4. 添加 JavaScript 函数

在 `<script>` 标签末尾添加：

```javascript
// 任务选择管理
const selectedTasks = new Set();

function toggleTaskSelection(taskId) {
    if (selectedTasks.has(taskId)) {
        selectedTasks.delete(taskId);
    } else {
        selectedTasks.add(taskId);
    }
    updateBatchActionsVisibility();
}

function updateBatchActionsVisibility() {
    const batchActions = document.querySelector('.batch-actions');
    const selectedCount = document.getElementById('selectedCount');
    
    if (selectedTasks.size > 0) {
        batchActions.style.display = 'block';
        selectedCount.textContent = `已选择 ${selectedTasks.size} 个任务`;
    } else {
        batchActions.style.display = 'none';
    }
}

// 取消任务
function cancelTask(taskId) {
    if (!confirm(getI18nText('confirm_cancel_task') || '确定要取消此任务吗？')) {
        return;
    }
    
    fetch(`/api/tasks/${taskId}/cancel`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            loadTasks(); // 刷新任务列表
        } else {
            alert(data.message || getI18nText('cancel_failed'));
        }
    })
    .catch(error => {
        console.error('取消任务失败:', error);
        alert(getI18nText('cancel_failed'));
    });
}

// 批量删除
function batchDeleteSelected() {
    if (selectedTasks.size === 0) {
        return;
    }
    
    if (!confirm(`确定要删除选中的 ${selectedTasks.size} 个任务吗？`)) {
        return;
    }
    
    fetch('/api/tasks/batch', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(Array.from(selectedTasks))
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            selectedTasks.clear();
            updateBatchActionsVisibility();
            loadTasks();
        } else {
            alert(data.message || '批量删除失败');
        }
    })
    .catch(error => {
        console.error('批量删除失败:', error);
        alert('批量删除失败');
    });
}
```

#### 5. 添加 CSS 样式

在 `<style>` 标签或 CSS 文件中添加：
```css
.task-checkbox {
    position: absolute;
    top: 12px;
    left: 12px;
    z-index: 10;
}

.task-card {
    position: relative;
    padding-left: 40px; /* 为复选框留出空间 */
}

.batch-actions {
    padding: 12px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 8px;
    display: flex;
    align-items: center;
}

.btn-warning {
    background-color: #f59e0b;
    color: white;
}

.btn-warning:hover {
    background-color: #d97706;
}
```

---

## 国际化文本

需要在 `i18n.js` 中添加以下翻译键：

```javascript
// zh-CN
{
    "stop": "停止",
    "cannot_delete_running": "无法删除运行中的任务",
    "batch_delete": "批量删除",
    "confirm_cancel_task": "确定要取消此任务吗？",
    "cancel_failed": "取消失败"
}

// en-US
{
    "stop": "Stop",
    "cannot_delete_running": "Cannot delete running task",
    "batch_delete": "Batch Delete",
    "confirm_cancel_task": "Are you sure you want to cancel this task?",
    "cancel_failed": "Cancel failed"
}

// ja-JP
{
    "stop": "停止",
    "cannot_delete_running": "実行中のタスクは削除できません",
    "batch_delete": "一括削除",
    "confirm_cancel_task": "このタスクをキャンセルしてもよろしいですか？",
    "cancel_failed": "キャンセルに失敗しました"
}
```

---

## 测试清单

### 后端测试
1. ✅ 数据库迁移成功执行
2. ✅ 创建可取消的导入/导出任务
3. ✅ 尝试删除运行中任务应抛出异常
4. ✅ 取消任务后状态变为 CANCELLED
5. ✅ 批量删除跳过运行中任务

### 前端测试
1. ⏳ 运行中且可取消的任务显示"停止"按钮
2. ⏳ 运行中任务的删除按钮禁用
3. ⏳ 已完成/失败任务可选择并批量删除
4. ⏳ 点击"停止"按钮调用取消 API
5. ⏳ 批量删除后刷新列表

---

## 注意事项

1. **取消机制的实际效果**：
   - 当前实现只是将任务状态标记为 CANCELLED
   - 实际的业务逻辑（导入/导出循环）需要定期检查任务状态
   - 建议在长时间运行的循环中添加状态检查点

2. **示例：在 ExportOrchestrator 中添加取消检查**
```java
// 在 copyGameFiles 循环中添加
for (Game game : games) {
    // 检查任务是否被取消
    BackgroundTask currentTask = taskService.getTaskById(taskId);
    if ("CANCELLED".equals(currentTask.getStatus())) {
        logger.info("任务已被取消，停止导出");
        break;
    }
    // ... 继续处理
}
```

3. **媒体下载任务的取消**：
   - 媒体下载使用独立的任务系统（media_download_task 表）
   - 已有停止功能，无需额外修改

---

## 下一步建议

1. 在导入/导出循环中添加取消检查点
2. 为刮削任务也添加取消支持（如果需要）
3. 添加任务历史记录清理策略（自动删除 30 天前的已完成任务）
4. 添加任务优先级管理功能
