package com.flux.browser;

import com.flux.browser.feature.*;
import java.time.*;

public final class ManagerChecks {
    public static void run() {
        var zone=ZoneId.of("America/New_York");
        var date=LocalDate.of(2026,3,8); // daylight-saving transition: a 23-hour local day
        var today=BrowsingPeriod.TODAY.range(date,zone);
        BrowserChecks.equal(Duration.between(today.from(),today.until()).toHours(),23L);
        BrowserChecks.check(today.contains(today.from()),"date range includes midnight");
        BrowserChecks.check(!today.contains(today.until()),"date range excludes next midnight");
        var yesterday=BrowsingPeriod.YESTERDAY.range(date,zone);
        BrowserChecks.equal(yesterday.until(),today.from());
        BrowserChecks.check(!BrowsingPeriod.OLDER.range(date,zone).contains(yesterday.from()),"older excludes yesterday");
        var downloads=new Downloads();
        downloads.update("{\"id\":1,\"name\":\"FILE.PDF\",\"status\":\"Downloading\",\"path\":\"/tmp/file.pdf\",\"received\":1,\"total\":4}");
        Instant started=downloads.items.getFirst().startedAt();
        downloads.update("{\"id\":1,\"name\":\"FILE.PDF\",\"status\":\"Complete\",\"path\":\"/tmp/file.pdf\",\"received\":4,\"total\":4}");
        BrowserChecks.equal(downloads.items.size(),1);
        BrowserChecks.equal(downloads.items.getFirst().startedAt(),started);
        BrowserChecks.equal(downloads.items.getFirst().kind(),"DOCUMENTS");
        downloads.items.add(new Downloads.Item(2,"movie.MP4","Downloading","",1,10));
        downloads.items.add(new Downloads.Item(3,"file.zip","Failed to save","",0,0));
        downloads.clearFinished();
        BrowserChecks.equal(downloads.items.size(),1);
        BrowserChecks.equal(downloads.items.getFirst().id(),2L);
        BrowserChecks.equal(downloads.items.getFirst().kind(),"VIDEO");
        System.out.println("ManagerChecks passed: local date/DST boundaries, stable download dates, file types and clearing preserves active downloads.");
    }
}
