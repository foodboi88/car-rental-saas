import { TestBed } from '@angular/core/testing';

import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  const root = document.documentElement;

  beforeEach(() => {
    localStorage.clear();
    delete root.dataset['theme'];
  });

  afterEach(() => {
    vi.restoreAllMocks();
    // jsdom không có matchMedia: xóa bản giả đã gắn trong test.
    delete (window as { matchMedia?: unknown }).matchMedia;
  });

  function create(): ThemeService {
    const service = TestBed.inject(ThemeService);
    TestBed.tick();
    return service;
  }

  function systemPrefersDark(prefersDark: boolean): void {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: () => ({ matches: prefersDark }) as MediaQueryList,
    });
  }

  it('should_follow_the_system_setting_when_nothing_is_saved', () => {
    systemPrefersDark(true);

    expect(create().theme()).toBe('dark');
  });

  it('should_use_light_when_the_system_prefers_light_and_nothing_is_saved', () => {
    systemPrefersDark(false);

    expect(create().theme()).toBe('light');
  });

  it('should_prefer_the_saved_choice_over_the_system_setting', () => {
    systemPrefersDark(true);
    localStorage.setItem(THEME_STORAGE_KEY, 'light');

    expect(create().theme()).toBe('light');
  });

  it('should_ignore_an_invalid_saved_value', () => {
    systemPrefersDark(false);
    localStorage.setItem(THEME_STORAGE_KEY, 'purple');

    expect(create().theme()).toBe('light');
  });

  it('should_write_the_theme_to_the_document_element', () => {
    systemPrefersDark(false);
    create();

    expect(root.dataset['theme']).toBe('light');
  });

  it('should_toggle_apply_and_remember_the_choice', () => {
    systemPrefersDark(false);
    const service = create();

    service.toggle();
    TestBed.tick();

    expect(service.isDark()).toBe(true);
    expect(root.dataset['theme']).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('should_still_switch_when_storage_is_blocked', () => {
    systemPrefersDark(false);
    const service = create();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    service.toggle();
    TestBed.tick();

    expect(root.dataset['theme']).toBe('dark');
  });
});
