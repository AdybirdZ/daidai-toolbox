package org.daidai.modules.ballrace;

import org.json.JSONException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 检查关卡编辑模型、保存数据与竞速模板之间的转换。 */
public final class EditableLevelTests {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectInvalid(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {
        List<RaceCourse.Template> defaults = RaceCourse.templates(.62f);
        Set<String> ids = new HashSet<>();
        for (RaceCourse.Template template : defaults) {
            EditableLevel level = EditableLevel.fromTemplate(template);
            check(ids.add(level.id), "内置关卡编号唯一");
            EditableLevel restored = EditableLevel.parse(level.toJson(), level.id);
            check(restored.size() == template.obstacles.size(), "内置关卡数据往返保留全部障碍");
            check(restored.toTemplate(.62f).obstacles.size() == template.obstacles.size(),
                "编辑数据可以转换回竞速模板");
        }
        check(ids.equals(new HashSet<>(EditableLevel.builtInIds())), "五种内置关卡都可编辑");

        EditableLevel level = EditableLevel.fromTemplate(defaults.get(0));
        EditableLevel.Obstacle slope = level.get(0);
        float originalLength = slope.size;
        slope.set(slope.x, slope.y, originalLength + 24f, slope.thickness, 135f);
        check(slope.size == originalLength + 24f && slope.rotationDegrees == 135f,
            "斜坡可以调整长度和旋转角度");
        RaceCourse.Obstacle physicsSlope = level.toTemplate(.5f).obstacles.get(0);
        check(physicsSlope.shape == RaceCourse.Obstacle.SEGMENT
                && Math.abs((float) Math.hypot(physicsSlope.x2 - physicsSlope.x1,
                    physicsSlope.y2 - physicsSlope.y1) - slope.size) < .01f,
            "旋转后的斜坡以对应线段进入物理模板");
        level.rename("Slope 2A");
        EditableLevel renamed = EditableLevel.parse(level.toJson(), level.id);
        check("Slope 2A".equals(renamed.name), "关卡名称可保存和恢复且允许中英文数字混排");
        check("Slope 2A".equals(renamed.toTemplate(.62f).name), "自定义关卡名称保留在竞速模板中");
        expectInvalid(() -> level.rename("  "), "空白关卡名称被拒绝");

        EditableLevel.Obstacle placedCircle = level.add(EditableLevel.CIRCLE, 1f, 1f);
        check(placedCircle.x >= placedCircle.size && placedCircle.y >= placedCircle.size,
            "靠近边缘放置圆形障碍时会约束到可用区域");
        placedCircle.setClamped(330f, 460f, 60f, 0f, 0f);
        check(placedCircle.x + placedCircle.size <= RaceCourse.WORLD_WIDTH - 12f
                && placedCircle.y + placedCircle.size <= RaceCourse.LEVEL_HEIGHT - 16f,
            "拖动和调整尺寸后障碍仍完整位于边界内");

        String valid = level.toJson();
        check(EditableLevel.parse(valid, level.id).size() == level.size(), "已编辑关卡可保存并重新载入");
        expectInvalid(() -> {
            try { EditableLevel.parse(valid, "bumpers"); }
            catch (JSONException error) { throw new IllegalArgumentException(error); }
        }, "不匹配的关卡编号被拒绝");
        String duplicateId = valid.replaceFirst("placed-3", "obstacle-0");
        expectInvalid(() -> {
            try { EditableLevel.parse(duplicateId, level.id); }
            catch (JSONException error) { throw new IllegalArgumentException(error); }
        }, "重复的障碍编号被拒绝");
        expectInvalid(() -> level.add("unknown", 180f, 240f), "未知障碍类型被拒绝");

        System.out.println("Editable level checks passed: " + checks);
    }
}
