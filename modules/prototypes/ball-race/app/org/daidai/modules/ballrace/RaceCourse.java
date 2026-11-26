package org.daidai.modules.ballrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

/** 首版使用带有命名连接点、可纵向拼接的竞速关卡。 */
public final class RaceCourse {
    public static final float WORLD_WIDTH = 360f;
    public static final float LEVEL_HEIGHT = 480f;
    public static final float START_AREA_HEIGHT = 400f;

    public static final class Port {
        public static final int TOP = 0;
        public static final int RIGHT = 1;
        public static final int BOTTOM = 2;
        public static final int LEFT = 3;

        public final int side;
        public final float offset;
        public final String label;

        Port(int side, float offset, String label) {
            this.side = side;
            this.offset = offset;
            this.label = label;
        }

        boolean faces(Port other) {
            return (side + 2) % 4 == other.side;
        }
    }

    public static final class Obstacle {
        public static final int SEGMENT = 0;
        public static final int CIRCLE = 1;
        public static final int ARC = 2;
        public static final int ELLIPSE = 3;
        public static final int ACTION_BOUNCE = 0;
        public static final int ACTION_IMPULSE = 1;
        public static final int ACTION_TELEPORT = 2;

        public final int shape;
        public final String label;
        public final float x1;
        public final float y1;
        public final float x2;
        public final float y2;
        public final float radius;
        public final float radiusY;
        public final float thickness;
        public final float elasticity;
        public final float friction;
        public final float arcDegrees;
        public final float rotationDegrees;
        public final boolean rounded;
        public final int color;
        public final boolean visible;
        public final boolean collisionEnabled;
        public final float motionX;
        public final float motionY;
        public final float motionPeriodSeconds;
        public final float motionPhaseRadians;
        public final float pivotOffsetX;
        public final float pivotOffsetY;
        public final float rotationSpeedDegreesPerSecond;
        public final float collisionImpulseX;
        public final float collisionImpulseY;
        public final int collisionAction;
        public final float teleportX;
        public final float teleportY;
        public final float actionCooldownSeconds;

        private Obstacle(int shape, String label, float x1, float y1, float x2, float y2,
                         float radius, float radiusY, float thickness, float elasticity, float friction,
                         float arcDegrees, float rotationDegrees, boolean rounded, int color,
                         boolean visible, boolean collisionEnabled, float motionX, float motionY,
                         float motionPeriodSeconds, float motionPhaseRadians, float pivotOffsetX,
                         float pivotOffsetY, float rotationSpeedDegreesPerSecond,
                         float collisionImpulseX, float collisionImpulseY,
                         int collisionAction, float teleportX, float teleportY,
                         float actionCooldownSeconds) {
            this.shape = shape;
            this.label = label;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.radius = radius;
            this.radiusY = radiusY;
            this.thickness = thickness;
            this.elasticity = clamp01(elasticity);
            this.friction = clamp01(friction);
            this.arcDegrees = arcDegrees;
            this.rotationDegrees = rotationDegrees;
            this.rounded = rounded;
            this.color = color;
            this.visible = visible;
            this.collisionEnabled = collisionEnabled;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionPeriodSeconds = motionPeriodSeconds;
            this.motionPhaseRadians = motionPhaseRadians;
            this.pivotOffsetX = pivotOffsetX;
            this.pivotOffsetY = pivotOffsetY;
            this.rotationSpeedDegreesPerSecond = rotationSpeedDegreesPerSecond;
            this.collisionImpulseX = collisionImpulseX;
            this.collisionImpulseY = collisionImpulseY;
            this.collisionAction = collisionAction;
            this.teleportX = teleportX;
            this.teleportY = teleportY;
            this.actionCooldownSeconds = actionCooldownSeconds;
        }

        static Obstacle segment(String label, float x1, float y1, float x2, float y2,
                                float thickness, float elasticity) {
            return segment(label, x1, y1, x2, y2, thickness, elasticity, true);
        }

        static Obstacle segment(String label, float x1, float y1, float x2, float y2,
                                float thickness, float elasticity, boolean rounded) {
            return new Obstacle(SEGMENT, label, x1, y1, x2, y2, 0f, 0f, thickness, elasticity,
                .35f, 0f, 0f, rounded, 0xFF596C83, true, true, 0f, 0f, 1f, 0f,
                0f, 0f, 0f, 0f, 0f, ACTION_BOUNCE, 0f, 0f, .35f);
        }

