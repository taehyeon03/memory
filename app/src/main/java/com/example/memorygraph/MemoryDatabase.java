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
    private static final int DB_VERSION = 3;

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
                "notes TEXT NOT NULL DEFAULT '', " +
                "tags TEXT NOT NULL DEFAULT '[]')");
        db.execSQL("CREATE TABLE relations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "person_a INTEGER NOT NULL, " +
                "person_b INTEGER NOT NULL, " +
                "strength INTEGER NOT NULL DEFAULT 1, " +
                "label TEXT NOT NULL DEFAULT '', " +
                "updated_at INTEGER NOT NULL, " +
                "UNIQUE(person_a, person_b))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS edges");
        db.execSQL("DROP TABLE IF EXISTS memories");
        db.execSQL("DROP TABLE IF EXISTS relations");
        db.execSQL("DROP TABLE IF EXISTS persons");
        onCreate(db);
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
            return existing;
        }
        ContentValues values = new ContentValues();
        values.put("name", name == null ? "" : name);
        values.put("phone", normalizedPhone);
        values.put("group_name", "친구");
        values.put("notes", "");
        values.put("tags", "[]");
        return db.insert("persons", null, values);
    }

    long insertManualPerson(String name, String groupName) {
        ContentValues values = new ContentValues();
        values.put("name", name == null ? "" : name);
        values.putNull("phone");
        values.put("group_name", groupName == null || groupName.isEmpty() ? "친구" : groupName);
        values.put("notes", "");
        values.put("tags", "[]");
        return getWritableDatabase().insert("persons", null, values);
    }

    void updatePerson(long personId, String name, String groupName, String notes, List<String> tags) {
        ContentValues values = new ContentValues();
        if (name != null) {
            values.put("name", name);
        }
        if (groupName != null && !groupName.isEmpty()) {
            values.put("group_name", groupName);
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

    void deletePerson(long personId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("relations", "person_a = ? OR person_b = ?",
                new String[]{String.valueOf(personId), String.valueOf(personId)});
        db.delete("persons", "id = ?", new String[]{String.valueOf(personId)});
    }

    void bumpRelation(long fromId, long toId, int delta) {
        if (fromId == toId) {
            return;
        }
        long a = Math.min(fromId, toId);
        long b = Math.max(fromId, toId);
        SQLiteDatabase db = getWritableDatabase();
        Models.Relation existing = findRelation(a, b);
        long now = System.currentTimeMillis();
        if (existing == null) {
            ContentValues values = new ContentValues();
            values.put("person_a", a);
            values.put("person_b", b);
            values.put("strength", Math.max(1, Math.min(10, delta)));
            values.put("label", "");
            values.put("updated_at", now);
            db.insert("relations", null, values);
            return;
        }
        int next = Math.max(0, Math.min(10, existing.strength + delta));
        if (next <= 0) {
            db.delete("relations", "id = ?", new String[]{String.valueOf(existing.id)});
            return;
        }
        ContentValues values = new ContentValues();
        values.put("strength", next);
        values.put("updated_at", now);
        db.update("relations", values, "id = ?", new String[]{String.valueOf(existing.id)});
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

    List<Models.Person> listPersons() {
        List<Models.Person> persons = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name, phone, group_name, notes, tags FROM persons ORDER BY id ASC", null)) {
            while (cursor.moveToNext()) {
                persons.add(new Models.Person(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        fromJson(cursor.getString(5))));
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
                        cursor.getInt(3),
                        cursor.getString(4),
                        cursor.getLong(5)));
            }
        }
        return relations;
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
                    cursor.getInt(3), cursor.getString(4), cursor.getLong(5));
        }
    }

    private Long findPersonIdByPhone(String phone) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM persons WHERE phone = ?",
                new String[]{phone})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : null;
        }
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
}
