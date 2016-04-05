package com.example.android.sunshine.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;

public class BitmapFromAssetTask extends AsyncTask<Asset, Void, Bitmap> {
    private static final String LOG_TAG = BitmapFromAssetTask.class.getSimpleName();

    private MyWatchFace myWatchFace;
    private GoogleApiClient mGoogleApiClient;

    public BitmapFromAssetTask(MyWatchFace myWatchFace, GoogleApiClient mGoogleApiClient) {
        this.myWatchFace = myWatchFace;
        this.mGoogleApiClient = mGoogleApiClient;
    }

    @Override
    protected Bitmap doInBackground(Asset[] assets) {
        Asset asset = assets[0];

        if (asset != null) {

            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                    mGoogleApiClient, asset).await().getInputStream();

            if (assetInputStream == null) {
                Log.w(LOG_TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (result != null) {
            myWatchFace.setWeatherConditionBitmap(result);
        }
    }
}
