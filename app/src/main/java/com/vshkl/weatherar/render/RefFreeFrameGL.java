/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

package com.vshkl.weatherar.render;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.content.res.Configuration;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.Vec4F;
import com.qualcomm.vuforia.VideoBackgroundConfig;
import com.vshkl.weatherar.application.Session;
import com.vshkl.weatherar.utils.Utils;
import com.vshkl.weatherar.utils.Texture;
import com.vshkl.weatherar.views.CameraActivity;


public class RefFreeFrameGL {
    
    private static final String LOGTAG = "RefFreeFrameGL";
    
    CameraActivity activity;
    Session vuforiaAppSession;
    
    private final class TEXTURE_NAME {
        public final static int TEXTURE_VIEWFINDER_MARKS_PORTRAIT = 0;
        public final static int TEXTURE_VIEWFINDER_MARKS = 1;
        public final static int TEXTURE_COUNT = 2;
    }
    
    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int colorHandle;
    private int mvpMatrixHandle;

    Matrix44F projectionOrtho, modelview;
    
    Vec4F color;
    
    String textureNames[] = {
            "Target/viewfinder_crop_marks_portrait.png",
            "Target/viewfinder_crop_marks_landscape.png" };
    Texture[] textures;
    
    int NUM_FRAME_VERTEX_TOTAL = 4;
    int NUM_FRAME_INDEX = 1 + NUM_FRAME_VERTEX_TOTAL;
    
    float frameVertices_viewfinder[] = new float[NUM_FRAME_VERTEX_TOTAL * 3];
    float frameTexCoords[] = new float[NUM_FRAME_VERTEX_TOTAL * 2];
    short frameIndices[] = new short[NUM_FRAME_INDEX];
    
    boolean isActivityPortrait;
    
    String frameVertexShader = " \n" + "attribute vec4 vertexPosition; \n"
        + "attribute vec2 vertexTexCoord; \n" + "\n"
        + "varying vec2 texCoord; \n" + "\n"
        + "uniform mat4 modelViewProjectionMatrix; \n" + "\n"
        + "void main() \n" + "{ \n"
        + "gl_Position = modelViewProjectionMatrix * vertexPosition; \n"
        + "texCoord = vertexTexCoord; \n" + "} \n";
    
    String frameFragmentShader = " \n" + "precision mediump float; \n" + "\n"
        + "varying vec2 texCoord; \n" + "\n"
        + "uniform sampler2D texSampler2D; \n" + "uniform vec4 keyColor; \n"
        + "\n" + "void main() \n" + "{ \n"
        + "vec4 texColor = texture2D(texSampler2D, texCoord); \n"
        + "gl_FragColor = keyColor * texColor; \n" + "} \n" + "";

    public RefFreeFrameGL(CameraActivity activity, Session session) {
        this.activity = activity;
        vuforiaAppSession = session;
        shaderProgramID = 0;
        vertexHandle = 0;
        textureCoordHandle = 0;
        mvpMatrixHandle = 0;
        
        Log.d(LOGTAG, "RefFreeFrameGL Ctor");
        textures = new Texture[TEXTURE_NAME.TEXTURE_COUNT];
        for (int i = 0; i < TEXTURE_NAME.TEXTURE_COUNT; i++)
            textures[i] = null;
        
        color = new Vec4F();
    }

    void setColor(float r, float g, float b, float a) {
        float[] tempColor = { r, g, b, a };
        color.setData(tempColor);
    }

    void setColor(float c[]) {
        if (c.length != 4)
            throw new IllegalArgumentException(
                "Color length must be 4 floats length");
        
        color.setData(c);
    }
    
    void setModelViewScale(float scale) {
        float[] tempModelViewData = modelview.getData();
        tempModelViewData[14] = scale;
        modelview.setData(tempModelViewData);
    }

    boolean init(int screenWidth, int screenHeight) {
        float tempMatrix44Array[] = new float[16];
        modelview = new Matrix44F();
        
        tempMatrix44Array[0] = tempMatrix44Array[5] = tempMatrix44Array[10] = tempMatrix44Array[15] = 1.0f;
        modelview.setData(tempMatrix44Array);
        
        float tempColor[] = { 1.0f, 1.0f, 1.0f, 0.6f };
        color.setData(tempColor);
        
        Configuration config = activity.getResources().getConfiguration();
        
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isActivityPortrait = false;
        } else {
            isActivityPortrait = true;
        }
        
        if ((shaderProgramID = Utils.createProgramFromShaderSrc(
                frameVertexShader, frameFragmentShader)) == 0) {
            return false;
        }
        
