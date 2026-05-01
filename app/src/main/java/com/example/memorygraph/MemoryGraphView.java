package com.example.memorygraph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class MemoryGraphView extends View {

    interface Listener {
        void onPersonTap(long personId);
        void onConnect(long fromId, long toId);
        void onEmptyTap();
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dragPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dashPath = new Path();

    private final Map<Long, PointF> positions = new HashMap<>();
    private final Map<Long, Integer> intimacyByPerson = new HashMap<>();
    private List<Models.Person> persons = new ArrayList<>();
    private List<Models.Relation> relations = new ArrayList<>();

    private Listener listener;
    private boolean connectMode;
    private long selectedPersonId = -1L;

    private long pressedPersonId = -1L;
    private float pressStartX;
    private float pressStartY;
    private float pointerX;
    private float pointerY;
    private boolean dragging;
    private final int touchSlop;

    MemoryGraphView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setupPaints();
    }

    private void setupPaints() {
        backgroundPaint.setColor(Color.rgb(8, 11, 22));
        gridPaint.setColor(Color.argb(28, 100, 116, 139));
        gridPaint.setStrokeWidth(dp(0.6f));

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeCap(Paint.Cap.ROUND);

        dragPaint.setStyle(Paint.Style.STROKE);
        dragPaint.setStrokeWidth(dp(2.2f));
        dragPaint.setColor(Color.rgb(125, 211, 252));
        dragPaint.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, 0));

        glowPaint.setStyle(Paint.Style.FILL);
        nodePaint.setStyle(Paint.Style.FILL);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.6f));

        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(2.2f));
        selectionPaint.setColor(Color.rgb(250, 204, 21));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(Color.rgb(226, 232, 240));

        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setColor(Color.rgb(100, 116, 139));
        emptyPaint.setTextSize(dp(14));

        hudPaint.setStyle(Paint.Style.FILL);
        hudPaint.setColor(Color.argb(180, 14, 165, 233));
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setConnectMode(boolean connectMode) {
        this.connectMode = connectMode;
        invalidate();
    }

    boolean isConnectMode() {
        return connectMode;
    }

    void setSelectedPerson(long personId) {
        this.selectedPersonId = personId;
        invalidate();
    }

    void setSnapshot(List<Models.Person> persons, List<Models.Relation> relations) {
        this.persons = persons == null ? new ArrayList<>() : persons;
        this.relations = relations == null ? new ArrayList<>() : relations;
        recomputeIntimacy();
        ensurePositions();
        layoutForces(160);
        invalidate();
    }

    private void recomputeIntimacy() {
        intimacyByPerson.clear();
        for (Models.Relation relation : relations) {
            intimacyByPerson.merge(relation.personA, relation.strength, Integer::sum);
            intimacyByPerson.merge(relation.personB, relation.strength, Integer::sum);
        }
    }

    private void ensurePositions() {
        int width = Math.max(getWidth(), dp(320));
        int height = Math.max(getHeight(), dp(420));
        Map<Long, PointF> next = new LinkedHashMap<>();
        Random random = new Random(42);
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(width, height) * 0.32f;
        int index = 0;
        for (Models.Person person : persons) {
            PointF existing = positions.get(person.id);
            if (existing != null) {
                next.put(person.id, existing);
            } else {
                double angle = (index * 2 * Math.PI / Math.max(1, persons.size())) + random.nextDouble() * 0.4;
                float x = cx + (float) Math.cos(angle) * (radius + random.nextInt(40));
                float y = cy + (float) Math.sin(angle) * (radius + random.nextInt(40));
                next.put(person.id, new PointF(x, y));
            }
            index++;
        }
        positions.clear();
        positions.putAll(next);
    }

    private void layoutForces(int iterations) {
        if (persons.size() < 2 || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        float width = getWidth();
        float height = getHeight();
        float area = width * height;
        float k = (float) Math.sqrt(area / persons.size()) * 0.55f;
        Map<Long, PointF> velocities = new HashMap<>();
        for (Long id : positions.keySet()) {
            velocities.put(id, new PointF(0, 0));
        }

        for (int iter = 0; iter < iterations; iter++) {
            float t = (1.0f - (iter / (float) iterations)) * dp(8);
            for (Models.Person a : persons) {
                PointF pa = positions.get(a.id);
                PointF va = velocities.get(a.id);
                if (pa == null || va == null) continue;
                va.set(0, 0);
                for (Models.Person b : persons) {
                    if (a.id == b.id) continue;
                    PointF pb = positions.get(b.id);
                    if (pb == null) continue;
                    float dx = pa.x - pb.x;
                    float dy = pa.y - pb.y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
                    float repulse = (k * k) / dist;
                    va.x += (dx / dist) * repulse;
                    va.y += (dy / dist) * repulse;
                }
            }
            for (Models.Relation relation : relations) {
                PointF pa = positions.get(relation.personA);
                PointF pb = positions.get(relation.personB);
                if (pa == null || pb == null) continue;
                float dx = pa.x - pb.x;
                float dy = pa.y - pb.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
                float pull = (dist * dist) / k;
                pull *= 0.5f + relation.strength * 0.18f;
                PointF va = velocities.get(relation.personA);
                PointF vb = velocities.get(relation.personB);
                if (va != null) {
                    va.x -= (dx / dist) * pull;
                    va.y -= (dy / dist) * pull;
                }
                if (vb != null) {
                    vb.x += (dx / dist) * pull;
                    vb.y += (dy / dist) * pull;
                }
            }
            for (Map.Entry<Long, PointF> entry : positions.entrySet()) {
                PointF p = entry.getValue();
                PointF v = velocities.get(entry.getKey());
                if (v == null) continue;
                float speed = (float) Math.sqrt(v.x * v.x + v.y * v.y) + 0.01f;
                float capped = Math.min(speed, t);
                p.x += (v.x / speed) * capped;
                p.y += (v.y / speed) * capped;
                p.x = clamp(p.x, dp(40), width - dp(40));
                p.y = clamp(p.y, dp(40), height - dp(40));
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        ensurePositions();
        layoutForces(120);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(backgroundPaint.getColor());
        drawGrid(canvas);

        if (persons.isEmpty()) {
            canvas.drawText("연락처에서 사람을 추가해보세요", getWidth() / 2f, getHeight() / 2f, emptyPaint);
            drawConnectHud(canvas);
            return;
        }

        for (Models.Relation relation : relations) {
            PointF pa = positions.get(relation.personA);
            PointF pb = positions.get(relation.personB);
            if (pa == null || pb == null) continue;
            float thickness = dp(1.2f) + dp(0.7f) * relation.strength;
            int alpha = Math.min(255, 140 + relation.strength * 12);
            edgePaint.setStrokeWidth(thickness);
            edgePaint.setColor(edgeColorFor(relation.strength));
            edgePaint.setAlpha(alpha);
            canvas.drawLine(pa.x, pa.y, pb.x, pb.y, edgePaint);
            if (relation.strength >= 6) {
                Paint halo = new Paint(edgePaint);
                halo.setStrokeWidth(thickness + dp(6));
                halo.setAlpha(60);
                canvas.drawLine(pa.x, pa.y, pb.x, pb.y, halo);
            }
        }

        if (dragging && pressedPersonId >= 0) {
            PointF source = positions.get(pressedPersonId);
            if (source != null) {
                dashPath.reset();
                dashPath.moveTo(source.x, source.y);
                dashPath.lineTo(pointerX, pointerY);
                canvas.drawPath(dashPath, dragPaint);
            }
        }

        for (Models.Person person : persons) {
            PointF p = positions.get(person.id);
            if (p == null) continue;
            int score = intimacyByPerson.getOrDefault(person.id, 0);
            float radius = dp(22) + Math.min(dp(18), score * dp(1.4f));
            int color = nodeColorFor(score);

            glowPaint.setColor(withAlpha(color, 95));
            canvas.drawCircle(p.x, p.y, radius + dp(16), glowPaint);
            glowPaint.setColor(withAlpha(color, 55));
            canvas.drawCircle(p.x, p.y, radius + dp(28), glowPaint);

            nodePaint.setColor(color);
            canvas.drawCircle(p.x, p.y, radius, nodePaint);

            ringPaint.setColor(withAlpha(brighten(color, 0.45f), 255));
            canvas.drawCircle(p.x, p.y, radius, ringPaint);

            if (person.id == selectedPersonId) {
                canvas.drawCircle(p.x, p.y, radius + dp(7), selectionPaint);
            }
            if (person.id == pressedPersonId && dragging && connectMode) {
                Paint hot = new Paint(selectionPaint);
                hot.setColor(Color.rgb(125, 211, 252));
                canvas.drawCircle(p.x, p.y, radius + dp(8), hot);
            }

            String initial = person.name == null || person.name.isEmpty()
                    ? "?" : person.name.substring(0, Math.min(2, person.name.length()));
            labelPaint.setTextSize(radius * 0.72f);
            labelPaint.setColor(Color.WHITE);
            labelPaint.setShadowLayer(dp(2), 0, dp(1), Color.argb(160, 0, 0, 0));
            canvas.drawText(initial, p.x, p.y + radius * 0.25f, labelPaint);
            labelPaint.clearShadowLayer();

            labelPaint.setTextSize(dp(12));
            labelPaint.setColor(Color.rgb(241, 245, 249));
            String full = person.name == null || person.name.isEmpty() ? "이름 없음" : person.name;
            canvas.drawText(ellipsize(full, 8), p.x, p.y + radius + dp(18), labelPaint);
            if (score > 0) {
                labelPaint.setTextSize(dp(10));
                labelPaint.setColor(Color.rgb(148, 163, 184));
                canvas.drawText("친밀도 " + score, p.x, p.y + radius + dp(31), labelPaint);
            }
        }

        drawConnectHud(canvas);
    }

    private void drawGrid(Canvas canvas) {
        int step = dp(36);
        for (int x = 0; x <= getWidth(); x += step) {
            canvas.drawLine(x, 0, x, getHeight(), gridPaint);
        }
        for (int y = 0; y <= getHeight(); y += step) {
            canvas.drawLine(0, y, getWidth(), y, gridPaint);
        }
    }

    private void drawConnectHud(Canvas canvas) {
        if (!connectMode) return;
        hudPaint.setColor(Color.argb(220, 14, 165, 233));
        float pad = dp(10);
        float w = dp(150);
        float h = dp(28);
        canvas.drawRoundRect(getWidth() - w - pad, pad, getWidth() - pad, pad + h, dp(14), dp(14), hudPaint);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(12));
        labelPaint.setColor(Color.WHITE);
        canvas.drawText("연결 모드 — 노드를 끌어서 연결",
                getWidth() - w - pad + w / 2f, pad + h * 0.66f, labelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                pressStartX = event.getX();
                pressStartY = event.getY();
                pointerX = pressStartX;
                pointerY = pressStartY;
                pressedPersonId = hitTest(pressStartX, pressStartY);
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                pointerX = event.getX();
                pointerY = event.getY();
                if (!dragging) {
                    float dx = pointerX - pressStartX;
                    float dy = pointerY - pressStartY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        dragging = true;
                        if (connectMode && pressedPersonId >= 0) {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        }
                    }
                }
                if (dragging && connectMode && pressedPersonId >= 0) {
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!dragging) {
                    if (pressedPersonId >= 0 && listener != null) {
                        listener.onPersonTap(pressedPersonId);
                    } else if (listener != null) {
                        listener.onEmptyTap();
                    }
                } else if (connectMode && pressedPersonId >= 0) {
                    long target = hitTest(event.getX(), event.getY());
                    if (target >= 0 && target != pressedPersonId && listener != null) {
                        listener.onConnect(pressedPersonId, target);
                    }
                }
                pressedPersonId = -1L;
                dragging = false;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                pressedPersonId = -1L;
                dragging = false;
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private long hitTest(float x, float y) {
        long hit = -1L;
        float bestDist = Float.MAX_VALUE;
        for (Models.Person person : persons) {
            PointF p = positions.get(person.id);
            if (p == null) continue;
            int score = intimacyByPerson.getOrDefault(person.id, 0);
            float radius = dp(22) + Math.min(dp(14), score * dp(1.2f)) + dp(10);
            float dx = p.x - x;
            float dy = p.y - y;
            float dist = dx * dx + dy * dy;
            if (dist < radius * radius && dist < bestDist) {
                bestDist = dist;
                hit = person.id;
            }
        }
        return hit;
    }

    private int nodeColorFor(int intimacy) {
        float t = Math.max(0f, Math.min(1f, intimacy / 18f));
        int low = Color.rgb(96, 165, 250);
        int mid = Color.rgb(168, 85, 247);
        int high = Color.rgb(236, 72, 153);
        if (t < 0.5f) {
            return blend(low, mid, t * 2f);
        }
        return blend(mid, high, (t - 0.5f) * 2f);
    }

    private int edgeColorFor(int strength) {
        float t = Math.max(0f, Math.min(1f, strength / 10f));
        int low = Color.rgb(125, 211, 252);
        int mid = Color.rgb(168, 85, 247);
        int high = Color.rgb(236, 72, 153);
        if (t < 0.5f) {
            return blend(low, mid, t * 2f);
        }
        return blend(mid, high, (t - 0.5f) * 2f);
    }

    private int blend(int colorA, int colorB, float t) {
        int r = (int) (Color.red(colorA) * (1 - t) + Color.red(colorB) * t);
        int g = (int) (Color.green(colorA) * (1 - t) + Color.green(colorB) * t);
        int b = (int) (Color.blue(colorA) * (1 - t) + Color.blue(colorB) * t);
        return Color.rgb(r, g, b);
    }

    private int brighten(int color, float amount) {
        int r = Math.min(255, (int) (Color.red(color) + 255 * amount));
        int g = Math.min(255, (int) (Color.green(color) + 255 * amount));
        int b = Math.min(255, (int) (Color.blue(color) + 255 * amount));
        return Color.rgb(r, g, b);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private String ellipsize(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
