import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import '../controllers/config_io_controller.dart';
import '../controllers/settings_controller.dart';
import '../controllers/update_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/section_label.dart';
import 'ai_config_page.dart';
import 'blacklist_page.dart';
import 'whitelist_page.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final _ctrl = SettingsController.instance;
  bool _checkingUpdate = false;

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void initState() {
    super.initState();
    _ctrl.addListener(_onChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  Future<void> _onResumeNotificationChanged(bool value) async {
    await _ctrl.setResumeNotification(value);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.restartScopeApp),
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  Future<void> _onUseHookAppIconChanged(bool value) async {
    await _ctrl.setUseHookAppIcon(value);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.restartScopeApp),
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  Future<void> _onRoundIconChanged(bool value) async {
    await _ctrl.setRoundIcon(value);
  }

  void _onMarqueeSpeedChanged(double value) {
    _ctrl.setMarqueeSpeed(value.round());
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 3)),
    );
  }

  String _localizeConfigIOError(AppLocalizations l10n, ConfigIOError error) {
    return switch (error) {
      ConfigIOError.invalidFormat => l10n.errorInvalidFormat,
      ConfigIOError.noStorageDirectory => l10n.errorNoStorageDir,
      ConfigIOError.noFileSelected => l10n.errorNoFileSelected,
      ConfigIOError.noFilePath => l10n.errorNoFilePath,
      ConfigIOError.emptyClipboard => l10n.errorEmptyClipboard,
    };
  }

  Future<void> _exportToFile() async {
    final l10n = AppLocalizations.of(context)!;
    try {
      final path = await ConfigIOController.exportToFile();
      _showSnack(l10n.exportedTo(path));
    } on ConfigIOException catch (e) {
      _showSnack(l10n.exportFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.exportFailed(e.toString()));
    }
  }

  Future<void> _exportToClipboard() async {
    final l10n = AppLocalizations.of(context)!;
    try {
      await ConfigIOController.exportToClipboard();
      _showSnack(l10n.configCopied);
    } on ConfigIOException catch (e) {
      _showSnack(l10n.exportFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.exportFailed(e.toString()));
    }
  }

  Future<void> _importFromFile() async {
    final l10n = AppLocalizations.of(context)!;
    try {
      final count = await ConfigIOController.importFromFile();
      _showSnack(l10n.importSuccess(count));
    } on ConfigIOException catch (e) {
      _showSnack(l10n.importFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.importFailed(e.toString()));
    }
  }

  Future<void> _importFromClipboard() async {
    final l10n = AppLocalizations.of(context)!;
    try {
      final count = await ConfigIOController.importFromClipboard();
      _showSnack(l10n.importSuccess(count));
    } on ConfigIOException catch (e) {
      _showSnack(l10n.importFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.importFailed(e.toString()));
    }
  }

  Future<void> _doCheckUpdate() async {
    setState(() => _checkingUpdate = true);
    try {
      final info = await PackageInfo.fromPlatform();
      if (mounted) {
        await UpdateController.checkAndShow(
          context,
          info.version,
          showUpToDate: true,
        );
      }
    } finally {
      if (mounted) setState(() => _checkingUpdate = false);
    }
  }

  String _themeModeLabel(AppLocalizations l10n) => switch (_ctrl.themeMode) {
    ThemeMode.light => l10n.themeModeLight,
    ThemeMode.dark => l10n.themeModeDark,
    ThemeMode.system => l10n.themeModeSystem,
  };

  String _localeLabel(AppLocalizations l10n) {
    if (_ctrl.locale == null) return l10n.languageAuto;
    return switch (_ctrl.locale!.languageCode) {
      'zh' => l10n.languageZh,
      'en' => l10n.languageEn,
      'ja' => l10n.languageJa,
      'tr' => l10n.languageTr,
      _ => _ctrl.locale!.languageCode,
    };
  }

  Future<void> _showThemeModeDialog(AppLocalizations l10n) async {
    final result = await showDialog<ThemeMode>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(l10n.themeModeTitle),
        children: [
          _RadioOption(l10n.themeModeSystem, ThemeMode.system, _ctrl.themeMode),
          _RadioOption(l10n.themeModeLight, ThemeMode.light, _ctrl.themeMode),
          _RadioOption(l10n.themeModeDark, ThemeMode.dark, _ctrl.themeMode),
        ],
      ),
    );
    if (result != null) _ctrl.setThemeMode(result);
  }

  Future<void> _showLanguageDialog(AppLocalizations l10n) async {
    final result = await showDialog<Locale?>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(l10n.languageTitle),
        children: [
          _RadioOption<Locale?>(l10n.languageAuto, null, _ctrl.locale),
          _RadioOption<Locale?>(
            l10n.languageZh,
            const Locale('zh'),
            _ctrl.locale,
          ),
          _RadioOption<Locale?>(
            l10n.languageEn,
            const Locale('en'),
            _ctrl.locale,
          ),
          _RadioOption<Locale?>(
            l10n.languageJa,
            const Locale('ja'),
            _ctrl.locale,
          ),
          _RadioOption<Locale?>(
            l10n.languageTr,
            const Locale('tr'),
            _ctrl.locale,
          ),
        ],
      ),
    );
    if (result != _ctrl.locale) _ctrl.setLocale(result);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: cs.surface,
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(
            title: Text(l10n.navSettings),
            backgroundColor: cs.surface,
            centerTitle: false,
          ),
          if (_ctrl.loading)
            const SliverFillRemaining(
              child: Center(child: CircularProgressIndicator()),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              sliver: SliverList(
                delegate: SliverChildListDelegate([
                  SectionLabel(l10n.aiConfigSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 4,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      leading: const Icon(Icons.psychology_outlined),
                      title: Text(l10n.aiConfigTitle),
                      subtitle: Text(
                        _ctrl.aiEnabled
                            ? l10n.aiConfigSubtitleEnabled
                            : l10n.aiConfigSubtitleDisabled,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const AiConfigPage(),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.navBlacklist),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              top: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.block),
                          title: Text(l10n.navBlacklist),
                          subtitle: Text(l10n.navBlacklistSubtitle),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => const BlacklistPage(),
                              ),
                            );
                          },
                        ),
                        const Divider(height: 1, indent: 56),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.check_circle_outline),
                          title: Text(l10n.navWhitelist),
                          subtitle: Text(l10n.navWhitelistSubtitle),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => const WhitelistPage(),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.generalSettings),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.vignette_outlined),
                          title: const Text("常驻岛"),
                          subtitle: const Text("在状态栏持续显示药丸形状的灵动岛"),
                          value: _ctrl.persistentIsland,
                          onChanged: (value) async {
                            await _ctrl.setPersistentIsland(value);
                            const MethodChannel('io.github.hyperisland/methods')
                                .invokeMethod('updatePersistentIsland', value);
                          },
                        ),
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.notifications_active_outlined),
                          title: Text(l10n.resumeNotification),
                          subtitle: Text(l10n.resumeNotificationSubtitle),
                          value: _ctrl.resumeNotification,
                          onChanged: _onResumeNotificationChanged,
                        ),
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.app_registration),
                          title: Text(l10n.useHookAppIcon),
                          subtitle: Text(l10n.useHookAppIconSubtitle),
                          value: _ctrl.useHookAppIcon,
                          onChanged: _onUseHookAppIconChanged,
                        ),
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.rounded_corner),
                          title: Text(l10n.roundIcon),
                          subtitle: Text(l10n.roundIconSubtitle),
                          value: _ctrl.roundIcon,
                          onChanged: _onRoundIconChanged,
                        ),
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.text_fields),
                          title: Text(l10n.marqueeFeature),
                          subtitle: Text(l10n.marqueeFeatureSubtitle),
                          value: _ctrl.marqueeFeature,
                          onChanged: _ctrl.setMarqueeFeature,
                        ),
                        if (_ctrl.marqueeFeature) ...[
                          const Divider(height: 1, indent: 56),
                          Padding(
                            padding: const EdgeInsets.only(
                              left: 56,
                              right: 16,
                              top: 8,
                              bottom: 8,
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  l10n.marqueeSpeed(_ctrl.marqueeSpeed),
                                  style: Theme.of(context).textTheme.bodyMedium,
                                ),
                                Slider(
                                  value: _ctrl.marqueeSpeed.toDouble(),
                                  min: 20,
                                  max: 500,
                                  onChanged: _onMarqueeSpeedChanged,
                                ),
                              ],
                            ),
                          ),
                        ],
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.lock_open),
                          title: Text(l10n.unlockAllFocusTitle),
                          subtitle: Text(l10n.unlockAllFocusSubtitle),
                          value: _ctrl.unlockAllFocus,
                          onChanged: _ctrl.setUnlockAllFocus,
                        ),
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.security),
                          title: Text(l10n.unlockFocusAuthTitle),
                          subtitle: Text(l10n.unlockFocusAuthSubtitle),
                          value: _ctrl.unlockFocusAuth,
                          onChanged: _ctrl.setUnlockFocusAuth,
                        ),
                        const Divider(height: 1, indent: 56),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          secondary: const Icon(Icons.update),
                          title: Text(l10n.checkUpdateOnLaunchTitle),
                          subtitle: Text(l10n.checkUpdateOnLaunchSubtitle),
                          value: _ctrl.checkUpdateOnLaunch,
                          onChanged: _ctrl.setCheckUpdateOnLaunch,
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.defaultConfigSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.defaultFirstFloat),
                          subtitle: Text(l10n.defaultFirstFloatSubtitle),
                          value: _ctrl.defaultFirstFloat,
                          onChanged: _ctrl.setDefaultFirstFloat,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.defaultEnableFloat),
                          subtitle: Text(l10n.defaultEnableFloatSubtitle),
                          value: _ctrl.defaultEnableFloat,
                          onChanged: _ctrl.setDefaultEnableFloat,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.defaultMarquee),
                          subtitle: Text(l10n.defaultMarqueeSubtitle),
                          value: _ctrl.defaultMarquee,
                          onChanged: _ctrl.setDefaultMarquee,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.defaultFocusNotif),
                          subtitle: Text(l10n.defaultFocusNotifSubtitle),
                          value: _ctrl.defaultFocusNotif,
                          onChanged: _ctrl.setDefaultFocusNotif,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.defaultPreserveSmallIcon),
                          subtitle: Text(l10n.defaultPreserveSmallIconSubtitle),
                          value: _ctrl.defaultPreserveSmallIcon,
                          onChanged: _ctrl.setDefaultPreserveSmallIcon,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.appearanceSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.palette_outlined),
                          title: Text(l10n.themeModeTitle),
                          subtitle: Text(_themeModeLabel(l10n)),
                          onTap: () => _showThemeModeDialog(l10n),
                        ),
                        const Divider(height: 1, indent: 56),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.language),
                          title: Text(l10n.languageTitle),
                          subtitle: Text(_localeLabel(l10n)),
                          onTap: () => _showLanguageDialog(l10n),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.configIOSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.file_upload_outlined),
                          title: Text(l10n.exportToFile),
                          onTap: _exportToFile,
                        ),
                        const Divider(height: 1, indent: 56),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.content_copy_outlined),
                          title: Text(l10n.exportToClipboard),
                          onTap: _exportToClipboard,
                        ),
                        const Divider(height: 1, indent: 56),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.file_download_outlined),
                          title: Text(l10n.importFromFile),
                          onTap: _importFromFile,
                        ),
                        const Divider(height: 1, indent: 56),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.content_paste_outlined),
                          title: Text(l10n.importFromClipboard),
                          onTap: _importFromClipboard,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.aboutSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.info_outline),
                          title: Text(l10n.aboutTitle),
                          subtitle: FutureBuilder<PackageInfo>(
                            future: PackageInfo.fromPlatform(),
                            builder: (context, snapshot) {
                              if (!snapshot.hasData) return const Text('...');
                              return Text('v${snapshot.data!.version}');
                            },
                          ),
                          trailing: _checkingUpdate
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : null,
                          onTap: _doCheckUpdate,
                        ),
                        const Divider(height: 1, indent: 56),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.code),
                          title: const Text('GitHub'),
                          subtitle: const Text('1812z/HyperIsland'),
                          onTap: () => launchUrl(
                            Uri.parse('https://github.com/1812z/HyperIsland'),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 32),
                ]),
              ),
            ),
        ],
      ),
    );
  }
}

class _RadioOption<T> extends StatelessWidget {
  final String label;
  final T value;
  final T groupValue;

  const _RadioOption(this.label, this.value, this.groupValue);

  @override
  Widget build(BuildContext context) {
    return RadioListTile<T>(
      title: Text(label),
      value: value,
      groupValue: groupValue,
      onChanged: (val) => Navigator.pop(context, val),
    );
  }
}
