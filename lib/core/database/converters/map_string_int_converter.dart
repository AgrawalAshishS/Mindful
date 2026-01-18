import 'dart:convert';

import 'package:drift/drift.dart';

class MapStringIntConverter extends TypeConverter<Map<String, int>, String> {
  const MapStringIntConverter();

  @override
  Map<String, int> fromSql(String fromDb) {
    try {
      return Map<String, int>.from(json.decode(fromDb));
    } catch (e) {
      return {};
    }
  }

  @override
  String toSql(Map<String, int> value) {
    return json.encode(value);
  }
}
