# Immich Swipe

![GitHub release (latest by date)](https://img.shields.io/github/v/release/minos2020/immich-swipe)
![GitHub License](https://img.shields.io/github/license/minos2020/immich-swipe)
![Kotlin](https://img.shields.io/badge/language-Kotlin-purple)
![Android](https://img.shields.io/badge/platform-Android-green)

[Version française ici](README.fr.md)

Immich Swipe is an open-source Android application designed to make sorting your photos and videos hosted on your [Immich](https://immich.app/) server easy and fun.

Inspired by the [Sponge](https://play.google.com/store/apps/details?id=com.prismtree.sponge&pcampaignid=web_share) app (similar concept for local files), it allows you to quickly sort your media using simple swipe gestures, featuring a smooth and modern interface.

> **Note**: This project started as a personal need and was developed with the help of AI. Although I am not a developer by trade, I place great importance on stability and user experience.
>
> **Disclaimer**: This is an independent project and is not affiliated in any way with the official Immich project.

## 📸 Overview

|                                         Home Screen                                          |                                    Sorting Stack                                     |                                       Review Mode                                        |
|:--------------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------:|
| <img src="metadata/en-US/images/phoneScreenshots/06_Dark_HomeScreen_ListView.jpg" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/08_Dark_ReviewScreen.png.jpg" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/07_Dark_SwipeScreen.jpg" width="200"> |
|                                     *Browse your albums*                                     |                                  *Swipe to decide*                                   |                                 *Check before deleting*                                  |




## ✨ Features

- **🚀 Fast Sorting Stack**: Swipe right to keep, left to delete. Sorted assets disappear from the timeline in real-time for a cleaner experience.
- **📁 Collections (Virtual Albums)**: Access special groups to review all your skipped items in one place.
- **📊 Global Usage Stats**: Visualize your progress with detailed statistics and a breakdown of your sorting actions.
- **🛡️ Advanced Review Mode**: Review all your decisions (delete, keep, lock) and see estimated reclaimed space before syncing.
- **🔄 Multi-Account Support**: Switch between different users seamlessly; your local decisions and progress are preserved for each account.
- **🗃️ Database Management**: Export, import, or clear your local database (globally or per user) to safeguard your sorting data.
- **🚦 Connection Diagnostic**: Real-time status indicator, HTTP/direct IP support, and in-app logs for easy troubleshooting.
- **🎨 Modern Interface**: Jetpack Compose and Material Design 3, with support for English, French, and Spanish.

## ⚙️ Configuration

1. Enter your Immich server URL (e.g., `https://immich.your-domain.com`).
2. Enter your Immich API Key (if you don't have it already, [create one here](https://my.immich.app/user-settings?isOpen=api-keys))
   - **Required Permissions**: For the app to function correctly, your API key must have the following permissions:
     - `user.read`
     - `album.read`
     - `asset.read`
     - `asset.view`
     - `asset.delete`
     - `asset.statistics`
     - `asset.update` (Optional --> if you want to archive assets, add them to favourites or to locked folder)
     - `userProfileImage.read` (Optional --> to show user profile image)
3. Select an album and start sorting!

## 📦 Installation

|                                                                                                                       **Obtainium**                                                                                                                        |   **Direct Download**   |  **IzzyOnDroid / F-Droid**    |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------:|:----------------------------:|
| [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png"  alt="Get it on Obtainium" height="50">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.minos2020.immichswipe%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FMinos2020%2Fimmich-swipe%22%2C%22author%22%3A%22Minos2020%22%2C%22name%22%3A%22Immich%20Swipe%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22github-creds%5C%22%3A%5C%22%5C%22%2C%5C%22GHReqPrefix%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3A%22GitHub%22%7D) | Get the latest APK from the [Releases](https://github.com/minos2020/immich-swipe/releases) section   |  Coming soon, hopefully


## 🛠️ Build

If you want to compile the application yourself:

- **JDK 17** or higher required.
- **Android Studio** (Ladybug version or newer recommended).
- Clone the repository and import the project into Android Studio.
- Sync Gradle
- Use `./gradlew assembleDebug` to generate a test APK.

## 📄 License

This project is licensed under the GNU GPL v3. See the [LICENSE](LICENSE) file for more details.

## ⚖️ Disclaimer

While this project is developed with care and tested regularly, I cannot guarantee absolute data safety. By using Immich Swipe, you acknowledge that the author shall not be held liable for any data loss or accidental deletion of media.

Please keep in mind:
- **Trash Safety**: Immich Swipe never empties the trash on your Immich server. If you make a mistake while sorting, your photos remain recoverable through the official Immich interface during the configured trash retention period.
- **Principle of Least Privilege**: To minimize risks, it is highly recommended to configure your API key with only the strictly required permissions listed in the [Configuration](#configuration) section.