        static Obstacle circle(String label, float x, float y, float radius, float elasticity) {
            return new Obstacle(CIRCLE, label, x, y, x, y, radius, radius, 0f, elasticity,
                .35f, 0f, 0f, true, 0xFF596C83, true, true, 0f, 0f, 1f, 0f,
                0f, 0f, 0f, 0f, 0f, ACTION_BOUNCE, 0f, 0f, .35f);
        }

        static Obstacle ellipse(String label, float x, float y, float radiusX, float radiusY, float elasticity) {
            return ellipse(label, x, y, radiusX, radiusY, 0f, elasticity);
        }

        static Obstacle ellipse(String label, float x, float y, float radiusX, float radiusY,
                                float rotation, float elasticity) {
            return new Obstacle(ELLIPSE, label, x, y, x, y, radiusX, radiusY, 0f, elasticity,
                .35f, 0f, rotation, true, 0xFF596C83, true, true, 0f, 0f, 1f, 0f,
                0f, 0f, 0f, 0f, 0f, ACTION_BOUNCE, 0f, 0f, .35f);
        }

        static Obstacle arc(String label, float x, float y, float radius, float thickness,
                            float degrees, float rotation, float elasticity, boolean rounded) {
            return new Obstacle(ARC, label, x, y, x, y, radius, radius, thickness, elasticity,
                .35f, degrees, rotation, rounded, 0xFF596C83, true, true, 0f, 0f, 1f, 0f,
                0f, 0f, 0f, 0f, 0f, ACTION_BOUNCE, 0f, 0f, .35f);
        }

        Obstacle withProperties(float radiusY, float friction, int color, boolean visible,
                                boolean collisionEnabled, float motionX, float motionY,
                                float motionPeriodSeconds, float motionPhaseRadians,
                                float pivotOffsetX, float pivotOffsetY,
                                float rotationSpeedDegreesPerSecond,
                                float collisionImpulseX, float collisionImpulseY) {
            return withProperties(radiusY, friction, color, visible, collisionEnabled, motionX, motionY,
                motionPeriodSeconds, motionPhaseRadians, pivotOffsetX, pivotOffsetY,
                rotationSpeedDegreesPerSecond, collisionImpulseX, collisionImpulseY,
                ACTION_BOUNCE, 0f, 0f, .35f);
        }

        Obstacle withProperties(float radiusY, float friction, int color, boolean visible,
                                boolean collisionEnabled, float motionX, float motionY,
                                float motionPeriodSeconds, float motionPhaseRadians,
                                float pivotOffsetX, float pivotOffsetY,
                                float rotationSpeedDegreesPerSecond,
                                float collisionImpulseX, float collisionImpulseY,
                                int collisionAction, float teleportX, float teleportY,
                                float actionCooldownSeconds) {
            return new Obstacle(shape, label, x1, y1, x2, y2, radius, radiusY, thickness,
                elasticity, friction, arcDegrees, rotationDegrees, rounded, color, visible,
                collisionEnabled, motionX, motionY, motionPeriodSeconds, motionPhaseRadians,
                pivotOffsetX, pivotOffsetY, rotationSpeedDegreesPerSecond, collisionImpulseX,
                collisionImpulseY, collisionAction, teleportX, teleportY, actionCooldownSeconds);
        }

