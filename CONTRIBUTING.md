# Contributing to Tellyscope

Thanks for helping! Tellyscope is pre-alpha, so the code and its structure are still moving.

## Ground rules

- **Read the [safety rules](README.md#safety-rules) first.** A pull request that uninstalls packages, bypasses the protected list or skips the backup step won't be merged.
- **Open pull requests against `dev`.** `main` is the release branch and only takes `dev`.
- **One change per pull request.** Smaller is easier to review.
- **`./gradlew build` must pass** before you open a PR. CI runs the same command.
- **New files start with an SPDX header:** `// SPDX-License-Identifier: GPL-3.0-only`.
- **Licensing.** By contributing you agree that your work is licensed under GPL-3.0. If you adapt code or lists from another project, check that its license is compatible and add it to [NOTICE](NOTICE) in the same PR.

## Releases

- **Alpha:** every push to `dev` builds `app.tellyscope.alpha`, which installs next to the release, and replaces the `alpha` pre-release on GitHub with it.
- **Shipping:** a release is a pull request from `dev` into `main`, merged with a merge commit. It must raise `versionName` and `versionCode` in `app/build.gradle.kts` past the latest `v*` tag and add that version's section to [CHANGELOG.md](CHANGELOG.md); CI refuses anything else. Merging it tags `vX.Y.Z` and publishes a GitHub release with the signed APK, notes from the changelog section and the commits since the previous tag.
- **Signing:** CI signs both builds from three repository secrets: `RELEASE_KEYSTORE_BASE64` (the keystore, base64-encoded), `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`, which is both the store and the key password (keytool's PKCS12 default uses one for both). Only the maintainer holds the key; never commit a keystore. Local release builds are unsigned.

## Debloat lists

Package lists are the riskiest part of the app. When you add a package:

- Say which TV models you tested on, and what changed after disabling it.
- If disabling it broke anything (launcher, updates, remote, input, HDMI-CEC), it belongs on the protected list instead.

## Style

- Kotlin official code style, as set up in `.editorconfig`.
- Logic that doesn't need Android goes in `:adb`, with unit tests.
- UI uses Material 3 Expressive components and the app theme. No hard-coded colors.

## Reporting a TV

Open a [device report](../../issues/new?template=device_report.yml). Leave out serial numbers, IP addresses and account names.
