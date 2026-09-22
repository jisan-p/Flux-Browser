import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
public class test_fs extends Application {
    @Override public void start(Stage stage) {
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setFullScreen(true);
        stage.setScene(new Scene(new StackPane(), 800, 600));
        stage.show();
    }
}
