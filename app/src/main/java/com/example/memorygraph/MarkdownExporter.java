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
        Map<Long, Models.Person> byId = new HashMap<>();
        for (Models.Person person : persons) {
            byId.put(person.id, person);
        }
        Map<Long, Integer> intimacy = new HashMap<>();
        Map<Long, StringBuilder> relLines = new HashMap<>();
        for (Models.Relation relation : relations) {
            intimacy.merge(relation.personA, relation.strength, Integer::sum);
            intimacy.merge(relation.personB, relation.strength, Integer::sum);
            appendRelLine(relLines, byId, relation.personA, relation.personB, relation);
            appendRelLine(relLines, byId, relation.personB, relation.personA, relation);
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
        StringBuilder sb = new StringBuilder();
        sb.append("# 인간관계 그래프\n\n");
        sb.append("- 생성: ").append(fmt.format(new Date())).append('\n');
        sb.append("- 사람 ").append(persons.size()).append("명, 관계 ").append(relations.size()).append("개\n\n");
        sb.append("> 노드(사람)와 무방향 관계(친밀도 1~10)로 구성된 인간관계 그래프 스냅샷입니다. ")
                .append("에이전트는 각 사람 섹션과 관계 강도를 단서로 활용하세요.\n\n");

        sb.append("## 사람\n\n");
        for (Models.Person person : persons) {
            int score = intimacy.getOrDefault(person.id, 0);
            sb.append("### ").append(safe(person.name, "이름 없음")).append('\n');
            sb.append("- id: ").append(person.id).append('\n');
            sb.append("- 그룹: ").append(person.groupName).append('\n');
            if (!person.phone.isEmpty()) {
                sb.append("- 전화: ").append(formatPhone(person.phone)).append('\n');
            }
            sb.append("- 친밀도 합산: ").append(score).append('\n');
            if (!person.tags.isEmpty()) {
                sb.append("- 태그: ").append(TextUtils.join(", ", person.tags)).append('\n');
            }
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
                    .append(" | ").append(relation.strength)
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
                .append(" — 강도 ").append(relation.strength);
        if (!relation.label.isEmpty()) {
            sb.append(" (").append(relation.label).append(")");
        }
        sb.append('\n');
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String formatPhone(String phone) {
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
        }
        return phone;
    }
}
