package com.example.memorygraph;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
    private static final int REQUEST_STARTUP_PERMISSIONS = 101;
    private static final int REQUEST_PICK_CONTACT = 200;
    private static final int REQUEST_SAVE_MD = 300;

    private MemoryDatabase database;
    private MemoryGraphView graphView;
    private TextView headlineView;
    private TextView eventView;
    private TextView statusView;
    private Button connectToggle;
    private long selectedPersonId = -1L;
    private String pendingMarkdown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new MemoryDatabase(this);
        database.ensureSelfPerson();
        setContentView(buildUi());
        refresh("준비 완료");
        requestStartupPermissionsOnce();
    }

    private View buildUi() {
        if (isWideLandscape()) {
            return buildLandscapeUi();
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8, 11, 22));

        root.addView(buildTopBar());
        root.addView(buildEventStrip());

        graphView = createGraphView();
        LinearLayout.LayoutParams graphParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        int margin = dp(12);
        graphParams.setMargins(margin, margin / 2, margin, margin / 2);
        graphView.setLayoutParams(graphParams);
        root.addView(graphView);

        statusView = buildStatusView();
        root.addView(statusView);

        root.addView(buildBottomBar());
        return root;
    }

    private View buildLandscapeUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(8, 11, 22));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.rgb(8, 11, 22));
        panel.setLayoutParams(new LinearLayout.LayoutParams(dp(360), LinearLayout.LayoutParams.MATCH_PARENT));
        panel.addView(buildTopBar());
        panel.addView(buildEventStrip());

        statusView = buildStatusView();
        panel.addView(statusView);

        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.addView(buildBottomBar());

        graphView = createGraphView();
        LinearLayout.LayoutParams graphParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        graphParams.setMargins(0, dp(12), dp(12), dp(12));
        graphView.setLayoutParams(graphParams);

        root.addView(panel);
        root.addView(graphView);
        return root;
    }

    private MemoryGraphView createGraphView() {
        MemoryGraphView view = new MemoryGraphView(this);
        view.setBackground(roundedFill(Color.rgb(11, 16, 30), dp(20), Color.rgb(30, 41, 59), dp(1)));
        view.setListener(new MemoryGraphView.Listener() {
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
                boolean added = database.connectPeople(fromId, toId);
                refresh(added ? "관계 추가: 보통" : "이미 연결된 관계");
            }

            @Override
            public void onEmptyTap() {
                selectedPersonId = -1L;
                graphView.setSelectedPerson(-1L);
            }
        });
        return view;
    }

    private TextView buildStatusView() {
        TextView view = new TextView(this);
        view.setTextColor(Color.rgb(148, 163, 184));
        view.setTextSize(12);
        view.setPadding(dp(16), 0, dp(16), dp(8));
        return view;
    }

    private View buildTopBar() {
        boolean compact = isCompactWidth();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        bar.setGravity(compact ? Gravity.START : Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(16), dp(12), dp(8));
        bar.setBackgroundColor(Color.rgb(8, 11, 22));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                compact ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                compact ? 0f : 1f);
        titles.setLayoutParams(titleParams);

        headlineView = new TextView(this);
        headlineView.setText("Memory Graph");
        headlineView.setTextSize(compact ? 19 : 20);
        headlineView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        headlineView.setTextColor(Color.rgb(241, 245, 249));
        titles.addView(headlineView);

        TextView subtitle = new TextView(this);
        subtitle.setText("연결 후 친밀도를 3단계로 직접 설정하세요");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Color.rgb(148, 163, 184));
        titles.addView(subtitle);

        bar.addView(titles);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                compact ? LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (compact) {
            actionParams.setMargins(0, dp(10), 0, 0);
        }
        actions.setLayoutParams(actionParams);

        connectToggle = iconButton("🔗", v -> toggleConnectMode());
        actions.addView(connectToggle);

        Button exportButton = iconButton("⤓ MD", v -> startMarkdownExport());
        actions.addView(exportButton);

        bar.addView(actions);
        return bar;
    }

    private View buildEventStrip() {
        eventView = new TextView(this);
        eventView.setTextColor(Color.rgb(219, 234, 254));
        eventView.setTextSize(isCompactWidth() ? 12 : 13);
        eventView.setGravity(Gravity.CENTER_VERTICAL);
        eventView.setMaxLines(2);
        eventView.setPadding(dp(14), dp(9), dp(14), dp(9));
        eventView.setBackground(roundedFill(Color.rgb(15, 23, 42), dp(14), Color.rgb(51, 65, 85), dp(1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(16), 0, dp(16), dp(8));
        eventView.setLayoutParams(params);
        return eventView;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(12), dp(8), dp(12), isWideLandscape() ? dp(42) : dp(20));
        bar.setBackgroundColor(Color.rgb(8, 11, 22));

        boolean compact = isCompactWidth();
        bar.addView(actionButton(compact ? "👤 연락처" : "👤  연락처에서", v -> pickContact()));
        bar.addView(actionButton(compact ? "📇 전체" : "📇  전체 가져오기", v -> importAllContacts()));
        bar.addView(actionButton(compact ? "⚙ 관리" : "⚙️  관리", v -> showManageSheet()));
        return bar;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(226, 232, 240));
        button.setTextSize(isCompactWidth() ? 12 : 13);
        button.setBackground(roundedFill(Color.rgb(30, 41, 59), dp(14), Color.rgb(51, 65, 85), dp(1)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params;
        params = new LinearLayout.LayoutParams(0, isCompactWidth() ? dp(48) : dp(52), 1f);
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
        button.setMinWidth(isCompactWidth() ? dp(52) : dp(56));
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

    private void requestStartupPermissionsOnce() {
        SharedPreferences prefs = getSharedPreferences("memory_graph_prefs", MODE_PRIVATE);
        List<String> permissions = new ArrayList<>();
        if (!prefs.getBoolean("contact_permission_requested", false)
                && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS);
            prefs.edit().putBoolean("contact_permission_requested", true).apply();
        }
        if (Build.VERSION.SDK_INT >= 33
                && !prefs.getBoolean("notification_permission_requested", false)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            prefs.edit().putBoolean("notification_permission_requested", true).apply();
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_STARTUP_PERMISSIONS);
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
        pendingMarkdown = buildMarkdown(persons, relations);
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
        database.ensureSelfPerson();
        database.ensureSelfLinks();
        List<Models.Person> persons = database.listPersons();
        List<Models.Relation> relations = database.listRelations();
        graphView.setSnapshot(persons, relations);
        graphView.setSelectedPerson(selectedPersonId);
        updateUpcomingEvents(persons);
        BirthdayReminder.scheduleAll(this, persons);
        statusView.setText(status + " · 사람 " + persons.size() + "명, 관계 " + relations.size() + "개");
    }

    private void updateUpcomingEvents(List<Models.Person> persons) {
        if (eventView == null) {
            return;
        }
        List<BirthdayReminder.UpcomingBirthday> events = BirthdayReminder.upcomingBirthdays(persons, 30, 2);
        if (events.isEmpty()) {
            eventView.setText("다가오는 이벤트 · 등록된 생일 없음");
            eventView.setTextColor(Color.rgb(148, 163, 184));
            return;
        }
        eventView.setTextColor(Color.rgb(219, 234, 254));
        StringBuilder text = new StringBuilder("다가오는 이벤트 · ");
        for (int i = 0; i < events.size(); i++) {
            BirthdayReminder.UpcomingBirthday event = events.get(i);
            if (i > 0) {
                text.append(" · ");
            }
            String name = event.person.name.isEmpty() ? "이름 없음" : event.person.name;
            text.append(name)
                    .append(" 생일 ")
                    .append(event.countdownLabel())
                    .append(" (")
                    .append(event.dateLabel)
                    .append(")");
        }
        eventView.setText(text);
    }

    private Models.Person findPerson(long id) {
        for (Models.Person person : database.listPersons()) {
            if (person.id == id) return person;
        }
        return null;
    }

    private void showPersonSheet(Models.Person person) {
        final AlertDialog[] sheetRef = new AlertDialog[1];
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

        layout.addView(labelText("그룹", 13, false));
        List<String> groups = database.listGroups();
        Spinner groupSpinner = groupSpinner(groups, person.groupName);
        layout.addView(groupSpinner);
        layout.addView(sheetActionButton("그룹 추가", v -> showAddGroupDialog(groups, groupSpinner)));

        layout.addView(labelText("성별", 13, false));
        Spinner genderInput = genderSpinner(person.gender);
        layout.addView(genderInput);

        EditText birthdayInput = sheetInput("생일 (MM-DD 또는 YYYY-MM-DD)", person.birthday);
        layout.addView(birthdayInput);

        EditText tagsInput = sheetInput("태그 (콤마 구분)", TextUtils.join(", ", person.tags));
        layout.addView(tagsInput);

        EditText notesInput = sheetInput("메모 — 에이전트가 읽을 자유 서술", person.notes);
        notesInput.setMinLines(4);
        notesInput.setGravity(Gravity.TOP);
        layout.addView(notesInput);

        List<Models.FieldDefinition> customFields = database.listFieldDefinitions();
        List<String> customValues = database.listFieldValues(person.id);
        List<EditText> customInputs = new ArrayList<>();
        layout.addView(labelText("추가 정보", 14, true));
        for (int i = 0; i < customFields.size(); i++) {
            Models.FieldDefinition field = customFields.get(i);
            layout.addView(labelText(field.label, 12, false));
            EditText input = sheetInput(field.label + " 입력", i < customValues.size() ? customValues.get(i) : "");
            customInputs.add(input);
            layout.addView(input);
        }
        layout.addView(sheetActionButton("항목 추가", v -> showAddFieldDialog(person, sheetRef[0])));

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

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("저장", null)
                .setNegativeButton("닫기", null);
        if (!person.isSelf) {
            builder.setNeutralButton("삭제", (d, w) -> confirmDeletePerson(person));
        }
        AlertDialog dialog = builder.create();
        sheetRef[0] = dialog;
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String rawBirthday = birthdayInput.getText().toString().trim();
            String birthday = BirthdayReminder.normalizeBirthday(rawBirthday);
            if (!rawBirthday.isEmpty() && birthday.isEmpty()) {
                toast("생일은 MM-DD 또는 YYYY-MM-DD 형식으로 입력하세요");
                return;
            }
            database.updatePerson(person.id,
                    nameInput.getText().toString().trim(),
                    groupValue(groups, groupSpinner),
                    genderValue(genderInput),
                    birthday,
                    notesInput.getText().toString(),
                    splitTags(tagsInput.getText().toString()));
            List<String> nextCustomValues = new ArrayList<>();
            for (EditText input : customInputs) {
                nextCustomValues.add(input.getText().toString());
            }
            database.updateFieldValues(person.id, customFields, nextCustomValues);
            refresh("저장됨");
            dialog.dismiss();
        });
    }

    private View buildRelationRow(long personId, long otherId, String otherName, int strength, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView name = labelText(otherName + (label.isEmpty() ? "" : " · " + label)
                + "  " + MemoryDatabase.strengthLabel(strength), 13, false);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameParams);
        row.addView(name);

        row.addView(strengthButton("약", strength == 1, v -> {
            database.setRelationStrength(personId, otherId, 1);
            refresh("관계 강도: 약함");
        }));
        row.addView(strengthButton("중", strength == 2, v -> {
            database.setRelationStrength(personId, otherId, 2);
            refresh("관계 강도: 보통");
        }));
        row.addView(strengthButton("강", strength == 3, v -> {
            database.setRelationStrength(personId, otherId, 3);
            refresh("관계 강도: 강함");
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

    private void showAddFieldDialog(Models.Person reopenPerson, AlertDialog currentDialog) {
        EditText input = sheetInput("새 항목 이름 (예: 직업, MBTI, 사는 곳)", "");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("정보 항목 추가")
                .setView(input)
                .setPositiveButton("추가", null)
                .setNegativeButton("취소", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                long id = database.addFieldDefinition(input.getText().toString());
                if (id <= 0) {
                    toast("항목 이름을 입력하세요");
                    return;
                }
                dialog.dismiss();
                refresh("정보 항목 추가됨");
                if (currentDialog != null) {
                    currentDialog.dismiss();
                }
                if (reopenPerson != null) {
                    Models.Person latest = findPerson(reopenPerson.id);
                    if (latest != null) {
                        showPersonSheet(latest);
                    }
                }
            });
        });
        dialog.show();
        input.requestFocus();
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
        layout.addView(sheetActionButton("정보 항목 추가", v -> showAddFieldDialog(null, null)));
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
        String md = buildMarkdown(database.listPersons(), database.listRelations());
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

    private String buildMarkdown(List<Models.Person> persons, List<Models.Relation> relations) {
        List<Models.FieldDefinition> fields = database.listFieldDefinitions();
        Map<Long, List<String>> fieldValues = new HashMap<>();
        for (Models.Person person : persons) {
            fieldValues.put(person.id, database.listFieldValues(person.id));
        }
        return MarkdownExporter.build(persons, relations, fields, fieldValues);
    }

    private void addSampleData() {
        long self = database.ensureSelfPerson();
        long minsu = database.upsertContact("민수", "01011112222");
        long jiyeon = database.upsertContact("지연", "01033334444");
        long mom = database.upsertContact("엄마", "01055556666");
        long boss = database.upsertContact("팀장", "01077778888");
        database.updatePerson(minsu, null, "친구", "male", "05-10",
                "대학 동기, 자취방 근처", Arrays.asList("대학", "동기"));
        database.updatePerson(jiyeon, null, "직장", "female", "05-18",
                "같은 팀 디자이너", Arrays.asList("디자이너"));
        database.updatePerson(mom, null, "가족", "female", "06-01",
                "통화 자주", Arrays.asList("부모"));
        database.updatePerson(boss, null, "직장", "unspecified", "12-12",
                "팀장, 1on1 격주", Arrays.asList("리더"));
        database.setRelationStrength(minsu, jiyeon, 3);
        database.setRelationLabel(minsu, jiyeon, "협업");
        database.setRelationStrength(jiyeon, boss, 3);
        database.setRelationLabel(jiyeon, boss, "리포팅");
        database.setRelationStrength(mom, minsu, 2);
        database.setRelationLabel(mom, minsu, "가족");
        database.setRelationStrength(boss, mom, 1);
        database.setRelationLabel(boss, mom, "느슨한 관계");
        database.setRelationStrength(self, minsu, 3);
        database.setRelationLabel(self, minsu, "친한 친구");
        database.setRelationStrength(self, jiyeon, 2);
        database.setRelationLabel(self, jiyeon, "같은 팀");
        database.setRelationStrength(self, mom, 3);
        database.setRelationLabel(self, mom, "가족");
        database.setRelationStrength(self, boss, 2);
        database.setRelationLabel(self, boss, "업무");

        database.addFieldDefinition("직업/역할");
        database.addFieldDefinition("사는 곳");
        database.addFieldDefinition("좋아하는 것");
        List<Models.FieldDefinition> fields = database.listFieldDefinitions();
        updateSampleFields(minsu, fields, "개발자", "서울 서쪽", "러닝, 커피");
        updateSampleFields(jiyeon, fields, "디자이너", "성수", "전시, 사진");
        updateSampleFields(mom, fields, "가족", "부산", "꽃, 산책");
        updateSampleFields(boss, fields, "팀장", "판교", "프로젝트 일정");
        refresh("샘플 추가");
    }

    private void updateSampleFields(long personId, List<Models.FieldDefinition> fields,
                                    String role, String location, String likes) {
        List<String> values = new ArrayList<>();
        for (Models.FieldDefinition field : fields) {
            if ("직업/역할".equals(field.label)) {
                values.add(role);
            } else if ("사는 곳".equals(field.label)) {
                values.add(location);
            } else if ("좋아하는 것".equals(field.label)) {
                values.add(likes);
            } else {
                values.add("");
            }
        }
        database.updateFieldValues(personId, fields, values);
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

    private Button strengthButton(String label, boolean selected, View.OnClickListener listener) {
        Button button = smallButton(label, listener);
        button.setBackground(roundedFill(
                selected ? Color.rgb(14, 165, 233) : Color.rgb(30, 41, 59),
                dp(10),
                selected ? Color.rgb(125, 211, 252) : Color.rgb(71, 85, 105),
                dp(1)));
        return button;
    }

    private Spinner genderSpinner(String gender) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"미지정", "여성", "남성", "기타"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(genderIndex(gender));
        spinner.setBackground(roundedFill(Color.rgb(11, 16, 30), dp(10), Color.rgb(51, 65, 85), dp(1)));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(0, dp(4), 0, dp(8));
        spinner.setLayoutParams(params);
        return spinner;
    }

    private int genderIndex(String gender) {
        if ("female".equals(gender)) return 1;
        if ("male".equals(gender)) return 2;
        if ("other".equals(gender)) return 3;
        return 0;
    }

    private String genderValue(Spinner spinner) {
        int position = spinner.getSelectedItemPosition();
        if (position == 1) return "female";
        if (position == 2) return "male";
        if (position == 3) return "other";
        return "unspecified";
    }

    private Spinner groupSpinner(List<String> groups, String selected) {
        Spinner spinner = new Spinner(this);
        List<String> values = new ArrayList<>();
        if (groups != null) {
            values.addAll(groups);
        }
        String current = selected == null ? "" : selected.trim();
        if (!current.isEmpty() && !values.contains(current)) {
            values.add(0, current);
        }
        if (values.isEmpty()) {
            values.add("친구");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                values.toArray(new String[0]));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, values.indexOf(current.isEmpty() ? "친구" : current)));
        spinner.setBackground(roundedFill(Color.rgb(11, 16, 30), dp(10), Color.rgb(51, 65, 85), dp(1)));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        params.setMargins(0, dp(4), 0, dp(8));
        spinner.setLayoutParams(params);
        return spinner;
    }

    private String groupValue(List<String> groups, Spinner spinner) {
        Object item = spinner.getSelectedItem();
        String selected = item == null ? "" : item.toString().trim();
        if (selected.isEmpty()) {
            return "친구";
        }
        database.addGroup(selected);
        return selected;
    }

    private void showAddGroupDialog(List<String> groups, Spinner spinner) {
        EditText input = sheetInput("새 그룹 이름 (예: 군대, 동호회)", "");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("그룹 추가")
                .setView(input)
                .setPositiveButton("추가", null)
                .setNegativeButton("취소", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String label = input.getText().toString().trim();
            long id = database.addGroup(label);
            if (id <= 0) {
                toast("그룹 이름을 입력하세요");
                return;
            }
            if (!groups.contains(label)) {
                groups.add(label);
            }
            ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
            adapter.add(label);
            adapter.notifyDataSetChanged();
            spinner.setSelection(adapter.getPosition(label));
            dialog.dismiss();
        }));
        dialog.show();
        input.requestFocus();
    }

    private boolean isCompactWidth() {
        Configuration configuration = getResources().getConfiguration();
        return isWideLandscape() || (configuration.screenWidthDp > 0 && configuration.screenWidthDp < 420);
    }

    private boolean isWideLandscape() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                && configuration.screenWidthDp >= 600;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
