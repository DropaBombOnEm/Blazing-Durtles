/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazing_durtles.wk.fragments;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

import com.blazing_durtles.wk.Actment;
import com.blazing_durtles.wk.GlobalSettings;
import com.blazing_durtles.wk.R;
import com.blazing_durtles.wk.activities.AboutActivity;
import com.blazing_durtles.wk.activities.AbstractActivity;
import com.blazing_durtles.wk.activities.BackupActivity;
import com.blazing_durtles.wk.activities.DataImportExportActivity;
import com.blazing_durtles.wk.activities.FontImportActivity;
import com.blazing_durtles.wk.activities.FontSelectionActivity;
import com.blazing_durtles.wk.activities.KeyboardHelpActivity;
import com.blazing_durtles.wk.activities.SupportActivity;
import com.blazing_durtles.wk.activities.ThemeCustomizationActivity;
import com.blazing_durtles.wk.api.ApiState;
import com.blazing_durtles.wk.components.NumberRangePreference;
import com.blazing_durtles.wk.components.NumberRangePreferenceDialogFragment;
import com.blazing_durtles.wk.components.TaggedUrlPreference;
import com.blazing_durtles.wk.components.TaggedUrlPreferenceDialogFragment;
import com.blazing_durtles.wk.jobs.ResetDatabaseJob;
import com.blazing_durtles.wk.livedata.LiveApiState;
import com.blazing_durtles.wk.services.JobRunnerService;
import com.blazing_durtles.wk.util.AudioUtil;
import com.blazing_durtles.wk.util.DbLogger;
import com.blazing_durtles.wk.util.ThemeUtil;
import com.blazing_durtles.wk.util.UpdateChecker;

import java.util.List;
import java.util.Objects;

import static com.blazing_durtles.wk.Constants.API_KEY_PERMISSION_NOTICE;
import static com.blazing_durtles.wk.Constants.ENABLE_ADVANCED_WARNING;
import static com.blazing_durtles.wk.Constants.EXPERIMENTAL_PREFERENCE_STATUS_NOTICE;
import static com.blazing_durtles.wk.Constants.RESET_DATABASE_WARNING;
import static com.blazing_durtles.wk.Constants.RESET_TUTORIALS_WARNING;
import static com.blazing_durtles.wk.Constants.SUBJECT_SELECTION_NOTICE;
import static com.blazing_durtles.wk.util.ObjectSupport.isTrue;
import static com.blazing_durtles.wk.util.ObjectSupport.runAsync;
import static com.blazing_durtles.wk.util.ObjectSupport.safe;
import static com.blazing_durtles.wk.util.TextUtil.renderHtml;

/**
 * Fragment for preferences.
 */
