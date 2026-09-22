import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
public class test_fs2 extends Application {
    @Override public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setFullScreen(true);
        Scene scene = new Scene(new StackPane(), 800, 600);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.show();
    }
}
