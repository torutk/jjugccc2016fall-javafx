/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

import com.torutk.tinymap.TinyMapModel.MapProjection;
import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;
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
    private static final double SCALE_RATE = 1.4; // 1段階の拡大縮小比

    @FXML
    private Label scaleLabel; // 縮尺表示用ラベル
    @FXML
    private ComboBox<MapProjection> projectionComboBox; // 投影法選択コンボボックス
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
     * ファイル選択ダイアログを表示し、ユーザーが指定したシェープファイルから地図データを読み込む。
     *
     * @param event
     */
    @FXML
    private void loadShapefile(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("シェープファイルを選択してね");
        chooser.setInitialDirectory(Paths.get(System.getProperty("user.dir")).toFile());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Shapefile", "*.shp"));
        File selected = chooser.showOpenDialog(mapCanvas.getScene().getWindow());
        if (selected == null) {
            return;
        }
        mapModel = new TinyMapModel(selected);
        MapProjection mapProj = projectionComboBox.getValue();
        if (mapProj != null) {
            mapModel.setProjection(mapProj.projection());
        }
        try {
            mapModel.loadLines();
        } catch (TinyMapException ex) {
            showError("シェープファイルの読み込みでエラーが発生しました。", ex);
        }
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
        // 投影法コンボボックス
        ObservableList<MapProjection> list =
                FXCollections.observableArrayList(MapProjection.values());
        projectionComboBox.getItems().addAll(list);
        // 実行環境でのドットピッチを計算
        dotPitchInMeter = 1 / Screen.getPrimary().getDpi() * METER_PER_INCH;
        // 拡大率の変更に縮尺ラベルの表示を連動
        scaleProperty.addListener((target, oldValue, newValue) -> {
            scaleLabel.setText(String.format("1/%,d", (int) (1 / (newValue.doubleValue() * dotPitchInMeter))));
        });
        // 初期縮尺の設定
        scaleProperty.set(1 / mapToScale(200_000_000));
        // ウィンドウサイズの変更に合わせて地図表示用Canvasの大きさを連動
        mapCanvas.widthProperty().bind(rootPane.widthProperty().subtract(120));
        mapCanvas.heightProperty().bind(rootPane.heightProperty());
        mapCanvas.widthProperty().addListener(event -> drawMapCanvas());
        mapCanvas.heightProperty().addListener(event -> drawMapCanvas());
        // 地図の拡大縮小平行移動の座標変換初期値
        mapTransform = new Affine(scaleProperty.get(), 0, 0, 0, -scaleProperty.get(), 0);
        // マウスホイールで拡大縮小（タッチパネル対応）
        mapCanvas.setOnScroll(event -> {
            if (event.getTouchCount() != 0 || event.isInertia()) {
                logger.finer("This is not pinch or wheel event, so skip zoom.");
                return;
            }
            zoom(event.getDeltaY());
        });
        // タッチパネルで拡大縮小
        mapCanvas.setOnZoom(event -> zoom(event.getZoomFactor() - 1d));
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

    private void showError(String message, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Tiny Map Viewer Message");
        String exMessage = (ex.getCause() != null) ? ex.getCause().getLocalizedMessage()
                : ex.getLocalizedMessage();
        alert.setContentText(String.format("%s%n%s", message, exMessage));
        alert.showAndWait();
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
     * @param scaleFactor 拡大か縮小かを正負で指定（符号のみ使用し、値は使用しない）
     */
    private void zoom(double scaleFactor) {
        double scale = scaleFactor >= 0 ? scaleProperty.get() * SCALE_RATE : scaleProperty.get() / SCALE_RATE;
        logger.info(String.format("scale factor = %f, calculated scale = %f%n", scaleFactor, scale));
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
            logger.log(Level.WARNING, "Cannot inverse from screen to map coordinate", ex);
        }
    }
    
}
