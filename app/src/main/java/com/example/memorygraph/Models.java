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
        final String notes;
        final List<String> tags;

        Person(long id, String name, String phone, String groupName, String notes, List<String> tags) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.phone = phone == null ? "" : phone;
            this.groupName = groupName == null || groupName.isEmpty() ? "친구" : groupName;
            this.notes = notes == null ? "" : notes;
            this.tags = tags == null ? new ArrayList<>() : tags;
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
}
