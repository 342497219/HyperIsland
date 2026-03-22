import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

const kPrefResumeNotification = 'pref_resume_notification';
const kPrefUseHookAppIcon     = 'pref_use_hook_app_icon';
const kPrefRoundIcon          = 'pref_round_icon';
const kPrefMarqueeFeature     = 'pref_marquee_feature';
const kPrefMarqueeSpeed       = 'pref_marquee_speed';
const kPrefWrapLongText       = 'pref_wrap_long_text';
const kPrefUnlockAllFocus     = 'pref_unlock_all_focus';
const kPrefUnlockFocusAuth    = 'pref_unlock_focus_auth';
const kPrefThemeMode             = 'pref_theme_mode';
const kPrefLocale                = 'pref_locale';
const kPrefCheckUpdateOnLaunch   = 'pref_check_update_on_launch';
class SettingsController extends ChangeNotifier {
  static final SettingsController instance = SettingsController._();

  SettingsController._() {
    _load();
  }

  bool resumeNotification = true;
  bool useHookAppIcon = true;
  bool roundIcon = true;
  bool marqueeFeature = false;
  int marqueeSpeed = 100;
  bool wrapLongText = false;
  bool unlockAllFocus = false;
  bool unlockFocusAuth = false;
  bool checkUpdateOnLaunch = true;
  ThemeMode themeMode = ThemeMode.system;
  Locale? locale; // null = follow system
  bool loading = true;

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    resumeNotification    = prefs.getBool(kPrefResumeNotification) ?? true;
    useHookAppIcon        = prefs.getBool(kPrefUseHookAppIcon) ?? true;
    roundIcon             = prefs.getBool(kPrefRoundIcon) ?? true;
    marqueeFeature        = prefs.getBool(kPrefMarqueeFeature) ?? false;
    marqueeSpeed          = (prefs.getInt(kPrefMarqueeSpeed) ?? 100).clamp(20, 500);
    wrapLongText          = prefs.getBool(kPrefWrapLongText) ?? false;
    unlockAllFocus        = prefs.getBool(kPrefUnlockAllFocus) ?? false;
    unlockFocusAuth       = prefs.getBool(kPrefUnlockFocusAuth) ?? false;
    checkUpdateOnLaunch   = prefs.getBool(kPrefCheckUpdateOnLaunch) ?? true;
    themeMode = switch (prefs.getString(kPrefThemeMode)) {
      'light'  => ThemeMode.light,
      'dark'   => ThemeMode.dark,
      _        => ThemeMode.system,
    };
    final localeStr = prefs.getString(kPrefLocale);
    locale = localeStr != null ? Locale(localeStr) : null;
    loading = false;
    notifyListeners();
  }

  Future<void> setResumeNotification(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefResumeNotification, value);
    resumeNotification = value;
    notifyListeners();
  }

  Future<void> setUseHookAppIcon(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefUseHookAppIcon, value);
    useHookAppIcon = value;
    notifyListeners();
  }

  Future<void> setRoundIcon(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefRoundIcon, value);
    roundIcon = value;
    notifyListeners();
  }

  Future<void> setMarqueeFeature(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefMarqueeFeature, value);
    marqueeFeature = value;
    notifyListeners();
  }

  Future<void> setMarqueeSpeed(int value) async {
    final clamped = value.clamp(20, 500);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(kPrefMarqueeSpeed, clamped);
    marqueeSpeed = clamped;
    notifyListeners();
  }

  Future<void> setWrapLongText(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefWrapLongText, value);
    wrapLongText = value;
    notifyListeners();
  }

  Future<void> setUnlockAllFocus(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefUnlockAllFocus, value);
    unlockAllFocus = value;
    notifyListeners();
  }

  Future<void> setUnlockFocusAuth(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefUnlockFocusAuth, value);
    unlockFocusAuth = value;
    notifyListeners();
  }

  Future<void> setCheckUpdateOnLaunch(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefCheckUpdateOnLaunch, value);
    checkUpdateOnLaunch = value;
    notifyListeners();
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    final prefs = await SharedPreferences.getInstance();
    final str = switch (mode) {
      ThemeMode.light  => 'light',
      ThemeMode.dark   => 'dark',
      ThemeMode.system => 'system',
    };
    await prefs.setString(kPrefThemeMode, str);
    themeMode = mode;
    notifyListeners();
  }

  Future<void> setLocale(Locale? loc) async {
    final prefs = await SharedPreferences.getInstance();
    if (loc == null) {
      await prefs.remove(kPrefLocale);
    } else {
      await prefs.setString(kPrefLocale, loc.languageCode);
    }
    locale = loc;
    notifyListeners();
  }
}
