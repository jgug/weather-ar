/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

package com.vshkl.weatherar.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class Texture {
    private static final String LOGTAG = "Vuforia_Texture";
    
    public int mWidth;
    public int mHeight;
    public int mChannels;
    public ByteBuffer mData;
    public int[] mTextureID = new int[1];
    public boolean mSuccess = false;
    
    public static Texture loadTextureFromApk(String fileName, AssetManager assets) {
        InputStream inputStream;
        try {
            inputStream = assets.open(fileName, AssetManager.ACCESS_BUFFER);
            
            BufferedInputStream bufferedStream = new BufferedInputStream(inputStream);
            Bitmap bM = BitmapFactory.decodeStream(bufferedStream);
            
            int[] data = new int[bM.getWidth() * bM.getHeight()];
            bM.getPixels(data, 0, bM.getWidth(), 0, 0, bM.getWidth(), bM.getHeight());
            
            return loadTextureFromIntBuffer(data, bM.getWidth(), bM.getHeight());
        } catch (IOException e) {
            Log.e(LOGTAG, "Failed to log texture '" + fileName + "' from APK");
            Log.i(LOGTAG, e.getMessage());
            return null;
        }
    }

    public static Texture loadTextureFromBitmap(Bitmap bitmap) {
        int[] data = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(data, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        return loadTextureFromIntBuffer(data, bitmap.getWidth(), bitmap.getHeight());
    }

    public static Texture loadTextureFromIntBuffer(int[] data, int width, int height) {
        int numPixels = width * height;
        byte[] dataBytes = new byte[numPixels * 4];
        
        for (int p = 0; p < numPixels; ++p) {
            int colour = data[p];
            dataBytes[p * 4] = (byte) (colour >>> 16);
            dataBytes[p * 4 + 1] = (byte) (colour >>> 8);
            dataBytes[p * 4 + 2] = (byte) colour;
            dataBytes[p * 4 + 3] = (byte) (colour >>> 24);
        }
        
        Texture texture = new Texture();
        texture.mWidth = width;
        texture.mHeight = height;
        texture.mChannels = 4;
        
        texture.mData = ByteBuffer.allocateDirect(dataBytes.length).order(ByteOrder.nativeOrder());
        int rowSize = texture.mWidth * texture.mChannels;
        for (int r = 0; r < texture.mHeight; r++) {
            texture.mData.put(dataBytes, rowSize * (texture.mHeight - 1 - r), rowSize);
        }
        
        texture.mData.rewind();
        
        dataBytes = null;
        data = null;
        
        texture.mSuccess = true;
        return texture;
    }
}
