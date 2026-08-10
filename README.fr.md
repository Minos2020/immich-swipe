# Immich Swipe

![GitHub release (latest by date)](https://img.shields.io/github/v/release/markvoronin354/immich-swipe-android)
![GitHub License](https://img.shields.io/github/license/markvoronin354/immich-swipe-android)
![Kotlin](https://img.shields.io/badge/langage-Kotlin-purple)
![Android](https://img.shields.io/badge/plateforme-Android-green)

[English version here](README.md)

Immich Swipe est une application Android open-source conçue pour faciliter le tri de vos photos et vidéos hébergées sur votre serveur [Immich](https://immich.app/).

Inspirée par l'application [Sponge](https://play.google.com/store/apps/details?id=com.prismtree.sponge&pcampaignid=web_share) (concept similaire pour les fichiers locaux), elle vous permet de trier rapidement vos médias par de simples gestes de balayage (swipe), avec une interface fluide et moderne.

> **Note** : Ce projet est né d'un besoin personnel et a été développé avec l'aide de l'IA. Bien que je ne sois pas développeur de métier, j'accorde une grande importance à la stabilité et à l'expérience utilisateur.
>
> **Avertissement** : Ce projet est indépendant et n'est affilié d'aucune façon avec le projet officiel Immich.

## 📸 Aperçu

|                                         Écran d'accueil                                         |                                     La pile de tri                                      |                                        Mode Revue                                        |
|:-----------------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------------:|
| <img src="metadata/en-US/images/phoneScreenshots/01_Light_HomeScreen_GridView.png" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/02_Light_SwipeScreen.png" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/03_Light_ReviewScreen.png" width="200"> |
|                                     *Parcourez vos albums*                                      |                                  *Swipez pour décider*                                  |                              *Vérifiez avant de supprimer*                               |

<details>
<summary>🌙 <b>Voir la galerie en Mode Sombre</b></summary>

|                                        Écran d'accueil                                         |                                     La pile de tri                                     |                                       Mode Revue                                        |                                       Paramètres                                       |
|:----------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------:|
| <img src="metadata/en-US/images/phoneScreenshots/05_Dark_HomeScreen_GridView.png" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/07_Dark_SwipeScreen.png" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/08_Dark_ReviewScreen.png" width="200"> | <img src="metadata/en-US/images/phoneScreenshots/Dark_SettingsScreen.png" width="200"> |

</details>

## ✨ Fonctionnalités

- **🚀 Pile de tri Rapide** : Swipez à droite pour garder, à gauche pour supprimer. Les médias triés disparaissent de la timeline en temps réel pour une expérience fluide.
- **🕒 Snooze (SKIP)** : Un doute ? Passez-le (SKIP). Les médias réapparaissent automatiquement après un délai configurable (1 jour, 1 mois, ou jamais).
- **📁 Collections (Albums Virtuels)** : Accédez à des groupes spéciaux comme la **Collection SKIPs** pour revoir tous vos médias passés au même endroit.
- **📊 Statistiques Globales** : Visualisez votre progression avec des statistiques détaillées et une répartition de vos actions de tri.
- **🛡️ Mode Revue Avancé** : Vérifiez toutes vos décisions (supprimer, garder, archiver, verrouiller) et estimez l'espace libéré avant la synchronisation avec Immich.
- **🔄 Support Multi-Comptes** : Déconnectez-vous et changez d'utilisateur facilement ; vos décisions locales et votre progression sont conservées pour chaque compte.
- **🗃️ Gestion de la Base de Données** : Exportez, importez ou videz votre base locale (globalement ou par utilisateur) pour sauvegarder vos données.
- **🚦 Diagnostic de Connexion** : Indicateur en temps réel, support HTTP et IP directes, et accès aux journaux (logs) pour faciliter le dépannage.
- **🎨 Interface Moderne** : Développée avec Jetpack Compose et Material Design 3, disponible en Français, Anglais et Espagnol.

## ⚙️ Configuration

1. Entrez l'URL de votre serveur Immich (ex: `https://immich.votre-domaine.fr`).
2. Entrez votre clé API Immich (si vous n'en avez pas, [créez en une](https://my.immich.app/user-settings?isOpen=api-keys))
   - **Permissions nécessaires** : Pour le bon fonctionnement de l'application, votre clé API doit avoir les permissions suivantes :
     - `user.read`
     - `album.read`
     - `asset.read`
     - `asset.view`
     - `asset.statistics`
     - `asset.delete`
     - `asset.update` (Optionnel --> si vous souhaitez pouvoir archiver des assets, les ajouter aux favoris ou encore les envoyer dans le dossier verrouillé)
     - `userProfileImage.read` (Optionnel --> permet d'afficher l'image du profil utilisateur)
3. Sélectionnez un album et commencez à trier !

## 📦 Installation

  |                                                                                                                       **Obtainium**                                                                                                                        |   **Téléchargement Direct**   |  **IzzyOnDroid / F-Droid**    |
  |:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------:|:----------------------------:|
  | [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png"  alt="Get it on Obtainium" height="50">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.markvoronin.immichswipe%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FMinos2020%2Fimmich-swipe%22%2C%22author%22%3A%22Minos2020%22%2C%22name%22%3A%22Immich%20Swipe%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22github-creds%5C%22%3A%5C%22%5C%22%2C%5C%22GHReqPrefix%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3A%22GitHub%22%7D) | Récupérez le dernier APK dans la section [Releases](https://github.com/markvoronin354/immich-swipe-android/releases)    |  Prochainement disponible


## 🛠️ Build

Si vous souhaitez compiler l'application vous-même :

- **JDK 17** ou supérieur requis.
- **Android Studio** (Version Ladybug ou plus récente recommandée).
- Clonez le dépôt et importez le projet dans Android Studio.
- Synchroniser Gradle
- Utilisez `./gradlew assembleDebug` pour générer un APK de test.

## 📄 Licence

Ce projet est sous licence GNU GPL v3. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

## ⚖️ Responsabilité

Bien que ce projet soit développé avec le plus grand soin et testé régulièrement, je ne peux garantir la sécurité absolue de vos données. En utilisant Immich Swipe, vous acceptez que l'auteur ne puisse être tenu responsable en cas de perte de données ou de suppression involontaire de médias.

Il est important de noter que :
- **Sécurité de la corbeille** : Immich Swipe ne vide jamais la corbeille de votre serveur Immich. En cas d'erreur de tri, vos photos restent récupérables via l'interface officielle d'Immich pendant la durée de conservation configurée de la corbeille.
- **Principe de moindre privilège** : Afin de minimiser les risques, il est vivement conseillé de ne configurer votre clé API qu'avec les permissions strictement nécessaires listées dans la section [Configuration](#configuration).
