package com.example.memorygraph;

import java.util.ArrayList;
import java.util.List;

final class Models {
    private Models() {
    }

    static final class Person {
        final long id;
        final String name;
        final String phone;
        final String groupName;
        final String gender;
        final String birthday;
        final String notes;
        final List<String> tags;
        final boolean isSelf;

        Person(long id, String name, String phone, String groupName, String gender,
               String birthday, String notes, List<String> tags, boolean isSelf) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.phone = phone == null ? "" : phone;
            this.groupName = groupName == null || groupName.isEmpty() ? "친구" : groupName;
            this.gender = gender == null || gender.isEmpty() ? "unspecified" : gender;
            this.birthday = birthday == null ? "" : birthday;
            this.notes = notes == null ? "" : notes;
            this.tags = tags == null ? new ArrayList<>() : tags;
            this.isSelf = isSelf;
        }
    }

    static final class Relation {
        final long id;
        final long personA;
        final long personB;
        final int strength;
        final String label;
        final long updatedAt;

        Relation(long id, long personA, long personB, int strength, String label, long updatedAt) {
            this.id = id;
            this.personA = personA;
            this.personB = personB;
            this.strength = strength;
            this.label = label == null ? "" : label;
            this.updatedAt = updatedAt;
        }
    }

    static final class FieldDefinition {
        final long id;
        final String label;
        final int sortOrder;

        FieldDefinition(long id, String label, int sortOrder) {
            this.id = id;
            this.label = label == null ? "" : label;
            this.sortOrder = sortOrder;
        }
    }
}
