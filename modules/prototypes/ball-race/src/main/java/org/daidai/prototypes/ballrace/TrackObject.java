package org.daidai.prototypes.ballrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 可编辑关卡中的一个物块，位置和角度都使用关卡本地坐标。 */
public final class TrackObject {
    public static final String RECTANGLE = "rectangle";
    public static final String CIRCLE = "circle";
    public static final String SEGMENT = "segment";
    public static final String POLYGON = "polygon";
    public static final int MAX_VERTICES = 128;

    /** 多边形顶点相对物块中心的位置。 */
    public static final class Vertex {
        public final float x;
        public final float y;

        public Vertex(float x, float y) {
            this.x = finite(x, "顶点 x 坐标");
            this.y = finite(y, "顶点 y 坐标");
        }
    }

    /** 旋转后的关卡本地坐标点。 */
    public static final class Point {
        public final float x;
        public final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public final String id;
    public final String type;
    public final float x;
    public final float y;
    public final float rotationDegrees;
    public final float width;
    public final float height;
    public final float radius;
    public final int color;
    public final boolean collisionEnabled;
    public final float elasticity;
    public final float friction;
    public final List<Vertex> vertices;

    public TrackObject(String id, String type, float x, float y, float rotationDegrees,
                       float width, float height, float radius, int color,
                       boolean collisionEnabled, float elasticity, float friction,
                       List<Vertex> vertices) {
        this.id = validId(id);
        this.type = validType(type);
        this.x = finite(x, "物块 x 坐标");
        this.y = finite(y, "物块 y 坐标");
        this.rotationDegrees = normalizeDegrees(finite(rotationDegrees, "物块旋转角度"));
        this.width = finite(width, "物块宽度");
        this.height = finite(height, "物块高度");
        this.radius = finite(radius, "物块半径");
        this.color = color;
        this.collisionEnabled = collisionEnabled;
        this.elasticity = unitInterval(elasticity, "物块弹性系数");
        this.friction = unitInterval(friction, "物块摩擦系数");
        List<Vertex> copied = vertices == null ? Collections.emptyList() : new ArrayList<>(vertices);
        if (copied.size() > MAX_VERTICES) throw new IllegalArgumentException("多边形顶点过多");
        this.vertices = Collections.unmodifiableList(copied);
        validateShape();
    }

    public static TrackObject rectangle(String id, float x, float y, float width, float height,
                                        float rotationDegrees, int color, boolean collisionEnabled,
                                        float elasticity, float friction) {
        return new TrackObject(id, RECTANGLE, x, y, rotationDegrees, width, height, 0f,
            color, collisionEnabled, elasticity, friction, Collections.emptyList());
    }

    public static TrackObject circle(String id, float x, float y, float radius, int color,
                                     boolean collisionEnabled, float elasticity, float friction) {
        return new TrackObject(id, CIRCLE, x, y, 0f, 0f, 0f, radius,
            color, collisionEnabled, elasticity, friction, Collections.emptyList());
    }

    public static TrackObject segment(String id, float x, float y, float length, float thickness,
                                      float rotationDegrees, int color, boolean collisionEnabled,
                                      float elasticity, float friction) {
        return new TrackObject(id, SEGMENT, x, y, rotationDegrees, length, thickness, 0f,
            color, collisionEnabled, elasticity, friction, Collections.emptyList());
    }

    public static TrackObject polygon(String id, float x, float y, float rotationDegrees,
                                      int color, boolean collisionEnabled, float elasticity,
                                      float friction, List<Vertex> vertices) {
        return new TrackObject(id, POLYGON, x, y, rotationDegrees, 0f, 0f, 0f,
            color, collisionEnabled, elasticity, friction, vertices);
    }

    /** 将物块局部坐标按中心旋转后平移到关卡坐标，屏幕坐标中正角度为顺时针。 */
    public Point transform(float localX, float localY) {
        double radians = Math.toRadians(rotationDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Point(x + (float) (localX * cosine - localY * sine),
            y + (float) (localX * sine + localY * cosine));
    }

    /** 检查旋转后的物块边界仍完整位于关卡区域内。 */
    public boolean fitsWithin(float levelWidth, float levelHeight) {
        if (!positive(levelWidth) || !positive(levelHeight)) return false;
        if (type.equals(CIRCLE)) {
            return x - radius >= 0f && y - radius >= 0f
                && x + radius <= levelWidth && y + radius <= levelHeight;
        }
        List<Vertex> corners = outlinePoints();
        for (Vertex corner : corners) {
            Point point = transform(corner.x, corner.y);
            if (point.x < 0f || point.y < 0f || point.x > levelWidth || point.y > levelHeight) return false;
        }
        return true;
    }

    /** 按矩形边界返回四角，线段则使用其两端和厚度四角。 */
    private List<Vertex> outlinePoints() {
        if (type.equals(POLYGON)) return vertices;
        float halfWidth = width * .5f;
        float halfHeight = height * .5f;
        List<Vertex> points = new ArrayList<>(4);
        points.add(new Vertex(-halfWidth, -halfHeight));
        points.add(new Vertex(halfWidth, -halfHeight));
        points.add(new Vertex(halfWidth, halfHeight));
        points.add(new Vertex(-halfWidth, halfHeight));
        return points;
    }

    private void validateShape() {
        if (type.equals(CIRCLE)) {
            if (!positive(radius) || width != 0f || height != 0f || !vertices.isEmpty()) {
                throw new IllegalArgumentException("圆形物块尺寸无效");
            }
        } else if (type.equals(RECTANGLE) || type.equals(SEGMENT)) {
            if (!positive(width) || !positive(height) || radius != 0f || !vertices.isEmpty()) {
                throw new IllegalArgumentException("矩形或线段尺寸无效");
            }
        } else {
            if (vertices.size() < 3 || width != 0f || height != 0f || radius != 0f) {
                throw new IllegalArgumentException("多边形物块无效");
            }
            boolean nonCollinear = false;
            Vertex first = vertices.get(0);
            for (int index = 1; index + 1 < vertices.size(); index++) {
                Vertex second = vertices.get(index);
                Vertex third = vertices.get(index + 1);
                float cross = (second.x - first.x) * (third.y - first.y)
                    - (second.y - first.y) * (third.x - first.x);
                if (Math.abs(cross) > 1e-5f) {
                    nonCollinear = true;
                    break;
                }
            }
            if (!nonCollinear) throw new IllegalArgumentException("多边形顶点不能共线");
        }
    }

    static float normalizeDegrees(float degrees) {
        float normalized = degrees % 360f;
        return normalized < 0f ? normalized + 360f : normalized;
    }

    static String validId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("对象 ID 无效");
        }
        return value;
    }

    private static String validType(String value) {
        if (!RECTANGLE.equals(value) && !CIRCLE.equals(value)
                && !SEGMENT.equals(value) && !POLYGON.equals(value)) {
            throw new IllegalArgumentException("物块类型不支持");
        }
        return value;
    }

    static float finite(float value, String label) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(label + "必须是有限数值");
        return value;
    }

    static float unitInterval(float value, String label) {
        finite(value, label);
        if (value < 0f || value > 1f) throw new IllegalArgumentException(label + "超出 0 到 1 的范围");
        return value;
    }

    static boolean positive(float value) {
        return Float.isFinite(value) && value > 0f;
    }
}
