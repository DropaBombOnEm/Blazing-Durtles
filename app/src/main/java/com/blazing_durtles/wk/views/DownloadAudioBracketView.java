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

package com.blazing_durtles.wk.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.blazing_durtles.wk.R;
import com.blazing_durtles.wk.db.model.AudioDownloadStatus;
import com.blazing_durtles.wk.jobs.StartAudioDownloadJob;
import com.blazing_durtles.wk.livedata.LiveTaskCounts;
import com.blazing_durtles.wk.proxy.ViewProxy;
import com.blazing_durtles.wk.services.JobRunnerService;

import java.util.Locale;

import javax.annotation.Nullable;

import static com.blazing_durtles.wk.util.ObjectSupport.safe;

/**
 * A custom view that shows a bracket of levels in the audio download overview.
 */
public final class DownloadAudioBracketView extends LinearLayout {
    private final ViewProxy label = new ViewProxy();
    private final ViewProxy rangeLabel = new ViewProxy();
    private final ViewProxy downloadButton = new ViewProxy();
    private boolean isDownloading = false; // Track if this bracket is currently downloading

    /**
     * The constructor.
     *
     * @param context Android context
     */
    public DownloadAudioBracketView(final Context context) {
        super(context);
        init();
    }

    /**
     * The constructor.
     *
     * @param context Android context
     * @param attrs attribute set
     */
    public DownloadAudioBracketView(final Context context, final @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * The constructor.
     *
     * @param context Android context
     * @param attrs attribute set
     * @param defStyleAttr the default style
     */
    public DownloadAudioBracketView(final Context context, final @Nullable AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Initialize the view.
     */
    private void init() {
        safe(() -> {
            inflate(getContext(), R.layout.download_audio_bracket, this);
            setOrientation(VERTICAL);
            setPadding(0, 0, 0, 0);
            label.setDelegate(this, R.id.label);
            rangeLabel.setDelegate(this, R.id.rangeLabel);
            downloadButton.setDelegate(this, R.id.downloadButton);
        });
    }

    /**
     * Set the bracket for this view.
     *
     * @param overview the overall overview
     * @param minLevel the lowest level in the bracket
     * @param maxLevel the highest level in the bracket
     */
    public void setBracket(final Iterable<AudioDownloadStatus> overview, final int minLevel, final int maxLevel) {
        setBracket(overview, minLevel, maxLevel, false);
    }

    /**
     * Set the bracket for this view, with an option to check the database.
     *
     * @param overview the overall overview
     * @param minLevel the lowest level in the bracket
     * @param maxLevel the highest level in the bracket
     * @param isChecking whether the database is being checked
     */
    public void setBracket(final Iterable<AudioDownloadStatus> overview, final int minLevel, final int maxLevel, final boolean isChecking) {
        safe(() -> {
            // Show 'Scanning Database...' and 'CHECKING...' only if isChecking is true or overview is empty
            if (isChecking || !overview.iterator().hasNext()) {
                label.setText("Scanning Database...");
                downloadButton.setText("CHECKING...");
                downloadButton.disableInteraction();
                downloadButton.setOnClickListener(null);
                isDownloading = false;
                return;
            }

            int numTotal = 0;
            int numNoAudio = 0;
            int numMissingAudio = 0;
            int numPartialAudio = 0;
            int numFullAudio = 0;

            // Only count audio for this bracket's level range
            for (final AudioDownloadStatus status: overview) {
                if (status.getLevel() < minLevel || status.getLevel() > maxLevel) {
                    continue;
                }
                numTotal += status.getNumTotal();
                numNoAudio += status.getNumNoAudio();
                numMissingAudio += status.getNumMissingAudio();
                numPartialAudio += status.getNumPartialAudio();
                numFullAudio += status.getNumFullAudio();
            }

            rangeLabel.setTextFormat("Levels %d-%d", minLevel, maxLevel);

            if (numMissingAudio == 0 && numPartialAudio == 0) {
                label.setText("Download Finished");
            }
            else {
                label.setTextFormat("%d / %d Completed, %d Partially Done",
                        numFullAudio,
                        numTotal - numNoAudio,
                        numPartialAudio);
            }

            // Only update button state/text if this bracket's data is valid
            if (numTotal == 0) {
                label.setText("Scanning Database...");
                downloadButton.setText("CHECKING...");
                downloadButton.disableInteraction();
                downloadButton.setOnClickListener(null);
                isDownloading = false;
            } else if (numMissingAudio == 0 && numPartialAudio == 0) {
                label.setText("Download Finished");
                downloadButton.setText("Completed");
                downloadButton.disableInteraction();
                downloadButton.setOnClickListener(null);
                isDownloading = false;
            } else if (isDownloading) {
                downloadButton.setText("Downloading...");
                downloadButton.disableInteraction();
                downloadButton.setOnClickListener(null);
            } else {
                downloadButton.setText("Download");
                downloadButton.enableInteraction();
                downloadButton.setOnClickListener(v -> safe(() -> {
                    isDownloading = true;
                    downloadButton.setText("Downloading...");
                    downloadButton.disableInteraction();
                    JobRunnerService.schedule(StartAudioDownloadJob.class,
                        String.format(Locale.ROOT, "%d|%d", minLevel, maxLevel));
                }));
            }
        });
    }
}
