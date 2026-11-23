package org.daidai.modules.ballrace;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 保存一个内置关卡模板的可编辑障碍布局。 */
public final class EditableLevel {
    public static final String FORMAT = "ball-race-editable-level";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_OBSTACLES = 64;
    public static final String CIRCLE = "circle";
    public static final String SLOPE = "slope";
    private static final float MIN_X = 12f;
    private static final float MIN_Y = 16f;
    private static final float MAX_RADIUS = 72f;
    private static final float MIN_SLOPE_LENGTH = 40f;
    private static final float MAX_SLOPE_LENGTH = 300f;
    private static final float MIN_THICKNESS = 6f;
    private static final float MAX_THICKNESS = 36f;
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** 障碍物以中心定位，斜坡的长度沿旋转角方向延伸。 */
    public static final class Obstacle {
        public final String id;
        public final String type;
        public float x;
        public float y;
        public float size;
        public float thickness;
        public float rotationDegrees;

        public Obstacle(String id, String type, float x, float y, float size,
                        float thickness, float rotationDegrees) {
            if (id == null || !VALID_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("障碍物编号无效");
            }
            if (!CIRCLE.equals(type) && !SLOPE.equals(type)) {
                throw new IllegalArgumentException("障碍物类型无效");
            }
            this.id = id;
            this.type = type;
            set(x, y, size, thickness, rotationDegrees);
        }

        public void set(float nextX, float nextY, float nextSize,
                        float nextThickness, float nextRotationDegrees) {
            finite(nextX, "障碍物横坐标");
            finite(nextY, "障碍物纵坐标");
            finite(nextSize, "障碍物尺寸");
            finite(nextThickness, "障碍物厚度");
            finite(nextRotationDegrees, "障碍物旋转角度");
            if (CIRCLE.equals(type)) {
                if (nextSize < 10f || nextSize > MAX_RADIUS) {
                    throw new IllegalArgumentException("圆形障碍半径超出可编辑范围");
                }
                if (nextThickness < 0f || nextThickness > MAX_THICKNESS) {
                    throw new IllegalArgumentException("障碍边缘厚度无效");
                }
            } else if (nextSize < MIN_SLOPE_LENGTH || nextSize > MAX_SLOPE_LENGTH
                    || nextThickness < MIN_THICKNESS || nextThickness > MAX_THICKNESS) {
                throw new IllegalArgumentException("斜坡尺寸超出可编辑范围");
            }
            float rotation = normalizeRotation(nextRotationDegrees);
            if (!fits(nextX, nextY, nextSize, nextThickness, rotation)) {
                throw new IllegalArgumentException("障碍物必须完整位于关卡边界内");
            }
            x = nextX;
            y = nextY;
            size = nextSize;
            thickness = nextThickness;
            rotationDegrees = rotation;
        }

        /** 调整尺寸或拖动时将中心约束在关卡边界内。 */
        public void setClamped(float nextX, float nextY, float nextSize,
                               float nextThickness, float nextRotationDegrees) {
            finite(nextX, "障碍物横坐标");
            finite(nextY, "障碍物纵坐标");
            finite(nextSize, "障碍物尺寸");
            finite(nextThickness, "障碍物厚度");
            finite(nextRotationDegrees, "障碍物旋转角度");
            float rotation = normalizeRotation(nextRotationDegrees);
            float extentX;
            float extentY;
            if (CIRCLE.equals(type)) {
                extentX = nextSize;
                extentY = nextSize;
            } else {
                float radians = (float) Math.toRadians(rotation);
                extentX = Math.abs((float) Math.cos(radians) * nextSize * .5f) + nextThickness * .5f;
                extentY = Math.abs((float) Math.sin(radians) * nextSize * .5f) + nextThickness * .5f;
            }
            float clampedX = clamp(nextX, MIN_X + extentX, RaceCourse.WORLD_WIDTH - MIN_X - extentX);
            float clampedY = clamp(nextY, MIN_Y + extentY, RaceCourse.LEVEL_HEIGHT - MIN_Y - extentY);
            set(clampedX, clampedY, nextSize, nextThickness, rotation);
        }

        public boolean hit(float pointX, float pointY, float tolerance) {
            if (CIRCLE.equals(type)) return distance(pointX, pointY, x, y) <= size + tolerance;
            float radians = (float) Math.toRadians(rotationDegrees);
            float halfX = (float) Math.cos(radians) * size * .5f;
            float halfY = (float) Math.sin(radians) * size * .5f;
            return distanceToSegment(pointX, pointY, x - halfX, y - halfY,
                x + halfX, y + halfY) <= thickness * .5f + tolerance;
        }

