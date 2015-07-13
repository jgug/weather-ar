/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

package com.vshkl.weatherar.render;

import android.util.Log;

import com.qualcomm.vuforia.ImageTargetBuilder;
import com.qualcomm.vuforia.ObjectTracker;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.TrackableSource;
import com.qualcomm.vuforia.TrackerManager;
import com.qualcomm.vuforia.Vec2F;
import com.qualcomm.vuforia.VideoBackgroundConfig;
import com.vshkl.weatherar.application.Session;
import com.vshkl.weatherar.utils.Utils;
import com.vshkl.weatherar.views.CameraActivity;

public class RefFreeFrame {

    private static final String LOGTAG = "RefFreeFrame";

    enum STATUS {
        STATUS_IDLE, STATUS_SCANNING, STATUS_CREATING, STATUS_SUCCESS
    }

    STATUS curStatus;

    float colorFrame[];

    Vec2F halfScreenSize;

    long lastFrameTime;
    long lastSuccessTime;

    RefFreeFrameGL frameGL;
    TrackableSource trackableSource;

    CameraActivity activity;
    Session vuforiaAppSession;

    public RefFreeFrame(CameraActivity activity, Session session) {
        this.activity = activity;
        vuforiaAppSession = session;
        colorFrame = new float[4];
        curStatus = STATUS.STATUS_IDLE;
        lastSuccessTime = 0;
        trackableSource = null;
        colorFrame[0] = 1.0f;
        colorFrame[1] = 0.0f;
        colorFrame[2] = 0.0f;
        colorFrame[3] = 0.75f;

        frameGL = new RefFreeFrameGL(this.activity, vuforiaAppSession);
        halfScreenSize = new Vec2F();
    }

    float transition(float v0, float inc, float a, float b) {
        float vOut = v0 + inc;
        return (vOut < a ? a : (vOut > b ? b : vOut));
    }

    float transition(float v0, float inc) {
        return transition(v0, inc, 0.0f, 1.0f);
    }

    public void init() {
        // load the frame texture
        frameGL.getTextures();

        trackableSource = null;
    }

    public void deInit() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
                .getTracker(ObjectTracker.getClassType()));
        if (objectTracker != null) {
            ImageTargetBuilder targetBuilder = objectTracker
                    .getImageTargetBuilder();
            if (targetBuilder != null && (targetBuilder.getFrameQuality()
                    != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)) {
                targetBuilder.stopScan();
            }
        }
    }

    public void initGL(int screenWidth, int screenHeight) {
        frameGL.init(screenWidth, screenHeight);

        Renderer renderer = Renderer.getInstance();
        VideoBackgroundConfig vc = renderer.getVideoBackgroundConfig();
        int temp[] = vc.getSize().getData();
        float[] videoBackgroundConfigSize = new float[2];
        videoBackgroundConfigSize[0] = temp[0] * 0.5f;
        videoBackgroundConfigSize[1] = temp[1] * 0.5f;

        halfScreenSize.setData(videoBackgroundConfigSize);

        lastFrameTime = System.currentTimeMillis();

        reset();
    }

    public void reset() {
        curStatus = STATUS.STATUS_IDLE;
    }


    public void setCreating() {
        curStatus = STATUS.STATUS_CREATING;
    }

    void updateUIState(ImageTargetBuilder targetBuilder, int frameQuality) {
        long elapsedTimeMS = System.currentTimeMillis() - lastFrameTime;
        lastFrameTime += elapsedTimeMS;

        float transitionHalfSecond = elapsedTimeMS * 0.002f;

        STATUS newStatus = curStatus;

        switch (curStatus) {
            case STATUS_IDLE:
                if (frameQuality != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)
                    newStatus = STATUS.STATUS_SCANNING;

                break;

            case STATUS_SCANNING:
                switch (frameQuality) {
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_LOW:
                        colorFrame[0] = 1.0f;
                        colorFrame[1] = 1.0f;
                        colorFrame[2] = 1.0f;

                        break;

                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_HIGH:
                    case ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_MEDIUM:
                        colorFrame[0] = transition(colorFrame[0],
                                -transitionHalfSecond);
                        colorFrame[1] = transition(colorFrame[1],
                                transitionHalfSecond);
                        colorFrame[2] = transition(colorFrame[2],
                                -transitionHalfSecond);

                        break;
                }
                break;

            case STATUS_CREATING: {
                TrackableSource newTrackableSource = targetBuilder
                        .getTrackableSource();
                if (newTrackableSource != null) {
                    newStatus = STATUS.STATUS_SUCCESS;
                    lastSuccessTime = lastFrameTime;
                    trackableSource = newTrackableSource;

                    activity.targetCreated();
                }
            }
            default:
                break;
        }

        curStatus = newStatus;
    }


    void render() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) (trackerManager
                .getTracker(ObjectTracker.getClassType()));

        ImageTargetBuilder targetBuilder = objectTracker.getImageTargetBuilder();
        int frameQuality = targetBuilder.getFrameQuality();

        updateUIState(targetBuilder, frameQuality);

        if (curStatus == STATUS.STATUS_SUCCESS) {
            curStatus = STATUS.STATUS_IDLE;

            Log.d(LOGTAG, "Built target, reactivating dataset with new target");
            activity.doStartTrackers();
        }

        switch (curStatus) {
            case STATUS_SCANNING:
                renderScanningViewfinder(frameQuality);
                break;
            default:
                break;

        }

        Utils.checkGLError("RefFreeFrame render");
    }

    void renderScanningViewfinder(int quality) {
        frameGL.setModelViewScale(2.0f);
        frameGL.setColor(colorFrame);
        frameGL.renderViewfinder();
    }

    public boolean hasNewTrackableSource() {
        return (trackableSource != null);
    }

    public TrackableSource getNewTrackableSource() {
        TrackableSource result = trackableSource;
        trackableSource = null;
        return result;
    }
}
