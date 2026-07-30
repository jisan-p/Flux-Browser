package org.custombrowser.browser;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Owns tab ordering, selection, closed-tab recovery, and disposal.
 */
public final class TabManager implements AutoCloseable {

    private static final int MAX_CLOSED_TABS = 20;

    private final ObservableList<BrowserTab> tabs =
            FXCollections.observableArrayList();
    private final ObjectProperty<BrowserTab> activeTab =
            new SimpleObjectProperty<>();
    private final Deque<ClosedTabSnapshot> closedTabs = new ArrayDeque<>();
    private final FaviconService faviconService;
    private final Consumer<URI> externalNavigationHandler;

    public TabManager(
            FaviconService faviconService,
            Consumer<URI> externalNavigationHandler) {
        this.faviconService = Objects.requireNonNull(
                faviconService, "faviconService");
        this.externalNavigationHandler = Objects.requireNonNull(
                externalNavigationHandler, "externalNavigationHandler");
    }

    public ObservableList<BrowserTab> tabs() {
        return tabs;
    }

    public ObjectProperty<BrowserTab> activeTabProperty() {
        return activeTab;
    }

    public BrowserTab activeTab() {
        return activeTab.get();
    }

    public BrowserTab createTab() {
        return createTab(true, null);
    }

    public BrowserTab createTab(URI address) {
        return createTab(false, address);
    }

    public void select(BrowserTab tab) {
        if (tabs.contains(tab)) {
            activeTab.set(tab);
        }
    }

    public void close(BrowserTab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0) {
            return;
        }
        closedTabs.addFirst(ClosedTabSnapshot.from(tab));
        while (closedTabs.size() > MAX_CLOSED_TABS) {
            closedTabs.removeLast();
        }

        boolean wasActive = tab == activeTab.get();
        tabs.remove(index);
        tab.dispose();

