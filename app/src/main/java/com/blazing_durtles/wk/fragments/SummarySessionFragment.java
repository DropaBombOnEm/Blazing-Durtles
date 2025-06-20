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

import static com.blazing_durtles.wk.util.ObjectSupport.runAsync;
import static com.blazing_durtles.wk.util.ObjectSupport.runAsyncWithProgress;
import static com.blazing_durtles.wk.util.ObjectSupport.safe;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.blazing_durtles.wk.GlobalSettings;
import com.blazing_durtles.wk.R;
import com.blazing_durtles.wk.WkApplication;
import com.blazing_durtles.wk.db.AppDatabase;
import com.blazing_durtles.wk.db.model.SessionItem;
import com.blazing_durtles.wk.db.model.Subject;
import com.blazing_durtles.wk.enums.FragmentTransitionAnimation;
import com.blazing_durtles.wk.jobs.ReportSessionItemJob;
import com.blazing_durtles.wk.livedata.LiveAlertContext;
import com.blazing_durtles.wk.livedata.LiveBurnedItems;
import com.blazing_durtles.wk.livedata.LiveCriticalCondition;
import com.blazing_durtles.wk.livedata.LiveLevelProgress;
import com.blazing_durtles.wk.livedata.LiveSrsBreakDown;
import com.blazing_durtles.wk.livedata.LiveTimeLine;
import com.blazing_durtles.wk.model.Question;
import com.blazing_durtles.wk.proxy.ViewProxy;
import com.blazing_durtles.wk.util.ThemeUtil;
import com.blazing_durtles.wk.views.SwipingScrollView;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Fragment for the session summary.
 */
public final class SummarySessionFragment extends AbstractSessionFragment implements SwipingScrollView.OnSwipeListener {
    private final ViewProxy specialButton1 = new ViewProxy();
    private final ViewProxy specialButton2 = new ViewProxy();
    private final ViewProxy specialButton3 = new ViewProxy();
    private final ViewProxy finishButton = new ViewProxy();
    private final ViewProxy showButton = new ViewProxy();
    private final ViewProxy resurrectIncorrectButton = new ViewProxy();
    private final ViewProxy correctHeader = new ViewProxy();
    private final ViewProxy correctTable = new ViewProxy();
    private final ViewProxy correctSummary = new ViewProxy();
    private final ViewProxy correctPercentage = new ViewProxy();
    private final ViewProxy correctRadicals = new ViewProxy();
    private final ViewProxy correctKanji = new ViewProxy();
    private final ViewProxy correctVocabulary = new ViewProxy();
    private final ViewProxy correctKanaVocabulary = new ViewProxy();
    private final ViewProxy incorrectHeader = new ViewProxy();
    private final ViewProxy incorrectTable = new ViewProxy();
    private final ViewProxy incorrectSummary = new ViewProxy();
    private final ViewProxy incorrectPercentage = new ViewProxy();
    private final ViewProxy incorrectRadicals = new ViewProxy();
    private final ViewProxy incorrectKanji = new ViewProxy();
    private final ViewProxy incorrectVocabulary = new ViewProxy();
    private final ViewProxy incorrectKanaVocabulary = new ViewProxy();
    private final ViewProxy finishProgressBar = new ViewProxy();
    private final ViewProxy incorrectStarSpinner = new ViewProxy();
    private final ViewProxy incorrectStarButton = new ViewProxy();
    private final ViewProxy correctStarSpinner = new ViewProxy();
    private final ViewProxy correctStarButton = new ViewProxy();
    private final ViewProxy scrollView = new ViewProxy();

    /**
     * The constructor.
     */
    public SummarySessionFragment() {
        super(R.layout.fragment_summary);
    }

    @Override
    protected void onCreateLocal() {
        //
    }

    @Override
    protected void onResumeLocal() {
        finishButton.requestFocus();
    }

    @SuppressLint("NewApi")
    private static boolean hasResurrecttableIncorrect() {
        //noinspection ConstantConditions
        return session.getItems().stream()
                .filter(SessionItem::hasIncorrectAnswers)
                .map(SessionItem::getSubject)
                .filter(Objects::nonNull)
                .anyMatch(Subject::isResurrectable);
    }

