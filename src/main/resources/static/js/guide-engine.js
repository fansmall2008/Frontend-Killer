/* ============================================================
   Frontend-Killer 导引引擎（像素 RPG 风）— P0
   自包含：动态注入 CSS、构建 DOM、加载 JSON、驱动对话。
   零外部依赖，离线可用。仅通过 common-scripts 里一行 <script> 接入。
   ============================================================ */
(function () {
    'use strict';

    var SPRITE_BASE = '/guide-sprites/';
    var GUIDE_BASE = '/guides/';
    var TYPE_MS = 28;

    // ---- 运行时状态 ----
    var state = {
        pageKey: null,
        guide: null,        // { version, steps:[...] }
        stepIdx: 0,
        sentIdx: 0,
        typing: false,
        typeTimer: null,
        fullText: '',
        lang: 'zh-CN'
    };

    // ---- DOM 引用 ----
    var dom = {};

    // ---- i18n 辅助 ----
    function t(key, fb) {
        if (typeof I18n !== 'undefined' && I18n.t) {
            try { var v = I18n.t(key, fb); return v || fb; } catch (e) {}
        }
        return fb;
    }

    function cssUrl() { return '/css/guide.css'; }

    // 当前语言：优先 I18n（语言包异步加载，首帧可能仍是默认值）
    function currentLang() {
        return (typeof I18n !== 'undefined' && I18n.currentLanguage) ? I18n.currentLanguage : (lsGet('language') || 'zh-CN');
    }

    // 按当前语言重贴已建 DOM 的 UI 文案，规避 I18n 异步加载竞态
    function refreshLabels() {
        state.lang = currentLang();
        if (!dom.name) return;
        dom.name.textContent = t('guide_name', '向导精灵');
        dom.btnPrev.textContent = t('guide_prev', '← 上一步');
        dom.btnNext.textContent = t('guide_next', '下一步 →');
        dom.btnSkip.textContent = t('guide_skip', '跳过本页');
        if (dom.helpText) dom.helpText.textContent = t('guide_help', '导引');
    }

    function injectCss() {
        if (document.getElementById('fk-guide-css')) return;
        var l = document.createElement('link');
        l.id = 'fk-guide-css';
        l.rel = 'stylesheet';
        l.href = cssUrl();
        document.head.appendChild(l);
    }

    function resolvePageKey() {
        var b = document.body;
        if (b && b.getAttribute('data-guide-page')) return b.getAttribute('data-guide-page');
        var p = (location.pathname || '/').replace(/\/+$/, '');
        var seg = p.split('/').pop() || 'home';
        if (!seg || seg === '') seg = 'home';
        return seg;
    }

    // ---- localStorage 便捷 ----
    function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
    function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }

    function isGloballyEnabled() {
        // 默认开启，除非显式关闭
        return lsGet('guideEnabled') !== 'false';
    }

    function pageSkipped() { return lsGet('guideSkipped_' + state.pageKey) === 'true'; }
    function pageSeen() { return lsGet('guideSeen_' + state.pageKey) === 'true'; }

    // ---- 构建 DOM ----
    function ensureDom() {
        if (dom.overlay) return;

        dom.overlay = el('div', 'fk-guide-overlay');
        dom.highlight = el('div', 'fk-guide-highlight');
        dom.overlay.appendChild(dom.highlight);

        dom.wrap = el('div', 'fk-guide-dialog-wrap');
        dom.sprite = document.createElement('img');
        dom.sprite.className = 'fk-guide-sprite';
        dom.sprite.src = SPRITE_BASE + 'idle.png';
        dom.sprite.alt = '';
        dom.dialog = el('div', 'fk-guide-dialog');
        dom.name = el('div', 'fk-guide-name', t('guide_name', '向导精灵'));
        dom.progress = el('div', 'fk-guide-progress', '');
        dom.text = el('div', 'fk-guide-text');
        dom.continue = el('div', 'fk-guide-continue', '▼');
        dom.controls = el('div', 'fk-guide-controls');
        dom.btnPrev = mkBtn(t('guide_prev', '← 上一步'), 'ghost');
        dom.btnNext = mkBtn(t('guide_next', '下一步 →'), 'primary');
        dom.btnSkip = mkBtn(t('guide_skip', '跳过本页'), 'ghost');
        dom.controls.appendChild(dom.btnPrev);
        dom.controls.appendChild(dom.btnSkip);
        dom.controls.appendChild(dom.btnNext);
        dom.dialog.appendChild(dom.name);
        dom.dialog.appendChild(dom.progress);
        dom.dialog.appendChild(dom.text);
        dom.dialog.appendChild(dom.continue);
        dom.dialog.appendChild(dom.controls);
        dom.wrap.appendChild(dom.sprite);
        dom.wrap.appendChild(dom.dialog);

        dom.help = el('div', 'fk-guide-help');
        var himg = document.createElement('img');
        himg.src = SPRITE_BASE + 'idle.png';
        dom.help.appendChild(himg);
        dom.helpText = document.createTextNode(t('guide_help', '导引'));
        dom.help.appendChild(dom.helpText);

        document.body.appendChild(dom.overlay);
        document.body.appendChild(dom.wrap);
        document.body.appendChild(dom.help);

        bindEvents();
    }

    function el(tag, cls, txt) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (txt != null) e.textContent = txt;
        return e;
    }
    function mkBtn(txt, kind) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'fk-guide-btn' + (kind ? ' ' + kind : '');
        b.textContent = txt;
        return b;
    }

    function bindEvents() {
        dom.btnNext.addEventListener('click', function (e) { e.stopPropagation(); onNext(); });
        dom.btnPrev.addEventListener('click', function (e) { e.stopPropagation(); onPrev(); });
        dom.btnSkip.addEventListener('click', function (e) { e.stopPropagation(); skipPage(); });
        dom.overlay.addEventListener('click', function () { onNext(); });
        dom.text.addEventListener('click', function (e) { e.stopPropagation(); onNext(); });
        dom.help.addEventListener('click', function () { openGuide(); });
        document.addEventListener('keydown', onKey, true);
    }

    function onKey(e) {
        if (!dom.overlay.classList.contains('active')) return;
        if (e.key === 'Escape') { e.preventDefault(); closeGuide(); }
        else if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onNext(); }
        else if (e.key === 'ArrowLeft') { e.preventDefault(); onPrev(); }
        else if (e.key === 'ArrowRight') { e.preventDefault(); onNext(); }
    }

    // ---- 加载导引内容 ----
    function loadGuide() {
        return fetch(GUIDE_BASE + state.pageKey + '.json', { cache: 'no-cache' })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                if (data && Array.isArray(data.steps)) { state.guide = data; return data; }
                return null;
            })
            .catch(function () { return null; });
    }

    function dialoguesOf(step) {
        var d = step.dialogue;
        if (!d) return [''];
        if (Array.isArray(d)) return d;
        // 对象形式：按语言取，回退 zh-CN
        return d[state.lang] || d['zh-CN'] || d['en-US'] || Object.values(d)[0] || [''];
    }

    // ---- 打开 / 关闭 ----
    function openGuide() {
        if (!state.guide || !state.guide.steps.length) return;
        ensureDom();
        refreshLabels();
        dom.help.classList.remove('active');
        dom.overlay.classList.add('active');
        dom.wrap.classList.add('active');
        state.stepIdx = -1;
        nextStep();
    }

    function closeGuide() {
        dom.overlay.classList.remove('active');
        dom.wrap.classList.remove('active');
        stopType();
        if (state.guide) showHelp();
    }

    function showHelp() {
        ensureDom();
        refreshLabels();
        dom.help.classList.add('active');
    }

    function skipPage() {
        lsSet('guideSkipped_' + state.pageKey, 'true');
        // 关闭当前播放，但保留右下角"导引"入口，方便随时重看
        closeGuide();
    }

    // ---- 步进逻辑 ----
    function nextStep() {
        var steps = state.guide.steps;
        var guard = 0;
        while (state.stepIdx < steps.length - 1 && guard++ < steps.length) {
            state.stepIdx++;
            var step = steps[state.stepIdx];
            var target = findTarget(step);
            if (step.selector && !target) continue; // 选择器失效 → 静默跳过该步
            renderStep(step, target);
            return;
        }
        // 末尾或无有效步骤
        if (state.stepIdx >= steps.length - 1) {
            var last = steps[steps.length - 1];
            if (state.stepIdx === steps.length - 1) { finishGuide(); return; }
        }
        finishGuide();
    }

    function findTarget(step) {
        if (!step.selector) return null;
        var e;
        try { e = document.querySelector(step.selector); } catch (err) { return null; }
        if (!e) return null;
        // 不可见（隐藏/零尺寸）视为未找到，让该步优雅跳过
        var r = e.getBoundingClientRect();
        if (r.width === 0 && r.height === 0) return null;
        if (e.offsetParent === null && getComputedStyle(e).position !== 'fixed') return null;
        return e;
    }

    function renderStep(step, target) {
        state.sentIdx = 0;
        positionHighlight(target);
        updateSprite(step, target);
        updateProgress();
        typeSentence(step);
    }

    function positionHighlight(target) {
        if (!target) {
            dom.highlight.classList.add('no-target');
            return;
        }
        dom.highlight.classList.remove('no-target');
        var r = target.getBoundingClientRect();
        var pad = 6;
        dom.highlight.style.left = (r.left - pad) + 'px';
        dom.highlight.style.top = (r.top - pad) + 'px';
        dom.highlight.style.width = (r.width + pad * 2) + 'px';
        dom.highlight.style.height = (r.height + pad * 2) + 'px';
        // 目标在视口外则滚入可见
        if (r.top < 60 || r.bottom > window.innerHeight) {
            target.scrollIntoView({ behavior: 'auto', block: 'center' });
            var r2 = target.getBoundingClientRect();
            dom.highlight.style.top = (r2.top - pad) + 'px';
            dom.highlight.style.left = (r2.left - pad) + 'px';
        }
    }

    function updateSprite(step, target) {
        var pose = step.pose || (target ? 'point' : 'idle');
        dom.sprite.src = SPRITE_BASE + pose + '.png';
        // 目标在右 → 精灵默认朝右(point 素材朝右)；目标在左 → 镜像
        if (target) {
            var r = target.getBoundingClientRect();
            var cx = r.left + r.width / 2;
            dom.sprite.classList.toggle('flip', cx < window.innerWidth / 2);
        } else {
            dom.sprite.classList.remove('flip');
        }
    }

    function updateProgress() {
        var total = state.guide.steps.length;
        dom.progress.textContent = (state.stepIdx + 1) + ' / ' + total;
    }

    // ---- 打字机 ----
    function typeSentence(step) {
        stopType();
        var arr = dialoguesOf(step);
        state.fullText = arr[state.sentIdx] || '';
        dom.text.textContent = '';
        dom.continue.classList.remove('show');
        state.typing = true;
        var i = 0;
        var txt = state.fullText;
        state.typeTimer = setInterval(function () {
            if (i >= txt.length) { finishSentence(); return; }
            dom.text.textContent = txt.slice(0, ++i);
        }, TYPE_MS);
    }

    function finishSentence() {
        stopType();
        dom.text.textContent = state.fullText;
        state.typing = false;
        dom.continue.classList.add('show');
    }

    function stopType() {
        if (state.typeTimer) { clearInterval(state.typeTimer); state.typeTimer = null; }
    }

    // ---- 导航 ----
    function onNext() {
        if (!dom.overlay.classList.contains('active')) return;
        if (state.typing) { finishSentence(); return; }
        var step = state.guide.steps[state.stepIdx];
        var arr = dialoguesOf(step);
        if (state.sentIdx < arr.length - 1) {
            state.sentIdx++;
            typeSentence(step);
        } else {
            nextStep();
        }
    }

    function onPrev() {
        if (state.typing) { finishSentence(); return; }
        if (state.sentIdx > 0) {
            state.sentIdx--;
            typeSentence(state.guide.steps[state.stepIdx]);
            return;
        }
        // 回到上一步（可能需跳过无效步骤，简化：直接减一并渲染）
        if (state.stepIdx > 0) {
            state.stepIdx--;
            var step = state.guide.steps[state.stepIdx];
            renderStep(step, findTarget(step));
        }
    }

    function finishGuide() {
        lsSet('guideSeen_' + state.pageKey, 'true');
        closeGuide();
    }

    // ---- 首次开启询问 ----
    function showFirstRunPrompt() {
        ensureDom();
        var modal = el('div', 'fk-guide-first active');
        var wrap = el('div', 'fk-guide-dialog-wrap active');
        wrap.style.position = 'static';
        wrap.style.transform = 'none';
        wrap.style.maxWidth = '640px';
        var sprite = document.createElement('img');
        sprite.className = 'fk-guide-sprite';
        sprite.src = SPRITE_BASE + 'idle.png';
        var dialog = el('div', 'fk-guide-dialog');
        dialog.appendChild(el('div', 'fk-guide-name', t('guide_name', '向导精灵')));
        var msg = el('div', 'fk-guide-text',
            t('guide_firstrun_msg', '初次见面！我是你的向导精灵。要不要让我带你逐一熟悉各个页面的功能和按钮？开启后，随时可点页面右下角的「导引」按钮重看。'));
        dialog.appendChild(msg);
        var controls = el('div', 'fk-guide-controls');
        var noBtn = mkBtn(t('guide_firstrun_no', '暂不需要'), 'ghost');
        var yesBtn = mkBtn(t('guide_firstrun_yes', '开启导引'), 'primary');
        controls.appendChild(noBtn);
        controls.appendChild(yesBtn);
        dialog.appendChild(controls);
        wrap.appendChild(sprite);
        wrap.appendChild(dialog);
        modal.appendChild(wrap);
        document.body.appendChild(modal);

        function decide(enabled) {
            lsSet('guideEnabled', enabled ? 'true' : 'false');
            lsSet('guidePrompted', 'true');
            saveSetting({ guide_enabled: enabled ? 'true' : 'false', guide_prompted: 'true' });
            modal.remove();
            if (enabled) openGuide();
        }
        yesBtn.addEventListener('click', function () { decide(true); });
        noBtn.addEventListener('click', function () { decide(false); });
    }

    function saveSetting(map) {
        try {
            fetch('/api/settings/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams(map)
            }).catch(function () {});
        } catch (e) {}
    }

    // ---- 启动 ----
    function boot() {
        injectCss();
        state.pageKey = resolvePageKey();
        state.lang = currentLang();

        // 先读后端全局开关，再决定是否继续
        fetch('/api/settings/get').then(function (r) { return r.json(); }).then(function (s) {
            s = s || {};
            if (s.guide_enabled === 'false') lsSet('guideEnabled', 'false');
            else if (s.guide_enabled === 'true') lsSet('guideEnabled', 'true');
            if (s.guide_prompted === 'true') lsSet('guidePrompted', 'true');

            if (!isGloballyEnabled()) return; // 用户已关闭，不打扰

            // 首次询问
            if (lsGet('guidePrompted') !== 'true') {
                // 加载本页导引备用；询问后再开
                loadGuide().then(function () { showFirstRunPrompt(); });
                return;
            }

            loadGuide().then(function (g) {
                if (!g) return; // 该页无导引
                showHelp();
                // 首次进入且未被跳过 → 自动播放；否则仅保留右下角手动入口
                if (!pageSkipped() && !pageSeen()) openGuide();
            });
        }).catch(function () {
            // 读设置失败：降级为纯 localStorage
            if (!isGloballyEnabled()) return;
            loadGuide().then(function (g) { if (g && !pageSkipped()) showHelp(); });
        });
    }

    window.FkGuide = {
        open: function () { openGuide(); },
        reload: boot
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
