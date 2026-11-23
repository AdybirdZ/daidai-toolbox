package org.daidai.prototypes.ballrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 根据关卡出口方向比较进度，并处理同关卡内的并列位置。 */
public final class RaceProgress {
    public static List<BallPhysics.Ball> order(List<BallPhysics.Ball> balls, RaceCourse course) {
        List<BallPhysics.Ball> result = new ArrayList<>(balls);
        result.sort((first, second) -> compare(first, second, course));
        return Collections.unmodifiableList(result);
    }

    public static int compare(BallPhysics.Ball first, BallPhysics.Ball second, RaceCourse course) {
        if (first.finished() != second.finished()) return first.finished() ? -1 : 1;
        if (first.finished()) {
            int finishOrder = Float.compare(first.finishTime, second.finishTime);
            return finishOrder != 0 ? finishOrder : first.name.compareTo(second.name);
        }

        RaceCourse.Level firstLevel = course.levelAt(first.y);
        RaceCourse.Level secondLevel = course.levelAt(second.y);
        if (firstLevel.index != secondLevel.index) return Integer.compare(secondLevel.index, firstLevel.index);

        float firstLocalY = firstLevel.localY(first.y);
        float secondLocalY = secondLevel.localY(second.y);
        RaceCourse.Port exit = firstLevel.template.exit;
        int progress;
        switch (exit.side) {
            case RaceCourse.Port.RIGHT:
                progress = Float.compare(Math.abs(firstLocalY - exit.offset), Math.abs(secondLocalY - exit.offset));
                if (progress != 0) return progress;
                progress = Float.compare(second.x, first.x);
                if (progress != 0) return progress;
                break;
            case RaceCourse.Port.LEFT:
                progress = Float.compare(Math.abs(firstLocalY - exit.offset), Math.abs(secondLocalY - exit.offset));
                if (progress != 0) return progress;
                progress = Float.compare(first.x, second.x);
                if (progress != 0) return progress;
                break;
            case RaceCourse.Port.TOP:
                progress = Float.compare(firstLocalY, secondLocalY);
                if (progress != 0) return progress;
                progress = Float.compare(Math.abs(first.x - exit.offset), Math.abs(second.x - exit.offset));
                if (progress != 0) return progress;
                break;
            case RaceCourse.Port.BOTTOM:
            default:
                progress = Float.compare(secondLocalY, firstLocalY);
                if (progress != 0) return progress;
                progress = Float.compare(Math.abs(first.x - exit.offset), Math.abs(second.x - exit.offset));
                if (progress != 0) return progress;
                break;
        }
        return first.name.compareTo(second.name);
    }

    public static BallPhysics.Ball leader(List<BallPhysics.Ball> balls, RaceCourse course) {
        List<BallPhysics.Ball> ordered = order(balls, course);
        return ordered.isEmpty() ? null : ordered.get(0);
    }

    public static BallPhysics.Ball trailer(List<BallPhysics.Ball> balls, RaceCourse course) {
        List<BallPhysics.Ball> ordered = order(balls, course);
        return ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);
    }

    public static BallPhysics.Ball byName(List<BallPhysics.Ball> balls, String name) {
        if (name == null) return null;
        String selected = name.trim();
        for (BallPhysics.Ball ball : balls) if (ball.name.equals(selected)) return ball;
        return null;
    }

    private RaceProgress() {
        throw new AssertionError();
    }
}
