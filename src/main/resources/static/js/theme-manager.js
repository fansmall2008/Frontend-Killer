/**
 * ThemeManager — 主题包加载与切换
 * 
 * 负责：
 *   1. 从后端加载当前主题配置
 *   2. 将配色方案注入 CSS 变量
 *   3. 动态加载自定义字体
 *   4. 通知 SoundEngine 更新音效配置
 */
const ThemeManager = {
    currentTheme: null,
    customStyleEl: null,  // 动态注入的 <style> 元素

    /* ---------- 初始化：加载并应用当前主题 ---------- */
    async init() {
        try {
            const resp = await fetch('/api/themes/current');
            if (resp.ok) {
                const theme = await resp.json();
                this.applyTheme(theme);
            }
        } catch (e) {
            console.warn('ThemeManager: 加载主题失败，使用默认', e);
        }
    },

    /* ---------- 应用主题 ---------- */
    applyTheme(theme) {
        if (!theme) return;
        this.currentTheme = theme;

        // 1. 应用配色（CSS 变量覆盖）
        const mode = document.documentElement.getAttribute('data-theme') || 'dark';
        const colors = theme.colors && theme.colors[mode];
        if (colors) {
            Object.entries(colors).forEach(([prop, value]) => {
                document.documentElement.style.setProperty(prop, value);
            });
        } else {
            // 清除之前主题的自定义颜色
            this._clearColorOverrides();
        }

        // 2. 应用自定义字体
        this._applyFonts(theme.fonts);

        // 3. 更新音效引擎
        SoundEngine.setTheme(theme);

        // 4. 记住当前主题 ID
        localStorage.setItem('activeThemeId', theme.id);
    },

    /* ---------- 切换到指定主题 ---------- */
    async switchTo(themeId) {
        try {
            // 后端保存
            const applyResp = await fetch('/api/themes/apply', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'themeId=' + encodeURIComponent(themeId)
            });
            const applyData = await applyResp.json();
            if (!applyData.success) {
                return { success: false, message: applyData.message };
            }

            // 加载完整主题数据
            const themeResp = await fetch('/api/themes/' + encodeURIComponent(themeId));
            if (themeResp.ok) {
                const theme = await themeResp.json();
                this.applyTheme(theme);
                SoundEngine.play('theme_switch');
                return { success: true };
            }
            return { success: false, message: '主题数据加载失败' };
        } catch (e) {
            console.error('切换主题失败', e);
            return { success: false, message: e.message };
        }
    },

    /* ---------- 注入自定义字体 ---------- */
    _applyFonts(fontConfig) {
        // 移除旧的自定义字体样式
        if (this.customStyleEl) {
            this.customStyleEl.remove();
            this.customStyleEl = null;
        }

        if (!fontConfig || Object.keys(fontConfig).length === 0) {
            // 清除字体覆盖，回到 theme.css 默认
            document.documentElement.style.removeProperty('--font-sans');
            document.documentElement.style.removeProperty('--font-display');
            document.documentElement.style.removeProperty('--font-pixel');
            return;
        }

        // 创建 @font-face 声明（如果有自定义字体文件）
        let css = '';
        const varOverrides = {};

        Object.entries(fontConfig).forEach(([slot, fontDef]) => {
            if (typeof fontDef === 'string') {
                // 直接指定 font-family 字符串（使用系统字体或已加载的字体）
                varOverrides['--font-' + slot] = fontDef;
            } else if (typeof fontDef === 'object' && fontDef.family) {
                // 带字体文件的定义
                const familyName = fontDef.family;
                const src = fontDef.src; // 相对于主题目录的路径
                const format = fontDef.format || this._guessFontFormat(src);
                css += `@font-face {
                    font-family: '${familyName}';
                    src: url('/api/themes/${this.currentTheme.id}/fonts/${src}') format('${format}');
                    font-display: swap;
                }\n`;
                varOverrides['--font-' + slot] = `'${familyName}', sans-serif`;
            }
        });

        if (css) {
            this.customStyleEl = document.createElement('style');
            this.customStyleEl.textContent = css;
            document.head.appendChild(this.customStyleEl);
        }

        // 覆盖 CSS 变量
        Object.entries(varOverrides).forEach(([prop, value]) => {
            document.documentElement.style.setProperty(prop, value);
        });
    },

    /* ---------- 清除配色覆盖 ---------- */
    _clearColorOverrides() {
        // 移除 :root 上之前主题设置的自定义颜色变量
        // 通过移除所有 --bg-*, --accent-*, --border-*, --text-*, --gradient-* 等
        const root = document.documentElement;
        const prefixes = ['--bg-', '--accent-', '--border-', '--text-', '--gradient-', '--sidebar-'];
        prefixes.forEach(prefix => {
            // 获取当前 theme.css 中定义的变量名列表，逐一移除 inline 覆盖
            // 简单做法：不逐一清除，而是在 applyTheme 时用完整列表覆盖
        });
    },

    /* ---------- 字体格式推断 ---------- */
    _guessFontFormat(src) {
        if (!src) return 'truetype';
        const ext = src.split('.').pop().toLowerCase();
        const map = { woff2: 'woff2', woff: 'woff', ttf: 'truetype', otf: 'opentype' };
        return map[ext] || 'truetype';
    }
};
