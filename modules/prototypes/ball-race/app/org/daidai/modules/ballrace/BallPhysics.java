package org.daidai.modules.ballrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 使用固定步长计算二维刚性圆盘运动，不考虑空气阻力。坐标采用近似 dp 的关卡单位。 */
public final class BallPhysics {
    public static final float FIXED_STEP = 1f / 120f;
    private static final int MAX_SUBSTEPS_PER_FRAME = 30;
    private static final int SOLVER_PASSES = 4;
    private static final float POSITION_SLOP = .015f;
    private static final float POSITION_CORRECTION = .82f;
    private static final float RESTING_SPEED = 1.5f;
    private static final float MAX_TRAVEL_PER_SUBSTEP_RADIUS = .4f;
    private static final int MAX_COLLISION_SUBSTEPS = 8192;

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
        public int teleportCount;
        private final Map<String, Float> actionTimes = new HashMap<>();

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

        private boolean actionReady(String obstacleId, float now, float cooldown) {
            Float previous = actionTimes.get(obstacleId);
            return previous == null || now - previous >= cooldown;
        }

        private void markAction(String obstacleId, float now) {
            actionTimes.put(obstacleId, now);
        }

        private void clearActions() {
            actionTimes.clear();
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
        public final boolean rounded;

        public Segment(float x1, float y1, float x2, float y2, float thickness,
                       float elasticity, float friction) {
            this(x1, y1, x2, y2, thickness, elasticity, friction, true);
        }

        public Segment(float x1, float y1, float x2, float y2, float thickness,
                       float elasticity, float friction, boolean rounded) {
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
            this.rounded = rounded;
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
        private final Segment finishFloor;
        public final List<Ball> balls = new ArrayList<>();
        public final List<Segment> segments = new ArrayList<>();
        public final List<CircleObstacle> circles = new ArrayList<>();
        private final List<RaceCourse.Obstacle> courseObstacles = new ArrayList<>();
        public float elapsedSeconds;
        public float timeLimitSeconds = 180f;
        public boolean running;
        public boolean timedOut;
        private float accumulator;
        private float fixedStepSeconds = FIXED_STEP;
        private int configuredPhysicsHz = 120;
        private float maxBallRadius = 1f;
        private float broadPhaseCellSize = 2f;
        private int[] gridX = new int[0];
        private int[] gridY = new int[0];
        private int[] gridNext = new int[0];
        private int[] gridHead = new int[0];
        private int[] gridCellX = new int[0];
        private int[] gridCellY = new int[0];
        private boolean[] gridOccupied = new boolean[0];

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
            this.finishFloor = new Segment(0f, finishY, width, finishY, 2f, .55f, 0f);
        }

        public void start() {
            if (balls.size() < 2) throw new IllegalStateException("A race needs at least two balls");
            configureBroadPhase();
            running = true;
        }

        public void setPhysicsHz(int hz) {
            if (hz != 30 && hz != 45 && hz != 60 && hz != 90 && hz != 120) {
                throw new IllegalArgumentException("Unsupported physics frequency");
            }
            if (running) throw new IllegalStateException("Cannot change physics frequency during a race");
            configuredPhysicsHz = hz;
            fixedStepSeconds = 1f / hz;
        }

        public int physicsHz() { return configuredPhysicsHz; }

        private void configureBroadPhase() {
            maxBallRadius = 1f;
            for (Ball ball : balls) maxBallRadius = Math.max(maxBallRadius, ball.radius);
            broadPhaseCellSize = maxBallRadius * 2f;
            int capacity = 1;
            while (capacity < balls.size() * 4) capacity <<= 1;
            if (gridHead.length != capacity) {
                gridHead = new int[capacity];
                gridCellX = new int[capacity];
                gridCellY = new int[capacity];
                gridOccupied = new boolean[capacity];
            } else {
                java.util.Arrays.fill(gridOccupied, false);
            }
            if (gridX.length < balls.size()) {
                gridX = new int[balls.size()];
                gridY = new int[balls.size()];
                gridNext = new int[balls.size()];
            }
        }

        public void pause() {
            running = false;
        }

        public void resetClock() {
            elapsedSeconds = 0f;
            accumulator = 0f;
            timedOut = false;
            for (Ball ball : balls) {
                ball.finishTime = -1f;
                ball.clearActions();
            }
        }

        /** 注册关卡中的可动障碍；边界仍使用 segments/circles 列表。 */
        public void setCourseObstacles(List<RaceCourse.Obstacle> obstacles) {
            courseObstacles.clear();
            if (obstacles != null) courseObstacles.addAll(obstacles);
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
            while (accumulator >= fixedStepSeconds && steps < MAX_SUBSTEPS_PER_FRAME && running) {
                fixedStep(fixedStepSeconds);
                accumulator -= fixedStepSeconds;
                steps++;
            }
            if (steps == MAX_SUBSTEPS_PER_FRAME && accumulator > fixedStepSeconds * MAX_SUBSTEPS_PER_FRAME) {
                accumulator = fixedStepSeconds * MAX_SUBSTEPS_PER_FRAME;
            }
        }

        private void fixedStep(float dt) {
            float smallestRadius = Float.MAX_VALUE;
            float fastest = 0f;
            float fastestObstacle = 0f;
            for (Ball ball : balls) {
                smallestRadius = Math.min(smallestRadius, ball.radius);
                fastest = Math.max(fastest, (float) Math.hypot(ball.vx, ball.vy));
            }
            for (RaceCourse.Obstacle obstacle : courseObstacles) {
                if (!obstacle.collisionEnabled) continue;
                RaceCourse.Obstacle.State state = obstacle.stateAt(elapsedSeconds);
                float extent;
                if (obstacle.shape == RaceCourse.Obstacle.SEGMENT) {
                    extent = (float) Math.hypot(state.x2 - state.x1, state.y2 - state.y1) * .5f;
                } else if (obstacle.shape == RaceCourse.Obstacle.ELLIPSE) {
                    extent = Math.max(obstacle.radius, obstacle.radiusY);
                } else {
                    extent = obstacle.radius + obstacle.thickness * .5f;
                }
                float angular = Math.abs((float) Math.toRadians(state.angularVelocityDegreesPerSecond)) * extent;
                fastestObstacle = Math.max(fastestObstacle,
                    (float) Math.hypot(state.velocityX, state.velocityY) + angular);
            }
            float maxTravel = Math.max(.25f, smallestRadius * MAX_TRAVEL_PER_SUBSTEP_RADIUS);
            float estimatedTravel = Math.max(fastest, fastestObstacle) * dt + .5f * gravity * dt * dt;
            int substeps = Math.max(1, Math.min(MAX_COLLISION_SUBSTEPS,
                (int) Math.ceil(estimatedTravel / maxTravel)));
            float substep = dt / substeps;
            for (int index = 0; index < substeps && running; index++) simulateSubstep(substep);
        }

        private void simulateSubstep(float dt) {
            elapsedSeconds += dt;
            for (Ball ball : balls) {
                float previousBottom = ball.y + ball.radius;
                ball.vy += gravity * dt;
                ball.x += ball.vx * dt;
                ball.y += ball.vy * dt;
                float openingLeft = finishLeft + ball.radius;
                float openingRight = finishRight - ball.radius;
                if (!ball.finished() && previousBottom < finishY
                        && ball.y + ball.radius >= finishY
                        && ball.x >= openingLeft && ball.x <= openingRight) {
                    finish(ball);
                }
            }

            for (int pass = 0; pass < SOLVER_PASSES; pass++) {
                for (Ball ball : balls) {
                    // 障碍物与边界始终碰撞，完成后只关闭球与球之间的碰撞。
                    for (Segment segment : segments) collide(ball, segment);
                    if (ball.finished()) collide(ball, finishFloor, false);
                    for (CircleObstacle circle : circles) collide(ball, circle);
                    for (RaceCourse.Obstacle obstacle : courseObstacles) collideCourseObstacle(ball, obstacle);
                }
                collideNearbyBalls();
            }

            for (Ball ball : balls) {
                if (ball.finished()) continue;
                float openingLeft = finishLeft + ball.radius;
                float openingRight = finishRight - ball.radius;
                if (!ball.finished() && ball.y + ball.radius >= finishY
                        && ball.x >= openingLeft && ball.x <= openingRight) {
                    finish(ball);
                } else if (!ball.finished() && ball.y + ball.radius >= finishY) {
                    collideWithFinishFloor(ball);
                }
                if (ball.x < -100f || ball.x > width + 100f || ball.y > finishY + 300f) {
                    finish(ball);
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

        private void collideCourseObstacle(Ball ball, RaceCourse.Obstacle obstacle) {
            if (!obstacle.collisionEnabled) return;
            RaceCourse.Obstacle.State state = obstacle.stateAt(elapsedSeconds);
            float angularVelocity = (float) Math.toRadians(state.angularVelocityDegreesPerSecond);
            boolean contact = false;
            if (obstacle.shape == RaceCourse.Obstacle.SEGMENT) {
                contact = collide(ball, new Segment(state.x1, state.y1, state.x2, state.y2,
                    obstacle.thickness, obstacle.elasticity, obstacle.friction, obstacle.rounded),
                    true, state.pivotX, state.pivotY, state.pivotVelocityX, state.pivotVelocityY, angularVelocity);
            } else if (obstacle.shape == RaceCourse.Obstacle.ARC) {
                int pieces = Math.max(4, Math.round(Math.abs(obstacle.arcDegrees) / 10f));
                double start = Math.toRadians(state.rotationDegrees - obstacle.arcDegrees * .5f);
                double step = Math.toRadians(obstacle.arcDegrees / pieces);
                for (int piece = 0; piece < pieces && !contact; piece++) {
                    double a = start + step * piece;
                    double b = start + step * (piece + 1);
                    float x1 = state.x + (float) Math.cos(a) * obstacle.radius;
                    float y1 = state.y + (float) Math.sin(a) * obstacle.radius;
                    float x2 = state.x + (float) Math.cos(b) * obstacle.radius;
                    float y2 = state.y + (float) Math.sin(b) * obstacle.radius;
                    contact = collide(ball, new Segment(x1, y1, x2, y2, obstacle.thickness,
                        obstacle.elasticity, obstacle.friction, obstacle.rounded), true,
                        state.pivotX, state.pivotY, state.pivotVelocityX, state.pivotVelocityY, angularVelocity);
                }
            } else if (obstacle.shape == RaceCourse.Obstacle.ELLIPSE) {
                contact = collideEllipse(ball, state.x, state.y, obstacle.radius, obstacle.radiusY,
                    state.rotationDegrees, obstacle.elasticity, obstacle.friction,
                    state.pivotX, state.pivotY, state.pivotVelocityX, state.pivotVelocityY, angularVelocity);
            } else {
                contact = collide(ball, new CircleObstacle(state.x, state.y, obstacle.radius,
                    obstacle.elasticity, obstacle.friction), state.pivotVelocityX, state.pivotVelocityY,
                    state.pivotX, state.pivotY, angularVelocity);
            }
            if (contact && ball.actionReady(obstacle.label, elapsedSeconds, obstacle.actionCooldownSeconds)) {
                ball.markAction(obstacle.label, elapsedSeconds);
                if (obstacle.collisionAction == RaceCourse.Obstacle.ACTION_IMPULSE) {
                    ball.vx += obstacle.collisionImpulseX * ball.inverseMass();
                    ball.vy += obstacle.collisionImpulseY * ball.inverseMass();
                } else if (obstacle.collisionAction == RaceCourse.Obstacle.ACTION_TELEPORT) {
                    ball.x = obstacle.teleportX;
                    ball.y = obstacle.teleportY;
                    ball.teleportCount++;
                }
            }
        }

        private void collideNearbyBalls() {
            if (gridHead.length == 0) configureBroadPhase();
            java.util.Arrays.fill(gridOccupied, false);
            for (int index = 0; index < balls.size(); index++) {
                Ball ball = balls.get(index);
                int cellX = (int) Math.floor(ball.x / broadPhaseCellSize);
                int cellY = (int) Math.floor(ball.y / broadPhaseCellSize);
                gridX[index] = cellX;
                gridY[index] = cellY;
                int slot = findGridSlot(cellX, cellY, true);
                gridNext[index] = gridHead[slot];
                gridHead[slot] = index;
            }
            for (int first = 0; first < balls.size(); first++) {
                Ball a = balls.get(first);
                if (a.finished() || !a.ballCollisionEnabled) continue;
                int centerX = gridX[first];
                int centerY = gridY[first];
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        int slot = findGridSlot(centerX + offsetX, centerY + offsetY, false);
                        if (slot < 0) continue;
                        for (int second = gridHead[slot]; second >= 0; second = gridNext[second]) {
                            if (second <= first) continue;
                            Ball b = balls.get(second);
                            if (!b.finished() && b.ballCollisionEnabled) collide(a, b);
                        }
                    }
                }
            }
        }

        private int findGridSlot(int x, int y, boolean create) {
            int mask = gridHead.length - 1;
            int slot = mixCell(x, y) & mask;
            while (gridOccupied[slot]) {
                if (gridCellX[slot] == x && gridCellY[slot] == y) return slot;
                slot = (slot + 1) & mask;
            }
            if (!create) return -1;
            gridOccupied[slot] = true;
            gridCellX[slot] = x;
            gridCellY[slot] = y;
            gridHead[slot] = -1;
            return slot;
        }

        private static int mixCell(int x, int y) {
            int hash = x * 0x8da6b343 ^ y * 0xd8163841;
            hash ^= hash >>> 16;
            return hash;
        }

        private void finish(Ball ball) {
            ball.finishTime = elapsedSeconds;
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

    private static boolean collide(Ball ball, CircleObstacle obstacle) {
        return collide(ball, obstacle, 0f, 0f);
    }

    private static boolean collide(Ball ball, CircleObstacle obstacle, float surfaceVx, float surfaceVy) {
        return collide(ball, obstacle, surfaceVx, surfaceVy, obstacle.x, obstacle.y, 0f);
    }

    private static boolean collide(Ball ball, CircleObstacle obstacle, float surfaceVx, float surfaceVy,
                                   float surfaceCenterX, float surfaceCenterY, float angularVelocity) {
        float dx = ball.x - obstacle.x;
        float dy = ball.y - obstacle.y;
        float distanceSquared = dx * dx + dy * dy;
        float combinedRadius = ball.radius + obstacle.radius;
        if (distanceSquared >= combinedRadius * combinedRadius) return false;
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
        float contactX = obstacle.x + nx * obstacle.radius;
        float contactY = obstacle.y + ny * obstacle.radius;
        float radiusX = contactX - surfaceCenterX;
        float radiusY = contactY - surfaceCenterY;
        float contactVx = surfaceVx - angularVelocity * radiusY;
        float contactVy = surfaceVy + angularVelocity * radiusX;
        resolveStaticContact(ball, nx, ny, combinedRadius - distance,
            geometricMean(ball.elasticity, obstacle.elasticity), obstacle.friction, true,
            contactVx, contactVy);
        return true;
    }

    private static boolean collide(Ball ball, Segment segment) {
        return collide(ball, segment, true);
    }

    private static boolean collide(Ball ball, Segment segment, boolean useRestingThreshold) {
        return collide(ball, segment, useRestingThreshold, 0f, 0f);
    }

    private static boolean collide(Ball ball, Segment segment, boolean useRestingThreshold,
                                   float surfaceVx, float surfaceVy) {
        return collide(ball, segment, useRestingThreshold, 0f, 0f, surfaceVx, surfaceVy, 0f);
    }

    private static boolean collide(Ball ball, Segment segment, boolean useRestingThreshold,
                                   float surfaceCenterX, float surfaceCenterY,
                                   float surfaceVx, float surfaceVy, float angularVelocity) {
        float dx = segment.x2 - segment.x1;
        float dy = segment.y2 - segment.y1;
        float lengthSquared = dx * dx + dy * dy;
        float along = ((ball.x - segment.x1) * dx + (ball.y - segment.y1) * dy) / lengthSquared;
        if (!segment.rounded && (along < 0f || along > 1f)) return false;
        along = Math.max(0f, Math.min(1f, along));
        float closestX = segment.x1 + dx * along;
        float closestY = segment.y1 + dy * along;
        float offsetX = ball.x - closestX;
        float offsetY = ball.y - closestY;
        float distanceSquared = offsetX * offsetX + offsetY * offsetY;
        float combinedRadius = ball.radius + segment.radius;
        if (distanceSquared >= combinedRadius * combinedRadius) return false;
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
            if ((ball.vx - surfaceVx) * nx + (ball.vy - surfaceVy) * ny > 0f) {
                nx = -nx;
                ny = -ny;
            }
        }
        float contactX = closestX + nx * segment.radius;
        float contactY = closestY + ny * segment.radius;
        float radiusX = contactX - surfaceCenterX;
        float radiusY = contactY - surfaceCenterY;
        float contactVx = surfaceVx - angularVelocity * radiusY;
        float contactVy = surfaceVy + angularVelocity * radiusX;
        resolveStaticContact(ball, nx, ny, combinedRadius - distance,
            geometricMean(ball.elasticity, segment.elasticity), segment.friction, useRestingThreshold,
            contactVx, contactVy);
        return true;
    }

    private static boolean collideEllipse(Ball ball, float centerX, float centerY,
                                          float radiusX, float radiusY, float rotationDegrees,
                                          float elasticity, float friction,
                                          float surfaceCenterX, float surfaceCenterY,
                                          float surfaceVx, float surfaceVy, float angularVelocity) {
        double angle = Math.toRadians(rotationDegrees);
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float worldX = ball.x - centerX;
        float worldY = ball.y - centerY;
        float px = worldX * cosine + worldY * sine;
        float py = -worldX * sine + worldY * cosine;
        float a = Math.max(1f, radiusX);
        float b = Math.max(1f, radiusY);
        float theta = (float) Math.atan2(py * a, px * b);
        if (!Float.isFinite(theta)) theta = 0f;
        for (int index = 0; index < 8; index++) {
            float ct = (float) Math.cos(theta);
            float st = (float) Math.sin(theta);
            float qx = a * ct;
            float qy = b * st;
            float dx = qx - px;
            float dy = qy - py;
            float q1x = -a * st;
            float q1y = b * ct;
            float q2x = -a * ct;
            float q2y = -b * st;
            float derivative = dx * q1x + dy * q1y;
            float second = q1x * q1x + q1y * q1y + dx * q2x + dy * q2y;
            if (Math.abs(second) < 1e-5f) break;
            float step = derivative / second;
            theta -= Math.max(-.5f, Math.min(.5f, step));
        }
        float qx = a * (float) Math.cos(theta);
        float qy = b * (float) Math.sin(theta);
        float deltaX = px - qx;
        float deltaY = py - qy;
        float distance = (float) Math.hypot(deltaX, deltaY);
        float normalized = px * px / (a * a) + py * py / (b * b);
        if (normalized > 1f && distance >= ball.radius) return false;

        float localNx = qx / (a * a);
        float localNy = qy / (b * b);
        float normalLength = (float) Math.hypot(localNx, localNy);
        if (normalLength < 1e-6f) { localNx = 0f; localNy = -1f; normalLength = 1f; }
        localNx /= normalLength;
        localNy /= normalLength;
        float nx = localNx * cosine - localNy * sine;
        float ny = localNx * sine + localNy * cosine;
        float penetration = normalized <= 1f ? ball.radius + distance : ball.radius - distance;
        float contactX = centerX + qx * cosine - qy * sine;
        float contactY = centerY + qx * sine + qy * cosine;
        float relativeX = contactX - surfaceCenterX;
        float relativeY = contactY - surfaceCenterY;
        float contactVx = surfaceVx - angularVelocity * relativeY;
        float contactVy = surfaceVy + angularVelocity * relativeX;
        resolveStaticContact(ball, nx, ny, penetration,
            geometricMean(ball.elasticity, elasticity), friction, true, contactVx, contactVy);
        return true;
    }

    private static void resolveStaticContact(Ball ball, float nx, float ny,
                                             float penetration, float elasticity, float friction) {
        resolveStaticContact(ball, nx, ny, penetration, elasticity, friction, true);
    }

    private static void resolveStaticContact(Ball ball, float nx, float ny,
                                             float penetration, float elasticity, float friction,
                                             boolean useRestingThreshold) {
        resolveStaticContact(ball, nx, ny, penetration, elasticity, friction, useRestingThreshold, 0f, 0f);
    }

    private static void resolveStaticContact(Ball ball, float nx, float ny,
                                             float penetration, float elasticity, float friction,
                                             boolean useRestingThreshold,
                                             float surfaceVx, float surfaceVy) {
        float rx = -nx * ball.radius;
        float ry = -ny * ball.radius;
        float contactVx = ball.vx - surfaceVx - ball.angularVelocity * ry;
        float contactVy = ball.vy - surfaceVy + ball.angularVelocity * rx;
        float normalSpeed = contactVx * nx + contactVy * ny;
        float normalImpulse = 0f;
        if (normalSpeed < 0f) {
            float bounce = useRestingThreshold && Math.abs(normalSpeed) < RESTING_SPEED ? 0f : elasticity;
            float normalDenominator = ball.inverseMass()
                + square(cross(rx, ry, nx, ny)) * ball.inverseMoment();
            normalImpulse = -(1f + bounce) * normalSpeed / normalDenominator;
            applyImpulse(ball, nx * normalImpulse, ny * normalImpulse, rx, ry);

            float tx = -ny;
            float ty = nx;
            contactVx = ball.vx - surfaceVx - ball.angularVelocity * ry;
            contactVy = ball.vy - surfaceVy + ball.angularVelocity * rx;
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
