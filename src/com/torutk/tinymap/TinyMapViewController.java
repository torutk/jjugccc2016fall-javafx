/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
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
import javafx.stage.Screen;

/**
 *
 * @author Toru Takahashi
 */
public class TinyMapViewController implements Initializable {

    private static final Affine IDENTITY_TRANSFORM = new Affine(); // 恒等変換（表示消去で使用）
    private static final double METER_PER_INCH = 0.0254; // インチからメートルへの換算値
    private static final double SCALE_RATE = 1.4; // 1段階の拡大縮小比

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

    private Point2D dragStartPoint; // 平行移動の開始点
    private Point2D mapTranslateAtDragStart; // 平行移動開始時点の地図の座標変換を保持
    private Point2D mapTranslate = new Point2D(0, 0); // 平行移動量

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
        scaleProperty.set(1 / mapToScale(200_000_000));
        // ウィンドウサイズの変更に合わせて地図表示用Canvasの大きさを連動
        mapCanvas.widthProperty().bind(rootPane.widthProperty().subtract(120));
        mapCanvas.heightProperty().bind(rootPane.heightProperty());
        mapCanvas.widthProperty().addListener(event -> drawMapCanvas());
        mapCanvas.heightProperty().addListener(event -> drawMapCanvas());
        // 地図の拡大縮小平行移動の座標変換初期値
        mapTransform = new Affine(scaleProperty.get(), 0, 0, 0, -scaleProperty.get(), 0);
        // マウスホイールで拡大縮小
        mapCanvas.setOnScroll(event -> {
            scaleProperty.set(
                    event.getDeltaY() >= 0 ? scaleProperty.get() * SCALE_RATE
                    : scaleProperty.get() / SCALE_RATE
            );
            mapTransform.setToTransform(
                    scaleProperty.get(), 0, mapTranslate.getX(),
                    0, -scaleProperty.get(), mapTranslate.getY()
            );
            drawMapCanvas();
        });
        // ドラッグで平行移動するための開始場所保持
        mapCanvas.setOnMousePressed(event -> {
            dragStartPoint = new Point2D(event.getSceneX(), event.getSceneY());
            mapTranslateAtDragStart = mapTranslate;
        });
        // ドラッグで平行移動
        mapCanvas.setOnMouseDragged(event -> {
            Point2D dragPoint = new Point2D(event.getSceneX(), event.getSceneY());
            mapTranslate = mapTranslateAtDragStart.add(dragPoint.subtract(dragStartPoint));
            mapTransform.setToTransform(
                    scaleProperty.get(), 0f, mapTranslate.getX(),
                    0f, -scaleProperty.get(), mapTranslate.getY()
            );
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
}
