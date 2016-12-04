/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 *
 * @author Toru Takahashi
 */
public class TinyMapModel {
    private File mapFile;
    private List<TinyMapPolyline> polylines;

    
    public Stream<TinyMapPolyline> stream() {
        return polylines.stream();
    }
    
    public void loadLines() {
        polylines = new ArrayList<>();
        loadTestPattern();
    }
    
    private void loadTestPattern() {
        for (int lat = -90; lat <= 90; lat += 10) {
            double[] xs = new double[36];
            double[] ys = new double[36];
            int lon = -180;
            for (int i = 0; i < 36; i++, lon += 10) {
                xs[i] = lon;
                ys[i] = lat;
            }
            polylines.add(new TinyMapPolyline(xs, ys));
        }
        for (int lon = -180; lon < 180; lon += 10) {
            double[] xs = new double[19];
            double[] ys = new double[19];
            int lat = -90;
            for (int i = 0; i <= 18; i++, lat += 10) {
                xs[i] = lon;
                ys[i] = lat;
            }
            polylines.add(new TinyMapPolyline(xs, ys));
        }
    }

    private void loadTestPattern2() {
        for (int lat = 0; lat <= 90; lat += 10) {
            int length = 18 - lat * 2 / 10;
            double[] xs = new double[length];
            double[] ys = new double[length];
            int lon = 0;
            for (int i = 0; i < length; i++, lon += 10) {
                xs[i] = lon;
                ys[i] = lat;
            }
            polylines.add(new TinyMapPolyline(xs, ys));
        }
        for (int lon = 0; lon <= 180; lon += 10) {
            int length = 9 - lon / 2 / 10;
            double[] xs = new double[length];
            double[] ys = new double[length];
            int lat = 0;
            for (int i = 0; i < length; i++, lat += 10) {
                xs[i] = lon;
                ys[i] = lat;
            }
            polylines.add(new TinyMapPolyline(xs, ys));
        }
    }
}
