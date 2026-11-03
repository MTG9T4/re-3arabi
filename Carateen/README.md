# Carateen for Cloudstream / Android TV

This fork updates the existing Carateen provider from Abodabodd. It reads both complete cartoon catalogs from https://carateen.tv and streams videos on demand. Video files are not copied into this repository.

## Install

1. Open Cloudstream → Settings → Extensions → Add repository.
2. Use any repository name and enter `https://raw.githubusercontent.com/MTG9T4/re-3arabi/main/repo.json`.
3. Find and install **Carateen**. Enable Arabic in the language filter if needed.
4. Select **Carateen** on Home or Search. The rows are **كرتون أيام زمان** (classic cartoons), **سبيستون غو** (Spacetoon Go), and **البث المباشر** (live channels).
5. Open a show, select an episode, and play. Films with several editions label their links with the site's language/edition names.

Open a row's full list to continue browsing beyond its first page. Search checks both full catalogs. The catalog cache refreshes after 15 minutes; episode lists and playback links are fetched when opened. Streaming availability still depends on the source website and its video servers.

## Changes in version 2

- Classic cartoons appear first, with 40-title pages and continuation through the full catalog.
- Search no longer cuts off after 40 matches per catalog.
- Both encrypted API envelopes and plain JSON arrays/objects are accepted; malformed responses raise an error.
- The catalog cache expires so new titles become available without restarting Cloudstream.
- Numeric seasons and film edition names are retained; playback requests include the source site's Referer.
- The fork has its own install manifest and automated Carateen build. Publishing replaces Carateen's package/entry and preserves other providers.

## Validation on 2026-11-03

- Live catalog APIs returned 336 classic-cartoon titles and 443 Spacetoon titles.
- Sample films and series from both catalogs returned episode lists and HLS playlists. Grendizer returned 76 entries including opening/closing titles.
- Two seconds of actual video and audio were decoded from one classic-cartoon film and one Spacetoon episode using FFmpeg. This is a desktop check; playback in Cloudstream on an Android TV has not been verified.
- Four JVM regression tests cover plain catalogs/Arabic text, a real encrypted episode API fixture, live-channel JSON, and malformed responses.
- Android `.cs3` packaging and the tests passed locally with the repository's Gradle wrapper.

Catalog counts change over time. These checks sample playback; they do not certify every episode or live channel.

## Build

Use JDK 17 or newer and an Android SDK. Set `ANDROID_HOME` and `GITHUB_REPOSITORY=MTG9T4/re-3arabi`, then run:

```sh
./gradlew :Carateen:testDebugUnitTest :Carateen:writeCacheEntry
```

The outputs are `Carateen/build/Carateen.cs3` and `Carateen/build/plugin-entry.json`. The **Carateen** GitHub workflow runs these checks and publishes to `builds`. The inherited all-provider workflow remains available manually; Carateen's workflow does not require unrelated provider secrets.