        public State stateAt(float seconds) {
            float centerX = shape == SEGMENT ? (x1 + x2) * .5f : x1;
            float centerY = shape == SEGMENT ? (y1 + y2) * .5f : y1;
            float angularSpeed = (float) Math.toRadians(rotationSpeedDegreesPerSecond);
            float period = motionPeriodSeconds > 0f ? motionPeriodSeconds : 1f;
            float phase = motionPhaseRadians + (float) (Math.PI * 2d * seconds / period);
            float offsetX = motionX * (float) Math.sin(phase);
            float offsetY = motionY * (float) Math.sin(phase);
            float moveVx = motionX * (float) Math.cos(phase) * (float) (Math.PI * 2d / period);
            float moveVy = motionY * (float) Math.cos(phase) * (float) (Math.PI * 2d / period);
            float angle = angularSpeed * seconds;
            float pivotX = centerX + pivotOffsetX;
            float pivotY = centerY + pivotOffsetY;
            float relativeX = centerX - pivotX;
            float relativeY = centerY - pivotY;
            float rotatedCenterX = pivotX + relativeX * (float) Math.cos(angle) - relativeY * (float) Math.sin(angle);
            float rotatedCenterY = pivotY + relativeX * (float) Math.sin(angle) + relativeY * (float) Math.cos(angle);
            float velocityX = moveVx - angularSpeed * (rotatedCenterY - pivotY);
            float velocityY = moveVy + angularSpeed * (rotatedCenterX - pivotX);
            float firstX = x1;
            float firstY = y1;
            float secondX = x2;
            float secondY = y2;
            if (shape == SEGMENT) {
                float[] first = rotate(x1 - pivotX, y1 - pivotY, angle);
                float[] second = rotate(x2 - pivotX, y2 - pivotY, angle);
                firstX = pivotX + first[0] + offsetX;
                firstY = pivotY + first[1] + offsetY;
                secondX = pivotX + second[0] + offsetX;
                secondY = pivotY + second[1] + offsetY;
            }
            return new State(rotatedCenterX + offsetX, rotatedCenterY + offsetY,
                firstX, firstY, secondX, secondY,
                normalizeDegrees(rotationDegrees + rotationSpeedDegreesPerSecond * seconds),
                velocityX, velocityY, rotationSpeedDegreesPerSecond,
                pivotX + offsetX, pivotY + offsetY, moveVx, moveVy, offsetX, offsetY);
        }

        private static float[] rotate(float x, float y, float angle) {
            float cosine = (float) Math.cos(angle);
            float sine = (float) Math.sin(angle);
            return new float[] { x * cosine - y * sine, x * sine + y * cosine };
        }

        private static float normalizeDegrees(float value) {
            float result = value % 360f;
            return result < 0f ? result + 360f : result;
        }

        public static final class State {
            public final float x;
            public final float y;
            public final float x1;
            public final float y1;
            public final float x2;
            public final float y2;
            public final float rotationDegrees;
            public final float velocityX;
            public final float velocityY;
            public final float angularVelocityDegreesPerSecond;
            public final float pivotX;
            public final float pivotY;
            public final float pivotVelocityX;
            public final float pivotVelocityY;
            public final float offsetX;
            public final float offsetY;

            private State(float x, float y, float x1, float y1, float x2, float y2,
                          float rotationDegrees, float velocityX, float velocityY,
                          float angularVelocityDegreesPerSecond, float pivotX, float pivotY,
                          float pivotVelocityX, float pivotVelocityY, float offsetX, float offsetY) {
                this.x = x;
                this.y = y;
                this.x1 = x1;
                this.y1 = y1;
                this.x2 = x2;
                this.y2 = y2;
                this.rotationDegrees = rotationDegrees;
                this.velocityX = velocityX;
                this.velocityY = velocityY;
                this.angularVelocityDegreesPerSecond = angularVelocityDegreesPerSecond;
                this.pivotX = pivotX;
                this.pivotY = pivotY;
                this.pivotVelocityX = pivotVelocityX;
                this.pivotVelocityY = pivotVelocityY;
                this.offsetX = offsetX;
                this.offsetY = offsetY;
            }
        }
    }

    public static final class TextObject {
        public final String text;
        public final float x, y, size, rotationDegrees;
        public final int color;
        TextObject(String text, float x, float y, float size, float rotationDegrees, int color) {
            this.text = text; this.x = x; this.y = y; this.size = size;
            this.rotationDegrees = rotationDegrees; this.color = color;
        }
    }

    public static final class Template {
        public final String id;
        public final String name;
        public final Port entry;
        public final Port exit;
        public final float width;
        public final float height;
        public final List<Obstacle> obstacles;
        public final List<TextObject> texts;

        Template(String id, Port entry, Port exit, List<Obstacle> obstacles) {
            this(id, id, entry, exit, WORLD_WIDTH, LEVEL_HEIGHT, obstacles);
        }

        Template(String id, String name, Port entry, Port exit, List<Obstacle> obstacles) {
            this(id, name, entry, exit, WORLD_WIDTH, LEVEL_HEIGHT, obstacles);
        }

        Template(String id, String name, Port entry, Port exit, float width, float height, List<Obstacle> obstacles) {
            this(id, name, entry, exit, width, height, obstacles, Collections.emptyList());
        }

