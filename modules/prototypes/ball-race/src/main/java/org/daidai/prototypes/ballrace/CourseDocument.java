package org.daidai.prototypes.ballrace;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 可保存的关卡布局，使用版本化 JSON 描述位置、旋转、入口、出口和连接关系。 */
public final class CourseDocument {
    public static final String FORMAT = "ball-race-course";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_LEVELS = 64;
    public static final int MAX_OBJECTS_PER_LEVEL = 256;
    public static final int MAX_CONNECTIONS = 512;
    public static final float MAX_LEVEL_DIMENSION = 100000f;
    private static final float CONNECTION_TOLERANCE = .25f;

    public static final class Level {
        public final String id;
        public final float width;
        public final float height;
        public final float originX;
        public final float originY;
        public final float rotationDegrees;
        public final List<Port> ports;
        public final List<TrackObject> objects;

        public Level(String id, float width, float height, float originX, float originY,
                     float rotationDegrees, List<Port> ports, List<TrackObject> objects) {
            this.id = TrackObject.validId(id);
            this.width = dimension(width, "关卡宽度");
            this.height = dimension(height, "关卡高度");
            this.originX = TrackObject.finite(originX, "关卡原点 x 坐标");
            this.originY = TrackObject.finite(originY, "关卡原点 y 坐标");
            this.rotationDegrees = TrackObject.normalizeDegrees(
                TrackObject.finite(rotationDegrees, "关卡旋转角度"));
            this.ports = immutableCopy(ports);
            this.objects = immutableCopy(objects);
            if (this.ports.size() > 8) throw new IllegalArgumentException("每关入口和出口最多 8 个");
            if (this.objects.size() > MAX_OBJECTS_PER_LEVEL) throw new IllegalArgumentException("单关物块数量超限");
            validateContents();
        }

        public Point worldPoint(float localX, float localY) {
            double radians = Math.toRadians(rotationDegrees);
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            return new Point(originX + (float) (localX * cosine - localY * sine),
                originY + (float) (localX * sine + localY * cosine));
        }

        private void validateContents() {
            Set<String> portIds = new HashSet<>();
            boolean hasEntry = false;
            boolean hasExit = false;
            for (Port port : ports) {
                if (!portIds.add(port.id)) throw new IllegalArgumentException("关卡内入口或出口 ID 重复");
                hasEntry |= Port.ENTRY.equals(port.kind);
                hasExit |= Port.EXIT.equals(port.kind);
                float edgeLength = port.side.equals(Port.TOP) || port.side.equals(Port.BOTTOM) ? width : height;
                if (port.offset > edgeLength) throw new IllegalArgumentException("入口或出口位置超出关卡边缘");
            }
            if (!hasEntry || !hasExit) throw new IllegalArgumentException("每关必须至少有一个入口和一个出口");
            Set<String> objectIds = new HashSet<>();
            for (TrackObject object : objects) {
                if (!objectIds.add(object.id)) throw new IllegalArgumentException("关卡内物块 ID 重复");
                if (!object.fitsWithin(width, height)) throw new IllegalArgumentException("物块超出关卡范围");
            }
        }
    }

    /** 入口或出口在关卡边缘上的命名连接点。 */
    public static final class Port {
        public static final String ENTRY = "entry";
        public static final String EXIT = "exit";
        public static final String TOP = "top";
        public static final String RIGHT = "right";
        public static final String BOTTOM = "bottom";
        public static final String LEFT = "left";

        public final String id;
        public final String kind;
        public final String side;
        public final float offset;
        public final String label;

        public Port(String id, String kind, String side, float offset, String label) {
            this.id = TrackObject.validId(id);
            this.kind = oneOf(kind, ENTRY, EXIT, "入口或出口类型");
            this.side = oneOf(side, TOP, RIGHT, BOTTOM, LEFT, "关卡边缘方向");
            this.offset = TrackObject.finite(offset, "入口或出口偏移");
            if (offset < 0f) throw new IllegalArgumentException("入口或出口偏移不能为负数");
            this.label = validLabel(label);
        }

        Point localPoint(Level level) {
            switch (side) {
                case TOP: return new Point(offset, 0f);
                case RIGHT: return new Point(level.width, offset);
                case BOTTOM: return new Point(offset, level.height);
                case LEFT: return new Point(0f, offset);
                default: throw new IllegalStateException("未知关卡边缘方向");
            }
        }

