package org.daidai.prototypes.ballrace;

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

        BallPhysics.World finishWorld = new BallPhysics.World(200f, 980f, 100f, 80f, 120f);
        BallPhysics.Ball finisher = ball("finisher", 100f, 70f, 0f, .8f, true);
        finishWorld.balls.add(finisher);
        finishWorld.balls.add(ball("finish other", 160f, 70f, 0f, .8f, false));
        finishWorld.start();
        run(finishWorld, .5f);
        check(finisher.finished(), "Crossing the finish opening records a finish time");
        check(Math.abs(finisher.vy) > .01f || finisher.y < finishWorld.finishY,
            "A finished ball remains in the physical world after crossing");

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
        RaceCourse course = RaceCourse.generate(21, 4, .5f);
        check(course.levelAt(0f) == course.levels.get(0)
                && course.levelAt(course.levels.get(1).originY) == course.levels.get(1),
            "关卡查找使用真实原点并包含上方发车区");
        RaceCourse.Level firstLevel = course.levels.get(0);
        BallPhysics.Ball ahead = ball("ahead", 180f, 420f, 0f, .5f, true);
        BallPhysics.Ball behind = ball("behind", 180f, 100f, 0f, .5f, true);
        check(RaceProgress.compare(ahead, behind, course) < 0,
            "On a vertical level, the ball nearer the lower exit ranks ahead");
        check(RaceProgress.leader(Arrays.asList(behind, ahead), course) == ahead
                && RaceProgress.trailer(Arrays.asList(behind, ahead), course) == behind,
            "Leader and trailer follow current level progress");
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
