package org.custombrowser.cssomparser;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.reader.CSSReader;

public class CssomParser {

    public List<CascadingStyleSheet> parse(Document dom) {
        if (dom == null) {
            System.err.println("Error: Document is empty.");
            return List.of(); // return empty list, not null (standard Java convention)
        }

        System.out.println("Building the CSSOM tree...");
        List<CascadingStyleSheet> cssom = new ArrayList<>();
        
        Elements styleElements = dom.select("style"); // selects all the <style> blocks in the html & returns an arraylist

        System.out.println("Found " + styleElements.size() + " style blocks in this page");

        //CSSWriterSettings writerSettings = new CSSWriterSettings(ECSSVersion.CSS30);

        for (Element styE : styleElements) {
            String cssContent = styE.html();
            CascadingStyleSheet css = CSSReader.readFromString(cssContent, ECSSVersion.CSS30); // parses the raw css string & builds the CSSOM tree
            
            if (css != null) {
                cssom.add(css);
            }
        }
        
        return cssom;
    }

}