        Template(String id, String name, Port entry, Port exit, float width, float height,
                 List<Obstacle> obstacles, List<TextObject> texts) {
            this.id = id;
            this.name = name == null ? id : name;
            this.entry = entry;
            this.exit = exit;
            this.width = width;
            this.height = height;
            this.obstacles = Collections.unmodifiableList(obstacles);
            this.texts = Collections.unmodifiableList(texts);
        }

        boolean connectsTo(Template next) {
            return exit.label.equals(next.entry.label)
                && exit.faces(next.entry)
                && Math.abs(exit.offset - next.entry.offset) <= width * .18f;
        }
    }

    public static final class Level {
        public final int index;
        public final Template template;
        public final float originX;
        public final float originY;

        Level(int index, Template template, float originX, float originY) {
            this.index = index;
            this.template = template;
            this.originX = originX;
            this.originY = originY;
        }

        public float exitX() {
            return originX + template.exit.offset;
        }

        public float exitY() {
            return originY + template.height;
        }

        public float localY(float worldY) {
            return worldY - originY;
        }
    }

    public static final class StartPosition {
        public final float x;
        public final float y;

        private StartPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public final List<Level> levels;
    public final List<Obstacle> obstacles;
    public final float width;
    public final float height;

    RaceCourse(List<Level> levels, List<Obstacle> obstacles) {
        this.levels = Collections.unmodifiableList(levels);
        this.obstacles = Collections.unmodifiableList(obstacles);
        float widest = WORLD_WIDTH;
        float bottom = 0f;
        for (Level level : levels) {
            widest = Math.max(widest, level.template.width);
            bottom = Math.max(bottom, level.originY + level.template.height);
        }
        this.width = widest;
        this.height = bottom;
    }

    public static RaceCourse generate(long seed, int levelCount, float obstacleElasticity) {
        return generate(seed, levelCount, templates(obstacleElasticity));
    }

    /** 使用关卡编辑器保存的模板生成一场竞速。 */
    public static RaceCourse generate(long seed, int levelCount, List<Template> library) {
        return generate(seed, levelCount, library, true);
    }

    /** 按候选关卡数量决定是否允许在一场比赛内重复抽取。 */
    public static RaceCourse generate(long seed, int levelCount, List<Template> library, boolean allowRepeats) {
        if (levelCount < 1) throw new IllegalArgumentException("Invalid level count");
        if (library == null || library.isEmpty()) throw new IllegalArgumentException("关卡模板不能为空");
        Set<String> templateIds = new HashSet<>();
        for (Template template : library) {
            if (template == null || template.id == null || !templateIds.add(template.id)) {
                throw new IllegalArgumentException("关卡候选编号必须唯一");
            }
        }
        if (!allowRepeats && levelCount > library.size()) {
            throw new IllegalArgumentException("关卡数量超过不重复候选数量");
        }
        Random random = new Random(seed);
        List<Level> levels = new ArrayList<>();
        List<Obstacle> obstacles = new ArrayList<>();
        Template previous = null;
        Set<String> usedIds = new HashSet<>();
        float originX = 0f;
        float originY = START_AREA_HEIGHT;

        for (int index = 0; index < levelCount; index++) {
            List<Template> compatible = new ArrayList<>();
            for (Template candidate : library) {
                if ((allowRepeats || !usedIds.contains(candidate.id))
                        && (previous == null || previous.connectsTo(candidate))) compatible.add(candidate);
            }
            if (compatible.isEmpty()) throw new IllegalStateException("No compatible next level");
            Template selected = compatible.get(random.nextInt(compatible.size()));
            if (previous != null) originX += previous.exit.offset - selected.entry.offset;
            Level level = new Level(index, selected, originX, originY);
            levels.add(level);
            appendObstacles(obstacles, level);
            usedIds.add(selected.id);
            previous = selected;
            originY += selected.height;
        }
        return new RaceCourse(levels, obstacles);
    }

    public static List<StartPosition> staggeredStarts(int ballCount, float width, float radius) {
        if (ballCount < 2 || width <= radius * 4f || radius <= 0f) {
            throw new IllegalArgumentException("Invalid starting grid");
        }
        int columns = Math.min(8, Math.max(2, ballCount));
        float spacing = Math.min(radius * 2.15f, (width - radius * 4f) / Math.max(1, columns - 1));
        float startX = (width - spacing * (columns - 1)) * .5f;
        float verticalSpacing = radius * 2f + 1f;
        List<StartPosition> positions = new ArrayList<>();
        for (int index = 0; index < ballCount; index++) {
            int row = index / columns;
            int column = index % columns;
            float rowOffset = row % 2 == 0 ? 0f : spacing * .5f;
            positions.add(new StartPosition(startX + column * spacing + rowOffset,
                24f + row * verticalSpacing));
        }
        return Collections.unmodifiableList(positions);
    }

