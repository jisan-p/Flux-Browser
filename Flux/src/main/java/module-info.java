module com.flux.browser {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires org.postgresql.jdbc;
    // Optional smoke checks use these JDK APIs; the browser does not start an HTTP server.
    requires static jdk.httpserver;
    requires static java.desktop;

    exports com.flux.browser;
    opens com.flux.browser.controller to javafx.fxml;
}
