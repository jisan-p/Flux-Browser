# Third-party notices

## Readability

Flux bundles `src/main/resources/com/flux/browser/script/Readability.js` from the supplied Min checkout's `ext/readability-master/Readability.js`. The library extracts article text for Reader mode; Min itself is not a runtime dependency. The original source header is preserved without modification. Copyright and Apache License 2.0 terms are included in `src/main/resources/licenses/Readability-LICENSE.md` and packaged with the application. Upstream: https://github.com/mozilla/readability.

## Optional filter updates

The built-in starter set consists of domain names. Selecting Update blocklist retrieves the public AdGuard DNS Filter, extracts supported plain domain rules, and saves them to the user's profile. The downloaded filter is not bundled in the application. Attribution, component sources and licensing are published at https://github.com/AdguardTeam/AdGuardSDNSFilter. Flux's domain-only importer does not implement the entire Adblock syntax.

## Dependencies and system frameworks

Gson is distributed under Apache License 2.0; its license is included in the Maven artifact. OpenJFX, pgJDBC and Maven plugin dependencies retain their upstream license notices. macOS WebKit, Security and PDFKit are linked system frameworks, not copied libraries.

## Eruda (retained compatibility development)

The locally bundled `src/main/resources/com/flux/browser/script/eruda.min.js` is Eruda 3.4.3, from [liriliri/eruda](https://github.com/liriliri/eruda/tree/v3.4.3). It is retained for future JavaFX compatibility work and is not loaded by v1’s native Mac engine. The MIT license and copyright notice are included in `src/main/resources/licenses/Eruda-LICENSE.txt`.

## Flux appearance assets

The wave, aurora and grid backgrounds are original procedural artwork in `WallpaperCanvas.java`. The short click/type WAV clips are original synthesized tones. Opera GX artwork, fonts and sound packs are not bundled. The installed Opera GX UI was used as a visual reference; Flux does not include Opera AI or claim to be Opera GX.
