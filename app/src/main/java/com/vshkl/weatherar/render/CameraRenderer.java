package com.vshkl.weatherar.render;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Tool;
import com.qualcomm.vuforia.TrackableResult;
import com.qualcomm.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.qualcomm.vuforia.Vuforia;
import com.vshkl.weatherar.R;
import com.vshkl.weatherar.application.Session;
import com.vshkl.weatherar.utils.FrameShaders;
import com.vshkl.weatherar.utils.Text;
import com.vshkl.weatherar.utils.Texture;
import com.vshkl.weatherar.utils.Utils;
import com.vshkl.weatherar.views.CameraActivity;

import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraRenderer implements GLSurfaceView.Renderer {

    private static final String LOGTAG = "UserDefinedTargetRend";

    Session session;

    public boolean isActive = false;

    private Vector<Texture> textures;

    private int shaderProgramID;
    private int vertexHandle;
    private int normalHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;

    private Text text;

    static final float scale = 20.f;

    private CameraActivity activity;

    public CameraRenderer(CameraActivity activity, Session session) {
        this.activity = activity;
        this.session = session;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        initRendering();
        session.onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        activity.updateRendering();
        session.onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!isActive) {
            return;
        }
        renderFrame();
    }


    private void renderFrame() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        State state = Renderer.getInstance().begin();

        Renderer.getInstance().drawVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glEnable(GLES20.GL_CULL_FACE);
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection()
                == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON) {
            GLES20.glFrontFace(GLES20.GL_CW);
        } else {
            GLES20.glFrontFace(GLES20.GL_CCW);
        }

        activity.refFreeFrame.render();

        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult trackableResult = state.getTrackableResult(tIdx);
            Matrix44F modelViewMatrix_Vuforia = Tool.convertPose2GLMatrix(trackableResult.getPose());
            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

            float[] modelViewProjection = new float[16];
            Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f, scale);
            Matrix.scaleM(modelViewMatrix, 0, scale, scale, scale);
            //TODO: Replace 90 degree rotation with right verticeses
            Matrix.rotateM(modelViewMatrix, 0, 90, 0.0f, 0.0f, scale);
            Matrix.multiplyMM(modelViewProjection, 0, Session
                    .getProjectionMatrix().getData(), 0, modelViewMatrix, 0);

            GLES20.glUseProgram(shaderProgramID);

            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                    false, 0, text.getVertices());
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT,
                    false, 0, text.getNormals());
            GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                    GLES20.GL_FLOAT, false, 0, text.getTexCoords());

            GLES20.glEnableVertexAttribArray(vertexHandle);
            GLES20.glEnableVertexAttribArray(normalHandle);
            GLES20.glEnableVertexAttribArray(textureCoordHandle);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

//            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
//            GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, createBitmapText(bitmap, "Hello!"), 0);
//            bitmap.recycle();

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures.get(0).mTextureID[0]);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjection, 0);
            GLES20.glUniform1i(texSampler2DHandle, 0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                    text.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                    text.getIndices());

            GLES20.glDisableVertexAttribArray(vertexHandle);
            GLES20.glDisableVertexAttribArray(normalHandle);
            GLES20.glDisableVertexAttribArray(textureCoordHandle);

            Utils.checkGLError("CameraActivity renderFrame");
        }

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        Renderer.getInstance().end();
    }


    private void initRendering() {
        Log.d(LOGTAG, "initRendering");

        text = new Text();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f : 1.0f);

//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        for (Texture t : textures) {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            // You search for this part
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
//            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
//                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
//            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
//                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            //
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = Utils.createProgramFromShaderSrc(
                FrameShaders.KEY_FRAME_VERTEX_SHADER,
                FrameShaders.KEY_FRAME_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition");
        normalHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexNormal");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "texSampler2D");
    }


    public void setTextures(Vector<Texture> textures) {
        this.textures = textures;
    }

    public Bitmap createBitmapText(Bitmap bitmap, String text) {
        Canvas canvas = new Canvas(bitmap);
        bitmap.eraseColor(0);

        Drawable background = new ColorDrawable(Color.TRANSPARENT);
        background.setBounds(0, 0, 512, 512);
        background.draw(canvas);

        Paint textPaint = new Paint();
        textPaint.setTextSize(64);
        textPaint.setAntiAlias(true);
        textPaint.setARGB(0xff, 0xff, 0xff, 0xff);

        canvas.drawText(text, 16, 112, textPaint);

        return bitmap;
    }
}
