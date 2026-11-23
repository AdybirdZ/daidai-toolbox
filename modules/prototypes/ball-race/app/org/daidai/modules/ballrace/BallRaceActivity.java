package org.daidai.modules.ballrace;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

import org.daidai.modules.shared.Texts;
import org.daidai.modules.shared.Ui;
import org.simpleweather.app.ModulePage;
import org.simpleweather.app.SettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 竖屏二维小球竞赛首版页面。 */
public final class BallRaceActivity extends ModulePage {
    private static final int MIN_BALLS = 2;
    private static final float BALL_RADIUS = 11f;
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
    private boolean ballCollisionEnabled = true;
    private int defaultFollowMode = CAMERA_FIRST;
    private int cameraMode = CAMERA_FIRST;
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
    private TextView rankingHeader;
    private Button activeRankingButton;
    private Button allRankingButton;
    private boolean showAllRankings;
    private boolean rankingFinalized;
    private LinearLayout raceActions;
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
    private EditableLevel.Obstacle selectedObstacle;
    private boolean updatingEditorControls;
    private boolean updatingEditorLevelNames;
    private long activeSeed;
    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            if (raceCanvas == null || isDestroyed() || isFinishing()) return;
            updateRaceStatus();
            updateRanking();
            main.postDelayed(this, 350);
        }
    };

    public BallRaceActivity(Activity activity) {
        super(activity);
        preferences = activity.getSharedPreferences("ballrace_module", Activity.MODE_PRIVATE);
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
        obstacleElasticity = clamp01(preferences.getFloat("default_obstacle_elasticity", .62f));
        ballElasticity = clamp01(preferences.getFloat("default_ball_elasticity", .68f));
        gravityG = clamp(preferences.getFloat("default_gravity_g", .75f), 0f, 2f);
        ballCollisionEnabled = preferences.getBoolean("default_ball_collision", true);
        matchLevelCount = clamp(preferences.getInt("race_level_count", 7), 1, 12);
        defaultFollowMode = clamp(preferences.getInt("default_follow_mode", CAMERA_FIRST), CAMERA_MANUAL, CAMERA_NAME);
        cameraMode = defaultFollowMode;
        cameraFollowName = "";
    }

    private ArrayList<String> readLevelIds(String key) {
        String saved = preferences.getString(key, null);
        if (saved == null || saved.trim().isEmpty()) return new ArrayList<>(EditableLevel.builtInIds());
        ArrayList<String> result = new ArrayList<>();
        for (String id : saved.split(",", -1)) {
            if (EditableLevel.isBuiltInId(id) && !result.contains(id)) result.add(id);
        }
        return result.isEmpty() ? new ArrayList<>(EditableLevel.builtInIds()) : result;
    }

    private void showMainMenu() {
        main.removeCallbacks(statusTicker);
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
        Button defaults = Ui.button(content, t("menu_default_settings"), () -> showDefaultSettings(false));
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
        LinearLayout page = Ui.page(activity, t("editor_title"), t("editor_menu_note"));
        LinearLayout content = Ui.scroll(page);
        Button editBalls = Ui.button(content, t("edit_balls_coming_soon"), () -> {});
        editBalls.setEnabled(false);
        Ui.button(content, t("edit_levels"), () -> {
            loadGameConfiguration();
            showLevelEditor(EditableLevel.builtInIds().get(0));
        });
        Ui.button(content, t("menu_back"), this::showMainMenu);
    }

    private void showSetup() {
        main.removeCallbacks(statusTicker);
        if (raceCanvas != null) raceCanvas.release();
        raceCanvas = null;
        LinearLayout page = Ui.page(activity, t("title"), t("race_setup"));
        setupContent = Ui.scroll(page);
        LinearLayout topActions = Ui.row(setupContent);
        Ui.cell(topActions, t("choose_participants"), this::showParticipants);
        Ui.cell(topActions, t("choose_levels"), this::showLevelPool);
        Ui.cell(topActions, t("match_settings"), this::showMatchSettings);

        LinearLayout summary = Ui.card(setupContent, t("race_setup"));
        summary.addView(Ui.text(activity, t("participant_summary", participantIds.size()), 15));
        summary.addView(Ui.text(activity, t("course_pool_summary", candidateLevelIds.size(), matchLevelCount), 15));
        summary.addView(Ui.text(activity, t("setup_note"), 14));
        Ui.button(setupContent, t("start_race"), this::startRace);
        Ui.button(setupContent, t("menu_back"), this::showMainMenu);
    }

    private void showParticipants() {
        LinearLayout page = Ui.page(activity, t("choose_participants"),
            t("participant_summary", participantIds.size()));
        LinearLayout content = Ui.scroll(page);
        for (String id : new ArrayList<>(participantIds)) {
            BallRoster.ColorSpec color = BallRoster.find(id);
            if (color == null) continue;
            LinearLayout row = Ui.row(Ui.card(content, ""));
            ImageView sample = new ImageView(activity);
            sample.setImageDrawable(colorDrawable(color.color));
            row.addView(sample, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
            TextView name = Ui.text(activity, t(color.resourceKey), 18);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Ui.cell(row, t("remove_ball"), () -> {
                if (!BallRoster.remove(participantIds, id)) {
                    Ui.toast(activity, t("minimum_racers"), 0).show();
                    return;
                }
                saveConfiguration();
                showParticipants();
            });
        }
        Ui.button(content, t("add_ball"), this::showAvailableParticipants);
        Ui.button(content, t("participant_done"), this::showSetup);
    }

    private void showAvailableParticipants() {
        LinearLayout page = Ui.page(activity, t("add_ball"), t("available_colors_note"));
        LinearLayout content = Ui.scroll(page);
        for (BallRoster.ColorSpec color : BallRoster.colors()) {
            if (participantIds.contains(color.id)) continue;
            LinearLayout card = Ui.card(content, "");
            LinearLayout row = Ui.row(card);
            ImageView sample = new ImageView(activity);
            sample.setImageDrawable(colorDrawable(color.color));
            row.addView(sample, new LinearLayout.LayoutParams(Ui.dp(activity, 40), Ui.dp(activity, 40)));
            TextView name = Ui.text(activity, t(color.resourceKey), 18);
            name.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Ui.cell(row, t("add_ball"), () -> {
                if (!BallRoster.add(participantIds, color.id)) {
                    Ui.toast(activity, t("maximum_racers"), 0).show();
                    return;
                }
                saveConfiguration();
                showAvailableParticipants();
            });
        }
        Ui.button(content, t("menu_back"), this::showParticipants);
    }

    private void showLevelPool() {
        LinearLayout page = Ui.page(activity, t("choose_levels"), t("level_pool_note"));
        LinearLayout content = Ui.scroll(page);
        levelPoolCheckBoxes = new ArrayList<>();
        List<String> ids = EditableLevel.builtInIds();
        for (String id : ids) {
            CheckBox selector = new CheckBox(activity);
            selector.setText(builtInLevelLabel(id));
            selector.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            selector.setTextColor(Ui.INK);
            selector.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BLUE));
            selector.setChecked(candidateLevelIds.contains(id));
            content.addView(selector);
            levelPoolCheckBoxes.add(selector);
        }
        Ui.button(content, t("save_level_pool"), () -> {
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
        matchLevelCountSeek = new SeekBar(activity);
        matchLevelCountSeek.setMax(11);
        matchLevelCountSeek.setProgress(matchLevelCount - 1);
        content.addView(matchLevelCountSeek);
        TextView repeatNote = Ui.text(activity, repeatRuleText(matchLevelCount), 13);
        content.addView(repeatNote);
        matchLevelCountSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                matchLevelCount = value + 1;
                matchLevelCountLabel.setText(levelCountText(matchLevelCount));
                repeatNote.setText(repeatRuleText(matchLevelCount));
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
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
        Ui.button(content, t("save_match_settings"), () -> {
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
        for (RaceCourse.Template template : RaceCourse.templates(obstacleElasticity)) {
            if (id.equals(template.id)) return EditableLevel.fromTemplate(template);
        }
        throw new IllegalArgumentException("内置关卡编号无效");
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
        if ("slopes".equals(id)) return t("level_slopes");
        if ("bumpers".equals(id)) return t("level_bumpers");
        if ("rails".equals(id)) return t("level_rails");
        if ("zigzag".equals(id)) return t("level_zigzag");
        return t("level_center_pegs");
    }

    private String[] editorLevelLabels() {
        List<String> labels = new ArrayList<>();
        for (String id : EditableLevel.builtInIds()) labels.add(builtInLevelLabel(id));
        return labels.toArray(new String[0]);
    }

    private void showLevelEditor(String selectedLevelId) {
        main.removeCallbacks(statusTicker);
        if (raceCanvas != null) raceCanvas.release();
        raceCanvas = null;
        editorLevel = loadEditableLevel(selectedLevelId);
        selectedObstacle = null;

        LinearLayout page = Ui.page(activity, t("editor_title"), t("editor_note"));
        LinearLayout content = Ui.scroll(page);
        LinearLayout levelCard = Ui.card(content, t("editor_level"));
        editorLevelSpinner = new Spinner(activity);
        editorLevelAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
            editorLevelLabels());
        editorLevelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        editorLevelSpinner.setAdapter(editorLevelAdapter);
        int selection = EditableLevel.builtInIds().indexOf(selectedLevelId);
        editorLevelSpinner.setSelection(Math.max(0, selection), false);
        levelCard.addView(editorLevelSpinner);
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
        editorLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingEditorLevelNames) return;
                String nextId = EditableLevel.builtInIds().get(position);
                if (!nextId.equals(editorLevel.id)) {
                    saveEditorLevelName();
                    saveEditorLevel();
                    editorLevel = loadEditableLevel(nextId);
                    selectedObstacle = null;
                    editorCanvas.setLevel(editorLevel);
                    editorLevelNameInput.setText(editorDisplayName(editorLevel));
                    updateEditorControls();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout canvasCard = Ui.card(content, t("editor_canvas"));
        editorCanvas = new EditorCanvas(editorLevel);
        canvasCard.addView(editorCanvas, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 420)));

        LinearLayout addActions = Ui.row(content);
        Ui.cell(addActions, t("editor_add_circle"), () -> addEditorObstacle(EditableLevel.CIRCLE));
        Ui.cell(addActions, t("editor_add_slope"), () -> addEditorObstacle(EditableLevel.SLOPE));
        Ui.cell(addActions, t("editor_delete"), this::deleteSelectedObstacle);

        LinearLayout propertyCard = Ui.card(content, t("editor_properties"));
        editorSizeLabel = Ui.text(activity, "", 14);
        propertyCard.addView(editorSizeLabel);
        editorSizeSeek = new SeekBar(activity);
        propertyCard.addView(editorSizeSeek);
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
        editorThicknessSeek.setMax(30);
        ((LinearLayout) editorThicknessRow).addView(editorThicknessLabel);
        ((LinearLayout) editorThicknessRow).addView(editorThicknessSeek);
        propertyCard.addView(editorThicknessRow);
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
        editorRotationSeek.setMax(359);
        ((LinearLayout) editorRotationRow).addView(editorRotationLabel);
        ((LinearLayout) editorRotationRow).addView(editorRotationSeek);
        propertyCard.addView(editorRotationRow);
        editorRotationSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seek, int value, boolean fromUser) {
                if (fromUser) updateSelectedRotation(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seek) {}
            @Override public void onStopTrackingTouch(SeekBar seek) {}
        });

        LinearLayout actions = Ui.row(content);
        Ui.cell(actions, t("editor_preview"), this::previewEditedLevel);
        Ui.cell(actions, t("editor_save"), this::saveEditorAndReturn);
        updateEditorControls();
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
        editorLevel.remove(selectedObstacle);
        selectedObstacle = null;
        editorCanvas.invalidate();
        updateEditorControls();
        saveEditorLevel();
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
        selectedObstacle = obstacle;
        updateEditorControls();
        if (editorCanvas != null) editorCanvas.invalidate();
    }

    private void updateEditorControls() {
        if (editorSizeSeek == null || editorLevel == null) return;
        updatingEditorControls = true;
        boolean selected = selectedObstacle != null;
        editorSizeSeek.setEnabled(selected);
        editorThicknessSeek.setEnabled(selected && EditableLevel.SLOPE.equals(selectedObstacle.type));
        editorRotationSeek.setEnabled(selected && EditableLevel.SLOPE.equals(selectedObstacle.type));
        editorThicknessRow.setVisibility(selected && EditableLevel.SLOPE.equals(selectedObstacle.type) ? View.VISIBLE : View.GONE);
        editorRotationRow.setVisibility(selected && EditableLevel.SLOPE.equals(selectedObstacle.type) ? View.VISIBLE : View.GONE);
        if (!selected) {
            editorSizeLabel.setText(t("editor_select_object"));
            editorThicknessLabel.setText(t("editor_thickness"));
            editorRotationLabel.setText(t("editor_rotation"));
        } else {
            boolean circle = EditableLevel.CIRCLE.equals(selectedObstacle.type);
            float minimum = circle ? 10f : 40f;
            float maximum = circle ? 72f : 300f;
            editorSizeLabel.setText(t(circle ? "editor_radius_value" : "editor_length_value",
                Math.round(selectedObstacle.size)));
            editorSizeSeek.setMax(Math.round(maximum - minimum));
            editorSizeSeek.setProgress(Math.round(selectedObstacle.size - minimum));
            if (!circle) {
                editorThicknessLabel.setText(t("editor_thickness_value", Math.round(selectedObstacle.thickness)));
                editorThicknessSeek.setProgress(Math.round(selectedObstacle.thickness - 6f));
                editorRotationLabel.setText(t("editor_rotation_value", Math.round(selectedObstacle.rotationDegrees)));
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
        return level == null || level.id.equals(level.name) ? builtInLevelLabel(level.id) : level.name;
    }

    private boolean saveEditorLevelName() {
        if (editorLevel == null || editorLevelNameInput == null) return true;
        try {
            String entered = editorLevelNameInput.getText().toString().trim();
            if (editorLevel.id.equals(editorLevel.name) && entered.equals(defaultLevelLabel(editorLevel.id))) {
                entered = editorLevel.id;
            }
            editorLevel.rename(entered);
            editorLevelNameInput.setText(editorLevel.name);
            saveEditorLevel();
            if (editorLevelAdapter != null) {
                int selected = editorLevelSpinner.getSelectedItemPosition();
                updatingEditorLevelNames = true;
                editorLevelAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
                    editorLevelLabels());
                editorLevelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                editorLevelSpinner.setAdapter(editorLevelAdapter);
                editorLevelSpinner.setSelection(selected, false);
                updatingEditorLevelNames = false;
            }
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
        showEditorMenu();
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
        main.removeCallbacks(statusTicker);
        RaceCourse course = RaceCourse.generate(seed, levelCount, templates, allowRepeats);
        BallPhysics.World world = createWorld(course);
        LinearLayout page = Ui.page(activity, t("title"), t("race_subtitle"));
        LinearLayout body = Ui.column(activity);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout frame = new FrameLayout(activity);
        frame.setBackground(shape(0xFFF4F6FA, 12));
        body.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        raceCanvas = new RaceCanvas(course, world, seed);
        raceCanvas.setCameraMode(cameraMode, cameraFollowName);
        frame.addView(raceCanvas, new FrameLayout.LayoutParams(-1, -1));

        rankingPanel = new LinearLayout(activity);
        rankingPanel.setOrientation(LinearLayout.VERTICAL);
        rankingPanel.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8), Ui.dp(activity, 8));
        rankingPanel.setBackground(shape(0xDDF5F7FB, 10));
        rankingHeader = Ui.text(activity, t("race_running"), 16);
        rankingHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        rankingHeader.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 2), Ui.dp(activity, 4), Ui.dp(activity, 4));
        rankingPanel.addView(rankingHeader, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout rankingTabs = new LinearLayout(activity);
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
        FrameLayout.LayoutParams rankingParams = new FrameLayout.LayoutParams(Ui.dp(activity, 196), Ui.dp(activity, 230), Gravity.TOP | Gravity.RIGHT);
        rankingParams.setMargins(0, Ui.dp(activity, 52), Ui.dp(activity, 8), 0);
        frame.addView(rankingPanel, rankingParams);

        raceStatus = Ui.text(activity, "", 16);
        raceStatus.setGravity(Gravity.CENTER_VERTICAL);
        raceStatus.setMaxLines(2);
        body.addView(raceStatus, new LinearLayout.LayoutParams(-1, Ui.dp(activity, 50)));

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

        updateRaceStatus();
        updateRanking();
        main.removeCallbacks(statusTicker);
        main.post(statusTicker);
        raceCanvas.beginCountdown();
    }

    private void showFollowSettings() {
        LinearLayout content = Ui.column(activity);
        content.setPadding(Ui.dp(activity, 20), 0, Ui.dp(activity, 20), 0);
        Spinner modes = new Spinner(activity);
        followModeSpinner = modes;
        modes.setAdapter(followModeAdapter());
        modes.setSelection(cameraMode);
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
        name.setVisibility(cameraMode == CAMERA_NAME ? View.VISIBLE : View.GONE);
        modes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                name.setVisibility(position == CAMERA_NAME ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        followDialog = new AlertDialog.Builder(activity)
            .setTitle(t("follow_settings"))
            .setView(content)
            .setPositiveButton(t("default_save"), (dialog, which) -> {
                cameraMode = modes.getSelectedItemPosition();
                cameraFollowName = name.getText().toString().trim();
                if (cameraMode == CAMERA_NAME && RaceProgress.byName(raceCanvas.world.balls, cameraFollowName) == null) {
                    Ui.toast(activity, t("follow_not_found"), 0).show();
                    return;
                }
                raceCanvas.setCameraMode(cameraMode, cameraFollowName);
            })
            .setNegativeButton(t("default_cancel"), null)
            .create();
        followDialog.show();
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
        for (RaceCourse.Obstacle obstacle : course.obstacles) {
            if (obstacle.shape == RaceCourse.Obstacle.SEGMENT) {
                world.segments.add(new BallPhysics.Segment(obstacle.x1, obstacle.y1, obstacle.x2, obstacle.y2,
                    obstacle.thickness, obstacle.elasticity, CONTACT_FRICTION));
            } else {
                world.circles.add(new BallPhysics.CircleObstacle(obstacle.x1, obstacle.y1, obstacle.radius,
                    obstacle.elasticity, CONTACT_FRICTION));
            }
        }
        int count = Math.max(MIN_BALLS, participantIds.size());
        List<RaceCourse.StartPosition> starts = RaceCourse.staggeredStarts(count, course.width, BALL_RADIUS);
        for (int index = 0; index < count; index++) {
            BallRoster.ColorSpec color = BallRoster.find(participantIds.get(index));
            RaceCourse.StartPosition start = starts.get(index);
            world.balls.add(new BallPhysics.Ball(t(color.resourceKey), color.color, start.x, start.y,
                BALL_RADIUS, BALL_MASS, ballElasticity, ballCollisionEnabled));
        }
        return world;
    }

    private void updateRaceStatus() {
        if (raceStatus == null || raceCanvas == null) return;
        List<BallPhysics.Ball> ordered = RaceProgress.order(raceCanvas.world.balls, raceCanvas.course);
        String state;
        if (raceCanvas.finished()) {
            state = raceCanvas.world.timedOut ? t("time_limit") : t("race_finished");
            raceStatus.setText(state);
        } else {
            state = raceCanvas.countdown() ? t("countdown") : raceCanvas.paused() ? t("race_paused") : t("race_running");
            raceStatus.setText(state + "　" + t("elapsed", raceCanvas.world.elapsedSeconds));
        }
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
            String detail = ball.finished() ? t("ranking_time", ball.finishTime) : t("ranking_unfinished");
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
            rankingHeader.setText(raceCanvas.world.timedOut ? t("time_limit") : t("race_finished"));
            raceStatus.setVisibility(View.GONE);
            if (raceActions != null) raceActions.setVisibility(View.GONE);
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
            TextView row = Ui.text(activity, (index + 1) + ". " + ball.name + "\n"
                + (ball.finished() ? t("ranking_time", ball.finishTime) : t("ranking_unfinished")), 18);
            row.setTextColor(Ui.INK);
            row.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
            rankingList.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void showSettings() {
        showDefaultSettings(true);
    }

    private void showDefaultSettings(boolean hostSettings) {
        LinearLayout page = Ui.page(activity, t("default_settings"), t("title"));
        LinearLayout content = Ui.scroll(page);
        boolean[] collisionEnabled = {preferences.getBoolean("default_ball_collision", true)};
        float[] obstacleElasticity = {clamp01(preferences.getFloat("default_obstacle_elasticity", .62f))};
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
        Ui.button(content, t("default_save"), () -> {
            preferences.edit()
                .putInt("default_follow_mode", defaultFollowSpinner.getSelectedItemPosition())
                .putBoolean("default_ball_collision", collisionEnabled[0])
                .putFloat("default_obstacle_elasticity", obstacleElasticity[0])
                .putFloat("default_ball_elasticity", ballElasticity[0])
                .putFloat("default_gravity_g", gravity[0])
                .commit();
            if (hostSettings) activity.finish(); else showMainMenu();
        });
        Ui.button(content, t("default_cancel"), hostSettings ? activity::finish : this::showMainMenu);
    }

    private ArrayAdapter<String> followModeAdapter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
            new String[]{t("camera_manual"), t("camera_last"), t("camera_first"), t("camera_name")});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
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
            scale = Math.min(getWidth() / RaceCourse.WORLD_WIDTH,
                getHeight() / RaceCourse.LEVEL_HEIGHT);
            if (scale <= 0f) scale = 1f;
            originX = (getWidth() - RaceCourse.WORLD_WIDTH * scale) * .5f;
            originY = (getHeight() - RaceCourse.LEVEL_HEIGHT * scale) * .5f;
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
            canvas.drawRect(0f, 0f, RaceCourse.WORLD_WIDTH, RaceCourse.LEVEL_HEIGHT, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xFF9BAABD);
            paint.setStrokeWidth(2f);
            canvas.drawRect(1f, 1f, RaceCourse.WORLD_WIDTH - 1f, RaceCourse.LEVEL_HEIGHT - 1f, paint);
            for (int index = 1; index < 6; index++) {
                float y = index * 80f;
                paint.setStrokeWidth(1f);
                paint.setColor(0xFFD5DEE8);
                canvas.drawLine(4f, y, RaceCourse.WORLD_WIDTH - 4f, y, paint);
            }
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (EditableLevel.Obstacle obstacle : level.obstacles()) {
                boolean selected = obstacle == selectedObstacle;
                if (EditableLevel.CIRCLE.equals(obstacle.type)) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFF596C83);
                    canvas.drawCircle(obstacle.x, obstacle.y, obstacle.size, paint);
                    if (selected) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(0xFF246BCE);
                        paint.setStrokeWidth(5f);
                        canvas.drawCircle(obstacle.x, obstacle.y, obstacle.size + 5f, paint);
                    }
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
                    paint.setColor(0xFF596C83);
                    paint.setStrokeWidth(obstacle.thickness);
                    canvas.drawLine(obstacle.x - halfX, obstacle.y - halfY,
                        obstacle.x + halfX, obstacle.y + halfY, paint);
                }
            }
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

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() != 1) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    updateTransform();
                    lastWorldX = worldX(event.getX());
                    lastWorldY = worldY(event.getY());
                    dragging = hit(lastWorldX, lastWorldY);
                    selectEditorObstacle(dragging);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging != null) {
                        float currentX = worldX(event.getX());
                        float currentY = worldY(event.getY());
                        dragging.setClamped(dragging.x + currentX - lastWorldX,
                            dragging.y + currentY - lastWorldY, dragging.size,
                            dragging.thickness, dragging.rotationDegrees);
                        lastWorldX = currentX;
                        lastWorldY = currentY;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging != null) saveEditorLevel();
                    dragging = null;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging != null) saveEditorLevel();
                    dragging = null;
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

        RaceCanvas(RaceCourse course, BallPhysics.World world, long seed) {
            super(activity);
            this.course = course;
            this.world = world;
            this.seed = seed;
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
            scale = getWidth() / RaceCourse.WORLD_WIDTH;
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
                if (!world.running) state = 3;
            }
            updateCamera();
            invalidate();
            scheduleFrame();
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
                cameraX = Math.max(20f - halfWidth, Math.min(course.width + halfWidth - 20f, cameraX));
                cameraY = Math.max(-halfHeight + 20f, Math.min(course.height + halfHeight - 20f, cameraY));
            } else {
                cameraX = Math.max(halfWidth, Math.min(course.width - halfWidth, cameraX));
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
                if (obstacle.shape == RaceCourse.Obstacle.SEGMENT) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFF596C83);
                    paint.setStrokeWidth(obstacle.thickness);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    canvas.drawLine(obstacle.x1, obstacle.y1, obstacle.x2, obstacle.y2, paint);
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(0xFF596C83);
                    canvas.drawCircle(obstacle.x1, obstacle.y1, obstacle.radius, paint);
                }
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
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ball.color);
                canvas.drawCircle(ball.x, ball.y, ball.radius, paint);
                canvas.drawText(ball.name, ball.x, ball.y - ball.radius - 4f, ballNamePaint);
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
