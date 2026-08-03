package org.custombrowser.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;

import org.custombrowser.browser.FaviconService;
import org.custombrowser.browser.PopupPolicyService;
import org.custombrowser.persistence.PersistenceService;
import org.junit.jupiter.api.Test;

class BrowsingDataServiceTest {

    @Test
    void clearsCookiesFromOwnedCookieStore() {
        CookieManager cookies = new CookieManager();
        cookies.getCookieStore().add(
                URI.create("https://example.com"),
                new HttpCookie("flux", "value"));
        PersistenceService persistence = PersistenceService.forTests();
        try {
            BrowsingDataService service = new BrowsingDataService(
                    cookies,
                    new FaviconService(),
                    new PopupPolicyService(persistence),
                    persistence);

            assertEquals(1, service.clearCookies());
            assertTrue(cookies.getCookieStore().getCookies().isEmpty());
            assertTrue(service.webViewCacheLimitation().contains("does not expose"));
        } finally {
            persistence.close();
        }
    }
}
