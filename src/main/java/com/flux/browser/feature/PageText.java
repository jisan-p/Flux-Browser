package com.flux.browser.feature;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class PageText {
    private PageText(){}
    public static final String INDEX = "(() => {if(document.querySelector('input[type=password]') || document.contentType!=='text/html')return ''; return (document.body?.innerText||'').slice(0,200000);})()";
    public static String readerScript() {
        try(var in=PageText.class.getResourceAsStream("/com/flux/browser/script/Readability.js")) {
            return "(() => {" + new String(in.readAllBytes(),StandardCharsets.UTF_8)
                + "\n;return (() => {const article=new Readability(document.cloneNode(true)).parse();"
                + "if(!article)return '';return JSON.stringify({title:article.title,text:article.textContent.slice(0,300000),byline:article.byline||''});})();})()";
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
}
