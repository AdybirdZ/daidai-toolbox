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
        return Class.forName("org.daidai.toolbox." + name, true, hostLoader);
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

    private void restorePreferences(String name, Map<String, ?> values) {
        if (values == null) return;
        SharedPreferences.Editor editor = context.getSharedPreferences(name, 0).edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof java.util.Set) {
                java.util.Set<String> strings = new java.util.HashSet<>();
                for (Object item : (java.util.Set<?>) value) strings.add(String.valueOf(item));
                editor.putStringSet(entry.getKey(), strings);
            }
        }
        if (!editor.commit()) throw new IllegalStateException("Could not restore " + name);
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

    private EditText findInput(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText found = findInput(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private java.util.List<EditText> findInputs(View view) {
        java.util.List<EditText> result = new java.util.ArrayList<>();
        if (view instanceof EditText) result.add((EditText) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) result.addAll(findInputs(group.getChildAt(index)));
        }
        return result;
    }

    private java.util.List<CheckBox> findCheckBoxes(View view) {
        java.util.List<CheckBox> result = new java.util.ArrayList<>();
        if (view instanceof CheckBox) result.add((CheckBox) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) result.addAll(findCheckBoxes(group.getChildAt(index)));
        }
        return result;
    }

    private boolean allUnchecked(java.util.List<CheckBox> boxes) {
        for (CheckBox box : boxes) if (box.isChecked()) return false;
        return !boxes.isEmpty();
    }

    private View findContentDescription(View view, String description) {
        if (description.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = findContentDescription(group.getChildAt(index), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int countColor(android.graphics.Bitmap bitmap, int color) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) if (bitmap.getPixel(x, y) == color) count++;
        }
        return count;
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

    private boolean clickActiveText(AccessibilityNodeInfo node, String value) {
        if (node == null) return false;
        CharSequence text = node.getText();
        if (text != null && value.equalsIgnoreCase(text.toString())) {
            AccessibilityNodeInfo target = node;
            while (target != null) {
                boolean matched = target.isClickable()
                    && target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                AccessibilityNodeInfo parent = target.getParent();
                if (target != node) target.recycle();
                if (matched) {
                    if (parent != null) parent.recycle();
                    return true;
                }
                target = parent;
            }
            android.graphics.Rect bounds = new android.graphics.Rect();
            node.getBoundsInScreen(bounds);
            if (!bounds.isEmpty()) {
                long time = SystemClock.uptimeMillis();
                float x = bounds.centerX();
                float y = bounds.centerY();
                sendPointerSync(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0));
                sendPointerSync(MotionEvent.obtain(time, time + 40L, MotionEvent.ACTION_UP, x, y, 0));
                return true;
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (clickActiveText(child, value)) return true;
        }
        return false;
    }

    private boolean clickActiveText(String value) {
        return clickActiveText(getUiAutomation().getRootInActiveWindow(), value);
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
        context.getSharedPreferences("ballrace_module", 0).edit()
            .remove("level_rails_json").commit();
        TextView editorEntry = findText(game.getWindow().getDecorView(), english ? "Editor" : "编辑器");
        check(editorEntry != null, "main menu exposes the editor");
        ui(() -> editorEntry.performClick());
        TextView editBalls = findText(game.getWindow().getDecorView(), english ? "Edit balls" : "编辑小球");
        check(editBalls != null && editBalls.isEnabled(),
            "editor menu exposes the ball editor entry");
        ui(() -> editBalls.performClick());
        await(() -> findText(game.getWindow().getDecorView(), english ? "Red" : "红色") != null,
            "ball editor lists the available ball appearances");
        TextView ballExport = findText(game.getWindow().getDecorView(), english ? "Export balls" : "导出小球");
        ui(() -> ballExport.performClick());
        TextView exportSelected = findText(game.getWindow().getDecorView(), english ? "Export selected" : "导出所选");
        check(exportSelected != null && allUnchecked(findCheckBoxes(game.getWindow().getDecorView())),
            "ball export selection mode opens without selecting a ball");
        ui(() -> exportSelected.performClick());
        check(findText(game.getWindow().getDecorView(), english ? "Export balls" : "导出小球") != null
                && findText(game.getWindow().getDecorView(), english ? "Export selected" : "导出所选") == null,
            "exporting an empty ball selection exits multi-select mode");
        TextView firstBall = findText(game.getWindow().getDecorView(), english ? "Red" : "红色");
        check(firstBall != null, "ball editor shows a ball name below its preview");
        ui(() -> ((View) firstBall.getParent().getParent()).performClick());
        await(() -> findText(game.getWindow().getDecorView(), english ? "Outline color (RRGGBB)" : "描边颜色（RRGGBB）") != null,
            "ball editor opens the selected ball appearance");
        check(findText(game.getWindow().getDecorView(), english ? "Trail color (RRGGBB)" : "尾迹颜色（RRGGBB）") != null,
            "ball editor exposes outline and trail colors");
        check(findText(game.getWindow().getDecorView(), english ? "Name color (RRGGBB)" : "名字颜色（RRGGBB）") != null
                && findText(game.getWindow().getDecorView(), english ? "Name outline color (RRGGBB)" : "名字描边颜色（RRGGBB）") != null,
            "ball editor exposes the editable name fill and outline colors");
        EditText nameColor = (EditText) field(page, "ballNameColorInput");
        EditText nameOutline = (EditText) field(page, "ballNameOutlineInput");
        View appearancePreview = findContentDescription(game.getWindow().getDecorView(), english ? "Red" : "红色");
        ui(() -> { nameColor.setText("00FF00"); nameOutline.setText("FF00FF"); });
        android.graphics.Bitmap previewBitmap = android.graphics.Bitmap.createBitmap(
            appearancePreview.getWidth(), appearancePreview.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        ui(() -> appearancePreview.draw(new android.graphics.Canvas(previewBitmap)));
        check(countColor(previewBitmap, 0xFF00FF00) > 0 && countColor(previewBitmap, 0xFFFF00FF) > 0,
            "ball appearance preview renders the live name fill and outline colors");
        previewBitmap.recycle();
        TextView ballSave = findText(game.getWindow().getDecorView(), english ? "Save and return" : "保存并返回");
        check(ballSave != null, "single ball editor provides save and return");
        ui(() -> ballSave.performClick());
        TextView ballListBack = findText(game.getWindow().getDecorView(), english ? "Back" : "返回");
        ui(() -> ballListBack.performClick());
        TextView editLevel = findText(game.getWindow().getDecorView(), english ? "Edit levels" : "编辑关卡");
        check(editLevel != null, "editor menu exposes the built-in level editor");
        ui(() -> editLevel.performClick());
        ui(() -> {
            try { call(page, "showLevelEditor", new Class<?>[]{String.class}, "slopes"); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        await(() -> findText(game.getWindow().getDecorView(), english ? "Level canvas" : "关卡画布") != null,
            "level editor opens");
        check(findText(game.getWindow().getDecorView(), english ? "Import level" : "导入关卡") == null
                && findText(game.getWindow().getDecorView(), english ? "Export level" : "导出关卡") == null
                && findText(game.getWindow().getDecorView(), english ? "New level" : "新建关卡") == null,
            "single-level editor hides list-level import, export and create actions");
        TextView properties = findText(game.getWindow().getDecorView(), english ? "Level properties" : "关卡属性");
        check(properties != null, "level editor exposes its properties action at the top");
        ui(() -> properties.performClick());
        getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        SystemClock.sleep(300L);
        check("slopes".equals(editorLevelId(page)), "built-in slope course opens from its stable id");

        EditText levelName = (EditText) field(page, "editorLevelNameInput");
        TextView saveName = findText(game.getWindow().getDecorView(), english ? "Save level name" : "保存关卡名称");
        ui(() -> {
            levelName.setText("Slope 2A");
            saveName.performClick();
        });
        check("Slope 2A".equals(field(field(page, "editorLevel"), "name")),
            "built-in level can be renamed without changing its stable id");

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
        ui(() -> {
            try { call(page, "deleteSelectedObstacle", new Class<?>[]{}); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        await(() -> activeWindowContains(english ? "Delete the selected object or port?" : "确定删除当前选中的对象或出入口点？"),
            "delete confirmation dialog opens");
        check(clickActiveText(english ? "Delete selected" : "删除所选"),
            "delete confirmation can be accepted");
        await(() -> {
            try { return editorObstacleCount(page) == before; }
            catch (Exception error) { throw new RuntimeException(error); }
        }, "selected obstacle deletion completes");
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

        TextView addEllipse = findText(game.getWindow().getDecorView(), english ? "Add ellipse" : "添加椭圆");
        check(addEllipse != null, "editor can add elliptical obstacles");
        ui(() -> addEllipse.performClick());
        Object ellipse = field(page, "selectedObstacle");
        check("ellipse".equals(field(ellipse, "type")), "new ellipse is selected for editing");
        EditText ellipseRadiusY = (EditText) field(page, "editorRadiusYInput");
        EditText ellipseColor = (EditText) field(page, "editorColorInput");
        EditText ellipseMotionX = (EditText) field(page, "editorMotionXInput");
        EditText ellipseMotionPeriod = (EditText) field(page, "editorMotionPeriodInput");
        EditText ellipsePivotX = (EditText) field(page, "editorPivotXInput");
        EditText ellipseRotationSpeed = (EditText) field(page, "editorRotationSpeedInput");
        EditText ellipseImpulseX = (EditText) field(page, "editorImpulseXInput");
        Spinner ellipseAction = (Spinner) field(page, "editorActionInput");
        ui(() -> {
            ellipseRadiusY.setText("33");
            ellipseColor.setText("33AA77");
            ellipseMotionX.setText("8");
            ellipseMotionPeriod.setText("2");
            ellipsePivotX.setText("4");
            ellipseRotationSpeed.setText("45");
            ellipseImpulseX.setText("2");
            ellipseAction.setSelection(1);
        });
        TextView applyObstacleProperties = findText(game.getWindow().getDecorView(), english ? "Apply properties" : "应用属性");
        check(applyObstacleProperties != null, "selected obstacle exposes property application");
        ui(() -> applyObstacleProperties.performClick());
        check(Math.abs((Float) field(ellipse, "radiusY") - 33f) < .01f
                && (Integer) field(ellipse, "color") == 0xFF33AA77
                && Math.abs((Float) field(ellipse, "motionX") - 8f) < .01f
                && Math.abs((Float) field(ellipse, "motionPeriodSeconds") - 2f) < .01f
                && Math.abs((Float) field(ellipse, "pivotOffsetX") - 4f) < .01f
                && Math.abs((Float) field(ellipse, "rotationSpeedDegreesPerSecond") - 45f) < .01f
                && Math.abs((Float) field(ellipse, "collisionImpulseX") - 2f) < .01f
                && (Integer) field(ellipse, "collisionAction") == 1,
            "ellipse geometry, appearance, motion, rotation and collision interaction are editable");
        ui(() -> {
            try {
                call(field(page, "editorLevel"), "remove", new Class<?>[]{ellipse.getClass()}, ellipse);
                call(page, "selectEditorObstacle", new Class<?>[]{slope.getClass()}, slope);
                call(page, "saveEditorLevel", new Class<?>[]{});
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });

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
        TextView discardChanges = findText(game.getWindow().getDecorView(), english ? "Discard and return" : "不保存并返回");
        check(saveChanges != null && discardChanges != null
                && findText(game.getWindow().getDecorView(), english ? "Back" : "返回") == null,
            "editor save and discard actions share one footer row without a separate back button");
        ui(() -> saveChanges.performClick());
        TextView reopenLevel = findText(game.getWindow().getDecorView(), english ? "Slope 2A" : "Slope 2A");
        if (reopenLevel == null) reopenLevel = findText(game.getWindow().getDecorView(), english ? "Slopes" : "Slope 2A");
        if (reopenLevel == null) reopenLevel = findTextContaining(game.getWindow().getDecorView(), "Slope 2A");
        ui(() -> {
            try { call(page, "showLevelEditor", new Class<?>[]{String.class}, "slopes"); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
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
        check(editorBack != null, "saved level returns to the level list");
        ui(() -> editorBack.performClick());
        TextView menuBack = findText(game.getWindow().getDecorView(), english ? "Back" : "返回");
        ui(() -> menuBack.performClick());
        await(() -> findText(game.getWindow().getDecorView(), english ? "Start a game" : "开始一场游戏") != null,
            "editor menu returns to the main menu");

        ui(() -> {
            try { call(page, "showLevelEditor", new Class<?>[]{String.class}, "rails"); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        EditText rename = findInput(game.getWindow().getDecorView());
        check(rename != null && "rails".equals(editorLevelId(page)),
            "built-in rails course exposes its editable name field");
        check((english ? "Rails" : "轨道关").equals(rename.getText().toString()),
            "rails editor uses the localized course name instead of its stable id");
        ui(() -> {
            rename.setText(english ? "Rails renamed" : "轨道改名测试");
            TextView saveRailsName = findText(game.getWindow().getDecorView(), english ? "Save level name" : "保存关卡名称");
            saveRailsName.performClick();
        });
        check(findInput(game.getWindow().getDecorView()).hasFocus() == false,
            "saving a level name removes focus from its input");
        ui(() -> {
            TextView save = findText(game.getWindow().getDecorView(), english ? "Save and return" : "保存并返回");
            save.performClick();
        });
        check((english ? "Rails renamed" : "轨道改名测试").equals(
                call(page, "builtInLevelLabel", new Class<?>[]{String.class}, "rails")),
            "renamed built-in rails course remains renamed in the level list");
        ui(() -> {
            TextView levelBack = findText(game.getWindow().getDecorView(), english ? "Back" : "返回");
            levelBack.performClick();
            TextView editorBackButton = findText(game.getWindow().getDecorView(), english ? "Back" : "返回");
            editorBackButton.performClick();
        });
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
        TextView menuSettings = findText(game.getWindow().getDecorView(), english ? "Settings" : "设置");
        check(startGame != null && editor != null && menuSettings != null, "main menu exposes its three actions");
        check(startGame.getHeight() >= 65 && editor.getHeight() >= 65 && menuSettings.getHeight() >= 65,
            "main menu actions have large tap targets");

        ui(() -> menuSettings.performClick());
        await(() -> findText(game.getWindow().getDecorView(),
            english ? "Game performance" : "游戏性能") != null,
            "settings hub opens");
        TextView defaultsButton = findText(game.getWindow().getDecorView(),
            english ? "Default settings" : "默认设置");
        check(defaultsButton != null, "settings hub exposes default settings");
        ui(() -> defaultsButton.performClick());
        await(() -> findText(game.getWindow().getDecorView(),
            english ? "Default camera follow mode" : "进入比赛时的默认跟随模式") != null,
            "default settings page opens");
        Spinner defaultFollow = (Spinner) field(page, "defaultFollowSpinner");
        CheckBox defaultCollision = (CheckBox) field(page, "defaultBallCollision");
        SeekBar defaultObstacle = (SeekBar) field(page, "defaultObstacleElasticitySeek");
        SeekBar defaultBall = (SeekBar) field(page, "defaultBallElasticitySeek");
        SeekBar defaultGravity = (SeekBar) field(page, "defaultGravitySeek");
        check(defaultFollow.getSelectedItemPosition() == 2 && defaultCollision.isChecked()
                && defaultObstacle.getProgress() == 100 && defaultBall.getProgress() == 68
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
        ViewGroup pinkCard = (ViewGroup) pink.getParent().getParent();
        TextView addPink = findText(pinkCard, english ? "Add a ball" : "添加小球");
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
        check(poolChecks.size() == 6, "all six built-in levels can be selected for random draws");
        for (CheckBox selector : poolChecks) {
            ViewGroup card = (ViewGroup) selector.getParent();
            check(card.getChildCount() == 2 && card.getChildAt(0) instanceof View,
                "level selection cards show a preview above the level name");
        }
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
        EditText levelCount = null;
        for (EditText input : findInputs(game.getWindow().getDecorView())) {
            if (input.getInputType() == (android.text.InputType.TYPE_CLASS_NUMBER)) { levelCount = input; break; }
        }
        CheckBox collisions = (CheckBox) field(page, "matchBallCollision");
        SeekBar obstacleElasticity = (SeekBar) field(page, "matchObstacleElasticitySeek");
        SeekBar ballElasticity = (SeekBar) field(page, "matchBallElasticitySeek");
        SeekBar gravity = (SeekBar) field(page, "matchGravitySeek");
        final EditText countInput = levelCount;
        ui(() -> { countInput.requestFocus(); countInput.setText("3"); countInput.clearFocus(); });
        TextView repeatRule = findTextContaining(game.getWindow().getDecorView(),
            english ? "will not repeat" : "不会重复");
        check(repeatRule != null && (english ? repeatRule.getText().toString().contains("will not repeat")
                : repeatRule.getText().toString().contains("不会重复")),
            "a race no longer than the selected pool uses the no-repeat rule");
        ui(() -> {
            countInput.requestFocus(); countInput.setText("7"); countInput.clearFocus();
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
        Object world = field(canvas, "world");
        ui(() -> {
            try {
                call(canvas, "stopFrame", new Class<?>[]{});
                call(world, "start", new Class<?>[]{});
                setField(canvas, "state", 1);
                call(canvas, "pauseRace", new Class<?>[]{});
            }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        check((Boolean) call(canvas, "paused", new Class<?>[]{}),
            "race view enters a stable paused state for UI checks");
        Object course = field(canvas, "course");
        java.util.List<?> raceLevels = (java.util.List<?>) field(course, "levels");
        check(raceLevels.size() == 7, "race uses the configured total level count");
        check((Float) field(raceLevels.get(0), "originY") >= 400f,
            "first track obstacle section starts below a separate launch area");
        java.util.List<?> starts = (java.util.List<?>) call(course.getClass(), "staggeredStarts",
            new Class<?>[]{int.class, float.class, float.class}, 16, 360f, 11f);
        check(Math.abs((Float) field(starts.get(0), "y") - (Float) field(starts.get(7), "y")) < .01f
                && (Float) field(starts.get(8), "y") > (Float) field(starts.get(7), "y"),
            "launch formation places eight close-packed balls in each row");
        java.util.Set<String> generatedIds = new java.util.HashSet<>();
        for (Object level : raceLevels) {
            Object template = field(level, "template");
            String id = (String) field(template, "id");
            check(candidateIds.contains(id), "randomly generated level belongs to the selected pool");
            generatedIds.add(id);
        }
        check(generatedIds.size() <= 3 && generatedIds.size() < raceLevels.size(),
            "a race longer than the selected pool allows level repeats");

        java.util.List<Object> balls = (java.util.List<Object>) field(world, "balls");
        check(balls.size() == 6, "race contains the selected participant roster");
        ViewGroup rankingRows = (ViewGroup) field(page, "rankingList");
        check(rankingRows.getChildCount() == 6
                && findText(game.getWindow().getDecorView(), english ? "Leaderboard" : "排行榜") != null
                && findText(game.getWindow().getDecorView(), english ? "All balls" : "所有小球") != null,
            "live race shows a switchable ranking of active racers");
        TextView liveTitle = (TextView) field(page, "rankingHeader");
        check(liveTitle.getGravity() == android.view.Gravity.CENTER,
            "live ranking title is centered");
        View rankingScroll = (View) field(page, "rankingScroll");
        check(rankingScroll instanceof android.widget.ScrollView,
            "live ranking supports vertical scrolling");
        check(((TextView) rankingRows.getChildAt(0)).getText().toString().contains(english ? "Level" : "第 "),
            "active racers show their current level instead of an unfinished label");
        TextView rankingHeader = (TextView) field(page, "rankingHeader");
        ui(() -> rankingHeader.performClick());
        check(((View) field(page, "rankingPanel")).getVisibility() == View.GONE
                && ((View) field(page, "rankingExpandButton")).getVisibility() == View.VISIBLE,
            "collapsing the ranking hides its translucent panel and shows a reopen control");
        View expandRanking = (View) field(page, "rankingExpandButton");
        ui(() -> expandRanking.performClick());
        check(rankingScroll.getVisibility() == View.VISIBLE
                && ((View) field(page, "rankingTabs")).getVisibility() == View.VISIBLE
                && findText(game.getWindow().getDecorView(), english ? "Leaderboard" : "排行榜") != null,
            "expanded ranking restores its title and view-selection buttons");
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
        TextView zoomAction = findText(game.getWindow().getDecorView(), english ? "Zoom" : "缩放");
        TextView settingsAction = findText(game.getWindow().getDecorView(), english ? "Settings" : "设置");
        TextView setupAction = findText(game.getWindow().getDecorView(), english ? "Back to setup" : "回到设置");
        TextView homeAction = findText(game.getWindow().getDecorView(), english ? "Home" : "桌面");
        check(pauseButton != null && followButton != null && zoomAction != null
                && settingsAction == null && setupAction == null && homeAction != null,
            "race footer contains pause, follow settings and desktop actions; pause=" + (pauseButton != null)
                + ", follow=" + (followButton != null) + ", zoom=" + (zoomAction != null)
                + ", settings=" + (settingsAction != null) + ", setup=" + (setupAction != null)
                + ", home=" + (homeAction != null));
        check(((ViewGroup) pauseButton.getParent()).getChildCount() == 4,
            "race action row contains pause, follow settings and zoom beside the host desktop action");
        TextView zoomButton = zoomAction;
        ui(() -> zoomButton.performClick());
        waitForIdleSync();
        FrameLayout zoomFrame = (FrameLayout) field(page, "raceFrame");
        View zoomPanel = (View) field(page, "zoomContainer");
        android.graphics.Rect frameBounds = new android.graphics.Rect();
        android.graphics.Rect zoomBounds = new android.graphics.Rect();
        boolean frameVisible = zoomFrame.getGlobalVisibleRect(frameBounds);
        boolean zoomVisible = zoomPanel != null && zoomPanel.getGlobalVisibleRect(zoomBounds);
        check(frameVisible && zoomVisible && frameBounds.contains(zoomBounds),
            "vertical zoom slider remains fully visible inside the race frame");
        SeekBar zoomSlider = (SeekBar) field(page, "zoomControl");
        ui(() -> { zoomSlider.setProgress(0); zoomSlider.setProgress(80); });
        check(Math.abs((Float) field(canvas, "zoom") - 1f) < .001f,
            "zoom slider midpoint matches the initial race zoom");
        ui(() -> zoomButton.performClick());
        float elapsedBeforeResume = elapsed(world);
        TextView resumeButton = pauseButton;
        ui(() -> resumeButton.performClick());

        await(() -> {
            try { return elapsed(world) > elapsedBeforeResume + .08f; }
            catch (Exception ignored) { return false; }
        }, "fixed-step race advances after the shared countdown");
        pauseButton = findText(game.getWindow().getDecorView(), english ? "Pause" : "暂停");
        TextView pauseBeforeFollow = pauseButton;
        ui(() -> pauseBeforeFollow.performClick());
        ui(() -> followButton.performClick());
        TextView followLast = findText(game.getWindow().getDecorView(), english ? "Follow last" : "跟随最后一名");
        check(followLast != null, "follow settings opens as a visible list above the race");
        ui(() -> followLast.performClick());
        await(() -> {
            try { return (Integer) field(canvas, "selectedCamera") == 1; }
            catch (Exception ignored) { return false; }
        }, "follow dialog applies the selected mode");
        check((Integer) field(canvas, "selectedCamera") == 1,
            "follow settings can switch away from the default leader mode");

        TextView followByName = findText(game.getWindow().getDecorView(), english ? "Follow by name" : "按名称跟随");
        ui(() -> followByName.performClick());
        EditText followName = (EditText) field(page, "followNameInput");
        TextView searchFollow = findText(game.getWindow().getDecorView(), english ? "Search and follow" : "搜索并跟随");
        ui(() -> { followName.setText("no such racer"); searchFollow.performClick(); });
        check((Integer) field(canvas, "selectedCamera") == 1
                && findText(game.getWindow().getDecorView(), english ? "No ball has that name." : "找不到该名称的小球") != null
                && !followName.hasFocus(),
            "name-follow search keeps the old mode, hides the cursor, and shows an inline miss");
        String targetName = (String) field(balls.get(0), "name");
        ui(() -> { followName.setText(targetName); searchFollow.performClick(); });
        check((Integer) field(canvas, "selectedCamera") == 3
                && targetName.equals(field(canvas, "selectedName"))
                && !followName.hasFocus(),
            "name-follow search selects the matching ball and hides the cursor");

        ui(() -> {
            try { call(canvas, "setCameraMode", new Class<?>[]{int.class, String.class}, 0, ""); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        Thread.sleep(100L);
        setField(canvas, "cameraX", 220f);
        setField(canvas, "cameraY", 220f);
        float cameraBeforeDrag = (Float) field(canvas, "cameraX");
        long time = SystemClock.uptimeMillis();
        float x = canvas.getWidth() * .5f;
        float y = canvas.getHeight() * .5f;
        ui(() -> {
            canvas.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0));
            canvas.dispatchTouchEvent(MotionEvent.obtain(time, time + 45, MotionEvent.ACTION_MOVE, x + 45f, y + 25f, 0));
            canvas.dispatchTouchEvent(MotionEvent.obtain(time, time + 90, MotionEvent.ACTION_UP, x + 45f, y + 25f, 0));
        });
        waitForIdleSync();
        check(Math.abs((Float) field(canvas, "cameraX") - cameraBeforeDrag) > 1f,
            "manual follow mode pans the camera");

        ui(() -> {
            try {
                call(canvas, "setZoom", new Class<?>[]{float.class}, .5f);
                setField(canvas, "cameraInitialized", false);
                call(canvas, "updateCamera", new Class<?>[]{});
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        check(Math.abs((Float) field(canvas, "cameraX") - (Float) field(course, "width") * .5f) < .1f,
            "very small zoom centers a track narrower than the screen");

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
        TextView finalRow = (TextView) ((ViewGroup) rankingRows.getChildAt(0)).getChildAt(0);
        check(finalRow.getText().toString().contains(english ? "Level" : "第 "),
            "unfinished racers show their last level in the final ranking");
        check(((ViewGroup) rankingRows.getChildAt(0)).getChildCount() == 2,
            "final result rows reserve a right-side ball marker");
        check(((View) field(page, "rankingHeader")).getVisibility() == View.VISIBLE
                && ((TextView) field(page, "rankingHeader")).getTextSize()
                    >= 30f * game.getResources().getDisplayMetrics().scaledDensity
                && ((TextView) field(page, "rankingHeader")).getGravity() == android.view.Gravity.CENTER
                && ((View) field(page, "rankingNote")).getVisibility() == View.VISIBLE
                && ((View) field(page, "followPanel")) == null
                && ((View) field(page, "zoomContainer")) == null
                && ((View) field(page, "raceFinishHome")).getVisibility() == View.VISIBLE,
            "final ranking has a large centered title, explanatory note, cleared controls and home action");
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
        TextView defaults = findText(game.getWindow().getDecorView(), args.getString("language").equals("en")
            ? "Default settings" : "默认设置");
        check(defaults != null, "settings page separates game, system and default settings");
        ui(() -> defaults.performClick());
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
                    restorePreferences("ballrace_module", previousPreferences);
                }
                restorePreferences("toolbox", previousToolbox);
                restorePreferences("apps_host", previousAppHost);
                if (host != null) ui(() -> host.finish());
            } catch (Throwable failure) {
                result.putString("result", "FAIL: fixture restore " + failure + "\n" + result.getString("result"));
            }
            finish(Activity.RESULT_OK, result);
        }
    }
}
