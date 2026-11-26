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
    public static final int SCHEMA_VERSION = 3;
    public static final int MAX_OBSTACLES = 64;
    public static final String CIRCLE = "circle";
    public static final String ELLIPSE = "ellipse";
    public static final String SLOPE = "slope";
    public static final String ARC = "arc";
    public static final String TEXT = "text";
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
        /** 椭圆的纵向半径；圆形和其他类型与 size 相同。 */
        public float radiusY;
        public float thickness;
        public float rotationDegrees;
        public float elasticity = .7f;
        public float friction = .35f;
        public int color = 0xFF596C83;
        public boolean visible = true;
        public boolean collisionEnabled = true;
        public float motionX;
        public float motionY;
        public float motionPeriodSeconds = 1f;
        public float motionPhaseRadians;
        public float pivotOffsetX;
        public float pivotOffsetY;
        public float rotationSpeedDegreesPerSecond;
        public float collisionImpulseX;
        public float collisionImpulseY;
        public int collisionAction = RaceCourse.Obstacle.ACTION_BOUNCE;
        public float teleportX;
        public float teleportY;
        public float actionCooldownSeconds = .35f;
        public float arcDegrees = 180f;
        public boolean rounded = true;
        public String text = "文字";
        public int textColor = 0xFF20334D;
        private float boundsWidth = RaceCourse.WORLD_WIDTH;
        private float boundsHeight = RaceCourse.LEVEL_HEIGHT;

        public Obstacle(String id, String type, float x, float y, float size,
                        float thickness, float rotationDegrees) {
            if (id == null || !VALID_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("障碍物编号无效");
            }
            if (!CIRCLE.equals(type) && !ELLIPSE.equals(type) && !SLOPE.equals(type)
                    && !ARC.equals(type) && !TEXT.equals(type)) {
                throw new IllegalArgumentException("障碍物类型无效");
            }
            this.id = id;
            this.type = type;
            this.radiusY = CIRCLE.equals(type) ? size : size;
            set(x, y, size, thickness, rotationDegrees);
        }

        public void set(float nextX, float nextY, float nextSize,
                        float nextThickness, float nextRotationDegrees) {
            finite(nextX, "障碍物横坐标");
            finite(nextY, "障碍物纵坐标");
            finite(nextSize, "障碍物尺寸");
            finite(nextThickness, "障碍物厚度");
            finite(nextRotationDegrees, "障碍物旋转角度");
            if (CIRCLE.equals(type) || ELLIPSE.equals(type) || ARC.equals(type)) {
                if (nextSize < 10f || nextSize > MAX_RADIUS) {
                    throw new IllegalArgumentException("圆形或圆弧半径超出可编辑范围");
                }
                if (nextThickness < 0f || nextThickness > MAX_THICKNESS) {
                    throw new IllegalArgumentException("障碍边缘厚度无效");
                }
                if (ELLIPSE.equals(type) && (radiusY < 10f || radiusY > MAX_RADIUS)) {
                    throw new IllegalArgumentException("椭圆纵向半径超出可编辑范围");
                }
            } else if (TEXT.equals(type)) {
                if (nextSize < 8f || nextSize > 96f) throw new IllegalArgumentException("文字大小超出可编辑范围");
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
            setClamped(nextX, nextY, nextSize, nextThickness, nextRotationDegrees, radiusY);
        }

        public void setClamped(float nextX, float nextY, float nextSize,
                               float nextThickness, float nextRotationDegrees, float nextRadiusY) {
            finite(nextX, "障碍物横坐标");
            finite(nextY, "障碍物纵坐标");
            finite(nextSize, "障碍物尺寸");
            finite(nextThickness, "障碍物厚度");
            finite(nextRotationDegrees, "障碍物旋转角度");
            finite(nextRadiusY, "椭圆纵向半径");
            if (ELLIPSE.equals(type) && (nextRadiusY < 10f || nextRadiusY > MAX_RADIUS)) {
                throw new IllegalArgumentException("椭圆纵向半径超出可编辑范围");
            }
            float previousRadiusY = radiusY;
            try {
                radiusY = nextRadiusY;
                float rotation = normalizeRotation(nextRotationDegrees);
                float extentX;
                float extentY;
                if (CIRCLE.equals(type)) {
                    extentX = nextSize;
                    extentY = nextSize;
                } else if (ELLIPSE.equals(type)) {
                    float radians = (float) Math.toRadians(rotation);
                    extentX = (float) Math.sqrt(square(nextSize * (float) Math.cos(radians))
                        + square(nextRadiusY * (float) Math.sin(radians)));
                    extentY = (float) Math.sqrt(square(nextSize * (float) Math.sin(radians))
                        + square(nextRadiusY * (float) Math.cos(radians)));
                } else if (ARC.equals(type)) {
                    extentX = nextSize + nextThickness * .5f;
                    extentY = nextSize + nextThickness * .5f;
                } else if (TEXT.equals(type)) {
                    extentX = nextSize * 2f;
                    extentY = nextSize;
                } else {
                    float radians = (float) Math.toRadians(rotation);
                    extentX = Math.abs((float) Math.cos(radians) * nextSize * .5f) + nextThickness * .5f;
                    extentY = Math.abs((float) Math.sin(radians) * nextSize * .5f) + nextThickness * .5f;
                }
                float clampedX = clamp(nextX, MIN_X + extentX, boundsWidth - MIN_X - extentX);
                float clampedY = clamp(nextY, MIN_Y + extentY, boundsHeight - MIN_Y - extentY);
                set(clampedX, clampedY, nextSize, nextThickness, rotation);
            } catch (IllegalArgumentException invalid) {
                radiusY = previousRadiusY;
                throw invalid;
            }
        }

        void setBounds(float width, float height) {
            boundsWidth = width;
            boundsHeight = height;
            set(x, y, size, thickness, rotationDegrees);
        }

        public void setRadiusY(float nextRadiusY) {
            finite(nextRadiusY, "椭圆纵向半径");
            if (!ELLIPSE.equals(type) || nextRadiusY < 10f || nextRadiusY > MAX_RADIUS) {
                throw new IllegalArgumentException("椭圆纵向半径超出可编辑范围");
            }
            float oldRadiusY = radiusY;
            radiusY = nextRadiusY;
            try {
                if (!fits(x, y, size, thickness, rotationDegrees)) throw new IllegalArgumentException("椭圆必须完整位于关卡边界内");
            } catch (IllegalArgumentException invalid) {
                radiusY = oldRadiusY;
                throw invalid;
            }
        }

        public boolean hit(float pointX, float pointY, float tolerance) {
            if (CIRCLE.equals(type)) return distance(pointX, pointY, x, y) <= size + tolerance;
            if (ELLIPSE.equals(type)) {
                float dx = pointX - x;
                float dy = pointY - y;
                float radians = (float) Math.toRadians(-rotationDegrees);
                float localX = dx * (float) Math.cos(radians) - dy * (float) Math.sin(radians);
                float localY = dx * (float) Math.sin(radians) + dy * (float) Math.cos(radians);
                float rx = Math.max(1f, size + tolerance);
                float ry = Math.max(1f, radiusY + tolerance);
                return localX * localX / (rx * rx) + localY * localY / (ry * ry) <= 1f;
            }
            if (ARC.equals(type)) {
                float dx = pointX - x, dy = pointY - y;
                float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                float delta = Math.abs(((angle - rotationDegrees + 540f) % 360f) - 180f);
                return delta <= arcDegrees * .5f + 12f
                    && Math.abs((float) Math.hypot(dx, dy) - size) <= Math.max(10f, thickness * .5f + tolerance);
            }
            if (TEXT.equals(type)) return Math.abs(pointX - x) <= size * 2f + tolerance
                && Math.abs(pointY - y) <= size + tolerance;
            float radians = (float) Math.toRadians(rotationDegrees);
            float halfX = (float) Math.cos(radians) * size * .5f;
            float halfY = (float) Math.sin(radians) * size * .5f;
            return distanceToSegment(pointX, pointY, x - halfX, y - halfY,
                x + halfX, y + halfY) <= thickness * .5f + tolerance;
        }

        RaceCourse.Obstacle toPhysicsObstacle(float elasticity) {
            validateRuntimeProperties(boundsWidth, boundsHeight);
            float effective = Math.max(0f, Math.min(1f, elasticity * this.elasticity));
            if (CIRCLE.equals(type)) return RaceCourse.Obstacle.circle(id, x, y, size, effective)
                .withProperties(radiusY, friction, color, visible, collisionEnabled, motionX, motionY,
                    motionPeriodSeconds, motionPhaseRadians, pivotOffsetX, pivotOffsetY,
                    rotationSpeedDegreesPerSecond, collisionImpulseX, collisionImpulseY,
                    collisionAction, teleportX, teleportY, actionCooldownSeconds);
            if (ELLIPSE.equals(type)) return RaceCourse.Obstacle.ellipse(id, x, y, size, radiusY,
                rotationDegrees, effective)
                .withProperties(radiusY, friction, color, visible, collisionEnabled, motionX, motionY,
                    motionPeriodSeconds, motionPhaseRadians, pivotOffsetX, pivotOffsetY,
                    rotationSpeedDegreesPerSecond, collisionImpulseX, collisionImpulseY,
                    collisionAction, teleportX, teleportY, actionCooldownSeconds);
            if (TEXT.equals(type)) return null;
            if (ARC.equals(type)) return RaceCourse.Obstacle.arc(id, x, y, size, thickness,
                arcDegrees, rotationDegrees, effective, rounded).withProperties(radiusY, friction, color,
                    visible, collisionEnabled, motionX, motionY, motionPeriodSeconds, motionPhaseRadians,
                    pivotOffsetX, pivotOffsetY, rotationSpeedDegreesPerSecond, collisionImpulseX,
                    collisionImpulseY, collisionAction, teleportX, teleportY, actionCooldownSeconds);
            float radians = (float) Math.toRadians(rotationDegrees);
            float halfX = (float) Math.cos(radians) * size * .5f;
            float halfY = (float) Math.sin(radians) * size * .5f;
            return RaceCourse.Obstacle.segment(id, x - halfX, y - halfY,
                x + halfX, y + halfY, thickness, effective, rounded).withProperties(radiusY, friction,
                    color, visible, collisionEnabled, motionX, motionY, motionPeriodSeconds,
                    motionPhaseRadians, pivotOffsetX, pivotOffsetY, rotationSpeedDegreesPerSecond,
                    collisionImpulseX, collisionImpulseY, collisionAction, teleportX, teleportY,
                    actionCooldownSeconds);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("type", type).put("x", x).put("y", y)
                .put("size", size).put("thickness", thickness).put("rotationDeg", rotationDegrees)
                .put("elasticity", elasticity).put("arcDegrees", arcDegrees).put("rounded", rounded)
                .put("radiusY", radiusY).put("friction", friction).put("color", color)
                .put("visible", visible).put("collisionEnabled", collisionEnabled)
                .put("motionX", motionX).put("motionY", motionY)
                .put("motionPeriodSeconds", motionPeriodSeconds).put("motionPhaseRadians", motionPhaseRadians)
                .put("pivotOffsetX", pivotOffsetX).put("pivotOffsetY", pivotOffsetY)
                .put("rotationSpeedDegreesPerSecond", rotationSpeedDegreesPerSecond)
                .put("collisionImpulseX", collisionImpulseX).put("collisionImpulseY", collisionImpulseY)
                .put("collisionAction", collisionAction).put("teleportX", teleportX)
                .put("teleportY", teleportY).put("actionCooldownSeconds", actionCooldownSeconds)
                .put("text", text).put("textColor", textColor);
        }

        static Obstacle fromJson(JSONObject value, float width, float height) throws JSONException {
            try {
                Obstacle obstacle = new Obstacle(value.getString("id"), value.getString("type"),
                    number(value, "x"), number(value, "y"), number(value, "size"),
                    number(value, "thickness"), number(value, "rotationDeg"));
                obstacle.setBounds(width, height);
                obstacle.elasticity = Math.max(0f, Math.min(1f, (float) value.optDouble("elasticity", .7f)));
                obstacle.radiusY = Math.max(10f, Math.min(MAX_RADIUS,
                    (float) value.optDouble("radiusY", obstacle.size)));
                obstacle.friction = Math.max(0f, Math.min(1f, (float) value.optDouble("friction", .35f)));
                obstacle.color = value.optInt("color", 0xFF596C83);
                obstacle.visible = value.optBoolean("visible", true);
                obstacle.collisionEnabled = value.optBoolean("collisionEnabled", true);
                obstacle.motionX = finiteOptional(value, "motionX", 0f);
                obstacle.motionY = finiteOptional(value, "motionY", 0f);
                obstacle.motionPeriodSeconds = Math.max(.05f, finiteOptional(value, "motionPeriodSeconds", 1f));
                obstacle.motionPhaseRadians = finiteOptional(value, "motionPhaseRadians", 0f);
                obstacle.pivotOffsetX = finiteOptional(value, "pivotOffsetX", 0f);
                obstacle.pivotOffsetY = finiteOptional(value, "pivotOffsetY", 0f);
                obstacle.rotationSpeedDegreesPerSecond = finiteOptional(value, "rotationSpeedDegreesPerSecond", 0f);
                obstacle.collisionImpulseX = finiteOptional(value, "collisionImpulseX", 0f);
                obstacle.collisionImpulseY = finiteOptional(value, "collisionImpulseY", 0f);
                int action = value.optInt("collisionAction", RaceCourse.Obstacle.ACTION_BOUNCE);
                if (action < RaceCourse.Obstacle.ACTION_BOUNCE || action > RaceCourse.Obstacle.ACTION_TELEPORT) {
                    throw new IllegalArgumentException("碰撞动作无效");
                }
                obstacle.collisionAction = action;
                obstacle.teleportX = finiteOptional(value, "teleportX", obstacle.x);
                obstacle.teleportY = finiteOptional(value, "teleportY", obstacle.y);
                obstacle.actionCooldownSeconds = Math.max(.05f, finiteOptional(value, "actionCooldownSeconds", .35f));
                obstacle.setBounds(width, height);
                obstacle.validateRuntimeProperties(width, height);
                obstacle.arcDegrees = Math.max(1f, Math.min(359f, (float) value.optDouble("arcDegrees", 180f)));
                obstacle.rounded = value.optBoolean("rounded", true);
                obstacle.text = value.optString("text", "文字");
                obstacle.textColor = value.optInt("textColor", 0xFF20334D);
                return obstacle;
            } catch (IllegalArgumentException invalid) {
                throw new JSONException(invalid.getMessage());
            }
        }

        private boolean fits(float centerX, float centerY, float dimension, float edge, float rotation) {
            if (CIRCLE.equals(type)) {
                return centerX - dimension >= MIN_X && centerX + dimension <= boundsWidth - MIN_X
                    && centerY - dimension >= MIN_Y && centerY + dimension <= boundsHeight - MIN_Y;
            }
            if (ELLIPSE.equals(type)) {
                float radians = (float) Math.toRadians(rotation);
                float extentX = (float) Math.sqrt(square(dimension * (float) Math.cos(radians))
                    + square(radiusY * (float) Math.sin(radians)));
                float extentY = (float) Math.sqrt(square(dimension * (float) Math.sin(radians))
                    + square(radiusY * (float) Math.cos(radians)));
                return centerX - extentX >= MIN_X && centerX + extentX <= boundsWidth - MIN_X
                    && centerY - extentY >= MIN_Y && centerY + extentY <= boundsHeight - MIN_Y;
            }
            if (ARC.equals(type)) {
                float extent = dimension + edge * .5f;
                return centerX - extent >= MIN_X && centerX + extent <= boundsWidth - MIN_X
                    && centerY - extent >= MIN_Y && centerY + extent <= boundsHeight - MIN_Y;
            }
            if (TEXT.equals(type)) {
                return centerX - dimension * 2f >= MIN_X && centerX + dimension * 2f <= boundsWidth - MIN_X
                    && centerY - dimension >= MIN_Y && centerY + dimension <= boundsHeight - MIN_Y;
            }
            float radians = (float) Math.toRadians(rotation);
            float halfX = (float) Math.cos(radians) * dimension * .5f;
            float halfY = (float) Math.sin(radians) * dimension * .5f;
            float extentX = Math.abs(halfX) + edge * .5f;
            float extentY = Math.abs(halfY) + edge * .5f;
            return centerX - extentX >= MIN_X && centerX + extentX <= boundsWidth - MIN_X
                && centerY - extentY >= MIN_Y && centerY + extentY <= boundsHeight - MIN_Y;
        }

        private void validateRuntimeProperties(float levelWidth, float levelHeight) {
            if (!Float.isFinite(elasticity) || elasticity < 0f || elasticity > 1f
                    || !Float.isFinite(friction) || friction < 0f || friction > 1f) {
                throw new IllegalArgumentException("障碍物弹性或摩擦系数无效");
            }
            if (!Float.isFinite(motionX) || !Float.isFinite(motionY)
                    || Math.abs(motionX) > levelWidth || Math.abs(motionY) > levelHeight
                    || !Float.isFinite(motionPeriodSeconds) || motionPeriodSeconds < .05f
                    || motionPeriodSeconds > 3600f || !Float.isFinite(motionPhaseRadians)) {
                throw new IllegalArgumentException("障碍物移动参数无效");
            }
            if (!Float.isFinite(pivotOffsetX) || !Float.isFinite(pivotOffsetY)
                    || Math.abs(pivotOffsetX) > levelWidth * 2f || Math.abs(pivotOffsetY) > levelHeight * 2f
                    || !Float.isFinite(rotationSpeedDegreesPerSecond)
                    || Math.abs(rotationSpeedDegreesPerSecond) > 720f) {
                throw new IllegalArgumentException("障碍物旋转参数无效");
            }
            if (!Float.isFinite(collisionImpulseX) || !Float.isFinite(collisionImpulseY)
                    || Math.abs(collisionImpulseX) > 5000f || Math.abs(collisionImpulseY) > 5000f
                    || collisionAction < RaceCourse.Obstacle.ACTION_BOUNCE
                    || collisionAction > RaceCourse.Obstacle.ACTION_TELEPORT) {
                throw new IllegalArgumentException("障碍物碰撞动作无效");
            }
            if (!Float.isFinite(teleportX) || !Float.isFinite(teleportY)
                    || teleportX < 0f || teleportX > levelWidth || teleportY < 0f || teleportY > levelHeight
                    || !Float.isFinite(actionCooldownSeconds) || actionCooldownSeconds < .05f
                    || actionCooldownSeconds > 60f) {
                throw new IllegalArgumentException("障碍物传送目标或冷却时间无效");
            }
        }
    }

    public final String id;
    public String name;
    public float width;
    public float height;
    public int entrySide = RaceCourse.Port.TOP;
    public int exitSide = RaceCourse.Port.BOTTOM;
    public float entryOffset;
    public float exitOffset;
    private final ArrayList<Obstacle> obstacles;
    private int nextId;

    private EditableLevel(String id, String name, List<Obstacle> obstacles) {
        this(id, name, RaceCourse.WORLD_WIDTH, RaceCourse.LEVEL_HEIGHT, obstacles);
    }

    private EditableLevel(String id, String name, float width, float height, List<Obstacle> obstacles) {
        if (!isValidLevelId(id)) throw new IllegalArgumentException("关卡编号无效");
        if (obstacles.size() > MAX_OBSTACLES) throw new IllegalArgumentException("关卡障碍数量超限");
        this.id = id;
        this.name = validName(name, id);
        this.width = validDimension(width, "关卡宽度");
        this.height = validDimension(height, "关卡高度");
        this.entryOffset = width * .5f;
        this.exitOffset = width * .5f;
        this.obstacles = new ArrayList<>(obstacles);
        Set<String> ids = new HashSet<>();
        for (Obstacle obstacle : obstacles) {
            if (obstacle == null || !ids.add(obstacle.id)) {
                throw new IllegalArgumentException("关卡障碍编号重复");
            }
            int suffix = numericSuffix(obstacle.id);
            if (suffix >= nextId && suffix < Integer.MAX_VALUE) nextId = suffix + 1;
            obstacle.setBounds(width, height);
        }
    }

    public static List<String> builtInIds() {
        ArrayList<String> ids = new ArrayList<>();
        Collections.addAll(ids, "slopes", "bumpers", "rails", "zigzag", "center-pegs",
            "custom-204016971e9f496bb4067f059d54b069");
        return Collections.unmodifiableList(ids);
    }

    public static boolean isBuiltInId(String id) { return id != null && builtInIds().contains(id); }

    public static boolean isValidLevelId(String id) {
        return isBuiltInId(id) || (id != null && id.matches("custom-[A-Za-z0-9_-]{1,56}"));
    }

    public static EditableLevel createCustom(String id, String name) {
        return new EditableLevel(id, name, Collections.emptyList());
    }

    public static EditableLevel importCustom(String json, String newId) throws JSONException {
        JSONObject root = new JSONObject(json);
        String sourceId = root.optString("id");
        if (!isValidLevelId(sourceId)) throw new JSONException("关卡编号无效");
        EditableLevel source = parse(json, sourceId);
        try {
            EditableLevel imported = new EditableLevel(newId, source.name, source.width, source.height, source.obstacles);
            imported.setPorts(source.entrySide, source.entryOffset, source.exitSide, source.exitOffset);
            return imported;
        } catch (IllegalArgumentException invalid) {
            throw new JSONException(invalid.getMessage());
        }
    }

    public static EditableLevel fromTemplate(RaceCourse.Template template) {
        ArrayList<Obstacle> items = new ArrayList<>();
        for (int index = 0; index < template.obstacles.size(); index++) {
            RaceCourse.Obstacle obstacle = template.obstacles.get(index);
            if (obstacle.shape == RaceCourse.Obstacle.CIRCLE) {
                Obstacle item = new Obstacle("obstacle-" + index, CIRCLE, obstacle.x1, obstacle.y1,
                    obstacle.radius, 0f, 0f);
                copyProperties(item, obstacle);
                items.add(item);
            } else if (obstacle.shape == RaceCourse.Obstacle.ELLIPSE) {
                Obstacle item = new Obstacle("obstacle-" + index, ELLIPSE, obstacle.x1, obstacle.y1,
                    obstacle.radius, 0f, obstacle.rotationDegrees);
                item.radiusY = obstacle.radiusY;
                copyProperties(item, obstacle);
                items.add(item);
            } else if (obstacle.shape == RaceCourse.Obstacle.ARC) {
                Obstacle item = new Obstacle("obstacle-" + index, ARC, obstacle.x1, obstacle.y1,
                    obstacle.radius, obstacle.thickness, obstacle.rotationDegrees);
                item.arcDegrees = obstacle.arcDegrees;
                item.rounded = obstacle.rounded;
                copyProperties(item, obstacle);
                items.add(item);
            } else {
                float dx = obstacle.x2 - obstacle.x1;
                float dy = obstacle.y2 - obstacle.y1;
                Obstacle item = new Obstacle("obstacle-" + index, SLOPE,
                    (obstacle.x1 + obstacle.x2) * .5f, (obstacle.y1 + obstacle.y2) * .5f,
                    (float) Math.hypot(dx, dy), obstacle.thickness,
                    (float) Math.toDegrees(Math.atan2(dy, dx)));
                item.rounded = obstacle.rounded;
                copyProperties(item, obstacle);
                items.add(item);
            }
        }
        return new EditableLevel(template.id, template.name, items);
    }

    private static void copyProperties(Obstacle target, RaceCourse.Obstacle source) {
        target.elasticity = source.elasticity;
        target.friction = source.friction;
        target.color = source.color;
        target.visible = source.visible;
        target.collisionEnabled = source.collisionEnabled;
        target.motionX = source.motionX;
        target.motionY = source.motionY;
        target.motionPeriodSeconds = source.motionPeriodSeconds;
        target.motionPhaseRadians = source.motionPhaseRadians;
        target.pivotOffsetX = source.pivotOffsetX;
        target.pivotOffsetY = source.pivotOffsetY;
        target.rotationSpeedDegreesPerSecond = source.rotationSpeedDegreesPerSecond;
        target.collisionImpulseX = source.collisionImpulseX;
        target.collisionImpulseY = source.collisionImpulseY;
        target.collisionAction = source.collisionAction;
        target.teleportX = source.teleportX;
        target.teleportY = source.teleportY;
        target.actionCooldownSeconds = source.actionCooldownSeconds;
    }

    public static EditableLevel parse(String json, String expectedId) throws JSONException {
        if (json == null || json.length() > 64 * 1024) throw new JSONException("关卡数据长度无效");
        if (!isValidLevelId(expectedId)) throw new JSONException("关卡编号无效");
        JSONObject root = new JSONObject(json);
        int version = root.optInt("schemaVersion", -1);
        if (!FORMAT.equals(root.optString("format")) || (version != 1 && version != 2 && version != SCHEMA_VERSION)
                || !expectedId.equals(root.optString("id"))) throw new JSONException("关卡数据版本或编号不匹配");
        JSONArray rows = root.getJSONArray("obstacles");
        if (rows.length() > MAX_OBSTACLES) throw new JSONException("障碍数量超限");
        float width = version == 1 ? RaceCourse.WORLD_WIDTH : (float) root.optDouble("width", RaceCourse.WORLD_WIDTH);
        float height = version == 1 ? RaceCourse.LEVEL_HEIGHT : (float) root.optDouble("height", RaceCourse.LEVEL_HEIGHT);
        ArrayList<Obstacle> items = new ArrayList<>();
        for (int index = 0; index < rows.length(); index++) items.add(Obstacle.fromJson(rows.getJSONObject(index), width, height));
        try {
        EditableLevel result = new EditableLevel(expectedId, root.optString("name", expectedId), width, height, items);
        result.setPorts(root.optInt("entrySide", RaceCourse.Port.TOP),
            (float) root.optDouble("entryOffset", width * .5f),
            root.optInt("exitSide", RaceCourse.Port.BOTTOM),
            (float) root.optDouble("exitOffset", width * .5f));
        return result;
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

    public void resize(float nextWidth, float nextHeight) {
        float oldWidth = width;
        float oldHeight = height;
        float validatedWidth = validDimension(nextWidth, "关卡宽度");
        float validatedHeight = validDimension(nextHeight, "关卡高度");
        try {
            for (Obstacle obstacle : obstacles) obstacle.setBounds(validatedWidth, validatedHeight);
            width = validatedWidth;
            height = validatedHeight;
            entryOffset = clampPortOffset(entrySide, entryOffset);
            exitOffset = clampPortOffset(exitSide, exitOffset);
        } catch (IllegalArgumentException invalid) {
            for (Obstacle obstacle : obstacles) obstacle.setBounds(oldWidth, oldHeight);
            throw invalid;
        }
    }

    public Obstacle add(String type, float x, float y) {
        if (obstacles.size() >= MAX_OBSTACLES) throw new IllegalStateException("关卡障碍数量已达上限");
        if (!CIRCLE.equals(type) && !ELLIPSE.equals(type) && !SLOPE.equals(type)
                && !ARC.equals(type) && !TEXT.equals(type)) throw new IllegalArgumentException("障碍物类型无效");
        String objectId = nextAvailableId();
        Obstacle obstacle = CIRCLE.equals(type)
            ? new Obstacle(objectId, CIRCLE, width * .5f, height * .5f, 24f, 0f, 0f)
            : new Obstacle(objectId, TEXT.equals(type) ? TEXT : (ARC.equals(type) ? ARC : SLOPE), width * .5f, height * .5f,
                TEXT.equals(type) ? 24f : (ARC.equals(type) ? 48f : 120f), TEXT.equals(type) ? 0f : 12f, 0f);
        if (ELLIPSE.equals(type)) {
            obstacle = new Obstacle(objectId, ELLIPSE, width * .5f, height * .5f, 32f, 0f, 0f);
            obstacle.radiusY = 20f;
        }
        obstacle.setClamped(x, y, obstacle.size, obstacle.thickness, obstacle.rotationDegrees);
        obstacles.add(obstacle);
        return obstacle;
    }

    public boolean remove(Obstacle obstacle) { return obstacles.remove(obstacle); }

    public RaceCourse.Template toTemplate(float elasticity) {
        RaceCourse.Port entry = new RaceCourse.Port(entrySide, entryOffset, portLabel(entrySide));
        RaceCourse.Port exit = new RaceCourse.Port(exitSide, exitOffset, portLabel(exitSide));
        ArrayList<RaceCourse.Obstacle> physics = new ArrayList<>();
        for (Obstacle obstacle : obstacles) {
            RaceCourse.Obstacle physicsObstacle = obstacle.toPhysicsObstacle(elasticity);
            if (physicsObstacle != null) physics.add(physicsObstacle);
        }
        ArrayList<RaceCourse.TextObject> texts = new ArrayList<>();
        for (Obstacle obstacle : obstacles) if (TEXT.equals(obstacle.type)) {
            texts.add(new RaceCourse.TextObject(obstacle.text, obstacle.x, obstacle.y, obstacle.size,
                obstacle.rotationDegrees, obstacle.textColor));
        }
        return new RaceCourse.Template(id, name, entry, exit, width, height, physics, texts);
    }

    public String toJson() {
        try {
            JSONArray rows = new JSONArray();
            for (Obstacle obstacle : obstacles) rows.put(obstacle.toJson());
            return new JSONObject().put("format", FORMAT).put("schemaVersion", SCHEMA_VERSION)
                .put("id", id).put("name", name).put("width", width).put("height", height)
                .put("entrySide", entrySide).put("entryOffset", entryOffset)
                .put("exitSide", exitSide).put("exitOffset", exitOffset)
                .put("obstacles", rows).toString();
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

    private static float square(float value) { return value * value; }

    private static float finiteOptional(JSONObject value, String key, float fallback) throws JSONException {
        double number = value.optDouble(key, fallback);
        if (!Double.isFinite(number) || Math.abs(number) > 100000f) throw new JSONException("障碍物属性数值无效");
        return (float) number;
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

    private static float validDimension(float value, String label) {
        finite(value, label);
        if (value < 180f || value > 1440f) throw new IllegalArgumentException(label + "超出范围");
        return value;
    }

    public void setPorts(int nextEntrySide, float nextEntryOffset, int nextExitSide, float nextExitOffset) {
        if (!validSide(nextEntrySide) || !validSide(nextExitSide)) throw new IllegalArgumentException("出入口方向无效");
        entrySide = nextEntrySide;
        exitSide = nextExitSide;
        entryOffset = clampPortOffset(entrySide, nextEntryOffset);
        exitOffset = clampPortOffset(exitSide, nextExitOffset);
    }

    public float entryX() { return portX(entrySide, entryOffset); }
    public float entryY() { return portY(entrySide, entryOffset); }
    public float exitX() { return portX(exitSide, exitOffset); }
    public float exitY() { return portY(exitSide, exitOffset); }

    public void movePort(boolean entry, float x, float y) {
        int side = entry ? entrySide : exitSide;
        float offset = (side == RaceCourse.Port.TOP || side == RaceCourse.Port.BOTTOM) ? x : y;
        if (entry) entryOffset = clampPortOffset(side, offset); else exitOffset = clampPortOffset(side, offset);
    }

    private float clampPortOffset(int side, float value) {
        finite(value, "出入口位置");
        return clamp(value, 0f, side == RaceCourse.Port.TOP || side == RaceCourse.Port.BOTTOM ? width : height);
    }

    private float portX(int side, float offset) {
        return side == RaceCourse.Port.LEFT ? 0f : side == RaceCourse.Port.RIGHT ? width : offset;
    }
    private float portY(int side, float offset) {
        return side == RaceCourse.Port.TOP ? 0f : side == RaceCourse.Port.BOTTOM ? height : offset;
    }
    private static boolean validSide(int side) { return side >= RaceCourse.Port.TOP && side <= RaceCourse.Port.LEFT; }
    private static String portLabel(int side) {
        return side == RaceCourse.Port.TOP || side == RaceCourse.Port.BOTTOM ? "vertical_center" : "horizontal_center";
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
