/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/core/enums/item_position.dart';
import 'package:mindful/core/extensions/ext_build_context.dart';
import 'package:mindful/core/extensions/ext_duration.dart';
import 'package:mindful/models/app_info.dart';
import 'package:mindful/providers/restrictions/apps_restrictions_provider.dart';
import 'package:mindful/ui/common/default_expandable_list_tile.dart';
import 'package:mindful/ui/dialogs/timer_picker_dialog.dart';

class ContinuousUsageTile extends ConsumerWidget {
  const ContinuousUsageTile({
    required this.appInfo,
    super.key,
  });

  final AppInfo appInfo;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final restriction = ref.watch(
      appsRestrictionsProvider.select(
        (v) => v[appInfo.packageName],
      ),
    );

    final continuousUsageSec = restriction?.maxContinuousUsageSec ?? 0;
    final breakTimeSec = restriction?.breakTimeSec ?? 0;

    return DefaultExpandableListTile(
      position: ItemPosition.mid,
      leadingIcon: FluentIcons.timer_20_regular,
      titleText: "Continuous Usage",
      subtitleText: continuousUsageSec > 0
          ? "Max usage: ${Duration(seconds: continuousUsageSec).toTimeShort(context)}, Break: ${Duration(seconds: breakTimeSec).toTimeShort(context)}"
          : "Not set",
      content: Column(
        children: [
          ListTile(
            title: const Text("Max Continuous Usage"),
            trailing: Text(
                Duration(seconds: continuousUsageSec).toTimeShort(context)),
            onTap: () async {
              final newTime = await showAppTimerPicker(
                appInfo: appInfo,
                context: context,
                initialTime: continuousUsageSec,
                heroTag: 'continuous-usage',
              );
              if (newTime != null) {
                ref
                    .read(appsRestrictionsProvider.notifier)
                    .updateMaxContinuousUsage(appInfo.packageName, newTime);
              }
            },
          ),
          ListTile(
            title: const Text("Break Time"),
            trailing:
                Text(Duration(seconds: breakTimeSec).toTimeShort(context)),
            onTap: () async {
              final newTime = await showAppTimerPicker(
                appInfo: appInfo,
                context: context,
                initialTime: breakTimeSec,
                heroTag: 'break-time',
              );
              if (newTime != null) {
                ref
                    .read(appsRestrictionsProvider.notifier)
                    .updateBreakTime(appInfo.packageName, newTime);
              }
            },
          ),
        ],
      ),
    );
  }
}
