package org.custombrowser.settings;

import java.net.CookieManager;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.custombrowser.browser.FaviconService;
import org.custombrowser.browser.PopupPolicyService;
import org.custombrowser.persistence.PersistenceService;

/**
 * Explicitly supported data-clearing operations. JavaFX WebView does not
 * expose a supported API for clearing its internal HTTP cache.
 */
public final class BrowsingDataService {

    private final CookieManager cookieManager;
    private final FaviconService faviconService;
    private final PopupPolicyService popupPolicyService;
    private final PersistenceService persistenceService;

    public BrowsingDataService(
            CookieManager cookieManager,
            FaviconService faviconService,
            PopupPolicyService popupPolicyService,
            PersistenceService persistenceService) {
        this.cookieManager = Objects.requireNonNull(
                cookieManager, "cookieManager");
        this.faviconService = Objects.requireNonNull(
                faviconService, "faviconService");
        this.popupPolicyService = Objects.requireNonNull(
                popupPolicyService, "popupPolicyService");
        this.persistenceService = Objects.requireNonNull(
                persistenceService, "persistenceService");
    }

    public int clearCookies() {
        int count = cookieManager.getCookieStore().getCookies().size();
        cookieManager.getCookieStore().removeAll();
        return count;
    }

    public void clearFavicons() {
        faviconService.clearCache();
    }

    public CompletableFuture<Void> clearHistory() {
        return persistenceService.clearVisits();
    }

    public CompletableFuture<Void> clearDownloadMetadata() {
        return persistenceService.clearDownloads();
    }

    public CompletableFuture<Void> clearSessionData() {
        return persistenceService.clearSessionData();
    }

    public void resetPopupPermissions() {
        popupPolicyService.reset();
    }

    public String webViewCacheLimitation() {
        return "JavaFX WebView does not expose a supported cache-clearing API.";
    }
}
