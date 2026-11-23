package org.simpleweather.qa;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** 使用本机签名的竞速包检查测试宿主的双语界面和生命周期。 */
public final class BallRaceTestHostUiTests extends Instrumentation {
    private Context context;
    private Activity host;
    private Activity game;
    private ClassLoader hostLoader;
    private Object apps;
    private Bundle args;
    private boolean wasInstalled;
    private byte[] previousPackage;
    private Map<String, ?> previousPreferences;
    private Map<String, ?> previousToolbox;
    private Map<String, ?> previousAppHost;
    private byte[] previousRollbackPackage;
    private int checks;
    private int savedSlopeObstacleCount;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        args = bundle;
        start();
    }

    private Class<?> hostClass(String name) throws Exception {
        return Class.forName("org.simpleweather.app." + name, true, hostLoader);
    }

    private Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private Object call(Object owner, String name, Class<?>[] parameterTypes, Object... values) throws Exception {
        Method method = (owner instanceof Class ? (Class<?>) owner : owner.getClass())
            .getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(owner instanceof Class ? null : owner, values);
    }

    private void ui(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new RuntimeException(failure.get());
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    private void await(java.util.function.BooleanSupplier condition, String label) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000L;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25L);
        check(condition.getAsBoolean(), label);
    }

    private TextView findText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findText(group.getChildAt(index), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void collectTexts(View view, java.util.List<String> values) {
        if (view instanceof TextView) values.add(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectTexts(group.getChildAt(index), values);
            }
        }
    }

    private boolean accessibilityContains(AccessibilityNodeInfo node, String value) {
        if (node == null) return false;
        CharSequence text = node.getText();
        if (text != null && text.toString().contains(value)) return true;
        for (int index = 0; index < node.getChildCount(); index++) {
            if (accessibilityContains(node.getChild(index), value)) return true;
        }
        return false;
    }

    private boolean activeWindowContains(String value) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        return accessibilityContains(root, value);
    }

    private TextView findTextContaining(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(value)) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findTextContaining(group.getChildAt(index), value);
                if (found != null) return found;
            }
        }
        return null;
    }

    private float elapsed(Object world) throws Exception {
        AtomicReference<Float> value = new AtomicReference<>();
        ui(() -> {
            try { value.set((Float) field(world, "elapsedSeconds")); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        return value.get();
    }

    private String editorLevelId(Object page) throws Exception {
        return (String) field(field(page, "editorLevel"), "id");
    }

    private int editorObstacleCount(Object page) throws Exception {
        Object level = field(page, "editorLevel");
        return ((java.util.List<?>) call(level, "obstacles", new Class<?>[]{})).size();
    }

    private void testLevelEditor() throws Exception {
        Object page = field(game, "page");
        boolean english = args.getString("language").equals("en");
        TextView editorEntry = findText(game.getWindow().getDecorView(), english ? "Editor" : "编辑器");
        check(editorEntry != null, "main menu exposes the editor");
        ui(() -> editorEntry.performClick());
        TextView editBalls = findText(game.getWindow().getDecorView(), english ? "Edit balls" : "编辑小球");
        check(editBalls != null && !editBalls.isEnabled(),
            "editor menu shows the not-yet-available ball editor entry");
        TextView editLevel = findText(game.getWindow().getDecorView(), english ? "Edit built-in levels" : "编辑内置关卡");
        check(editLevel != null, "editor menu exposes the built-in level editor");
        ui(() -> editLevel.performClick());
        await(() -> findText(game.getWindow().getDecorView(), english ? "Level canvas" : "关卡画布") != null,
            "level editor opens");

        EditText levelName = (EditText) field(page, "editorLevelNameInput");
        TextView saveName = findText(game.getWindow().getDecorView(), english ? "Save level name" : "保存关卡名称");
        ui(() -> {
            levelName.setText("Slope 2A");
            saveName.performClick();
        });
        check("Slope 2A".equals(field(field(page, "editorLevel"), "name")),
            "built-in level can be renamed without changing its stable id");

        Spinner levels = (Spinner) field(page, "editorLevelSpinner");
        String[] ids = {"slopes", "bumpers", "rails", "zigzag", "center-pegs"};
        for (int index = 0; index < ids.length; index++) {
            final int selected = index;
            ui(() -> levels.setSelection(selected));
            await(() -> {
                try { return ids[selected].equals(editorLevelId(page)); }
                catch (Exception ignored) { return false; }
            }, "built-in level can be opened in the editor: " + ids[index]);
            check(ids[index].equals(editorLevelId(page)), "selected built-in level is editable");
        }
        ui(() -> levels.setSelection(0));
        await(() -> {
            try { return "slopes".equals(editorLevelId(page)); }
            catch (Exception ignored) { return false; }
        }, "editor returns to the selected test level");

        int before = editorObstacleCount(page);
        TextView addCircle = findText(game.getWindow().getDecorView(), english ? "Add ball obstacle" : "添加圆球");
        check(addCircle != null, "editor can add circular obstacles");
        ui(() -> addCircle.performClick());
        check(editorObstacleCount(page) == before + 1, "circular obstacle is placed on the level");
        Object selected = field(page, "selectedObstacle");
        check("circle".equals(field(selected, "type")), "new circle is selected for editing");
        SeekBar size = (SeekBar) field(page, "editorSizeSeek");
        float oldRadius = (Float) field(selected, "size");
        int nextRadius = Math.min(size.getMax(), size.getProgress() + 9);
        ui(() -> {
            size.setProgress(nextRadius);
            try { call(page, "updateSelectedSize", new Class<?>[]{int.class}, nextRadius); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        check((Float) field(selected, "size") > oldRadius, "circular obstacle radius can be adjusted");
        TextView delete = findText(game.getWindow().getDecorView(), english ? "Delete selected" : "删除所选");
        ui(() -> delete.performClick());
        check(editorObstacleCount(page) == before, "selected obstacle can be deleted");
        check(field(page, "selectedObstacle") == null, "deleted obstacle is cleared from the selection");

        TextView addSlope = findText(game.getWindow().getDecorView(), english ? "Add slope" : "添加斜坡");
        ui(() -> addSlope.performClick());
        Object slope = field(page, "selectedObstacle");
        check("slope".equals(field(slope, "type")), "new slope is selected for editing");
        float oldLength = (Float) field(slope, "size");
        ui(() -> {
            try {
                call(page, "updateSelectedSize", new Class<?>[]{int.class}, 145);
                call(page, "updateSelectedRotation", new Class<?>[]{float.class}, 120f);
                call(page, "updateSelectedThickness", new Class<?>[]{float.class}, 22f);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        check((Float) field(slope, "size") > oldLength
                && Math.abs((Float) field(slope, "rotationDegrees") - 120f) < .01f
                && Math.abs((Float) field(slope, "thickness") - 22f) < .01f,
            "slope length, thickness and rotation can be adjusted");

        View canvas = (View) field(page, "editorCanvas");
        float oldX = (Float) field(slope, "x");
        int[] screen = new int[2];
        canvas.getLocationOnScreen(screen);
        float localX = (Float) call(canvas, "screenX", new Class<?>[]{float.class}, oldX);
        float localY = (Float) call(canvas, "screenY", new Class<?>[]{float.class}, (Float) field(slope, "y"));
        long time = SystemClock.uptimeMillis();
        sendPointerSync(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
            screen[0] + localX, screen[1] + localY, 0));
        sendPointerSync(MotionEvent.obtain(time, time + 45, MotionEvent.ACTION_MOVE,
            screen[0] + localX + 24f, screen[1] + localY + 18f, 0));
        sendPointerSync(MotionEvent.obtain(time, time + 90, MotionEvent.ACTION_UP,
            screen[0] + localX + 24f, screen[1] + localY + 18f, 0));
        check((Float) field(slope, "x") > oldX + 10f, "dragging moves the selected obstacle on the canvas");

        String saved = context.getSharedPreferences("ballrace_module", 0)
            .getString("level_slopes_json", null);
        check(saved != null && saved.contains("placed-"), "edited level is saved locally");
        TextView saveChanges = findText(game.getWindow().getDecorView(), english ? "Save and return" : "保存并返回");
        ui(() -> saveChanges.performClick());
        TextView editAgain = findText(game.getWindow().getDecorView(),
            english ? "Edit built-in levels" : "编辑内置关卡");
        ui(() -> editAgain.performClick());
        await(() -> findText(game.getWindow().getDecorView(), english ? "Level canvas" : "关卡画布") != null,
            "saved level can be reopened in the editor");
        check(editorObstacleCount(page) == before + 1, "saved obstacle layout is restored after reopening");
        Object restoredSlope = call(field(page, "editorLevel"), "get", new Class<?>[]{int.class}, before);
        check(Math.abs((Float) field(restoredSlope, "rotationDegrees") - 120f) < .01f,
            "saved slope rotation is restored after reopening");
        savedSlopeObstacleCount = before + 1;
        TextView saveAfterReload = findText(game.getWindow().getDecorView(),
            english ? "Save and return" : "保存并返回");
        ui(() -> saveAfterReload.performClick());
        TextView editorBack = findText(game.getWindow().getDecorView(), english ? "Back" : "返回");
        check(editorBack != null, "saved level returns to the editor menu");
        ui(() -> editorBack.performClick());
        await(() -> findText(game.getWindow().getDecorView(), english ? "Start a game" : "开始一场游戏") != null,
            "editor menu returns to the main menu");
    }

    private void install() throws Exception {
        try (FileInputStream input = new FileInputStream("/data/local/tmp/internal-apps/ballrace.ddapp")) {
            call(apps, "install", new Class<?>[]{java.io.InputStream.class, String.class, String.class},
                input, "ballrace", null);
        }
    }

    private void runRace(long seed) {
        ui(() -> {
            try { call(field(game, "page"), "showRace", new Class<?>[]{long.class}, seed); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }

    private void testRaceView() throws Exception {
        Object page = field(game, "page");
        boolean english = args.getString("language").equals("en");
        check(page.getClass().getClassLoader() != hostLoader, "race page executes from the installed package");
        check(game.getRequestedOrientation() == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            "race activity requests portrait orientation");
        TextView title = findText(game.getWindow().getDecorView(), english
            ? "Daidai Toolbox Test · Ball Race" : "测试呆呆工具箱 · 小球竞赛");
        check(title != null && (title.getGravity() & android.view.Gravity.HORIZONTAL_GRAVITY_MASK)
                == android.view.Gravity.CENTER_HORIZONTAL,
            "main menu centers the race title");
        TextView startGame = findText(game.getWindow().getDecorView(), english ? "Start a game" : "开始一场游戏");
        TextView editor = findText(game.getWindow().getDecorView(), english ? "Editor" : "编辑器");
        TextView defaults = findText(game.getWindow().getDecorView(), english ? "Default settings" : "默认设置");
        check(startGame != null && editor != null && defaults != null, "main menu exposes its three actions");
        check(startGame.getHeight() >= 65 && editor.getHeight() >= 65 && defaults.getHeight() >= 65,
            "main menu actions have large tap targets");

        ui(() -> defaults.performClick());
        await(() -> findText(game.getWindow().getDecorView(),
            english ? "Default camera follow mode" : "进入比赛时的默认跟随模式") != null,
            "default settings page opens");
        Spinner defaultFollow = (Spinner) field(page, "defaultFollowSpinner");
        CheckBox defaultCollision = (CheckBox) field(page, "defaultBallCollision");
        SeekBar defaultObstacle = (SeekBar) field(page, "defaultObstacleElasticitySeek");
        SeekBar defaultBall = (SeekBar) field(page, "defaultBallElasticitySeek");
        SeekBar defaultGravity = (SeekBar) field(page, "defaultGravitySeek");
        check(defaultFollow.getSelectedItemPosition() == 2 && defaultCollision.isChecked()
                && defaultObstacle.getProgress() == 62 && defaultBall.getProgress() == 68
                && defaultGravity.getProgress() == 75,
            "defaults are follow first, ball collisions on, obstacle 0.62, ball 0.68 and gravity 0.75g");
        TextView saveDefaults = findText(game.getWindow().getDecorView(),
            english ? "Save default settings" : "保存默认设置");
        ui(() -> {
            defaultObstacle.setProgress(55);
            defaultBall.setProgress(70);
            defaultGravity.setProgress(100);
            defaultCollision.setChecked(false);
            defaultFollow.setSelection(2);
            saveDefaults.performClick();
        });
        await(() -> findText(game.getWindow().getDecorView(),
            english ? "Start a game" : "开始一场游戏") != null,
            "saved defaults return to the main menu");

        testLevelEditor();
        startGame = findText(game.getWindow().getDecorView(), english ? "Start a game" : "开始一场游戏");
        TextView startGameButton = startGame;
        ui(() -> startGameButton.performClick());
        await(() -> findText(game.getWindow().getDecorView(),
            english ? "Choose racers" : "选择参赛小球") != null,
            "start-game action opens the race setup page");
        check(findText(game.getWindow().getDecorView(), english ? "Choose levels" : "选择关卡") != null
                && findText(game.getWindow().getDecorView(), english ? "Race settings" : "比赛设置") != null
                && findText(game.getWindow().getDecorView(), english ? "Settings" : "设置") == null,
            "race setup starts with participant, level-pool and race-settings buttons only");

        java.util.List<String> participants = (java.util.List<String>) field(page, "participantIds");
        check(participants.size() == 6 && new java.util.HashSet<>(participants).size() == participants.size(),
            "six unique colored balls are selected initially");
        TextView chooseParticipants = findText(game.getWindow().getDecorView(),
            english ? "Choose racers" : "选择参赛小球");
        ui(() -> chooseParticipants.performClick());
        TextView removeBall = findText(game.getWindow().getDecorView(), english ? "Remove" : "移除");
        ui(() -> removeBall.performClick());
        check(participants.size() == 5, "an existing racer can be removed");
        TextView addBall = findText(game.getWindow().getDecorView(), english ? "Add a ball" : "添加小球");
        ui(() -> addBall.performClick());
        await(() -> findText(game.getWindow().getDecorView(),
            english ? "Light pink" : "浅粉色") != null,
            "available color catalog includes pink shades");
        TextView pink = findText(game.getWindow().getDecorView(), english ? "Light pink" : "浅粉色");
        ViewGroup pinkRow = (ViewGroup) pink.getParent();
        TextView addPink = findText(pinkRow, english ? "Add a ball" : "添加小球");
        ui(() -> addPink.performClick());
        check(participants.size() == 6 && participants.contains("pink_light")
                && new java.util.HashSet<>(participants).size() == participants.size(),
            "an unselected color can be added once to the roster");
        TextView backToRoster = findText(game.getWindow().getDecorView(), english ? "Back" : "返回");
        ui(() -> backToRoster.performClick());
        TextView rosterDone = findText(game.getWindow().getDecorView(), english ? "Done" : "完成");
        ui(() -> rosterDone.performClick());

        TextView chooseLevels = findText(game.getWindow().getDecorView(), english ? "Choose levels" : "选择关卡");
        ui(() -> chooseLevels.performClick());
        java.util.List<CheckBox> poolChecks = (java.util.List<CheckBox>) field(page, "levelPoolCheckBoxes");
        check(poolChecks.size() == 5, "all five built-in levels can be selected for random draws");
        for (int index = 0; index < poolChecks.size(); index++) {
            final boolean checked = index < 3;
            final CheckBox selector = poolChecks.get(index);
            ui(() -> selector.setChecked(checked));
        }
        TextView savePool = findText(game.getWindow().getDecorView(),
            english ? "Save level selection" : "保存关卡选择");
        ui(() -> savePool.performClick());
        java.util.List<String> candidateIds = (java.util.List<String>) field(page, "candidateLevelIds");
        check(candidateIds.size() == 3, "only selected level types enter the random pool");

        TextView settings = findText(game.getWindow().getDecorView(), english ? "Race settings" : "比赛设置");
        ui(() -> settings.performClick());
        SeekBar levelCount = (SeekBar) field(page, "matchLevelCountSeek");
        CheckBox collisions = (CheckBox) field(page, "matchBallCollision");
        SeekBar obstacleElasticity = (SeekBar) field(page, "matchObstacleElasticitySeek");
        SeekBar ballElasticity = (SeekBar) field(page, "matchBallElasticitySeek");
        SeekBar gravity = (SeekBar) field(page, "matchGravitySeek");
        ui(() -> levelCount.setProgress(2));
        TextView repeatRule = findTextContaining(game.getWindow().getDecorView(),
            english ? "will not repeat" : "不会重复");
        check(repeatRule != null && (english ? repeatRule.getText().toString().contains("will not repeat")
                : repeatRule.getText().toString().contains("不会重复")),
            "a race no longer than the selected pool uses the no-repeat rule");
        ui(() -> {
            levelCount.setProgress(6);
            collisions.setChecked(false);
            obstacleElasticity.setProgress(55);
            ballElasticity.setProgress(70);
            gravity.setProgress(100);
        });
        check(findTextContaining(game.getWindow().getDecorView(),
                english ? "Repeats are allowed" : "允许重复抽取") != null,
            "a race longer than the selected pool allows repeats");
        TextView saveMatch = findText(game.getWindow().getDecorView(),
            english ? "Save race settings" : "保存比赛设置");
        ui(() -> saveMatch.performClick());

        long seed = 0L;
        while (new java.util.Random(seed).nextInt(3) != 0) seed++;
        runRace(seed);
        View canvas = (View) field(page, "raceCanvas");
        check(canvas != null, "race view is created");
        Object course = field(canvas, "course");
        java.util.List<?> raceLevels = (java.util.List<?>) field(course, "levels");
        check(raceLevels.size() == 7, "race uses the configured total level count");
        java.util.Set<String> generatedIds = new java.util.HashSet<>();
        for (Object level : raceLevels) {
            Object template = field(level, "template");
            String id = (String) field(template, "id");
            check(candidateIds.contains(id), "randomly generated level belongs to the selected pool");
            generatedIds.add(id);
        }
        check(generatedIds.size() <= 3 && generatedIds.size() < raceLevels.size(),
            "a race longer than the selected pool allows level repeats");

        Object world = field(canvas, "world");
        java.util.List<Object> balls = (java.util.List<Object>) field(world, "balls");
        check(balls.size() == 6, "race contains the selected participant roster");
        ViewGroup rankingRows = (ViewGroup) field(page, "rankingList");
        check(rankingRows.getChildCount() == 6
                && findText(game.getWindow().getDecorView(), english ? "Racing" : "正在比赛") != null
                && findText(game.getWindow().getDecorView(), english ? "All balls" : "所有小球") != null,
            "live race shows a switchable ranking of active racers");
        View rankingScroll = (View) field(page, "rankingScroll");
        check(rankingScroll instanceof android.widget.ScrollView,
            "live ranking supports vertical scrolling");
        Button allRankings = (Button) field(page, "allRankingButton");
        ui(() -> allRankings.performClick());
        check((Boolean) field(page, "showAllRankings") && rankingRows.getChildCount() == 6,
            "all-racers ranking can be selected during the race");
        Button activeRankings = (Button) field(page, "activeRankingButton");
        ui(() -> activeRankings.performClick());
        check(!(Boolean) field(page, "showAllRankings"),
            "active-racers ranking can be restored during the race");
        check(Math.abs((Float) field(world, "gravity") - 980f) < .01f,
            "race uses the configured gravity value of 1g");
        float sharedElasticity = (Float) field(balls.get(0), "elasticity");
        boolean sharedCollisionSetting = (Boolean) field(balls.get(0), "ballCollisionEnabled");
        boolean sameBallPhysics = true;
        for (Object ball : balls) {
            sameBallPhysics &= Math.abs((Float) field(ball, "elasticity") - sharedElasticity) < .001f;
            sameBallPhysics &= (Boolean) field(ball, "ballCollisionEnabled") == sharedCollisionSetting;
        }
        check(Math.abs(sharedElasticity - .70f) < .01f && !sharedCollisionSetting && sameBallPhysics,
            "all balls share one elasticity and the selected ball-to-ball collision setting");
        String mixedName = english ? "Light blue Green 7" : "浅蓝 Green 7";
        BallPhysicsNameCheck(canvas, mixedName);

        check((Integer) field(canvas, "selectedCamera") == 2,
            "new races default to following the leading unfinished racer");
        TextView pauseButton = findText(game.getWindow().getDecorView(), english ? "Pause" : "暂停");
        TextView followButton = findText(game.getWindow().getDecorView(), english ? "Follow settings" : "跟随设置");
        check(pauseButton != null && followButton != null
                && findText(game.getWindow().getDecorView(), english ? "Settings" : "设置") == null
                && findText(game.getWindow().getDecorView(), english ? "Back to setup" : "回到设置") == null
                && findText(game.getWindow().getDecorView(), english ? "Home" : "桌面") != null,
            "race footer contains pause, follow settings and desktop actions");
        check(((ViewGroup) pauseButton.getParent()).getChildCount() == 2,
            "race action row contains only pause and follow settings beside the host desktop action");

        await(() -> {
            try { return elapsed(world) > .08f; }
            catch (Exception ignored) { return false; }
        }, "fixed-step race advances after the shared countdown");
        ui(() -> followButton.performClick());
        Spinner followMode = (Spinner) field(page, "followModeSpinner");
        AlertDialog followDialog = (AlertDialog) field(page, "followDialog");
        ui(() -> {
            followMode.setSelection(1);
            followDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        await(() -> {
            try { return (Integer) field(canvas, "selectedCamera") == 1; }
            catch (Exception ignored) { return false; }
        }, "follow dialog applies the selected mode");
        check((Integer) field(canvas, "selectedCamera") == 1,
            "follow settings can switch away from the default leader mode");

        ui(() -> {
            try { call(canvas, "setCameraMode", new Class<?>[]{int.class, String.class}, 0, ""); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        Thread.sleep(100L);
        float cameraBeforeDrag = (Float) field(canvas, "cameraX");
        int[] origin = new int[2];
        canvas.getLocationOnScreen(origin);
        long time = SystemClock.uptimeMillis();
        float x = origin[0] + canvas.getWidth() * .5f;
        float y = origin[1] + canvas.getHeight() * .5f;
        sendPointerSync(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0));
        sendPointerSync(MotionEvent.obtain(time, time + 45, MotionEvent.ACTION_MOVE, x + 45f, y + 25f, 0));
        sendPointerSync(MotionEvent.obtain(time, time + 90, MotionEvent.ACTION_UP, x + 45f, y + 25f, 0));
        waitForIdleSync();
        check(Math.abs((Float) field(canvas, "cameraX") - cameraBeforeDrag) > 1f,
            "manual follow mode pans the camera");

        ui(() -> {
            try { call(canvas, "setCameraMode", new Class<?>[]{int.class, String.class}, 2, ""); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        Object raceProgressClass = Class.forName("org.daidai.modules.ballrace.RaceProgress", true,
            page.getClass().getClassLoader());
        Class<?> raceCourseClass = course.getClass();
        Object finishedLeader = balls.get(0);
        Object activeLeader = balls.get(1);
        ui(() -> {
            try { call(canvas, "stopFrame", new Class<?>[]{}); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        setField(finishedLeader, "finishTime", 1f);
        for (int index = 1; index < balls.size(); index++) {
            Object ball = balls.get(index);
            setField(ball, "finishTime", -1f);
            setField(ball, "y", index == 1 ? 1000f : 100f);
        }
        Object unfinishedLeader = call(raceProgressClass, "leaderAmongUnfinished",
            new Class<?>[]{java.util.List.class, raceCourseClass}, balls, course);
        check(unfinishedLeader == activeLeader && unfinishedLeader != finishedLeader,
            "camera regression fixture places the leading active racer behind a finished leader");
        setField(canvas, "cameraInitialized", false);
        ui(() -> {
            try { call(canvas, "updateCamera", new Class<?>[]{}); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        check(Math.abs((Float) field(canvas, "smoothedX") - (Float) field(unfinishedLeader, "x")) < .01f
                && Math.abs((Float) field(canvas, "smoothedY") - (Float) field(unfinishedLeader, "y")) < .01f,
            "leader camera follows the leading unfinished ball after the former leader finishes");

        ui(() -> {
            try {
                call(canvas, "stopFrame", new Class<?>[]{});
                call(world, "resetClock", new Class<?>[]{});
                for (int index = 0; index < balls.size(); index++) {
                    Object ball = balls.get(index);
                    setField(ball, "x", 48f + index * 48f);
                    setField(ball, "y", 80f + index * 24f);
                    setField(ball, "vx", 0f);
                    setField(ball, "vy", 0f);
                    setField(ball, "angularVelocity", 0f);
                }
                call(world, "start", new Class<?>[]{});
                setField(canvas, "state", 1);
                setField(canvas, "lastFrameNanos", 0L);
                call(canvas, "scheduleFrame", new Class<?>[]{});
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
        await(() -> {
            try { return elapsed(world) > .08f; }
            catch (Exception ignored) { return false; }
        }, "deterministic running fixture advances before the pause check");
        AtomicReference<Boolean> paused = new AtomicReference<>(false);
        ui(() -> {
            try { paused.set((Boolean) call(canvas, "pauseRace", new Class<?>[]{})); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        check(paused.get(), "running race can be paused");
        float pauseTime = elapsed(world);
        Thread.sleep(350L);
        check(Math.abs(elapsed(world) - pauseTime) < .001f, "simulation time stops while paused");
        ui(() -> {
            try { call(canvas, "resumeRace", new Class<?>[]{}); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        await(() -> {
            try { return elapsed(world) > pauseTime + .05f; }
            catch (Exception ignored) { return false; }
        }, "race resumes from the paused simulation time");
        float elapsed = elapsed(world);
        ui(() -> {
            try { setField(world, "timeLimitSeconds", elapsed + .12f); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        await(() -> {
            try { return (Integer) field(canvas, "state") == 3; }
            catch (Exception ignored) { return false; }
        },
            "time limit ends the race and leaves a ranked result");
        await(() -> {
            try { return (Boolean) field(page, "rankingFinalized"); }
            catch (Exception ignored) { return false; }
        }, "finished state expands the result ranking");
        waitForIdleSync();
        check((Boolean) call(canvas, "finished", new Class<?>[]{}), "completed race exposes a stable finished state");
        check((Boolean) field(page, "rankingFinalized")
                && ((View) field(page, "raceStatus")).getVisibility() == View.GONE
                && ((View) field(page, "raceActions")).getVisibility() == View.GONE,
            "finished race expands the results ranking and clears race controls");
        check(((FrameLayout.LayoutParams) ((View) field(page, "rankingPanel")).getLayoutParams()).width == -1
                && rankingRows.getChildCount() == balls.size(),
            "final ranking fills the race canvas and includes every racer");
        TextView finalRow = (TextView) rankingRows.getChildAt(0);
        check(finalRow.getText().toString().contains(english ? "Unfinished" : "未完赛"),
            "unfinished racers remain ranked and receive the unfinished label");
        ui(() -> game.finish());
        game = null;
    }
    private void BallPhysicsNameCheck(View canvas, String expectedName) throws Exception {
        Paint labelPaint = (Paint) field(canvas, "ballNamePaint");
        check(labelPaint.getTextAlign() == Paint.Align.CENTER && labelPaint.measureText(expectedName) > 0f,
            "mixed-script ball labels use centered text measurement");
    }

    private void testSettingsPage() throws Exception {
        Intent intent = new Intent(context, hostClass("InternalAppActivity"));
        intent.putExtra("internal_app", "ballrace").putExtra("internal_page", "settings")
            .putExtra("settings_mode", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        game = startActivitySync(intent);
        waitForIdleSync();
        check(field(game, "page") != null, "ball-race settings page opens in the host activity");
        check(findText(game.getWindow().getDecorView(), args.getString("language").equals("en")
                ? "Enable ball-to-ball collision" : "开启球间碰撞") != null,
            "settings identify the shared ball-to-ball collision behavior");
        check(findText(game.getWindow().getDecorView(), args.getString("language").equals("en")
                ? "Home" : "桌面") != null,
            "settings page includes the host desktop action");
        ui(() -> game.finish());
        game = null;
    }

    private void testDesktopReturnAndReopen() throws Exception {
        Intent pageIntent = (Intent) hostClass("ModuleRuntime")
            .getMethod("pageIntent", Context.class, String.class, String.class)
            .invoke(null, host, "ballrace", "main");
        game = startActivitySync(pageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        String homeLabel = args.getString("language").equals("en") ? "Home" : "桌面";
        TextView home = findText(game.getWindow().getDecorView(), homeLabel);
        check(home != null && home.isClickable(), "race page exposes the host desktop action");
        ui(() -> home.performClick());
        await(() -> game.isFinishing(), "desktop action closes the race page");
        game = null;
        check(host != null && !host.isFinishing(), "the test host returns to its launcher activity");
        String raceTitle = args.getString("language").equals("en") ? "Ball Race" : "小球竞赛";
        await(() -> findText(host.getWindow().getDecorView(), raceTitle) != null,
            "launcher redraw completes after returning from the race page");
        java.util.List<String> desktopTexts = new java.util.ArrayList<>();
        collectTexts(host.getWindow().getDecorView(), desktopTexts);
        check(desktopTexts.contains(raceTitle), "desktop shows the race app: " + desktopTexts);
        check(findText(host.getWindow().getDecorView(), args.getString("language").equals("en")
                ? "App store" : "应用商店") == null, "desktop hides the app store");
        check(findText(host.getWindow().getDecorView(), args.getString("language").equals("en")
                ? "Weather" : "天气") == null, "desktop hides other toolbox apps");
        TextView tileLabel = findText(host.getWindow().getDecorView(), raceTitle);
        View tile = tileLabel == null ? null : (View) tileLabel.getParent();
        check(tile != null && tile.isClickable(), "race desktop tile is actionable");
        ActivityMonitor monitor = addMonitor(hostClass("InternalAppActivity").getName(), null, false);
        ui(() -> tile.performClick());
        Activity reopened = waitForMonitorWithTimeout(monitor, 10000L);
        removeMonitor(monitor);
        game = reopened;
        check(game != null, "race app opens again from the test desktop");
        check(field(game, "page") != null, "reopened race page is loaded from its signed package");
        ui(() -> game.finish());
        game = null;
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            context = getTargetContext();
            hostLoader = context.getClassLoader();
            String language = args.getString("language", "zh-CN");
            SharedPreferences toolbox = context.getSharedPreferences("toolbox", 0);
            previousToolbox = new HashMap<>(toolbox.getAll());
            toolbox.edit().putString("app_language", language).putInt("seen_version", Integer.MAX_VALUE).commit();
            SharedPreferences state = context.getSharedPreferences("apps_host", 0);
            wasInstalled = false;
            File packageFile = new File(context.getFilesDir(), "apps/ballrace.ddapp");
            File rollbackFile = new File(context.getFilesDir(), "apps/ballrace.previous.ddapp");
            if (packageFile.isFile()) previousPackage = Files.readAllBytes(packageFile.toPath());
            if (rollbackFile.isFile()) previousRollbackPackage = Files.readAllBytes(rollbackFile.toPath());
            previousPreferences = new HashMap<>(context.getSharedPreferences("ballrace_module", 0).getAll());

            android.content.pm.PackageInfo regular = context.getPackageManager().getPackageInfo("org.simpleweather.app", 0);
            check("org.simpleweather.app".equals(regular.packageName),
                "regular toolbox remains installed alongside the test host");
            host = startActivitySync(new Intent().setClass(context, hostClass("MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            String raceTitle = language.equals("en") ? "Ball Race" : "小球竞赛";
            check(findText(host.getWindow().getDecorView(), raceTitle) != null,
                "test desktop contains the race app");
            check(findText(host.getWindow().getDecorView(), language.equals("en") ? "App store" : "应用商店") == null,
                "test desktop omits the store entry");
            check(findText(host.getWindow().getDecorView(), language.equals("en") ? "Weather" : "天气") == null,
                "test desktop omits other toolbox apps");
            previousAppHost = new HashMap<>(state.getAll());
            apps = field(host, "apps");
            wasInstalled = (Boolean) call(apps, "installed", new Class<?>[]{String.class}, "ballrace");
            if (wasInstalled) {
                previousPackage = Files.readAllBytes(packageFile.toPath());
                ui(() -> {
                    try { call(apps, "uninstall", new Class<?>[]{String.class}, "ballrace"); }
                    catch (Exception error) { throw new RuntimeException(error); }
                });
                long deadline = SystemClock.elapsedRealtime() + 10000L;
                while ((Boolean) field(apps, "busy") && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25L);
                check(!(Boolean) call(apps, "installed", new Class<?>[]{String.class}, "ballrace"),
                    "pre-existing race package can be isolated for testing");
            }
            install();
            check((Boolean) call(apps, "installed", new Class<?>[]{String.class}, "ballrace"),
                "signed race package installs into the host");
            context.getSharedPreferences("ballrace_module", 0).edit().clear().commit();
            Intent pageIntent = (Intent) hostClass("ModuleRuntime")
                .getMethod("pageIntent", Context.class, String.class, String.class)
                .invoke(null, host, "ballrace", "main");
            game = startActivitySync(pageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            testRaceView();
            testSettingsPage();
            testDesktopReturnAndReopen();
            result.putString("result", "PASS: " + checks + " ball-race UI, camera and lifecycle checks (" + language + ")");
        } catch (Throwable failure) {
            StringWriter log = new StringWriter();
            failure.printStackTrace(new PrintWriter(log));
            result.putString("result", "FAIL: " + log);
        } finally {
            if (game != null) ui(() -> game.finish());
            try {
                if (apps != null) {
                    if ((Boolean) call(apps, "installed", new Class<?>[]{String.class}, "ballrace")) {
                        ui(() -> {
                            try { call(apps, "uninstall", new Class<?>[]{String.class}, "ballrace"); }
                            catch (Exception error) { throw new RuntimeException(error); }
                        });
                        long deadline = SystemClock.elapsedRealtime() + 10000L;
                        while ((Boolean) field(apps, "busy") && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25L);
                    }
                    if (previousPackage != null) {
                        call(hostClass("ModuleRuntime"), "beginChange",
                            new Class<?>[]{Context.class, String.class}, context, "ballrace");
                        try {
                            File directory = new File(context.getFilesDir(), "apps");
                            directory.mkdirs();
                            File packageFile = new File(directory, "ballrace.ddapp");
                            Files.write(packageFile.toPath(), previousPackage);
                            if (!packageFile.setReadOnly()) throw new IllegalStateException("Cannot restore race package");
                            File rollbackFile = new File(directory, "ballrace.previous.ddapp");
                            if (previousRollbackPackage == null) Files.deleteIfExists(rollbackFile.toPath());
                            else {
                                Files.write(rollbackFile.toPath(), previousRollbackPackage);
                                rollbackFile.setReadOnly();
                            }
                        } finally {
                            call(hostClass("ModuleRuntime"), "endChange", new Class<?>[]{String.class}, "ballrace");
                        }
                    }
                    SharedPreferences prefs = context.getSharedPreferences("ballrace_module", 0);
                    call(hostClass("ConfigurationTransfer"), "write",
                        new Class<?>[]{SharedPreferences.class, Map.class}, prefs, previousPreferences);
                }
                if (previousToolbox != null) call(hostClass("ConfigurationTransfer"), "write",
                    new Class<?>[]{SharedPreferences.class, Map.class},
                    context.getSharedPreferences("toolbox", 0), previousToolbox);
                if (previousAppHost != null) call(hostClass("ConfigurationTransfer"), "write",
                    new Class<?>[]{SharedPreferences.class, Map.class},
                    context.getSharedPreferences("apps_host", 0), previousAppHost);
                if (host != null) ui(() -> host.finish());
            } catch (Throwable failure) {
                result.putString("result", "FAIL: fixture restore " + failure + "\n" + result.getString("result"));
            }
            finish(Activity.RESULT_OK, result);
        }
    }
}
