package org.daidai.modules.ballrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 使用固定步长计算二维刚性圆盘运动，不考虑空气阻力。坐标采用近似 dp 的关卡单位。 */
public final class BallPhysics {
    public static final float FIXED_STEP = 1f / 120f;
    private static final int MAX_SUBSTEPS_PER_FRAME = 30;
    private static final int SOLVER_PASSES = 4;
    private static final float POSITION_SLOP = .015f;
    private static final float POSITION_CORRECTION = .82f;
    private static final float RESTING_SPEED = 1.5f;

    public static final class Ball {
        public final String name;
        public final int color;
        public final float radius;
        public final float mass;
        public final float elasticity;
        public final boolean ballCollisionEnabled;
        public float x;
        public float y;
        public float vx;
        public float vy;
        public float angularVelocity;
        public float finishTime = -1f;

        public Ball(String name, int color, float x, float y, float radius, float mass,
                    float elasticity, boolean ballCollisionEnabled) {
            if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Ball name is required");
            if (!positive(radius) || !positive(mass) || !unit(elasticity)) throw new IllegalArgumentException("Invalid ball physics");
            this.name = name;
            this.color = color;
            this.x = finite(x);
            this.y = finite(y);
            this.radius = radius;
            this.mass = mass;
            this.elasticity = elasticity;
            this.ballCollisionEnabled = ballCollisionEnabled;
        }

        public float inverseMass() {
            return 1f / mass;
        }

        /** 二维游戏模型将小球视为均匀实心圆盘。 */
        public float inverseMoment() {
            return 2f / (mass * radius * radius);
        }

        public boolean finished() {
            return finishTime >= 0f;
        }
    }

    public static final class Segment {
        public final float x1;
        public final float y1;
        public final float x2;
        public final float y2;
        public final float radius;
        public final float elasticity;
        public final float friction;

        public Segment(float x1, float y1, float x2, float y2, float thickness,
                       float elasticity, float friction) {
            if (!positive(thickness) || !unit(elasticity) || !unit(friction)) {
                throw new IllegalArgumentException("Invalid obstacle physics");
            }
            if (x1 == x2 && y1 == y2) throw new IllegalArgumentException("Obstacle segment has no length");
            this.x1 = finite(x1);
            this.y1 = finite(y1);
            this.x2 = finite(x2);
            this.y2 = finite(y2);
            this.radius = thickness * .5f;
            this.elasticity = elasticity;
            this.friction = friction;
        }
    }

    public static final class CircleObstacle {
        public final float x;
        public final float y;
        public final float radius;
        public final float elasticity;
        public final float friction;

        public CircleObstacle(float x, float y, float radius, float elasticity, float friction) {
            if (!positive(radius) || !unit(elasticity) || !unit(friction)) {
                throw new IllegalArgumentException("Invalid obstacle physics");
            }
            this.x = finite(x);
            this.y = finite(y);
            this.radius = radius;
            this.elasticity = elasticity;
            this.friction = friction;
        }
    }

    public static final class World {
        public final float width;
        public final float gravity;
        public final float finishY;
        public final float finishLeft;
        public final float finishRight;
        private final Segment leftFinishFloor;
        private final Segment rightFinishFloor;
        public final List<Ball> balls = new ArrayList<>();
        public final List<Segment> segments = new ArrayList<>();
        public final List<CircleObstacle> circles = new ArrayList<>();
        public float elapsedSeconds;
        public float timeLimitSeconds = 180f;
        public boolean running;
        public boolean timedOut;
        private float accumulator;

        public World(float width, float gravity, float finishY, float finishLeft, float finishRight) {
            if (!positive(width) || !Float.isFinite(gravity) || gravity < 0f || !positive(finishY)
                    || finishLeft < 0f || finishRight > width || finishLeft >= finishRight) {
                throw new IllegalArgumentException("Invalid race world");
            }
            this.width = width;
            this.gravity = gravity;
            this.finishY = finishY;
            this.finishLeft = finishLeft;
            this.finishRight = finishRight;
            this.leftFinishFloor = new Segment(0f, finishY, finishLeft, finishY, 2f, 0f, .35f);
            this.rightFinishFloor = new Segment(finishRight, finishY, width, finishY, 2f, 0f, .35f);
        }

