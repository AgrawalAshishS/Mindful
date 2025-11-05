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
import 'package:mindful/core/database/adapters/time_of_day_adapter.dart';
import 'package.flutter/material.dart';

@DataClassName("Schedule")
class Schedules extends Table {
  IntColumn get id => integer().autoIncrement()();

  TextColumn get name => text().withLength(min: 1, max: 50)();

  /// The timer set for the app in SECONDS
  IntColumn get timerSec => integer().withDefault(const Constant(0))();

  /// The number of times user can launch this app
  IntColumn get launchLimit => integer().withDefault(const Constant(0))();

  /// [TimeOfDay] in minutes from where the active period will start.
  /// It is stored as total minutes.
  IntColumn get activePeriodStart => integer()
      .map(const TimeOfDayAdapterConverter())
      .withDefault(const Constant(0))();

  /// [TimeOfDay] in minutes when the active period will end
  /// It is stored as total minutes.
  IntColumn get activePeriodEnd => integer()
      .map(const TimeOfDayAdapterConverter())
      .withDefault(const Constant(0))();

  /// Total duration of active period from start to end in MINUTES
  IntColumn get periodDurationInMins =>
      integer().withDefault(const Constant(0))();

  /// Comma-separated list of days of the week (e.g., "Mon,Tue,Wed")
  TextColumn get daysOfWeek => text().withDefault(const Constant(''))();

  /// Session duration in minutes
  IntColumn get sessionDuration => integer().withDefault(const Constant(0))();

  /// Cooldown duration in minutes
  IntColumn get cooldownDuration => integer().withDefault(const Constant(0))();
}
