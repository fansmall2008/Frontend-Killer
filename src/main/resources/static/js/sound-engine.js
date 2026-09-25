/**
 * SoundEngine — 主题音效引擎
 * 
 * 支持两种音效来源：
 *   1. Web Audio API 合成（默认，无需音频文件）
 *   2. 文件加载（自定义主题可放置 mp3/wav/ogg 到主题目录）
 * 
 * 用法：
 *   SoundEngine.play('success');
 *   SoundEngine.setVolume(0.5);
 *   SoundEngine.setEnabled(false);
 */
const SoundEngine = {
    ctx: null,
    enabled: true,
    masterVolume: 0.5,
    soundConfig: {},   // 当前主题的音效定义
    themeId: 'default',
    audioCache: {},    // 已加载的 Audio 对象缓存
    perEventEnabled: null, // 每个事件的独立开关 { click: true, success: true, ... }

    /* ---------- 初始化 ---------- */
    init(themeData) {
        try {
            this.ctx = new (window.AudioContext || window.webkitAudioContext)();
        } catch (e) {
            console.warn('SoundEngine: Web Audio API 不可用');
            return;
        }
        if (themeData) {
            this.themeId = themeData.id || 'default';
            this.soundConfig = themeData.sounds || {};
        }
        // 从 localStorage 读取每个事件的独立开关
        try {
            const saved = localStorage.getItem('soundEventSettings');
            this.perEventEnabled = saved ? JSON.parse(saved) : null;
        } catch (e) {
            this.perEventEnabled = null;
        }
        // 从 localStorage 读取全局设置
        const globalEnabled = localStorage.getItem('soundEnabled');
        if (globalEnabled !== null) this.enabled = globalEnabled !== 'false';
        const globalVol = localStorage.getItem('soundVolume');
        if (globalVol !== null) this.masterVolume = parseFloat(globalVol);
    },

    /* ---------- 更新主题音效配置 ---------- */
    setTheme(themeData) {
        if (!themeData) return;
        this.themeId = themeData.id || 'default';
        this.soundConfig = themeData.sounds || {};
        this.audioCache = {};
    },

    /* ---------- 播放音效 ---------- */
    play(eventName) {
        if (!this.enabled || !this.ctx) return;

        // 检查该事件是否被单独禁用
        if (this.perEventEnabled && this.perEventEnabled[eventName] === false) return;

        const config = this.soundConfig[eventName];
        if (!config) return;

        // 恢复被挂起的 AudioContext（浏览器自动播放策略）
        if (this.ctx.state === 'suspended') {
            this.ctx.resume();
        }

        // 文件音效优先
        if (config.type === 'file' && config.src) {
            this._playFile(config);
            return;
        }

        // 合成音效
        if (config.type === 'synth') {
            this._playSynth(config);
        }
    },

    /* ---------- 合成音效 ---------- */
    _playSynth(config) {
        if (!this.ctx) return;
        const now = this.ctx.currentTime;
        const duration = config.duration || 0.15;
        const volume = (config.volume || 0.3) * this.masterVolume;

        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();

        osc.type = config.wave || 'sine';
        osc.frequency.setValueAtTime(config.freq || 440, now);

        // 频率滑动（freq → freqEnd）
        if (config.freqEnd) {
            osc.frequency.exponentialRampToValueAtTime(
                Math.max(config.freqEnd, 20), now + duration
            );
        }

        // ADSR 包络
        gain.gain.setValueAtTime(volume, now);
        gain.gain.setValueAtTime(volume, now + duration * 0.7);
        gain.gain.exponentialRampToValueAtTime(0.001, now + duration);

        osc.connect(gain);
        gain.connect(this.ctx.destination);
        osc.start(now);
        osc.stop(now + duration + 0.05);
    },

    /* ---------- 文件音效 ---------- */
    _playFile(config) {
        let audio = this.audioCache[config.src];
        if (!audio) {
            audio = new Audio(config.src);
            audio.preload = 'auto';
            this.audioCache[config.src] = audio;
        }
        audio.volume = (config.volume || 0.5) * this.masterVolume;
        audio.currentTime = 0;
        audio.play().catch(() => {});
    },

    /* ---------- 预加载文件音效 ---------- */
    preloadFiles() {
        if (!this.soundConfig) return;
        Object.entries(this.soundConfig).forEach(([name, config]) => {
            if (config.type === 'file' && config.src && !this.audioCache[config.src]) {
                const audio = new Audio();
                audio.preload = 'auto';
                audio.src = config.src;
                this.audioCache[config.src] = audio;
            }
        });
    },

    /* ---------- 全局开关 ---------- */
    setEnabled(enabled) {
        this.enabled = enabled;
        localStorage.setItem('soundEnabled', String(enabled));
    },

    /* ---------- 主音量 ---------- */
    setVolume(vol) {
        this.masterVolume = Math.max(0, Math.min(1, vol));
        localStorage.setItem('soundVolume', String(this.masterVolume));
    },

    /* ---------- 设置单个事件的开关 ---------- */
    setEventEnabled(eventName, enabled) {
        if (!this.perEventEnabled) this.perEventEnabled = {};
        this.perEventEnabled[eventName] = enabled;
        localStorage.setItem('soundEventSettings', JSON.stringify(this.perEventEnabled));
    },

    /* ---------- 获取单个事件的开关状态 ---------- */
    isEventEnabled(eventName) {
        if (!this.perEventEnabled) return true;
        return this.perEventEnabled[eventName] !== false;
    },

    /* ---------- 试听指定音效 ---------- */
    preview(eventName) {
        this.play(eventName);
    }
};

// 首次交互时恢复 AudioContext（浏览器策略要求）
document.addEventListener('click', function _resumeAudio() {
    if (SoundEngine.ctx && SoundEngine.ctx.state === 'suspended') {
        SoundEngine.ctx.resume();
    }
    document.removeEventListener('click', _resumeAudio);
}, { once: true });
