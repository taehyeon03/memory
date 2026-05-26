package com.example.memorygraph;

import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MarkdownExporter {
    private MarkdownExporter() {
    }

    static String build(List<Models.Person> persons, List<Models.Relation> relations) {
        return build(persons, relations, null, null);
    }

    static String build(List<Models.Person> persons, List<Models.Relation> relations,
                        List<Models.FieldDefinition> fields,
                        Map<Long, List<String>> fieldValuesByPerson) {
        Map<Long, Models.Person> byId = new HashMap<>();
        for (Models.Person person : persons) {
            byId.put(person.id, person);
        }
        Map<Long, Integer> relationCounts = new HashMap<>();
        Map<Long, StringBuilder> relLines = new HashMap<>();
        for (Models.Relation relation : relations) {
            relationCounts.merge(relation.personA, 1, Integer::sum);
            relationCounts.merge(relation.personB, 1, Integer::sum);
            appendRelLine(relLines, byId, relation.personA, relation.personB, relation);
            appendRelLine(relLines, byId, relation.personB, relation.personA, relation);
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        StringBuilder sb = new StringBuilder();
        sb.append("# 인간관계 그래프\n\n");
        sb.append("- 생성: ").append(fmt.format(new Date())).append('\n');
        sb.append("- 사람 ").append(persons.size()).append("명, 관계 ").append(relations.size()).append("개\n\n");
        sb.append("> `나`를 기준점으로 둔 인간관계 그래프 스냅샷입니다. ")
                .append("관계 강도는 약함/보통/강함 3단계입니다.\n\n");

        List<BirthdayReminder.UpcomingBirthday> events = BirthdayReminder.upcomingBirthdays(persons, 30, 5);
        if (!events.isEmpty()) {
            sb.append("## 다가오는 이벤트\n\n");
            for (BirthdayReminder.UpcomingBirthday event : events) {
                sb.append("- ")
                        .append(safe(event.person.name, "이름 없음"))
                        .append(" 생일: ")
                        .append(event.countdownLabel())
                        .append(" (")
                        .append(event.dateLabel)
                        .append(")\n");
            }
            sb.append('\n');
        }

        sb.append("## 사람\n\n");
        for (Models.Person person : persons) {
            int relationCount = relationCounts.getOrDefault(person.id, 0);
            sb.append("### ").append(safe(person.name, "이름 없음")).append('\n');
            sb.append("- id: ").append(person.id).append('\n');
            if (person.isSelf) {
                sb.append("- 기준점: 나\n");
            }
            sb.append("- 그룹: ").append(person.groupName).append('\n');
            if (!person.phone.isEmpty()) {
                sb.append("- 전화: ").append(formatPhone(person.phone)).append('\n');
            }
            sb.append("- 성별: ").append(BirthdayReminder.genderLabel(person.gender)).append('\n');
            if (!person.birthday.isEmpty()) {
                sb.append("- 생일: ").append(person.birthday).append('\n');
            }
            sb.append("- 연결 수: ").append(relationCount).append('\n');
            if (!person.tags.isEmpty()) {
                sb.append("- 태그: ").append(TextUtils.join(", ", person.tags)).append('\n');
            }
            appendCustomFields(sb, person, fields, fieldValuesByPerson);
            StringBuilder rels = relLines.get(person.id);
            if (rels != null) {
                sb.append("- 관계:\n").append(rels);
            }
            if (!person.notes.isEmpty()) {
                sb.append("- 메모:\n");
                for (String line : person.notes.split("\\r?\\n")) {
                    sb.append("  > ").append(line).append('\n');
                }
            }
            sb.append('\n');
        }

        sb.append("## 관계 엣지\n\n");
        sb.append("| A | B | 강도 | 라벨 |\n|---|---|---|---|\n");
        for (Models.Relation relation : relations) {
            Models.Person a = byId.get(relation.personA);
            Models.Person b = byId.get(relation.personB);
            sb.append("| ").append(a == null ? relation.personA : safe(a.name, "?"))
                    .append(" | ").append(b == null ? relation.personB : safe(b.name, "?"))
                    .append(" | ").append(MemoryDatabase.strengthLabel(relation.strength))
                    .append(" | ").append(relation.label.isEmpty() ? "-" : relation.label)
                    .append(" |\n");
        }
        return sb.toString();
    }

    private static void appendRelLine(Map<Long, StringBuilder> map, Map<Long, Models.Person> byId,
                                      long owner, long other, Models.Relation relation) {
        Models.Person otherPerson = byId.get(other);
        String otherName = otherPerson == null ? "id:" + other : safe(otherPerson.name, "id:" + other);
        StringBuilder sb = map.computeIfAbsent(owner, k -> new StringBuilder());
        sb.append("  - ").append(otherName)
                .append(" — ").append(MemoryDatabase.strengthLabel(relation.strength));
        if (!relation.label.isEmpty()) {
            sb.append(" (").append(relation.label).append(")");
        }
        sb.append('\n');
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static void appendCustomFields(StringBuilder sb, Models.Person person,
                                           List<Models.FieldDefinition> fields,
                                           Map<Long, List<String>> fieldValuesByPerson) {
        if (fields == null || fields.isEmpty() || fieldValuesByPerson == null) {
            return;
        }
        List<String> values = fieldValuesByPerson.get(person.id);
        if (values == null || values.isEmpty()) {
            return;
        }
        boolean wroteHeader = false;
        int count = Math.min(fields.size(), values.size());
        for (int i = 0; i < count; i++) {
            String value = values.get(i);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (!wroteHeader) {
                sb.append("- 추가 정보:\n");
                wroteHeader = true;
            }
            sb.append("  - ")
                    .append(fields.get(i).label)
                    .append(": ")
                    .append(value.trim())
                    .append('\n');
        }
    }

    private static String formatPhone(String phone) {
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
        }
        return phone;
    }
}
