part of '../app_database.dart';

@DriftAccessor(tables: [UsageLimitsTable])
class UsageLimitsDao extends DatabaseAccessor<AppDatabase>
    with _$UsageLimitsDaoMixin {
  UsageLimitsDao(super.db);

  Future<List<UsageLimit>> getUsageLimits() => select(usageLimitsTable).get();

  Future<int> addUsageLimit(UsageLimitsTableCompanion entry) =>
      into(usageLimitsTable).insert(entry);

  Future<void> updateUsageLimit(UsageLimitsTableCompanion entry) =>
      update(usageLimitsTable).replace(entry);

  Future<void> deleteUsageLimit(int id) =>
      (delete(usageLimitsTable)..where((t) => t.id.equals(id))).go();
}