        if (tabs.isEmpty()) {
            createTab();
        } else if (wasActive) {
            activeTab.set(tabs.get(Math.min(index, tabs.size() - 1)));
        }
    }

    public Optional<BrowserTab> reopenClosedTab() {
        ClosedTabSnapshot snapshot = closedTabs.pollFirst();
        if (snapshot == null) {
            return Optional.empty();
        }
        BrowserTab tab = createRestoredTab(snapshot.toTabState(false));
        tabs.add(tab);
        activeTab.set(tab);
        return Optional.of(tab);
    }

    public BrowserTab duplicate(BrowserTab source) {
        boolean startsOnStartPage = source.startPageProperty().get();
        URI address = startsOnStartPage
                ? null
                : validAddress(source.locationProperty().get());
        BrowserTab duplicate = createTab(startsOnStartPage, address);
        duplicate.setZoom(source.zoom());
        return duplicate;
    }

    public void closeOtherTabs(BrowserTab keep) {
        tabs.stream()
                .filter(tab -> tab != keep && !tab.pinnedProperty().get())
                .toList()
                .forEach(this::close);
        select(keep);
    }

    public void closeTabsToRight(BrowserTab anchor) {
        int index = tabs.indexOf(anchor);
        if (index < 0) {
            return;
        }
        tabs.subList(index + 1, tabs.size()).stream()
                .filter(tab -> !tab.pinnedProperty().get())
                .toList()
                .forEach(this::close);
    }

    public void move(BrowserTab tab, int targetIndex) {
        int oldIndex = tabs.indexOf(tab);
        if (oldIndex < 0) {
            return;
        }
        int clamped = Math.max(0, Math.min(targetIndex, tabs.size() - 1));
        if (oldIndex == clamped) {
            return;
        }
        tabs.remove(oldIndex);
        tabs.add(clamped, tab);
    }

    public void moveBy(BrowserTab tab, int offset) {
        move(tab, tabs.indexOf(tab) + offset);
    }

    public Optional<BrowserTab> find(UUID id) {
        return tabs.stream().filter(tab -> tab.id().equals(id)).findFirst();
    }

    public void selectRelative(int offset) {
        if (tabs.isEmpty()) {
            return;
        }
        int current = Math.max(0, tabs.indexOf(activeTab.get()));
        int next = Math.floorMod(current + offset, tabs.size());
        activeTab.set(tabs.get(next));
    }

    public void selectIndex(int index) {
        if (!tabs.isEmpty()) {
            int clamped = Math.max(0, Math.min(index, tabs.size() - 1));
            activeTab.set(tabs.get(clamped));
        }
    }

    public List<TabState> snapshot() {
        BrowserTab selected = activeTab.get();
        return tabs.stream()
                .map(tab -> TabState.from(tab, tab == selected))
                .toList();
    }

    public List<TabState> recentlyClosedSnapshot() {
        return closedTabs.stream()
                .map(snapshot -> snapshot.toTabState(false))
                .toList();
    }

    public void restoreSession(
            List<TabState> openTabs,
            List<TabState> recentlyClosed) {
        if (!tabs.isEmpty()) {
            throw new IllegalStateException(
                    "A session can only be restored into an empty TabManager");
        }
        BrowserTab selected = null;
        for (TabState state : openTabs) {
            BrowserTab tab = createRestoredTab(state);
            tabs.add(tab);
            if (state.selected()) {
                selected = tab;
            }
        }
        closedTabs.clear();
        recentlyClosed.stream()
                .limit(MAX_CLOSED_TABS)
                .map(ClosedTabSnapshot::from)
                .forEach(closedTabs::addLast);
        if (!tabs.isEmpty()) {
            activeTab.set(selected == null ? tabs.getFirst() : selected);
        }
    }

    @Override
    public void close() {
        List.copyOf(tabs).forEach(BrowserTab::dispose);
        tabs.clear();
        activeTab.set(null);
        closedTabs.clear();
    }

    private BrowserTab createTab(boolean startsOnStartPage, URI address) {
        BrowserTab tab = newBrowserTab(startsOnStartPage);
        tabs.add(tab);
        activeTab.set(tab);
        if (address != null) {
            tab.navigate(address);
        }
        return tab;
    }

    private BrowserTab newBrowserTab(boolean startsOnStartPage) {
        BrowserTab tab = new BrowserTab(
                faviconService,
                externalNavigationHandler,
                startsOnStartPage);
        tab.setPopupEngineSupplier(() -> {
            BrowserTab popup = createTab(false, null);
            return popup.engine();
        });
        return tab;
    }

    private BrowserTab createRestoredTab(TabState state) {
        BrowserTab tab = newBrowserTab(state.startPage());
        tab.restore(
                validAddress(state.address()),
                state.title(),
                state.pinned(),
                state.zoom(),
                state.startPage());
        return tab;
    }

    private static URI validAddress(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return null;
        }
        try {
            return URI.create(rawAddress);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record ClosedTabSnapshot(
            URI address,
            String title,
            boolean startPage,
            boolean pinned,
            double zoom) {

        static ClosedTabSnapshot from(BrowserTab tab) {
            return new ClosedTabSnapshot(
                    validAddress(tab.locationProperty().get()),
                    tab.titleProperty().get(),
                    tab.startPageProperty().get(),
                    tab.pinnedProperty().get(),
                    tab.zoom());
        }

        static ClosedTabSnapshot from(TabState state) {
            return new ClosedTabSnapshot(
                    validAddress(state.address()),
                    state.title(),
                    state.startPage(),
                    state.pinned(),
                    state.zoom());
        }

        TabState toTabState(boolean selected) {
            return new TabState(
                    UUID.randomUUID(),
                    address == null ? null : address.toString(),
                    title,
                    pinned,
                    selected,
                    zoom,
                    startPage);
        }
    }

    public record TabState(
            UUID id,
            String address,
            String title,
            boolean pinned,
            boolean selected,
            double zoom,
            boolean startPage) {

        public TabState {
            Objects.requireNonNull(id, "id");
        }

        static TabState from(BrowserTab tab, boolean selected) {
            return new TabState(
                    tab.id(),
                    tab.locationProperty().get(),
                    tab.titleProperty().get(),
                    tab.pinnedProperty().get(),
                    selected,
                    tab.zoom(),
                    tab.startPageProperty().get());
        }
    }
}
