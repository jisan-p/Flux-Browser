# Third-party notices

## Readability

Flux bundles `src/main/resources/com/flux/browser/script/Readability.js` from the supplied Min checkout's `ext/readability-master/Readability.js`. The library extracts article text for Reader mode; Min itself is not a runtime dependency. The original source header is preserved without modification. Copyright and Apache License 2.0 terms are included in `src/main/resources/licenses/Readability-LICENSE.md` and packaged with the application. Upstream: https://github.com/mozilla/readability.

## Optional filter updates

The built-in starter set consists of domain names. Selecting Update blocklist retrieves the public AdGuard DNS Filter, extracts supported plain domain rules, and saves them to the user's profile. The downloaded filter is not bundled in the application. Attribution, component sources and licensing are published at https://github.com/AdguardTeam/AdGuardSDNSFilter. Flux's domain-only importer does not implement the entire Adblock syntax.

## Dependencies and system frameworks

Gson is distributed under Apache License 2.0; its license is included in the Maven artifact. OpenJFX, pgJDBC and Maven plugin dependencies retain their upstream license notices. macOS WebKit, Security and PDFKit are linked system frameworks, not copied libraries.
