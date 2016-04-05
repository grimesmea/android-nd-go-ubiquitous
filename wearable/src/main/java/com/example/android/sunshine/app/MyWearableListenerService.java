/*
package com.example.android.sunshine.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class MyWearableListenerService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = "WearableListenerService";
    private static final long TIMEOUT_MS = 500;

    private static final String WEATHER_DATA_PATH = "/weather-data";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";
    private static final String CONDITION_ASSET_KEY = "com.example.key.conditionAsset";
    private static final String HIGH_TEMP_KEY = "com.example.key.highTemp";
    private static final String LOW_TEMP_KEY = "com.example.key.lowTemp";

    private MyWatchFace myWatchFace;
    private Context context;
    private GoogleApiClient mGoogleApiClient;

    private static Bitmap weatherConditionBitmap = null;
    private static String highTemp = "";
    private static String lowTemp = "";

    public MyWearableListenerService() { }

    public MyWearableListenerService(MyWatchFace myWatchFace) {
        this.myWatchFace = myWatchFace;
        Log.d(LOG_TAG, "service created");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onDataChanged: " + dataEvents);
        }

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(myWatchFace)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }
        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult =
                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e(LOG_TAG, "Failed to connect to GoogleApiClient.");
                return;
            }
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : dataEvents) {
            Uri uri = event.getDataItem().getUri();

            if (uri.getPath().equals(WEATHER_DATA_PATH)) {
                Log.d(LOG_TAG, "Received weather data on wearable");

                DataMap weatherData = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                Asset weatherConditionAsset = weatherData.getAsset(CONDITION_ASSET_KEY);
                weatherConditionBitmap = loadBitmapFromAsset(weatherConditionAsset);
                highTemp = weatherData.getString(HIGH_TEMP_KEY);
                lowTemp = weatherData.getString(LOW_TEMP_KEY);

                //updateWatchFaceWeatherData();
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

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(LOG_TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }

    */
/*public void updateWatchFaceWeatherData() {
        myWatchFace.setWeatherConditionBitmap(weatherConditionBitmap);
        myWatchFace.setHighTemp(highTemp);
        myWatchFace.setLowTemp(lowTemp);
    }*//*


    @Override
    public void onConnected(Bundle connectionHint) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onConnected: " + connectionHint);
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onConnectionSuspended: " + cause);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "onConnectionFailed: " + connectionResult);
        }
    }
}
*/
