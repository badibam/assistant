# Le rapport de bug — conception

Conçu le 2026-10-08, après un plantage sur un autre téléphone (0.5.0, Android 15) dont il n'est rien resté : l'app plantait à chaque lancement, sa désinstallation a effacé la base et le journal. Un rapport assemblé par l'app, montré mot pour mot, envoyé par le menu de partage d'Android.

## Ce que contient le rapport

Un texte, dans cet ordre :

1. **Ce qui s'est passé** : le champ libre de l'écran, tel que la personne l'a écrit, non nettoyé — elle l'écrit en sachant qu'il part. Absent s'il est vide.
2. **L'appareil** : version de l'app (`versionName`, `versionCode`), fabricant et modèle, version d'Android et niveau d'API, langue du téléphone, thème de l'app.
3. **Le dernier plantage**, s'il y en a un : sa date, la version de l'app qui tournait, sa pile d'appels nettoyée.
4. **Le journal** : les lignes WARN et ERROR des 2 dernières heures, 300 au plus, les plus récentes gardées ; pour chacune la date, le niveau, la catégorie (`tag`), le message et la pile d'appels (`throwableMessage`) nettoyés. Jamais VERBOSE, DEBUG ni INFO : ce sont celles qui recopient les résultats des services et les prompts.

Le rapport s'écrit en anglais quelle que soit la langue du téléphone, comme le journal qu'il recopie : il est lu par qui corrige l'app. Il ne dépasse pas 200 000 caractères : un texte partagé par Android au-delà d'environ 1 Mo est refusé, et une messagerie ne fait pas mieux. Les lignes les plus anciennes sortent d'abord, et le rapport dit combien.

## Le nettoyage

Mécanique, appliqué aux messages et aux piles d'appels (pas au champ libre) :

- **Un identifiant** (UUID) devient `#1`, `#2`… dans l'ordre d'apparition, le même identifiant gardant le même numéro dans tout le rapport : on suit « le même outil » d'une ligne à l'autre sans savoir lequel.
- **Un texte cité**, entre `'…'` ou `"…"`, devient `«text»` : le code cite presque toujours ainsi une valeur venue de l'utilisateur (`Field validation failed for '${field.displayName}'`).

Ce n'est pas une anonymisation garantie, et l'écran ne le prétend pas : il dit « identifiants et textes cités remplacés ; relisez avant d'envoyer ». L'écran mot pour mot est le garde-fou du reste.

## Le plantage enregistré

- Un gestionnaire d'exceptions non rattrapées (`Thread.setDefaultUncaughtExceptionHandler`), installé au tout début du processus, dans une classe `Application` à créer (l'app n'en a pas), avant toute initialisation, écrit le plantage dans un fichier de `filesDir` : date, version de l'app, pile d'appels brute. Puis il passe la main au gestionnaire d'Android, qui tue l'app comme avant.
- Un seul fichier : le plantage suivant écrase le précédent. Il est nettoyé à la lecture, pas à l'écriture : écrire au moment du plantage doit être le plus court possible.
- Le fichier porte une marque « vu », posée quand l'écran d'après plantage a été quitté, par l'un ou l'autre bouton. Le plantage reste dans les rapports suivants tant qu'un autre ne l'a pas remplacé.

## L'écran d'après plantage

Au lancement, avant l'initialisation de l'IA, du planificateur et de l'écran d'accueil, l'app regarde le fichier : un plantage pas encore vu ouvre d'abord cet écran.

- « L'app s'est arrêtée le 07/10 à 19h52. »
- **Voir le rapport** : l'écran du rapport, puis retour ici.
- **Continuer** : marque le plantage vu, puis démarre l'app normalement.

Il ne fait rien d'autre : une session de discussion coupée en plein tour est déjà remise au repos au démarrage (`AIEventProcessor.settleRoundCutByAppClosing`). Si le plantage revient, l'écran revient, et le rapport reste atteignable à chaque lancement.

## L'écran du rapport

Ouvert depuis l'écran d'après plantage, ou à la demande par la tuile « Signaler un bug » de Réglages › Système (à côté des journaux).

- En haut, le champ libre « Ce qui s'est passé », facultatif.
- Puis le rapport, en texte brut, défilant, exactement ce qui part — champ libre compris, mis à jour quand on l'écrit.
- La phrase sur le nettoyage.
- **Envoyer** : le menu de partage d'Android (`ACTION_SEND`, `text/plain`) avec le rapport en texte et un sujet « Rapport de bug Assistant <version> ». Aucun destinataire imposé : la personne choisit l'app et le destinataire.

Pas de retrait ligne par ligne : on envoie ou non, et on coupe au besoin dans l'app qui reçoit.

## Ce que garantissent les tests

- Le nettoyage : un UUID devient `#n`, le même UUID le même `#n` partout ; un texte cité entre `'` ou `"` devient `«text»` ; le reste est intact.
- Le choix des lignes : ni VERBOSE, ni DEBUG, ni INFO ; rien de plus vieux que 2 heures ; 300 au plus, les plus récentes.
- Le texte envoyé est celui que l'écran montre (une seule fonction produit les deux).

## Sur l'appareil

- Provoquer un plantage (`adb shell am crash com.assistant.debug` pour la version debug, à vérifier qu'il passe par le gestionnaire), relancer : l'écran d'après plantage, le rapport avec la pile d'appels, le partage vers une app de messagerie.
- « Continuer » : l'app démarre, l'écran ne revient pas au lancement suivant.
- La tuile « Signaler un bug » sans plantage : le rapport sans section plantage.
