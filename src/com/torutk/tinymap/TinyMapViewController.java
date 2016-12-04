/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

import static java.lang.String.format;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.stage.Screen;

/**
 *
 * @author Toru Takahashi
 */
public class TinyMapViewController implements Initializable {
    
    private static final Logger logger = Logger.getLogger(TinyMapViewController.class.getName());
    private static final Affine IDENTITY_TRANSFORM = new Affine(); // 恒等変換（表示消去で使用）
    private static final double METER_PER_INCH = 0.0254; // インチからメートルへの換算値
    private static final double SCALE_RATE = 2; // 1段階の拡大縮小比
    
    @FXML
    private Label scaleLabel; // 縮尺表示用ラベル
    @FXML
    private ComboBox projectionComboBox; // 投影法選択コンボボックス
    @FXML
    private Canvas mapCanvas; // 地図描画領域
    @FXML
    private Pane rootPane;
    
    private Affine mapTransform = new Affine(); // 地図の拡大縮小スクロールの座標変換
    private DoubleProperty scaleProperty = new SimpleDoubleProperty(1); // 地図の拡大率
    private double dotPitchInMeter; // 実行環境でのドットピッチを保持
    private Point2D prevDragPoint; // 平行移動の開始点

    private TinyMapModel mapModel;
    
    /**
     * 地図データを読み込む。
     * 
     * @param event 
     */
    @FXML
    private void loadShapefile(ActionEvent event) {
        System.out.println("You clicked me!");
        mapModel = new TinyMapModel();
        mapModel.loadLines();
        drawMapCanvas();
    }

    /**
     * 地図表示領域を背景色（海）で塗りつぶす。
     */
    private void clearMapCanvas() {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.setTransform(IDENTITY_TRANSFORM);
        gc.setFill(Color.MIDNIGHTBLUE);
        gc.fillRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());
    }   

    /**
     * 地図の描画
     */
    private void drawMapCanvas() {
        logger.info(mapTransform.toString());
        clearMapCanvas();
        if (mapModel == null) {
            return;
        }
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.setTransform(mapTransform);
        gc.setStroke(Color.LIGHTGREEN);
        gc.setLineWidth(1d / scaleProperty.get());
        mapModel.stream().forEach(polyline -> {
            gc.strokePolyline(polyline.getXArray(), polyline.getYArray(), polyline.size());
        });
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // 実行環境でのドットピッチを計算
        dotPitchInMeter = 1 / Screen.getPrimary().getDpi() * METER_PER_INCH;
        // 拡大率の変更に縮尺ラベルの表示を連動
        scaleProperty.addListener((target, oldValue, newValue) -> {
            scaleLabel.setText(String.format("1/%,d", (int) (1 / (newValue.doubleValue() * dotPitchInMeter))));
        });
        // 初期縮尺の設定
        scaleProperty.set(1 / mapToScale(5_000));
        // ウィンドウサイズの変更に合わせて地図表示用Canvasの大きさを連動
        mapCanvas.widthProperty().bind(rootPane.widthProperty().subtract(120));
        mapCanvas.heightProperty().bind(rootPane.heightProperty());
        mapCanvas.widthProperty().addListener(event -> drawMapCanvas());
        mapCanvas.heightProperty().addListener(event -> drawMapCanvas());
        // 地図の拡大縮小平行移動の座標変換初期値
        mapTransform = new Affine(scaleProperty.get(), 0, 0, 0, -scaleProperty.get(), 0);
        // マウスホイールで拡大縮小
        mapCanvas.setOnScroll(event -> 
            zoom(event.getDeltaY() >= 0 ? scaleProperty.get() * SCALE_RATE : scaleProperty.get() / SCALE_RATE)
        );

        // ドラッグで平行移動するための開始場所保持
        mapCanvas.setOnMousePressed(event -> {
            prevDragPoint = new Point2D(event.getSceneX(), event.getSceneY());
        });
        // ドラッグで平行移動
        mapCanvas.setOnMouseDragged(event -> {
            Point2D dragPoint = new Point2D(event.getSceneX(), event.getSceneY());
            Point2D translate = dragPoint.subtract(prevDragPoint);
            prevDragPoint = dragPoint;
            mapTransform.setTx(mapTransform.getTx() + translate.getX());
            mapTransform.setTy(mapTransform.getTy() + translate.getY());
            drawMapCanvas();
        });
    }    
    


    /**
     * 地図縮尺（例： 1 / 10,000）をAffineのscaleに変換する.
     *
     * @param reduce 縮尺の母数（例： 10,000）
     * @return
     */
    double mapToScale(double reduce) {
        return reduce * dotPitchInMeter;
    }

    /**
     * mapCanvasの画面中心位置の地図座標を計算
     */
    private Point2D getCenterOfDisplayInMap() throws NonInvertibleTransformException {
        return mapTransform.inverseTransform(getCenterOfDisplay());
    }

    /**
     * mapCanvasの中心画面座標を返却する。
     * 
     * @return mapCanvasの中心画面座標
     */
    private Point2D getCenterOfDisplay() {
        return new Point2D(mapCanvas.getWidth() / 2, mapCanvas.getHeight() / 2);
    }
    
    /**
     * 地図表示の拡大・縮小処理。
     * <p>
     * 画面の真ん中を中心に拡大・縮小表示する。
     * <p>
     * 次の座標変換を行う。
     * <ul>
     * <li> Y軸の正負変換
     * <li> スケール変換
     * <li> 並行移動
     * </ul>
     * これらを合成すると次のAffine行列となる。
     * <pre>
     * | scale      0  画面の中心画素(x) - (画面の中心に位置する地図座標(x) * scale) | 
     * |     0  scale  画面の中心画素(y) + (画面の中心に位置する地図座標(y) * scale) |
     * </pre>
     * 
     * @param scale 拡大率
     */
    private void zoom(double scale) {
        try {
            scaleProperty.set(scale);
            Point2D centerOfDisplayInMap = getCenterOfDisplayInMap();
            Point2D centerOfDisplay = getCenterOfDisplay();
            double tx = centerOfDisplay.getX() - centerOfDisplayInMap.getX() * scale;
            double ty = centerOfDisplay.getY() + centerOfDisplayInMap.getY() * scale;
            mapTransform.setToTransform(
                    scaleProperty.get(), 0, tx,
                    0, -scaleProperty.get(), ty
            );
            drawMapCanvas();
        } catch (NonInvertibleTransformException ex) {
            Logger.getLogger(TinyMapViewController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
