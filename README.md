# Yandex Ads Patches

[ReVanced](https://revanced.app) patches that remove ads from Yandex apps.

## 🧩 Patches

### Yandex Weather (`ru.yandex.weatherplugin`)

| Patch | Description |
|-------|-------------|
| Hide ads | Stops the Yandex Mobile Ads SDK from loading banner, native, interstitial, rewarded, app open and instream ads. Ad loads fail immediately and banner views collapse. |
| Disable account library verification | Prevents the Yandex account library from closing the app on start, because the patched app is not signed by Yandex. Signing in to a Yandex account may not work. |
| Remove shared permissions | Removes permissions shared with other Yandex apps and the original app, so the patched app installs next to them. Signing in with an account from other Yandex apps will not work. |
| Rename content providers | Renames the content providers that "Change package name" does not rename, so the patched app installs and starts next to the original app. |

Tested with Yandex Weather 26.9.50.

## 🚀 Usage

### 1. Add the patch bundle to ReVanced Manager

In ReVanced Manager, add a remote patch bundle with this URL:

```
https://nuc134r.github.io/v5/patches/bundle.json
```

Manager then updates the patches when a new version is released and shows their changelog.

### 2. Get the app

Download Yandex Weather as an APK or a split APK bundle (`.xapk`, `.apkm`), for example from APKPure, and select it in Manager.

### 3. Select the patches

| Patch | Bundle | Setting |
|-------|--------|---------|
| Hide ads | Yandex Ads Patches | On |
| Disable account library verification | Yandex Ads Patches | On |
| Remove shared permissions | Yandex Ads Patches | On |
| Rename content providers | Yandex Ads Patches | On |
| Change package name | ReVanced Patches | On, with **Update permissions** and **Update providers** enabled |

"Change package name" installs the patched app as `ru.yandex.weatherplugin.revanced`,
next to the original Yandex Weather and other Yandex apps.
Without it, uninstall the original app first.

Other patches, such as "Disable Pairip license check", are not needed.

### 4. Patch and install

Patch the app and install it.

## ⚠️ Known limitations

- Signing in to a Yandex account in the patched app may not work.
  The app is not signed by Yandex, so the Yandex account library rejects it.
  Weather, locations and widgets work without an account.
- Ads that are not loaded by the Yandex Mobile Ads SDK are not removed.

## 🛠️ Building

Building requires a GitHub account, because the ReVanced dependencies are published to GitHub Packages.
Create a token with the `read:packages` scope and add it to `~/.gradle/gradle.properties`:

```properties
gpr.user=<GitHub username>
gpr.key=<token>
githubPackagesUsername=<GitHub username>
githubPackagesPassword=<token>
```

Then build the patches:

```bash
./gradlew build
```

The patches are saved to `patches/build/libs/patches-<version>.rvp`.

## 🔁 Releasing

Releases are made automatically by the [release workflow](.github/workflows/release.yml)
when commits are pushed to `main`.
The version is derived from [conventional commit](https://www.conventionalcommits.org) messages:
`fix:` releases a patch version and `feat:` a minor version.
Each release updates [patches-bundle.json](patches-bundle.json)
and the bundle on [nuc134r.github.io](https://github.com/nuc134r/nuc134r.github.io).

## 🧪 Testing

The [Test Yandex Weather](.github/workflows/test_yandex_weather.yml) workflow patches the app,
installs it next to the original app on an Android emulator and checks that it starts.
It takes the APK from a release asset, which can be attached to a draft release,
and is started manually from the Actions tab.

## 📜 License

These patches are licensed under the [GPLv3](LICENSE) license.