        RaceCourse.Obstacle toPhysicsObstacle(float elasticity) {
            if (CIRCLE.equals(type)) return RaceCourse.Obstacle.circle(id, x, y, size, elasticity);
            float radians = (float) Math.toRadians(rotationDegrees);
            float halfX = (float) Math.cos(radians) * size * .5f;
            float halfY = (float) Math.sin(radians) * size * .5f;
            return RaceCourse.Obstacle.segment(id, x - halfX, y - halfY,
                x + halfX, y + halfY, thickness, elasticity);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("type", type).put("x", x).put("y", y)
                .put("size", size).put("thickness", thickness).put("rotationDeg", rotationDegrees);
        }

        static Obstacle fromJson(JSONObject value) throws JSONException {
            try {
                return new Obstacle(value.getString("id"), value.getString("type"),
                    number(value, "x"), number(value, "y"), number(value, "size"),
                    number(value, "thickness"), number(value, "rotationDeg"));
            } catch (IllegalArgumentException invalid) {
                throw new JSONException(invalid.getMessage());
            }
        }

        private boolean fits(float centerX, float centerY, float dimension, float edge, float rotation) {
            if (CIRCLE.equals(type)) {
                return centerX - dimension >= MIN_X && centerX + dimension <= RaceCourse.WORLD_WIDTH - MIN_X
                    && centerY - dimension >= MIN_Y && centerY + dimension <= RaceCourse.LEVEL_HEIGHT - MIN_Y;
            }
            float radians = (float) Math.toRadians(rotation);
            float halfX = (float) Math.cos(radians) * dimension * .5f;
            float halfY = (float) Math.sin(radians) * dimension * .5f;
            float extentX = Math.abs(halfX) + edge * .5f;
            float extentY = Math.abs(halfY) + edge * .5f;
            return centerX - extentX >= MIN_X && centerX + extentX <= RaceCourse.WORLD_WIDTH - MIN_X
                && centerY - extentY >= MIN_Y && centerY + extentY <= RaceCourse.LEVEL_HEIGHT - MIN_Y;
        }
    }

    public final String id;
    public String name;
    private final ArrayList<Obstacle> obstacles;
    private int nextId;

    private EditableLevel(String id, String name, List<Obstacle> obstacles) {
        if (!isBuiltInId(id)) throw new IllegalArgumentException("内置关卡编号无效");
        if (obstacles.size() > MAX_OBSTACLES) throw new IllegalArgumentException("关卡障碍数量超限");
        this.id = id;
        this.name = validName(name, id);
        this.obstacles = new ArrayList<>(obstacles);
        Set<String> ids = new HashSet<>();
        for (Obstacle obstacle : obstacles) {
            if (obstacle == null || !ids.add(obstacle.id)) {
                throw new IllegalArgumentException("关卡障碍编号重复");
            }
            int suffix = numericSuffix(obstacle.id);
            if (suffix >= nextId && suffix < Integer.MAX_VALUE) nextId = suffix + 1;
        }
    }

    public static List<String> builtInIds() {
        ArrayList<String> ids = new ArrayList<>();
        Collections.addAll(ids, "slopes", "bumpers", "rails", "zigzag", "center-pegs");
        return Collections.unmodifiableList(ids);
    }

    public static boolean isBuiltInId(String id) { return id != null && builtInIds().contains(id); }

    public static EditableLevel fromTemplate(RaceCourse.Template template) {
        ArrayList<Obstacle> items = new ArrayList<>();
        for (int index = 0; index < template.obstacles.size(); index++) {
            RaceCourse.Obstacle obstacle = template.obstacles.get(index);
            if (obstacle.shape == RaceCourse.Obstacle.CIRCLE) {
                items.add(new Obstacle("obstacle-" + index, CIRCLE, obstacle.x1, obstacle.y1,
                    obstacle.radius, 0f, 0f));
            } else {
                float dx = obstacle.x2 - obstacle.x1;
                float dy = obstacle.y2 - obstacle.y1;
                items.add(new Obstacle("obstacle-" + index, SLOPE,
                    (obstacle.x1 + obstacle.x2) * .5f, (obstacle.y1 + obstacle.y2) * .5f,
                    (float) Math.hypot(dx, dy), obstacle.thickness,
                    (float) Math.toDegrees(Math.atan2(dy, dx))));
            }
        }
        return new EditableLevel(template.id, template.name, items);
    }

