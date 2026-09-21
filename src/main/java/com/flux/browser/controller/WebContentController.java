package com.flux.browser.controller;

import javafx.fxml.FXML;
import javafx.scene.web.WebView;

/** FXML-owned WebView, instantiated only when its tab first navigates. */
public final class WebContentController {
    @FXML private WebView webView;
    public WebView view() { return webView; }
}
