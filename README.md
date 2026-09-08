# Wakh — Application Android

Messagerie vocale peer-to-peer en Kotlin / Jetpack Compose, avec un design
épuré bleu ciel. Voir le cahier des charges complet dans la conversation ;
résumé technique ci-dessous.

## Ouvrir le projet

1. Ouvrez ce dossier dans Android Studio (Koala ou plus récent recommandé).
2. Laissez Gradle synchroniser (JDK 17 requis).
3. Dans `app/build.gradle.kts`, le champ `SIGNALING_HOST` pointe par défaut
   vers `10.0.2.2:8000` (alias de `localhost` depuis l'émulateur Android
   pour joindre le serveur `wakh-server` lancé sur votre machine). Adaptez
   cette valeur si vous testez sur un appareil physique (IP locale de votre
   machine) ou si le serveur est déployé ailleurs.
4. Lancez le serveur `wakh-server` (voir son propre README) avant de tester
   l'envoi de messages.
5. `Run` sur un émulateur ou appareil (minSdk 26 / Android 8.0+).

## Ce qui a été implémenté

- **Numéro saisi manuellement, identification indépendante de l'indicatif sénégalais** : Wakh étant destiné aux numéros sénégalais (indicatif "+221", qui ne varie jamais pour ce public), l'inscription, la connexion (formulaire complet) et l'ajout de contact acceptent le numéro avec indicatif ("+221771234567"/"221771234567") ou sans ("771234567") — les séparateurs de présentation sont retirés (`util/PhoneNumberUtils.stripPhoneNumberSeparators`) puis l'indicatif "221" est retiré s'il est présent (`canonicalSenegalesePhoneNumber`) pour obtenir l'identifiant réel : les deux formes désignent donc désormais le **même** compte/contact. Si le numéro saisi porte un indicatif international explicite différent de "+221" (`hasNonSenegaleseCountryCode`), l'app affiche un avertissement ("Ce numéro ne semble pas être un numéro sénégalais...") avant de permettre de continuer quand même, tel quel (aucune transformation n'est appliquée à un numéro non sénégalais).
- **Reconnexion par code PIN seul** (`UserPreferences.rememberedIdentity`) : une fois connecté sur un appareil, nom et numéro y restent mémorisés même après déconnexion — se reconnecter sur ce même appareil ne demande alors que le code PIN. Un lien "Utiliser un autre compte" permet de repasser au formulaire complet (nom + numéro détecté/saisi + PIN) si besoin.
- **Ouverture de documents avec les applications installées** (`util/MediaStorage.openMediaExternally`) : sélecteur explicite "Ouvrir avec..." (`Intent.createChooser`) plutôt qu'une résolution implicite, avec message clair si aucune application installée ne peut ouvrir ce type de fichier (au lieu d'un échec silencieux).

- **Thème clair/sombre** (`ui/theme/`, `UserPreferences.themeMode`) : sélecteur Clair/Sombre/Système dans l'écran Paramètres. "Système" (par défaut) suit le réglage du téléphone ; un choix explicite est mémorisé et **persiste après déconnexion** (c'est un réglage de l'appareil, pas du compte). Techniquement : les couleurs propres à Wakh (`SurfaceWhite`, `AppBackground`, `BubbleReceived`...) sont devenues des propriétés composables réactives (`@Composable get()`) plutôt que des constantes fixes, ce qui a permis d'ajouter le thème sombre sans modifier aucun des écrans qui les utilisent déjà.
- **Partage vers d'autres applications** (`util/ShareUtils.kt`) : appui long sur une bulle de message → menu "Partager" / "Supprimer". Partager ouvre la feuille de partage système standard (texte pour un message texte, fichier via URI `content://` FileProvider pour audio/image/vidéo/document) — disponible pour tous les messages, envoyés comme reçus.

