package org.daidai.modules.ballrace;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PathEffect;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

import org.daidai.modules.shared.Texts;
import org.daidai.modules.shared.Ui;
import org.daidai.toolbox.ModulePage;
import org.daidai.toolbox.SettingsActivity;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.IdentityHashMap;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** 竖屏二维小球竞赛首版页面。 */
public final class BallRaceActivity extends ModulePage {
    private static final int MIN_BALLS = 2;
    private static final float BALL_RADIUS = 10f;
    private static final float BALL_MASS = 1f;
    private static final float EARTH_GRAVITY = 980f;
    private static final float CONTACT_FRICTION = .42f;
    private static final int CAMERA_MANUAL = 0;
    private static final int CAMERA_LAST = 1;
    private static final int CAMERA_FIRST = 2;
    private static final int CAMERA_NAME = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private ArrayList<String> participantIds = new ArrayList<>(BallRoster.defaultIds());
    private ArrayList<String> candidateLevelIds = new ArrayList<>(EditableLevel.builtInIds());
    private int matchLevelCount = 7;
    private float obstacleElasticity;
    private float ballElasticity = .68f;
    private float gravityG = .75f;
    private int physicsHz = 120;
    private int trailQuality = 2;
    private boolean ballCollisionEnabled = true;
    private int defaultFollowMode = CAMERA_FIRST;
    private int cameraMode = CAMERA_FIRST;
    private int cameraModeBeforeName = CAMERA_FIRST;
    private String cameraFollowName = "";
    private LinearLayout setupContent;
    private CheckBox defaultBallCollision;
    private TextView defaultObstacleElasticityLabel;
    private SeekBar defaultObstacleElasticitySeek;
    private TextView defaultBallElasticityLabel;
    private SeekBar defaultBallElasticitySeek;
    private TextView defaultGravityLabel;
    private SeekBar defaultGravitySeek;
    private Spinner defaultFollowSpinner;
    private ArrayList<CheckBox> levelPoolCheckBoxes = new ArrayList<>();
    private Spinner followModeSpinner;
    private EditText followNameInput;
    private AlertDialog followDialog;
    private TextView matchLevelCountLabel;
    private SeekBar matchLevelCountSeek;
    private TextView matchObstacleElasticityLabel;
    private SeekBar matchObstacleElasticitySeek;
    private CheckBox matchTrailsEnabled;
    private TextView matchBallElasticityLabel;
    private SeekBar matchBallElasticitySeek;
    private TextView matchGravityLabel;
    private SeekBar matchGravitySeek;
    private CheckBox matchBallCollision;
    private RaceCanvas raceCanvas;
    private TextView raceStatus;
    private LinearLayout rankingList;
    private ScrollView rankingScroll;
    private LinearLayout rankingPanel;
    private View rankingResizeHandle;
    private TextView rankingHeader;
    private Button rankingExpandButton;
    private boolean rankingCollapsed;
    private FrameLayout raceFrame;
    private LinearLayout followPanel;
    private LinearLayout rankingTabs;
    private Button activeRankingButton;
    private Button allRankingButton;
    private boolean showAllRankings;
    private boolean rankingFinalized;
    private LinearLayout raceActions;
    private Button raceFinishHome;
    private SeekBar zoomControl;
    private FrameLayout zoomContainer;
    private TextView rankingNote;
    private String editorOriginalJson;
    private Spinner editorLevelSpinner;
    private ArrayAdapter<String> editorLevelAdapter;
    private EditText editorLevelNameInput;
    private TextView editorSizeLabel;
    private TextView editorThicknessLabel;
    private TextView editorRotationLabel;
    private SeekBar editorSizeSeek;
    private SeekBar editorThicknessSeek;
    private SeekBar editorRotationSeek;
    private View editorThicknessRow;
    private View editorRotationRow;
    private EditorCanvas editorCanvas;
    private EditableLevel editorLevel;
    private ArrayList<String> exportLevelIds = new ArrayList<>();
    private ArrayList<String> exportBallIds = new ArrayList<>();
    private boolean selectingBallsToExport, selectingLevelsToExport;
    private final HashSet<String> selectedBallExports = new HashSet<>();
    private final HashSet<String> selectedLevelExports = new HashSet<>();
    private EditableLevel.Obstacle selectedObstacle;
    private int selectedEditorPort;
    private boolean updatingEditorControls;
    private boolean updatingEditorLevelNames;
    private EditText editorXInput, editorYInput, editorElasticityInput, editorDimensionInput, editorThicknessInput, editorAngleInput;
    private EditText editorArcAngleInput, editorTextInput;
    private EditText editorRadiusYInput, editorColorInput, editorFrictionInput;
    private EditText editorMotionXInput, editorMotionYInput, editorMotionPeriodInput, editorMotionPhaseInput;
    private EditText editorPivotXInput, editorPivotYInput, editorRotationSpeedInput;
    private EditText editorImpulseXInput, editorImpulseYInput;
    private CheckBox editorRoundedInput, editorVisibleInput, editorCollisionEnabledInput;
    private EditText editorTeleportXInput, editorTeleportYInput, editorActionCooldownInput;
    private Spinner editorActionInput;
    private TextView editorActionTitle;
    private LinearLayout editorAddObjectPanel;
    private final Map<EditText, TextView> editorPropertyLabels = new IdentityHashMap<>();
    private LinearLayout editorPageContent;
    private LinearLayout editorPropertyCard;
    private LinearLayout levelPropertiesPanel;
    private Button levelPropertiesToggle;
    private static final int IMPORT_LEVEL_REQUEST = 741;
    private static final int EXPORT_LEVEL_REQUEST = 742;
    private static final int IMPORT_BALL_IMAGE_REQUEST = 743;
    private static final int IMPORT_BALLS_REQUEST = 744;
    private static final int EXPORT_BALLS_REQUEST = 745;
    private String editingBallId;
    private String originalBallImagePath;
    private boolean returningFromBallCrop;
    private EditText ballOutlineInput;
    private EditText ballTrailInput;
    private EditText ballNameColorInput;
    private EditText ballNameOutlineInput;
    private BallCropView activeCropView;
    private Bitmap pendingBallBitmap;
    private long activeSeed;
    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            if (raceCanvas == null || isDestroyed() || isFinishing()) return;
            updateRanking();
            main.postDelayed(this, 350);
        }
    };

    public BallRaceActivity(Activity activity) {
        super(activity);
        preferences = activity.getSharedPreferences("ballrace_module", Activity.MODE_PRIVATE);
        for (String id : preferences.getString("custom_ball_ids", "").split(",")) {
            if (id.isEmpty()) continue;
            BallRoster.registerCustom(id, preferences.getString(ballKey(id, "name"), id),
                preferences.getInt(ballKey(id, "body"), 0xFF14B8A6));
        }
        migrateBuiltInNameOutlines();
    }

    private String t(String key, Object... args) {
        return Texts.get(activity, "ballrace_" + key, args);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("settings_mode", false)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            showSettings();
            return;
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        showMainMenu();
    }

    private void loadGameConfiguration() {
        participantIds = new ArrayList<>(BallRoster.parse(
            preferences.getString("race_participant_ids", null), BallRoster.defaultIds()));
        if (participantIds.size() < MIN_BALLS) participantIds = new ArrayList<>(BallRoster.defaultIds());
        candidateLevelIds = readLevelIds("race_candidate_level_ids");
        obstacleElasticity = clamp01(preferences.getFloat("default_obstacle_elasticity", 1f));
        ballElasticity = clamp01(preferences.getFloat("default_ball_elasticity", .68f));
        gravityG = clamp(preferences.getFloat("default_gravity_g", .75f), 0f, 2f);
        physicsHz = preferences.getInt("race_physics_hz", 120);
        if (physicsHz != 30 && physicsHz != 45 && physicsHz != 60 && physicsHz != 90 && physicsHz != 120) physicsHz = 120;
        trailQuality = clamp(preferences.getInt("race_trail_quality", 2), 0, 2);
        ballCollisionEnabled = preferences.getBoolean("default_ball_collision", true);
        matchLevelCount = Math.max(1, preferences.getInt("race_level_count", 7));
        defaultFollowMode = clamp(preferences.getInt("default_follow_mode", CAMERA_FIRST), CAMERA_MANUAL, CAMERA_NAME);
        cameraMode = defaultFollowMode;
        cameraFollowName = "";
    }

    private ArrayList<String> readLevelIds(String key) {
        String saved = preferences.getString(key, null);
        if (saved == null || saved.trim().isEmpty()) return new ArrayList<>(EditableLevel.builtInIds());
        ArrayList<String> result = new ArrayList<>();
        for (String id : saved.split(",", -1)) {
            if (EditableLevel.isValidLevelId(id) && !result.contains(id)) result.add(id);
        }
        return result.isEmpty() ? new ArrayList<>(EditableLevel.builtInIds()) : result;
    }

    private void showMainMenu() {
        main.removeCallbacks(statusTicker);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (raceCanvas != null) raceCanvas.release();
        raceCanvas = null;
        editorCanvas = null;
        LinearLayout page = Ui.page(activity, t("title"), "");
        TextView heading = (TextView) page.getChildAt(0);
        heading.setGravity(Gravity.CENTER);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout content = Ui.column(activity);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        View topSpace = new View(activity);
        content.addView(topSpace, new LinearLayout.LayoutParams(-1, 0, 1));
        Button start = Ui.button(content, t("menu_start_game"), () -> {
            loadGameConfiguration();
            showSetup();
        });
        Button editor = Ui.button(content, t("menu_editor"), this::showEditorMenu);
        Button defaults = Ui.button(content, t("menu_settings"), () -> showSettingsHub(false));
        for (Button button : Arrays.asList(start, editor, defaults)) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
            button.setMinHeight(Ui.dp(activity, 76));
            button.setMinimumHeight(Ui.dp(activity, 76));
            button.getLayoutParams().height = Ui.dp(activity, 78);
        }
        View bottomSpace = new View(activity);
        content.addView(bottomSpace, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showEditorMenu() {
        LinearLayout page = Ui.page(activity, t("editor_title"), "");
        LinearLayout content = Ui.scroll(page);
        Button balls = Ui.button(content, t("edit_balls"), this::showBallList);
        Button levels = Ui.button(content, t("edit_levels"), () -> {
            loadGameConfiguration();
            showLevelList();
        });
        Button back = Ui.button(content, t("menu_back"), this::showMainMenu);
        for (Button button : new Button[]{balls, levels, back}) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) button.getLayoutParams();
            params.height = Ui.dp(activity, 76);
            params.setMargins(Ui.dp(activity, 18), Ui.dp(activity, 10), Ui.dp(activity, 18), Ui.dp(activity, 10));
            button.setLayoutParams(params);
        }
    }

    private void showBallList() {
        LinearLayout page = Ui.page(activity, t("edit_balls"), t("ball_editor_note"));
        LinearLayout actions = Ui.row(page);
        Ui.cell(actions, t("ball_new"), this::showCreateBallDialog);
        Ui.cell(actions, t("ball_import"), this::importBalls);
        Ui.cell(actions, t(selectingBallsToExport ? "ball_export_selected" : "ball_export"), this::chooseBallsToExport);
        ScrollView scroll = new ScrollView(activity);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = Ui.column(activity);
        scroll.addView(content);
        LinearLayout row = null;
        List<BallRoster.ColorSpec> colors = BallRoster.colors();
        for (int index = 0; index < colors.size(); index++) {
            if (index % 2 == 0) row = Ui.row(content);
            BallRoster.ColorSpec color = colors.get(index);
            LinearLayout card = Ui.card(row, "");
            card.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            BallAppearanceView preview = new BallAppearanceView(color.id, Ui.dp(activity, 82));
            card.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 100)));
            BallNameView name = new BallNameView(color.id, 18f);
            name.setGravity(Gravity.CENTER);
            LinearLayout nameRow = Ui.row(card);
            if (selectingBallsToExport) {
                CheckBox selected = new CheckBox(activity); selected.setChecked(selectedBallExports.contains(color.id));
                selected.setOnCheckedChangeListener((button, checked) -> { if (checked) selectedBallExports.add(color.id); else selectedBallExports.remove(color.id); });
                nameRow.addView(selected, new LinearLayout.LayoutParams(Ui.dp(activity, 42), Ui.dp(activity, 40)));
            }
            nameRow.addView(name, new LinearLayout.LayoutParams(0, Ui.dp(activity, 44), 1));
            nameRow.setOnClickListener(v -> { if (selectingBallsToExport) { if (!selectedBallExports.remove(color.id)) selectedBallExports.add(color.id); showBallList(); } else showBallEditor(color.id); });
            card.setOnClickListener(v -> { if (selectingBallsToExport) { if (!selectedBallExports.remove(color.id)) selectedBallExports.add(color.id); showBallList(); } else showBallEditor(color.id); });
            if (index % 2 == 1) Ui.cell(row, "", () -> {}).setVisibility(View.GONE);
        }
        Ui.button(page, t("menu_back"), this::showEditorMenu);
    }

    private void showBallCrop() {
        LinearLayout page = Ui.page(activity, t("ball_crop_title"), t("ball_crop_note"));
        LinearLayout content = Ui.column(activity);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        activeCropView = new BallCropView(pendingBallBitmap);
        content.addView(activeCropView, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.button(page, t("ball_crop_save"), () -> {
            try {
                Bitmap cropped = activeCropView.crop();
                File dir = new File(activity.getFilesDir(), "ballrace-images");
                if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException();
                File target = new File(dir, editingBallId + ".png");
                try (FileOutputStream output = new FileOutputStream(target)) {
                    if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new java.io.IOException();
                }
                preferences.edit().putString(ballKey(editingBallId, "image"), target.getAbsolutePath()).apply();
                pendingBallBitmap = null;
                returningFromBallCrop = true;
                showBallEditor(editingBallId);
            } catch (Exception error) { Ui.toast(activity, t("ball_crop_failed"), 0).show(); }
        });
        Ui.button(page, t("menu_back"), this::showBallEditorFromCrop);
        page.addView(Ui.text(activity, t("ball_crop_circle_note"), 13));
    }

    private void importBalls() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent, IMPORT_BALLS_REQUEST);
        } catch (Exception error) { Ui.toast(activity, t("ball_import_failed"), 0).show(); }
    }

    private void chooseBallsToExport() {
        if (!selectingBallsToExport) { selectingBallsToExport = true; selectedBallExports.clear(); showBallList(); return; }
        exportBallIds.clear(); exportBallIds.addAll(selectedBallExports);
        if (exportBallIds.isEmpty()) {
            selectingBallsToExport = false;
            selectedBallExports.clear();
            showBallList();
            Ui.toast(activity, t("ball_export_none"), 0).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "ball-race-balls.json");
            activity.startActivityForResult(intent, EXPORT_BALLS_REQUEST);
        } catch (Exception error) { Ui.toast(activity, t("ball_export_failed"), 0).show(); }
        return;
        /*
        List<BallRoster.ColorSpec> colors = BallRoster.colors();
        String[] labels = new String[colors.size()]; boolean[] checked = new boolean[colors.size()];
        for (int i = 0; i < colors.size(); i++) labels[i] = ballName(colors.get(i).id);
        new AlertDialog.Builder(activity).setTitle(t("ball_export_select"))
            .setMultiChoiceItems(labels, checked, (d, which, value) -> checked[which] = value)
            .setNegativeButton(t("default_cancel"), null)
            .setPositiveButton(t("ball_export_selected"), (d, w) -> {
                exportBallIds.clear();
                for (int i = 0; i < colors.size(); i++) if (checked[i]) exportBallIds.add(colors.get(i).id);
                if (exportBallIds.isEmpty()) return;
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "ball-race-balls.json");
                    activity.startActivityForResult(intent, EXPORT_BALLS_REQUEST);
                } catch (Exception error) { Ui.toast(activity, t("ball_export_failed"), 0).show(); }
            }).show();
        */
    }

    private void showBallEditorFromCrop() {
        pendingBallBitmap = null;
        if (editingBallId != null) showBallEditor(editingBallId); else showBallList();
    }

    private final class BallCropView extends View {
        private final Bitmap source;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float centerX;
        private float centerY;
        private float cropSize;
        private float lastX;
        private float lastY;
        private float pinchDistance;
        BallCropView(Bitmap source) {
            super(activity); this.source = source; setBackgroundColor(0xFF202A38);
            if (source != null) {
                centerX = source.getWidth() * .5f;
                centerY = source.getHeight() * .5f;
                cropSize = Math.min(source.getWidth(), source.getHeight());
            }
        }
        @Override protected void onDraw(Canvas canvas) {
            if (source == null) return;
            float size = Math.min(getWidth(), getHeight()) * .78f;
            float left = (getWidth() - size) * .5f, top = (getHeight() - size) * .5f;
            float sx = centerX - cropSize * .5f, sy = centerY - cropSize * .5f;
            canvas.drawBitmap(source, new Rect((int) sx, (int) sy, (int) (sx + cropSize), (int) (sy + cropSize)),
                new RectF(left, top, left + size, top + size), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(activity, 3)); paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(getWidth() * .5f, getHeight() * .5f, size * .5f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (source == null) return true;
            if (event.getPointerCount() >= 2) {
                float dx = event.getX(1) - event.getX(0), dy = event.getY(1) - event.getY(0);
                float distance = (float) Math.hypot(dx, dy);
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) pinchDistance = distance;
                else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && pinchDistance > 0f) {
                    cropSize = clampCrop(cropSize * distance / pinchDistance);
                    pinchDistance = distance;
                    invalidate();
                }
                return true;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX(); lastY = event.getY(); pinchDistance = 0f; return true;
                case MotionEvent.ACTION_MOVE:
                    float displaySize = Math.min(getWidth(), getHeight()) * .78f;
                    centerX += (event.getX() - lastX) * cropSize / Math.max(displaySize, 1f);
                    centerY += (event.getY() - lastY) * cropSize / Math.max(displaySize, 1f);
                    clampCenter(); lastX = event.getX(); lastY = event.getY(); invalidate(); return true;
                case MotionEvent.ACTION_UP:
                    performClick(); return true;
                default: return true;
            }
        }
        private float clampCrop(float value) {
            float maximum = Math.min(source.getWidth(), source.getHeight());
            return Math.max(maximum * .15f, Math.min(maximum, value));
        }
        private void clampCenter() {
            float half = cropSize * .5f;
            centerX = Math.max(half, Math.min(source.getWidth() - half, centerX));
            centerY = Math.max(half, Math.min(source.getHeight() - half, centerY));
        }
        Bitmap crop() {
            int size = Math.max(1, Math.round(cropSize));
            int left = Math.round(centerX - size * .5f), top = Math.round(centerY - size * .5f);
            left = Math.max(0, Math.min(source.getWidth() - size, left));
            top = Math.max(0, Math.min(source.getHeight() - size, top));
            Bitmap square = Bitmap.createBitmap(source, left, top, size, size);
            Bitmap circle = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(circle);
            Path clip = new Path();
            clip.addCircle(size * .5f, size * .5f, size * .5f, Path.Direction.CW);
            canvas.clipPath(clip);
            canvas.drawBitmap(square, 0f, 0f, paint);
            square.recycle();
            return circle;
        }
    }

    private void showBallEditor(String id) {
        BallRoster.ColorSpec color = BallRoster.find(id);
        if (color == null) return;
        if (!returningFromBallCrop) originalBallImagePath = preferences.getString(ballKey(id, "image"), null);
        returningFromBallCrop = false;
        editingBallId = id;
        LinearLayout page = Ui.page(activity, t("edit_ball"), ballName(id));
        ScrollView scroll = new ScrollView(activity);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = Ui.column(activity);
        scroll.addView(content);
        BallAppearance appearance = ballAppearance(id);
        BallAppearanceView preview = new BallAppearanceView(id, Ui.dp(activity, 130), true);
        content.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 170)));
        Ui.button(content, t("ball_import_image"), () -> {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivityForResult(intent, IMPORT_BALL_IMAGE_REQUEST);
            } catch (Exception error) { Ui.toast(activity, t("ball_import_failed"), 0).show(); }
        });
        ballOutlineInput = hexInput(appearance.outlineColor);
        ballTrailInput = hexInput(appearance.trailColor);
        ballNameColorInput = hexInput(preferences.getInt(ballKey(id, "name_color"), appearance.bodyColor));
        int nameColorDefault = preferences.getInt(ballKey(id, "name_color"), appearance.bodyColor);
        ballNameOutlineInput = hexInput(preferences.getInt(ballKey(id, "name_outline"), complementaryNameColor(nameColorDefault)));
        preview.setNameColors(nameColorDefault, preferences.getInt(ballKey(id, "name_outline"), complementaryNameColor(nameColorDefault)));
        ballNameColorInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                try { preview.setNameColors(parseHexColor(value.toString()), parseHexColor(ballNameOutlineInput.getText().toString())); }
                catch (IllegalArgumentException ignored) {}
            }
            @Override public void afterTextChanged(android.text.Editable value) {}
        });
        ballNameOutlineInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                try { preview.setNameColors(parseHexColor(ballNameColorInput.getText().toString()), parseHexColor(value.toString())); }
                catch (IllegalArgumentException ignored) {}
            }
            @Override public void afterTextChanged(android.text.Editable value) {}
        });
        content.addView(labelledInput(t("ball_outline_color"), ballOutlineInput, appearance.outlineColor));
        content.addView(labelledInput(t("ball_trail_color"), ballTrailInput, appearance.trailColor));
        content.addView(labelledInput(t("ball_name_color"), ballNameColorInput, nameColorDefault));
        content.addView(labelledInput(t("ball_name_outline_color"), ballNameOutlineInput,
            preferences.getInt(ballKey(id, "name_outline"), complementaryNameColor(nameColorDefault))));
        Ui.button(content, t("ball_name_outline_complement"), () -> {
            try {
                int nameColor = parseHexColor(ballNameColorInput.getText().toString());
                setColorInput(ballNameOutlineInput, hexColor(complementaryNameColor(nameColor)));
            } catch (IllegalArgumentException invalid) {
                Ui.toast(activity, t("ball_invalid_color"), 0).show();
            }
        });
        LinearLayout actions = Ui.row(page);
        Ui.cell(actions, t("ball_save_return"), () -> {
            try {
                int outline = parseHexColor(ballOutlineInput.getText().toString());
                int trail = parseHexColor(ballTrailInput.getText().toString());
                int nameColor = parseHexColor(ballNameColorInput.getText().toString());
                int nameOutline = parseHexColor(ballNameOutlineInput.getText().toString());
                preferences.edit().putInt(ballKey(id, "outline"), outline)
                    .putInt(ballKey(id, "trail"), trail).putInt(ballKey(id, "name_color"), nameColor)
                    .putInt(ballKey(id, "name_outline"), nameOutline).apply();
                originalBallImagePath = preferences.getString(ballKey(id, "image"), null);
                editingBallId = null;
                showBallList();
            } catch (IllegalArgumentException invalid) {
                Ui.toast(activity, t("ball_invalid_color"), 0).show();
            }
        });
        Ui.cell(actions, t("ball_discard_return"), () -> new AlertDialog.Builder(activity)
            .setMessage(t("ball_discard_confirm"))
            .setNegativeButton(t("close"), null)
            .setPositiveButton(t("ball_discard_return"), (dialog, which) -> {
                SharedPreferences.Editor restore = preferences.edit();
                if (originalBallImagePath == null) restore.remove(ballKey(id, "image"));
                else restore.putString(ballKey(id, "image"), originalBallImagePath);
                restore.apply();
                originalBallImagePath = null;
                editingBallId = null;
                showBallList();
            }).show());
    }

    private void showCreateBallDialog() {
        LinearLayout fields = Ui.column(activity);
        EditText name = new EditText(activity); name.setSingleLine(true); name.setHint(t("ball_new_name"));
        EditText body = hexInput(0xFF14B8A6); body.setHint(t("ball_new_color"));
        fields.addView(name); fields.addView(body);
        new AlertDialog.Builder(activity).setTitle(t("ball_new")).setView(fields)
            .setNegativeButton(t("close"), null)
            .setPositiveButton(t("ball_save"), (dialog, which) -> {
                try {
                    String label = name.getText().toString().trim();
                    if (label.isEmpty() || label.length() > 32) throw new IllegalArgumentException();
                    int bodyColor = parseHexColor(body.getText().toString());
                    String id = "custom-" + UUID.randomUUID().toString().replace("-", "");
                    preferences.edit().putString(ballKey(id, "name"), label).putInt(ballKey(id, "body"), bodyColor)
                        .putInt(ballKey(id, "outline"), 0xFF111111).putInt(ballKey(id, "trail"), bodyColor)
                        .putInt(ballKey(id, "name_color"), bodyColor)
                        .putInt(ballKey(id, "name_outline"), complementaryNameColor(bodyColor))
                        .putString("custom_ball_ids", preferences.getString("custom_ball_ids", "") + id + ",").apply();
                    BallRoster.registerCustom(id, label, bodyColor); showBallEditor(id);
                } catch (IllegalArgumentException invalid) { Ui.toast(activity, t("ball_invalid_color"), 0).show(); }
            }).show();
    }

    private String ballName(String id) {
        BallRoster.ColorSpec spec = BallRoster.find(id);
        return spec == null ? id : (spec.resourceKey.startsWith("custom:") ? spec.resourceKey.substring(7) : t(spec.resourceKey));
    }

    private LinearLayout labelledInput(String label, EditText input) {
        return labelledInput(label, input, null);
    }

    private LinearLayout labelledInput(String label, EditText input, Integer color) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout field = Ui.column(activity);
        field.addView(Ui.text(activity, label, 14));
        field.addView(input);
        row.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
        if (color != null) {
            View sample = new View(activity);
            sample.setBackground(shape(color, 8));
            sample.setContentDescription(label);
            LinearLayout.LayoutParams sampleParams = new LinearLayout.LayoutParams(Ui.dp(activity, 42), Ui.dp(activity, 42));
            sampleParams.gravity = Gravity.BOTTOM;
            sampleParams.leftMargin = Ui.dp(activity, 10);
            row.addView(sample, sampleParams);
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    try { sample.setBackground(shape(parseHexColor(text.toString()), 8)); }
                    catch (IllegalArgumentException invalid) { }
                }
                @Override public void afterTextChanged(Editable editable) {}
            });
        }
        return row;
    }

    private void setColorInput(EditText input, String value) {
        boolean focused = input.hasFocus();
        input.setText(value);
        if (focused) input.setSelection(value.length());
    }

    private EditText hexInput(int color) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(hexColor(color));
        input.setHint("RRGGBB");
        input.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
        input.setBackground(shape(0xFFF4F6FA, 10));
        return input;
    }

    private String ballKey(String id, String field) { return "ball_" + id + "_" + field; }

    private String ballKeyForName(String name, String field) {
        for (BallRoster.ColorSpec spec : BallRoster.colors()) if (ballName(spec.id).equals(name)) return ballKey(spec.id, field);
        return "ball_unknown_" + field;
    }

    private BallAppearance ballAppearance(String id) {
        BallRoster.ColorSpec color = BallRoster.find(id);
        int body = color == null ? 0xFF808080 : preferences.getInt(ballKey(id, "body"), color.color);
        int outlineDefault = "black".equals(id) ? 0xFFD1D5DB : 0xFF111111;
        return new BallAppearance(body,
            preferences.getInt(ballKey(id, "outline"), outlineDefault),
            preferences.getInt(ballKey(id, "trail"), body),
            preferences.getString(ballKey(id, "image"), null));
    }

    private int parseHexColor(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
        long raw = Long.parseLong(text, 16);
        return (int) (0xFF000000L | raw);
    }

    private String hexColor(int color) { return String.format(java.util.Locale.US, "%06X", color & 0xFFFFFF); }

    private int complementaryNameColor(int color) {
        return BallRoster.complementaryContrastColor(color);
    }

    private void migrateBuiltInNameOutlines() {
        if (preferences.getInt("name_outline_schema", 0) >= 3) return;
        SharedPreferences.Editor edit = preferences.edit();
        for (BallRoster.ColorSpec spec : BallRoster.colors()) {
            if (spec.id.startsWith("custom-")) continue;
            int body = preferences.getInt(ballKey(spec.id, "name_color"), spec.color);
            String key = ballKey(spec.id, "name_outline");
            int stored = preferences.getInt(key, Integer.MIN_VALUE);
            if (stored == Integer.MIN_VALUE || stored == legacyNameOutline(body)
                    || stored == 0xFF111111 || stored == 0xFF000000
                    || isOldDarkChromaticOutline(body, stored)) {
                edit.putInt(key, complementaryNameColor(body));
            }
        }
        edit.putInt("name_outline_schema", 3);
        edit.apply();
    }

    private boolean isOldDarkChromaticOutline(int body, int outline) {
        float[] hsv = new float[3]; Color.colorToHSV(body, hsv);
        if (hsv[1] < .12f) return false;
        int red = (outline >>> 16) & 255, green = (outline >>> 8) & 255, blue = outline & 255;
        return red < 70 && green < 70 && blue < 70;
    }

    private int legacyNameOutline(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < .12f) {
            int value = Math.round((1f - hsv[2]) * 255f);
            return Color.rgb(value, value, value);
        }
        hsv[0] = (hsv[0] + 180f) % 360f;
        return Color.HSVToColor(new float[]{hsv[0], hsv[1], hsv[2]});
    }

    private final class BallAppearance {
        final int bodyColor;
        final int outlineColor;
        final int trailColor;
        final String imagePath;
        BallAppearance(int body, int outline, int trail, String image) {
            bodyColor = body; outlineColor = outline; trailColor = trail; imagePath = image;
        }
    }

    private final class BallAppearanceView extends View {
        private final String id;
        private final float diameter;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean showName;
        private int previewNameColor;
        private int previewNameOutline;
        private Bitmap bitmap;
        BallAppearanceView(String id, float diameter) {
            this(id, diameter, false);
        }
        BallAppearanceView(String id, float diameter, boolean showName) {
            super(activity); this.id = id; this.diameter = diameter;
            this.showName = showName;
            String path = ballAppearance(id).imagePath;
            if (path != null) bitmap = BitmapFactory.decodeFile(path);
            setContentDescription(ballName(id));
        }
        void setNameColors(int nameColor, int nameOutline) {
            previewNameColor = nameColor;
            previewNameOutline = nameOutline;
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            BallAppearance appearance = ballAppearance(id);
            float radius = Math.min(getWidth(), getHeight()) * .32f;
            float cx = getWidth() * .5f, cy = getHeight() * .5f;
            canvas.save();
            Path clip = new Path(); clip.addCircle(cx, cy, radius, Path.Direction.CW); canvas.clipPath(clip);
            paint.setStyle(Paint.Style.FILL); paint.setColor(appearance.bodyColor);
            canvas.drawCircle(cx, cy, radius, paint);
            if (bitmap != null) {
                RectF dst = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
                canvas.drawBitmap(bitmap, null, dst, paint);
            }
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.dp(activity, 3));
            paint.setColor(appearance.outlineColor); canvas.drawCircle(cx, cy, radius, paint);
            if (showName) {
                int nameColor = previewNameColor != 0 ? previewNameColor
                    : preferences.getInt(ballKey(id, "name_color"), appearance.bodyColor);
                int nameOutline = previewNameOutline != 0 ? previewNameOutline
                    : preferences.getInt(ballKey(id, "name_outline"), complementaryNameColor(nameColor));
                float baseline = cy - radius - 4f;
                namePaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f,
                    getResources().getDisplayMetrics()));
                namePaint.setTextAlign(Paint.Align.CENTER);
                namePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                namePaint.setColor(nameOutline);
                namePaint.setStyle(Paint.Style.STROKE);
                namePaint.setStrokeWidth(3f);
                canvas.drawText(ballName(id), cx, baseline, namePaint);
                namePaint.setColor(nameColor);
                namePaint.setStyle(Paint.Style.FILL);
                canvas.drawText(ballName(id), cx, baseline, namePaint);
            }
        }
    }

    /** 绘制与比赛画面一致的彩色球名，确保列表预览也能看清描边。 */
    private final class BallNameView extends TextView {
        private final String id;
        private final Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BallNameView(String id, float textSizeSp) {
            super(activity);
            this.id = id;
            setText(ballName(id));
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
            setTextColor(Color.TRANSPARENT);
            setGravity(Gravity.CENTER);
            setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            setPadding(Ui.dp(activity, 4), Ui.dp(activity, 2), Ui.dp(activity, 4), Ui.dp(activity, 2));
        }
        @Override protected void onDraw(Canvas canvas) {
            BallAppearance appearance = ballAppearance(id);
            int nameColor = preferences.getInt(ballKey(id, "name_color"), appearance.bodyColor);
            int nameOutline = preferences.getInt(ballKey(id, "name_outline"), complementaryNameColor(nameColor));
            namePaint.setTypeface(getTypeface());
            namePaint.setTextSize(getTextSize());
            namePaint.setTextAlign(Paint.Align.CENTER);
            float x = getWidth() * .5f;
            Paint.FontMetrics metrics = namePaint.getFontMetrics();
            float y = getHeight() * .5f - (metrics.ascent + metrics.descent) * .5f;
            namePaint.setStyle(Paint.Style.STROKE);
            namePaint.setStrokeWidth(Ui.dp(activity, 2));
            namePaint.setColor(nameOutline);
            canvas.drawText(getText().toString(), x, y, namePaint);
            namePaint.setStyle(Paint.Style.FILL);
            namePaint.setColor(nameColor);
            canvas.drawText(getText().toString(), x, y, namePaint);
        }
    }

    private void showSetup() {
        main.removeCallbacks(statusTicker);
        if (raceCanvas != null) raceCanvas.release();
        raceCanvas = null;
        LinearLayout page = Ui.page(activity, t("title"), t("race_setup"));
        setupContent = Ui.scroll(page);
        Button participants = Ui.button(setupContent, t("choose_participants"), this::showParticipants);
        Button levels = Ui.button(setupContent, t("choose_levels"), this::showLevelPool);
        Button settings = Ui.button(setupContent, t("match_settings"), this::showMatchSettings);
        for (Button button : new Button[]{participants, levels, settings}) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setMinHeight(Ui.dp(activity, 62));
            button.setMinimumHeight(Ui.dp(activity, 62));
            button.getLayoutParams().height = Ui.dp(activity, 64);
        }

        LinearLayout summary = Ui.card(setupContent, t("race_setup"));
        summary.addView(Ui.text(activity, t("participant_summary", participantIds.size()), 15));
        summary.addView(Ui.text(activity, t("course_pool_summary", candidateLevelIds.size(), matchLevelCount), 15));
        summary.addView(Ui.text(activity, t("setup_note"), 14));
        Button start = Ui.button(page, t("start_race"), this::startRace);
        Button back = Ui.button(page, t("menu_back"), this::showMainMenu);
        for (Button button : new Button[]{start, back}) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setMinHeight(Ui.dp(activity, 60));
            button.setMinimumHeight(Ui.dp(activity, 60));
            button.getLayoutParams().height = Ui.dp(activity, 62);
        }
    }

    private void showParticipants() {
        LinearLayout page = Ui.page(activity, t("choose_participants"),
            t("participant_summary", participantIds.size()));
        LinearLayout content = Ui.scroll(page);
        LinearLayout row = null;
        int cardIndex = 0;
        for (String id : new ArrayList<>(participantIds)) {
            BallRoster.ColorSpec color = BallRoster.find(id);
            if (color == null) continue;
            if (cardIndex++ % 2 == 0) row = Ui.row(content);
            LinearLayout card = Ui.card(row, "");
            card.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout itemRow = Ui.row(card);
            ImageView sample = new ImageView(activity);
            sample.setImageDrawable(colorDrawable(color.color));
            itemRow.addView(sample, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
            TextView name = Ui.text(activity, ballName(color.id), 18);
            name.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Ui.button(card, t("remove_ball"), () -> {
                if (!BallRoster.remove(participantIds, id)) {
                    Ui.toast(activity, t("minimum_racers"), 0).show();
                    return;
                }
                saveConfiguration();
                showParticipants();
            });
        }
        Ui.button(page, t("add_ball"), this::showAvailableParticipants);
        Ui.button(page, t("participant_done"), this::showSetup);
    }

    private void showAvailableParticipants() {
        LinearLayout page = Ui.page(activity, t("add_ball"), t("available_colors_note"));
        LinearLayout content = Ui.scroll(page);
        LinearLayout row = null;
        int cardIndex = 0;
        for (BallRoster.ColorSpec color : BallRoster.colors()) {
            if (participantIds.contains(color.id)) continue;
            if (cardIndex++ % 2 == 0) row = Ui.row(content);
            LinearLayout card = Ui.card(row, "");
            card.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout itemRow = Ui.row(card);
            ImageView sample = new ImageView(activity);
            sample.setImageDrawable(colorDrawable(color.color));
            itemRow.addView(sample, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
            TextView name = Ui.text(activity, ballName(color.id), 18);
            name.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Ui.button(card, t("add_ball"), () -> {
                if (!BallRoster.add(participantIds, color.id)) {
                    Ui.toast(activity, t("maximum_racers"), 0).show();
                    return;
                }
                saveConfiguration();
                showAvailableParticipants();
            });
        }
        Ui.button(page, t("menu_back"), this::showParticipants);
    }

    private void showLevelPool() {
        LinearLayout page = Ui.page(activity, t("choose_levels"), t("level_pool_note"));
        LinearLayout content = Ui.scroll(page);
        levelPoolCheckBoxes = new ArrayList<>();
        List<String> ids = editorLevelIds();
        ArrayList<String> labels = levelDisplayLabels(ids);
        LinearLayout row = null;
        for (String id : ids) {
            int index = ids.indexOf(id);
            if (index % 2 == 0) row = Ui.row(content);
            LinearLayout item = Ui.card(row, "");
            item.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            LevelPreview preview = new LevelPreview(loadEditableLevel(id));
            item.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 132)));
            CheckBox selector = new CheckBox(activity);
            selector.setText(labels.get(index));
            selector.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            selector.setTextColor(Ui.INK);
            selector.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
            selector.setChecked(candidateLevelIds.contains(id));
            item.addView(selector, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 52)));
            levelPoolCheckBoxes.add(selector);
        }
        Ui.button(page, t("save_level_pool"), () -> {
            ArrayList<String> next = new ArrayList<>();
            for (int index = 0; index < ids.size(); index++) if (levelPoolCheckBoxes.get(index).isChecked()) next.add(ids.get(index));
            if (next.isEmpty()) {
                Ui.toast(activity, t("select_one_level"), 0).show();
                return;
            }
            candidateLevelIds = next;
            saveConfiguration();
            showSetup();
        });
    }

    private void showMatchSettings() {
        LinearLayout page = Ui.page(activity, t("match_settings"), t("match_settings_note"));
        LinearLayout content = Ui.scroll(page);
        matchLevelCountLabel = Ui.text(activity, levelCountText(matchLevelCount), 15);
        content.addView(matchLevelCountLabel);
        EditText levelCountInput = new EditText(activity);
        levelCountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        levelCountInput.setSingleLine(true);
        levelCountInput.setText(Integer.toString(matchLevelCount));
        levelCountInput.setHint(t("level_count_value", 1));
        content.addView(levelCountInput);
        TextView repeatNote = Ui.text(activity, repeatRuleText(matchLevelCount), 13);
        content.addView(repeatNote);
        matchTrailsEnabled = new CheckBox(activity);
        matchTrailsEnabled.setText(t("trails_enabled"));
        matchTrailsEnabled.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        matchTrailsEnabled.setTextColor(Ui.INK);
        matchTrailsEnabled.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
        matchTrailsEnabled.setChecked(preferences.getBoolean("race_trails_enabled", true));
        content.addView(matchTrailsEnabled);
        levelCountInput.setOnFocusChangeListener((view, focused) -> {
            if (focused) return;
            try {
                matchLevelCount = Math.max(1, Integer.parseInt(levelCountInput.getText().toString()));
                levelCountInput.setText(Integer.toString(matchLevelCount));
                matchLevelCountLabel.setText(levelCountText(matchLevelCount));
                repeatNote.setText(repeatRuleText(matchLevelCount));
            } catch (NumberFormatException invalid) {
                matchLevelCount = 1; levelCountInput.setText("1"); matchLevelCountLabel.setText(levelCountText(1));
            }
        });

        matchBallCollision = new CheckBox(activity);
        matchBallCollision.setText(t("ball_collision"));
        matchBallCollision.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        matchBallCollision.setTextColor(Ui.INK);
        matchBallCollision.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
        matchBallCollision.setChecked(ballCollisionEnabled);
        content.addView(matchBallCollision);
        content.addView(Ui.text(activity, t("ball_collision_note"), 13));

        matchObstacleElasticityLabel = Ui.text(activity, elasticityText(obstacleElasticity), 15);
        content.addView(matchObstacleElasticityLabel);
        matchObstacleElasticitySeek = new SeekBar(activity);
        matchObstacleElasticitySeek.setMax(100);
        matchObstacleElasticitySeek.setProgress(Math.round(obstacleElasticity * 100f));
        content.addView(matchObstacleElasticitySeek);
        matchObstacleElasticitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                obstacleElasticity = value / 100f;
                matchObstacleElasticityLabel.setText(elasticityText(obstacleElasticity));
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });

        matchBallElasticityLabel = Ui.text(activity, t("ball_elasticity_value", ballElasticity), 15);
        content.addView(matchBallElasticityLabel);
        matchBallElasticitySeek = new SeekBar(activity);
        matchBallElasticitySeek.setMax(100);
        matchBallElasticitySeek.setProgress(Math.round(ballElasticity * 100f));
        content.addView(matchBallElasticitySeek);
        matchBallElasticitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                ballElasticity = value / 100f;
                matchBallElasticityLabel.setText(t("ball_elasticity_value", ballElasticity));
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });

        matchGravityLabel = Ui.text(activity, gravityText(gravityG), 15);
        content.addView(matchGravityLabel);
        matchGravitySeek = new SeekBar(activity);
        matchGravitySeek.setMax(200);
        matchGravitySeek.setProgress(Math.round(gravityG * 100f));
        content.addView(matchGravitySeek);
        matchGravitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                gravityG = value / 100f;
                matchGravityLabel.setText(gravityText(gravityG));
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });
        Ui.button(page, t("save_match_settings"), () -> {
            ballCollisionEnabled = matchBallCollision.isChecked();
            saveConfiguration();
            showSetup();
        });
    }

    private boolean saveConfiguration() {
        return preferences.edit()
            .putString("race_participant_ids", BallRoster.serialize(participantIds))
            .putString("race_candidate_level_ids", String.join(",", candidateLevelIds))
            .putInt("race_level_count", matchLevelCount)
            .putFloat("race_obstacle_elasticity", obstacleElasticity)
            .putFloat("race_ball_elasticity", ballElasticity)
            .putFloat("race_gravity_g", gravityG)
            .putBoolean("race_ball_collision", ballCollisionEnabled)
            .putBoolean("race_trails_enabled", matchTrailsEnabled == null
                ? preferences.getBoolean("race_trails_enabled", true) : matchTrailsEnabled.isChecked())
            .commit();
    }

    private void startRace() {
        if (participantIds.size() < 2) {
            Ui.toast(activity, t("minimum_racers"), 0).show();
            return;
        }
        saveConfiguration();
        activeSeed = System.currentTimeMillis();
        showRace(activeSeed);
    }

    private void showRace(long seed) {
        boolean allowRepeats = matchLevelCount > candidateLevelIds.size();
        showRace(seed, matchLevelCount, loadTemplateLibrary(), allowRepeats);
    }

    private List<RaceCourse.Template> loadTemplateLibrary() {
        List<RaceCourse.Template> templates = new ArrayList<>();
        for (String id : candidateLevelIds) templates.add(loadEditableLevel(id).toTemplate(obstacleElasticity));
        return templates;
    }

    private EditableLevel loadEditableLevel(String id) {
        String saved = preferences.getString("level_" + id + "_json", null);
        if (saved != null) {
            try {
                return EditableLevel.parse(saved, id);
            } catch (org.json.JSONException | IllegalArgumentException invalid) {
                Ui.toast(activity, t("editor_data_invalid"), 0).show();
            }
        }
        try (java.io.InputStream input = activity.getAssets().open("levels/" + id + ".json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return EditableLevel.parse(new String(output.toByteArray(), StandardCharsets.UTF_8), id);
        } catch (java.io.IOException | org.json.JSONException ignored) {
            // 缺少附件时回退到代码内置模板。
        }
        for (RaceCourse.Template template : RaceCourse.templates(obstacleElasticity)) {
            if (id.equals(template.id)) return EditableLevel.fromTemplate(template);
        }
        throw new IllegalArgumentException("关卡编号无效");
    }

    private String builtInLevelLabel(String id) {
        String saved = preferences.getString("level_" + id + "_json", null);
        if (saved != null) {
            try {
                String name = EditableLevel.parse(saved, id).name;
                if (!id.equals(name)) return name;
            } catch (org.json.JSONException | IllegalArgumentException ignored) {
                // 载入编辑器时会显示内置名称并在后续读取时回退。
            }
        }
        return defaultLevelLabel(id);
    }

    private String defaultLevelLabel(String id) {
        if ("custom-204016971e9f496bb4067f059d54b069".equals(id)) return "呆呆鸟的第一个关卡";
        if (!EditableLevel.isBuiltInId(id)) return id;
        if ("slopes".equals(id)) return t("level_slopes");
        if ("bumpers".equals(id)) return t("level_bumpers");
        if ("rails".equals(id)) return t("level_rails");
        if ("zigzag".equals(id)) return t("level_zigzag");
        return t("level_center_pegs");
    }

    /** 为重复显示名称添加序号，实际选择和存储仍使用稳定关卡编号。 */
    private ArrayList<String> levelDisplayLabels(List<String> ids) {
        ArrayList<String> labels = new ArrayList<>();
        java.util.HashMap<String, Integer> totals = new java.util.HashMap<>();
        java.util.HashMap<String, Integer> seen = new java.util.HashMap<>();
        for (String id : ids) {
            String label = builtInLevelLabel(id);
            totals.put(label, totals.containsKey(label) ? totals.get(label) + 1 : 1);
        }
        for (String id : ids) {
            String label = builtInLevelLabel(id);
            if (totals.get(label) > 1) {
                int number = seen.containsKey(label) ? seen.get(label) + 1 : 1;
                seen.put(label, number);
                label += "（" + number + "）";
            }
            labels.add(label);
        }
        return labels;
    }

    private String[] editorLevelLabels() {
        List<String> ids = editorLevelIds();
        List<String> labels = levelDisplayLabels(ids);
        return labels.toArray(new String[0]);
    }

    private List<String> editorLevelIds() {
        Set<String> hidden = new HashSet<>(Arrays.asList(
            preferences.getString("hidden_level_ids", "").split(",")));
        ArrayList<String> ids = new ArrayList<>();
        for (String id : EditableLevel.builtInIds()) if (!hidden.contains(id)) ids.add(id);
        for (String id : preferences.getString("custom_level_ids", "").split(",")) {
            if (EditableLevel.isValidLevelId(id) && !hidden.contains(id) && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }

    private void saveCustomLevelId(String id) {
        ArrayList<String> ids = new ArrayList<>();
        for (String value : preferences.getString("custom_level_ids", "").split(",")) {
            if (EditableLevel.isValidLevelId(value) && !ids.contains(value)) ids.add(value);
        }
        if (!ids.contains(id)) ids.add(id);
        preferences.edit().putString("custom_level_ids", String.join(",", ids)).apply();
    }

    private void createCustomLevel() {
        String id = "custom-" + UUID.randomUUID().toString().replace("-", "");
        editorLevel = EditableLevel.createCustom(id, t("editor_new_level"));
        saveCustomLevelId(id);
        saveEditorLevel();
        showLevelEditor(id);
    }

    private void showLevelList() {
        LinearLayout page = Ui.page(activity, t("edit_levels"), t("editor_note"));
        LinearLayout actions = Ui.row(page);
        Ui.cell(actions, t("editor_import"), this::importLevel);
        Ui.cell(actions, t(selectingLevelsToExport ? "editor_export_selected" : "editor_export"), this::chooseLevelsToExport);
        Ui.cell(actions, t(selectingLevelsToExport ? "editor_delete_selected" : "editor_delete_levels"), this::chooseLevelsToDelete);
        Ui.cell(actions, t("editor_new_level"), this::createCustomLevel);
        ScrollView scroll = new ScrollView(activity);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = Ui.column(activity);
        scroll.addView(content);
        List<String> ids = editorLevelIds();
        ArrayList<String> labels = levelDisplayLabels(ids);
        LinearLayout row = null;
        for (int index = 0; index < ids.size(); index++) {
            if (index % 2 == 0) row = Ui.row(content);
            String id = ids.get(index);
            EditableLevel level = loadEditableLevel(id);
            LinearLayout item = Ui.card(row, "");
            item.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            LevelPreview preview = new LevelPreview(level);
            item.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 112)));
            TextView name = Ui.text(activity, labels.get(index), 16);
            name.setGravity(Gravity.CENTER);
            LinearLayout nameRow = Ui.row(item);
            if (selectingLevelsToExport) {
                CheckBox selected = new CheckBox(activity); selected.setChecked(selectedLevelExports.contains(id));
                selected.setOnCheckedChangeListener((button, checked) -> { if (checked) selectedLevelExports.add(id); else selectedLevelExports.remove(id); });
                nameRow.addView(selected, new LinearLayout.LayoutParams(Ui.dp(activity, 42), Ui.dp(activity, 40)));
            }
            nameRow.addView(name, new LinearLayout.LayoutParams(0, Ui.dp(activity, 40), 1));
            CheckBox selector = selectingLevelsToExport && nameRow.getChildCount() > 0
                && nameRow.getChildAt(0) instanceof CheckBox ? (CheckBox) nameRow.getChildAt(0) : null;
            preview.setOnClickListener(v -> {
                if (selectingLevelsToExport && selector != null) selector.setChecked(!selector.isChecked());
                else if (!selectingLevelsToExport) showLevelEditor(id);
            });
            nameRow.setOnClickListener(v -> {
                if (selectingLevelsToExport && selector != null) selector.setChecked(!selector.isChecked());
                else if (!selectingLevelsToExport) showLevelEditor(id);
            });
            if (index % 2 == 1) Ui.cell(row, "", () -> {}).setVisibility(View.GONE);
        }
        Ui.button(page, t("menu_back"), this::showEditorMenu);
    }

    private void chooseLevelsToExport() {
        if (!selectingLevelsToExport) { selectingLevelsToExport = true; selectedLevelExports.clear(); showLevelList(); return; }
        exportLevelIds.clear(); exportLevelIds.addAll(selectedLevelExports);
        if (exportLevelIds.isEmpty()) {
            selectingLevelsToExport = false;
            selectedLevelExports.clear();
            showLevelList();
            return;
        }
        selectingLevelsToExport = false;
        selectedLevelExports.clear();
        String file = exportLevelIds.size() == 1 ? exportLevelIds.get(0) + ".json" : "ball-race-levels.zip";
        try {
            showLevelList();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(exportLevelIds.size() == 1 ? "application/json" : "application/zip")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, file);
            activity.startActivityForResult(intent, EXPORT_LEVEL_REQUEST);
        } catch (Exception error) { Ui.toast(activity, t("editor_export_failed"), 0).show(); }
        return;
        /*
        List<String> ids = editorLevelIds();
        String[] labels = new String[ids.size()];
        boolean[] checked = new boolean[ids.size()];
        List<String> displayLabels = levelDisplayLabels(ids);
        for (int index = 0; index < ids.size(); index++) labels[index] = displayLabels.get(index);
        new AlertDialog.Builder(activity).setTitle(t("editor_export"))
            .setMultiChoiceItems(labels, checked, (dialog, which, value) -> checked[which] = value)
            .setNegativeButton(t("default_cancel"), null)
            .setPositiveButton(t("editor_export"), (dialog, which) -> {
                exportLevelIds.clear();
                for (int index = 0; index < ids.size(); index++) if (checked[index]) exportLevelIds.add(ids.get(index));
                if (exportLevelIds.isEmpty()) return;
                String file = exportLevelIds.size() == 1 ? exportLevelIds.get(0) + ".json" : "ball-race-levels.zip";
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType(exportLevelIds.size() == 1 ? "application/json" : "application/zip")
                        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, file);
                    activity.startActivityForResult(intent, EXPORT_LEVEL_REQUEST);
                } catch (Exception error) { Ui.toast(activity, t("editor_export_failed"), 0).show(); }
            }).show();
        */
    }

    private void chooseLevelsToDelete() {
        if (!selectingLevelsToExport) {
            selectingLevelsToExport = true;
            selectedLevelExports.clear();
            showLevelList();
            return;
        }
        ArrayList<String> deletable = new ArrayList<>(selectedLevelExports);
        if (deletable.isEmpty()) {
            selectingLevelsToExport = false;
            selectedLevelExports.clear();
            showLevelList();
            return;
        }
        new AlertDialog.Builder(activity).setMessage(t("editor_delete_confirm"))
            .setNegativeButton(t("default_cancel"), null)
            .setPositiveButton(t("editor_delete_selected"), (dialog, which) -> {
                String current = preferences.getString("custom_level_ids", "");
                ArrayList<String> remaining = new ArrayList<>();
                for (String id : current.split(",")) if (!deletable.contains(id) && !id.isEmpty()) remaining.add(id);
                ArrayList<String> hidden = new ArrayList<>(Arrays.asList(
                    preferences.getString("hidden_level_ids", "").split(",")));
                for (String id : deletable) if (EditableLevel.isBuiltInId(id) && !hidden.contains(id)) hidden.add(id);
                SharedPreferences.Editor edit = preferences.edit()
                    .putString("custom_level_ids", String.join(",", remaining))
                    .putString("hidden_level_ids", String.join(",", hidden));
                for (String id : deletable) edit.remove("level_" + id + "_json");
                edit.apply();
                candidateLevelIds.removeAll(deletable);
                saveConfiguration();
                selectingLevelsToExport = false; selectedLevelExports.clear(); showLevelList();
            }).setOnCancelListener(dialog -> {
                selectingLevelsToExport = false;
                selectedLevelExports.clear();
                showLevelList();
            }).show();
    }

    private void showLevelEditor(String selectedLevelId) {
        main.removeCallbacks(statusTicker);
        if (raceCanvas != null) raceCanvas.release();
        raceCanvas = null;
        editorLevel = loadEditableLevel(selectedLevelId);
        if (selectedLevelId.equals(editorLevel.name)) editorLevel.rename(defaultLevelLabel(selectedLevelId));
        editorOriginalJson = editorLevel.toJson();
        selectedObstacle = null;

        LinearLayout page = Ui.page(activity, t("editor_title"), t("editor_note"));
        LinearLayout content = Ui.scroll(page);
        editorPageContent = content;
        levelPropertiesToggle = Ui.button(content, t("editor_level_properties_expand"), this::showLevelProperties);
        LinearLayout levelCard = Ui.card(content, t("editor_level"));
        editorLevelNameInput = new EditText(activity);
        editorLevelNameInput.setSingleLine(true);
        editorLevelNameInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        editorLevelNameInput.setTextColor(Ui.INK);
        editorLevelNameInput.setHint(t("editor_level_name"));
        editorLevelNameInput.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
        editorLevelNameInput.setBackground(shape(0xFFF4F6FA, 10));
        editorLevelNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        editorLevelNameInput.setText(editorDisplayName(editorLevel));
        levelCard.addView(editorLevelNameInput);
        Ui.button(levelCard, t("editor_save_name"), this::saveEditorLevelName);
        LinearLayout canvasCard = Ui.card(content, t("editor_canvas"));
        editorCanvas = new EditorCanvas(editorLevel);
        canvasCard.addView(editorCanvas, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 420)));

        editorAddObjectPanel = Ui.column(activity);
        LinearLayout firstAddRow = Ui.row(editorAddObjectPanel);
        Ui.cell(firstAddRow, t("editor_add_circle"), () -> addEditorObstacle(EditableLevel.CIRCLE));
        Ui.cell(firstAddRow, t("editor_add_slope"), () -> addEditorObstacle(EditableLevel.SLOPE));
        LinearLayout secondAddRow = Ui.row(editorAddObjectPanel);
        Ui.cell(secondAddRow, t("editor_arc"), () -> addEditorObstacle(EditableLevel.ARC));
        Ui.cell(secondAddRow, t("editor_text"), () -> addEditorObstacle(EditableLevel.TEXT));
        LinearLayout thirdAddRow = Ui.row(editorAddObjectPanel);
        Ui.cell(thirdAddRow, t("editor_add_ellipse"), () -> addEditorObstacle(EditableLevel.ELLIPSE));
        Ui.cell(thirdAddRow, "", () -> {}).setVisibility(View.GONE);
        content.addView(editorAddObjectPanel);
        Ui.button(content, t("editor_add_object"), () ->
            editorAddObjectPanel.setVisibility(editorAddObjectPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        Ui.button(content, t("editor_delete"), this::deleteSelectedObstacle);

        editorPropertyCard = Ui.card(content, t("editor_properties"));
        editorXInput = propertyInput(editorPropertyCard, t("editor_x"));
        editorYInput = propertyInput(editorPropertyCard, t("editor_y"));
        editorElasticityInput = propertyInput(editorPropertyCard, t("editor_object_elasticity"));
        editorDimensionInput = propertyInput(editorPropertyCard, t("editor_dimension"));
        editorThicknessInput = propertyInput(editorPropertyCard, t("editor_thickness"));
        editorAngleInput = propertyInput(editorPropertyCard, t("editor_rotation"));
        editorArcAngleInput = propertyInput(editorPropertyCard, t("editor_arc_angle"));
        editorTextInput = propertyInput(editorPropertyCard, t("editor_text_content"));
        editorRadiusYInput = propertyInput(editorPropertyCard, t("editor_radius_y"));
        editorColorInput = propertyInput(editorPropertyCard, t("editor_object_color"));
        editorColorInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        editorColorInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        editorFrictionInput = propertyInput(editorPropertyCard, t("editor_friction"));
        editorMotionXInput = propertyInput(editorPropertyCard, t("editor_motion_x"));
        editorMotionYInput = propertyInput(editorPropertyCard, t("editor_motion_y"));
        editorMotionPeriodInput = propertyInput(editorPropertyCard, t("editor_motion_period"));
        editorMotionPhaseInput = propertyInput(editorPropertyCard, t("editor_motion_phase"));
        editorPivotXInput = propertyInput(editorPropertyCard, t("editor_pivot_x"));
        editorPivotYInput = propertyInput(editorPropertyCard, t("editor_pivot_y"));
        editorRotationSpeedInput = propertyInput(editorPropertyCard, t("editor_rotation_speed"));
        editorImpulseXInput = propertyInput(editorPropertyCard, t("editor_impulse_x"));
        editorImpulseYInput = propertyInput(editorPropertyCard, t("editor_impulse_y"));
        editorMotionPhaseInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editorPivotXInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editorPivotYInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editorRotationSpeedInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editorImpulseXInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editorImpulseYInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        editorRoundedInput = new CheckBox(activity);
        editorRoundedInput.setText(t("editor_rounded"));
        editorRoundedInput.setTextColor(Ui.INK);
        editorPropertyCard.addView(editorRoundedInput);
        editorVisibleInput = new CheckBox(activity);
        editorVisibleInput.setText(t("editor_visible"));
        editorVisibleInput.setTextColor(Ui.INK);
        editorPropertyCard.addView(editorVisibleInput);
        editorCollisionEnabledInput = new CheckBox(activity);
        editorCollisionEnabledInput.setText(t("editor_collision_enabled"));
        editorCollisionEnabledInput.setTextColor(Ui.INK);
        editorPropertyCard.addView(editorCollisionEnabledInput);
        editorActionInput = new Spinner(activity);
        editorActionInput.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
            new String[]{t("editor_action_bounce"), t("editor_action_impulse"), t("editor_action_teleport")}));
        editorActionInput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!updatingEditorControls) updateEditorActionControls(true, position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        editorActionTitle = Ui.text(activity, t("editor_collision_action"), 14);
        editorPropertyCard.addView(editorActionTitle);
        editorPropertyCard.addView(editorActionInput);
        editorTeleportXInput = propertyInput(editorPropertyCard, t("editor_teleport_x"));
        editorTeleportYInput = propertyInput(editorPropertyCard, t("editor_teleport_y"));
        editorActionCooldownInput = propertyInput(editorPropertyCard, t("editor_action_cooldown"));
        Ui.button(editorPropertyCard, t("editor_apply_properties"), this::applyEditorProperties);
        editorSizeLabel = Ui.text(activity, "", 14);
        editorPropertyCard.addView(editorSizeLabel);
        editorSizeSeek = new SeekBar(activity);
        editorSizeSeek.setVisibility(View.GONE);
        editorPropertyCard.addView(editorSizeSeek);
        editorSizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                if (fromUser) updateSelectedSize(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });

        editorThicknessRow = Ui.column(activity);
        editorThicknessLabel = Ui.text(activity, "", 14);
        editorThicknessSeek = new SeekBar(activity);
        editorThicknessSeek.setVisibility(View.GONE);
        editorThicknessSeek.setMax(30);
        ((LinearLayout) editorThicknessRow).addView(editorThicknessLabel);
        ((LinearLayout) editorThicknessRow).addView(editorThicknessSeek);
        editorPropertyCard.addView(editorThicknessRow);
        editorThicknessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                if (fromUser) updateSelectedThickness(value + 6f);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });

        editorRotationRow = Ui.column(activity);
        editorRotationLabel = Ui.text(activity, "", 14);
        editorRotationSeek = new SeekBar(activity);
        editorRotationSeek.setVisibility(View.GONE);
        editorRotationSeek.setMax(359);
        ((LinearLayout) editorRotationRow).addView(editorRotationLabel);
        ((LinearLayout) editorRotationRow).addView(editorRotationSeek);
        editorPropertyCard.addView(editorRotationRow);
        editorRotationSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                if (fromUser) updateSelectedRotation(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });

        LinearLayout actions = Ui.row(page);
        Ui.cell(actions, t("editor_preview"), this::previewEditedLevel);
        Ui.cell(actions, t("editor_save"), this::saveEditorAndReturn);
        Ui.cell(actions, t("editor_discard"), this::discardEditorAndReturn);
        updateEditorControls();
    }

    private EditText propertyInput(LinearLayout parent, String label) {
        TextView title = Ui.text(activity, label, 14);
        parent.addView(title);
        EditText input = dimensionInput(0f, false); input.setHint(""); parent.addView(input);
        editorPropertyLabels.put(input, title);
        return input;
    }

    private void setPropertyVisible(EditText input, boolean visible) {
        input.setVisibility(visible ? View.VISIBLE : View.GONE);
        TextView label = editorPropertyLabels.get(input);
        if (label != null) label.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyEditorProperties() {
        if (selectedObstacle == null && selectedEditorPort == 0) return;
        try {
            float x = Float.parseFloat(editorXInput.getText().toString());
            float y = Float.parseFloat(editorYInput.getText().toString());
            if (selectedEditorPort != 0) editorLevel.movePort(selectedEditorPort == 1, x, y);
            else {
                float dimension = Float.parseFloat(editorDimensionInput.getText().toString());
                float thickness = Float.parseFloat(editorThicknessInput.getText().toString());
                float angle = Float.parseFloat(editorAngleInput.getText().toString());
                float radiusY = EditableLevel.ELLIPSE.equals(selectedObstacle.type)
                    ? Float.parseFloat(editorRadiusYInput.getText().toString()) : selectedObstacle.radiusY;
                float motionX = editorNumber(editorMotionXInput, -editorLevel.width, editorLevel.width);
                float motionY = editorNumber(editorMotionYInput, -editorLevel.height, editorLevel.height);
                float motionPeriod = editorNumber(editorMotionPeriodInput, .05f, 3600f);
                float motionPhase = editorNumber(editorMotionPhaseInput, -100000f, 100000f);
                float pivotX = editorNumber(editorPivotXInput, -editorLevel.width * 2f, editorLevel.width * 2f);
                float pivotY = editorNumber(editorPivotYInput, -editorLevel.height * 2f, editorLevel.height * 2f);
                float rotationSpeed = editorNumber(editorRotationSpeedInput, -720f, 720f);
                float impulseX = editorNumber(editorImpulseXInput, -5000f, 5000f);
                float impulseY = editorNumber(editorImpulseYInput, -5000f, 5000f);
                float teleportX = editorNumber(editorTeleportXInput, 0f, editorLevel.width);
                float teleportY = editorNumber(editorTeleportYInput, 0f, editorLevel.height);
                float actionCooldown = editorNumber(editorActionCooldownInput, .05f, 60f);
                float elasticity = editorNumber(editorElasticityInput, 0f, 1f);
                float arcDegrees = editorNumber(editorArcAngleInput, 1f, 359f);
                float friction = editorNumber(editorFrictionInput, 0f, 1f);
                int color = parseEditorColor(editorColorInput.getText().toString(), selectedObstacle.color);
                int action = editorActionInput.getSelectedItemPosition();
                String textValue = selectedObstacle.text;
                if (EditableLevel.TEXT.equals(selectedObstacle.type)) {
                    textValue = editorTextInput.getText().toString().trim();
                    if (textValue.isEmpty() || textValue.length() > 80) throw new IllegalArgumentException();
                }
                selectedObstacle.setClamped(x, y, dimension, thickness, angle, radiusY);
                selectedObstacle.elasticity = elasticity;
                selectedObstacle.arcDegrees = arcDegrees;
                selectedObstacle.rounded = editorRoundedInput.isChecked();
                selectedObstacle.visible = editorVisibleInput.isChecked();
                selectedObstacle.collisionEnabled = editorCollisionEnabledInput.isChecked();
                selectedObstacle.color = color;
                selectedObstacle.friction = friction;
                selectedObstacle.motionX = motionX;
                selectedObstacle.motionY = motionY;
                selectedObstacle.motionPeriodSeconds = motionPeriod;
                selectedObstacle.motionPhaseRadians = motionPhase;
                selectedObstacle.pivotOffsetX = pivotX;
                selectedObstacle.pivotOffsetY = pivotY;
                selectedObstacle.rotationSpeedDegreesPerSecond = rotationSpeed;
                selectedObstacle.collisionImpulseX = impulseX;
                selectedObstacle.collisionImpulseY = impulseY;
                selectedObstacle.collisionAction = action;
                selectedObstacle.teleportX = teleportX;
                selectedObstacle.teleportY = teleportY;
                selectedObstacle.actionCooldownSeconds = actionCooldown;
                selectedObstacle.text = textValue;
            }
            saveEditorLevel(); editorCanvas.invalidate(); updateEditorControls();
            editorCanvas.requestFocus();
            editorXInput.clearFocus(); editorYInput.clearFocus(); editorElasticityInput.clearFocus();
            editorDimensionInput.clearFocus(); editorThicknessInput.clearFocus(); editorAngleInput.clearFocus();
            editorArcAngleInput.clearFocus(); editorTextInput.clearFocus(); editorRadiusYInput.clearFocus();
            editorColorInput.clearFocus(); editorFrictionInput.clearFocus(); editorMotionXInput.clearFocus();
            editorMotionYInput.clearFocus(); editorMotionPeriodInput.clearFocus(); editorMotionPhaseInput.clearFocus();
            editorPivotXInput.clearFocus(); editorPivotYInput.clearFocus(); editorRotationSpeedInput.clearFocus();
            editorImpulseXInput.clearFocus(); editorImpulseYInput.clearFocus();
            editorTeleportXInput.clearFocus(); editorTeleportYInput.clearFocus(); editorActionCooldownInput.clearFocus();
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(editorCanvas.getWindowToken(), 0);
        } catch (RuntimeException invalid) { Ui.toast(activity, t("editor_adjust_failed"), 0).show(); }
    }

    private static float clampEditor(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float editorNumber(EditText input, float minimum, float maximum) {
        float value = Float.parseFloat(input.getText().toString());
        if (!Float.isFinite(value) || value < minimum || value > maximum) throw new IllegalArgumentException();
        return value;
    }

    private static int parseEditorColor(String text, int fallback) {
        try {
            String value = text == null ? "" : text.trim();
            if (value.length() != 6) throw new IllegalArgumentException("颜色必须使用六位十六进制值");
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("颜色必须使用六位十六进制值");
        }
    }

    private void updateEditorActionControls(boolean visible, int action) {
        boolean impulse = visible && action == RaceCourse.Obstacle.ACTION_IMPULSE;
        boolean teleport = visible && action == RaceCourse.Obstacle.ACTION_TELEPORT;
        setPropertyVisible(editorImpulseXInput, impulse);
        setPropertyVisible(editorImpulseYInput, impulse);
        setPropertyVisible(editorTeleportXInput, teleport);
        setPropertyVisible(editorTeleportYInput, teleport);
        setPropertyVisible(editorActionCooldownInput, visible);
        if (editorActionTitle != null) editorActionTitle.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void showLevelProperties() {
        if (levelPropertiesPanel != null) {
            boolean visible = levelPropertiesPanel.getVisibility() != View.VISIBLE;
            levelPropertiesPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
            levelPropertiesToggle.setText(t(visible ? "editor_level_properties_collapse" : "editor_level_properties_expand"));
            return;
        }
        levelPropertiesPanel = Ui.card(editorPageContent, "");
        Spinner entry = sideSpinner(editorLevel.entrySide);
        Spinner exit = sideSpinner(editorLevel.exitSide);
        EditText width = dimensionInput(editorLevel.width, true);
        EditText height = dimensionInput(editorLevel.height, false);
        levelPropertiesPanel.addView(Ui.text(activity, t("editor_entry_side"), 14));
        levelPropertiesPanel.addView(entry);
        levelPropertiesPanel.addView(Ui.text(activity, t("editor_exit_side"), 14));
        levelPropertiesPanel.addView(exit);
        levelPropertiesPanel.addView(Ui.text(activity, t("editor_width"), 14));
        levelPropertiesPanel.addView(width);
        levelPropertiesPanel.addView(Ui.text(activity, t("editor_height"), 14));
        levelPropertiesPanel.addView(height);
        Ui.button(levelPropertiesPanel, t("editor_save"), () -> {
            try {
                editorLevel.resize(Float.parseFloat(width.getText().toString()), Float.parseFloat(height.getText().toString()));
                editorLevel.setPorts(entry.getSelectedItemPosition(), editorLevel.entryOffset,
                    exit.getSelectedItemPosition(), editorLevel.exitOffset);
                saveEditorLevel(); editorCanvas.invalidate(); updateEditorControls();
            } catch (RuntimeException invalid) { Ui.toast(activity, t("editor_adjust_failed"), 0).show(); }
        });
        levelPropertiesPanel.setVisibility(View.VISIBLE);
        levelPropertiesToggle.setText(t("editor_level_properties_collapse"));
    }

    private Spinner sideSpinner(int selected) {
        Spinner spinner = new Spinner(activity);
        spinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
            new String[]{t("editor_side_top"), t("editor_side_right"), t("editor_side_bottom"), t("editor_side_left")}));
        spinner.setSelection(selected);
        return spinner;
    }

    private EditText dimensionInput(float value, boolean width) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);
        input.setText(Float.toString(value));
        input.setHint(t(width ? "editor_width" : "editor_height"));
        return input;
    }

    private final class LevelPreview extends View {
        private final EditableLevel level;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LevelPreview(EditableLevel level) { super(activity); this.level = level; setBackgroundColor(0xFFF4F6FA); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / level.width, getHeight() / level.height);
            float ox = (getWidth() - level.width * scale) * .5f;
            float oy = (getHeight() - level.height * scale) * .5f;
            canvas.save(); canvas.translate(ox, oy); canvas.scale(scale, scale);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f / scale); paint.setColor(0xFFCBD5E1);
            canvas.drawRect(0, 0, level.width, level.height, paint);
            for (EditableLevel.Obstacle obstacle : level.obstacles()) {
                paint.setColor(0xFF596C83); paint.setStyle(Paint.Style.FILL);
                if (!obstacle.visible) continue;
                paint.setColor(obstacle.color);
                if (EditableLevel.CIRCLE.equals(obstacle.type)) {
                    canvas.drawCircle(obstacle.x, obstacle.y, obstacle.size, paint);
                } else if (EditableLevel.ELLIPSE.equals(obstacle.type)) {
                    canvas.save();
                    canvas.rotate(obstacle.rotationDegrees, obstacle.x, obstacle.y);
                    canvas.drawOval(obstacle.x - obstacle.size, obstacle.y - obstacle.radiusY,
                        obstacle.x + obstacle.size, obstacle.y + obstacle.radiusY, paint);
                    canvas.restore();
                } else if (EditableLevel.ARC.equals(obstacle.type)) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(obstacle.thickness);
                    paint.setStrokeCap(obstacle.rounded ? Paint.Cap.ROUND : Paint.Cap.BUTT);
                    RectF bounds = new RectF(obstacle.x - obstacle.size, obstacle.y - obstacle.size,
                        obstacle.x + obstacle.size, obstacle.y + obstacle.size);
                    canvas.drawArc(bounds, obstacle.rotationDegrees - obstacle.arcDegrees * .5f,
                        obstacle.arcDegrees, false, paint);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                } else if (EditableLevel.TEXT.equals(obstacle.type)) {
                    paint.setTextSize(obstacle.size);
                    canvas.save();
                    canvas.rotate(obstacle.rotationDegrees, obstacle.x, obstacle.y);
                    canvas.drawText(obstacle.text, obstacle.x, obstacle.y, paint);
                    canvas.restore();
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(obstacle.thickness);
                    float radians = (float) Math.toRadians(obstacle.rotationDegrees);
                    float dx = (float) Math.cos(radians) * obstacle.size * .5f;
                    float dy = (float) Math.sin(radians) * obstacle.size * .5f;
                    canvas.drawLine(obstacle.x - dx, obstacle.y - dy,
                        obstacle.x + dx, obstacle.y + dy, paint);
                }
            }
            canvas.restore();
        }
    }

    private void addEditorObstacle(String type) {
        try {
            selectedObstacle = editorLevel.add(type, RaceCourse.WORLD_WIDTH * .5f, RaceCourse.LEVEL_HEIGHT * .5f);
            editorCanvas.invalidate();
            updateEditorControls();
            saveEditorLevel();
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            Ui.toast(activity, t("editor_add_failed"), 0).show();
        }
    }

    private void deleteSelectedObstacle() {
        if (selectedObstacle == null) return;
        new AlertDialog.Builder(activity).setMessage(t("editor_delete_object_confirm"))
            .setNegativeButton(t("default_cancel"), (dialog, which) -> {
                selectingLevelsToExport = false;
                selectedLevelExports.clear();
                showLevelList();
            })
            .setPositiveButton(t("editor_delete"), (dialog, which) -> {
                editorLevel.remove(selectedObstacle);
                selectedObstacle = null;
                editorCanvas.invalidate();
                updateEditorControls();
                saveEditorLevel();
            }).show();
    }

    private void updateSelectedSize(int progress) {
        if (selectedObstacle == null || updatingEditorControls) return;
        float minimum = EditableLevel.CIRCLE.equals(selectedObstacle.type) ? 10f : 40f;
        float size = minimum + progress;
        updateSelectedObstacle(size, selectedObstacle.thickness, selectedObstacle.rotationDegrees);
    }

    private void updateSelectedThickness(float thickness) {
        if (selectedObstacle == null || updatingEditorControls) return;
        updateSelectedObstacle(selectedObstacle.size, thickness, selectedObstacle.rotationDegrees);
    }

    private void updateSelectedRotation(float degrees) {
        if (selectedObstacle == null || updatingEditorControls) return;
        updateSelectedObstacle(selectedObstacle.size, selectedObstacle.thickness, degrees);
    }

    private void updateSelectedObstacle(float size, float thickness, float rotation) {
        try {
            selectedObstacle.setClamped(selectedObstacle.x, selectedObstacle.y, size, thickness, rotation);
            editorCanvas.invalidate();
            updateEditorControls();
            saveEditorLevel();
        } catch (IllegalArgumentException invalid) {
            Ui.toast(activity, t("editor_adjust_failed"), 0).show();
            updateEditorControls();
        }
    }

    private void selectEditorObstacle(EditableLevel.Obstacle obstacle) {
        selectedEditorPort = 0;
        selectedObstacle = obstacle;
        updateEditorControls();
        if (editorCanvas != null) editorCanvas.invalidate();
    }

    private void selectEditorPort(int port) {
        selectedObstacle = null;
        selectedEditorPort = port;
        updateEditorControls();
        if (editorCanvas != null) editorCanvas.invalidate();
    }

    private void updateEditorControls() {
        if (editorSizeSeek == null || editorLevel == null) return;
        updatingEditorControls = true;
        boolean selected = selectedObstacle != null || selectedEditorPort != 0;
        boolean slopeSelected = selectedObstacle != null && EditableLevel.SLOPE.equals(selectedObstacle.type);
        editorSizeSeek.setEnabled(selectedObstacle != null);
        editorThicknessSeek.setEnabled(slopeSelected);
        editorRotationSeek.setEnabled(slopeSelected);
        editorThicknessRow.setVisibility(slopeSelected ? View.VISIBLE : View.GONE);
        editorRotationRow.setVisibility(slopeSelected ? View.VISIBLE : View.GONE);
        editorSizeLabel.setVisibility(View.GONE);
        editorThicknessRow.setVisibility(View.GONE);
        editorRotationRow.setVisibility(View.GONE);
        editorPropertyCard.setVisibility(selected ? View.VISIBLE : View.GONE);
        if (!selected) {
            editorXInput.setText(""); editorYInput.setText("");
            editorElasticityInput.setText(""); editorDimensionInput.setText("");
            editorThicknessInput.setText(""); editorAngleInput.setText("");
            editorArcAngleInput.setText(""); editorTextInput.setText("");
            editorRadiusYInput.setText(""); editorColorInput.setText(""); editorFrictionInput.setText("");
            editorMotionXInput.setText(""); editorMotionYInput.setText(""); editorMotionPeriodInput.setText(""); editorMotionPhaseInput.setText("");
            editorPivotXInput.setText(""); editorPivotYInput.setText(""); editorRotationSpeedInput.setText("");
            editorImpulseXInput.setText(""); editorImpulseYInput.setText(""); editorTeleportXInput.setText(""); editorTeleportYInput.setText(""); editorActionCooldownInput.setText("");
            editorRoundedInput.setVisibility(View.GONE);
            editorVisibleInput.setVisibility(View.GONE); editorCollisionEnabledInput.setVisibility(View.GONE); editorActionInput.setVisibility(View.GONE);
            updateEditorActionControls(false, 0);
            editorSizeLabel.setText(t("editor_select_object"));
            editorThicknessLabel.setText(t("editor_thickness"));
            editorRotationLabel.setText(t("editor_rotation"));
        } else if (selectedEditorPort != 0) {
            boolean entry = selectedEditorPort == 1;
            editorXInput.setText(Float.toString(entry ? editorLevel.entryX() : editorLevel.exitX()));
            editorYInput.setText(Float.toString(entry ? editorLevel.entryY() : editorLevel.exitY()));
            setPropertyVisible(editorXInput, true); setPropertyVisible(editorYInput, true);
            setPropertyVisible(editorElasticityInput, false); setPropertyVisible(editorDimensionInput, false);
            setPropertyVisible(editorThicknessInput, false); setPropertyVisible(editorAngleInput, false);
            setPropertyVisible(editorArcAngleInput, false); setPropertyVisible(editorTextInput, false);
            setPropertyVisible(editorRadiusYInput, false); setPropertyVisible(editorColorInput, false); setPropertyVisible(editorFrictionInput, false);
            setPropertyVisible(editorMotionXInput, false); setPropertyVisible(editorMotionYInput, false); setPropertyVisible(editorMotionPeriodInput, false); setPropertyVisible(editorMotionPhaseInput, false);
            setPropertyVisible(editorPivotXInput, false); setPropertyVisible(editorPivotYInput, false); setPropertyVisible(editorRotationSpeedInput, false);
            setPropertyVisible(editorImpulseXInput, false); setPropertyVisible(editorImpulseYInput, false); setPropertyVisible(editorTeleportXInput, false); setPropertyVisible(editorTeleportYInput, false); setPropertyVisible(editorActionCooldownInput, false);
            editorRoundedInput.setVisibility(View.GONE);
            editorVisibleInput.setVisibility(View.GONE); editorCollisionEnabledInput.setVisibility(View.GONE); editorActionInput.setVisibility(View.GONE);
            updateEditorActionControls(false, 0);
        } else {
            boolean slope = EditableLevel.SLOPE.equals(selectedObstacle.type);
            boolean arc = EditableLevel.ARC.equals(selectedObstacle.type);
            boolean text = EditableLevel.TEXT.equals(selectedObstacle.type);
            boolean circle = EditableLevel.CIRCLE.equals(selectedObstacle.type);
            boolean ellipse = EditableLevel.ELLIPSE.equals(selectedObstacle.type);
            TextView dimensionLabel = editorPropertyLabels.get(editorDimensionInput);
            if (dimensionLabel != null) dimensionLabel.setText(t(circle ? "editor_radius" : ellipse ? "editor_radius_x" : text ? "editor_text_size" : arc ? "editor_arc_radius" : "editor_length"));
            TextView thicknessLabel = editorPropertyLabels.get(editorThicknessInput);
            if (thicknessLabel != null) thicknessLabel.setText(t(arc ? "editor_arc_thickness" : "editor_thickness"));
            TextView rotationLabel = editorPropertyLabels.get(editorAngleInput);
            if (rotationLabel != null) rotationLabel.setText(t(arc ? "editor_arc_rotation" : text ? "editor_text_rotation" : "editor_rotation"));
            setPropertyVisible(editorXInput, true); setPropertyVisible(editorYInput, true);
            setPropertyVisible(editorElasticityInput, !text); setPropertyVisible(editorDimensionInput, true);
            setPropertyVisible(editorThicknessInput, slope || arc);
            setPropertyVisible(editorAngleInput, slope || arc || text || ellipse);
            setPropertyVisible(editorArcAngleInput, arc);
            setPropertyVisible(editorTextInput, text);
            setPropertyVisible(editorRadiusYInput, ellipse);
            setPropertyVisible(editorColorInput, !text); setPropertyVisible(editorFrictionInput, !text);
            setPropertyVisible(editorMotionXInput, !text); setPropertyVisible(editorMotionYInput, !text); setPropertyVisible(editorMotionPeriodInput, !text); setPropertyVisible(editorMotionPhaseInput, !text);
            setPropertyVisible(editorPivotXInput, !text); setPropertyVisible(editorPivotYInput, !text); setPropertyVisible(editorRotationSpeedInput, !text);
            setPropertyVisible(editorImpulseXInput, false); setPropertyVisible(editorImpulseYInput, false); setPropertyVisible(editorTeleportXInput, false); setPropertyVisible(editorTeleportYInput, false); setPropertyVisible(editorActionCooldownInput, false);
            editorRoundedInput.setVisibility(slope || arc ? View.VISIBLE : View.GONE);
            editorVisibleInput.setVisibility(text ? View.GONE : View.VISIBLE); editorCollisionEnabledInput.setVisibility(text ? View.GONE : View.VISIBLE); editorActionInput.setVisibility(text ? View.GONE : View.VISIBLE);
            editorXInput.setText(Float.toString(selectedObstacle.x));
            editorYInput.setText(Float.toString(selectedObstacle.y));
            editorElasticityInput.setText(Float.toString(selectedObstacle.elasticity));
            editorDimensionInput.setText(Float.toString(selectedObstacle.size));
            editorThicknessInput.setText(Float.toString(selectedObstacle.thickness));
            editorAngleInput.setText(Float.toString(selectedObstacle.rotationDegrees));
            editorArcAngleInput.setText(Float.toString(selectedObstacle.arcDegrees));
            editorTextInput.setText(selectedObstacle.text);
            editorRadiusYInput.setText(Float.toString(selectedObstacle.radiusY));
            editorColorInput.setText(String.format(java.util.Locale.US, "%06X", selectedObstacle.color & 0xFFFFFF));
            editorFrictionInput.setText(Float.toString(selectedObstacle.friction));
            editorMotionXInput.setText(Float.toString(selectedObstacle.motionX)); editorMotionYInput.setText(Float.toString(selectedObstacle.motionY));
            editorMotionPeriodInput.setText(Float.toString(selectedObstacle.motionPeriodSeconds)); editorMotionPhaseInput.setText(Float.toString(selectedObstacle.motionPhaseRadians));
            editorPivotXInput.setText(Float.toString(selectedObstacle.pivotOffsetX)); editorPivotYInput.setText(Float.toString(selectedObstacle.pivotOffsetY));
            editorRotationSpeedInput.setText(Float.toString(selectedObstacle.rotationSpeedDegreesPerSecond));
            editorImpulseXInput.setText(Float.toString(selectedObstacle.collisionImpulseX)); editorImpulseYInput.setText(Float.toString(selectedObstacle.collisionImpulseY));
            editorTeleportXInput.setText(Float.toString(selectedObstacle.teleportX)); editorTeleportYInput.setText(Float.toString(selectedObstacle.teleportY)); editorActionCooldownInput.setText(Float.toString(selectedObstacle.actionCooldownSeconds));
            editorRoundedInput.setChecked(selectedObstacle.rounded);
            editorVisibleInput.setChecked(selectedObstacle.visible); editorCollisionEnabledInput.setChecked(selectedObstacle.collisionEnabled);
            editorActionInput.setSelection(Math.max(0, Math.min(2, selectedObstacle.collisionAction)));
            updateEditorActionControls(!text, selectedObstacle.collisionAction);
            float minimum = circle || ellipse ? 10f : 40f;
            float maximum = circle || ellipse ? 72f : 300f;
            editorSizeLabel.setText("");
            editorSizeSeek.setMax(Math.round(maximum - minimum));
            editorSizeSeek.setProgress(Math.round(selectedObstacle.size - minimum));
            if (!circle) {
                editorThicknessSeek.setProgress(Math.round(selectedObstacle.thickness - 6f));
                editorRotationSeek.setProgress(Math.round(selectedObstacle.rotationDegrees) % 360);
            }
        }
        updatingEditorControls = false;
    }

    private void saveEditorLevel() {
        if (editorLevel != null) preferences.edit()
            .putString("level_" + editorLevel.id + "_json", editorLevel.toJson()).apply();
    }

    private String editorDisplayName(EditableLevel level) {
        return level == null ? "" : level.name;
    }

    private void importLevel() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivityForResult(intent, IMPORT_LEVEL_REQUEST);
        } catch (Exception error) {
            Ui.toast(activity, t("editor_import_failed"), 0).show();
        }
    }

    private void exportLevel() {
        saveEditorLevelName();
        saveEditorLevel();
        String fileName = editorLevel.id;
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, fileName + ".json");
            activity.startActivityForResult(intent, EXPORT_LEVEL_REQUEST);
        } catch (Exception error) {
            Ui.toast(activity, t("editor_export_failed"), 0).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == IMPORT_BALLS_REQUEST) {
                String json;
                try (InputStream input = activity.getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) throw new java.io.IOException();
                    byte[] buffer = new byte[2048]; int count;
                    while ((count = input.read(buffer)) >= 0) { if (output.size() + count > 256 * 1024) throw new java.io.IOException(); output.write(buffer, 0, count); }
                    json = new String(output.toByteArray(), StandardCharsets.UTF_8);
                }
                org.json.JSONArray rows = new org.json.JSONObject(json).getJSONArray("balls");
                if (rows.length() > 128) throw new java.io.IOException();
                SharedPreferences.Editor values = preferences.edit();
                StringBuilder added = new StringBuilder(preferences.getString("custom_ball_ids", ""));
                for (int i = 0; i < rows.length(); i++) {
                    org.json.JSONObject ball = rows.getJSONObject(i);
                    String id = "custom-" + UUID.randomUUID().toString().replace("-", "");
                    String name = ball.getString("name"); int body = ball.getInt("body");
                    values.putString(ballKey(id, "name"), name).putInt(ballKey(id, "body"), body)
                        .putInt(ballKey(id, "outline"), ball.optInt("outline", 0xFF111111))
                        .putInt(ballKey(id, "trail"), ball.optInt("trail", body))
                        .putInt(ballKey(id, "name_color"), ball.optInt("nameColor", body))
                        .putInt(ballKey(id, "name_outline"), ball.optInt("nameOutline", complementaryNameColor(ball.optInt("nameColor", body))));
                    added.append(id).append(','); BallRoster.registerCustom(id, name, body);
                }
                values.putString("custom_ball_ids", added.toString()).apply();
                showBallList();
            } else if (request == EXPORT_BALLS_REQUEST) {
                org.json.JSONArray rows = new org.json.JSONArray();
                for (String id : exportBallIds) {
                    BallAppearance appearance = ballAppearance(id);
                    rows.put(new org.json.JSONObject().put("name", ballName(id)).put("body", appearance.bodyColor)
                        .put("outline", appearance.outlineColor).put("trail", appearance.trailColor)
                        .put("nameColor", preferences.getInt(ballKey(id, "name_color"), appearance.bodyColor))
                        .put("nameOutline", preferences.getInt(ballKey(id, "name_outline"), complementaryNameColor(preferences.getInt(ballKey(id, "name_color"), appearance.bodyColor)))));
                }
                try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException();
                    output.write(new org.json.JSONObject().put("format", "ball-race-balls").put("version", 1)
                        .put("balls", rows).toString().getBytes(StandardCharsets.UTF_8));
                }
                selectingBallsToExport = false;
                selectedBallExports.clear();
                exportBallIds.clear();
                Ui.toast(activity, t("ball_exported"), 0).show();
                showBallList();
            } else if (request == IMPORT_BALL_IMAGE_REQUEST) {
                try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new java.io.IOException();
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(input, null, bounds);
                    if (bounds.outWidth < 1 || bounds.outHeight < 1) throw new java.io.IOException();
                    int sample = 1;
                    while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2;
                    BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
                    try (InputStream image = activity.getContentResolver().openInputStream(uri)) {
                        pendingBallBitmap = BitmapFactory.decodeStream(image, null, options);
                    }
                }
                if (pendingBallBitmap == null) throw new java.io.IOException();
                showBallCrop();
            } else if (request == EXPORT_LEVEL_REQUEST) {
                try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new java.io.IOException();
                    if (exportLevelIds.size() > 1) {
                        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8);
                        for (String id : exportLevelIds) {
                            EditableLevel level = loadEditableLevel(id);
                            zip.putNextEntry(new ZipEntry(level.id + ".json"));
                            zip.write(level.toJson().getBytes(StandardCharsets.UTF_8));
                            zip.closeEntry();
                        }
                        zip.finish();
                    } else {
                        EditableLevel level = exportLevelIds.isEmpty() ? editorLevel : loadEditableLevel(exportLevelIds.get(0));
                        output.write(level.toJson().getBytes(StandardCharsets.UTF_8));
                    }
                }
                Ui.toast(activity, t("editor_exported"), 0).show();
            } else if (request == IMPORT_LEVEL_REQUEST) {
                byte[] bytes;
                try (InputStream input = activity.getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) throw new java.io.IOException();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (output.size() + count > 1024 * 1024) throw new java.io.IOException();
                        output.write(buffer, 0, count);
                    }
                    bytes = output.toByteArray();
                }
                if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
                    ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8);
                    ZipEntry entry; int imported = 0;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                            ByteArrayOutputStream levelBytes = new ByteArrayOutputStream(); byte[] buffer = new byte[2048]; int count;
                            while ((count = zip.read(buffer)) >= 0) { if (levelBytes.size() + count > 64 * 1024) throw new java.io.IOException(); levelBytes.write(buffer, 0, count); }
                            String id = "custom-" + UUID.randomUUID().toString().replace("-", "");
                            EditableLevel importedLevel = EditableLevel.importCustom(new String(levelBytes.toByteArray(), StandardCharsets.UTF_8), id);
                            preferences.edit().putString("level_" + id + "_json", importedLevel.toJson()).apply(); saveCustomLevelId(id); imported++;
                        }
                        zip.closeEntry();
                    }
                    if (imported == 0) throw new java.io.IOException();
                    showLevelList();
                } else {
                    String id = "custom-" + UUID.randomUUID().toString().replace("-", "");
                    editorLevel = EditableLevel.importCustom(new String(bytes, StandardCharsets.UTF_8), id);
                    saveCustomLevelId(id); saveEditorLevel(); showLevelList();
                }
            }
        } catch (Exception error) {
            Ui.toast(activity, request == IMPORT_LEVEL_REQUEST ? t("editor_import_failed") : t("editor_export_failed"), 0).show();
        }
    }

    private boolean saveEditorLevelName() {
        if (editorLevel == null || editorLevelNameInput == null) return true;
        try {
            String entered = editorLevelNameInput.getText().toString().trim();
            editorLevel.rename(entered);
            editorLevelNameInput.setText(editorLevel.name);
            saveEditorLevel();
            editorLevelNameInput.clearFocus();
            return true;
        } catch (IllegalArgumentException invalid) {
            Ui.toast(activity, t("editor_name_invalid"), 0).show();
            editorLevelNameInput.setText(editorDisplayName(editorLevel));
            return false;
        }
    }

    private void saveEditorAndReturn() {
        saveEditorLevelName();
        saveEditorLevel();
        showLevelList();
    }

    private void discardEditorAndReturn() {
        new AlertDialog.Builder(activity)
            .setMessage(t("editor_discard_confirm"))
            .setNegativeButton(t("default_cancel"), null)
            .setPositiveButton(t("editor_discard"), (dialog, which) -> {
                if (editorLevel != null && editorOriginalJson != null) {
                    preferences.edit().putString("level_" + editorLevel.id + "_json", editorOriginalJson).apply();
                }
                showLevelList();
            }).show();
    }

    private void previewEditedLevel() {
        saveEditorLevelName();
        saveEditorLevel();
        showRace(System.currentTimeMillis(), 1,
            Collections.singletonList(editorLevel.toTemplate(obstacleElasticity)), true);
    }

    private void showRace(long seed, int levelCount, List<RaceCourse.Template> templates, boolean allowRepeats) {
        if (raceCanvas != null) raceCanvas.release();
        raceCanvas = null;
        rankingCollapsed = false;
        rankingFinalized = false;
        showAllRankings = false;
        if (zoomContainer != null) {
            if (raceFrame != null) raceFrame.removeView(zoomContainer);
            zoomContainer = null;
        }
        zoomControl = null;
        followPanel = null;
        main.removeCallbacks(statusTicker);
        RaceCourse course = RaceCourse.generate(seed, levelCount, templates, allowRepeats);
        BallPhysics.World world = createWorld(course);
        world.setPhysicsHz(physicsHz);
        if (preferences.getBoolean("race_keep_screen_awake", false))
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout page = Ui.page(activity, t("title"), t("race_subtitle"));
        LinearLayout body = Ui.column(activity);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout frame = new FrameLayout(activity);
        raceFrame = frame;
        frame.setBackground(shape(0xFFF4F6FA, 12));
        body.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        raceCanvas = new RaceCanvas(course, world, seed);
        raceCanvas.setCameraMode(cameraMode, cameraFollowName);
        frame.addView(raceCanvas, new FrameLayout.LayoutParams(-1, -1));

        rankingPanel = new LinearLayout(activity);
        rankingPanel.setOrientation(LinearLayout.VERTICAL);
        rankingPanel.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8));
        rankingPanel.setBackground(shape(0xDDF5F7FB, 10));
        rankingHeader = Ui.text(activity, t("ranking_title"), 20);
        rankingHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        rankingHeader.setGravity(Gravity.CENTER);
        rankingHeader.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 2), Ui.dp(activity, 4), Ui.dp(activity, 4));
        rankingPanel.addView(rankingHeader, new LinearLayout.LayoutParams(-1, -2));
        rankingNote = Ui.text(activity, "", 13);
        rankingNote.setVisibility(View.GONE);
        rankingPanel.addView(rankingNote, new LinearLayout.LayoutParams(-1, -2));
        rankingHeader.setOnClickListener(v -> {
            rankingCollapsed = !rankingCollapsed;
            rankingPanel.setVisibility(rankingCollapsed ? View.GONE : View.VISIBLE);
            if (rankingExpandButton != null) rankingExpandButton.setVisibility(rankingCollapsed ? View.VISIBLE : View.GONE);
            rankingTabs.setVisibility(rankingCollapsed || raceCanvas.finished() ? View.GONE : View.VISIBLE);
            rankingHeader.setText(t("ranking_title"));
            rankingHeader.setGravity(Gravity.CENTER);
        });
        rankingTabs = new LinearLayout(activity);
        rankingTabs.setOrientation(LinearLayout.HORIZONTAL);
        activeRankingButton = Ui.cell(rankingTabs, t("ranking_active"), () -> {
            showAllRankings = false;
            updateRanking();
        });
        allRankingButton = Ui.cell(rankingTabs, t("ranking_all"), () -> {
            showAllRankings = true;
            updateRanking();
        });
        rankingPanel.addView(rankingTabs, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 42)));
        rankingScroll = new ScrollView(activity);
        rankingList = new LinearLayout(activity);
        rankingList.setOrientation(LinearLayout.VERTICAL);
        rankingScroll.addView(rankingList, new ScrollView.LayoutParams(-1, -2));
        rankingPanel.addView(rankingScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        rankingResizeHandle = new View(activity);
        rankingResizeHandle.setBackground(shape(0xFF8B98A9, 8));
        LinearLayout.LayoutParams resizeParams = new LinearLayout.LayoutParams(Ui.dp(activity, 52), Ui.dp(activity, 7));
        resizeParams.gravity = Gravity.CENTER_HORIZONTAL;
        resizeParams.topMargin = Ui.dp(activity, 7);
        rankingPanel.addView(rankingResizeHandle, resizeParams);
        rankingResizeHandle.setContentDescription(t("ranking_resize"));
        rankingResizeHandle.setOnTouchListener(new View.OnTouchListener() {
            private float downY;
            private int startHeight;
            @Override public boolean onTouch(View view, MotionEvent event) {
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rankingPanel.getLayoutParams();
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downY = event.getRawY(); startHeight = params.height;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    int maxHeight = Math.max(Ui.dp(activity, 180), raceFrame.getHeight() - Ui.dp(activity, 70));
                    params.height = Math.max(Ui.dp(activity, 150), Math.min(maxHeight,
                        startHeight + Math.round(event.getRawY() - downY)));
                    rankingPanel.setLayoutParams(params);
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                return true;
            }
        });
        FrameLayout.LayoutParams rankingParams = new FrameLayout.LayoutParams(Ui.dp(activity, 196), Ui.dp(activity, 360), Gravity.TOP | Gravity.RIGHT);
        rankingParams.setMargins(0, Ui.dp(activity, 52), Ui.dp(activity, 8), 0);
        frame.addView(rankingPanel, rankingParams);
        rankingExpandButton = new Button(activity);
        rankingExpandButton.setText(t("ranking_expand"));
        rankingExpandButton.setOnClickListener(v -> {
            rankingCollapsed = false;
            rankingPanel.setVisibility(View.VISIBLE);
            rankingExpandButton.setVisibility(View.GONE);
            rankingTabs.setVisibility(raceCanvas.finished() ? View.GONE : View.VISIBLE);
            rankingHeader.setText(t("ranking_title"));
            rankingHeader.setGravity(Gravity.CENTER);
            updateRanking();
        });
        rankingExpandButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        rankingExpandButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams expandParams = new FrameLayout.LayoutParams(Ui.dp(activity, 116), Ui.dp(activity, 42), Gravity.TOP | Gravity.RIGHT);
        expandParams.setMargins(0, Ui.dp(activity, 52), Ui.dp(activity, 8), 0);
        frame.addView(rankingExpandButton, expandParams);

        raceStatus = Ui.text(activity, "", 20);
        raceStatus.setVisibility(View.GONE);
        raceStatus.setGravity(Gravity.CENTER_VERTICAL);
        raceStatus.setMaxLines(2);

        LinearLayout actions = Ui.row(body);
        raceActions = actions;
        Button[] pause = new Button[1];
        pause[0] = Ui.cell(actions, t("pause"), () -> {
            if (raceCanvas == null) return;
            if (raceCanvas.paused()) {
                raceCanvas.resumeRace();
                pause[0].setText(t("pause"));
            } else if (raceCanvas.pauseRace()) {
                pause[0].setText(t("resume"));
            }
        });
        Ui.cell(actions, t("follow_settings"), this::showFollowSettings);
        Ui.cell(actions, t("zoom"), () -> showZoomControl());
        Ui.cell(actions, t("menu_home"), this::showMainMenu);
        raceFinishHome = Ui.button(body, t("menu_home"), this::showMainMenu);
        raceFinishHome.setVisibility(View.GONE);

        updateRanking();
        main.removeCallbacks(statusTicker);
        main.post(statusTicker);
        raceCanvas.beginCountdown();
    }

    private void showFollowSettings() {
        if (raceFrame == null || raceCanvas == null || raceCanvas.finished()) return;
        if (followPanel != null) {
            raceFrame.removeView(followPanel);
            followPanel = null;
            return;
        }
        LinearLayout content = Ui.column(activity);
        content.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
        content.setBackground(shape(0xF5F5F7FA, 10));
        TextView title = Ui.text(activity, t("follow_settings"), 18);
        content.addView(title);
        RadioGroup modes = new RadioGroup(activity);
        followModeSpinner = null;
        String[] labels = followModeLabels();
        for (int index = 0; index < labels.length; index++) {
            RadioButton option = new RadioButton(activity);
            option.setText(labels[index]);
            option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            option.setTextColor(Ui.INK);
            option.setId(1000 + index);
            modes.addView(option, new RadioGroup.LayoutParams(-1, Ui.dp(activity, 44)));
        }
        modes.check(1000 + cameraMode);
        content.addView(modes);
        EditText name = new EditText(activity);
        followNameInput = name;
        name.setSingleLine(true);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTextColor(Ui.INK);
        name.setHint(t("follow_name"));
        name.setText(cameraFollowName);
        name.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
        name.setBackground(shape(0xFFF4F6FA, 10));
        content.addView(name);
        Button search = Ui.button(content, t("follow_search"), () -> {
            InputMethodManager input = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (input != null) input.hideSoftInputFromWindow(name.getWindowToken(), 0);
            name.clearFocus();
            TextView result = (TextView) content.getTag();
            result.setText(applyCameraName(name.getText().toString())
                ? t("follow_searching", cameraFollowName) : t("follow_not_found"));
            result.setVisibility(View.VISIBLE);
        });
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        TextView result = Ui.text(activity, "", 13);
        result.setTextColor(0xFF667085);
        result.setVisibility(View.GONE);
        content.addView(result);
        content.setTag(result);
        name.setVisibility(cameraMode == CAMERA_NAME ? View.VISIBLE : View.GONE);
        search.setVisibility(cameraMode == CAMERA_NAME ? View.VISIBLE : View.GONE);
        modes.setOnCheckedChangeListener((group, checkedId) -> {
            int mode = checkedId - 1000;
            name.setVisibility(mode == CAMERA_NAME ? View.VISIBLE : View.GONE);
            search.setVisibility(mode == CAMERA_NAME ? View.VISIBLE : View.GONE);
            if (mode != CAMERA_NAME) {
                cameraMode = mode; cameraFollowName = "";
                if (raceCanvas != null) raceCanvas.setCameraMode(cameraMode, cameraFollowName);
            }
        });
        followPanel = content;
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        panelParams.setMargins(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8), 0);
        raceFrame.addView(followPanel, panelParams);
    }

    private boolean applyCameraName(String value) {
        String requested = value == null ? "" : value.trim();
        BallPhysics.Ball match = RaceProgress.byName(raceCanvas.world.balls, requested);
        if (match != null) {
            cameraMode = CAMERA_NAME;
            cameraFollowName = match.name;
            raceCanvas.setCameraMode(cameraMode, cameraFollowName);
            return true;
        }
        return false;
    }

    private void showZoomControl() {
        if (zoomContainer != null) {
            raceFrame.removeView(zoomContainer);
            zoomControl = null;
            zoomContainer = null;
            return;
        }
        zoomContainer = new FrameLayout(activity);
        zoomContainer.setClipChildren(false);
        zoomContainer.setClipToPadding(false);
        zoomControl = new SeekBar(activity);
        zoomControl.setMax(160); zoomControl.setProgress(80); zoomControl.setRotation(-90f);
        zoomControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                if (raceCanvas != null && fromUser) raceCanvas.setZoom(zoomForSlider(value));
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });
        // 按比赛画布的实际高度限制滑块，避免小屏或高密度设备上底端被裁切。
        int margin = Ui.dp(activity, 8);
        int sliderLength = Math.min(Ui.dp(activity, 196),
            Math.max(Ui.dp(activity, 120), raceFrame.getHeight() - margin * 2));
        zoomContainer.addView(zoomControl,
            new FrameLayout.LayoutParams(sliderLength, Ui.dp(activity, 48), Gravity.CENTER));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Ui.dp(activity, 56), sliderLength + margin, Gravity.RIGHT | Gravity.BOTTOM);
        params.setMargins(0, 0, Ui.dp(activity, 12), margin);
        raceFrame.addView(zoomContainer, params);
    }

    private static float zoomForSlider(int value) {
        int clamped = Math.max(0, Math.min(160, value));
        if (clamped <= 80) return .5f + clamped / 160f;
        return 1f + (clamped - 80) / 80f * 1.5f;
    }

    private BallPhysics.World createWorld(RaceCourse course) {
        float finishY = course.height;
        float finishLeft = 10f;
        float finishRight = course.width - 10f;
        BallPhysics.World world = new BallPhysics.World(course.width, EARTH_GRAVITY * gravityG,
            finishY, finishLeft, finishRight);
        world.timeLimitSeconds = 180f;
        world.segments.add(new BallPhysics.Segment(10, 0, 10, finishY, 8, obstacleElasticity, CONTACT_FRICTION));
        world.segments.add(new BallPhysics.Segment(course.width - 10, 0, course.width - 10, finishY, 8, obstacleElasticity, CONTACT_FRICTION));
        // 关卡障碍由物理世界按固定时间动态更新，避免移动物块只改变外观。
        world.setCourseObstacles(course.obstacles);
        int count = Math.max(MIN_BALLS, participantIds.size());
        List<RaceCourse.StartPosition> starts = RaceCourse.staggeredStarts(count, course.width, BALL_RADIUS);
        for (int index = 0; index < count; index++) {
            BallRoster.ColorSpec color = BallRoster.find(participantIds.get(index));
            RaceCourse.StartPosition start = starts.get(index);
            world.balls.add(new BallPhysics.Ball(ballName(color.id), preferences.getInt(ballKey(color.id, "body"), color.color), start.x, start.y,
                BALL_RADIUS, BALL_MASS, ballElasticity, ballCollisionEnabled));
        }
        return world;
    }

    /** 根据同一套进度排序规则刷新可滚动榜单。 */
    private void updateRanking() {
        if (rankingList == null || raceCanvas == null || rankingFinalized) return;
        List<BallPhysics.Ball> ordered = RaceProgress.order(raceCanvas.world.balls, raceCanvas.course);
        rankingList.removeAllViews();
        int displayed = 0;
        for (int index = 0; index < ordered.size(); index++) {
            BallPhysics.Ball ball = ordered.get(index);
            if (!showAllRankings && ball.finished()) continue;
            TextView row = Ui.text(activity, "", 13);
            row.setTextColor(Ui.INK);
            String detail = ball.finished() ? t("ranking_time", ball.finishTime)
                : t("ranking_level", raceCanvas.course.levelAt(ball.y).index + 1);
            row.setText((displayed + 1) + ". " + ball.name + "\n" + detail);
            row.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4));
            rankingList.addView(row, new LinearLayout.LayoutParams(-1, -2));
            displayed++;
        }
        if (displayed == 0) rankingList.addView(Ui.text(activity, t("ranking_no_active"), 14));
        activeRankingButton.setEnabled(showAllRankings);
        allRankingButton.setEnabled(!showAllRankings);
        if (raceCanvas.finished() && !rankingFinalized) {
            rankingFinalized = true;
            rankingHeader.setText(t("ranking_title"));
            rankingHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            rankingHeader.setGravity(Gravity.CENTER);
            rankingNote.setText(raceCanvas.world.timedOut ? t("ranking_time_limit_note") : t("ranking_finished_note"));
            rankingNote.setVisibility(View.VISIBLE);
            rankingNote.setGravity(Gravity.LEFT);
            raceStatus.setVisibility(View.GONE);
            if (raceActions != null) raceActions.setVisibility(View.GONE);
            if (raceFinishHome != null) raceFinishHome.setVisibility(View.VISIBLE);
            if (followPanel != null) { raceFrame.removeView(followPanel); followPanel = null; }
            if (zoomControl != null) { raceFrame.removeView(zoomContainer); zoomControl = null; zoomContainer = null; }
            rankingPanel.setVisibility(View.VISIBLE);
            if (rankingExpandButton != null) rankingExpandButton.setVisibility(View.GONE);
            rankingPanel.setBackground(shape(0xF5F5F7FA, 0));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rankingPanel.getLayoutParams();
            params.width = -1;
            params.height = -1;
            params.setMargins(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8));
            rankingPanel.setLayoutParams(params);
            showAllRankings = true;
            activeRankingButton.setVisibility(View.GONE);
            allRankingButton.setVisibility(View.GONE);
            updateRankingRowsOnly(ordered);
        }
    }

    private void updateRankingRowsOnly(List<BallPhysics.Ball> ordered) {
        rankingList.removeAllViews();
        for (int index = 0; index < ordered.size(); index++) {
            BallPhysics.Ball ball = ordered.get(index);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = Ui.text(activity, (index + 1) + ". " + ball.name + "\n"
                + (ball.finished() ? t("ranking_time", ball.finishTime)
                    : t("ranking_level", raceCanvas.course.levelAt(ball.y).index + 1)), 18);
            label.setTextColor(Ui.INK);
            label.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            View swatch = new View(activity) {
                private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    ballPaint.setColor(ball.color);
                    ballPaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(getWidth() * .5f, getHeight() * .5f,
                        Math.min(getWidth(), getHeight()) * .38f, ballPaint);
                    ballPaint.setStyle(Paint.Style.STROKE);
                    ballPaint.setStrokeWidth(Math.max(2f, Math.min(getWidth(), getHeight()) * .08f));
                    ballPaint.setColor("black".equals(ball.name) ? 0xFFD1D5DB : 0xFF111111);
                    canvas.drawCircle(getWidth() * .5f, getHeight() * .5f,
                        Math.min(getWidth(), getHeight()) * .38f, ballPaint);
                }
            };
            swatch.setContentDescription(ball.name);
            row.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
            rankingList.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void showSettings() {
        showSettingsHub(true);
    }

    private void showSettingsHub(boolean hostSettings) {
        LinearLayout page = Ui.page(activity, t("settings"), t("settings_note"));
        LinearLayout content = Ui.column(activity);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        Button performance = Ui.button(content, t("performance_settings"), () -> showPerformanceSettings(hostSettings));
        Button system = Ui.button(content, t("system_settings"), () -> showSystemSettings(hostSettings));
        Button defaults = Ui.button(content, t("default_settings"), () -> showDefaultSettings(hostSettings));
        Button back = Ui.button(page, t("menu_back"), hostSettings ? activity::finish : this::showMainMenu);
        for (Button button : new Button[]{performance, system, defaults, back}) {
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setMinHeight(Ui.dp(activity, 64));
            button.setMinimumHeight(Ui.dp(activity, 64));
            button.getLayoutParams().height = Ui.dp(activity, 66);
        }
    }

    private void showPerformanceSettings(boolean hostSettings) {
        LinearLayout page = Ui.page(activity, t("performance_settings"), t("performance_note"));
        LinearLayout content = Ui.scroll(page);
        content.addView(Ui.text(activity, t("physics_frequency"), 16));
        Spinner frequency = new Spinner(activity);
        String[] values = {t("physics_hz_30"), t("physics_hz_45"), t("physics_hz_60"), t("physics_hz_90"), t("physics_hz_120")};
        frequency.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, values));
        int[] frequencies = {30, 45, 60, 90, 120};
        int selected = 4;
        for (int index = 0; index < frequencies.length; index++) if (frequencies[index] == physicsHz) selected = index;
        frequency.setSelection(selected);
        content.addView(frequency);
        content.addView(Ui.text(activity, t("physics_frequency_note"), 13));
        content.addView(Ui.text(activity, t("trail_quality"), 16));
        Spinner trails = new Spinner(activity);
        trails.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
            new String[]{t("trail_quality_off"), t("trail_quality_short"), t("trail_quality_standard")}));
        trails.setSelection(trailQuality);
        content.addView(trails);
        content.addView(Ui.text(activity, t("trail_quality_note"), 13));
        LinearLayout actions = Ui.row(page);
        Ui.cell(actions, t("settings_save"), () -> {
            physicsHz = frequencies[frequency.getSelectedItemPosition()];
            trailQuality = trails.getSelectedItemPosition();
            preferences.edit().putInt("race_physics_hz", physicsHz).putInt("race_trail_quality", trailQuality).apply();
            showSettingsHub(hostSettings);
        });
        Ui.cell(actions, t("default_cancel"), () -> showSettingsHub(hostSettings));
    }

    private void showSystemSettings(boolean hostSettings) {
        LinearLayout page = Ui.page(activity, t("system_settings"), t("system_settings_note"));
        LinearLayout content = Ui.scroll(page);
        CheckBox keepAwake = new CheckBox(activity);
        keepAwake.setText(t("keep_screen_awake"));
        keepAwake.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        keepAwake.setTextColor(Ui.INK);
        keepAwake.setChecked(preferences.getBoolean("race_keep_screen_awake", false));
        content.addView(keepAwake);
        content.addView(Ui.text(activity, t("keep_screen_awake_note"), 13));
        LinearLayout actions = Ui.row(page);
        Ui.cell(actions, t("settings_save"), () -> {
            preferences.edit().putBoolean("race_keep_screen_awake", keepAwake.isChecked()).apply();
            showSettingsHub(hostSettings);
        });
        Ui.cell(actions, t("default_cancel"), () -> showSettingsHub(hostSettings));
    }

    private void showDefaultSettings(boolean hostSettings) {
        LinearLayout page = Ui.page(activity, t("default_settings"), t("title"));
        LinearLayout content = Ui.scroll(page);
        boolean[] collisionEnabled = {preferences.getBoolean("default_ball_collision", true)};
        float[] obstacleElasticity = {clamp01(preferences.getFloat("default_obstacle_elasticity", 1f))};
        float[] ballElasticity = {clamp01(preferences.getFloat("default_ball_elasticity", .68f))};
        float[] gravity = {clamp(preferences.getFloat("default_gravity_g", .75f), 0f, 2f)};
        content.addView(Ui.text(activity, t("default_follow_mode"), 16));
        defaultFollowSpinner = new Spinner(activity);
        defaultFollowSpinner.setAdapter(followModeAdapter());
        defaultFollowSpinner.setSelection(clamp(preferences.getInt("default_follow_mode", CAMERA_FIRST), CAMERA_MANUAL, CAMERA_NAME));
        content.addView(defaultFollowSpinner);

        defaultObstacleElasticityLabel = Ui.text(activity, elasticityText(obstacleElasticity[0]), 14);
        content.addView(defaultObstacleElasticityLabel);
        defaultObstacleElasticitySeek = new SeekBar(activity);
        defaultObstacleElasticitySeek.setMax(100);
        defaultObstacleElasticitySeek.setProgress(Math.round(obstacleElasticity[0] * 100f));
        content.addView(defaultObstacleElasticitySeek);
        defaultObstacleElasticitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                defaultObstacleElasticityLabel.setText(elasticityText(value / 100f));
                obstacleElasticity[0] = value / 100f;
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });
        content.addView(Ui.text(activity, t("obstacle_elasticity_note"), 13));

        defaultBallElasticityLabel = Ui.text(activity, t("default_ball_elasticity_value", ballElasticity[0]), 14);
        content.addView(defaultBallElasticityLabel);
        defaultBallElasticitySeek = new SeekBar(activity);
        defaultBallElasticitySeek.setMax(100);
        defaultBallElasticitySeek.setProgress(Math.round(ballElasticity[0] * 100f));
        content.addView(defaultBallElasticitySeek);
        defaultBallElasticitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                defaultBallElasticityLabel.setText(t("default_ball_elasticity_value", value / 100f));
                ballElasticity[0] = value / 100f;
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });

        defaultBallCollision = new CheckBox(activity);
        defaultBallCollision.setText(t("ball_collision"));
        defaultBallCollision.setTextColor(Ui.INK);
        defaultBallCollision.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
        defaultBallCollision.setChecked(collisionEnabled[0]);
        defaultBallCollision.setOnCheckedChangeListener((button, enabled) -> collisionEnabled[0] = enabled);
        content.addView(defaultBallCollision);
        content.addView(Ui.text(activity, t("ball_collision_note"), 13));

        defaultGravityLabel = Ui.text(activity, gravityText(gravity[0]), 14);
        content.addView(defaultGravityLabel);
        defaultGravitySeek = new SeekBar(activity);
        defaultGravitySeek.setMax(200);
        defaultGravitySeek.setProgress(Math.round(gravity[0] * 100f));
        content.addView(defaultGravitySeek);
        defaultGravitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                defaultGravityLabel.setText(gravityText(value / 100f));
                gravity[0] = value / 100f;
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });
        content.addView(Ui.text(activity, t("gravity_note"), 13));
        content.addView(Ui.text(activity, t("physics_note"), 13));
        LinearLayout defaultActions = Ui.row(page);
        Ui.cell(defaultActions, t("default_save"), () -> {
            preferences.edit()
                .putInt("default_follow_mode", defaultFollowSpinner.getSelectedItemPosition())
                .putBoolean("default_ball_collision", collisionEnabled[0])
                .putFloat("default_obstacle_elasticity", obstacleElasticity[0])
                .putFloat("default_ball_elasticity", ballElasticity[0])
                .putFloat("default_gravity_g", gravity[0])
                .commit();
            if (hostSettings) activity.finish(); else showMainMenu();
        });
        Ui.cell(defaultActions, t("default_cancel"), hostSettings ? activity::finish : this::showMainMenu);
    }

    private ArrayAdapter<String> followModeAdapter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
            new String[]{t("camera_manual"), t("camera_last"), t("camera_first"), t("camera_name")});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private String[] followModeLabels() {
        return new String[]{t("camera_manual"), t("camera_last"), t("camera_first"), t("camera_name")};
    }

    @Override protected void onResume() {
        super.onResume();
        if (raceCanvas != null) raceCanvas.hostResumed();
        if (raceCanvas != null) {
            main.removeCallbacks(statusTicker);
            main.post(statusTicker);
        }
    }

    @Override protected void onPause() {
        main.removeCallbacks(statusTicker);
        if (raceCanvas != null) raceCanvas.hostPaused();
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(statusTicker);
        if (raceCanvas != null) raceCanvas.release();
        super.onDestroy();
    }

    private final class EditorCanvas extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private EditableLevel level;
        private EditableLevel.Obstacle dragging;
        private int draggingPort;
        private float scale = 1f;
        private float originX;
        private float originY;
        private float lastWorldX;
        private float lastWorldY;

        EditorCanvas(EditableLevel level) {
            super(activity);
            this.level = level;
            setBackgroundColor(0xFFF4F6FA);
            setContentDescription(t("editor_canvas_accessibility"));
            setFocusable(true);
        }

        void setLevel(EditableLevel next) {
            level = next;
            invalidate();
        }

        private void updateTransform() {
            scale = Math.min(getWidth() / level.width, getHeight() / level.height);
            if (scale <= 0f) scale = 1f;
            originX = (getWidth() - level.width * scale) * .5f;
            originY = (getHeight() - level.height * scale) * .5f;
        }

        private float worldX(float screenX) { return (screenX - originX) / scale; }
        private float worldY(float screenY) { return (screenY - originY) / scale; }
        float screenX(float pointX) { updateTransform(); return originX + pointX * scale; }
        float screenY(float pointY) { updateTransform(); return originY + pointY * scale; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            updateTransform();
            canvas.save();
            canvas.translate(originX, originY);
            canvas.scale(scale, scale);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFEAF0F7);
            canvas.drawRect(0f, 0f, level.width, level.height, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF9BAABD);
            paint.setStrokeWidth(2f);
            canvas.drawRect(1f, 1f, level.width - 1f, level.height - 1f, paint);
            for (int index = 1; index < 6; index++) {
                float y = index * level.height / 6f;
                paint.setStrokeWidth(1f);
                paint.setColor(0xFFD5DEE8);
                canvas.drawLine(4f, y, RaceCourse.WORLD_WIDTH - 4f, y, paint);
            }
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (EditableLevel.Obstacle obstacle : level.obstacles()) {
                boolean selected = obstacle == selectedObstacle;
                if (!obstacle.visible) continue;
                paint.setColor(obstacle.color);
                if (EditableLevel.CIRCLE.equals(obstacle.type)) {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(obstacle.x, obstacle.y, obstacle.size, paint);
                    if (selected) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(0xFF246BCE);
                        paint.setStrokeWidth(5f);
                        canvas.drawCircle(obstacle.x, obstacle.y, obstacle.size + 5f, paint);
                    }
                } else if (EditableLevel.ELLIPSE.equals(obstacle.type)) {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.save();
                    canvas.rotate(obstacle.rotationDegrees, obstacle.x, obstacle.y);
                    canvas.drawOval(obstacle.x - obstacle.size, obstacle.y - obstacle.radiusY,
                        obstacle.x + obstacle.size, obstacle.y + obstacle.radiusY, paint);
                    canvas.restore();
                    if (selected) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(0xFF246BCE);
                        paint.setStrokeWidth(5f);
                        canvas.drawOval(obstacle.x - obstacle.size - 5f, obstacle.y - obstacle.radiusY - 5f,
                            obstacle.x + obstacle.size + 5f, obstacle.y + obstacle.radiusY + 5f, paint);
                    }
                } else if (EditableLevel.ARC.equals(obstacle.type)) {
                    RectF bounds = new RectF(obstacle.x - obstacle.size, obstacle.y - obstacle.size,
                        obstacle.x + obstacle.size, obstacle.y + obstacle.size);
                    paint.setStrokeCap(obstacle.rounded ? Paint.Cap.ROUND : Paint.Cap.BUTT);
                    if (selected) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(0xFF246BCE);
                        paint.setStrokeWidth(obstacle.thickness + 7f);
                        canvas.drawArc(bounds, obstacle.rotationDegrees - obstacle.arcDegrees * .5f,
                            obstacle.arcDegrees, false, paint);
                    }
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(obstacle.thickness);
                    canvas.drawArc(bounds, obstacle.rotationDegrees - obstacle.arcDegrees * .5f,
                        obstacle.arcDegrees, false, paint);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                } else {
                    float radians = (float) Math.toRadians(obstacle.rotationDegrees);
                    float halfX = (float) Math.cos(radians) * obstacle.size * .5f;
                    float halfY = (float) Math.sin(radians) * obstacle.size * .5f;
                    if (selected) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(0xFF246BCE);
                        paint.setStrokeWidth(obstacle.thickness + 7f);
                        canvas.drawLine(obstacle.x - halfX, obstacle.y - halfY,
                            obstacle.x + halfX, obstacle.y + halfY, paint);
                    }
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(obstacle.thickness);
                    canvas.drawLine(obstacle.x - halfX, obstacle.y - halfY,
                        obstacle.x + halfX, obstacle.y + halfY, paint);
                }
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFF8A00);
            canvas.drawCircle(level.entryX(), level.entryY(), 10f, paint);
            canvas.drawCircle(level.exitX(), level.exitY(), 10f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFFFFFFFF);
            paint.setStrokeWidth(2.5f);
            canvas.drawCircle(level.entryX(), level.entryY(), 10f, paint);
            canvas.drawCircle(level.exitX(), level.exitY(), 10f, paint);
            canvas.restore();
        }

        private EditableLevel.Obstacle hit(float x, float y) {
            List<EditableLevel.Obstacle> obstacles = level.obstacles();
            for (int index = obstacles.size() - 1; index >= 0; index--) {
                EditableLevel.Obstacle obstacle = obstacles.get(index);
                if (obstacle.hit(x, y, 10f)) return obstacle;
            }
            return null;
        }

        private float distance(float x1, float y1, float x2, float y2) {
            return (float) Math.hypot(x1 - x2, y1 - y2);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() != 1) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    updateTransform();
                    lastWorldX = worldX(event.getX());
                    lastWorldY = worldY(event.getY());
                    draggingPort = 0;
                    if (distance(lastWorldX, lastWorldY, level.entryX(), level.entryY()) <= 20f) draggingPort = 1;
                    else if (distance(lastWorldX, lastWorldY, level.exitX(), level.exitY()) <= 20f) draggingPort = 2;
                    dragging = draggingPort == 0 ? hit(lastWorldX, lastWorldY) : null;
                    if (dragging == null && draggingPort == 0 && selectedObstacle != null
                            && selectedObstacle.hit(lastWorldX, lastWorldY, 80f)) {
                        dragging = selectedObstacle;
                    }
                    if (draggingPort != 0) selectEditorPort(draggingPort);
                    else selectEditorObstacle(dragging);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (draggingPort != 0) {
                        level.movePort(draggingPort == 1, worldX(event.getX()), worldY(event.getY()));
                        lastWorldX = worldX(event.getX());
                        lastWorldY = worldY(event.getY());
                        invalidate();
                        updateEditorControls();
                    } else if (dragging != null) {
                        float currentX = worldX(event.getX());
                        float currentY = worldY(event.getY());
                        dragging.setClamped(dragging.x + currentX - lastWorldX,
                            dragging.y + currentY - lastWorldY, dragging.size,
                            dragging.thickness, dragging.rotationDegrees);
                        lastWorldX = currentX;
                        lastWorldY = currentY;
                        invalidate();
                        updateEditorControls();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging != null || draggingPort != 0) saveEditorLevel();
                    dragging = null;
                    draggingPort = 0;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging != null || draggingPort != 0) saveEditorLevel();
                    dragging = null;
                    draggingPort = 0;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private final class RaceCanvas extends View implements Choreographer.FrameCallback {
        private final RaceCourse course;
        private final BallPhysics.World world;
        private final long seed;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ballNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint finishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<BallPhysics.Ball, BallAppearance> ballAppearances = new IdentityHashMap<>();
        private final Map<BallPhysics.Ball, Bitmap> ballImages = new IdentityHashMap<>();
        private final Map<BallPhysics.Ball, ArrayDeque<float[]>> trailHistory = new IdentityHashMap<>();
        private final PathEffect separatorEffect = new DashPathEffect(new float[]{7f, 6f}, 0f);
        private volatile int state = 0;
        private long countdownEnd;
        private long lastFrameNanos;
        private float scale = 1f;
        private float cameraX = RaceCourse.WORLD_WIDTH * .5f;
        private float cameraY = 220f;
        private float touchX;
        private float touchY;
        private float smoothedX;
        private float smoothedY;
        private boolean cameraInitialized;
        private int selectedCamera = CAMERA_LAST;
        private String selectedName = "";
        private boolean hostVisible = true;
        private boolean released;
        private boolean frameScheduled;
        private float zoom = 1f;

        RaceCanvas(RaceCourse course, BallPhysics.World world, long seed) {
            super(activity);
            this.course = course;
            this.world = world;
            this.seed = seed;
            for (BallPhysics.Ball ball : world.balls) {
                for (BallRoster.ColorSpec spec : BallRoster.colors()) {
                    if (!ballName(spec.id).equals(ball.name)) continue;
                    BallAppearance appearance = ballAppearance(spec.id);
                    ballAppearances.put(ball, appearance);
                    if (appearance.imagePath != null) {
                        Bitmap image = BitmapFactory.decodeFile(appearance.imagePath);
                        if (image != null) ballImages.put(ball, image);
                    }
                    break;
                }
            }
            setBackgroundColor(0xFFF4F6FA);
            textPaint.setColor(0xFF20334D);
            textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            textPaint.setTextSize(13f);
            ballNamePaint.setColor(0xFF30445D);
            ballNamePaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            ballNamePaint.setTextSize(11f);
            ballNamePaint.setTextAlign(Paint.Align.CENTER);
            finishPaint.setColor(0xFF246BCE);
            finishPaint.setStyle(Paint.Style.STROKE);
            finishPaint.setStrokeWidth(2.2f);
            setFocusable(true);
        }

        void setCameraMode(int mode, String name) {
            selectedCamera = clamp(mode, CAMERA_MANUAL, CAMERA_NAME);
            selectedName = name == null ? "" : name.trim();
            if (selectedCamera != CAMERA_MANUAL) cameraInitialized = false;
            invalidate();
        }

        void setZoom(float value) { zoom = Math.max(.5f, Math.min(2.5f, value)); invalidate(); }

        void beginCountdown() {
            if (released || !hostVisible) return;
            state = 0;
            countdownEnd = SystemClock.uptimeMillis() + 3000L;
            lastFrameNanos = 0L;
            scheduleFrame();
        }

        private void scheduleFrame() {
            if (released || !hostVisible || frameScheduled || state == 2 || state == 3) return;
            frameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        @Override public void doFrame(long frameTimeNanos) {
            frameScheduled = false;
            if (released || !hostVisible || state == 2 || state == 3) return;
            scale = getWidth() / RaceCourse.WORLD_WIDTH * zoom;
            if (scale <= 0f) {
                scheduleFrame();
                return;
            }
            if (state == 0 && SystemClock.uptimeMillis() >= countdownEnd) {
                world.start();
                state = 1;
            }
            if (state == 1) {
                float elapsed = lastFrameNanos == 0L ? 0f : Math.min(.25f,
                    (frameTimeNanos - lastFrameNanos) / 1_000_000_000f);
                lastFrameNanos = frameTimeNanos;
                world.advance(elapsed);
                recordTrailPositions();
                if (!world.running) state = 3;
            }
            updateCamera();
            invalidate();
            scheduleFrame();
        }

        private void recordTrailPositions() {
            boolean enabled = preferences.getBoolean("race_trails_enabled", true) && trailQuality > 0;
            for (BallPhysics.Ball ball : world.balls) {
                ArrayDeque<float[]> points = trailHistory.get(ball);
                if (points == null) {
                    points = new ArrayDeque<>();
                    trailHistory.put(ball, points);
                }
                if (!enabled) {
                    points.clear();
                    continue;
                }
                points.addLast(new float[]{ball.x, ball.y});
                int maxPoints = trailQuality == 1 ? 15 : 45;
                while (points.size() > maxPoints) points.removeFirst();
            }
        }

        boolean countdown() { return state == 0; }
        boolean paused() { return state == 2; }
        boolean finished() { return state == 3; }

        boolean pauseRace() {
            if (state == 0 || state == 3) return false;
            if (state != 1) return false;
            world.pause();
            state = 2;
            stopFrame();
            invalidate();
            return true;
        }

        void resumeRace() {
            if (state != 2) return;
            state = 1;
            lastFrameNanos = 0L;
            world.running = true;
            invalidate();
            scheduleFrame();
        }

        void hostPaused() {
            hostVisible = false;
            lastFrameNanos = 0L;
            stopFrame();
        }

        void hostResumed() {
            hostVisible = true;
            lastFrameNanos = 0L;
            if (state == 0) countdownEnd = SystemClock.uptimeMillis() + 3000L;
            scheduleFrame();
        }

        void release() {
            released = true;
            stopFrame();
            paint.setShader(null);
        }

        private void stopFrame() {
            if (frameScheduled) Choreographer.getInstance().removeFrameCallback(this);
            frameScheduled = false;
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            scale = width / RaceCourse.WORLD_WIDTH;
            if (width > 0 && height > 0) updateCamera();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xFFF4F6FA);
            if (released || getWidth() <= 0 || getHeight() <= 0) return;
            if (scale <= 0f) return;

            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            canvas.translate(getWidth() * .5f - cameraX * scale, getHeight() * .5f - cameraY * scale);
            canvas.scale(scale, scale);
            drawCourse(canvas);
            drawBalls(canvas);
            canvas.restore();
            drawOverlay(canvas);
        }

        private void updateCamera() {
            float halfHeight = getHeight() / (2f * Math.max(scale, .001f));
            if (selectedCamera == CAMERA_MANUAL) {
                if (!cameraInitialized) {
                    cameraX = course.width * .5f;
                    cameraY = Math.min(course.height, Math.max(halfHeight, 215f));
                    cameraInitialized = true;
                }
                clampCamera(halfHeight);
                return;
            }
            List<BallPhysics.Ball> ordered = RaceProgress.order(world.balls, course);
            BallPhysics.Ball target = null;
            if (selectedCamera == CAMERA_LAST) target = ordered.get(ordered.size() - 1);
            else if (selectedCamera == CAMERA_FIRST) {
                target = RaceProgress.leaderAmongUnfinished(world.balls, course);
                if (target == null) target = ordered.get(0);
            }
            else target = RaceProgress.byName(world.balls, selectedName);
            if (target == null) target = ordered.get(ordered.size() - 1);
            if (target == null) return;
            if (!cameraInitialized) {
                smoothedX = target.x;
                smoothedY = target.y;
                cameraInitialized = true;
            } else if (state == 1) {
                float amount = Math.min(1f, 7f / 60f);
                smoothedX += (target.x - smoothedX) * amount;
                smoothedY += (target.y - smoothedY) * amount;
            }
            cameraX = smoothedX;
            cameraY = smoothedY;
            clampCamera(halfHeight);
        }

        private void clampCamera(float halfHeight) {
            float halfWidth = getWidth() / (2f * Math.max(scale, .001f));
            if (selectedCamera == CAMERA_MANUAL) {
                cameraX = halfWidth >= course.width * .5f ? course.width * .5f
                    : Math.max(20f - halfWidth, Math.min(course.width + halfWidth - 20f, cameraX));
                cameraY = Math.max(-halfHeight + 20f, Math.min(course.height + halfHeight - 20f, cameraY));
            } else {
                cameraX = halfWidth >= course.width * .5f ? course.width * .5f
                    : Math.max(halfWidth, Math.min(course.width - halfWidth, cameraX));
                cameraY = Math.max(halfHeight, Math.min(course.height - halfHeight, cameraY));
            }
        }

        private void drawCourse(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFEAF0F7);
            canvas.drawRect(10, 0, course.width - 10, course.height, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF8FA2B9);
            paint.setStrokeWidth(8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(10, 0, 10, course.height, paint);
            canvas.drawLine(course.width - 10, 0, course.width - 10, course.height, paint);

            for (RaceCourse.Level level : course.levels) {
                float y = level.originY;
                paint.setColor(0xFFB7C4D3);
                paint.setStrokeWidth(1.2f);
                paint.setPathEffect(separatorEffect);
                canvas.drawLine(14, y, course.width - 14, y, paint);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF6B7D93);
                canvas.drawText(t("level_number", level.index + 1), 18, y + 25, textPaint);
                canvas.drawText(t("entry_marker"), level.template.entry.offset - 20, y + 22, textPaint);
                paint.setColor(0xFF7C8DA3);
                canvas.drawText("↓", level.template.entry.offset - 3, y + 44, textPaint);
                paint.setColor(0xFF6B7D93);
                canvas.drawText(t("exit_marker"), level.template.exit.offset - 18,
                    y + level.template.height - 20, textPaint);
                canvas.drawText("↓", level.template.exit.offset - 3,
                    y + level.template.height - 5, textPaint);
            }

            for (RaceCourse.Obstacle obstacle : course.obstacles) {
                if (!obstacle.visible) continue;
                RaceCourse.Obstacle.State state = obstacle.stateAt(world.elapsedSeconds);
                paint.setColor(obstacle.color);
                if (obstacle.shape == RaceCourse.Obstacle.SEGMENT) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(obstacle.thickness);
                    paint.setStrokeCap(obstacle.rounded ? Paint.Cap.ROUND : Paint.Cap.BUTT);
                    canvas.drawLine(state.x1, state.y1, state.x2, state.y2, paint);
                } else if (obstacle.shape == RaceCourse.Obstacle.ARC) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(obstacle.thickness);
                    paint.setStrokeCap(obstacle.rounded ? Paint.Cap.ROUND : Paint.Cap.BUTT);
                    RectF bounds = new RectF(state.x - obstacle.radius, state.y - obstacle.radius,
                        state.x + obstacle.radius, state.y + obstacle.radius);
                    canvas.drawArc(bounds, state.rotationDegrees - obstacle.arcDegrees * .5f,
                        obstacle.arcDegrees, false, paint);
                } else if (obstacle.shape == RaceCourse.Obstacle.ELLIPSE) {
                    paint.setStyle(Paint.Style.FILL);
                    RectF bounds = new RectF(state.x - obstacle.radius, state.y - obstacle.radiusY,
                        state.x + obstacle.radius, state.y + obstacle.radiusY);
                    canvas.save();
                    canvas.rotate(state.rotationDegrees, state.x, state.y);
                    canvas.drawOval(bounds, paint);
                    canvas.restore();
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(state.x, state.y, obstacle.radius, paint);
                }
            }
            for (RaceCourse.Level level : course.levels) for (RaceCourse.TextObject text : level.template.texts) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(text.color);
                paint.setTextSize(text.size);
                canvas.save();
                canvas.rotate(text.rotationDegrees, level.originX + text.x, level.originY + text.y);
                canvas.drawText(text.text, level.originX + text.x, level.originY + text.y, paint);
                canvas.restore();
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF246BCE);
            paint.setStrokeWidth(2.5f);
            canvas.drawLine(10f, course.height, course.width - 10f, course.height, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF246BCE);
            canvas.drawText(t("finish_line"), course.width * .5f - 28f, course.height - 9f, textPaint);
        }

        private void drawBalls(Canvas canvas) {
            for (BallPhysics.Ball ball : world.balls) {
                BallAppearance appearance = ballAppearances.get(ball);
                if (appearance == null) appearance = new BallAppearance(ball.color,
                    "black".equals(ball.name) ? 0xFFD1D5DB : 0xFF111111, ball.color, null);
                int alpha = ball.finished() ? 110 : 255;
                canvas.saveLayerAlpha(0f, 0f, course.width, course.height, alpha);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, ball.radius * .28f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(appearance.trailColor);
                ArrayDeque<float[]> points = trailHistory.get(ball);
                if (points != null && points.size() > 1 && preferences.getBoolean("race_trails_enabled", true)) {
                    float[] previous = null;
                    for (float[] point : points) {
                        if (previous != null) canvas.drawLine(previous[0], previous[1], point[0], point[1], paint);
                        previous = point;
                    }
                }
                Bitmap image = ballImages.get(ball);
                if (image != null) {
                    canvas.save();
                    Path clip = new Path(); clip.addCircle(ball.x, ball.y, ball.radius, Path.Direction.CW);
                    canvas.clipPath(clip);
                    canvas.drawBitmap(image, null, new RectF(ball.x - ball.radius, ball.y - ball.radius,
                        ball.x + ball.radius, ball.y + ball.radius), paint);
                    canvas.restore();
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ball.color);
                    canvas.drawCircle(ball.x, ball.y, ball.radius, paint);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1.5f, ball.radius * .16f));
                paint.setColor(appearance.outlineColor);
                canvas.drawCircle(ball.x, ball.y, ball.radius, paint);
                int nameColor = preferences.getInt(ballKeyForName(ball.name, "name_color"), ball.color);
                int nameOutline = preferences.getInt(ballKeyForName(ball.name, "name_outline"), complementaryNameColor(nameColor));
                ballNamePaint.setColor(nameOutline); ballNamePaint.setStyle(Paint.Style.STROKE); ballNamePaint.setStrokeWidth(3f);
                canvas.drawText(ball.name, ball.x, ball.y - ball.radius - 4f, ballNamePaint);
                ballNamePaint.setColor(nameColor);
                ballNamePaint.setStyle(Paint.Style.FILL);
                canvas.drawText(ball.name, ball.x, ball.y - ball.radius - 4f, ballNamePaint);
                canvas.restore();
            }
        }

        private void drawOverlay(Canvas canvas) {
            canvas.save();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xDDFFFFFF);
            canvas.drawRoundRect(Ui.dp(activity, 8), Ui.dp(activity, 8), getWidth() - Ui.dp(activity, 8),
                Ui.dp(activity, 48), Ui.dp(activity, 10), Ui.dp(activity, 10), paint);
            paint.setColor(0xFF20334D);
            paint.setTextSize(Ui.dp(activity, 16));
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            String text;
            if (state == 0) {
                long remaining = Math.max(0L, countdownEnd - SystemClock.uptimeMillis());
                text = t("countdown_value", Math.max(1, (int) Math.ceil(remaining / 1000f)));
            } else if (state == 2) text = t("race_paused");
            else if (state == 3) text = world.timedOut ? t("time_limit") : t("race_finished");
            else text = t("elapsed", world.elapsedSeconds);
            canvas.drawText(text, Ui.dp(activity, 16), Ui.dp(activity, 36), paint);
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (selectedCamera != CAMERA_MANUAL) return true;
            if (event.getPointerCount() != 1) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchX = event.getX();
                    touchY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - touchX;
                    float dy = event.getY() - touchY;
                    cameraX -= dx / Math.max(scale, .001f);
                    cameraY -= dy / Math.max(scale, .001f);
                    touchX = event.getX();
                    touchY = event.getY();
                    cameraInitialized = true;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    performClick();
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(Ui.dp(activity, radiusDp));
        return drawable;
    }

    private GradientDrawable colorDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private String elasticityText(float value) {
        return t("elasticity_value", value);
    }

    private String gravityText(float value) {
        return t("gravity_value", value);
    }

    private String levelCountText(int value) {
        return t("level_count_value", value);
    }

    private String repeatRuleText(int value) {
        return value > candidateLevelIds.size() ? t("repeat_rule") : t("no_repeat_rule");
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
