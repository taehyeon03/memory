package com.example.memorygraph;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int REQUEST_CONTACT_PERMISSION = 100;
    private static final int REQUEST_PICK_CONTACT = 200;
    private static final int REQUEST_SAVE_MD = 300;

    private MemoryDatabase database;
    private MemoryGraphView graphView;
    private TextView headlineView;
    private TextView statusView;
    private Button connectToggle;
    private long selectedPersonId = -1L;
    private String pendingMarkdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new MemoryDatabase(this);
        setContentView(buildUi());
        refresh("준비 완료");
        requestContactPermissionOnce();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 11, 22));

        root.addView(buildTopBar());

        graphView = new MemoryGraphView(this);
        LinearLayout.LayoutParams graphParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        int margin = dp(12);
        graphParams.setMargins(margin, margin / 2, margin, margin / 2);
        graphView.setLayoutParams(graphParams);
        graphView.setBackground(roundedFill(Color.rgb(11, 16, 30), dp(20), Color.rgb(30, 41, 59), dp(1)));
        graphView.setListener(new MemoryGraphView.Listener() {
            @Override
            public void onPersonTap(long personId) {
                selectedPersonId = personId;
                graphView.setSelectedPerson(personId);
                Models.Person person = findPerson(personId);
                if (person != null) {
                    showPersonSheet(person);
                }
            }

            @Override
            public void onConnect(long fromId, long toId) {
                database.bumpRelation(fromId, toId, 1);
                refresh("관계 강화 +1");
            }

            @Override
            public void onEmptyTap() {
                selectedPersonId = -1L;
                graphView.setSelectedPerson(-1L);
            }
        });
        root.addView(graphView);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(148, 163, 184));
        statusView.setTextSize(12);
        statusView.setPadding(dp(16), 0, dp(16), dp(8));
        root.addView(statusView);

        root.addView(buildBottomBar());
        return root;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(16), dp(12), dp(8));
        bar.setBackgroundColor(Color.rgb(8, 11, 22));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titles.setLayoutParams(titleParams);

        headlineView = new TextView(this);
        headlineView.setText("Memory Graph");
        headlineView.setTextSize(20);
        headlineView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headlineView.setTextColor(Color.rgb(241, 245, 249));
        titles.addView(headlineView);

        TextView subtitle = new TextView(this);
        subtitle.setText("사람 노드를 끌어 친밀도를 키워보세요");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.rgb(148, 163, 184));
        titles.addView(subtitle);

        bar.addView(titles);

        connectToggle = iconButton("🔗", v -> toggleConnectMode());
        bar.addView(connectToggle);

        Button exportButton = iconButton("⤓ MD", v -> startMarkdownExport());
        bar.addView(exportButton);

        return bar;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(20));
        bar.setBackgroundColor(Color.rgb(8, 11, 22));

        bar.addView(actionButton("👤  연락처에서", v -> pickContact()));
        bar.addView(actionButton("📇  전체 가져오기", v -> importAllContacts()));
        bar.addView(actionButton("⚙️  관리", v -> showManageSheet()));
        return bar;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(226, 232, 240));
        button.setTextSize(13);
        button.setBackground(roundedFill(Color.rgb(30, 41, 59), dp(14), Color.rgb(51, 65, 85), dp(1)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button iconButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(226, 232, 240));
        button.setTextSize(13);
        button.setBackground(roundedFill(Color.rgb(30, 41, 59), dp(20), Color.rgb(51, 65, 85), dp(1)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        button.setMinWidth(dp(56));
        button.setPadding(dp(14), 0, dp(14), 0);
        return button;
    }

    private GradientDrawable roundedFill(int fill, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void toggleConnectMode() {
        boolean next = !graphView.isConnectMode();
        graphView.setConnectMode(next);
        connectToggle.setText(next ? "🔗 ON" : "🔗");
        connectToggle.setBackground(roundedFill(
                next ? Color.rgb(14, 165, 233) : Color.rgb(30, 41, 59),
                dp(20),
                next ? Color.rgb(125, 211, 252) : Color.rgb(51, 65, 85),
                dp(1)));
        toast(next ? "연결 모드 — 노드를 끌어 다른 노드에 놓으면 친밀도가 +1" : "보기 모드");
    }

    private void requestContactPermissionOnce() {
        SharedPreferences prefs = getSharedPreferences("memory_graph_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("contact_permission_requested", false)) {
            return;
        }
        prefs.edit().putBoolean("contact_permission_requested", true).apply();
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACT_PERMISSION);
        }
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_CONTACT);
    }

    private void importAllContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACT_PERMISSION);
            return;
        }
        int imported = 0;
        ContentResolver resolver = getContentResolver();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    String phone = cursor.getString(1);
                    if (database.upsertContact(name, phone) > 0) {
                        imported++;
                    }
                }
            }
        }
        refresh("연락처 " + imported + "건 가져옴");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQUEST_PICK_CONTACT && data.getData() != null) {
            importPickedContact(data.getData());
        } else if (requestCode == REQUEST_SAVE_MD && data.getData() != null && pendingMarkdown != null) {
            writeMarkdownTo(data.getData(), pendingMarkdown);
            pendingMarkdown = null;
        }
    }

    private void importPickedContact(Uri contactUri) {
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };
        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                String phone = cursor.getString(1);
                long personId = database.upsertContact(name, phone);
                if (personId > 0) {
                    selectedPersonId = personId;
                    refresh("연락처 추가: " + name);
                } else {
                    toast("전화번호 없는 연락처는 추가할 수 없습니다");
                }
            }
        }
    }

    private void startMarkdownExport() {
        List<Models.Person> persons = database.listPersons();
        List<Models.Relation> relations = database.listRelations();
        if (persons.isEmpty()) {
            toast("내보낼 사람이 없습니다");
            return;
        }
        pendingMarkdown = MarkdownExporter.build(persons, relations);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/markdown");
        intent.putExtra(Intent.EXTRA_TITLE, "memory_graph.md");
        startActivityForResult(intent, REQUEST_SAVE_MD);
    }

    private void writeMarkdownTo(Uri uri, String content) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                toast("파일을 열 수 없습니다");
                return;
            }
            output.write(content.getBytes("UTF-8"));
            output.flush();
            toast("MD 저장 완료");
        } catch (Exception e) {
            toast("MD 저장 실패: " + e.getMessage());
        }
    }

    private void refresh(String status) {
        List<Models.Person> persons = database.listPersons();
        List<Models.Relation> relations = database.listRelations();
        graphView.setSnapshot(persons, relations);
        graphView.setSelectedPerson(selectedPersonId);
        statusView.setText(status + " · 사람 " + persons.size() + "명, 관계 " + relations.size() + "개");
    }

    private Models.Person findPerson(long id) {
        for (Models.Person person : database.listPersons()) {
            if (person.id == id) return person;
        }
        return null;
    }

    private void showPersonSheet(Models.Person person) {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(15, 23, 42));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(layout);

        TextView header = labelText(person.name.isEmpty() ? "이름 없음" : person.name, 18, true);
        layout.addView(header);

        EditText nameInput = sheetInput("이름", person.name);
        layout.addView(nameInput);

        EditText groupInput = sheetInput("그룹: 가족/친구/직장/...", person.groupName);
        layout.addView(groupInput);

        EditText tagsInput = sheetInput("태그 (콤마 구분)", TextUtils.join(", ", person.tags));
        layout.addView(tagsInput);

        EditText notesInput = sheetInput("메모 — 에이전트가 읽을 자유 서술", person.notes);
        notesInput.setMinLines(4);
        notesInput.setGravity(Gravity.TOP);
        layout.addView(notesInput);

        layout.addView(labelText("관계", 14, true));
        Map<Long, String> nameById = new HashMap<>();
        for (Models.Person p : database.listPersons()) {
            nameById.put(p.id, p.name.isEmpty() ? "이름 없음" : p.name);
        }
        boolean any = false;
        for (Models.Relation relation : database.listRelations()) {
            long otherId;
            if (relation.personA == person.id) otherId = relation.personB;
            else if (relation.personB == person.id) otherId = relation.personA;
            else continue;
            any = true;
            layout.addView(buildRelationRow(person.id, otherId, nameById.getOrDefault(otherId, "?"),
                    relation.strength, relation.label));
        }
        if (!any) {
            TextView none = labelText("관계 없음 — 연결 모드에서 노드를 다른 노드로 끌어보세요", 12, false);
            none.setTextColor(Color.rgb(148, 163, 184));
            layout.addView(none);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("저장", (d, w) -> {
                    database.updatePerson(person.id,
                            nameInput.getText().toString().trim(),
                            groupInput.getText().toString().trim(),
                            notesInput.getText().toString(),
                            splitTags(tagsInput.getText().toString()));
                    refresh("저장됨");
                })
                .setNeutralButton("삭제", (d, w) -> confirmDeletePerson(person))
                .setNegativeButton("닫기", null)
                .create();
        dialog.show();
    }

    private View buildRelationRow(long personId, long otherId, String otherName, int strength, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView name = labelText(otherName + (label.isEmpty() ? "" : " · " + label) + "  강도 " + strength, 13, false);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameParams);
        row.addView(name);

        row.addView(smallButton("−", v -> {
            database.bumpRelation(personId, otherId, -1);
            refresh("강도 −1");
        }));
        row.addView(smallButton("+", v -> {
            database.bumpRelation(personId, otherId, 1);
            refresh("강도 +1");
        }));
        row.addView(smallButton("✎", v -> editRelationLabel(personId, otherId, label)));
        row.addView(smallButton("✕", v -> confirmDeleteRelation(personId, otherId, otherName)));
        return row;
    }

    private void confirmDeleteRelation(long personId, long otherId, String otherName) {
        new AlertDialog.Builder(this)
                .setTitle("연결 끊기")
                .setMessage(otherName + "와의 관계를 끊을까요?")
                .setPositiveButton("끊기", (d, w) -> {
                    database.deleteRelation(personId, otherId);
                    refresh("연결 끊김");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void editRelationLabel(long personId, long otherId, String current) {
        EditText input = sheetInput("관계 라벨 (예: 동료, 가족, 멘토)", current);
        new AlertDialog.Builder(this)
                .setTitle("관계 라벨")
                .setView(input)
                .setPositiveButton("저장", (d, w) -> {
                    database.setRelationLabel(personId, otherId, input.getText().toString().trim());
                    refresh("라벨 저장");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void confirmDeletePerson(Models.Person person) {
        new AlertDialog.Builder(this)
                .setTitle("삭제")
                .setMessage((person.name.isEmpty() ? "이 사람" : person.name) + "을(를) 삭제할까요? 관련된 관계도 함께 삭제됩니다.")
                .setPositiveButton("삭제", (d, w) -> {
                    database.deletePerson(person.id);
                    selectedPersonId = -1L;
                    refresh("삭제됨");
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showManageSheet() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(20), dp(20), dp(20));
        layout.setBackgroundColor(Color.rgb(15, 23, 42));

        layout.addView(labelText("도구", 16, true));
        layout.addView(sheetActionButton("샘플 데이터 추가", v -> addSampleData()));
        layout.addView(sheetActionButton("권한 다시 요청", v -> requestPermissions(
                new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACT_PERMISSION)));
        layout.addView(sheetActionButton("MD 미리보기", v -> previewMarkdown()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(layout)
                .setNegativeButton("닫기", null)
                .create();
        dialog.show();
    }

    private Button sheetActionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setBackground(roundedFill(Color.rgb(37, 99, 235), dp(12), Color.rgb(96, 165, 250), dp(1)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void previewMarkdown() {
        String md = MarkdownExporter.build(database.listPersons(), database.listRelations());
        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setText(md);
        text.setTextSize(12);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextColor(Color.rgb(226, 232, 240));
        text.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.setBackgroundColor(Color.rgb(15, 23, 42));
        scroll.addView(text);
        new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("내보내기", (d, w) -> startMarkdownExport())
                .setNegativeButton("닫기", null)
                .show();
    }

    private void addSampleData() {
        long minsu = database.upsertContact("민수", "01011112222");
        long jiyeon = database.upsertContact("지연", "01033334444");
        long mom = database.upsertContact("엄마", "01055556666");
        long boss = database.upsertContact("팀장", "01077778888");
        database.updatePerson(minsu, null, "친구", "대학 동기, 자취방 근처", Arrays.asList("대학", "동기"));
        database.updatePerson(jiyeon, null, "직장", "같은 팀 디자이너", Arrays.asList("디자이너"));
        database.updatePerson(mom, null, "가족", "통화 자주", Arrays.asList("부모"));
        database.updatePerson(boss, null, "직장", "팀장, 1on1 격주", Arrays.asList("리더"));
        database.bumpRelation(minsu, jiyeon, 3);
        database.bumpRelation(jiyeon, boss, 4);
        database.bumpRelation(mom, minsu, 1);
        database.bumpRelation(boss, mom, 1);
        refresh("샘플 추가");
    }

    private List<String> splitTags(String input) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String part : input.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private TextView labelText(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(226, 232, 240));
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        view.setPadding(0, dp(8), 0, dp(4));
        return view;
    }

    private EditText sheetInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setTextSize(14);
        input.setTextColor(Color.rgb(241, 245, 249));
        input.setHintTextColor(Color.rgb(100, 116, 139));
        input.setBackground(roundedFill(Color.rgb(11, 16, 30), dp(10), Color.rgb(51, 65, 85), dp(1)));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        input.setLayoutParams(params);
        return input;
    }

    private Button smallButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setBackground(roundedFill(Color.rgb(30, 41, 59), dp(10), Color.rgb(71, 85, 105), dp(1)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(36));
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
