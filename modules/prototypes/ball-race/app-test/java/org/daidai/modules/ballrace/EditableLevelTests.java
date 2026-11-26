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
        check(ids.containsAll(new HashSet<>(java.util.Arrays.asList("slopes", "bumpers", "rails", "zigzag", "center-pegs")))
                && EditableLevel.builtInIds().contains("custom-204016971e9f496bb4067f059d54b069"), "六种内置关卡编号可编辑");

        EditableLevel custom = EditableLevel.createCustom("custom-regression", "自定义测试关");
        custom.add(EditableLevel.CIRCLE, 180f, 240f);
        EditableLevel imported = EditableLevel.importCustom(custom.toJson(), "custom-imported");
        check(imported.id.equals("custom-imported") && imported.name.equals(custom.name)
                && imported.size() == 1 && EditableLevel.parse(imported.toJson(), imported.id).size() == 1,
            "Custom levels import under a fresh identity and preserve editable objects");

        EditableLevel level = EditableLevel.fromTemplate(defaults.get(0));
        EditableLevel.Obstacle slope = level.get(0);
        float originalLength = slope.size;
        slope.set(slope.x, slope.y, originalLength + 24f, slope.thickness, 135f);
        slope.rounded = false;
        check(slope.size == originalLength + 24f && slope.rotationDegrees == 135f,
            "斜坡可以调整长度和旋转角度");
        RaceCourse.Obstacle physicsSlope = level.toTemplate(.5f).obstacles.get(0);
        check(physicsSlope.shape == RaceCourse.Obstacle.SEGMENT
                && Math.abs((float) Math.hypot(physicsSlope.x2 - physicsSlope.x1,
                    physicsSlope.y2 - physicsSlope.y1) - slope.size) < .01f
                && !physicsSlope.rounded,
            "旋转后的斜坡长度和取消圆角设置传递到物理模板");
        level.rename("Slope 2A");
        EditableLevel renamed = EditableLevel.parse(level.toJson(), level.id);
        check("Slope 2A".equals(renamed.name), "关卡名称可保存和恢复且允许中英文数字混排");
        check("Slope 2A".equals(renamed.toTemplate(.62f).name), "自定义关卡名称保留在竞速模板中");
        level.setPorts(RaceCourse.Port.LEFT, 125f, RaceCourse.Port.RIGHT, 310f);
        EditableLevel ports = EditableLevel.parse(level.toJson(), level.id);
        check(ports.entrySide == RaceCourse.Port.LEFT && ports.exitSide == RaceCourse.Port.RIGHT
                && ports.entryY() == 125f && ports.exitY() == 310f,
            "关卡出入口方向和沿边位置在数据往返中保留");
        ports.movePort(true, -20f, 9999f);
        check(ports.entryX() == 0f && ports.entryY() == ports.height,
            "拖动出入口时位置限制在对应边界上");
        EditableLevel defaultsPorts = EditableLevel.fromTemplate(defaults.get(0));
        check(defaultsPorts.entrySide == RaceCourse.Port.TOP && defaultsPorts.exitSide == RaceCourse.Port.BOTTOM
                && defaultsPorts.entryOffset == defaultsPorts.width * .5f
                && defaultsPorts.exitOffset == defaultsPorts.width * .5f,
            "既有内置关卡默认入口在上边、出口在下边且位于中心");
        expectInvalid(() -> level.rename("  "), "空白关卡名称被拒绝");

        EditableLevel.Obstacle placedCircle = level.add(EditableLevel.CIRCLE, 1f, 1f);
        check(placedCircle.x >= placedCircle.size && placedCircle.y >= placedCircle.size,
            "靠近边缘放置圆形障碍时会约束到可用区域");
        placedCircle.setClamped(330f, 460f, 60f, 0f, 0f);
        check(placedCircle.x + placedCircle.size <= RaceCourse.WORLD_WIDTH - 12f
                && placedCircle.y + placedCircle.size <= RaceCourse.LEVEL_HEIGHT - 16f,
            "拖动和调整尺寸后障碍仍完整位于边界内");

        EditableLevel.Obstacle guardedEllipse = level.add(EditableLevel.ELLIPSE, 180f, 240f);
        float originalEllipseXRadius = guardedEllipse.size;
        float originalEllipseYRadius = guardedEllipse.radiusY;
        expectInvalid(() -> guardedEllipse.setClamped(180f, 240f, 100f, 0f, 0f, 70f),
            "非法椭圆尺寸在边界约束阶段被拒绝");
        check(guardedEllipse.size == originalEllipseXRadius && guardedEllipse.radiusY == originalEllipseYRadius,
            "失败的椭圆尺寸编辑不会留下半径的部分更新");

        EditableLevel.Obstacle arc = level.add(EditableLevel.ARC, 180f, 240f);
        expectInvalid(() -> arc.set(45f, 240f, 48f, 12f, 0f),
            "圆弧完整半径必须留在关卡边界内");
        check(arc.hit(arc.x + arc.size, arc.y, 10f), "圆弧在弧线位置可以被选中");
        arc.arcDegrees = 90f;
        arc.rounded = false;
        check(arc.hit(arc.x + arc.size, arc.y, 10f)
                && !arc.hit(arc.x, arc.y + arc.size, 10f), "修改圆弧度数后命中范围随弧度变化");
        EditableLevel arcRoundTrip = EditableLevel.parse(level.toJson(), level.id);
        EditableLevel.Obstacle restoredArc = null;
        for (EditableLevel.Obstacle obstacle : arcRoundTrip.obstacles()) {
            if (EditableLevel.ARC.equals(obstacle.type)) restoredArc = obstacle;
        }
        check(restoredArc != null && restoredArc.arcDegrees == 90f && !restoredArc.rounded,
            "圆弧度数与平头设置保存并载入后保持");
        boolean physicsArcIsFlat = false;
        for (RaceCourse.Obstacle obstacle : arcRoundTrip.toTemplate(.7f).obstacles) {
            if (RaceCourse.Obstacle.ARC == obstacle.shape) physicsArcIsFlat = !obstacle.rounded;
        }
        check(physicsArcIsFlat, "圆弧平头设置传递到比赛物理模板");

        EditableLevel advanced = EditableLevel.createCustom("custom-advanced-obstacles", "扩展障碍测试");
        EditableLevel.Obstacle ellipse = advanced.add(EditableLevel.ELLIPSE, 150f, 180f);
        ellipse.size = 42f;
        ellipse.radiusY = 18f;
        ellipse.elasticity = .8f;
        ellipse.friction = .6f;
        ellipse.color = 0xFF2577AA;
        ellipse.visible = false;
        ellipse.collisionEnabled = false;
        ellipse.motionX = 12f;
        ellipse.motionY = 8f;
        ellipse.motionPeriodSeconds = 2f;
        ellipse.motionPhaseRadians = 0f;
        ellipse.pivotOffsetX = 16f;
        ellipse.pivotOffsetY = 0f;
        ellipse.rotationSpeedDegreesPerSecond = 90f;
        ellipse.collisionImpulseX = 3f;
        ellipse.collisionImpulseY = -2f;
        ellipse.collisionAction = RaceCourse.Obstacle.ACTION_TELEPORT;
        ellipse.teleportX = 220f;
        ellipse.teleportY = 260f;
        ellipse.actionCooldownSeconds = .8f;
        EditableLevel extendedRoundTrip = EditableLevel.parse(advanced.toJson(), advanced.id);
        EditableLevel.Obstacle restoredEllipse = extendedRoundTrip.get(0);
        check(EditableLevel.ELLIPSE.equals(restoredEllipse.type)
                && restoredEllipse.size == 42f && restoredEllipse.radiusY == 18f,
            "椭圆的横纵半径在保存后恢复");
        check(restoredEllipse.elasticity == .8f && restoredEllipse.friction == .6f
                && restoredEllipse.color == 0xFF2577AA && !restoredEllipse.visible
                && !restoredEllipse.collisionEnabled,
            "障碍外观、碰撞开关、弹性和摩擦属性在保存后恢复");
        check(restoredEllipse.motionX == 12f && restoredEllipse.motionY == 8f
                && restoredEllipse.motionPeriodSeconds == 2f && restoredEllipse.motionPhaseRadians == 0f
                && restoredEllipse.pivotOffsetX == 16f && restoredEllipse.pivotOffsetY == 0f
                && restoredEllipse.rotationSpeedDegreesPerSecond == 90f,
            "障碍移动周期和旋转轴属性在保存后恢复");
        check(restoredEllipse.collisionImpulseX == 3f && restoredEllipse.collisionImpulseY == -2f,
            "碰撞附加冲量属性在保存后恢复");
        check(restoredEllipse.collisionAction == RaceCourse.Obstacle.ACTION_TELEPORT
                && restoredEllipse.teleportX == 220f && restoredEllipse.teleportY == 260f
                && restoredEllipse.actionCooldownSeconds == .8f,
            "碰撞动作、传送目标和触发冷却时间在保存后恢复");
        RaceCourse.Obstacle physicsEllipse = extendedRoundTrip.toTemplate(1f).obstacles.get(0);
        check(physicsEllipse.shape == RaceCourse.Obstacle.ELLIPSE
                && physicsEllipse.radius == 42f && physicsEllipse.radiusY == 18f
                && physicsEllipse.friction == .6f && !physicsEllipse.visible
                && !physicsEllipse.collisionEnabled
                && physicsEllipse.collisionAction == RaceCourse.Obstacle.ACTION_TELEPORT
                && physicsEllipse.teleportX == 220f && physicsEllipse.teleportY == 260f,
            "椭圆几何、显隐、接触属性和碰撞动作传入竞速模板");
        RaceCourse.Obstacle.State startState = physicsEllipse.stateAt(0f);
        RaceCourse.Obstacle.State movedState = physicsEllipse.stateAt(.5f);
        RaceCourse.Obstacle.State repeatedState = physicsEllipse.stateAt(4f);
        check(Math.abs(movedState.x - startState.x) > 1f && Math.abs(movedState.rotationDegrees) > 1f,
            "障碍可按正弦运动并绕指定轴旋转");
        check(Math.abs(repeatedState.offsetX - startState.offsetX) < .01f
                && Math.abs(repeatedState.offsetY - startState.offsetY) < .01f,
            "障碍平移按设定周期重复");

        String valid = level.toJson();
        check(EditableLevel.parse(valid, level.id).size() == level.size(), "已编辑关卡可保存并重新载入");
        String legacyJson = "{\"format\":\"ball-race-editable-level\",\"schemaVersion\":1,"
            + "\"id\":\"custom-legacy\",\"name\":\"旧关卡\",\"obstacles\":["
            + "{\"id\":\"obstacle-0\",\"type\":\"circle\",\"x\":150,\"y\":180,"
            + "\"size\":20,\"thickness\":0,\"rotationDeg\":0}]}";
        EditableLevel legacyLevel = EditableLevel.parse(legacyJson, "custom-legacy");
        check(legacyLevel.size() == 1 && legacyLevel.get(0).friction >= 0f
                && legacyLevel.get(0).collisionEnabled,
            "旧版关卡数据仍可载入并为新增障碍属性补默认值");
        String legacyV2Json = "{\"format\":\"ball-race-editable-level\",\"schemaVersion\":2,"
            + "\"id\":\"custom-legacy-v2\",\"name\":\"旧版尺寸关卡\",\"width\":400,\"height\":520,"
            + "\"entrySide\":0,\"entryOffset\":200,\"exitSide\":2,\"exitOffset\":200,"
            + "\"obstacles\":[{\"id\":\"obstacle-0\",\"type\":\"circle\",\"x\":160,\"y\":200,"
            + "\"size\":20,\"thickness\":0,\"rotationDeg\":0}]}";
        EditableLevel legacyV2 = EditableLevel.parse(legacyV2Json, "custom-legacy-v2");
        check(legacyV2.width == 400f && legacyV2.height == 520f
                && legacyV2.entrySide == RaceCourse.Port.TOP && legacyV2.get(0).collisionEnabled,
            "schema 2 关卡尺寸与出入口保留，新增属性使用兼容默认值");
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
