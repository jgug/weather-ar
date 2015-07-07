/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

package com.vshkl.weatherar.application;


public class ExceptionAR extends java.lang.Exception
{
    
    private static final long serialVersionUID = 2L;
    
    public static final int INITIALIZATION_FAILURE = 0;
    public static final int VUFORIA_ALREADY_INITIALIZATED = 1;
    public static final int TRACKERS_INITIALIZATION_FAILURE = 2;
    public static final int LOADING_TRACKERS_FAILURE = 3;
    public static final int UNLOADING_TRACKERS_FAILURE = 4;
    public static final int TRACKERS_DEINITIALIZATION_FAILURE = 5;
    public static final int CAMERA_INITIALIZATION_FAILURE = 6;
    public static final int SET_FOCUS_MODE_FAILURE = 7;
    public static final int ACTIVATE_FLASH_FAILURE = 8;
    
    private int code = -1;
    private String string = "";

    public ExceptionAR(int code, String description)
    {
        super(description);
        this.code = code;
        string = description;
    }

    public int getCode()
    {
        return code;
    }
    
    public String getString()
    {
        return string;
    }
}
