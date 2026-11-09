# Hilal'ify : Azan Times

Hilal'ify is an Android prayer-times app (Jetpack Compose, Material 3) that shows daily Islamic prayer times, counts down to the next prayer, and sends adhan alarms and status notifications. It uses an iOS-inspired UI with a liquid-glass look for dialogs and the bottom navigation bar.

- Package: `com.kceryrk.prayertimes`
- Min SDK 26 (Android 8.0), target SDK 34
- Current version: 1.2
- Prayer data: [Aladhan API](https://aladhan.com/prayer-times-api)
- City search: [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api)

## Features

### Prayer times
- Daily times for Fajr, Sunrise, Dhuhr, Asr, Maghrib, and Isha
- Next-prayer highlight, with a countdown that shows seconds under 10 minutes
- Past prayers are dimmed
- Localized Gregorian and Hijri dates
- Calculation method and related options in Settings

### Location
- GPS mode: detects the current city and country
- City mode: search for any city (type at least 3 letters) and save recent cities
- Tap the location label to change the city
- Country names are localized to the app language

### Qibla
- Compass screen that shows the Qibla bearing
- Indicator for when you are facing Qibla
- Requires location

### Adhan alarms and notifications
- Per-prayer adhan alarm toggles
- Optional permanent notification with:
  - Next prayer and countdown
  - Today's times (as rows or columns)
  - Transparent background
  - App name toggle
- Alarms are rescheduled after reboot

### Home-screen widget
- Shows the next prayer and today's times
- Adjustable background opacity

### Tools
- **Zikirmatik**: tap counter with reset, save, history, and clear. Right- or left-hand layout.
- **Compass**: standalone compass.

### Settings
- **General**: GPS on or off, location mode
- **Appearance**: theme (system, light, dark), accent color, language (system, English, Türkçe)
- **Prayer times**: calculation options
- **Adhan alarms**: per-prayer toggles
- **Permanent notification**: display options
- **Widget**: display options
- **About**: version, update checks, support link

### Updates
- Checks GitHub Releases for a newer version
- Downloads and installs the update in the app

### Interface
- Accent colors with a centered swatch picker
- iOS-style toggles
- Glass dialogs with blur, a thin border, and a lens distortion effect
- Searchable city picker that matches the home list style

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Detect the city for prayer times and Qibla |
| `INTERNET` | Fetch prayer times, city search, and update checks |
| `POST_NOTIFICATIONS` | Adhan alarms and the permanent notification |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `REQUEST_INSTALL_PACKAGES` | Install in-app updates |

## Tech stack
- Kotlin, Jetpack Compose, Material 3
- AndroidX WorkManager for periodic refresh (6, 12, or 24 hours)
- AppWidget for the home-screen widget
- Graphics and RenderEffect (API 33+) for the glass blur effects

## Building

```bash
./gradlew :app:assembleDebug
```

Open the project in Android Studio and run the `app` configuration on a device or emulator (Android 8.0+).

## Notes
- Background GPS for location updates while the app is closed is not implemented yet.
- Blur on dialogs uses RenderEffect and needs Android 13 (API 33) or newer. Older versions show the dialog without the blur.
