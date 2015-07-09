package com.vshkl.weatherar.views;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.RelativeLayout;

import com.qualcomm.vuforia.CameraDevice;
import com.qualcomm.vuforia.DataSet;
import com.qualcomm.vuforia.ImageTargetBuilder;
import com.qualcomm.vuforia.ObjectTracker;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Trackable;
import com.qualcomm.vuforia.Tracker;
import com.qualcomm.vuforia.TrackerManager;
import com.qualcomm.vuforia.Vuforia;
import com.vshkl.weatherar.R;
import com.vshkl.weatherar.application.*;
import com.vshkl.weatherar.render.RefFreeFrame;
import com.vshkl.weatherar.render.CameraRenderer;
import com.vshkl.weatherar.utils.ApplicationGLView;
import com.vshkl.weatherar.utils.Texture;

import org.androidannotations.annotations.EActivity;

import java.util.Vector;

@EActivity(R.layout.activity_camera)
public class CameraActivity extends AppCompatActivity implements Control {

    private static final String LOGTAG = "CameraActivity";

    private static final int LENGTH = 1024;
    private static final int HEIGHT = 1024;

    private Session session;
    private ApplicationGLView applicationGLView;
    private CameraRenderer cameraRenderer;

    private Vector<Texture> textures;

    private RelativeLayout relativeLayout;
    private View bottomBar;
    private View cameraButton;

    int targetBuilderCounter = 1;

    DataSet dataSetUserDef = null;

    private boolean isExtendedTracking = false;

    public RefFreeFrame refFreeFrame;

    boolean isDroidDevice = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getIntent().getExtras();
        String weatherStr = bundle.getString("Forecast");
        Log.v("WEATHER IN CAMERA", weatherStr);

        session = new Session(this);
        session.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        textures = new Vector<>();
        loadTextures(weatherStr);

        isDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");
    }

    private void loadTextures(String text) {
        Bitmap bitmap = Bitmap.createBitmap(LENGTH, HEIGHT, Bitmap.Config.ARGB_8888);
        textures.add(Texture.loadTextureFromBitmap(createBitmapText(bitmap, text)));
        bitmap.recycle();
    }

    @Override
    protected void onResume() {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        if (isDroidDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        try {
            session.resumeAR();
        } catch (ExceptionAR e) {
            Log.e(LOGTAG, e.getString());
        }

        if (applicationGLView != null) {
            applicationGLView.setVisibility(View.VISIBLE);
            applicationGLView.onResume();
        }

    }

    @Override
    protected void onPause() {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (applicationGLView != null) {
            applicationGLView.setVisibility(View.INVISIBLE);
            applicationGLView.onPause();
        }

        try {
            session.pauseAR();
        } catch (ExceptionAR e) {
            Log.e(LOGTAG, e.getString());
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        try {
            session.stopAR();
        } catch (ExceptionAR e) {
            Log.e(LOGTAG, e.getString());
        }

        textures.clear();
        textures = null;

        System.gc();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        session.onConfigurationChanged();

        if (relativeLayout != null) {
            relativeLayout.removeAllViews();
            ((ViewGroup) relativeLayout.getParent()).removeView(relativeLayout);
        }

        addOverlayView(false);
    }

    private void initApplicationAR() {
        refFreeFrame = new RefFreeFrame(this, session);
        refFreeFrame.init();

        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        applicationGLView = new ApplicationGLView(this);
        applicationGLView.init(translucent, depthSize, stencilSize);

        cameraRenderer = new CameraRenderer(this, session);
        cameraRenderer.setTextures(textures);
        applicationGLView.setRenderer(cameraRenderer);
        addOverlayView(true);

    }

    private void addOverlayView(boolean initLayout) {
        LayoutInflater inflater = LayoutInflater.from(this);
        relativeLayout = (RelativeLayout) inflater.inflate(R.layout.camera_overlay, null, false);

        relativeLayout.setVisibility(View.VISIBLE);

        if (initLayout) {
            relativeLayout.setBackgroundColor(Color.BLACK);
        }

        addContentView(relativeLayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        bottomBar = relativeLayout.findViewById(R.id.bottom_bar);
        cameraButton = relativeLayout.findViewById(R.id.camera_button);

        startUserDefinedTargets();
        initializeBuildTargetModeViews();

        relativeLayout.bringToFront();
    }

    public void onCameraClick(View v) {
        if (isUserDefinedTargetsRunning()) {
            startBuild();
        }
    }

    public Texture createTexture(String nName) {
        return Texture.loadTextureFromApk(nName, getAssets());
    }

    public void targetCreated() {
        if (refFreeFrame != null) {
            refFreeFrame.reset();
        }
    }

    private void initializeBuildTargetModeViews() {
        bottomBar.setVisibility(View.VISIBLE);
        cameraButton.setVisibility(View.VISIBLE);
    }

    boolean startUserDefinedTargets()
    {
        Log.d(LOGTAG, "startUserDefinedTargets");

        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
                .getTracker(ObjectTracker.getClassType()));

        if (objectTracker != null) {
            ImageTargetBuilder targetBuilder = objectTracker.getImageTargetBuilder();

            if (targetBuilder != null) {
                if (targetBuilder.getFrameQuality()
                        != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE) {
                    targetBuilder.stopScan();
                }
                objectTracker.stop();
                targetBuilder.startScan();
            }
        } else {
            return false;
        }

        return true;
    }

    boolean isUserDefinedTargetsRunning() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());

        if (objectTracker != null)
        {
            ImageTargetBuilder targetBuilder = objectTracker.getImageTargetBuilder();
            if (targetBuilder != null)
            {
                Log.e(LOGTAG, "Quality> " + targetBuilder.getFrameQuality());
                return (targetBuilder.getFrameQuality()
                        != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE) ? true : false;
            }
        }

        return false;
    }

    void startBuild() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());

        if (objectTracker != null) {
            ImageTargetBuilder targetBuilder = objectTracker.getImageTargetBuilder();
            if (targetBuilder != null) {
                String name;
                do {
                    name = "UserTarget-" + targetBuilderCounter;
                    Log.d(LOGTAG, "TRYING " + name);
                    targetBuilderCounter++;
                } while (!targetBuilder.build(name, 320.0f));

                refFreeFrame.setCreating();
            }
        }
    }

    public void updateRendering() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        refFreeFrame.initGL(metrics.widthPixels, metrics.heightPixels);
    }

    @Override
    public boolean doInitTrackers() {
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();
        Tracker tracker = trackerManager.initTracker(ObjectTracker.getClassType());

        if (tracker == null) {
            Log.d(LOGTAG, "Failed to initialize ObjectTracker.");
            result = false;
        } else {
            Log.d(LOGTAG, "Successfully initialized ObjectTracker.");
        }

        return result;
    }

    @Override
    public boolean doLoadTrackersData() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());

        if (objectTracker == null) {
            Log.d(LOGTAG, "Failed to load tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }

        dataSetUserDef = objectTracker.createDataSet();
        if (dataSetUserDef == null) {
            Log.d(LOGTAG, "Failed to create a new tracking data.");
            return false;
        }

        if (!objectTracker.activateDataSet(dataSetUserDef)) {
            Log.d(LOGTAG, "Failed to activate data set.");
            return false;
        }

        Log.d(LOGTAG, "Successfully loaded and activated data set.");
        return true;
    }

    @Override
    public boolean doStartTrackers() {
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (objectTracker != null) {
            objectTracker.start();
        }

        return result;
    }

    @Override
    public boolean doStopTrackers() {
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(ObjectTracker.getClassType());
        if (objectTracker != null) {
            objectTracker.stop();
        }

        return result;
    }


    @Override
    public boolean doUnloadTrackersData() {
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            result = false;
            Log.d(LOGTAG, "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.");
        }

        if (dataSetUserDef != null) {
            if (objectTracker.getActiveDataSet() != null
                    && !objectTracker.deactivateDataSet(dataSetUserDef)) {
                Log.d(LOGTAG, "Failed to destroy the tracking data set because the data set could not be deactivated.");
                result = false;
            }

            if (!objectTracker.destroyDataSet(dataSetUserDef)) {
                Log.d(LOGTAG, "Failed to destroy the tracking data set.");
                result = false;
            }

            Log.d(LOGTAG, "Successfully destroyed the data set.");
            dataSetUserDef = null;
        }

        return result;
    }


    @Override
    public boolean doDeinitTrackers() {
        boolean result = true;

        if (refFreeFrame != null) {
            refFreeFrame.deInit();
        }

        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ObjectTracker.getClassType());

        return result;
    }

    @Override
    public void onInitARDone(ExceptionAR exception) {

        if (exception == null) {
            initApplicationAR();

            cameraRenderer.isActive = true;

            addContentView(applicationGLView, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));

            relativeLayout.bringToFront();

            relativeLayout.setBackgroundColor(Color.TRANSPARENT);

            try {
                session.startAR(CameraDevice.CAMERA.CAMERA_DEFAULT);
            } catch (ExceptionAR e) {
                Log.e(LOGTAG, e.getString());
            }

            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);
        } else {
            Log.e(LOGTAG, exception.getString());
        }
    }

    @Override
    public void onQCARUpdate(State state) {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());

        if (refFreeFrame.hasNewTrackableSource()) {
            Log.d(LOGTAG, "Attempting to transfer the trackable source to the dataset");

            objectTracker.deactivateDataSet(objectTracker.getActiveDataSet());

            if (dataSetUserDef.hasReachedTrackableLimit()
                    || dataSetUserDef.getNumTrackables() >= 5) {
                dataSetUserDef.destroy(dataSetUserDef.getTrackable(0));
            }

            if (isExtendedTracking && dataSetUserDef.getNumTrackables() > 0) {
                int previousCreatedTrackableIndex = dataSetUserDef.getNumTrackables() - 1;

                objectTracker.resetExtendedTracking();
                dataSetUserDef.getTrackable(previousCreatedTrackableIndex)
                        .stopExtendedTracking();
            }

            Trackable trackable = dataSetUserDef
                    .createTrackable(refFreeFrame.getNewTrackableSource());

            objectTracker.activateDataSet(dataSetUserDef);

            if (isExtendedTracking) {
                trackable.startExtendedTracking();
            }
        }
    }

    public Bitmap createBitmapText(Bitmap bitmap, String text) {
        Canvas canvas = new Canvas(bitmap);
        bitmap.eraseColor(0);

        Drawable background = new ColorDrawable(Color.TRANSPARENT);
        background.setBounds(0, 0, LENGTH, HEIGHT);
        background.draw(canvas);

        TextPaint textPaint = new TextPaint();
        textPaint.setTextSize(72);
        textPaint.setAntiAlias(true);
        textPaint.setHinting(Paint.HINTING_ON);
        textPaint.setARGB(0xff, 0xff, 0xff, 0xff);

        int x = 16;
        int y = 112;
        for (String line : text.split("\n")) {
            canvas.drawText(line, x, y, textPaint);
            y += textPaint.descent() - textPaint.ascent();
        }

        return bitmap;
    }
}
