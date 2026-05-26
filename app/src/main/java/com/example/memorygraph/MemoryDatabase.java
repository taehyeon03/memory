package com.example.memorygraph;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class MemoryDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "memory_graph.db";
    private static final int DB_VERSION = 7;

    MemoryDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE persons (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "phone TEXT UNIQUE, " +
                "group_name TEXT NOT NULL DEFAULT '친구', " +
                "gender TEXT NOT NULL DEFAULT 'unspecified', " +
                "birthday TEXT NOT NULL DEFAULT '', " +
                "notes TEXT NOT NULL DEFAULT '', " +
                "tags TEXT NOT NULL DEFAULT '[]', " +
                "is_self INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE relations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "person_a INTEGER NOT NULL, " +
                "person_b INTEGER NOT NULL, " +
                "strength INTEGER NOT NULL DEFAULT 1, " +
                "label TEXT NOT NULL DEFAULT '', " +
                "updated_at INTEGER NOT NULL, " +
                "UNIQUE(person_a, person_b))");
        createFieldTables(db);
        createGroupTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS edges");
        db.execSQL("DROP TABLE IF EXISTS memories");
        if (oldVersion < 3) {
            db.execSQL("DROP TABLE IF EXISTS relations");
            db.execSQL("DROP TABLE IF EXISTS persons");
            onCreate(db);
            return;
        }
        if (oldVersion < 4) {
            addColumnIfMissing(db, "persons", "gender",
                    "ALTER TABLE persons ADD COLUMN gender TEXT NOT NULL DEFAULT 'unspecified'");
            addColumnIfMissing(db, "persons", "birthday",
                    "ALTER TABLE persons ADD COLUMN birthday TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 5) {
            addColumnIfMissing(db, "persons", "is_self",
                    "ALTER TABLE persons ADD COLUMN is_self INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 6) {
            createFieldTables(db);
            db.execSQL("UPDATE relations SET strength = CASE " +
                    "WHEN strength <= 2 THEN 1 " +
                    "WHEN strength <= 6 THEN 2 " +
                    "ELSE 3 END");
        }
        if (oldVersion < 7) {
            createGroupTables(db);
            seedGroupsFromExistingPeople(db);
        }
    }

    long ensureSelfPerson() {
        SQLiteDatabase db = getWritableDatabase();
        Long existing = findSelfPersonId();
        if (existing != null) {
            ensureSelfLinks(existing);
            return existing;
        }
        ContentValues values = new ContentValues();
        values.put("name", "나");
        values.putNull("phone");
        values.put("group_name", "나");
        values.put("gender", "unspecified");
        values.put("birthday", "");
        values.put("notes", "그래프의 기준이 되는 내 프로필");
        values.put("tags", toJson(new ArrayList<String>()));
        values.put("is_self", 1);
        long selfId = db.insert("persons", null, values);
        if (selfId > 0) {
            ensureSelfLinks(selfId);
        }
        return selfId;
    }

    long upsertContact(String name, String phone) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isEmpty()) {
            return -1L;
        }
        SQLiteDatabase db = getWritableDatabase();
        Long existing = findPersonIdByPhone(normalizedPhone);
        if (existing != null) {
            ContentValues values = new ContentValues();
            values.put("name", name == null ? "" : name);
            db.update("persons", values, "id = ?", new String[]{String.valueOf(existing)});
            ensureSelfRelation(existing, 1, "내 연락처");
            return existing;
        }
        ContentValues values = new ContentValues();
        values.put("name", name == null ? "" : name);
        values.put("phone", normalizedPhone);
        values.put("group_name", "친구");
        values.put("gender", "unspecified");
        values.put("birthday", "");
        values.put("notes", "");
        values.put("tags", "[]");
        values.put("is_self", 0);
        long personId = db.insert("persons", null, values);
        ensureSelfRelation(personId, 1, "내 연락처");
        return personId;
    }

    long insertManualPerson(String name, String groupName) {
        ContentValues values = new ContentValues();
        values.put("name", name == null ? "" : name);
        values.putNull("phone");
        values.put("group_name", groupName == null || groupName.isEmpty() ? "친구" : groupName);
        values.put("gender", "unspecified");
        values.put("birthday", "");
        values.put("notes", "");
        values.put("tags", "[]");
        values.put("is_self", 0);
        long personId = getWritableDatabase().insert("persons", null, values);
        ensureSelfRelation(personId, 1, "직접 추가");
        return personId;
    }

    void updatePerson(long personId, String name, String groupName, String gender,
                      String birthday, String notes, List<String> tags) {
        ContentValues values = new ContentValues();
        if (name != null) {
            values.put("name", name);
        }
        if (groupName != null && !groupName.isEmpty()) {
            values.put("group_name", groupName);
        }
        if (gender != null) {
            values.put("gender", gender.isEmpty() ? "unspecified" : gender);
        }
        if (birthday != null) {
            values.put("birthday", birthday);
        }
        if (notes != null) {
            values.put("notes", notes);
        }
        if (tags != null) {
            values.put("tags", toJson(tags));
        }
        if (values.size() == 0) {
            return;
        }
        getWritableDatabase().update("persons", values, "id = ?", new String[]{String.valueOf(personId)});
    }

    List<String> listGroups() {
        List<String> groups = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT label FROM person_groups ORDER BY sort_order ASC, id ASC", null)) {
            while (cursor.moveToNext()) {
                String label = cursor.getString(0);
                if (label != null && !label.trim().isEmpty()) {
                    groups.add(label);
                }
            }
        }
        if (!groups.contains("친구")) groups.add(0, "친구");
        return groups;
    }

    long addGroup(String label) {
        String clean = label == null ? "" : label.trim();
        if (clean.isEmpty()) {
            return -1L;
        }
        SQLiteDatabase db = getWritableDatabase();
        Long existing = findGroupId(clean);
        if (existing != null) {
            return existing;
        }
        int sortOrder = 0;
        try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM person_groups", null)) {
            if (cursor.moveToFirst()) {
                sortOrder = cursor.getInt(0);
            }
        }
        ContentValues values = new ContentValues();
        values.put("label", clean);
        values.put("sort_order", sortOrder);
        values.put("created_at", System.currentTimeMillis());
        return db.insert("person_groups", null, values);
    }

    void deletePerson(long personId) {
        if (isSelfPerson(personId)) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        db.delete("relations", "person_a = ? OR person_b = ?",
                new String[]{String.valueOf(personId), String.valueOf(personId)});
        db.delete("persons", "id = ?", new String[]{String.valueOf(personId)});
    }

    void setRelationStrength(long fromId, long toId, int strength) {
        if (fromId == toId) {
            return;
        }
        long a = Math.min(fromId, toId);
        long b = Math.max(fromId, toId);
        SQLiteDatabase db = getWritableDatabase();
        Models.Relation existing = findRelation(a, b);
        long now = System.currentTimeMillis();
        int normalized = normalizeStrength(strength);
        if (existing == null) {
            ContentValues values = new ContentValues();
            values.put("person_a", a);
            values.put("person_b", b);
            values.put("strength", normalized);
            values.put("label", "");
            values.put("updated_at", now);
            db.insert("relations", null, values);
            return;
        }
        ContentValues values = new ContentValues();
        values.put("strength", normalized);
        values.put("updated_at", now);
        db.update("relations", values, "id = ?", new String[]{String.valueOf(existing.id)});
    }

    boolean connectPeople(long fromId, long toId) {
        if (fromId == toId) {
            return false;
        }
        long a = Math.min(fromId, toId);
        long b = Math.max(fromId, toId);
        if (findRelation(a, b) != null) {
            return false;
        }
        ensureRelation(a, b, 2, "");
        return true;
    }

    void setRelationLabel(long fromId, long toId, String label) {
        long a = Math.min(fromId, toId);
        long b = Math.max(fromId, toId);
        ContentValues values = new ContentValues();
        values.put("label", label == null ? "" : label);
        getWritableDatabase().update("relations", values, "person_a = ? AND person_b = ?",
                new String[]{String.valueOf(a), String.valueOf(b)});
    }

    void deleteRelation(long fromId, long toId) {
        long a = Math.min(fromId, toId);
        long b = Math.max(fromId, toId);
        getWritableDatabase().delete("relations", "person_a = ? AND person_b = ?",
                new String[]{String.valueOf(a), String.valueOf(b)});
    }

    void ensureSelfLinks() {
        Long selfId = findSelfPersonId();
        if (selfId != null) {
            ensureSelfLinks(selfId);
        }
    }

    List<Models.Person> listPersons() {
        List<Models.Person> persons = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name, phone, group_name, gender, birthday, notes, tags, is_self " +
                        "FROM persons ORDER BY is_self DESC, id ASC", null)) {
            while (cursor.moveToNext()) {
                persons.add(new Models.Person(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        fromJson(cursor.getString(7)),
                        cursor.getInt(8) == 1));
            }
        }
        return persons;
    }

    List<Models.Relation> listRelations() {
        List<Models.Relation> relations = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, person_a, person_b, strength, label, updated_at FROM relations", null)) {
            while (cursor.moveToNext()) {
                relations.add(new Models.Relation(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getLong(2),
                        normalizeStrength(cursor.getInt(3)),
                        cursor.getString(4),
                        cursor.getLong(5)));
            }
        }
        return relations;
    }

    List<Models.FieldDefinition> listFieldDefinitions() {
        List<Models.FieldDefinition> fields = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, label, sort_order FROM person_fields ORDER BY sort_order ASC, id ASC", null)) {
            while (cursor.moveToNext()) {
                fields.add(new Models.FieldDefinition(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)));
            }
        }
        return fields;
    }

    long addFieldDefinition(String label) {
        String clean = label == null ? "" : label.trim();
        if (clean.isEmpty()) {
            return -1L;
        }
        SQLiteDatabase db = getWritableDatabase();
        Long existing = findFieldDefinitionId(clean);
        if (existing != null) {
            return existing;
        }
        int sortOrder = 0;
        try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM person_fields", null)) {
            if (cursor.moveToFirst()) {
                sortOrder = cursor.getInt(0);
            }
        }
        ContentValues values = new ContentValues();
        values.put("label", clean);
        values.put("sort_order", sortOrder);
        values.put("created_at", System.currentTimeMillis());
        return db.insert("person_fields", null, values);
    }

    void deleteFieldDefinition(long fieldId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("person_field_values", "field_id = ?", new String[]{String.valueOf(fieldId)});
        db.delete("person_fields", "id = ?", new String[]{String.valueOf(fieldId)});
    }

    List<String> listFieldValues(long personId) {
        List<Models.FieldDefinition> fields = listFieldDefinitions();
        List<String> values = new ArrayList<>();
        for (Models.FieldDefinition field : fields) {
            values.add(getFieldValue(personId, field.id));
        }
        return values;
    }

    void updateFieldValues(long personId, List<Models.FieldDefinition> fields, List<String> values) {
        if (fields == null || values == null) {
            return;
        }
        int count = Math.min(fields.size(), values.size());
        for (int i = 0; i < count; i++) {
            setFieldValue(personId, fields.get(i).id, values.get(i));
        }
    }

    private Models.Relation findRelation(long a, long b) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, person_a, person_b, strength, label, updated_at FROM relations " +
                        "WHERE person_a = ? AND person_b = ?",
                new String[]{String.valueOf(a), String.valueOf(b)})) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new Models.Relation(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2),
                    normalizeStrength(cursor.getInt(3)), cursor.getString(4), cursor.getLong(5));
        }
    }

    private void ensureSelfLinks(long selfId) {
        List<Long> personIds = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM persons WHERE id != ?",
                new String[]{String.valueOf(selfId)})) {
            while (cursor.moveToNext()) {
                personIds.add(cursor.getLong(0));
            }
        }
        for (Long personId : personIds) {
            ensureRelation(selfId, personId, 1, "내 연락처");
        }
    }

    private void ensureSelfRelation(long personId, int strength, String label) {
        Long selfId = findSelfPersonId();
        if (selfId == null || personId <= 0 || personId == selfId) {
            return;
        }
        ensureRelation(selfId, personId, strength, label);
    }

    private void ensureRelation(long fromId, long toId, int strength, String label) {
        if (fromId == toId) {
            return;
        }
        long a = Math.min(fromId, toId);
        long b = Math.max(fromId, toId);
        if (findRelation(a, b) != null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("person_a", a);
        values.put("person_b", b);
        values.put("strength", normalizeStrength(strength));
        values.put("label", label == null ? "" : label);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insert("relations", null, values);
    }

    private boolean isSelfPerson(long personId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT is_self FROM persons WHERE id = ?",
                new String[]{String.valueOf(personId)})) {
            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    private Long findSelfPersonId() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM persons WHERE is_self = 1 ORDER BY id ASC LIMIT 1", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private Long findPersonIdByPhone(String phone) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM persons WHERE phone = ?",
                new String[]{phone})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private Long findFieldDefinitionId(String label) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM person_fields WHERE label = ?",
                new String[]{label})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    private String getFieldValue(long personId, long fieldId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT value FROM person_field_values WHERE person_id = ? AND field_id = ?",
                new String[]{String.valueOf(personId), String.valueOf(fieldId)})) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    private void setFieldValue(long personId, long fieldId, String value) {
        String clean = value == null ? "" : value.trim();
        SQLiteDatabase db = getWritableDatabase();
        if (clean.isEmpty()) {
            db.delete("person_field_values", "person_id = ? AND field_id = ?",
                    new String[]{String.valueOf(personId), String.valueOf(fieldId)});
            return;
        }
        ContentValues values = new ContentValues();
        values.put("person_id", personId);
        values.put("field_id", fieldId);
        values.put("value", clean);
        db.insertWithOnConflict("person_field_values", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static void createFieldTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS person_fields (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "label TEXT NOT NULL UNIQUE, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS person_field_values (" +
                "person_id INTEGER NOT NULL, " +
                "field_id INTEGER NOT NULL, " +
                "value TEXT NOT NULL DEFAULT '', " +
                "PRIMARY KEY(person_id, field_id))");
    }

    private static void createGroupTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS person_groups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "label TEXT NOT NULL UNIQUE, " +
                "sort_order INTEGER NOT NULL DEFAULT 0, " +
                "created_at INTEGER NOT NULL)");
        // 기본 값
        db.execSQL("INSERT OR IGNORE INTO person_groups(label, sort_order, created_at) VALUES('친구', 1, strftime('%s','now') * 1000)");
        db.execSQL("INSERT OR IGNORE INTO person_groups(label, sort_order, created_at) VALUES('가족', 2, strftime('%s','now') * 1000)");
        db.execSQL("INSERT OR IGNORE INTO person_groups(label, sort_order, created_at) VALUES('직장', 3, strftime('%s','now') * 1000)");
        db.execSQL("INSERT OR IGNORE INTO person_groups(label, sort_order, created_at) VALUES('학교', 4, strftime('%s','now') * 1000)");
        db.execSQL("INSERT OR IGNORE INTO person_groups(label, sort_order, created_at) VALUES('군대', 5, strftime('%s','now') * 1000)");
    }

    private static void seedGroupsFromExistingPeople(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT group_name FROM persons", null)) {
            while (cursor.moveToNext()) {
                String group = cursor.getString(0);
                if (group == null) continue;
                String clean = group.trim();
                if (clean.isEmpty()) continue;
                ContentValues values = new ContentValues();
                values.put("label", clean);
                values.put("sort_order", 100);
                values.put("created_at", System.currentTimeMillis());
                db.insertWithOnConflict("person_groups", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private Long findGroupId(String label) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM person_groups WHERE label = ?",
                new String[]{label})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
    }

    static int normalizeStrength(int strength) {
        if (strength <= 1) return 1;
        if (strength >= 3) return 3;
        return 2;
    }

    static String strengthLabel(int strength) {
        int normalized = normalizeStrength(strength);
        if (normalized == 1) return "약함";
        if (normalized == 2) return "보통";
        return "강함";
    }

    static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }

    static String toJson(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                array.put(value);
            }
        }
        return array.toString();
    }

    static List<String> fromJson(String json) {
        List<String> values = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return values;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                values.add(array.optString(i));
            }
        } catch (JSONException ignored) {
        }
        return values;
    }

    private static void addColumnIfMissing(SQLiteDatabase db, String table, String column, String alterSql) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(1))) {
                    return;
                }
            }
        }
        db.execSQL(alterSql);
    }
}
