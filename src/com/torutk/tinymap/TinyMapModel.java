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
}
