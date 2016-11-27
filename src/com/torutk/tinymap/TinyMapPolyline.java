/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

/**
 *
 * @author Toru Takahashi
 */
public class TinyMapPolyline {
    private final double[] xArray;
    private final double[] yArray;

    public TinyMapPolyline(double[] xArray, double[] yArray) {
        this.xArray = xArray;
        this.yArray = yArray;
    }

    public double[] getXArray() {
        return xArray;
    }

    public double[] getYArray() {
        return yArray;
    }

    public int size() {
        return xArray.length;
    }
    
}
