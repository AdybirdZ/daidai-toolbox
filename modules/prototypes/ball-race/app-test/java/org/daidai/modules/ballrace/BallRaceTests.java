package org.daidai.modules.ballrace;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BallRaceTests {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static BallPhysics.World world(float gravity) {
        return new BallPhysics.World(200f, gravity, 10_000f, 80f, 120f);
    }

    private static BallPhysics.Ball ball(String name, float x, float y, float vx,
                                         float elasticity, boolean ballCollisionEnabled) {
        BallPhysics.Ball ball = new BallPhysics.Ball(name, 0xFFEF4444, x, y, 10f, 1f,
            elasticity, ballCollisionEnabled);
        ball.vx = vx;
        return ball;
    }

    private static void run(BallPhysics.World world, float seconds) {
        int frames = Math.round(seconds / BallPhysics.FIXED_STEP);
        for (int index = 0; index < frames; index++) world.advance(BallPhysics.FIXED_STEP);
    }

    private static RaceCourse oneLevel(RaceCourse.Template template) {
        return new RaceCourse(Collections.singletonList(new RaceCourse.Level(0, template, 0f, 0f)),
            Collections.emptyList());
    }

    public static void main(String[] args) {
        BallPhysics.World fallingWorld = world(100f);
        BallPhysics.Ball falling = ball("falling", 70f, 40f, 12f, .8f, false);
        BallPhysics.Ball other = ball("other", 130f, 40f, -7f, .8f, false);
        fallingWorld.balls.add(falling);
        fallingWorld.balls.add(other);
        fallingWorld.start();
        run(fallingWorld, 1f);
        float time = fallingWorld.elapsedSeconds;
        check(Math.abs(falling.vx - 12f) < .001f && Math.abs(other.vx + 7f) < .001f,
            "No air drag preserves horizontal speed during free fall");
        check(Math.abs(falling.vy - 100f * time) < .05f,
            "Gravity changes vertical speed by g times elapsed time");
        float expectedY = 40f + 50f * time * time + 50f * time * BallPhysics.FIXED_STEP;
        check(Math.abs(falling.y - expectedY) < .12f,
            "Fixed-step gravity integration follows the uniform-acceleration trajectory");

        BallPhysics.World zeroGravityWorld = world(0f);
        BallPhysics.Ball weightless = ball("weightless", 80f, 120f, 0f, .8f, true);
        zeroGravityWorld.balls.add(weightless);
        zeroGravityWorld.balls.add(ball("weightless other", 150f, 120f, 0f, .8f, true));
        zeroGravityWorld.start();
        run(zeroGravityWorld, .5f);
        check(Math.abs(weightless.y - 120f) < .001f && Math.abs(weightless.vy) < .001f,
            "A configurable zero-gravity race keeps balls weightless");

        BallPhysics.World finishWorld = new BallPhysics.World(200f, 980f, 100f, 80f, 120f);
        BallPhysics.Ball finisher = ball("finisher", 100f, 70f, 0f, .8f, true);
        finishWorld.balls.add(finisher);
        finishWorld.balls.add(ball("finish other", 160f, 70f, 0f, .8f, false));
        finishWorld.start();
        run(finishWorld, .5f);
        check(finisher.finished(), "Crossing the finish opening records a finish time");
        check(Math.abs(finisher.vy) > .01f || finisher.y < finishWorld.finishY,
            "A finished ball remains in the physical world after crossing");

        BallPhysics.World fastFinishWorld = new BallPhysics.World(200f, 0f, 100f, 80f, 120f);
        fastFinishWorld.setPhysicsHz(30);
        BallPhysics.Ball fastFinisher = ball("fast finisher", 100f, 40f, 0f, .8f, false);
        fastFinisher.vy = 3000f;
        fastFinishWorld.balls.add(fastFinisher);
        fastFinishWorld.balls.add(ball("fast finish other", 160f, 40f, 0f, .8f, false));
        fastFinishWorld.start();
        fastFinishWorld.advance(1f / 30f);
        check(fastFinisher.finished(), "Adaptive collision steps detect a high-speed finish crossing at 30 Hz");

        BallPhysics.World fastWallWorld = world(0f);
        fastWallWorld.setPhysicsHz(30);
        BallPhysics.Ball fastWallBall = ball("fast wall", 40f, 100f, 3000f, 1f, false);
        fastWallWorld.balls.add(fastWallBall);
        fastWallWorld.balls.add(ball("fast wall other", 160f, 300f, 0f, 1f, false));
        fastWallWorld.segments.add(new BallPhysics.Segment(100f, 0f, 100f, 500f, 2f, 1f, 0f));
        fastWallWorld.start();
        fastWallWorld.advance(1f / 30f);
        check(fastWallBall.x < 100f && fastWallBall.vx < 0f,
            "Adaptive collision steps stop a high-speed ball tunneling through a thin wall at 30 Hz");

        int darkOutline = BallRoster.complementaryContrastColor(0xFF111827);
        check(((darkOutline >>> 16) & 255) > 180,
            "A dark neutral name gets a light outline");
        check(BallRoster.complementaryContrastColor(0xFFFFFFFF) == 0xFF000000,
            "A light neutral name gets a dark outline");
        check((BallRoster.complementaryContrastColor(0xFF1E3A8A) & 0xFFFFFF)
                != (BallRoster.complementaryContrastColor(0xFF93C5FD) & 0xFFFFFF),
            "Dark and light chromatic names get distinct contrast outlines");
        int redOutline = BallRoster.complementaryContrastColor(0xFFEF4444);
        check(((redOutline >>> 8) & 255) > 120 && (redOutline & 255) > 120,
            "A red name gets a visible blue-green complementary outline");

        BallPhysics.World elasticWorld = world(.001f);
        BallPhysics.Ball elasticBall = ball("elastic", 50f, 100f, 80f, 1f, true);
        elasticWorld.balls.add(elasticBall);
        elasticWorld.balls.add(ball("inactive", 160f, 300f, 0f, 1f, false));
        elasticWorld.segments.add(new BallPhysics.Segment(100f, 0f, 100f, 500f, 2f, 1f, 0f));
        elasticWorld.start();
        run(elasticWorld, 1f);
        check(elasticBall.vx < -79f, "A perfectly elastic wall returns the normal speed in the opposite direction");

        BallPhysics.World inelasticWorld = world(.001f);
        BallPhysics.Ball inelasticBall = ball("inelastic", 50f, 100f, 80f, 0f, true);
        inelasticWorld.balls.add(inelasticBall);
        inelasticWorld.balls.add(ball("inactive", 160f, 300f, 0f, 1f, false));
        inelasticWorld.segments.add(new BallPhysics.Segment(100f, 0f, 100f, 500f, 2f, 0f, 0f));
        inelasticWorld.start();
        run(inelasticWorld, 1f);
        check(Math.abs(inelasticBall.vx) < .1f, "A zero-restitution wall removes the normal rebound");

        BallPhysics.World passThroughWorld = world(.001f);
        BallPhysics.Ball passThrough = ball("pass", 50f, 100f, 80f, .8f, false);
        passThroughWorld.balls.add(passThrough);
        passThroughWorld.balls.add(ball("inactive", 160f, 300f, 0f, .8f, false));
        passThroughWorld.segments.add(new BallPhysics.Segment(100f, 0f, 100f, 500f, 2f, 1f, .5f));
        passThroughWorld.start();
        run(passThroughWorld, 1f);
        check(passThrough.x < 100f && passThrough.vx < -60f,
            "Disabling ball-to-ball collision keeps obstacle contact enabled");

        EditableLevel ellipseLevel = EditableLevel.createCustom("custom-physics-ellipse", "椭圆碰撞测试");
        EditableLevel.Obstacle editableEllipse = ellipseLevel.add(EditableLevel.ELLIPSE, 100f, 100f);
        editableEllipse.size = 20f;
        editableEllipse.radiusY = 10f;
        editableEllipse.elasticity = 1f;
        editableEllipse.friction = 0f;
        BallPhysics.World ellipseWorld = new BallPhysics.World(360f, 0f, 1000f, 20f, 340f);
        BallPhysics.Ball ellipseHit = ball("ellipse hit", 50f, 100f, 80f, 1f, false);
        ellipseWorld.balls.add(ellipseHit);
        ellipseWorld.balls.add(ball("ellipse idle", 300f, 400f, 0f, 1f, false));
        ellipseWorld.setCourseObstacles(ellipseLevel.toTemplate(1f).obstacles);
        ellipseWorld.start();
        run(ellipseWorld, .8f);
        check(ellipseHit.x < 100f && ellipseHit.vx < 0f,
            "椭圆障碍会阻止小球穿透并按椭圆法线反弹");

        BallPhysics.World rotatingEllipseWorld = new BallPhysics.World(360f, 0f, 1000f, 20f, 340f);
        rotatingEllipseWorld.setPhysicsHz(30);
        BallPhysics.Ball rotatingEllipseHit = new BallPhysics.Ball("rotating ellipse hit", 0xFFEF4444,
            85.04f, 170.43f, 2f, 1f, .8f, false);
        rotatingEllipseWorld.balls.add(rotatingEllipseHit);
        rotatingEllipseWorld.balls.add(new BallPhysics.Ball("rotating ellipse idle", 0xFF22AA66,
            300f, 400f, 2f, 1f, .8f, false));
        RaceCourse.Obstacle rotatingEllipse = RaceCourse.Obstacle.ellipse("rotating-ellipse",
            100f, 100f, 8f, 72f, 0f, .8f).withProperties(72f, .35f, 0xFF596C83,
                true, true, 0f, 0f, 1f, 0f, 0f, 0f, 720f,
                0f, 0f, RaceCourse.Obstacle.ACTION_BOUNCE, 0f, 0f, .35f);
        rotatingEllipseWorld.setCourseObstacles(Collections.singletonList(rotatingEllipse));
        rotatingEllipseWorld.start();
        run(rotatingEllipseWorld, 1f / 30f);
        check(Math.abs(rotatingEllipseHit.x - 85.04f) > .1f
                || Math.abs(rotatingEllipseHit.y - 170.43f) > .1f,
            "旋转椭圆按较长半径估算碰撞子步，避免长轴扫过小球时漏检");

        EditableLevel movingLevel = EditableLevel.createCustom("custom-moving-slope", "移动斜坡测试");
        EditableLevel.Obstacle movingSlope = movingLevel.add(EditableLevel.SLOPE, 100f, 70f);
        movingSlope.size = 120f;
        movingSlope.thickness = 4f;
        movingSlope.motionY = 30f;
        movingSlope.motionPeriodSeconds = 2f;
        movingSlope.motionPhaseRadians = (float) -Math.PI / 2f;
        BallPhysics.World movingWorld = new BallPhysics.World(360f, 0f, 1000f, 20f, 340f);
        BallPhysics.Ball pushedBySlope = ball("moving contact", 100f, 85f, 0f, .8f, false);
        movingWorld.balls.add(pushedBySlope);
        movingWorld.balls.add(ball("moving idle", 300f, 400f, 0f, .8f, false));
        movingWorld.setCourseObstacles(movingLevel.toTemplate(1f).obstacles);
        movingWorld.start();
        run(movingWorld, .7f);
        check(pushedBySlope.vy > 1f,
            "移动斜坡的表面速度会传给接触中的小球");

        EditableLevel rotatingLevel = EditableLevel.createCustom("custom-rotating-slope", "旋转斜坡测试");
        EditableLevel.Obstacle rotatingSlope = rotatingLevel.add(EditableLevel.SLOPE, 180f, 160f);
        rotatingSlope.size = 120f;
        rotatingSlope.thickness = 12f;
        rotatingSlope.rotationSpeedDegreesPerSecond = 90f;
        BallPhysics.World rotatingWorld = new BallPhysics.World(360f, 0f, 1000f, 20f, 340f);
        BallPhysics.Ball rotatingContact = ball("rotating contact", 210f, 170f, 0f, 1f, false);
        rotatingWorld.balls.add(rotatingContact);
        rotatingWorld.balls.add(ball("rotating idle", 300f, 400f, 0f, 1f, false));
        rotatingWorld.setCourseObstacles(rotatingLevel.toTemplate(1f).obstacles);
        rotatingWorld.start();
        run(rotatingWorld, .02f);
        check(Math.abs(rotatingContact.vx) + Math.abs(rotatingContact.vy) > 1f,
            "绕轴旋转的斜坡会将接触点速度传给小球，实际速度="
                + rotatingContact.vx + "," + rotatingContact.vy);

        EditableLevel impulseLevel = EditableLevel.createCustom("custom-impulse-obstacle", "冲量测试");
        EditableLevel.Obstacle impulseObstacle = impulseLevel.add(EditableLevel.CIRCLE, 100f, 100f);
        impulseObstacle.size = 20f;
        impulseObstacle.elasticity = 0f;
        impulseObstacle.friction = 0f;
        impulseObstacle.collisionAction = RaceCourse.Obstacle.ACTION_IMPULSE;
        impulseObstacle.collisionImpulseX = -35f;
        impulseObstacle.actionCooldownSeconds = .8f;
        BallPhysics.World impulseWorld = new BallPhysics.World(360f, 0f, 1000f, 20f, 340f);
        BallPhysics.Ball impulseHit = ball("impulse hit", 50f, 100f, 60f, 1f, false);
        impulseWorld.balls.add(impulseHit);
        impulseWorld.balls.add(ball("impulse idle", 300f, 400f, 0f, 1f, false));
        impulseWorld.setCourseObstacles(impulseLevel.toTemplate(1f).obstacles);
        impulseWorld.start();
        run(impulseWorld, .6f);
        check(impulseHit.vx < -20f,
            "障碍碰撞动作会对小球施加配置的附加冲量，实际 vx=" + impulseHit.vx + " x=" + impulseHit.x);

        EditableLevel teleportLevel = EditableLevel.createCustom("custom-teleport-obstacle", "传送测试");
        EditableLevel.Obstacle teleportObstacle = teleportLevel.add(EditableLevel.CIRCLE, 100f, 100f);
        teleportObstacle.size = 20f;
        teleportObstacle.collisionAction = RaceCourse.Obstacle.ACTION_TELEPORT;
        teleportObstacle.teleportX = 220f;
        teleportObstacle.teleportY = 260f;
        teleportObstacle.actionCooldownSeconds = .8f;
        BallPhysics.World teleportWorld = new BallPhysics.World(360f, 0f, 1000f, 20f, 340f);
        BallPhysics.Ball teleported = ball("teleported", 50f, 100f, 80f, .8f, false);
        teleportWorld.balls.add(teleported);
        teleportWorld.balls.add(ball("teleport idle", 300f, 400f, 0f, .8f, false));
        teleportWorld.setCourseObstacles(teleportLevel.toTemplate(1f).obstacles);
        teleportWorld.start();
        run(teleportWorld, .35f);
        check(teleported.teleportCount == 1 && teleported.y > 220f,
            "传送障碍按目标坐标移动小球且接触冷却避免连续触发");

        BallPhysics.World pairWorld = world(.001f);
        BallPhysics.Ball left = ball("left", 70f, 100f, 20f, 1f, true);
        BallPhysics.Ball right = ball("right", 110f, 100f, -20f, 1f, true);
        pairWorld.balls.add(left);
        pairWorld.balls.add(right);
        pairWorld.start();
        run(pairWorld, .6f);
        check(left.vx < 0f && right.vx > 0f, "Equal-mass elastic ball collisions exchange direction");
        check(Math.abs(left.vx + right.vx) < .02f, "Ball-pair collisions conserve horizontal momentum");

        BallPhysics.World mixedCollisionWorld = world(.001f);
        BallPhysics.Ball collidable = ball("collidable", 70f, 100f, 20f, 1f, true);
        BallPhysics.Ball passThroughBall = ball("pass-through", 110f, 100f, -20f, 1f, false);
        mixedCollisionWorld.balls.add(collidable);
        mixedCollisionWorld.balls.add(passThroughBall);
        mixedCollisionWorld.start();
        run(mixedCollisionWorld, .6f);
        check(collidable.vx > 0f && passThroughBall.vx < 0f,
            "Disabling ball-to-ball collision lets balls pass through each other");

        BallPhysics.World spinWorld = world(980f);
        BallPhysics.Ball spinning = ball("spinner", 100f, 80f, 0f, .8f, true);
        spinWorld.balls.add(spinning);
        spinWorld.balls.add(ball("inactive", 260f, 80f, 0f, .8f, false));
        spinWorld.segments.add(new BallPhysics.Segment(40f, 140f, 160f, 260f, 4f, .8f, .6f));
        spinWorld.start();
        run(spinWorld, .6f);
        check(Math.abs(spinning.angularVelocity) > .05f,
            "Tangential collision friction changes a ball's angular velocity");

        for (int seed = 0; seed < 30; seed++) {
            RaceCourse course = RaceCourse.generate(seed, 7, .63f);
            check(course.levels.size() == 7 && course.linksAreValid(),
                "Generated levels join matching exit and entry tags for seed " + seed);
            for (RaceCourse.Obstacle obstacle : course.obstacles) {
                check(obstacle.elasticity == .7f, "Built-in obstacles use the default object elasticity");
            }
        }
        List<RaceCourse.Template> subset = Arrays.asList(
            RaceCourse.templates(.4f).get(0), RaceCourse.templates(.4f).get(1), RaceCourse.templates(.4f).get(2));
        RaceCourse noRepeatCourse = RaceCourse.generate(44, 3, subset, false);
        java.util.Set<String> levelIds = new java.util.HashSet<>();
        for (RaceCourse.Level level : noRepeatCourse.levels) levelIds.add(level.template.id);
        check(noRepeatCourse.levels.size() == 3 && levelIds.size() == 3,
            "A course shorter than its candidate pool does not repeat levels");
        List<RaceCourse.Template> allTemplates = RaceCourse.templates(.62f);
        for (int mask = 1; mask < (1 << allTemplates.size()); mask++) {
            List<RaceCourse.Template> candidates = new java.util.ArrayList<>();
            for (int index = 0; index < allTemplates.size(); index++) {
                if ((mask & (1 << index)) != 0) candidates.add(allTemplates.get(index));
            }
            for (int seed = 0; seed < 5; seed++) {
                RaceCourse uniqueCourse = RaceCourse.generate(seed, candidates.size(), candidates, false);
                java.util.Set<String> uniqueIds = new java.util.HashSet<>();
                for (RaceCourse.Level level : uniqueCourse.levels) uniqueIds.add(level.template.id);
                check(uniqueCourse.levels.size() == candidates.size() && uniqueIds.size() == candidates.size(),
                    "Every built-in candidate pool can fill its race without repeating");
            }
        }
        RaceCourse repeatsAllowed = RaceCourse.generate(45, 4, subset, true);
        check(repeatsAllowed.levels.size() == 4, "A course longer than its candidate pool may repeat levels");
        try {
            RaceCourse.generate(45, 4, subset, false);
            throw new AssertionError("No-repeat course beyond candidate count must be rejected");
        } catch (IllegalArgumentException expected) {
            check(true, "No-repeat mode rejects more levels than its candidate pool");
        }

        check(BallRoster.colors().size() == 31, "Palette includes nine three-tone hue families plus black, white and gray shades");
        for (String hue : Arrays.asList("red", "orange", "yellow", "green", "blue", "purple",
                "pink", "blue_green", "turquoise")) {
            check(BallRoster.find(hue + "_light") != null && BallRoster.find(hue) != null
                    && BallRoster.find(hue + "_dark") != null,
                "Each color family has light, medium and dark variants");
        }
        check(BallRoster.find("black") != null && BallRoster.find("white") != null
                && BallRoster.find("black_light") == null && BallRoster.find("white_dark") == null,
            "Black and white are single colors without shade variants");
        List<String> fullPaletteRoster = new java.util.ArrayList<>();
        for (BallRoster.ColorSpec color : BallRoster.colors()) {
            check(BallRoster.add(fullPaletteRoster, color.id), "Every distinct color can be added without a racer cap");
        }
        check(fullPaletteRoster.size() == BallRoster.colors().size()
                && BallRoster.normalize(fullPaletteRoster).size() == BallRoster.colors().size(),
            "Roster normalization preserves all unique racers in the color catalog");
        List<String> roster = BallRoster.parse("red,orange,yellow,green,blue,purple", BallRoster.defaultIds());
        check(roster.size() == 6 && BallRoster.add(roster, "blue_green")
                && !BallRoster.add(roster, "blue_green"),
            "A color variant can appear once in the participant roster");
        check(!BallRoster.remove(roster, "blue_green") || roster.size() >= 2,
            "Participant roster prevents removing below two racers");
        check(BallRoster.normalize(Arrays.asList("red_light", "red_light", "unknown")).size() == 1,
            "Roster storage removes duplicate and unknown ball identifiers");
        RaceCourse course = RaceCourse.generate(21, 4, .5f);
        check(course.levelAt(0f) == course.levels.get(0)
                && course.levelAt(course.levels.get(1).originY) == course.levels.get(1),
            "Course lookup follows actual level origins after the launch area");
        RaceCourse.Level firstLevel = course.levels.get(0);
        BallPhysics.Ball ahead = ball("ahead", 180f, 420f, 0f, .5f, true);
        BallPhysics.Ball behind = ball("behind", 180f, 100f, 0f, .5f, true);
        check(RaceProgress.compare(ahead, behind, course) < 0,
            "On a vertical level, the ball nearer the lower exit ranks ahead");
        check(RaceProgress.leader(Arrays.asList(behind, ahead), course) == ahead
                && RaceProgress.trailer(Arrays.asList(behind, ahead), course) == behind,
            "Leader and trailer follow current level progress");
        ahead.finishTime = 2f;
        BallPhysics.Ball activeAhead = ball("active ahead", 180f, 430f, 0f, .5f, true);
        BallPhysics.Ball activeBehind = ball("active behind", 180f, 280f, 0f, .5f, true);
        check(RaceProgress.leader(Arrays.asList(activeBehind, activeAhead, ahead), course) == ahead,
            "Finished balls remain first in final race ranking");
        check(RaceProgress.leaderAmongUnfinished(Arrays.asList(activeBehind, ahead, activeAhead), course) == activeAhead,
            "Live leader following selects the foremost ball that has not finished");
        BallPhysics.Ball laterName = ball("Zed", 180f, 0f, 0f, .5f, true);
        BallPhysics.Ball earlierName = ball("Amy", 180f, 0f, 0f, .5f, true);
        laterName.finishTime = 8f;
        earlierName.finishTime = 8f;
        check(RaceProgress.compare(earlierName, laterName, course) < 0,
            "Balls crossing in the same simulation step use name order for a deterministic tie");

        RaceCourse.Port bottomEntry = new RaceCourse.Port(RaceCourse.Port.BOTTOM, 180f, "vertical_center");
        RaceCourse.Port rightExit = new RaceCourse.Port(RaceCourse.Port.RIGHT, 240f, "side_center");
        RaceCourse.Template sideTemplate = new RaceCourse.Template("test-side", bottomEntry, rightExit,
            Collections.emptyList());
        RaceCourse sideCourse = oneLevel(sideTemplate);
        BallPhysics.Ball leftAtTie = ball("left", 80f, 220f, 0f, .5f, true);
        BallPhysics.Ball rightAtTie = ball("right", 240f, 260f, 0f, .5f, true);
        check(RaceProgress.compare(leftAtTie, rightAtTie, sideCourse) > 0,
            "For a bottom-to-right turn, the leftmost ball loses a vertical-distance tie");

        for (int count : new int[]{2, 6, 12, 13, 29, 64}) {
            List<RaceCourse.StartPosition> positions = RaceCourse.staggeredStarts(count, 360f, 11f);
            check(positions.size() == count, "Starting formation contains each ball");
            for (int first = 0; first < positions.size(); first++) {
                for (int second = first + 1; second < positions.size(); second++) {
                    RaceCourse.StartPosition a = positions.get(first);
                    RaceCourse.StartPosition b = positions.get(second);
                    float distance = (float) Math.hypot(a.x - b.x, a.y - b.y);
                    check(distance > 22f, "Starting collision circles do not overlap");
                }
            }
            check(positions.get(0).y <= positions.get(positions.size() - 1).y,
                "Starting balls occupy stable launch rows");
        }

        System.out.println("Ball race checks passed: " + checks);
    }
}