        public void start() {
            if (balls.size() < 2) throw new IllegalStateException("A race needs at least two balls");
            running = true;
        }

        public void pause() {
            running = false;
        }

        public void resetClock() {
            elapsedSeconds = 0f;
            accumulator = 0f;
            timedOut = false;
            for (Ball ball : balls) ball.finishTime = -1f;
        }

        public List<Ball> standings() {
            List<Ball> result = new ArrayList<>(balls);
            result.sort((left, right) -> {
                if (left.finished() != right.finished()) return left.finished() ? -1 : 1;
                if (left.finished()) {
                    int finishOrder = Float.compare(left.finishTime, right.finishTime);
                    return finishOrder != 0 ? finishOrder : left.name.compareTo(right.name);
                }
                int progress = Float.compare(right.y, left.y);
                if (progress != 0) return progress;
                return left.name.compareTo(right.name);
            });
            return Collections.unmodifiableList(result);
        }

        /** 输入经过时间，并按稳定的 120 Hz 固定步长推进仿真。 */
        public void advance(float frameSeconds) {
            if (!running || !Float.isFinite(frameSeconds) || frameSeconds <= 0f) return;
            accumulator += Math.min(frameSeconds, .25f);
            int steps = 0;
            while (accumulator >= FIXED_STEP && steps < MAX_SUBSTEPS_PER_FRAME && running) {
                fixedStep(FIXED_STEP);
                accumulator -= FIXED_STEP;
                steps++;
            }
            if (steps == MAX_SUBSTEPS_PER_FRAME && accumulator > FIXED_STEP * MAX_SUBSTEPS_PER_FRAME) {
                accumulator = FIXED_STEP * MAX_SUBSTEPS_PER_FRAME;
            }
        }

        private void fixedStep(float dt) {
            elapsedSeconds += dt;
            for (Ball ball : balls) {
                if (ball.finished()) continue;
                ball.vy += gravity * dt;
                ball.x += ball.vx * dt;
                ball.y += ball.vy * dt;
            }

            for (int pass = 0; pass < SOLVER_PASSES; pass++) {
                for (Ball ball : balls) {
                    if (!ball.finished()) {
                        // 障碍物与边界始终碰撞，开关只控制球与球之间的碰撞。
                        for (Segment segment : segments) collide(ball, segment);
                        for (CircleObstacle circle : circles) collide(ball, circle);
                    }
                }
                for (int first = 0; first < balls.size(); first++) {
                    Ball a = balls.get(first);
                    if (a.finished() || !a.ballCollisionEnabled) continue;
                    for (int second = first + 1; second < balls.size(); second++) {
                        Ball b = balls.get(second);
                        if (!b.finished() && b.ballCollisionEnabled) collide(a, b);
                    }
                }
            }

            for (Ball ball : balls) {
                if (ball.finished()) continue;
                float openingLeft = finishLeft + ball.radius;
                float openingRight = finishRight - ball.radius;
                if (ball.y + ball.radius >= finishY && ball.x >= openingLeft && ball.x <= openingRight) {
                    ball.y = Math.min(ball.y, finishY);
                    ball.finishTime = elapsedSeconds;
                } else if (ball.y + ball.radius >= finishY) {
                    collideWithFinishFloor(ball);
                }
                if (ball.x < -100f || ball.x > width + 100f || ball.y > finishY + 300f) {
                    ball.finishTime = elapsedSeconds;
                }
            }

            boolean allFinished = true;
            for (Ball ball : balls) allFinished &= ball.finished();
            if (elapsedSeconds >= timeLimitSeconds && !allFinished) {
                timedOut = true;
                running = false;
            } else if (allFinished) {
                running = false;
            }
        }

        private void collideWithFinishFloor(Ball ball) {
            if (ball.x < finishLeft) {
                collide(ball, leftFinishFloor);
            } else if (ball.x > finishRight) {
                collide(ball, rightFinishFloor);
            } else {
                float leftClearance = ball.x - finishLeft;
                float rightClearance = finishRight - ball.x;
                if (leftClearance < rightClearance) collide(ball, leftFinishFloor);
                else collide(ball, rightFinishFloor);
            }
        }
    }