public final class PreferencesFragment extends PreferenceFragmentCompat {
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            // Handle permission results
            final @Nullable TwoStatePreference enableNotificationsPref = findPreference("enable_notifications");
            if (result.containsKey(android.Manifest.permission.POST_NOTIFICATIONS)) {
                boolean granted = result.get(android.Manifest.permission.POST_NOTIFICATIONS);
                GlobalSettings.Other.setEnableNotifications(granted);
                enableNotificationsPref.setChecked(granted);
                enableNotificationsPref.callChangeListener(granted);
            }
        });

    @Override
    public void onCreatePreferences(final @Nullable Bundle savedInstanceState, final @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        // Add listener for always_hide_wk_mnemonic changes
        final @Nullable TwoStatePreference alwaysHideWKPref = findPreference("always_hide_wk_mnemonic");
        final @Nullable TwoStatePreference notAlwaysHideWKPref = findPreference("not_always_hide_wk_mnemonic");

        if (alwaysHideWKPref != null && notAlwaysHideWKPref != null) {
            alwaysHideWKPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean alwaysHide = (Boolean) newValue;
                notAlwaysHideWKPref.setChecked(!alwaysHide);
                return true;
            });

            // Set initial state
            notAlwaysHideWKPref.setChecked(!alwaysHideWKPref.isChecked());
        }

        // Notification permission request logic
        final @Nullable TwoStatePreference enableNotificationsPref = findPreference("enable_notifications");
        if (enableNotificationsPref != null) {
            // Set initial state based on both app setting and permission
            boolean enabled = GlobalSettings.Other.getEnableNotifications();
            boolean permissionGranted = true;
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                permissionGranted = ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            }
            enableNotificationsPref.setChecked(enabled && permissionGranted);

            enableNotificationsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean wantEnable = (Boolean) newValue;
                if (wantEnable) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        Activity activity = requireActivity();
                        if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            // Show rationale if needed
                            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.POST_NOTIFICATIONS)) {
                                new AlertDialog.Builder(activity)
                                    .setTitle("Notification Permissions Required")
                                    .setMessage("To show notifications for new reviews, please allow notification permissions...")
                                    .setPositiveButton("Allow", (dialog, which) -> ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001))
                                    .setNegativeButton("Cancel", (dialog, which) -> {
                                        enableNotificationsPref.setChecked(false);
                                        GlobalSettings.Other.setEnableNotifications(false);
                                    })
                                    .show();
                            } else {
                                ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                            }
                            // Don't enable yet, wait for permission result
                            return false;
                        }
                    }
                    GlobalSettings.Other.setEnableNotifications(true);
                    enableNotificationsPref.setChecked(true);
                } else {
                    GlobalSettings.Other.setEnableNotifications(false);
                    enableNotificationsPref.setChecked(false);
                }
                return false; // We handle the toggle manually
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        final @Nullable TwoStatePreference enableNotificationsPref = findPreference("enable_notifications");
        if (requestCode == 1001 && enableNotificationsPref != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                GlobalSettings.Other.setEnableNotifications(true);
                enableNotificationsPref.setChecked(true);
                enableNotificationsPref.callChangeListener(true); // Force UI update
            } else {
                GlobalSettings.Other.setEnableNotifications(false);
                enableNotificationsPref.setChecked(false);
                enableNotificationsPref.callChangeListener(false);
            }
        }
    }

    private void onViewCreatedBase(final View view, final @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackgroundColor(ThemeUtil.getColor(R.attr.colorBackground));

        final @Nullable Preference enableAdvanced = findPreference("enable_advanced");
        if (enableAdvanced != null) {
            enableAdvanced.setOnPreferenceChangeListener((preference, newValue) -> safe(false, () -> {
                final boolean enabled = isTrue(newValue);
                if (enabled && !GlobalSettings.getAdvancedEnabled()) {
                    new AlertDialog.Builder(view.getContext())
                            .setTitle("Enable Advanced Settings?")
                            .setMessage(renderHtml(ENABLE_ADVANCED_WARNING))
                            .setIcon(R.drawable.ic_baseline_warning_24px)
                            .setNegativeButton("No", (dialog, which) -> safe(() -> {
                                GlobalSettings.setAdvancedEnabled(false);
                                setVisibility("advanced_lesson_settings", false);
                                setVisibility("advanced_review_settings", false);
                                setVisibility("advanced_self_study_settings", false);
                                setVisibility("advanced_other_settings", false);
                                ((TwoStatePreference) preference).setChecked(false);
                            }))
                            .setPositiveButton("Yes", (dialog, which) -> safe(() -> {
                                GlobalSettings.setAdvancedEnabled(true);
                                setVisibility("advanced_lesson_settings", true);
                                setVisibility("advanced_review_settings", true);
                                setVisibility("advanced_self_study_settings", true);
                                setVisibility("advanced_other_settings", true);
                                ((TwoStatePreference) preference).setChecked(true);
                            })).create().show();
                    return false;
                }
                else {
                    setVisibility("advanced_lesson_settings", enabled);
                    setVisibility("advanced_review_settings", enabled);
                    setVisibility("advanced_self_study_settings", enabled);
                    setVisibility("advanced_other_settings", enabled);
                }
                this.updateTheme();
                requireActivity().recreate();
                return true;
            }));
        }

        setOnPreferenceClick("reset_database", preference -> safe(false, () -> {
            new AlertDialog.Builder(preference.getContext())
                    .setTitle("Reset Database?")
                    .setMessage(renderHtml(RESET_DATABASE_WARNING))
                    .setIcon(R.drawable.ic_baseline_warning_24px)
                    .setNegativeButton("No", (dialog, which) -> {})
                    .setPositiveButton("Yes", (dialog, which) -> safe(() -> {
                        JobRunnerService.schedule(ResetDatabaseJob.class, "");
                        goToMainActivity();
                    })).create().show();
            return true;
        }));



        setOnPreferenceClick("reset_tutorials", preference -> safe(false, () -> {
            new AlertDialog.Builder(preference.getContext())
                    .setTitle("Reset Confirmations and Tutorials?")
                    .setMessage(renderHtml(RESET_TUTORIALS_WARNING))
                    .setIcon(R.drawable.ic_baseline_warning_24px)
                    .setNegativeButton("No", (dialog, which) -> {})
                    .setPositiveButton("Yes", (dialog, which) -> safe(() -> {
                        GlobalSettings.resetConfirmationsAndTutorials();
                        Toast.makeText(preference.getContext(), "Confirmations and tutorials reset", Toast.LENGTH_LONG).show();
                    })).create().show();
            return true;
        }));

        setOnPreferenceClick("backup_settings", preference -> {
            new AlertDialog.Builder(preference.getContext())
                    .setTitle("Backup Settings?")
                    .setMessage("Are you sure you want to backup your settings?")
                    .setIcon(R.drawable.ic_baseline_warning_24px)
                    .setNegativeButton("No", (dialog, which) -> {})
                    .setPositiveButton("Yes", (dialog, which) -> {
                        try {
                            startBackup();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).create().show();
            return true;
        });

        setOnPreferenceClick("restore_settings", preference -> {
            new AlertDialog.Builder(preference.getContext())
                    .setTitle("Restore Settings?")
                    .setMessage("Are you sure you want to restore your settings?")
                    .setIcon(R.drawable.ic_baseline_warning_24px)
                    .setNegativeButton("No", (dialog, which) -> {})
                    .setPositiveButton("Yes", (dialog, which) -> {
                        try {
                            startRestore();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).create().show();
            return true;
        });

        final @Nullable ListPreference audioLocation = findPreference("audio_location");
        if (audioLocation != null) {
            final List<String> locationValues = AudioUtil.getLocationValues();
            final List<String> locations = AudioUtil.getLocations(locationValues);
            audioLocation.setEntries(locations.toArray(new String[] {}));
            audioLocation.setEntryValues(locationValues.toArray(new String[] {}));
            audioLocation.setVisible(true);
        }

        final @Nullable ListPreference nightThemePreference = findPreference("nightTheme");
        if (nightThemePreference != null) {
            // Set the summary provider for night theme to update the summary when the preference changes
            nightThemePreference.setSummaryProvider(preference -> {
                final CharSequence entry = Objects.requireNonNull(((ListPreference) preference).getEntry());
                return entry + " is used in system wide dark mode";
            });

            // Listen for changes to update the summary for night theme accordingly
            nightThemePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                // Update the state of the Preference with the new value.
                // The SummaryProvider will handle updating the summary.
                this.updateTheme();
                requireActivity().recreate();
                return true;
            });
        }

        final @Nullable ListPreference themePreference = findPreference("theme");
        if (themePreference != null) {
            // Set the summary provider for theme to update the summary when the preference changes
            themePreference.setSummaryProvider(preference -> {
                final CharSequence entry = Objects.requireNonNull(((ListPreference) preference).getEntry());
                return entry + " theme is currently selected";
            });

            // Listen for changes to update the summary for theme accordingly
            themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                // Update the state of the Preference with the new value.
                // The SummaryProvider will handle updating the summary.
                return true;
            });
        }

        // Daily Streak toggle
        final @Nullable TwoStatePreference showDailyStreakPref = findPreference("show_daily_streak");
        if (showDailyStreakPref != null) {
            showDailyStreakPref.setChecked(GlobalSettings.Dashboard.getShowDailyStreak());
            // Set summary to show longest streak
            updateLongestStreakSummary(showDailyStreakPref);
            showDailyStreakPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean show = (Boolean) newValue;
                GlobalSettings.Dashboard.setShowDailyStreak(show);
                updateLongestStreakSummary(showDailyStreakPref);
                return true;
            });
        }

        final @Nullable TwoStatePreference showDailyReviewPref = findPreference("show_daily_review_counter");
        if (showDailyReviewPref != null) {
            updateDailyReviewCounterSummary(showDailyReviewPref);
            showDailyReviewPref.setOnPreferenceChangeListener((preference, newValue) -> {
                updateDailyReviewCounterSummary(showDailyReviewPref);
                return true;
            });
        }
        final @Nullable TwoStatePreference showDailyLessonPref = findPreference("show_daily_lesson_counter");
        if (showDailyLessonPref != null) {
            updateDailyLessonCounterSummary(showDailyLessonPref);
            showDailyLessonPref.setOnPreferenceChangeListener((preference, newValue) -> {
                updateDailyLessonCounterSummary(showDailyLessonPref);
                return true;
            });
        }

        setVisibility("api_key_help", LiveApiState.getInstance().get() != ApiState.OK);
        setVisibility("advanced_lesson_settings", GlobalSettings.getAdvancedEnabled());
        setVisibility("advanced_review_settings", GlobalSettings.getAdvancedEnabled());
        setVisibility("advanced_self_study_settings", GlobalSettings.getAdvancedEnabled());
        setVisibility("advanced_other_settings", GlobalSettings.getAdvancedEnabled());
        setVisibility("ime_hint_reading", Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        setVisibility("ime_hint_meaning", Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        setVisibility("web_password", true);

        setSummaryHtml("api_key_help", API_KEY_PERMISSION_NOTICE);
        setSummaryHtml("experimental_status", EXPERIMENTAL_PREFERENCE_STATUS_NOTICE);
        setSummaryHtml("lesson_subject_selection", SUBJECT_SELECTION_NOTICE);
        setSummaryHtml("review_subject_selection", SUBJECT_SELECTION_NOTICE);
        setSummaryHtml("self_study_subject_selection", SUBJECT_SELECTION_NOTICE);

        setNumberInputType("overdue_threshold");
        setNumberInputType("max_lesson_session_size");
        setNumberInputType("max_review_session_size");
        setNumberInputType("max_self_study_session_size");
        setDecimalNumberInputType("next_button_delay");

        setOnClickGoToActivity("about_this_app", AboutActivity.class);
        setOnClickGoToActivity("support_and_feedback", SupportActivity.class);
        setOnPreferenceClick("check_for_update", preference -> {
            String currentVersion = com.blazing_durtles.wk.BuildConfig.VERSION_NAME; // Always matches build.gradle versionName
            UpdateChecker.checkForUpdate(requireActivity(), currentVersion);
            return true;
        });
        setOnClickGoToActivity("theme_customization", ThemeCustomizationActivity.class);
        setOnClickGoToActivity("font_selection", FontSelectionActivity.class);
        setOnClickGoToActivity("font_import", FontImportActivity.class);
        setOnClickGoToActivity("keyboard_help", KeyboardHelpActivity.class);
        setOnClickGoToActivity("data_import_export", DataImportExportActivity.class);
    }

    @Override
    public void onViewCreated(final View view, final @Nullable Bundle savedInstanceState) {
        safe(() -> onViewCreatedBase(view, savedInstanceState));
    }

    @Override
    public void onResume() {
        safe(() -> {
            super.onResume();
            final @Nullable Bundle args = getArguments();
            @Nullable CharSequence title = null;
            if (args != null) {
                final @Nullable String key = args.getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT);
                if (key != null) {
                    final @Nullable Preference preference = findPreference(key);
                    if (preference != null) {
                        title = preference.getTitle();
                    }
                }
            }
            if (title == null) {
                title = "Settings";
            }
            final @Nullable Activity activity = getActivity();
            if (activity instanceof Actment) {
                final @Nullable Toolbar toolbar = ((Actment) activity).getToolbar();
                if (toolbar != null) {
                    toolbar.setTitle(title);
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDisplayPreferenceDialog(final Preference preference) {
        safe(() -> {
            if (preference instanceof TaggedUrlPreference) {
                if (getParentFragmentManager().findFragmentByTag("TaggedUrlPreference") != null) {
                    return;
                }
                final DialogFragment f = TaggedUrlPreferenceDialogFragment.newInstance(preference.getKey());
                f.setTargetFragment(this, 0);
                f.show(getParentFragmentManager(), "TaggedUrlPreference");
                return;
            }
            if (preference instanceof NumberRangePreference) {
                if (getParentFragmentManager().findFragmentByTag("NumberRangePreference") != null) {
                    return;
                }
                final DialogFragment f = NumberRangePreferenceDialogFragment.newInstance(preference.getKey());
                f.setTargetFragment(this, 0);
                f.show(getParentFragmentManager(), "NumberRangePreference");
                return;
            }

            super.onDisplayPreferenceDialog(preference);
        });
    }

    private void goToActivity(final Class<? extends AbstractActivity> clas) {
        final @Nullable Activity a = getActivity();
        if (a instanceof Actment) {
            ((Actment) a).goToActivity(clas);
        }
    }

    private void goToMainActivity() {
        final @Nullable Activity a = getActivity();
        if (a instanceof Actment) {
            ((Actment) a).goToMainActivity();
        }
    }

    private void setOnPreferenceClick(final CharSequence key, final @Nullable Preference.OnPreferenceClickListener listener) {
        safe(() -> {
            final @Nullable Preference pref = findPreference(key);
            if (pref != null) {
                pref.setOnPreferenceClickListener(listener);
            }
        });
    }

    private void setOnClickGoToActivity(final CharSequence key, final Class<? extends AbstractActivity> clas) {
        safe(() -> setOnPreferenceClick(key, preference -> {
            safe(() -> goToActivity(clas));
            return true;
        }));
    }

    private void startBackup() {
        safe(() -> {
            Intent intent = new Intent(getActivity(), BackupActivity.class);
            intent.setAction("com.blazing_durtles.wk.BACKUP");
            startActivity(intent);
        });
    }

    private void startRestore() {
        safe(() -> {
            Intent intent = new Intent(getActivity(), BackupActivity.class);
            intent.setAction("com.blazing_durtles.wk.RESTORE");
            startActivity(intent);
        });
    }


    private void setNumberInputType(final CharSequence key) {
        safe(() -> {
            final @Nullable EditTextPreference pref = findPreference(key);
            if (pref != null) {
                pref.setOnBindEditTextListener(editText -> safe(() -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setSelection(editText.getText().length());
                }));
            }
        });
    }

    private void setSummaryHtml(final CharSequence key, final String html) {
        safe(() -> {
            final @Nullable Preference pref = findPreference(key);
            if (pref != null) {
                pref.setSummary(renderHtml(html));
            }
        });
    }

    private void setDecimalNumberInputType(@SuppressWarnings("SameParameterValue") final CharSequence key) {
        safe(() -> {
            final @Nullable EditTextPreference pref = findPreference(key);
            if (pref != null) {
                pref.setOnBindEditTextListener(editText -> safe(() -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                    editText.setSelection(editText.getText().length());
                }));
            }
        });
    }

    private void setVisibility(final CharSequence key, final boolean visible) {
        safe(() -> {
            final @Nullable Preference pref = findPreference(key);
            if (pref != null) {
                pref.setVisible(visible);
            }
        });

    }
    // In PreferencesFragment
    public void updateTheme() {
        View view = requireView();
        view.setBackgroundColor(ThemeUtil.getColor(R.attr.colorBackground));
    }

    private void updateLongestStreakSummary(TwoStatePreference pref) {
        SharedPreferences prefs = requireContext().getSharedPreferences("streak_prefs", android.content.Context.MODE_PRIVATE);
        int longest = prefs.getInt("longest_streak", 0);
        String dayLabel = (longest == 1) ? "Day" : "Days";
        pref.setSummary("(Longest Streak: " + longest + " " + dayLabel + ")");
    }

    private void updateDailyReviewCounterSummary(TwoStatePreference pref) {
        int highest = GlobalSettings.getDailyReviewHighest();
        pref.setSummary("(Highest: " + highest + ")");
    }

    private void updateDailyLessonCounterSummary(TwoStatePreference pref) {
        int highest = GlobalSettings.getDailyLessonHighest();
        pref.setSummary("(Highest: " + highest + ")");
    }


}
