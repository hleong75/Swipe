# Swipe Android Bot (Reflets de France)

Application Android Kotlin (API 26+) qui :
- capture l'écran en continu avec `MediaProjection`
- analyse uniquement la zone "tapis"
- détecte les motifs couleur codés en dur (logo crème + chrono vert)
- déclenche un swipe via `AccessibilityService.dispatchGesture()`

## Premier lancement

1. Ouvrir l'app.
2. Cliquer **Ouvrir paramètres Accessibilité** puis activer **Swipe Gesture Service**.
3. Cliquer **Autoriser la superposition** et autoriser l'affichage par-dessus les autres apps.
4. Cliquer **Démarrer** puis accepter la popup système de capture d'écran.

Le service tourne ensuite en **foreground service** avec notification persistante.

## Overlay debug

L'overlay affiche :
- état (en cours / pause)
- dernière détection (type, x, score)
- nombre total de swipes

Boutons overlay :
- **Pause/Reprendre**
- **Debug** : sauvegarde la frame courante (PNG) et logue les compteurs de scan
- **Quitter** : stoppe le service

## Architecture

- `Constants.kt` : tous les ratios, seuils, timings
- `ScreenCaptureManager` : flux `MediaProjection` + `ImageReader` en callback continu
- `BeltDetector` : scan de la zone tapis, compteurs par colonnes
- `SwipeDispatcher` : dispatch de geste via service accessibilité
- `OverlayController` : fenêtre flottante debug
- `AutomationService` : orchestration capture/analyse/action
- `MainActivity` : guidage permissions + démarrage