    private static void collide(Ball ball, CircleObstacle obstacle) {
        float dx = ball.x - obstacle.x;
        float dy = ball.y - obstacle.y;
        float distanceSquared = dx * dx + dy * dy;
        float combinedRadius = ball.radius + obstacle.radius;
        if (distanceSquared >= combinedRadius * combinedRadius) return;
        float distance = (float) Math.sqrt(distanceSquared);
        float nx;
        float ny;
        if (distance > 1e-6f) {
            nx = dx / distance;
            ny = dy / distance;
        } else {
            nx = 0f;
            ny = -1f;
        }
        resolveStaticContact(ball, nx, ny, combinedRadius - distance,
            geometricMean(ball.elasticity, obstacle.elasticity), obstacle.friction);
    }

    private static void collide(Ball ball, Segment segment) {
        float dx = segment.x2 - segment.x1;
        float dy = segment.y2 - segment.y1;
        float lengthSquared = dx * dx + dy * dy;
        float along = ((ball.x - segment.x1) * dx + (ball.y - segment.y1) * dy) / lengthSquared;
        along = Math.max(0f, Math.min(1f, along));
        float closestX = segment.x1 + dx * along;
        float closestY = segment.y1 + dy * along;
        float offsetX = ball.x - closestX;
        float offsetY = ball.y - closestY;
        float distanceSquared = offsetX * offsetX + offsetY * offsetY;
        float combinedRadius = ball.radius + segment.radius;
        if (distanceSquared >= combinedRadius * combinedRadius) return;
        float distance = (float) Math.sqrt(distanceSquared);
        float nx;
        float ny;
        if (distance > 1e-6f) {
            nx = offsetX / distance;
            ny = offsetY / distance;
        } else {
            float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
            nx = -dy * inverseLength;
            ny = dx * inverseLength;
            if (ball.vx * nx + ball.vy * ny > 0f) {
                nx = -nx;
                ny = -ny;
            }
        }
        resolveStaticContact(ball, nx, ny, combinedRadius - distance,
            geometricMean(ball.elasticity, segment.elasticity), segment.friction);
    }

    private static void resolveStaticContact(Ball ball, float nx, float ny,
                                             float penetration, float elasticity, float friction) {
        float rx = -nx * ball.radius;
        float ry = -ny * ball.radius;
        float contactVx = ball.vx - ball.angularVelocity * ry;
        float contactVy = ball.vy + ball.angularVelocity * rx;
        float normalSpeed = contactVx * nx + contactVy * ny;
        float normalImpulse = 0f;
        if (normalSpeed < 0f) {
            float bounce = Math.abs(normalSpeed) < RESTING_SPEED ? 0f : elasticity;
            float normalDenominator = ball.inverseMass()
                + square(cross(rx, ry, nx, ny)) * ball.inverseMoment();
            normalImpulse = -(1f + bounce) * normalSpeed / normalDenominator;
            applyImpulse(ball, nx * normalImpulse, ny * normalImpulse, rx, ry);

            float tx = -ny;
            float ty = nx;
            contactVx = ball.vx - ball.angularVelocity * ry;
            contactVy = ball.vy + ball.angularVelocity * rx;
            float tangentSpeed = contactVx * tx + contactVy * ty;
            float tangentDenominator = ball.inverseMass()
                + square(cross(rx, ry, tx, ty)) * ball.inverseMoment();
            float tangentImpulse = -tangentSpeed / tangentDenominator;
            float limit = friction * normalImpulse;
            tangentImpulse = Math.max(-limit, Math.min(limit, tangentImpulse));
            applyImpulse(ball, tx * tangentImpulse, ty * tangentImpulse, rx, ry);
        }
        float correction = Math.max(penetration - POSITION_SLOP, 0f) * POSITION_CORRECTION;
        ball.x += nx * correction;
        ball.y += ny * correction;
    }

