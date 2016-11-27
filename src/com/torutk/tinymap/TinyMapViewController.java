/*
 * © 2016 Toru Takahashi
 */
package com.torutk.tinymap;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 *
 * @author Toru Takahashi
 */
public class TinyMapViewController implements Initializable {
    
    @FXML
    private Label scaleLabel;
    @FXML
    private ComboBox projectionComboBox;
    @FXML
    private Canvas mapCanvas;
    @FXML
    private Pane rootPane;
    
    private TinyMapModel mapModel;
    
    @FXML
    private void loadShapefile(ActionEvent event) {
        System.out.println("You clicked me!");
        mapModel = new TinyMapModel();
        mapModel.loadLines();
        clearMapCanvas();
        drawMapCanvas();
    }

    /**
     * 地図表示領域を背景色（海）で塗りつぶす。
     */
    private void clearMapCanvas() {
        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.setFill(Color.MIDNIGHTBLUE);
        gc.fillRect(0, 0, mapCanvas.getWidth(), mapCanvas.getHeight());
    }   

    private void drawMapCanvas() {
        mapModel.stream().forEach(polyline -> {
            GraphicsContext gc = mapCanvas.getGraphicsContext2D();
            gc.setStroke(Color.LIGHTGREEN);
            gc.strokePolyline(polyline.getXArray(), polyline.getYArray(), polyline.size());
        });
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        mapCanvas.widthProperty().bind(rootPane.widthProperty().subtract(120));
        mapCanvas.heightProperty().bind(rootPane.heightProperty());
    }    
    
}
