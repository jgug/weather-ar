package com.vshkl.weatherar.utils;

import java.nio.Buffer;

public class Text extends MeshObject {
    private Buffer vertBuff;
    private Buffer texCoordBuff;
    private Buffer normBuff;
    private Buffer indBuff;

    private int indicesNumber = 0;
    private int verticesNumber;

    public Text()
    {
        setVerts();
        setTexCoords();
        setNorms();
        setIndices();
    }

    private void setVerts() {
        double[] PLANE_VERTICES = {-1.0, -1.0, 0.0,
                                    1.0, -1.0, 0.0,
                                    1.0,  1.0, 0.0,
                                   -1.0,  1.0, 0.0};
        vertBuff = fillBuffer(PLANE_VERTICES);
        verticesNumber = PLANE_VERTICES.length / 3;
    }

    private void setTexCoords() {
        double[] PLANE_TEX_COORDS = {0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0};
        texCoordBuff = fillBuffer(PLANE_TEX_COORDS);
    }

    private void setNorms() {
        double[] PLANE_NORMS = {0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0};
        normBuff = fillBuffer(PLANE_NORMS);
    }

    private void setIndices() {
        short[] PLANE_INDICES = {0, 1, 2, 0, 2, 3};
        indBuff = fillBuffer(PLANE_INDICES);
        indicesNumber = PLANE_INDICES.length;
    }

    public int getNumObjectIndex() {
        return indicesNumber;
    }

    @Override
    public int getNumObjectVertex() {
        return verticesNumber;
    }

    @Override
    public Buffer getBuffer(BUFFER_TYPE bufferType) {
        Buffer result = null;
        switch (bufferType) {
            case BUFFER_TYPE_VERTEX:
                result = vertBuff;
                break;
            case BUFFER_TYPE_TEXTURE_COORD:
                result = texCoordBuff;
                break;
            case BUFFER_TYPE_NORMALS:
                result = normBuff;
                break;
            case BUFFER_TYPE_INDICES:
                result = indBuff;
            default:
                break;
        }
        return result;
    }
}
