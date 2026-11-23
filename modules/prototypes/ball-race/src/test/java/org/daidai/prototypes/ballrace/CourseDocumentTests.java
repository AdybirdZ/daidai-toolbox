package org.daidai.prototypes.ballrace;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class CourseDocumentTests {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); }
        catch (RuntimeException expected) { rejected = true; }
        check(rejected, message);
    }

    private static CourseDocument.Port port(String id, String kind, String side, float offset, String label) {
        return new CourseDocument.Port(id, kind, side, offset, label);
    }

    private static CourseDocument.Level level(String id, float originX, float originY,
                                               List<CourseDocument.Port> ports,
                                               List<TrackObject> objects) {
        return new CourseDocument.Level(id, 360f, 480f, originX, originY, 0f, ports, objects);
    }

    private static CourseDocument verticalCourse() {
        List<CourseDocument.Port> lowerPorts = Arrays.asList(
            port("start", CourseDocument.Port.ENTRY, CourseDocument.Port.TOP, 180f, "main"),
            port("down", CourseDocument.Port.EXIT, CourseDocument.Port.BOTTOM, 180f, "main"));
        List<CourseDocument.Port> upperPorts = Arrays.asList(
            port("up", CourseDocument.Port.ENTRY, CourseDocument.Port.TOP, 180f, "main"),
            port("finish", CourseDocument.Port.EXIT, CourseDocument.Port.BOTTOM, 180f, "finish"));
        TrackObject rectangle = TrackObject.rectangle("beam-1", 180f, 120f, 48f, 10f,
            35f, 0xFF425B76, true, .7f, .35f);
        TrackObject circle = TrackObject.circle("bumper-1", 92f, 220f, 18f,
            0xFFEF4444, true, .8f, .4f);
        TrackObject segment = TrackObject.segment("rail-1", 260f, 300f, 96f, 8f,
            90f, 0xFF596C83, true, .5f, .25f);
        TrackObject polygon = TrackObject.polygon("wedge-1", 140f, 390f, 15f,
            0xFF22C55E, true, .6f, .3f, Arrays.asList(
                new TrackObject.Vertex(-20f, -15f), new TrackObject.Vertex(24f, -8f),
                new TrackObject.Vertex(4f, 22f)));
        List<TrackObject> objects = Arrays.asList(rectangle, circle, segment, polygon);
        return new CourseDocument("course-1", "Vertical practice", "level-1",
            Arrays.asList(level("level-1", 0f, 0f, lowerPorts, objects),
                level("level-2", 0f, 480f, upperPorts, Collections.emptyList())),
            Collections.singletonList(new CourseDocument.Connection(
                "level-1", "down", "level-2", "up", CourseDocument.Connection.WALKABLE)));
    }

    public static void main(String[] args) throws Exception {
        TrackObject rotated = TrackObject.rectangle("rotated", 10f, 20f, 4f, 2f,
            90f, 0xFFFFFFFF, true, .5f, .25f);
        TrackObject.Point corner = rotated.transform(2f, 1f);
        check(Math.abs(corner.x - 9f) < .001f && Math.abs(corner.y - 22f) < .001f,
            "Clockwise object rotation transforms local geometry around its center");
        check(Math.abs(TrackObject.normalizeDegrees(-90f) - 270f) < .001f,
            "Negative rotation values normalize to a stable stored angle");
        check(rotated.fitsWithin(20f, 30f), "Rotated obstacle bounds are validated in level coordinates");

        CourseDocument course = verticalCourse();
        String encoded = course.toJson();
        CourseDocument decoded = CourseDocument.parse(encoded);
        check(decoded.levels.size() == 2 && decoded.connections.size() == 1,
            "Course JSON retains its levels and explicit port connection");
        check(encoded.equals(decoded.toJson()), "Course JSON round-trips into a stable representation");
        CourseDocument.Level first = decoded.levels.get(0);
        check(first.objects.size() == 4, "Course JSON stores all obstacle shapes");
        TrackObject beam = first.objects.get(0);
        check(beam.id.equals("beam-1") && Math.abs(beam.x - 180f) < .001f
                && Math.abs(beam.y - 120f) < .001f && Math.abs(beam.rotationDegrees - 35f) < .001f,
            "Obstacle identity, center position and rotation survive serialization");
        check(first.objects.get(3).vertices.size() == 3, "Polygon vertices remain local to their object origin");

        TrackObject outside = TrackObject.rectangle("outside", 5f, 5f, 20f, 12f,
            0f, 0xFFFFFFFF, true, .5f, .25f);
        rejects(() -> level("bad-level", 0f, 0f,
                Arrays.asList(port("in", "entry", "top", 180f, "main"),
                    port("out", "exit", "bottom", 180f, "main")),
                Collections.singletonList(outside)),
            "Objects extending beyond a level edge are rejected");

        List<CourseDocument.Port> mismatchPorts = Arrays.asList(
            port("start", "entry", "top", 180f, "wrong-tag"),
            port("down", "exit", "bottom", 180f, "main"));
        CourseDocument.Level mismatch = level("mismatch", 0f, 480f, mismatchPorts, Collections.emptyList());
        rejects(() -> new CourseDocument("course-2", "Bad labels", "level-1",
                Arrays.asList(first, mismatch), Collections.singletonList(new CourseDocument.Connection(
                    "level-1", "down", "mismatch", "start", "walkable"))),
            "Mismatched entry and exit tags cannot be connected");

        CourseDocument.Level misplaced = level("misplaced", 0f, 490f,
            Arrays.asList(port("start", "entry", "top", 180f, "main"),
                port("finish", "exit", "bottom", 180f, "finish")), Collections.emptyList());
        rejects(() -> new CourseDocument("course-3", "Bad geometry", "level-1",
                Arrays.asList(first, misplaced), Collections.singletonList(new CourseDocument.Connection(
                    "level-1", "down", "misplaced", "start", "walkable"))),
            "Physically connected ports must share a world position");

        CourseDocument.Level teleportTarget = level("teleport-target", 820f, -240f,
            Arrays.asList(port("entry", "entry", "left", 240f, "main"),
                port("finish", "exit", "right", 240f, "finish")), Collections.emptyList());
        CourseDocument teleportCourse = new CourseDocument("course-4", "Portal", "level-1",
            Arrays.asList(first, teleportTarget), Collections.singletonList(
                new CourseDocument.Connection("level-1", "down", "teleport-target", "entry", "teleport")));
        check(teleportCourse.levels.size() == 2,
            "Teleport links can connect tagged ports at distant world positions");

        CourseDocument.Level isolated = level("isolated", 500f, 500f,
            Arrays.asList(port("entry", "entry", "top", 180f, "main"),
                port("exit", "exit", "bottom", 180f, "finish")), Collections.emptyList());
        rejects(() -> new CourseDocument("course-5", "Disconnected", "level-1",
                Arrays.asList(first, isolated), Collections.emptyList()),
            "Unreachable levels are rejected before saving a race course");
        rejects(() -> CourseDocument.parse(encoded.replace("\"schemaVersion\":1", "\"schemaVersion\":99")),
            "Unknown schema versions are rejected");
        rejects(() -> TrackObject.circle("bad", 50f, 50f, -1f, 0xFFFFFFFF, true, .5f, .2f),
            "Invalid dimensions are rejected");
        if (args.length > 0) {
            CourseDocument example = CourseDocument.parse(new String(
                Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8));
            check(example.levels.size() == 3 && example.connections.size() == 2,
                "Documented sample course parses and keeps its labelled vertical chain");
        }

        System.out.println("Course document checks passed: " + checks);
    }
}