    @SuppressLint("NewApi")
    private static long[] getResurrectableIds() {
        //noinspection ConstantConditions
        final List<Long> resurrectableSubjectIds = session.getItems().stream()
                .filter(SessionItem::hasIncorrectAnswers)
                .map(SessionItem::getSubject)
                .filter(Objects::nonNull)
                .map(Subject::getId)
                .collect(Collectors.toList());
        final long[] ids = new long[resurrectableSubjectIds.size()];
        for (int i=0; i<ids.length; i++) {
            ids[i] = resurrectableSubjectIds.get(i);
        }
        return ids;
    }

    @Override
    public void onViewCreatedLocal(final View view, final @Nullable Bundle savedInstanceState) {
        specialButton1.setDelegate(view, R.id.specialButton1);
        specialButton2.setDelegate(view, R.id.specialButton2);
        specialButton3.setDelegate(view, R.id.specialButton3);
        finishButton.setDelegate(view, R.id.finishButton);
        showButton.setDelegate(view, R.id.showButton);
        resurrectIncorrectButton.setDelegate(view, R.id.resurrectIncorrectButton);
        correctHeader.setDelegate(view, R.id.correctHeader);
        correctTable.setDelegate(view, R.id.correctTable);
        correctSummary.setDelegate(view, R.id.correctSummary);
        correctPercentage.setDelegate(view, R.id.correctPercentage);
        correctRadicals.setDelegate(view, R.id.correctRadicals);
        correctKanji.setDelegate(view, R.id.correctKanji);
        correctVocabulary.setDelegate(view, R.id.correctVocabulary);
        correctKanaVocabulary.setDelegate(view, R.id.correctKanaVocabulary);
        incorrectHeader.setDelegate(view, R.id.incorrectHeader);
        incorrectTable.setDelegate(view, R.id.incorrectTable);
        incorrectSummary.setDelegate(view, R.id.incorrectSummary);
        incorrectPercentage.setDelegate(view, R.id.incorrectPercentage);
        incorrectRadicals.setDelegate(view, R.id.incorrectRadicals);
        incorrectKanji.setDelegate(view, R.id.incorrectKanji);
        incorrectVocabulary.setDelegate(view, R.id.incorrectVocabulary);
        incorrectKanaVocabulary.setDelegate(view, R.id.incorrectKanaVocabulary);
        finishProgressBar.setDelegate(view, R.id.finishProgressBar);
        incorrectStarSpinner.setDelegate(view, R.id.incorrectStarSpinner);
        incorrectStarButton.setDelegate(view, R.id.incorrectStarButton);
        correctStarSpinner.setDelegate(view, R.id.correctStarSpinner);
        correctStarButton.setDelegate(view, R.id.correctStarButton);

        int numCorrectRadicals = 0;
        int numCorrectKanji = 0;
        int numCorrectVocabulary = 0;
        int numCorrectKanaVocabulary = 0;
        int correctTotal = 0;
        int totalRadical = 0;
        int totalKanji = 0;
        int totalVocabulary = 0;
        int totalKanaVocabulary = 0;
        int total = 0;
        for (final SessionItem item: session.getItems()) {
            if (item.isAbandoned()) {
                continue;
            }
            final int incorrect = item.getQuestion1Incorrect() + item.getQuestion2Incorrect() + item.getQuestion3Incorrect() + item.getQuestion4Incorrect();
            final Subject subject = requireNonNull(item.getSubject());
            if (subject.getType().isRadical()) {
                total++;
                totalRadical++;
                if (incorrect == 0) {
                    numCorrectRadicals++;
                    correctTotal++;
                }
            }
            if (subject.getType().isKanji()) {
                total++;
                totalKanji++;
                if (incorrect == 0) {
                    numCorrectKanji++;
                    correctTotal++;
                }
            }
            if (subject.getType().isVocabulary()) {
                total++;
                totalVocabulary++;
                if (incorrect == 0) {
                    numCorrectVocabulary++;
                    correctTotal++;
                }
            }
            if (subject.getType().isKanaVocabulary()) {
                total++;
                totalKanaVocabulary++;
                if (incorrect == 0) {
                    numCorrectKanaVocabulary++;
                    correctTotal++;
                }
            }
        }
        final float correctFraction = (total == 0) ? 0 : ((float) correctTotal) / total;
        final int totalPercentage = Math.round(correctFraction * 100);

        if (total == 0) {
            correctPercentage.setTextFormat("%d%%", 0);
            incorrectPercentage.setTextFormat("%d%%", 0);
        } else {
            correctPercentage.setTextFormat("%d%%", totalPercentage);
            incorrectPercentage.setTextFormat("%d%%", 100-totalPercentage);
        }
        correctRadicals.setText(numCorrectRadicals);
        correctKanji.setText(numCorrectKanji);
        correctVocabulary.setText(numCorrectVocabulary);
        correctKanaVocabulary.setText(numCorrectKanaVocabulary);

        incorrectRadicals.setText(totalRadical-numCorrectRadicals);
        incorrectKanji.setText(totalKanji-numCorrectKanji);
        incorrectVocabulary.setText(totalVocabulary-numCorrectVocabulary);
        incorrectKanaVocabulary.setText(totalKanaVocabulary-numCorrectKanaVocabulary);

        // Hide correct/incorrect fields (entire TableRow: label and value) if value is 0 or 0%
        View incorrectPercentageRow = (View) view.findViewById(R.id.incorrectPercentage).getParent();
        if (incorrectPercentageRow != null) incorrectPercentageRow.setVisibility((100-totalPercentage) == 0 ? View.GONE : View.VISIBLE);
        View correctPercentageRow = (View) view.findViewById(R.id.correctPercentage).getParent();
        if (correctPercentageRow != null) correctPercentageRow.setVisibility(totalPercentage == 0 ? View.GONE : View.VISIBLE);
        View correctRadicalsRow = (View) view.findViewById(R.id.correctRadicals).getParent();
        if (correctRadicalsRow != null) correctRadicalsRow.setVisibility(numCorrectRadicals == 0 ? View.GONE : View.VISIBLE);
        View correctKanjiRow = (View) view.findViewById(R.id.correctKanji).getParent();
        if (correctKanjiRow != null) correctKanjiRow.setVisibility(numCorrectKanji == 0 ? View.GONE : View.VISIBLE);
        View correctVocabularyRow = (View) view.findViewById(R.id.correctVocabulary).getParent();
        if (correctVocabularyRow != null) correctVocabularyRow.setVisibility(numCorrectVocabulary == 0 ? View.GONE : View.VISIBLE);
        View correctKanaVocabularyRow = (View) view.findViewById(R.id.correctKanaVocabulary).getParent();
        if (correctKanaVocabularyRow != null) correctKanaVocabularyRow.setVisibility(numCorrectKanaVocabulary == 0 ? View.GONE : View.VISIBLE);
        View incorrectRadicalsRow = (View) view.findViewById(R.id.incorrectRadicals).getParent();
        if (incorrectRadicalsRow != null) incorrectRadicalsRow.setVisibility((totalRadical-numCorrectRadicals) == 0 ? View.GONE : View.VISIBLE);
        View incorrectKanjiRow = (View) view.findViewById(R.id.incorrectKanji).getParent();
        if (incorrectKanjiRow != null) incorrectKanjiRow.setVisibility((totalKanji-numCorrectKanji) == 0 ? View.GONE : View.VISIBLE);
        View incorrectVocabularyRow = (View) view.findViewById(R.id.incorrectVocabulary).getParent();
        if (incorrectVocabularyRow != null) incorrectVocabularyRow.setVisibility((totalVocabulary-numCorrectVocabulary) == 0 ? View.GONE : View.VISIBLE);
        View incorrectKanaVocabularyRow = (View) view.findViewById(R.id.incorrectKanaVocabulary).getParent();
        if (incorrectKanaVocabularyRow != null) incorrectKanaVocabularyRow.setVisibility((totalKanaVocabulary-numCorrectKanaVocabulary) == 0 ? View.GONE : View.VISIBLE);

        // Hide/show the blank spacer row below Overall Percentage Correct
        View correctPercentageSpacerRow = view.findViewById(R.id.correctPercentageSpacerRow);
        if (correctPercentageSpacerRow != null) correctPercentageSpacerRow.setVisibility(totalPercentage == 0 ? View.GONE : View.VISIBLE);

        // Hide/show the blank spacer row below Overall Percentage Incorrect
        View incorrectPercentageSpacerRow = view.findViewById(R.id.incorrectPercentageSpacerRow);
        if (incorrectPercentageSpacerRow != null) incorrectPercentageSpacerRow.setVisibility((100-totalPercentage) == 0 ? View.GONE : View.VISIBLE);        // Hide entire summary sections if all values are zero
        boolean allCorrectZero = totalPercentage == 0 && numCorrectRadicals == 0 && numCorrectKanji == 0 && numCorrectVocabulary == 0 && numCorrectKanaVocabulary == 0;
        boolean allIncorrectZero = total == 0 || ((100-totalPercentage) == 0 && (totalRadical-numCorrectRadicals) == 0 && (totalKanji-numCorrectKanji) == 0 && (totalVocabulary-numCorrectVocabulary) == 0 && (totalKanaVocabulary-numCorrectKanaVocabulary) == 0);
        correctSummary.setVisibility(allCorrectZero ? View.GONE : View.VISIBLE);
        incorrectSummary.setVisibility(allIncorrectZero ? View.GONE : View.VISIBLE);


        specialButton1.setVisibility(GlobalSettings.AdvancedOther.getSpecialButton1Behavior().canShow());
        specialButton1.setText(GlobalSettings.AdvancedOther.getSpecialButton1Behavior().getLabel());

        specialButton2.setVisibility(GlobalSettings.AdvancedOther.getSpecialButton2Behavior().canShow());
        specialButton2.setText(GlobalSettings.AdvancedOther.getSpecialButton2Behavior().getLabel());

        specialButton3.setVisibility(GlobalSettings.AdvancedOther.getSpecialButton3Behavior().canShow());
        specialButton3.setText(GlobalSettings.AdvancedOther.getSpecialButton3Behavior().getLabel());

        specialButton1.setOnClickListener(v -> safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            GlobalSettings.AdvancedOther.getSpecialButton1Behavior().perform();
        }));

        specialButton2.setOnClickListener(v -> safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            GlobalSettings.AdvancedOther.getSpecialButton2Behavior().perform();
        }));

        specialButton3.setOnClickListener(v -> safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            GlobalSettings.AdvancedOther.getSpecialButton3Behavior().perform();
        }));

        finishButton.setOnClickListener(this::onFinishClick);

        showButton.setOnClickListener(v -> safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            showItems();
            enableInteraction();
        }));

        if (hasResurrecttableIncorrect()) {
            resurrectIncorrectButton.setVisibility(true);
            resurrectIncorrectButton.setOnClickListener(v -> safe(() -> goToResurrectActivity(getResurrectableIds())));
        }
        else {
            resurrectIncorrectButton.setVisibility(false);
        }

        final String[] names = {"☆ ☆ ☆ ☆ ☆", "★ ☆ ☆ ☆ ☆", "★ ★ ☆ ☆ ☆", "★ ★ ★ ☆ ☆", "★ ★ ★ ★ ☆", "★ ★ ★ ★ ★"};
        final SpinnerAdapter adapter = new ArrayAdapter<>(view.getContext(), R.layout.spinner_item, names);
        incorrectStarSpinner.setAdapter(adapter);
        correctStarSpinner.setAdapter(adapter);

        incorrectStarSpinner.setParentVisibility(false);
        correctStarSpinner.setParentVisibility(false);

        incorrectStarButton.setOnClickListener(v -> safe(() -> {
            final @Nullable Object selection = incorrectStarSpinner.getSelection();
            if (selection instanceof CharSequence) {
                int newNumStars = 0;
                final CharSequence cs = (CharSequence) selection;
                for (int i=0; i<cs.length(); i++) {
                    if (cs.charAt(i) == '★') {
                        newNumStars++;
                    }
                }
                updateIncorrectStars(newNumStars);
            }
        }));

        correctStarButton.setOnClickListener(v -> safe(() -> {
            final @Nullable Object selection = correctStarSpinner.getSelection();
            if (selection instanceof CharSequence) {
                int newNumStars = 0;
                final CharSequence cs = (CharSequence) selection;
                for (int i=0; i<cs.length(); i++) {
                    if (cs.charAt(i) == '★') {
                        newNumStars++;
                    }
                }
                updateCorrectStars(newNumStars);
            }
        }));

        scrollView.setDelegate(view, R.id.scrollView);
        scrollView.setSwipeListener(this);

        // Hide both correct and incorrect tables if no items were completed
        total = 0; // Reset the existing variable instead of redeclaring it
