const I18n = {
  currentLanguage: 'zh-CN',
  translations: {},
  supportedLanguages: ['zh-CN', 'en-US', 'ja-JP'],

  async loadLanguage(lang) {
    try {
      const response = await fetch(`locales/${lang}.json`);
      if (!response.ok) {
        throw new Error(`Failed to load language file: ${lang}`);
      }
      this.translations = await response.json();
      this.currentLanguage = lang;
      localStorage.setItem('language', lang);
      document.documentElement.lang = lang;
      this.updatePage();
      return true;
    } catch (error) {
      console.error('Error loading language:', error);
      return false;
    }
  },

  getCurrentLanguage() {
    return localStorage.getItem('language') || 'zh-CN';
  },

  async setLanguage(lang) {
    if (!this.supportedLanguages.includes(lang)) {
      console.warn(`Language ${lang} is not supported`);
      return false;
    }
    return await this.loadLanguage(lang);
  },

  t(key, defaultValue = '') {
    const keys = key.split('.');
    let value = this.translations;
    
    for (const k of keys) {
      if (value && typeof value === 'object' && k in value) {
        value = value[k];
      } else {
        return defaultValue || key;
      }
    }
    
    return value || defaultValue || key;
  },

  updatePage() {
    document.querySelectorAll('[data-i18n]').forEach(element => {
      const key = element.getAttribute('data-i18n');
      const translation = this.t(key);
      if (translation && translation !== key) {
        element.textContent = translation;
      }
    });

    const titleKey = document.querySelector('[data-i18n="page_title"]')?.getAttribute('data-i18n');
    const subtitleKey = document.querySelector('[data-i18n="page_subtitle"]')?.getAttribute('data-i18n');
    
    if (titleKey && subtitleKey) {
      document.title = `${this.t(titleKey)} - ${this.t(subtitleKey)}`;
    }
  },

  async init() {
    const savedLang = this.getCurrentLanguage();
    await this.loadLanguage(savedLang);
  }
};

document.addEventListener('DOMContentLoaded', () => {
  I18n.init();
});