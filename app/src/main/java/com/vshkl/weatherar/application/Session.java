/*===============================================================================
Copyright (c) 2012-2015 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/


package com.vshkl.weatherar.application;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.qualcomm.vuforia.CameraCalibration;
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
import com.vshkl.weatherar.utils.KeysManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class Session implements UpdateCallbackInterface {
    //========== Variables ========================================================================

    private static final String LOGTAG = "Vuforia_Sample_App";
    
    private Activity activity;
    private Control sessionControl;
    
    private boolean isStarted = false;
    private boolean isCameraRunning = false;
    
    private int screenWidth = 0;
    private int screenHeight = 0;
    
    private InitVuforiaTask initVuforiaTask;
    private LoadTrackerTask loadTrackerTask;

    private final Object shutdownLock = new Object();
    
    private int vuforiaFlags = 0;
    
    private int camera = CameraDevice.CAMERA.CAMERA_DEFAULT;
    
    private static Matrix44F projectionMatrix;
    
    private boolean isPortrait = false;

    //========== Constructor ======================================================================

    public Session(Control sessionControl) {
        this.sessionControl = sessionControl;
    }

    //========== Augmented Reality ===============================================================
    
    public void initAR(Activity activity, int screenOrientation) {
        ExceptionAR vuforiaExceptionAR = null;
        this.activity = activity;
        
        if ((screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
            && (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO))
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
        
        this.activity.setRequestedOrientation(screenOrientation);
        
        updateActivityOrientation();
        
        storeScreenDimensions();
        
        this.activity.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        vuforiaFlags = Vuforia.GL_20;

        if (initVuforiaTask != null) {
            String logMessage = "Cannot initialize SDK twice";
            vuforiaExceptionAR = new ExceptionAR(
                    ExceptionAR.VUFORIA_ALREADY_INITIALIZATED, logMessage);
            Log.e(LOGTAG, logMessage);
        }
        
        if (vuforiaExceptionAR == null) {
            try {
                initVuforiaTask = new InitVuforiaTask();
                initVuforiaTask.execute();
            } catch (java.lang.Exception e) {
                String logMessage = "Initializing Vuforia SDK failed";
                vuforiaExceptionAR = new ExceptionAR(
                        ExceptionAR.INITIALIZATION_FAILURE, logMessage);
                Log.e(LOGTAG, logMessage);
            }
        }
        
        if (vuforiaExceptionAR != null) {
            sessionControl.onInitARDone(vuforiaExceptionAR);
        }
    }

    public void startAR(int camera) throws ExceptionAR {
        String error;
        if(isCameraRunning) {
        	error = "Camera already running, unable to open again";
        	Log.e(LOGTAG, error);
            throw new ExceptionAR(ExceptionAR.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        this.camera = camera;
        if (!CameraDevice.getInstance().init(camera)) {
            error = "Unable to open camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new ExceptionAR(ExceptionAR.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        configureVideoBackground();
        
        if (!CameraDevice.getInstance().selectVideoMode(CameraDevice.MODE.MODE_DEFAULT)) {
            error = "Unable to set video mode";
            Log.e(LOGTAG, error);
            throw new ExceptionAR(ExceptionAR.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        if (!CameraDevice.getInstance().start()) {
            error = "Unable to start camera device: " + camera;
            Log.e(LOGTAG, error);
            throw new ExceptionAR(ExceptionAR.CAMERA_INITIALIZATION_FAILURE, error);
        }
        
        setProjectionMatrix();
        
        sessionControl.doStartTrackers();
        
        isCameraRunning = true;
        
        if(!CameraDevice.getInstance()
            .setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)) {
            CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
        }
    }

    public void stopAR() throws ExceptionAR {
        if (initVuforiaTask != null
            && initVuforiaTask.getStatus() != InitVuforiaTask.Status.FINISHED) {
            initVuforiaTask.cancel(true);
            initVuforiaTask = null;
        }
        
        if (loadTrackerTask != null
            && loadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED) {
            loadTrackerTask.cancel(true);
            loadTrackerTask = null;
        }
        
        initVuforiaTask = null;
        loadTrackerTask = null;
        
        isStarted = false;
        
        stopCamera();

        synchronized (shutdownLock) {
            
            boolean unloadTrackersResult;
            boolean deinitTrackersResult;
            
            unloadTrackersResult = sessionControl.doUnloadTrackersData();
            
            deinitTrackersResult = sessionControl.doDeinitTrackers();
            
            Vuforia.deinit();
            
            if (!unloadTrackersResult)
                throw new ExceptionAR(
                    ExceptionAR.UNLOADING_TRACKERS_FAILURE,
                    "Failed to unload trackers\' data");
            
            if (!deinitTrackersResult)
                throw new ExceptionAR(
                    ExceptionAR.TRACKERS_DEINITIALIZATION_FAILURE,
                    "Failed to deinitialize trackers");
        }
    }

    public void resumeAR() throws ExceptionAR {
        Vuforia.onResume();
        if (isStarted) {
            startAR(camera);
        }
    }

    public void pauseAR() throws ExceptionAR {
        if (isStarted) {
            stopCamera();
        }
        Vuforia.onPause();
    }

    private boolean isARRunning() {
        return isStarted;
    }

    //========== Lifecycle ========================================================================

    public void onResume() {
        Vuforia.onResume();
    }

    public void onPause() {
        Vuforia.onPause();
    }

    public void onSurfaceChanged(int width, int height) {
        Vuforia.onSurfaceChanged(width, height);
    }

    public void onSurfaceCreated() {
        Vuforia.onSurfaceCreated();
    }

    //========== Utils ============================================================================
    
    public static Matrix44F getProjectionMatrix() {
        return projectionMatrix;
    }

    public void onConfigurationChanged() {
        updateActivityOrientation();
        storeScreenDimensions();
        
        if (isARRunning()) {
            configureVideoBackground();
            setProjectionMatrix();
        }
        
    }

    private String getInitializationErrorString(int code) {
        if (code == Vuforia.INIT_DEVICE_NOT_SUPPORTED)
            return activity.getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED);
        if (code == Vuforia.INIT_NO_CAMERA_ACCESS)
            return activity.getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS);
        if (code == Vuforia.INIT_LICENSE_ERROR_MISSING_KEY)
            return activity.getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_INVALID_KEY)
            return activity.getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return activity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return activity.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT);
        if (code == Vuforia.INIT_LICENSE_ERROR_CANCELED_KEY)
            return activity.getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY);
        if (code == Vuforia.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return activity.getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH);
        else {
            return activity.getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR);
        }
    }

    private void storeScreenDimensions() {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
    }

    private void updateActivityOrientation() {
        Configuration config = activity.getResources().getConfiguration();

        switch (config.orientation) {
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

        Log.i(LOGTAG, "Activity is in " + (isPortrait ? "PORTRAIT" : "LANDSCAPE"));
    }

    public void setProjectionMatrix() {
        CameraCalibration camCal = CameraDevice.getInstance().getCameraCalibration();
        projectionMatrix = Tool.getProjectionGL(camCal, 10.0f, 5000.0f);
    }

    public void stopCamera() {
        if(isCameraRunning) {
            sessionControl.doStopTrackers();
            CameraDevice.getInstance().stop();
            CameraDevice.getInstance().deinit();
            isCameraRunning = false;
        }
    }

    private boolean setFocusMode(int mode) throws ExceptionAR {
        boolean result = CameraDevice.getInstance().setFocusMode(mode);

        if (!result) {
            throw new ExceptionAR(
                    ExceptionAR.SET_FOCUS_MODE_FAILURE,
                    "Failed to set focus mode: " + mode);
        }

        return result;
    }

    private void configureVideoBackground() {
        CameraDevice cameraDevice = CameraDevice.getInstance();
        VideoMode vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT);

        VideoBackgroundConfig config = new VideoBackgroundConfig();
        config.setEnabled(true);
        config.setSynchronous(true);
        config.setPosition(new Vec2I(0, 0));

        int xSize;
        int ySize;
        if (isPortrait) {
            xSize = (int) (vm.getHeight() * (screenHeight / (float) vm
                    .getWidth()));
            ySize = screenHeight;

            if (xSize < screenWidth) {
                xSize = screenWidth;
                ySize = (int) (screenWidth * (vm.getWidth() / (float) vm
                        .getHeight()));
            }
        } else {
            xSize = screenWidth;
            ySize = (int) (vm.getHeight() * (screenWidth / (float) vm
                    .getWidth()));

            if (ySize < screenHeight) {
                xSize = (int) (screenHeight * (vm.getWidth() / (float) vm
                        .getHeight()));
                ySize = screenHeight;
            }
        }

        config.setSize(new Vec2I(xSize, ySize));

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.getWidth()
                + " , " + vm.getHeight() + "), Screen (" + screenWidth + " , "
                + screenHeight + "), mSize (" + xSize + " , " + ySize + ")");

        Renderer.getInstance().setVideoBackgroundConfig(config);

    }

    //========== UpdateCallbackInterface ==========================================================

    @Override
    public void QCAR_onUpdate(State s) {
        sessionControl.onQCARUpdate(s);
    }

    //========== Asynchronous procassiong =========================================================

    private class InitVuforiaTask extends AsyncTask<Void, Integer, Boolean> {
        private int progressValue = -1;
        
        @Override
        protected Boolean doInBackground(Void... params) {
            synchronized (shutdownLock) {
                Vuforia.setInitParameters(activity, vuforiaFlags,
                        KeysManager.getKey(activity, "vuforia_license_key"));
                
                do {
                    progressValue = Vuforia.init();
                    publishProgress(progressValue);
                } while (!isCancelled() && progressValue >= 0 && progressValue < 100);

                return (progressValue > 0);
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            ExceptionAR vuforiaExceptionAR;
            
            if (result) {
                Log.d(LOGTAG, "InitVuforiaTask.onPostExecute: Vuforia "
                    + "initialization successful");
                
                boolean initTrackersResult;
                initTrackersResult = sessionControl.doInitTrackers();
                
                if (initTrackersResult) {
                    try {
                        loadTrackerTask = new LoadTrackerTask();
                        loadTrackerTask.execute();
                    } catch (java.lang.Exception e) {
                        String logMessage = "Loading tracking data set failed";
                        vuforiaExceptionAR = new ExceptionAR(
                            ExceptionAR.LOADING_TRACKERS_FAILURE,
                            logMessage);
                        Log.e(LOGTAG, logMessage);
                        sessionControl.onInitARDone(vuforiaExceptionAR);
                    }
                    
                } else {
                    vuforiaExceptionAR = new ExceptionAR(
                        ExceptionAR.TRACKERS_INITIALIZATION_FAILURE,
                        "Failed to initialize trackers");
                    sessionControl.onInitARDone(vuforiaExceptionAR);
                }
            } else {
                String logMessage;
                logMessage = getInitializationErrorString(progressValue);
                
                Log.e(LOGTAG, "InitVuforiaTask.onPostExecute: " + logMessage
                    + " Exiting.");

                vuforiaExceptionAR = new ExceptionAR(
                    ExceptionAR.INITIALIZATION_FAILURE,
                    logMessage);
                sessionControl.onInitARDone(vuforiaExceptionAR);
            }
        }
    }
    
    private class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            synchronized (shutdownLock) {
                return sessionControl.doLoadTrackersData();
            }
        }
        
        @Override
        protected void onPostExecute(Boolean result) {
            ExceptionAR vuforiaExceptionAR = null;
            
            Log.d(LOGTAG, "LoadTrackerTask.onPostExecute: execution "
                + (result ? "successful" : "failed"));
            
            if (!result) {
                String logMessage = "Failed to load tracker data.";
                Log.e(LOGTAG, logMessage);
                vuforiaExceptionAR = new ExceptionAR(
                    ExceptionAR.LOADING_TRACKERS_FAILURE,
                    logMessage);
            } else {
                System.gc();
                Vuforia.registerCallback(Session.this);
                isStarted = true;
            }

            sessionControl.onInitARDone(vuforiaExceptionAR);
        }
    }
}
