/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:drift/drift.dart';

@DataClassName("UsageLimit")
class UsageLimitsTable extends Table {
  IntColumn get id => integer().autoIncrement()();
  TextColumn get appPackageName => text()();
  IntColumn get days => integer()();
  IntColumn get usageLimit => integer()();
}
