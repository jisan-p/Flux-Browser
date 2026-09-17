package com.flux.browser.web;

import java.lang.reflect.Proxy;

/** Optional terminal diagnostics, never installed for normal browsing. */
final class JavaFxConsole {
    private static boolean attempted;
    private JavaFxConsole() { }
    static synchronized void installIfRequested() {
        if (attempted || !Boolean.getBoolean("flux.consoleLogging")) return;
        attempted = true;
        try {
            Class<?> listener = Class.forName("com.sun.javafx.webkit.WebConsoleListener");
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(object,method,args) -> {
                if (method.getName().equals("messageAdded")) {
                    // Explicit developer opt-in; cap each message to avoid runaway terminal output sizes.
                    String message = String.valueOf(args[1]), source = String.valueOf(args[3]);
                    System.out.printf("[WebConsole %s:%s] %s%n",source.substring(0,Math.min(512,source.length())),args[2],message.substring(0,Math.min(4096,message.length())));
                    return null;
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(object);
                    case "equals" -> object == args[0];
                    case "toString" -> "Flux console listener";
                    default -> null;
                };
            });
            listener.getMethod("setDefaultListener",listener).invoke(null,proxy);
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.err.println("Flux terminal console logging is unavailable; the in-page developer console is still available.");
        }
    }
}
