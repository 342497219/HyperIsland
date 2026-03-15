import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _channel = MethodChannel('com.example.hyperisland/test');
const kPrefGenericWhitelist = 'pref_generic_whitelist';

class AppInfo {
  final String packageName;
  final String appName;
  final Uint8List icon;

  const AppInfo({
    required this.packageName,
    required this.appName,
    required this.icon,
  });
}

class WhitelistController extends ChangeNotifier {
  List<AppInfo> _allApps = [];
  Set<String> enabledPackages = {};
  bool loading = true;
  String _searchQuery = '';

  WhitelistController() {
    _load();
  }

  List<AppInfo> get filteredApps {
    if (_searchQuery.isEmpty) return _allApps;
    final q = _searchQuery.toLowerCase();
    return _allApps
        .where((a) =>
            a.appName.toLowerCase().contains(q) ||
            a.packageName.toLowerCase().contains(q))
        .toList();
  }

  Future<void> _load() async {
    loading = true;
    notifyListeners();

    try {
      final prefs = await SharedPreferences.getInstance();
      final csv = prefs.getString(kPrefGenericWhitelist) ?? '';
      enabledPackages =
          csv.isEmpty ? {} : csv.split(',').where((s) => s.isNotEmpty).toSet();

      final rawList =
          await _channel.invokeMethod<List<dynamic>>('getInstalledApps') ?? [];
      _allApps = rawList.map((raw) {
        final map = Map<String, dynamic>.from(raw as Map);
        return AppInfo(
          packageName: map['packageName'] as String,
          appName: map['appName'] as String,
          icon: Uint8List.fromList((map['icon'] as List).cast<int>()),
        );
      }).toList();
    } catch (e) {
      debugPrint('WhitelistController._load error: $e');
    }

    loading = false;
    notifyListeners();
  }

  Future<void> setEnabled(String packageName, bool enabled) async {
    if (enabled) {
      enabledPackages.add(packageName);
    } else {
      enabledPackages.remove(packageName);
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    notifyListeners();
  }

  void setSearch(String query) {
    _searchQuery = query;
    notifyListeners();
  }
}
