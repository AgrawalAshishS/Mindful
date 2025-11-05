/*
 *
 *  * Copyright (c) 2024 Mindful (https://github.com/akaMrNagar/Mindful)
 *  * Author : Pawan Nagar (https://github.com/akaMrNagar)
 *  *
 *  * This source code is licensed under the GPL-2.0 license license found in the
 *  * LICENSE file in the root directory of this source tree.
 *
 */

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mindful/core/database/app_database.dart';
import 'package:mindful/core/extensions/ext_build_context.dart';
import 'package:mindful/providers/restrictions/schedules_provider.dart';

class EditScheduleScreen extends ConsumerStatefulWidget {
  const EditScheduleScreen({super.key});

  @override
  ConsumerState<EditScheduleScreen> createState() => _EditScheduleScreenState();
}

class _EditScheduleScreenState extends ConsumerState<EditScheduleScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _timerController;
  late final TextEditingController _launchLimitController;
  late final TextEditingController _daysOfWeekController;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController();
    _timerController = TextEditingController();
    _launchLimitController = TextEditingController();
    _daysOfWeekController = TextEditingController();
  }

  @override
  void dispose() {
    _nameController.dispose();
    _timerController.dispose();
    _launchLimitController.dispose();
    _daysOfWeekController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheduleId = context.resolveParam<int>('scheduleId');
    final isNew = scheduleId == null;
    final schedule = ref.watch(schedulesProvider).firstWhere(
          (s) => s.id == scheduleId,
          orElse: () => const Schedule(
            id: 0,
            name: '',
            timerSec: 0,
            launchLimit: 0,
            activePeriodStart: TimeOfDayAdapter(hour: 0, minute: 0),
            activePeriodEnd: TimeOfDayAdapter(hour: 0, minute: 0),
            periodDurationInMins: 0,
            daysOfWeek: '',
            sessionDuration: 0,
            cooldownDuration: 0,
          ),
        );

    if (!isNew) {
      _nameController.text = schedule.name;
      _timerController.text = schedule.timerSec.toString();
      _launchLimitController.text = schedule.launchLimit.toString();
      _daysOfWeekController.text = schedule.daysOfWeek;
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(isNew ? 'New Schedule' : 'Edit Schedule'),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TextFormField(
              controller: _nameController,
              decoration: const InputDecoration(labelText: 'Name'),
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return 'Please enter a name';
                }
                return null;
              },
            ),
            TextFormField(
              controller: _timerController,
              decoration: const InputDecoration(labelText: 'Timer (seconds)'),
              keyboardType: TextInputType.number,
            ),
            TextFormField(
              controller: _launchLimitController,
              decoration: const InputDecoration(labelText: 'Launch Limit'),
              keyboardType: TextInputType.number,
            ),
            TextFormField(
              controller: _daysOfWeekController,
              decoration:
                  const InputDecoration(labelText: 'Days of Week (Mon,Tue,Wed)'),
            ),
            ElevatedButton(
              onPressed: () {
                if (_formKey.currentState!.validate()) {
                  final newSchedule = SchedulesCompanion.insert(
                    name: _nameController.text,
                    timerSec: Value(int.tryParse(_timerController.text) ?? 0),
                    launchLimit:
                        Value(int.tryParse(_launchLimitController.text) ?? 0),
                    daysOfWeek: Value(_daysOfWeekController.text),
                  );
                  if (isNew) {
                    ref.read(schedulesProvider.notifier).addSchedule(newSchedule);
                  } else {
                    ref.read(schedulesProvider.notifier).updateSchedule(
                          schedule.copyWith(
                            name: _nameController.text,
                            timerSec: int.tryParse(_timerController.text) ?? 0,
                            launchLimit:
                                int.tryParse(_launchLimitController.text) ?? 0,
                            daysOfWeek: _daysOfWeekController.text,
                          ),
                        );
                  }
                  Navigator.of(context).pop();
                }
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }
}
