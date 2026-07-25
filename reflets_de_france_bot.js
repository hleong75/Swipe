"ui";

//=======================================================================
// BOT "Reflets de France" — jeu de swipe (Carrefour, 30 ans)
//
// Comportement voulu : swipe vers le bas UNIQUEMENT quand la couleur
// du logo "Reflets de France" est détectée sur le tapis (la bande où
// défilent les produits + le crono). Aucune autre action n'est faite :
// pas de swipe sur les autres marques, pas de swipe "au hasard".
//
// Prérequis :
//  - Auto.js / AutoJs6 installé (root non requis)
//  - Service d'accessibilité activé pour l'app (une fois, dans les
//    paramètres Android -> Accessibilité)
//  - Autoriser la capture d'écran quand le script le demande
//
// IMPORTANT : les couleurs et la zone du tapis ci-dessous ont été
// calibrées à partir d'UNE capture d'écran (résolution 1080x2400).
// Si ton téléphone a une résolution différente, ou si le rendu diffère
// un peu (luminosité, thème), il faudra réajuster les valeurs de la
// section RÉGLAGES ci-dessous. Voir le mode débogage tout en bas.
//=======================================================================

auto.waitFor(); // attend que le service d'accessibilité soit prêt

if (!requestScreenCapture()) {
    toast("Capture d'écran refusée : le script s'arrête.");
    exit();
}

//----------------------- RÉGLAGES À CALIBRER --------------------------

// Zone "tapis" = bande horizontale où défilent les produits ET le crono.
// Valeurs de départ pour un écran 1080x2400 (adapter à ton téléphone).
var BELT_X = 0;
var BELT_Y = 1140;
var BELT_W = 1080;
var BELT_H = 360;

// Swipe vers le bas déclenché quand le logo est trouvé
var SWIPE_DISTANCE = 500;   // pixels vers le bas
var SWIPE_DURATION  = 220;  // ms

// Pas d'échantillonnage (plus petit = plus précis mais plus lent)
var SAMPLE_STEP = 10;

// Couleur crème du logo "Reflets de France", mesurée sur le tapis
var CREAM_MIN_R = 190;
var CREAM_MIN_G = 165;
var CREAM_MIN_B = 95;
var CREAM_MAX_B = 205;

// Écart Rouge - Bleu : distingue le crème du logo (~68-74) des autres
// emballages beiges/crème (ex : paquet "Sensation", ~35-50)
var MIN_RB_GAP = 55;

// Nombre minimum de pixels "crème logo" dans une colonne pour valider
// une vraie détection (évite qu'un pixel isolé déclenche un swipe)
var MIN_MATCH_COUNT = 25;

// Largeur approximative d'un produit sur le tapis, pour regrouper les
// pixels détectés en "colonnes" (un produit = une colonne)
var COLUMN_WIDTH = 60;

// Anti-rebond : pause après un swipe pour ne pas re-déclencher sur le
// même produit encore visible à l'écran
var COOLDOWN_MS = 900;

//------------------------------------------------------------------------

log("Bot démarré. Reste sur l'écran de jeu. Ctrl+C / retour pour arrêter.");

var lastSwipe = 0;

while (true) {
    var img = captureScreen();
    var hit = scanBelt(img);
    img.recycle();

    if (hit !== null && (Date.now() - lastSwipe) > COOLDOWN_MS) {
        swipe(hit.x, hit.y, hit.x, hit.y + SWIPE_DISTANCE, SWIPE_DURATION);
        lastSwipe = Date.now();
        log("Logo Reflets de France détecté (x=" + hit.x + ") -> swipe vers le bas");
    }
    sleep(120);
}

// Scanne la bande du tapis et renvoie le point de swipe si le motif
// crème caractéristique du logo est trouvé, sinon null (aucune action).
function scanBelt(img) {
    var clip = images.clip(img, BELT_X, BELT_Y, BELT_W, BELT_H);
    var counts = {};

    for (var y = 0; y < BELT_H; y += SAMPLE_STEP) {
        for (var x = 0; x < BELT_W; x += SAMPLE_STEP) {
            var c = images.pixel(clip, x, y);
            var r = colors.red(c);
            var g = colors.green(c);
            var b = colors.blue(c);

            var isCream = (r >= CREAM_MIN_R && g >= CREAM_MIN_G &&
                           b >= CREAM_MIN_B && b <= CREAM_MAX_B &&
                           (r - b) >= MIN_RB_GAP);

            if (isCream) {
                var bucket = Math.floor(x / COLUMN_WIDTH);
                counts[bucket] = (counts[bucket] || 0) + 1;
            }
        }
    }
    clip.recycle();

    var bestBucket = null;
    var bestCount = 0;
    for (var k in counts) {
        if (counts[k] > bestCount) {
            bestCount = counts[k];
            bestBucket = k;
        }
    }

    if (bestBucket !== null && bestCount >= MIN_MATCH_COUNT) {
        var xCenter = BELT_X + parseInt(bestBucket) * COLUMN_WIDTH + COLUMN_WIDTH / 2;
        return { x: xCenter, y: BELT_Y + BELT_H / 2 };
    }
    return null;
}

//=======================================================================
// MODE DÉBOGAGE / CALIBRAGE
// Si les swipes ne se déclenchent jamais (ou trop souvent), commente le
// bloc "while(true)" ci-dessus et décommente ce qui suit pour prendre
// une capture, la sauvegarder, puis lire la couleur à un point précis :
//
// var img = captureScreen();
// images.save(img, "/sdcard/debug_capture.png");
// var c = images.pixel(img, 245, 1345); // remplace par tes coordonnées
// toast("R=" + colors.red(c) + " G=" + colors.green(c) + " B=" + colors.blue(c));
// img.recycle();
//
// Transfère /sdcard/debug_capture.png sur PC (ou renvoie-la moi) pour
// ajuster précisément BELT_Y/BELT_H et les seuils CREAM_*.
//=======================================================================
