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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

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

    Bitmap weatherConditionBitmap;
    String highTemp;
    String lowTemp;
    Long dataTimestamp;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    public void setWeatherConditionBitmap(Bitmap weatherConditionBitmap) {
        this.weatherConditionBitmap = Bitmap.createScaledBitmap(weatherConditionBitmap, 75, 75, false);
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

    private class Engine extends CanvasWatchFaceService.Engine
            implements DataApi.DataListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        private static final String LOG_TAG = "MyWatchFace";
        private static final float HR_WIDTH = 1f;

        private static final String WEATHER_DATA_PATH = "/weather-data";
        private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";
        private static final String CONDITION_ASSET_KEY = "com.example.key.conditionAsset";
        private static final String HIGH_TEMP_KEY = "com.example.key.highTemp";
        private static final String LOW_TEMP_KEY = "com.example.key.lowTemp";
        private static final String TIMESTAMP_KEY = "com.example.key.timestamp";

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

        Date currentTime;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

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
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                    .setShowSystemUiTime(false)
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

            weatherConditionBitmap = null;
            highTemp = "";
            lowTemp = "";
            dataTimestamp = null;


            Log.d(LOG_TAG, "Connecting in onCreate");
            mGoogleApiClient.connect();
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
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.getInstance();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
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
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHoursPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : Typeface.DEFAULT_BOLD);
            mHighTempPaint.setTypeface(burnInProtection ? NORMAL_TYPEFACE : Typeface.DEFAULT_BOLD);

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

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            float boundsWidth = bounds.width();
            float boundsHeight = bounds.height();

            float horizontalRuleLength = boundsWidth / 6;
            float horizontalRuleYOffset = (boundsHeight / 5) * 3;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, boundsWidth, boundsHeight, mBackgroundPaint);
            }

            // Draw HH:MM, date, horizontal rule, and temperature in both interactive and ambient
            // mode.
            mCalendar = Calendar.getInstance();
            currentTime = mCalendar.getTime();

            String hour = hoursFormat.format(currentTime);
            canvas.drawText(hour, boundsWidth / 2, (boundsHeight / 5) * 2,
                    mHoursPaint);

            String minutes = minutesFormat.format(currentTime);
            canvas.drawText(minutes, boundsWidth / 2, (boundsHeight / 5) * 2,
                    mMinutesPaint);

            String date = dateFormat.format(currentTime).toUpperCase();
            canvas.drawText(date, boundsWidth / 2, (boundsHeight / 11) * 6,
                    mDatePaint);

            // Reset temps values and icon bitmap if data is from a day that is not today.
            if (dataTimestamp != null) {
                String timestampDate = dateFormat.format(dataTimestamp).toUpperCase();
                if (!date.equals(timestampDate)) {
                    weatherConditionBitmap = null;
                    highTemp = "";
                    lowTemp = "";
                }
            }

            // Draw weather data if in interactive mode
            if (!isInAmbientMode()) {
                canvas.drawLine(
                        horizontalRuleLength * 2, horizontalRuleYOffset,
                        horizontalRuleLength * 4, horizontalRuleYOffset,
                        mHorizontalRulePaint);

                if (weatherConditionBitmap != null) {
                    canvas.drawBitmap(weatherConditionBitmap,
                            (boundsWidth / 10), (boundsHeight / 5) * 3, mWeatherIconPaint);
                }

                canvas.drawText(highTemp, boundsWidth / 2, (boundsHeight / 5) * 4,
                        mHighTempPaint);
                canvas.drawText(lowTemp, (boundsWidth / 9) * 7, (boundsHeight / 5) * 4,
                        mLowTempPaint);
            }
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

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "onDataChanged: " + dataEvents);
            }

            // Loop through the events and send a message
            // to the node that created the data item.
            for (DataEvent event : dataEvents) {
                Uri uri = event.getDataItem().getUri();

                if (uri.getPath().equals(WEATHER_DATA_PATH)) {
                    Log.d(LOG_TAG, "Received weather data on wearable");

                    DataMap weatherData = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                    Asset weatherConditionAsset = weatherData.getAsset(CONDITION_ASSET_KEY);
                    BitmapFromAssetTask getBitmapFromAssetTask =
                            new BitmapFromAssetTask(MyWatchFace.this, mGoogleApiClient);
                    getBitmapFromAssetTask.execute(weatherConditionAsset);

                    highTemp = weatherData.getString(HIGH_TEMP_KEY);
                    lowTemp = weatherData.getString(LOW_TEMP_KEY);

                    dataTimestamp = weatherData.getLong(TIMESTAMP_KEY);
                }

                // Get the node id from the host value of the URI
                String nodeId = uri.getHost();
                // Set the data of the message to be the bytes of the URI
                byte[] payload = uri.toString().getBytes();

                // Send the RPC
                Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId,
                        DATA_ITEM_RECEIVED_PATH, payload);
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "onConnected: " + connectionHint);
            }
            Log.d(LOG_TAG, "Connected.");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "onConnectionSuspended: " + cause);
            }
            Log.d(LOG_TAG, "Connection suspended.");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
                Log.d(LOG_TAG, "onConnectionFailed: " + connectionResult);
            }
            Log.e(LOG_TAG, "Connection failed.");
        }
    }
}