    public Level levelAt(float worldY) {
        Level selected = levels.get(0);
        for (Level level : levels) {
            if (worldY < level.originY) break;
            selected = level;
        }
        return selected;
    }

    public boolean linksAreValid() {
        for (int index = 0; index + 1 < levels.size(); index++) {
            Level current = levels.get(index);
            Level next = levels.get(index + 1);
            if (!current.template.connectsTo(next.template)) return false;
            if (Math.abs(current.exitX() - (next.originX + next.template.entry.offset)) > .001f) return false;
            if (Math.abs(current.exitY() - (next.originY)) > .001f) return false;
        }
        return true;
    }

    public static List<Template> templates(float elasticity) {
        float e = .7f;
        List<Template> result = new ArrayList<>();
        Port entry = new Port(Port.TOP, WORLD_WIDTH * .5f, "vertical_center");
        Port exit = new Port(Port.BOTTOM, WORLD_WIDTH * .5f, "vertical_center");
        result.add(template("slopes", entry, exit,
            Obstacle.segment("slope-a", 42, 118, 210, 150, 10, e),
            Obstacle.segment("slope-b", 148, 262, 318, 224, 10, e),
            Obstacle.circle("bumper-a", 88, 333, 24, e)));
        result.add(template("bumpers", entry, exit,
            Obstacle.circle("bumper-a", 112, 126, 27, e),
            Obstacle.circle("bumper-b", 251, 222, 25, e),
            Obstacle.segment("slope-a", 45, 316, 190, 279, 10, e),
            Obstacle.circle("bumper-c", 171, 385, 20, e)));
        result.add(template("rails", entry, exit,
            Obstacle.segment("rail-a", 80, 106, 300, 142, 11, e),
            Obstacle.segment("rail-b", 28, 238, 236, 211, 11, e),
            Obstacle.segment("rail-c", 132, 365, 334, 329, 11, e)));
        result.add(template("zigzag", entry, exit,
            Obstacle.segment("slope-a", 26, 138, 168, 178, 10, e),
            Obstacle.segment("slope-b", 192, 250, 334, 210, 10, e),
            Obstacle.segment("slope-c", 35, 344, 194, 306, 10, e),
            Obstacle.circle("bumper-a", 270, 375, 19, e)));
        result.add(template("center-pegs", entry, exit,
            Obstacle.circle("peg-a", 180, 117, 18, e),
            Obstacle.circle("peg-b", 128, 213, 18, e),
            Obstacle.circle("peg-c", 232, 309, 18, e),
            Obstacle.segment("slope-a", 36, 393, 146, 366, 10, e)));
        return result;
    }

    private static Template template(String id, Port entry, Port exit, Obstacle... pieces) {
        List<Obstacle> obstacles = new ArrayList<>();
        Collections.addAll(obstacles, pieces);
        return new Template(id, entry, exit, obstacles);
    }

    private static void appendObstacles(List<Obstacle> target, Level level) {
        for (Obstacle obstacle : level.template.obstacles) {
            target.add(new Obstacle(obstacle.shape, level.index + ":" + obstacle.label,
                obstacle.x1 + level.originX, obstacle.y1 + level.originY,
                obstacle.x2 + level.originX, obstacle.y2 + level.originY,
                obstacle.radius, obstacle.radiusY, obstacle.thickness, obstacle.elasticity,
                obstacle.friction, obstacle.arcDegrees, obstacle.rotationDegrees, obstacle.rounded,
                obstacle.color, obstacle.visible, obstacle.collisionEnabled, obstacle.motionX,
                obstacle.motionY, obstacle.motionPeriodSeconds, obstacle.motionPhaseRadians,
                obstacle.pivotOffsetX, obstacle.pivotOffsetY, obstacle.rotationSpeedDegreesPerSecond,
                obstacle.collisionImpulseX, obstacle.collisionImpulseY, obstacle.collisionAction,
                obstacle.teleportX + level.originX, obstacle.teleportY + level.originY,
                obstacle.actionCooldownSeconds));
        }
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private RaceCourse() {
        throw new AssertionError();
    }
}