    public static EditableLevel parse(String json, String expectedId) throws JSONException {
        if (json == null || json.length() > 64 * 1024) throw new JSONException("关卡数据长度无效");
        if (!isBuiltInId(expectedId)) throw new JSONException("内置关卡编号无效");
        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format")) || root.optInt("schemaVersion") != SCHEMA_VERSION
                || !expectedId.equals(root.optString("id"))) throw new JSONException("关卡数据版本或编号不匹配");
        JSONArray rows = root.getJSONArray("obstacles");
        if (rows.length() > MAX_OBSTACLES) throw new JSONException("障碍数量超限");
        ArrayList<Obstacle> items = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) items.add(Obstacle.fromJson(rows.getJSONObject(index)));
        try {
        return new EditableLevel(expectedId, root.optString("name", expectedId), items);
        } catch (IllegalArgumentException invalid) {
            throw new JSONException(invalid.getMessage());
        }
    }

    public int size() { return obstacles.size(); }
    public Obstacle get(int index) { return obstacles.get(index); }
    public List<Obstacle> obstacles() { return Collections.unmodifiableList(obstacles); }

    public void rename(String nextName) {
        name = validName(nextName, null);
    }

    public Obstacle add(String type, float x, float y) {
        if (obstacles.size() >= MAX_OBSTACLES) throw new IllegalStateException("关卡障碍数量已达上限");
        if (!CIRCLE.equals(type) && !SLOPE.equals(type)) throw new IllegalArgumentException("障碍物类型无效");
        String objectId = nextAvailableId();
        Obstacle obstacle = CIRCLE.equals(type)
            ? new Obstacle(objectId, CIRCLE, RaceCourse.WORLD_WIDTH * .5f,
                RaceCourse.LEVEL_HEIGHT * .5f, 24f, 0f, 0f)
            : new Obstacle(objectId, SLOPE, RaceCourse.WORLD_WIDTH * .5f,
                RaceCourse.LEVEL_HEIGHT * .5f, 120f, 12f, 0f);
        obstacle.setClamped(x, y, obstacle.size, obstacle.thickness, obstacle.rotationDegrees);
        obstacles.add(obstacle);
        return obstacle;
    }

    public boolean remove(Obstacle obstacle) { return obstacles.remove(obstacle); }

    public RaceCourse.Template toTemplate(float elasticity) {
        RaceCourse.Port entry = new RaceCourse.Port(RaceCourse.Port.TOP, RaceCourse.WORLD_WIDTH * .5f, "vertical_center");
        RaceCourse.Port exit = new RaceCourse.Port(RaceCourse.Port.BOTTOM, RaceCourse.WORLD_WIDTH * .5f, "vertical_center");
        ArrayList<RaceCourse.Obstacle> physics = new ArrayList<>();
        for (Obstacle obstacle : obstacles) physics.add(obstacle.toPhysicsObstacle(elasticity));
        return new RaceCourse.Template(id, name, entry, exit, physics);
    }

    public String toJson() {
        try {
            JSONArray rows = new JSONArray();
            for (Obstacle obstacle : obstacles) rows.put(obstacle.toJson());
            return new JSONObject().put("format", FORMAT).put("schemaVersion", SCHEMA_VERSION)
                .put("id", id).put("name", name).put("obstacles", rows).toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static float number(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Number)) throw new JSONException("障碍尺寸必须是数值");
        float result = ((Number) value).floatValue();
        if (!Float.isFinite(result)) throw new JSONException("障碍尺寸必须是有限数值");
        return result;
    }

    private static float normalizeRotation(float value) {
        float result = value % 360f;
        return result < 0f ? result + 360f : result;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("障碍物尺寸超过关卡范围");
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x1 - x2, y1 - y2);
    }

    private static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        float amount = lengthSquared == 0f ? 0f
            : Math.max(0f, Math.min(1f, ((px - x1) * dx + (py - y1) * dy) / lengthSquared));
        return distance(px, py, x1 + amount * dx, y1 + amount * dy);
    }

    private static int numericSuffix(String id) {
        int split = id.lastIndexOf('-');
        if (split < 0 || split == id.length() - 1) return -1;
        try { return Integer.parseInt(id.substring(split + 1)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static String validName(String value, String fallback) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) result = fallback == null ? "" : fallback;
        if (result.isEmpty() || result.length() > 32) throw new IllegalArgumentException("关卡名称须为 1 到 32 个字符");
        for (int index = 0; index < result.length(); index++) {
            if (Character.isISOControl(result.charAt(index))) throw new IllegalArgumentException("关卡名称不能包含控制字符");
        }
        return result;
    }

    private String nextAvailableId() {
        Set<String> ids = new HashSet<>();
        for (Obstacle obstacle : obstacles) ids.add(obstacle.id);
        for (int attempt = 0; attempt <= MAX_OBSTACLES; attempt++) {
            if (nextId < 0 || nextId == Integer.MAX_VALUE) nextId = 0;
            String candidate = "placed-" + nextId++;
            if (!ids.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("无法分配障碍编号");
    }

    private static void finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + "必须为有限数值");
    }
}
