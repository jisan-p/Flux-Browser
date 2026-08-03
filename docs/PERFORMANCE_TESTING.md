# Performance Testing

Phase 6 supplies reproducible observation points rather than machine-specific
pass/fail millisecond limits. Compare measurements on the same machine, JDK,
display server, database state, and build revision.

## Built-in measurements

Start Flux normally:

```bash
mvn javafx:run
```

The console prints `Flux performance [startup]` lines for:

- `startup.persistence`
- `startup.context`
- `startup.fxml`
- `startup.css-layout`
- `startup.total`

The shutdown summary additionally reports `tab.create`, `tab.close`, and
`database.operation` count, total, average, and maximum time. Each summary also
reports used, committed, and maximum JVM heap in MiB. No address, page title,
search term, or download path is logged.

## Repeatable lifecycle sample

1. Start Flux and wait 30 seconds without opening GX Control. Record startup and
   idle heap values.
2. Create 20 Start Page tabs. They should not cause page engines or network
   loads; startup pages use no WebView until navigation.
3. Navigate in five tabs to a local fixture, close all but one, and wait 30
   seconds. Repeat this open/navigate/close cycle five times.
4. Open GX Control for 70 seconds, then close it. Charts must remain at 60
   samples and sampling must pause when the panel closes.
5. Close Flux normally and save the shutdown performance lines.

Use a local deterministic page instead of an internet site:

```bash
python3 -m http.server 8765 --bind 127.0.0.1 \
  --directory src/test/resources/manual
```

Open `http://127.0.0.1:8765/phase2-browser-test.html`.

Expected healthy behavior: the UI remains responsive, terminal download tasks
never exceed 100, favicon images never exceed 256, chart series never exceed 60,
closed tab engines are disposed, and post-cycle heap settles instead of growing
in direct proportion to every closed tab. JVM and WebKit caches may retain some
memory, so an exact return to the initial heap is not expected.

## Optional JDK observations

Find the process and inspect its heap from another terminal:

```bash
jps -l
jcmd <PID> GC.heap_info
jcmd <PID> Thread.print
```

Expected: one `flux-persistence` thread, up to three `flux-download` threads,
and a `flux-resource-monitor` thread. The monitor thread remains alive as a
daemon but does no scheduled sampling while GX Control is closed. After normal
application exit, the Flux JVM and all these threads disappear.

Do not run profiling commands with sudo. If `jcmd` reports that it cannot attach,
run it as the same operating-system user and with the same JDK that launched
Flux.
