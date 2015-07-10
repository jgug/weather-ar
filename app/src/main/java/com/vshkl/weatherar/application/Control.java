/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

package com.vshkl.weatherar.application;

import com.qualcomm.vuforia.State;


public interface Control {
    
    boolean doInitTrackers();
    
    boolean doLoadTrackersData();
    
    boolean doStartTrackers();

    boolean doStopTrackers();

    boolean doUnloadTrackersData();

    boolean doDeinitTrackers();

    void onInitARDone(ExceptionAR e);

    void onQCARUpdate(State state);
    
}