        Point localNormal() {
            switch (side) {
                case TOP: return new Point(0f, -1f);
                case RIGHT: return new Point(1f, 0f);
                case BOTTOM: return new Point(0f, 1f);
                case LEFT: return new Point(-1f, 0f);
                default: throw new IllegalStateException("未知关卡边缘方向");
            }
        }
    }

    /** 关卡之间的单向连接，可表示物理通道或将来加入的传送关系。 */
    public static final class Connection {
        public static final String WALKABLE = "walkable";
        public static final String TELEPORT = "teleport";

        public final String fromLevelId;
        public final String exitPortId;
        public final String toLevelId;
        public final String entryPortId;
        public final String type;

        public Connection(String fromLevelId, String exitPortId, String toLevelId,
                          String entryPortId, String type) {
            this.fromLevelId = TrackObject.validId(fromLevelId);
            this.exitPortId = TrackObject.validId(exitPortId);
            this.toLevelId = TrackObject.validId(toLevelId);
            this.entryPortId = TrackObject.validId(entryPortId);
            this.type = oneOf(type, WALKABLE, TELEPORT, "关卡连接类型");
            if (fromLevelId.equals(toLevelId)) throw new IllegalArgumentException("关卡不能连接到自身");
        }
    }

    /** 关卡在课程坐标系中的世界位置。 */
    public static final class Point {
        public final float x;
        public final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public final String id;
    public final String name;
    public final String startLevelId;
    public final List<Level> levels;
    public final List<Connection> connections;

