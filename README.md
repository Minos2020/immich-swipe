# Immich Swipe

![GitHub release (latest by date)](https://img.shields.io/badge/Release-2.4.1-blue)
![GitHub License](https://img.shields.io/github/license/markvoronin354/immich-swipe-android)
![Kotlin](https://img.shields.io/badge/language-Kotlin-purple)
![Android](https://img.shields.io/badge/platform-Android-green)



Immich Swipe is an open-source Android application designed to make sorting your photos and videos hosted on your [Immich](https://immich.app/) server easy and fun

This is not my project. you can find the original project at (https://github.com/Minos2020/immich-swipe-android.git). I created this fork to act as a rough draft for new ideas and features that I would love to see added in the future.


> **Disclaimer**: This is an independent project and is not affiliated in any way with the official Immich project.

## 📸 Overview

|                                         Home Screen                                          |                                    Sorting Stack                                     |                                       Review Mode                                        |
|:--------------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------:|
| <img src="metadata/en-US/images/phoneScreenshots/10_Light_HomeScreen_Listview.jpg" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/09_Dark_SwipeScreen.jpg" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/11_Dark_ReviewScreen.jpg" width="200"> |
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
     - `asset.download`
     - `asset.statistics`
     - `asset.update` (Optional --> if you want to archive assets, add them to favourites or to locked folder)
     - `userProfileImage.read` (Optional --> to show user profile image)
3. Select an album and start sorting!

## 📦 Installation

|                                                                                                                       **Orion Store**                                                                                                                        |   **Direct Download**   |  **IzzyOnDroid / F-Droid**    |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------:|:----------------------------:|
| [<img src="https://github.com/RookieEnough/Orion-Store/blob/main/assets/orion-badge.png?raw=true"  alt="Get it on Orion Store" height="50">](https://rookieenough.github.io/Orion-Data/redirect.html?id=immich-swipe-android) | Get the latest APK from the [Releases](https://github.com/markvoronin354/immich-swipe-android/releases) section   |  Coming soon, hopefully


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
