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

package com.smouldering_durtles.wk.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.lifecycle.LifecycleOwner;

import com.smouldering_durtles.wk.GlobalSettings;
import com.smouldering_durtles.wk.R;
import com.smouldering_durtles.wk.livedata.LiveTimeLine;
import com.smouldering_durtles.wk.livedata.LiveVacationMode;
import com.smouldering_durtles.wk.model.TimeLine;

import java.util.Locale;

import javax.annotation.Nullable;

import static com.smouldering_durtles.wk.Constants.DAY;
import static com.smouldering_durtles.wk.util.ObjectSupport.getWaitTimeAsInformalString;
import static com.smouldering_durtles.wk.util.ObjectSupport.safe;

/**
 * A custom view that describes the number of upcoming reviews and when they happen.
 */
public final class UpcomingReviewsView extends AppCompatTextView {
    /**
     * The constructor.
     *
     * @param context Android context
     */
    public UpcomingReviewsView(final Context context) {
        super(context, null, R.attr.WK_TextView_Normal);
    }

    /**
     * The constructor.
     *
     * @param context Android context
     * @param attrs attribute set
     */
    public UpcomingReviewsView(final Context context, final AttributeSet attrs) {
        super(context, attrs, R.attr.WK_TextView_Normal);
    }

    /**
     * Set the lifecycle owner for this view, to hook LiveData observers to.
     *
     * @param lifecycleOwner the lifecycle owner
     */
    public void setLifecycleOwner(final LifecycleOwner lifecycleOwner) {
        safe(() -> LiveTimeLine.getInstance().observe(lifecycleOwner, t -> safe(() -> {
            if (t != null) {
                update(t);
            }
        })));
    }

    /**
     * Update the text based on the latest TimeLine data available.
     *
     * @param timeLine the timeline
     */
    private void update(final TimeLine timeLine) {
        if (GlobalSettings.getFirstTimeSetup() == 0) {
            setVisibility(GONE);
            return;
        }

        if (LiveVacationMode.getInstance().get()) {
            setText("Vacation mode is active");
            setVisibility(VISIBLE);
            return;
        }

        final boolean hasAvailableReviews = timeLine.hasAvailableReviews();
        final boolean hasUpcomingReviews = timeLine.hasUpcomingReviews();
        final boolean hasLongTermUpcomingReviews = timeLine.hasLongTermUpcomingReviews();
        final @Nullable String text;

        if (hasAvailableReviews) {
            if (hasUpcomingReviews) {
                if (timeLine.getSize() <= 48) {
                    text = String.format(Locale.ROOT, "+%d Over the Next %dhrs",
                        timeLine.getNumUpcomingReviews(), timeLine.getSize());
                } else {
                    text = String.format(Locale.ROOT, "+%d in the Next %d Days",
                        timeLine.getNumUpcomingReviews(), timeLine.getSize() / 24);
                }
            }
            else {
                if (hasLongTermUpcomingReviews) {
                    final long waitTime = timeLine.getLongTermUpcomingReviewDate() - System.currentTimeMillis();
                    final float waitTimeDays = (waitTime * 1.0f) / DAY;
                    text = String.format(Locale.ROOT, "+%d in the Next %1.1f Days",
                        timeLine.getNumLongTermUpcomingReviews(), waitTimeDays);
                }
                else {
                    text = null;
                }
            }
        }
        else {
            if (hasUpcomingReviews) {
                if (timeLine.getSize() <= 48) {
                    text = String.format(Locale.ROOT, "+%d Over the Next %dhrs",
                        timeLine.getNumUpcomingReviews(), timeLine.getSize());
                } else {
                    text = String.format(Locale.ROOT, "+%d in the Next %d Days",
                        timeLine.getNumUpcomingReviews(), timeLine.getSize() / 24);
                }
            }
            else {
                if (hasLongTermUpcomingReviews) {
                    final long waitTime = timeLine.getLongTermUpcomingReviewDate() - System.currentTimeMillis();
                    final float waitTimeDays = (waitTime * 1.0f) / DAY;
                    text = String.format(Locale.ROOT, "+%d in the Next %1.1f Days",
                        timeLine.getNumLongTermUpcomingReviews(), waitTimeDays);
                }
                else {
                    text = null;
                }
            }
        }

        if (text == null) {
            setVisibility(View.GONE);
            return;
        }

        setText(text);
        setVisibility(View.VISIBLE);
    }
}