    private static void collide(Ball a, Ball b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;
        float combinedRadius = a.radius + b.radius;
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared >= combinedRadius * combinedRadius) return;
        float distance = (float) Math.sqrt(distanceSquared);
        float nx = distance > 1e-6f ? dx / distance : (a.name.compareTo(b.name) <= 0 ? 1f : -1f);
        float ny = distance > 1e-6f ? dy / distance : 0f;
        float rAx = nx * a.radius;
        float rAy = ny * a.radius;
        float rBx = -nx * b.radius;
        float rBy = -ny * b.radius;
        float aVx = a.vx - a.angularVelocity * rAy;
        float aVy = a.vy + a.angularVelocity * rAx;
        float bVx = b.vx - b.angularVelocity * rBy;
        float bVy = b.vy + b.angularVelocity * rBx;
        float relativeX = bVx - aVx;
        float relativeY = bVy - aVy;
        float normalSpeed = relativeX * nx + relativeY * ny;
        float inverseMass = a.inverseMass() + b.inverseMass();
        float normalImpulse = 0f;

        if (normalSpeed < 0f) {
            float bounce = Math.abs(normalSpeed) < RESTING_SPEED
                ? 0f : geometricMean(a.elasticity, b.elasticity);
            float normalDenominator = inverseMass
                + square(cross(rAx, rAy, nx, ny)) * a.inverseMoment()
                + square(cross(rBx, rBy, nx, ny)) * b.inverseMoment();
            normalImpulse = -(1f + bounce) * normalSpeed / normalDenominator;
            applyImpulse(a, -nx * normalImpulse, -ny * normalImpulse, rAx, rAy);
            applyImpulse(b, nx * normalImpulse, ny * normalImpulse, rBx, rBy);

            float tx = -ny;
            float ty = nx;
            aVx = a.vx - a.angularVelocity * rAy;
            aVy = a.vy + a.angularVelocity * rAx;
            bVx = b.vx - b.angularVelocity * rBy;
            bVy = b.vy + b.angularVelocity * rBx;
            relativeX = bVx - aVx;
            relativeY = bVy - aVy;
            float tangentSpeed = relativeX * tx + relativeY * ty;
            float tangentDenominator = inverseMass
                + square(cross(rAx, rAy, tx, ty)) * a.inverseMoment()
                + square(cross(rBx, rBy, tx, ty)) * b.inverseMoment();
            float tangentImpulse = -tangentSpeed / tangentDenominator;
            float friction = .35f;
            float limit = friction * normalImpulse;
            tangentImpulse = Math.max(-limit, Math.min(limit, tangentImpulse));
            applyImpulse(a, -tx * tangentImpulse, -ty * tangentImpulse, rAx, rAy);
            applyImpulse(b, tx * tangentImpulse, ty * tangentImpulse, rBx, rBy);
        }

        float correction = Math.max(combinedRadius - distance - POSITION_SLOP, 0f)
            * POSITION_CORRECTION / inverseMass;
        a.x -= nx * correction * a.inverseMass();
        a.y -= ny * correction * a.inverseMass();
        b.x += nx * correction * b.inverseMass();
        b.y += ny * correction * b.inverseMass();
    }

    private static void applyImpulse(Ball ball, float impulseX, float impulseY, float rx, float ry) {
        ball.vx += impulseX * ball.inverseMass();
        ball.vy += impulseY * ball.inverseMass();
        ball.angularVelocity += cross(rx, ry, impulseX, impulseY) * ball.inverseMoment();
    }

    private static float geometricMean(float first, float second) {
        return (float) Math.sqrt(first * second);
    }

    private static float cross(float ax, float ay, float bx, float by) {
        return ax * by - ay * bx;
    }

    private static float square(float value) {
        return value * value;
    }

    private static float finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Physics value must be finite");
        return value;
    }

    private static boolean positive(float value) {
        return Float.isFinite(value) && value > 0f;
    }

    private static boolean unit(float value) {
        return Float.isFinite(value) && value >= 0f && value <= 1f;
    }

    private BallPhysics() {
        throw new AssertionError();
    }
}