for (final SessionItem item : session.getItems()) {
    if (!item.isAbandoned()) {
        total++;
    }
}
        // Hide both correct and incorrect tables if no items were completed
        if (total == 0) {
            correctTable.setVisibility(View.GONE);
            incorrectTable.setVisibility(View.GONE);
        } else {
            correctTable.setVisibility(View.VISIBLE);
            incorrectTable.setVisibility(View.VISIBLE);
        }        // Ensure incorrectTable is hidden when total is 0
        if (total == 0) {
            incorrectTable.setVisibility(View.GONE);
        }
    }

    @Override
    public String getToolbarTitle() {
        return "Session Summary";
    }

    @Override
    public int getToolbarBackgroundColor() {
        return ThemeUtil.getColor(R.attr.toolbarColorBackground);
    }

    @Override
    public void enableInteraction() {
        safe(() -> {
            finishButton.enableInteraction();
            showButton.enableInteraction();
            specialButton1.enableInteraction();
            specialButton2.enableInteraction();
            specialButton3.enableInteraction();
            interactionEnabled = true;
        });
    }

    @Override
    public void disableInteraction() {
        safe(() -> {
            interactionEnabled = false;
            finishButton.disableInteraction();
            showButton.disableInteraction();
            specialButton1.disableInteraction();
            specialButton2.disableInteraction();
            specialButton3.disableInteraction();
        });
    }

    @Override
    public @Nullable Subject getCurrentSubject() {
        return null;
    }

    @Override
    public @Nullable SessionItem getItem() {
        return null;
    }

    @Override
    public @Nullable Question getQuestion() {
        return null;
    }

    @Override
    public @Nullable Subject getSubject() {
        return null;
    }

    @Override
    public void showOrHideSoftInput() {
        hideSoftInput();
    }

    @Override
    public void updateViews() {
        //
    }

    @Override
    public FragmentTransitionAnimation getAnimation(final AbstractSessionFragment newFragment) {
        return FragmentTransitionAnimation.LTR;
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("SameReturnValue")
    private static @Nullable Void doInBackground(final Consumer<Object[]> publisher) {
        int count = 0;
        for (final SessionItem item: WkApplication.getDatabase().sessionItemDao().getAll()) {
            if (item.isPending()) {
                final ReportSessionItemJob job = new ReportSessionItemJob(
                        item.getId(),
                        item.getAssignmentId(),
                        session.getType(),
                        item.getQuestion1Incorrect(),
                        item.getQuestion2Incorrect() + item.getQuestion3Incorrect() + item.getQuestion4Incorrect(),
                        item.getLastAnswer(),
                        false);
                job.run();
                publisher.accept(new Object[] {++count});
            }
        }

        LiveTimeLine.getInstance().update();
        LiveSrsBreakDown.getInstance().update();
        LiveLevelProgress.getInstance().update();
        LiveCriticalCondition.getInstance().update();
        LiveBurnedItems.getInstance().update();
        LiveAlertContext.getInstance().update();

        return null;
    }

    private void finishSession() {
        if (!session.isDelayed()) {
            session.finish();
            return;
        }

        final int total = session.getNumPendingItems();
        finishProgressBar.setMax(total);
        finishProgressBar.setProgress(0);
        finishProgressBar.setVisibility(View.VISIBLE);

        runAsyncWithProgress(
                this,
                SummarySessionFragment::doInBackground,
                values -> {
                    int progress = (int) values[0];
                    if (progress < 0) {
                        progress = 0;
                    }
                    if (progress > total) {
                        progress = total;
                    }
                    finishProgressBar.setProgress(progress);
                }, result -> {
                    finishProgressBar.setVisibility(false);
                    session.finish();
                });
    }

    @SuppressLint("NewApi")
    private void showItems() {
        showButton.setVisibility(false);
        incorrectSummary.setVisibility(false);
        correctSummary.setVisibility(false);
        incorrectHeader.setVisibility(true);
        correctHeader.setVisibility(true);

        final boolean srsRelevant = session.getType().isSrsRelevant();

        Comparator<SessionItem> incorrectComparator = Comparator.comparingInt(item -> requireNonNull(item.getSubject()).getTypeOrder());
        //noinspection IfMayBeConditional
        if (srsRelevant) {
            incorrectComparator = incorrectComparator.thenComparing(SessionItem::getNewSrsStage);
        }
        else {
            incorrectComparator = incorrectComparator.thenComparing(item -> requireNonNull(item.getSubject()).getSrsStage());
        }
        incorrectComparator = incorrectComparator
                .thenComparingInt(item -> requireNonNull(item.getSubject()).getLevel())
                .thenComparingInt(item -> requireNonNull(item.getSubject()).getLessonPosition())
                .thenComparingLong(item -> requireNonNull(item.getSubject()).getId());

        final List<Subject> incorrectSubjects = session.getItems().stream()
                .filter(item -> !item.isAbandoned())
                .filter(SessionItem::hasIncorrectAnswers)
                .sorted(incorrectComparator)
                .map(SessionItem::getSubject)
                .collect(Collectors.toList());

        final Comparator<Subject> correctComparator =
                Comparator.comparingInt(Subject::getTypeOrder)
                .thenComparingInt(Subject::getLevel)
                .thenComparingInt(Subject::getLessonPosition)
                .thenComparingLong(Subject::getId);

        final List<Subject> correctSubjects = session.getItems().stream()
                .filter(item -> !item.isAbandoned())
                .filter(item -> !item.hasIncorrectAnswers())
                .map(SessionItem::getSubject)
                .sorted(correctComparator)
                .collect(Collectors.toList());

        incorrectTable.setVisibility(View.VISIBLE);
        incorrectTable.setSubjects(this, incorrectSubjects, true, true);

        correctTable.setVisibility(View.VISIBLE);
        correctTable.setSubjects(this, correctSubjects, true, true);

        // Hide both correct and incorrect tables if no items were completed
        int total = 0;
        for (final SessionItem item : session.getItems()) {
            if (!item.isAbandoned()) {
                total++;
            }
        }
        // Hide both correct and incorrect tables if no items were completed
        if (total == 0) {
            correctTable.setVisibility(View.GONE);
            incorrectTable.setVisibility(View.GONE);
        } else {
            correctTable.setVisibility(View.VISIBLE);
            incorrectTable.setVisibility(View.VISIBLE);
        }

        // Ensure incorrectTable is hidden when total is 0
        if (total == 0) {
            incorrectTable.setVisibility(View.GONE);        }

        incorrectStarSpinner.setParentVisibility(GlobalSettings.Other.getEnableStarRatings());
        correctStarSpinner.setParentVisibility(GlobalSettings.Other.getEnableStarRatings());
    }

    @SuppressLint("NewApi")
    private void updateIncorrectStars(final int newNumStars) {
        final List<Subject> subjects = session.getItems().stream()
                .filter(SessionItem::hasIncorrectAnswers)
                .map(SessionItem::getSubject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final String message = String.format(Locale.ROOT, "Do you want to set the star rating for %d subject%s to %d star%s?",
                subjects.size(), subjects.size() == 1 ? "" : "s", newNumStars, newNumStars == 1 ? "" : "s");

        new AlertDialog.Builder(requireContext())
                .setTitle("Set Star Ratings?")
                .setMessage(message)
                .setNegativeButton("No", (dialog, which) -> {})
                .setPositiveButton("Yes", (dialog, which) -> safe(() -> runAsync(getActivity(), () -> {
                    final AppDatabase db = WkApplication.getDatabase();
                    subjects.forEach(subject -> db.subjectDao().updateStars(subject.getId(), newNumStars));
                    return null;
                }, result -> Toast.makeText(requireContext(), "Star Ratings Updated", Toast.LENGTH_SHORT).show())))
                .create().show();
    }

    @SuppressLint("NewApi")
    private void updateCorrectStars(final int newNumStars) {
        final List<Subject> subjects = session.getItems().stream()
                .filter(item -> !item.hasIncorrectAnswers())
                .map(SessionItem::getSubject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        final String message = String.format(Locale.ROOT, "Do you want to set the star rating for %d subject%s to %d star%s?",
                subjects.size(), subjects.size() == 1 ? "" : "s", newNumStars, newNumStars == 1 ? "" : "s");

        new AlertDialog.Builder(requireContext())
                .setTitle("Set Star Ratings?")
                .setMessage(message)
                .setNegativeButton("No", (dialog, which) -> {})
                .setPositiveButton("Yes", (dialog, which) -> safe(() -> runAsync(getActivity(), () -> {
                    final AppDatabase db = WkApplication.getDatabase();
                    subjects.forEach(subject -> db.subjectDao().updateStars(subject.getId(), newNumStars));
                    return null;
                }, result -> Toast.makeText(requireContext(), "Star Ratings Updated", Toast.LENGTH_SHORT).show())))
                .create().show();
    }

    @Override
    public void onSwipeLeft(SwipingScrollView view) {
        // Do nothing (match behavior in AnsweredSessionFragment)
    }

    @Override
    public void onSwipeRight(SwipingScrollView view) {
        onFinishClick(view);
    }

    private void onFinishClick(View v) {
        safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            finishSession();
        });
    }
}