        if ((vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexPosition")) == -1) {
            return false;
        }

        if ((textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexTexCoord")) == -1) {
            return false;
        }

        if ((mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "modelViewProjectionMatrix")) == -1) {
            return false;
        }

        if ((colorHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "keyColor")) == -1) {
            return false;
        }
        
        Renderer renderer = Renderer.getInstance();
        VideoBackgroundConfig vc = renderer.getVideoBackgroundConfig();
        
        projectionOrtho = new Matrix44F();
        for (int i = 0; i < tempMatrix44Array.length; i++) {
            tempMatrix44Array[i] = 0;
        }
        
        int tempVC[] = vc.getSize().getData();
        
        tempMatrix44Array[0] = 2.0f / (float) (tempVC[0]);
        tempMatrix44Array[5] = 2.0f / (float) (tempVC[1]);
        tempMatrix44Array[10] = 1.0f / (-10.0f);
        tempMatrix44Array[11] = -5.0f / (-10.0f);
        tempMatrix44Array[15] = 1.0f;

        float sizeH_viewfinder = ((float) screenWidth / tempVC[0]) * (2.0f / tempMatrix44Array[0]);
        float sizeV_viewfinder = ((float) screenHeight / tempVC[1]) * (2.0f / tempMatrix44Array[5]);
        
        Log.d(LOGTAG, "Viewfinder Size: " + sizeH_viewfinder + ", " + sizeV_viewfinder);

        int cnt = 0, tCnt = 0;

        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 0.0f;
        frameTexCoords[tCnt++] = 1.0f;
        
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 1.0f;
        frameTexCoords[tCnt++] = 1.0f;
        
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 1.0f;
        frameTexCoords[tCnt++] = 0.0f;
        
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeH_viewfinder;
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeV_viewfinder;
        frameVertices_viewfinder[cnt++] = 0.0f;
        frameTexCoords[tCnt++] = 0.0f;
        frameTexCoords[tCnt++] = 0.0f;
        
        cnt = 0;
        for (short i = 0; i < NUM_FRAME_VERTEX_TOTAL; i++) {
            frameIndices[cnt++] = i;
        }
        frameIndices[cnt++] = 0;
        
        for (Texture t : textures) {
            if (t != null) {
                GLES20.glGenTextures(1, t.mTextureID, 0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
            }
        }
        
        return true;
    }
    
    private Buffer fillBuffer(float[] array) {
        ByteBuffer bb = ByteBuffer.allocateDirect(4 * array.length);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (double d : array) {
            bb.putFloat((float) d);
        }
        bb.rewind();
        
        return bb;
    }
    
    private Buffer fillBuffer(short[] array) {
        ByteBuffer bb = ByteBuffer.allocateDirect(2 * array.length);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        for (short s : array) {
            bb.putShort(s);
        }
        bb.rewind();
        
        return bb;
    }
    
    void getTextures() {
        for (int i = 0; i < TEXTURE_NAME.TEXTURE_COUNT; i++)
            textures[i] = activity.createTexture(textureNames[i]);
    }
    
    void renderViewfinder() {
        if (textures == null) {
            return;
        }
        
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        
        GLES20.glUseProgram(shaderProgramID);
        
        float[] mvp = new float[16];
        Matrix.multiplyMM(mvp, 0, projectionOrtho.getData(), 0, modelview.getData(), 0);
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvp, 0);
        
        Buffer verticesBuffer = fillBuffer(frameVertices_viewfinder);
        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false, 0, verticesBuffer);
        
        Buffer texCoordBuffer = fillBuffer(frameTexCoords);
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT,
            false, 0, texCoordBuffer);
        
        GLES20.glEnableVertexAttribArray(vertexHandle);
        GLES20.glEnableVertexAttribArray(textureCoordHandle);
        
        GLES20.glUniform4fv(colorHandle, 1, color.getData(), 0);
        
        if (isActivityPortrait && textures[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS_PORTRAIT]
                != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(
                    GLES20.GL_TEXTURE_2D,
                    textures[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS_PORTRAIT].mTextureID[0]);
        } else if (!isActivityPortrait && textures[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS] != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                textures[TEXTURE_NAME.TEXTURE_VIEWFINDER_MARKS].mTextureID[0]);
        }
        
        Buffer indicesBuffer = fillBuffer(frameIndices);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, NUM_FRAME_INDEX,
            GLES20.GL_UNSIGNED_SHORT, indicesBuffer);
        
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }
    
}
