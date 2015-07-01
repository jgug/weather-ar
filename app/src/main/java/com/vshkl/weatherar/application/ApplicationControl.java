package com.vshkl.weatherar.application;

import com.qualcomm.vuforia.State;

public interface ApplicationControl {

    boolean doInitTrackers();
    boolean doLoadTrackersData();
    boolean doStartTrackers();
    boolean doStopTrackers();
    boolean doUnloadTrackersData();
    boolean doDeinitTrackers();
    void onInitARDone(ApplicationException e);
    void onQCARUpdate(State state);
}
