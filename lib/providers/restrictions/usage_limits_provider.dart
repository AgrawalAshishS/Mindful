/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/core/database/daos/usage_limits_dao.dart';
import 'package:mindful/core/services/drift_db_service.dart';
import 'package:drift/drift.dart';

final usageLimitsProvider =
    StateNotifierProvider<UsageLimitsNotifier, List<UsageLimit>>(
  (ref) => UsageLimitsNotifier(),
);

class UsageLimitsNotifier extends StateNotifier<List<UsageLimit>> {
  late UsageLimitsDao _dao;

  UsageLimitsNotifier() : super([]) {
    _init();
  }

  void _init() async {
    _dao = DriftDbService.instance.driftDb.usageLimitsDao;
    state = await _dao.getUsageLimits();
  }

  Future<void> addUsageLimit(UsageLimitsTableCompanion entry) async {
    final id = await _dao.addUsageLimit(entry);
    final newLimit = UsageLimit(
        id: id,
        appPackageName: entry.appPackageName.value,
        days: entry.days.value,
        usageLimit: entry.usageLimit.value);
    state = [...state, newLimit];
  }

  Future<void> updateUsageLimit(UsageLimitsTableCompanion entry) async {
    await _dao.updateUsageLimit(entry);
    state = [
      for (final limit in state)
        if (limit.id == entry.id.value)
          UsageLimit(
            id: entry.id.value,
            appPackageName: entry.appPackageName.value,
            days: entry.days.value,
            usageLimit: entry.usageLimit.value,
          )
        else
          limit,
    ];
  }

  Future<void> deleteUsageLimit(int id) async {
    await _dao.deleteUsageLimit(id);
    state = state.where((limit) => limit.id != id).toList();
  }
}
