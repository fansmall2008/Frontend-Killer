/**
 * TemplateVars — 模板执行前变量设定弹窗（导入/导出共用）。
 *
 * 当 v3 模板声明了 variables 块时，页面在真正执行导入/导出动作前调用 TemplateVars.open()
 * 弹出对话框，逐项展示：变量名（label）、输入框、用途说明（description）。
 * 用户填写的值以 {name: value} 形式回传，随请求提交给后端注入表达式引擎与 {name} 占位符。
 *
 * 设计要点：
 * - 零依赖：自带样式（tv- 前缀，避免与全局主题 CSS 冲突），仅可选使用全局 I18n。
 * - 键盘可达：Esc 关闭、Enter 提交、打开后自动聚焦首个输入框。
 * - default 值：若不含 {占位符} 则作为初值预填；含占位符（如 {platform.system}）时
 *   交由后端按平台解析，输入框仅作 placeholder 提示。
 *
 * 用法：
 *   TemplateVars.open(declarations, {
 *     onConfirm: function (valuesMap) { ... },   // 用户确认后回调，valuesMap = {name:value}
 *     onCancel:  function () { ... }             // 可选
 *   });
 */
(function (global) {
    'use strict';

    var STYLE_ID = 'tv-style';
    var varOverlay = null;
    var currentOnConfirm = null;
    var currentOnCancel = null;

    function t(key, fallback) {
        try {
            if (global.I18n && typeof global.I18n.t === 'function') {
                var v = global.I18n.t(key, fallback);
                // I18n.t 缺省时会回传 key 本身，此时用 fallback
                if (v && v !== key) return v;
            }
        } catch (e) { /* ignore */ }
        return fallback;
    }

    function ensureStyle() {
        if (document.getElementById(STYLE_ID)) return;
        var css = [
            '.tv-overlay{position:fixed;inset:0;background:rgba(0,0,0,.6);z-index:9999;',
            'display:flex;align-items:center;justify-content:center;padding:16px;}',
            '.tv-dialog{background:var(--panel-bg,#1e1e28);color:var(--text-color,#e6e6e6);',
            'border:1px solid var(--border-color,#333);border-radius:10px;max-width:520px;width:100%;',
            'max-height:80vh;overflow:auto;box-shadow:0 10px 40px rgba(0,0,0,.5);}',
            '.tv-header{padding:16px 20px;border-bottom:1px solid var(--border-color,#333);',
            'font-size:16px;font-weight:600;}',
            '.tv-body{padding:16px 20px;}',
            '.tv-field{margin-bottom:18px;}',
            '.tv-label{display:block;font-weight:600;margin-bottom:4px;}',
            '.tv-required{color:#e5484d;margin-left:4px;}',
            '.tv-desc{font-size:12px;opacity:.75;margin-bottom:6px;line-height:1.4;}',
            '.tv-input{width:100%;box-sizing:border-box;padding:8px 10px;border-radius:6px;',
            'border:1px solid var(--border-color,#444);background:var(--input-bg,#14141b);',
            'color:inherit;font-size:14px;}',
            '.tv-input.tv-invalid{border-color:#e5484d;}',
            '.tv-error{color:#e5484d;font-size:12px;margin-top:4px;display:none;}',
            '.tv-footer{padding:12px 20px 18px;display:flex;justify-content:flex-end;gap:10px;}',
            '.tv-btn{padding:8px 18px;border-radius:6px;border:1px solid var(--border-color,#444);',
            'background:transparent;color:inherit;cursor:pointer;font-size:14px;}',
            '.tv-btn-primary{background:var(--accent-color,#4f7cff);border-color:var(--accent-color,#4f7cff);color:#fff;}',
            '.tv-btn:hover{opacity:.9;}'
        ].join('');
        var style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = css;
        document.head.appendChild(style);
    }

    function hasPlaceholder(s) {
        return s && s.indexOf('{') >= 0;
    }

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    function buildFieldHtml(decl) {
        var label = decl.label || decl.name;
        var required = !!decl.required;
        var desc = decl.description || '';
        var def = decl['default'];
        var prefill = (!hasPlaceholder(def)) ? (def || '') : '';
        var placeholder = hasPlaceholder(def) ? def : '';
        var typeHint = (decl.type === 'path')
            ? t('tv_path_placeholder', '请输入路径')
            : '';
        var ph = placeholder || typeHint;
        var html = '<div class="tv-field" data-name="' + escapeHtml(decl.name) + '">';
        html += '<label class="tv-label">' + escapeHtml(label);
        if (required) html += '<span class="tv-required" title="' + escapeHtml(t('tv_required_tip', '必填')) + '">*</span>';
        html += '</label>';
        if (desc) html += '<div class="tv-desc">' + escapeHtml(desc) + '</div>';
        html += '<input class="tv-input" type="text" data-name="' + escapeHtml(decl.name) + '"';
        html += ' data-required="' + (required ? '1' : '0') + '"';
        html += ' value="' + escapeHtml(prefill) + '"';
        html += ' placeholder="' + escapeHtml(ph) + '">';
        html += '<div class="tv-error">' + escapeHtml(t('tv_required_error', '此项为必填')) + '</div>';
        html += '</div>';
        return html;
    }

    function close() {
        if (varOverlay && varOverlay.parentNode) {
            varOverlay.parentNode.removeChild(varOverlay);
        }
        varOverlay = null;
        currentOnConfirm = null;
        currentOnCancel = null;
        document.removeEventListener('keydown', onKeydown, true);
    }

    function onKeydown(e) {
        if (e.key === 'Escape') {
            e.preventDefault();
            var cb = currentOnCancel;
            close();
            if (cb) cb();
        } else if (e.key === 'Enter' && e.target && e.target.classList && e.target.classList.contains('tv-input')) {
            e.preventDefault();
            doConfirm();
        }
    }

    function doConfirm() {
        var inputs = varOverlay.querySelectorAll('input.tv-input');
        var values = {};
        var firstInvalid = null;
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            var name = input.getAttribute('data-name');
            var required = input.getAttribute('data-required') === '1';
            var val = (input.value || '').trim();
            var field = input.parentNode;
            var errEl = field.querySelector('.tv-error');
            if (required && !val) {
                input.classList.add('tv-invalid');
                if (errEl) errEl.style.display = 'block';
                if (!firstInvalid) firstInvalid = input;
                continue;
            }
            input.classList.remove('tv-invalid');
            if (errEl) errEl.style.display = 'none';
            if (val) values[name] = val;
        }
        if (firstInvalid) {
            firstInvalid.focus();
            return;
        }
        var cb = currentOnConfirm;
        close();
        if (cb) cb(values);
    }

    /**
     * 打开变量设定弹窗。
     * @param {Array} declarations 后端返回的变量声明数组
     * @param {Object} options { onConfirm(valuesMap), onCancel() }
     */
    function open(declarations, options) {
        options = options || {};
        if (!declarations || !declarations.length) {
            // 无需设定，直接回调空值
            if (options.onConfirm) options.onConfirm({});
            return;
        }
        ensureStyle();
        close(); // 清理残留

        currentOnConfirm = options.onConfirm || null;
        currentOnCancel = options.onCancel || null;

        varOverlay = document.createElement('div');
        varOverlay.className = 'tv-overlay';

        var fieldsHtml = declarations.map(buildFieldHtml).join('');
        var title = options.title || t('tv_modal_title', '模板变量设定');
        var cancelText = t('tv_cancel', '取消');
        var confirmText = t('tv_confirm', '确定');

        varOverlay.innerHTML =
            '<div class="tv-dialog" role="dialog" aria-modal="true" aria-label="' + escapeHtml(title) + '">' +
              '<div class="tv-header">' + escapeHtml(title) + '</div>' +
              '<div class="tv-body">' + fieldsHtml + '</div>' +
              '<div class="tv-footer">' +
                '<button type="button" class="tv-btn tv-btn-cancel">' + escapeHtml(cancelText) + '</button>' +
                '<button type="button" class="tv-btn tv-btn-primary">' + escapeHtml(confirmText) + '</button>' +
              '</div>' +
            '</div>';

        // 事件绑定
        varOverlay.querySelector('.tv-btn-cancel').addEventListener('click', function () {
            var cb = currentOnCancel;
            close();
            if (cb) cb();
        });
        varOverlay.querySelector('.tv-btn-primary').addEventListener('click', doConfirm);
        varOverlay.addEventListener('mousedown', function (e) {
            if (e.target === varOverlay) {
                var cb = currentOnCancel;
                close();
                if (cb) cb();
            }
        });

        document.body.appendChild(varOverlay);
        document.addEventListener('keydown', onKeydown, true);

        var first = varOverlay.querySelector('input.tv-input');
        if (first) first.focus();
    }

    /**
     * 工具：从指定前端的规则数组中取出变量声明。
     * @param {Array} rules loadFrontends 拿到的规则数组
     * @param {string} frontendKey 选中的 frontend
     */
    function getDeclarationsForFrontend(rules, frontendKey) {
        if (!rules || !frontendKey) return [];
        for (var i = 0; i < rules.length; i++) {
            if (rules[i] && rules[i].frontend === frontendKey) {
                return rules[i].variables || [];
            }
        }
        return [];
    }

    global.TemplateVars = {
        open: open,
        close: close,
        getDeclarationsForFrontend: getDeclarationsForFrontend
    };
})(window);
