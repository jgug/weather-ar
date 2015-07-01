package com.vshkl.weatherar.application;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.util.Log;

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

    private Object shutdownLock = new Object();

    private InitVuforia initVuforia;
    private LoadTracker loadTracker;

    //========== Constructor ======================================================================

    public ApplicationSession(ApplicationControl control) {
        this.control = control;
    }

    //========== Augmented Reality ================================================================

    public void initAR(Activity activity, int screenOrientation) {
        this.activity = activity;


    }

    public void runAR(int camera) {

    }

    public void pauseAR() {

    }

    public void stopAR() {

    }

    public void resumeAR() {

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

    }

    //========== Asynchronous processing ==========================================================

    private class InitVuforia extends AsyncTask<Void, Integer, Boolean> {

        private int progress = -1;

        @Override
        protected Boolean doInBackground(Void... params) {
            synchronized (shutdownLock) {
                Vuforia.setInitParameters(activity, vuforiaFlag, "");

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
