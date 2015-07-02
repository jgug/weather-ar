package com.vshkl.weatherar.application;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.qualcomm.vuforia.CameraDevice;
import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Tool;
import com.qualcomm.vuforia.Vec2I;
import com.qualcomm.vuforia.VideoBackgroundConfig;
import com.qualcomm.vuforia.VideoMode;
import com.qualcomm.vuforia.Vuforia;
import com.qualcomm.vuforia.Vuforia.UpdateCallbackInterface;
import com.vshkl.weatherar.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ApplicationSession implements UpdateCallbackInterface{

    //========== Variables ========================================================================

    private static final String TAG = "Vuforia_app";

    private Activity activity;
    private ApplicationControl control;

    private boolean isStarted = false;
    private boolean isCameraRunning = false;
    private boolean isPortrait = false;

    private int vuforiaFlag = 0;

    private int screenWidth = 0;
    private int screenHeight = 0;

    private int camera = CameraDevice.CAMERA.CAMERA_DEFAULT;

    private Matrix44F projectionMatrix;

    private final Object shutdownLock = new Object();

    private InitVuforia initVuforia;
    private LoadTracker loadTracker;

    //========== Constructor ======================================================================

    public ApplicationSession(ApplicationControl control) {
        this.control = control;
    }

    //========== Augmented Reality ================================================================

    public void initAR(Activity activity, int screenOrientation) {
        this.activity = activity;

        ApplicationException exception = null;

        activity.setRequestedOrientation(screenOrientation);
        updateActivityOrientation();
        storeScreenDimensions();

        activity.getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vuforiaFlag = Vuforia.GL_20;

        if (initVuforia != null) {
            exception = new ApplicationException(
                    ApplicationException.VUFORIA_ALREADY_INITIALIZATED, "Whoops!");
            Log.e(TAG, "VUFORIA_ALREADY_INITIALIZATED!");
        }

        if (exception == null) {
            try {
                initVuforia = new InitVuforia();
                initVuforia.execute();
            } catch (Exception e) {
                exception = new ApplicationException(
                        ApplicationException.INITIALIZATION_FAILURE, "Whoops!");
                Log.e(TAG, "INITIALIZATION_FAILURE!");
            }
        }

        if (exception != null) {
            control.onInitARDone(exception);
        }
    }

    public void startAR(int camera) throws ApplicationException{

        if(isCameraRunning) {
            Log.e(TAG, "CAMERA_INITIALIZATION_FAILURE!");
            throw new ApplicationException(
                    ApplicationException.CAMERA_INITIALIZATION_FAILURE, "Whoops!");
        }

        camera = camera;
        if (!CameraDevice.getInstance().init(camera)) {
            Log.e(TAG, "CAMERA_INITIALIZATION_FAILURE!");
            throw new ApplicationException(
                    ApplicationException.CAMERA_INITIALIZATION_FAILURE, "Whoops");
        }

        configureVideoBackground();

        if (!CameraDevice.getInstance().selectVideoMode(CameraDevice.MODE.MODE_DEFAULT)) {
            Log.e(TAG, "CAMERA_INITIALIZATION_FAILURE!");
            throw new ApplicationException(
                    ApplicationException.CAMERA_INITIALIZATION_FAILURE, "Whoops");
        }

        if (!CameraDevice.getInstance().start()) {
            Log.e(TAG, "CAMERA_INITIALIZATION_FAILURE!");
            throw new ApplicationException(
                    ApplicationException.CAMERA_INITIALIZATION_FAILURE, "Whoops");
        }

        setProjectionMatrix();

        control.doStartTrackers();

        isCameraRunning = true;

        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)) {
            CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
        }
    }

    public void pauseAR() throws ApplicationException {
        if (isStarted) {
            stopCamera();
        }
        Vuforia.onPause();
    }

    public void stopAR() throws ApplicationException {
        if (initVuforia != null && initVuforia.getStatus() != InitVuforia.Status.FINISHED) {
            initVuforia.cancel(true);
            initVuforia = null;
        }

        if (loadTracker != null && loadTracker.getStatus() != LoadTracker.Status.FINISHED) {
            loadTracker.cancel(true);
            loadTracker = null;
        }

        initVuforia = null;
        loadTracker = null;

        isStarted = false;

        stopCamera();

        synchronized (shutdownLock) {

            boolean unloadTrackersResult;
            boolean deinitTrackersResult;

            unloadTrackersResult = control.doUnloadTrackersData();

            deinitTrackersResult = control.doDeinitTrackers();

            Vuforia.deinit();

            if (!unloadTrackersResult)
                throw new ApplicationException(
                        ApplicationException.UNLOADING_TRACKERS_FAILURE,
                        "Failed to unload trackers\' data");

            if (!deinitTrackersResult)
                throw new ApplicationException(
                        ApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                        "Failed to deinitialize trackers");
        }
    }

    public void resumeAR() throws ApplicationException {
        Vuforia.onResume();
        if (isStarted) {
            startAR(camera);
        }
    }

    public boolean isARRuning() {
        return isStarted;
    }

    //========== Lifecycle ========================================================================

    public void onResume()
    {
        Vuforia.onResume();
    }


    public void onPause()
    {
        Vuforia.onPause();
    }


    public void onSurfaceChanged(int width, int height)
    {
        Vuforia.onSurfaceChanged(width, height);
    }


    public void onSurfaceCreated()
    {
        Vuforia.onSurfaceCreated();
    }

    //========== Utils ============================================================================

    private void storeScreenDimensions() {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
    }

    private void updateActivityOrientation() {
        Configuration configuration = activity.getResources().getConfiguration();

        switch (configuration.orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                isPortrait = true;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                isPortrait = false;
                break;
            case Configuration.ORIENTATION_UNDEFINED:
            default:
                break;
        }
    }

    private boolean setFocuseMode(int mode) {
        return CameraDevice.getInstance().selectVideoMode(mode);
    }

    private void configureVideoBackground() {
        VideoMode vm = CameraDevice.getInstance().getVideoMode(CameraDevice.MODE.MODE_DEFAULT);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setEnabled(true);
        config.setSynchronous(true);
        config.setPosition(new Vec2I(0, 0));

        int xSize = 0;
        int ySize = 0;

        if (isPortrait) {
            xSize = (int) (vm.getHeight() * (screenHeight / (float) vm.getWidth()));
            ySize = screenHeight;

            if (xSize < screenHeight) {
                xSize = screenWidth;
                ySize = (int) (vm.getHeight() * (vm.getWidth() / (float) vm.getWidth()));
            }
        } else {
            xSize = screenWidth;
            ySize = (int) (vm.getHeight() * (screenWidth / (float) vm.getWidth()));

            if (ySize < screenHeight) {
                xSize = (int) (screenHeight * (vm.getWidth() / (float) vm.getHeight()));
                ySize = screenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Renderer.getInstance().setVideoBackgroundConfig(config);
    }

    private String getLicenseKey() {
        Resources resources = activity.getResources();
        InputStream stream = resources.openRawResource(R.raw.license);
        String licenseKey = "";
        Properties properties = new Properties();
        try {
            properties.load(stream);
            licenseKey = properties.getProperty("vuforia_license_key");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return licenseKey;
    }

    public Matrix44F getProjectionMatrix() {
        return projectionMatrix;
    }

    public void setProjectionMatrix() {
        projectionMatrix = Tool.getProjectionGL(
                CameraDevice.getInstance().getCameraCalibration(), 10.0f, 5000.0f);
    }

    public void stopCamera() {
        if(isCameraRunning) {
            control.doStartTrackers();
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
            isCameraRunning = false;
        }
    }


    public void onConfigurationChanged() {
        updateActivityOrientation();

        storeScreenDimensions();

        if (isARRuning()) {
            configureVideoBackground();
            setProjectionMatrix();
        }
    }

    //========== UpdateCallbackInterface ==========================================================

    @Override
    public void QCAR_onUpdate(State state) {
        control.onQCARUpdate(state);
    }

    //========== Asynchronous processing ==========================================================

    private class InitVuforia extends AsyncTask<Void, Integer, Boolean> {

        private int progress = -1;

        @Override
        protected Boolean doInBackground(Void... params) {
            synchronized (shutdownLock) {
                Vuforia.setInitParameters(activity, vuforiaFlag, getLicenseKey());
                do {
                    progress = Vuforia.init();
                    publishProgress(progress);
                } while (!isCancelled() && progress >= 0 && progress < 100);

                return (progress > 0);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                boolean initTrackerResult;
                initTrackerResult = control.doInitTrackers();

                if (initTrackerResult) {
                    try {
                        loadTracker = new LoadTracker();
                        loadTracker.execute();
                    } catch (Exception e) {
                        control.onInitARDone(new ApplicationException(
                                ApplicationException.LOADING_TRACKERS_FAILURE, "Whoops!"));
                    }
                } else {
                    control.onInitARDone(new ApplicationException(
                            ApplicationException.TRACKERS_INITIALIZATION_FAILURE, "Whoops!"));
                }
            } else {
                control.onInitARDone(new ApplicationException(
                        ApplicationException.INITIALIZATION_FAILURE, "Whoops!"));
            }
        }
    }

    private class LoadTracker extends AsyncTask<Void, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            synchronized (shutdownLock) {
                return control.doLoadTrackersData();
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                System.gc();
                Vuforia.registerCallback(ApplicationSession.this);
                isStarted = true;
            }

            control.onInitARDone(new ApplicationException(
                    ApplicationException.LOADING_TRACKERS_FAILURE, "Whoops!"));
        }
    }
}