- **Envoi multiple d'images** (jusqu'à 10 à la fois, `ActivityResultContracts.PickMultipleVisualMedia`) et **documents** (PDF, Word, ZIP... jusqu'à 30 Mo, `ActivityResultContracts.OpenDocument`, nom et taille d'origine préservés et affichés) — selon exactement le même principe P2P/file d'attente que l'audio. Le sous-dossier `documents/` de l'espace privé de l'app est déclaré dans `res/xml/file_paths.xml` (FileProvider) : sans cette déclaration, l'ouverture externe d'un document échoue (un dossier non déclaré fait lever une exception par `FileProvider.getUriForFile`).
- **Menu Paramètres** (`ui/settings/`) : nom affiché modifiable (reste purement local), numéro en lecture seule, déconnexion — désormais regroupés ici plutôt qu'un simple bouton dans la liste des conversations.
- **Suppression "de part et d'autre"** (`MessageRepository.deleteForEveryone`) : pour un message que vous avez envoyé, choix entre "pour moi" (local) et "pour tout le monde" (demande de suppression envoyée au(x) destinataire(s), P2P si en ligne sinon file d'attente serveur — même principe que l'envoi d'un message). Un message reçu ne peut être supprimé que localement.

- **Groupes** (`ui/creategroup/`, `ui/groupinfo/`,
  `data/repository/GroupRepository.kt`) : création à partir des contacts
  existants (purement local, Room — `GroupEntity`/`GroupMemberEntity`). Un
  message envoyé à un groupe est transmis **individuellement à chaque
  membre** selon exactement le même principe que le 1-à-1 (P2P direct si
  en ligne, sinon file d'attente) — voir
  `MessageRepository.attemptGroupTextDelivery` /
  `attemptGroupMediaDelivery`. **Gestion après création** (appui sur le
  nom du groupe dans l'écran de discussion, `ui/groupinfo/GroupInfoScreen.kt`) :
  renommage, ajout de membres parmi les contacts existants, retrait d'un
  membre — l'historique des messages du groupe n'est jamais affecté par
  ces changements. **Simplification assumée** : statut agrégé par message
  (pas de suivi de livraison par membre dans l'UI), pas de rôle
  "administrateur" ni de "quitter le groupe" (retirer un membre se fait
  uniquement depuis cet écran, pour n'importe quel membre). Le serveur n'a
  aucune notion de "groupe" (voir README du serveur).

- **Compte obligatoire** : créer un compte (nom + numéro de téléphone +
  code PIN à 4 chiffres, `ui/common/PinInputField.kt`,
  `ui/auth/RegisterScreen.kt`) ou se connecter à un compte
  existant (`ui/auth/LoginScreen.kt`) est nécessaire avant toute autre
  action. Le jeton obtenu (`data/local/datastore/UserPreferences.kt`) est
  utilisé pour la connexion WebSocket et toutes les routes REST
  protégées. Si le serveur invalide le jeton (compte supprimé, connexion
  ailleurs...), l'application se déconnecte automatiquement et ramène à
  l'écran d'accueil (`data/remote/SignalingClient.kt`, propriété
  `authInvalidated`). Bouton de déconnexion manuelle dans la liste des
  conversations.
- **Ajout de contact** par numéro de téléphone (Room,
  `data/local/db/ContactEntity.kt`), avec **import direct depuis les
  contacts du téléphone** : sélecteur système de contacts
  (`ACTION_PICK` sur `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`,
  `ui/addcontact/AddContactScreen.kt` + `util/ContactsUtils.kt`) —
  aucune permission `READ_CONTACTS` requise, même principe de
  confidentialité que le sélecteur photo/vidéo système utilisé dans le
  chat. Le numéro et le nom pré-remplissent le formulaire, modifiables
  avant validation.
- **Messages lus/non lus** (`data/local/db/MessageEntity.kt` champ
  `isRead`, `MessageDao.observeUnreadCounts`) : un message reçu démarre
  non lu, et toute la conversation est marquée comme lue dès l'ouverture
  de l'écran de discussion et en continu tant qu'il reste ouvert
  (`ChatViewModel`). Badge bleu ciel + texte en gras dans la liste des
  conversations pour celles avec des messages non lus
  (`ui/conversations/ConversationsListScreen.kt`). Purement local :
  aucun accusé de lecture n'est renvoyé à l'expéditeur (contrairement à
  l'accusé de livraison "envoyé et reçu", qui lui transite réellement
  par le réseau).
- **Suppression de message** : appui long sur une bulle → confirmation →
  suppression de la base locale et du fichier associé s'il y en a un
  (`MessageRepository.deleteMessage`). Suppression "pour moi" uniquement
  — le contact conserve sa propre copie, il n'existe pas de mécanisme
  pour la supprimer à distance chez lui.
- **Enregistrement vocal** AAC/.m4a via `MediaRecorder`
  (`audio/AudioRecorder.kt`), bouton circulaire animé à maintenir
  (`ui/chat/components/RecordButton.kt`).
- **Visionneuse plein écran intégrée** (`ui/chat/components/MediaViewer.kt`) :
  ouverte au tap sur une bulle image/vidéo. Image : zoom au pincement et
  déplacement au doigt (`graphicsLayer` + `detectTransformGestures`, sans
  dépendance externe). Vidéo : lecture avec contrôles standard via
  ExoPlayer/Media3 (`androidx.media3:media3-exoplayer` +
  `media3-ui`, fichier local lu directement, pas de streaming). Un bouton
  secondaire permet d'ouvrir le média avec une application externe
  (visionneuse système, pour l'enregistrer ou le partager).
- **Images et vidéos, selon exactement le même principe que l'audio**
  (`webrtc/WebRtcManager.kt` méthode `sendFileMessage` généralisée pour
  les trois types binaires) : sélection via le sélecteur photo/vidéo
  système (`ActivityResultContracts.PickVisualMedia`, aucune permission
  requise), copie dans le stockage local de l'app, puis P2P direct ou
  repli en file d'attente hors-ligne (`data/remote/MediaQueueApi.kt`).
  Vignettes affichées via Coil (`ui/chat/components/MessageBubble.kt`),
  tap sur la bulle pour ouvrir la visionneuse plein écran intégrée
  (voir ci-dessus).
- **Envoi de texte, selon exactement le même principe que l'audio**
  (`webrtc/WebRtcManager.kt` méthode `sendTextMessage`, `data/remote/TextQueueApi.kt`) :
  tentative P2P directe via WebRTC DataChannel si le contact est en ligne
  (un seul message de contrôle JSON, pas de découpage nécessaire vu la
  taille), repli automatique en file d'attente hors-ligne côté serveur
  sinon, avec récupération automatique à la reconnexion. Champ de saisie
  unifié (`ui/chat/components/InputBar.kt`) : bouton d'envoi si du texte
  est présent, bouton micro sinon.
- **Envoi P2P direct** via WebRTC DataChannel (`webrtc/WebRtcManager.kt`) :
  le fichier est découpé en blocs de 16 Ko, encadrés par de petits messages
  de contrôle JSON (méta / fin / accusé de réception). Aucune piste
  audio/vidéo WebRTC n'est utilisée : uniquement le DataChannel.
- **Repli automatique** en file d'attente hors-ligne côté serveur si le
  destinataire n'est pas joignable, avec récupération automatique à sa
  reconnexion (`data/repository/MessageRepository.kt`).
- **Statuts de message** (envoi en cours / en attente hors-ligne / envoyé
  et reçu / échec avec bouton réessayer) et **indicateur en ligne/hors
  ligne** du contact, mis à jour par sondage périodique
  (`presence_check`) pendant que l'écran de discussion est ouvert.
- **Lecture** via `MediaPlayer` avec barre de progression, durée et
  horodatage dans des bulles façon messagerie (`ui/chat/components/MessageBubble.kt`).
- **Design** Material3 bleu ciel (`ui/theme/`), dégradé sur l'onboarding.

## Limitations connues (assumées pour ce projet)

- L'indicatif sénégalais est ignoré à l'identification (voir ci-dessus) :
  "771234567" et "+221771234567" désignent désormais le MÊME compte. Un
  numéro avec un autre indicatif n'est en revanche pas normalisé (l'app
  se contente d'avertir) : deux saisies différentes d'un même numéro
  étranger (ex. avec/sans le "+") resteraient deux identifiants
  distincts, comme avant pour tous les numéros.
- La reconnexion "PIN seul" (`UserPreferences.rememberedIdentity`) est
  propre à l'appareil : se connecter à un compte existant depuis un
  appareil qui ne l'a jamais utilisé demande toujours le formulaire
  complet (nom + numéro + PIN).
- Base de données Room en version 5 avec `fallbackToDestructiveMigration()` :
  toute évolution de schéma efface l'historique local existant au
  lancement suivant. À remplacer par de vraies `Migration` avant toute
  mise en production avec de vrais utilisateurs.
- Groupes : ajout/retrait de membre et renommage possibles après création
  (voir `ui/groupinfo/`), mais pas de rôle "administrateur" — n'importe
  qui ayant accès à l'appareil peut modifier n'importe quel groupe. Le
  réessai d'un message de groupe échoué renvoie à TOUS les membres (pas de
  suivi individuel de qui l'avait déjà reçu), ce qui peut créer un
  doublon chez un membre déjà servi.
- Une seule connexion P2P active à la fois (pas d'envois simultanés sur
  plusieurs conversations en parallèle).
- Pas de suppression de message ni de groupes — volontairement hors du
  périmètre demandé (messagerie individuelle uniquement).
- Images et vidéos : uniquement sélection depuis la galerie (sélecteur
  système, sans permission requise) — pas de capture caméra directement
  depuis l'app. Aucune limite de taille n'est appliquée côté client avant
  l'envoi P2P (seule la file d'attente hors-ligne applique les limites du
  serveur, 10 Mo/50 Mo par défaut) : un très gros fichier envoyé à un
  contact en ligne peut prendre du temps à transférer.
- Le service au premier plan (`service/SignalingForegroundService.kt`)
  garde le processus actif pour recevoir des appels entrants en arrière-plan,
  mais reste soumis aux politiques d'économie de batterie d'Android selon
  les constructeurs (Doze, App Standby...).
- La dépendance WebRTC (`io.github.webrtc-sdk:android`) est un binaire tiers
  qui suit les builds officiels de libwebrtc ; vérifiez la dernière version
  disponible sur Maven Central si celle épinglée ici n'existe plus. Même
  remarque pour Coil (`io.coil-kt:coil-compose`/`coil-video`) et Media3
  (`androidx.media3:media3-exoplayer`/`media3-ui`) : versions choisies au
  meilleur de la connaissance au moment de l'écriture, à reconfirmer lors
  du premier `./gradlew build`.
# cool_wakh_android
