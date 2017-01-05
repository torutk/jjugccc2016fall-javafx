/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.geometry.Point2D;
import org.nocrala.tools.gis.data.esri.shapefile.ShapeFileReader;
import org.nocrala.tools.gis.data.esri.shapefile.ValidationPreferences;
import org.nocrala.tools.gis.data.esri.shapefile.exception.InvalidShapeFileException;
import org.nocrala.tools.gis.data.esri.shapefile.shape.AbstractShape;
import org.nocrala.tools.gis.data.esri.shapefile.shape.PointData;
import org.nocrala.tools.gis.data.esri.shapefile.shape.ShapeType;
import org.nocrala.tools.gis.data.esri.shapefile.shape.shapes.PolylineShape;
import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.CoordinateTransform;
import org.osgeo.proj4j.CoordinateTransformFactory;
import org.osgeo.proj4j.ProjCoordinate;

/**
 *
 * @author Toru Takahashi
 */
public class TinyMapModel {
    private static final Logger logger = Logger.getLogger(TinyMapModel.class.getName());
    private File mapFile;
    private List<TinyMapPolyline> polylines = new ArrayList<>();
    private Function<PointData, Point2D> projection;
        
    public TinyMapModel(File selected) {
        mapFile = selected;
        projection = p -> new Point2D(p.getX() * 100_000, p.getY() * 100_000); // 1度100kmとした直交座標変換
    }

    public void setProjection(Function<PointData, Point2D> projection) {
        this.projection = projection;
    }

    public Stream<TinyMapPolyline> stream() {
        return polylines.stream();
    }
    
    public void loadLines() throws TinyMapException {
        polylines.clear();
        ValidationPreferences preferences = new ValidationPreferences();
        preferences.setAllowUnlimitedNumberOfPointsPerShape(true);
        try (InputStream inStream = new BufferedInputStream(new FileInputStream(mapFile))) {
            ShapeFileReader reader = new ShapeFileReader(inStream, preferences);
            AbstractShape shape = reader.next();
            while (shape != null) {
                if (shape.getShapeType() != ShapeType.POLYLINE) {
                    continue;
                }
                PolylineShape polyline = (PolylineShape) shape;
                for (int i = 0; i < polyline.getNumberOfParts(); i++) {
                    TinyMapPolyline mapPolyline = createMapPolylineFrom(polyline, i);
                    polylines.add(mapPolyline);
                }
                shape = reader.next();
            }
        } catch (IOException | InvalidShapeFileException ex) {
            throw new TinyMapException("シェープファイル読み込み時に例外発生", ex);
        }
        logger.info(String.format("Read %d polylines from %s%n", polylines.size(), mapFile.getName()));
    }
        
    TinyMapPolyline createMapPolylineFrom(PolylineShape shape, int part) {
        PointData[] gcsPoints = shape.getPointsOfPart(part);
        double[] xArray = new double[gcsPoints.length];
        double[] yArray = new double[gcsPoints.length];
        for (int i = 0; i < gcsPoints.length; i++) {
            Point2D pcsPoint = projection.apply(gcsPoints[i]);
            xArray[i] = pcsPoint.getX();
            yArray[i] = pcsPoint.getY();
        }
        return new TinyMapPolyline(xArray, yArray);
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

    public static enum MapProjection {
        MOLLWEIDE("ESRI:54009"),
        MERCATOR("ESRI:54004"),
        ECKERT6("ESRI:54010"),
        CASSINI("ESRI:54028");

        private CRSFactory crsFactory = new CRSFactory();
        private CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
                
        private CoordinateReferenceSystem crsWgs84;
        private CoordinateReferenceSystem crsProjected;
        private CoordinateTransform transform;
        
        private MapProjection(String name) {
            crsWgs84 = crsFactory.createFromName("EPSG:4326");
            crsProjected = crsFactory.createFromName(name);
            transform = ctFactory.createTransform(crsWgs84, crsProjected);
        }
        
        public Function<PointData, Point2D> projection() {
            return point -> {
                ProjCoordinate gcsPoint = new ProjCoordinate(point.getX(), point.getY());
                ProjCoordinate pcsPoint = new ProjCoordinate();
                pcsPoint = transform.transform(gcsPoint, pcsPoint);
                return new Point2D(pcsPoint.x, pcsPoint.y);
            };
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
