/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package.drift/drift.dart';

@DataClassName("AppSchedule")
class AppSchedules extends Table {
  /// Package name of the related app
  TextColumn get appPackage => text()();

  /// ID of the [Schedule] this app is associated with
  IntColumn get scheduleId => integer()();

  @override
  Set<Column<Object>>? get primaryKey => {appPackage, scheduleId};
}
