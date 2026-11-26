package org.daidai.prototypes.ballrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 保存可按入口和出口标签纵向拼接的竞速关卡。 */
public final class RaceCourse {
    public static final float WORLD_WIDTH = 360f;
    public static final float LEVEL_HEIGHT = 480f;

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

        public final int shape;
        public final String label;
        public final float x1;
        public final float y1;
        public final float x2;
        public final float y2;
        public final float radius;
        public final float thickness;
        public final float elasticity;

        private Obstacle(int shape, String label, float x1, float y1, float x2, float y2,
                         float radius, float thickness, float elasticity) {
            this.shape = shape;
            this.label = label;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.radius = radius;
            this.thickness = thickness;
            this.elasticity = clamp01(elasticity);
        }

        static Obstacle segment(String label, float x1, float y1, float x2, float y2,
                                float thickness, float elasticity) {
            return new Obstacle(SEGMENT, label, x1, y1, x2, y2, 0f, thickness, elasticity);
        }

        static Obstacle circle(String label, float x, float y, float radius, float elasticity) {
            return new Obstacle(CIRCLE, label, x, y, x, y, radius, 0f, elasticity);
        }
    }

    public static final class Template {
        public final String id;
        public final Port entry;
        public final Port exit;
        public final float width;
        public final float height;
        public final List<Obstacle> obstacles;

        Template(String id, Port entry, Port exit, List<Obstacle> obstacles) {
            this.id = id;
            this.entry = entry;
            this.exit = exit;
            this.width = WORLD_WIDTH;
            this.height = LEVEL_HEIGHT;
            this.obstacles = Collections.unmodifiableList(obstacles);
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
        this.width = WORLD_WIDTH;
        this.height = levels.get(levels.size() - 1).originY + LEVEL_HEIGHT;
    }

    public static RaceCourse generate(long seed, int levelCount, float obstacleElasticity) {
        if (levelCount < 1) throw new IllegalArgumentException("Invalid level count");
        List<Template> library = templates(obstacleElasticity);
        Random random = new Random(seed);
        List<Level> levels = new ArrayList<>();
        List<Obstacle> obstacles = new ArrayList<>();
        Template previous = null;
        float originX = 0f;
        float originY = 0f;

        for (int index = 0; index < levelCount; index++) {
            List<Template> compatible = new ArrayList<>();
            for (Template candidate : library) {
                if (previous == null || previous.connectsTo(candidate)) compatible.add(candidate);
            }
            if (compatible.isEmpty()) throw new IllegalStateException("No compatible next level");
            Template selected = compatible.get(random.nextInt(compatible.size()));
            if (previous != null) originX += previous.exit.offset - selected.entry.offset;
            Level level = new Level(index, selected, originX, originY);
            levels.add(level);
            appendObstacles(obstacles, level);
            previous = selected;
            originY += selected.height;
        }
        return new RaceCourse(levels, obstacles);
    }

    public static List<StartPosition> staggeredStarts(int ballCount, float width, float radius) {
        if (ballCount < 2 || width <= radius * 4f || radius <= 0f) {
            throw new IllegalArgumentException("Invalid starting grid");
        }
        int columns = Math.min(4, ballCount - 1);
        float spacing = Math.min(56f, (width - radius * 4f) / Math.max(1, columns - 1));
        float startX = (width - spacing * (columns - 1)) * .5f;
        float verticalSpacing = radius * 2f + 3f;
        List<StartPosition> positions = new ArrayList<>();
        for (int index = 0; index < ballCount; index++) {
            int row = index / columns;
            int column = index % columns;
            float rowOffset = row % 2 == 0 ? 0f : spacing * .5f;
            positions.add(new StartPosition(startX + column * spacing + rowOffset,
                32f + row * verticalSpacing));
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
                obstacle.radius, obstacle.thickness, obstacle.elasticity));
        }
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private RaceCourse() {
        throw new AssertionError();
    }
}
