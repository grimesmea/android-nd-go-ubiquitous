/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grimesmea.gmail.com.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HR_WIDTH = 1f;

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mHoursPaint;
        Paint mMinutesPaint;
        Paint mDatePaint;
        Paint mHorizontalRulePaint;
        Paint mWeatherIconPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;

        boolean mAmbient;

        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
            }
        };
        SimpleDateFormat hoursFormat;
        SimpleDateFormat minutesFormat;
        SimpleDateFormat dateFormat;
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.background2));

            mHoursPaint = createDefaultTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text));
            mHoursPaint.setTextAlign(Paint.Align.RIGHT);

            mMinutesPaint = createDefaultTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text));
            mMinutesPaint.setTextAlign(Paint.Align.LEFT);

            mDatePaint = createDefaultTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text));
            mDatePaint.setTextAlign(Paint.Align.CENTER);

            mHorizontalRulePaint = new Paint();
            mHorizontalRulePaint.setColor(ContextCompat.getColor(getBaseContext(), R.color.digital_text));
            mHorizontalRulePaint.setStrokeWidth(HR_WIDTH);
            mHorizontalRulePaint.setAntiAlias(true);

            mWeatherIconPaint = new Paint();
            mWeatherIconPaint.setAntiAlias(true);

            mHighTempPaint = createDefaultTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text));
            mHighTempPaint.setTextAlign(Paint.Align.CENTER);

            mLowTempPaint = createDefaultTextPaint(ContextCompat.getColor(getBaseContext(), R.color.digital_text));
            mLowTempPaint.setTextAlign(Paint.Align.CENTER);

            mCalendar = Calendar.getInstance(TimeZone.getDefault());
            hoursFormat = new SimpleDateFormat("hh", Locale.getDefault());
            minutesFormat = new SimpleDateFormat(":mm", Locale.getDefault());
            dateFormat = new SimpleDateFormat("E, MMM d yyyy", Locale.getDefault());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createDefaultTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.getInstance();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.large_digital_text_size_round : R.dimen.large_digital_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.small_digital_text_size_round : R.dimen.small_digital_text_size);
            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.medium_digital_text_size_round : R.dimen.medium_digital_text_size);

            mHoursPaint.setTextSize(timeTextSize);
            mHoursPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mMinutesPaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mHighTempPaint.setTextSize(tempTextSize);
            mHighTempPaint.setTypeface(Typeface.DEFAULT_BOLD);
            mLowTempPaint.setTextSize(tempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHoursPaint.setAntiAlias(!inAmbientMode);
                    mMinutesPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mHorizontalRulePaint.setAntiAlias(!inAmbientMode);
                    mWeatherIconPaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                }

                if (mAmbient) {
                    mHoursPaint.setTypeface(NORMAL_TYPEFACE);
                    mHighTempPaint.setTypeface(NORMAL_TYPEFACE);
                } else {
                    mHoursPaint.setTypeface(Typeface.DEFAULT_BOLD);
                    mHighTempPaint.setTypeface(Typeface.DEFAULT_BOLD);
                }

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            float horizontalRuleLength = canvas.getWidth() / 6;
            float horizontalRuleYOffset = (canvas.getHeight() / 5) * 3;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw HH:MM, date, horizontal rule, and temperature in both interactive and ambient
            // mode.
            mCalendar = Calendar.getInstance();
            Date currentTime = mCalendar.getTime();

            String hour = hoursFormat.format(currentTime);
            canvas.drawText(hour, canvas.getWidth() / 2, (canvas.getHeight() / 5) * 2,
                    mHoursPaint);

            String minutes = minutesFormat.format(currentTime);
            canvas.drawText(minutes, canvas.getWidth() / 2, (canvas.getHeight() / 5) * 2,
                    mMinutesPaint);

            String date = dateFormat.format(currentTime).toUpperCase();
            canvas.drawText(date, canvas.getWidth() / 2, (canvas.getHeight() / 11) * 6,
                    mDatePaint);

            canvas.drawLine(
                    horizontalRuleLength * 2, horizontalRuleYOffset,
                    horizontalRuleLength * 4, horizontalRuleYOffset,
                    mHorizontalRulePaint);

            String highTemp = 25 + "\u00B0";
            canvas.drawText(highTemp, canvas.getWidth() / 2, (canvas.getHeight() / 5) * 4,
                    mHighTempPaint);

            String lowTemp = 0 + "\u00B0";
            canvas.drawText(lowTemp, (canvas.getWidth() / 5) * 4, (canvas.getHeight() / 5) * 4,
                    mLowTempPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