    public CourseDocument(String id, String name, String startLevelId,
                          List<Level> levels, List<Connection> connections) {
        this.id = TrackObject.validId(id);
        this.name = validName(name);
        this.startLevelId = TrackObject.validId(startLevelId);
        this.levels = immutableCopy(levels);
        this.connections = immutableCopy(connections);
        validateCourse();
    }

    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("coordinateUnit", "level-unit");
            root.put("id", id);
            root.put("name", name);
            root.put("startLevelId", startLevelId);
            JSONArray levelRows = new JSONArray();
            for (Level level : levels) levelRows.put(writeLevel(level));
            root.put("levels", levelRows);
            JSONArray connectionRows = new JSONArray();
            for (Connection connection : connections) connectionRows.put(writeConnection(connection));
            root.put("connections", connectionRows);
            return root.toString();
        } catch (JSONException error) {
            throw new IllegalStateException("无法序列化关卡", error);
        }
    }

    public static CourseDocument parse(String json) throws JSONException {
        if (json == null || json.length() > 1_000_000) throw new JSONException("关卡文件为空或过大");
        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format"))) throw new JSONException("关卡文件类型不正确");
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) throw new JSONException("不支持的关卡文件版本");
        if (!"level-unit".equals(root.optString("coordinateUnit"))) throw new JSONException("关卡坐标单位不正确");
        JSONArray levelRows = root.getJSONArray("levels");
        JSONArray connectionRows = root.getJSONArray("connections");
        if (levelRows.length() == 0 || levelRows.length() > MAX_LEVELS
                || connectionRows.length() > MAX_CONNECTIONS) throw new JSONException("关卡数量超出限制");
        List<Level> levels = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        try {
            for (int index = 0; index < levelRows.length(); index++) levels.add(readLevel(levelRows.getJSONObject(index)));
            for (int index = 0; index < connectionRows.length(); index++) {
                connections.add(readConnection(connectionRows.getJSONObject(index)));
            }
            return new CourseDocument(root.getString("id"), root.getString("name"),
                root.getString("startLevelId"), levels, connections);
        } catch (IllegalArgumentException error) {
            throw new JSONException(error.getMessage());
        }
    }

    private void validateCourse() {
        if (levels.isEmpty() || levels.size() > MAX_LEVELS) throw new IllegalArgumentException("关卡数量无效");
        if (connections.size() > MAX_CONNECTIONS) throw new IllegalArgumentException("关卡连接数量超限");
        Map<String, Level> byId = new HashMap<>();
        for (Level level : levels) {
            if (byId.put(level.id, level) != null) throw new IllegalArgumentException("关卡 ID 重复");
        }
        if (!byId.containsKey(startLevelId)) throw new IllegalArgumentException("起始关卡不存在");

        Map<String, Port> portIndex = new HashMap<>();
        for (Level level : levels) {
            for (Port port : level.ports) portIndex.put(portKey(level.id, port.id), port);
        }

        Set<String> edges = new HashSet<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        for (Connection connection : connections) {
            Level from = byId.get(connection.fromLevelId);
            Level to = byId.get(connection.toLevelId);
            if (from == null || to == null) throw new IllegalArgumentException("连接引用了不存在的关卡");
            Port exit = portIndex.get(portKey(from.id, connection.exitPortId));
            Port entry = portIndex.get(portKey(to.id, connection.entryPortId));
            if (exit == null || entry == null || !Port.EXIT.equals(exit.kind) || !Port.ENTRY.equals(entry.kind)) {
                throw new IllegalArgumentException("连接必须从出口指向入口");
            }
            if (!exit.label.equals(entry.label)) throw new IllegalArgumentException("连接两端的标签不匹配");
            String edge = from.id + ":" + exit.id + ">" + to.id + ":" + entry.id;
            if (!edges.add(edge)) throw new IllegalArgumentException("重复的关卡连接");
            if (Connection.WALKABLE.equals(connection.type)) validatePhysicalJoin(from, exit, to, entry);
            outgoing.computeIfAbsent(from.id, ignored -> new ArrayList<>()).add(to.id);
            incoming.computeIfAbsent(to.id, ignored -> new ArrayList<>()).add(from.id);
        }
        validateReachability(byId, outgoing, incoming);
    }

    private static void validatePhysicalJoin(Level from, Port exit, Level to, Port entry) {
        Point fromPoint = from.worldPoint(exit.localPoint(from).x, exit.localPoint(from).y);
        Point toPoint = to.worldPoint(entry.localPoint(to).x, entry.localPoint(to).y);
        if (distance(fromPoint, toPoint) > CONNECTION_TOLERANCE) {
            throw new IllegalArgumentException("物理连接的入口和出口没有对齐");
        }
        Point fromNormal = rotate(exit.localNormal(), from.rotationDegrees);
        Point toNormal = rotate(entry.localNormal(), to.rotationDegrees);
        float dot = fromNormal.x * toNormal.x + fromNormal.y * toNormal.y;
        if (dot > -.999f) throw new IllegalArgumentException("物理连接的两端没有相向");
    }

    private void validateReachability(Map<String, Level> byId, Map<String, List<String>> outgoing,
                                      Map<String, List<String>> incoming) {
        Set<String> reached = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(startLevelId);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!reached.add(current)) continue;
            for (String next : outgoing.getOrDefault(current, Collections.emptyList())) pending.addLast(next);
        }
        if (reached.size() != byId.size()) throw new IllegalArgumentException("存在从起点无法到达的关卡");
        Set<String> canFinish = new HashSet<>();
        ArrayDeque<String> reversePending = new ArrayDeque<>();
        for (String id : byId.keySet()) {
            if (outgoing.getOrDefault(id, Collections.emptyList()).isEmpty()) reversePending.addLast(id);
        }
        if (reversePending.isEmpty()) throw new IllegalArgumentException("赛道至少需要一个终点关卡");
        while (!reversePending.isEmpty()) {
            String current = reversePending.removeFirst();
            if (!canFinish.add(current)) continue;
            for (String previous : incoming.getOrDefault(current, Collections.emptyList())) {
                reversePending.addLast(previous);
            }
        }
        if (canFinish.size() != byId.size()) throw new IllegalArgumentException("存在无法抵达终点的关卡");
    }

    private static JSONObject writeLevel(Level level) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", level.id);
        value.put("width", level.width);
        value.put("height", level.height);
        value.put("originX", level.originX);
        value.put("originY", level.originY);
        value.put("rotationDeg", level.rotationDegrees);
        JSONArray ports = new JSONArray();
        for (Port port : level.ports) {
            ports.put(new JSONObject().put("id", port.id).put("kind", port.kind)
                .put("side", port.side).put("offset", port.offset).put("label", port.label));
        }
        value.put("ports", ports);
        JSONArray objects = new JSONArray();
        for (TrackObject object : level.objects) objects.put(writeObject(object));
        value.put("objects", objects);
        return value;
    }

    private static JSONObject writeObject(TrackObject object) throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", object.id);
        value.put("type", object.type);
        value.put("x", object.x);
        value.put("y", object.y);
        value.put("rotationDeg", object.rotationDegrees);
        value.put("width", object.width);
        value.put("height", object.height);
        value.put("radius", object.radius);
        value.put("color", object.color);
        value.put("collision", object.collisionEnabled);
        value.put("elasticity", object.elasticity);
        value.put("friction", object.friction);
        JSONArray vertices = new JSONArray();
        for (TrackObject.Vertex vertex : object.vertices) {
            vertices.put(new JSONArray().put(vertex.x).put(vertex.y));
        }
        value.put("vertices", vertices);
        return value;
    }

    private static JSONObject writeConnection(Connection connection) throws JSONException {
        return new JSONObject().put("fromLevelId", connection.fromLevelId)
            .put("exitPortId", connection.exitPortId).put("toLevelId", connection.toLevelId)
            .put("entryPortId", connection.entryPortId).put("type", connection.type);
    }

    private static Level readLevel(JSONObject value) throws JSONException {
        List<Port> ports = new ArrayList<>();
        JSONArray portRows = value.getJSONArray("ports");
        if (portRows.length() > 8) throw new JSONException("单关入口和出口数量超限");
        for (int index = 0; index < portRows.length(); index++) {
            JSONObject port = portRows.getJSONObject(index);
            ports.add(new Port(port.getString("id"), port.getString("kind"), port.getString("side"),
                number(port, "offset"), port.getString("label")));
        }
        List<TrackObject> objects = new ArrayList<>();
        JSONArray objectRows = value.getJSONArray("objects");
        if (objectRows.length() > MAX_OBJECTS_PER_LEVEL) throw new JSONException("单关物块数量超限");
        for (int index = 0; index < objectRows.length(); index++) objects.add(readObject(objectRows.getJSONObject(index)));
        return new Level(value.getString("id"), number(value, "width"), number(value, "height"),
            number(value, "originX"), number(value, "originY"), number(value, "rotationDeg"), ports, objects);
    }

    private static TrackObject readObject(JSONObject value) throws JSONException {
        List<TrackObject.Vertex> vertices = new ArrayList<>();
        JSONArray vertexRows = value.getJSONArray("vertices");
        if (vertexRows.length() > TrackObject.MAX_VERTICES) throw new JSONException("多边形顶点数量超限");
        for (int index = 0; index < vertexRows.length(); index++) {
            JSONArray pair = vertexRows.getJSONArray(index);
            if (pair.length() != 2) throw new JSONException("多边形顶点格式错误");
            vertices.add(new TrackObject.Vertex((float) pair.getDouble(0), (float) pair.getDouble(1)));
        }
        return new TrackObject(value.getString("id"), value.getString("type"), number(value, "x"),
            number(value, "y"), number(value, "rotationDeg"), number(value, "width"),
            number(value, "height"), number(value, "radius"), value.getInt("color"),
            value.getBoolean("collision"), number(value, "elasticity"), number(value, "friction"), vertices);
    }

    private static Connection readConnection(JSONObject value) throws JSONException {
        return new Connection(value.getString("fromLevelId"), value.getString("exitPortId"),
            value.getString("toLevelId"), value.getString("entryPortId"), value.getString("type"));
    }

    private static float number(JSONObject value, String key) throws JSONException {
        double number = value.getDouble(key);
        if (!Double.isFinite(number) || Math.abs(number) > MAX_LEVEL_DIMENSION * 100f) {
            throw new JSONException("数值无效或超出范围：" + key);
        }
        return (float) number;
    }

    private static float dimension(float value, String label) {
        TrackObject.finite(value, label);
        if (value <= 0f || value > MAX_LEVEL_DIMENSION) throw new IllegalArgumentException(label + "超出范围");
        return value;
    }

    private static String validLabel(String label) {
        if (label == null || label.trim().isEmpty() || label.length() > 40) {
            throw new IllegalArgumentException("入口或出口标签无效");
        }
        return label.trim();
    }

    private static String validName(String name) {
        if (name == null || name.trim().isEmpty() || name.length() > 80) {
            throw new IllegalArgumentException("关卡名称无效");
        }
        return name.trim();
    }

    private static String oneOf(String value, String first, String second, String label) {
        if (!first.equals(value) && !second.equals(value)) throw new IllegalArgumentException(label + "无效");
        return value;
    }

    private static String oneOf(String value, String first, String second, String third,
                                String fourth, String label) {
        if (!first.equals(value) && !second.equals(value) && !third.equals(value) && !fourth.equals(value)) {
            throw new IllegalArgumentException(label + "无效");
        }
        return value;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(source == null ? new ArrayList<>() : new ArrayList<>(source));
    }

    private static Point rotate(Point point, float degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Point((float) (point.x * cosine - point.y * sine),
            (float) (point.x * sine + point.y * cosine));
    }

    private static double distance(Point first, Point second) {
        return Math.hypot(first.x - second.x, first.y - second.y);
    }

    private static String portKey(String levelId, String portId) {
        return levelId + ":" + portId;
    }
}
