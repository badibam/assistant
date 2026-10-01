# Démo

Une démo installée par l'app elle-même : un groupe de zones « Démo » qui raconte la vie d'une personne fictive et montre tout ce que l'app sait faire, ses données à flot au moment de l'installation. Elle remplace la zone « Panorama » créée à la main par l'IA pendant une recette (copie de la base du téléphone, `tmp/db`, 2026-09-30).

## Le récit

Camille, 34 ans, graphiste indépendante à Lyon. Prépare un semi-marathon dans 6 semaines, surveille sommeil et alimentation, cultive un balcon, apprend l'italien avant un voyage à Rome. Le prénom ne dit pas le genre, la démo non plus.

12 semaines d'historique. Les données se tiennent entre elles : une semaine de rush au travail (≈ 50 h) fait baisser le sommeil et l'humeur, sauter une sortie, manquer l'objectif de la semaine et 6 soirées d'italien ; le carnet de projet la raconte.

Chaque description de zone et d'outil dit sa place dans l'ensemble : ce qu'il lit, qui le lit, ce que ça permet (« l'objectif de la semaine compte les sorties d'ici »). Chaque description de zone dit aussi que ce qu'on y ajoute disparaît à la prochaine mise à jour.

## Les zones

Toutes dans le groupe de zones « Démo », chaque tuile de zone dans un mode différent.

### Course (tuile FULL) — groupes Entraînement, Corps, Analyse

- Entraînement
  - Sorties : suivi numérique (km), EXTENDED. Champs : Durée (durée), Type (choix : footing, fractionné, sortie longue, côtes), Ressenti (échelle 1–5). ≈ 3 par semaine, sortie longue de 8 à 16 km, semaine creuse au rush. Raccourcis « Footing 8 km », « Fractionné ».
  - Renfo : suivi occurrence, MINIMAL, champ Exercices (texte long). 1 à 2 par semaine.
  - Étirements : suivi oui/non, LINE, presque chaque jour, avec des trous.
  - Programme 10 km : suivi numérique terminé avant l'historique, `enabled: false` — un outil en sommeil qui garde ses données.
- Corps
  - Poids : suivi numérique (kg), LINE, 2 à 3 pesées par semaine, de 71,8 à 69,9 avec du bruit. Champ Pesé à (heure).
  - Sommeil : suivi durée, CONDENSED, une nuit par jour, 5 h 40 à 8 h 30, champ Qualité (échelle).
- Hors groupe
  - Humeur : suivi échelle 1–5, ICON, une note par jour, liée au sommeil.
- Analyse
  - Semaine d'entraînement : objectif, SQUARE. Critères : 3 sorties ; `km_semaine` ≥ `objectif_km` ; sommeil moyen ≥ 7 h ; « Pas de douleur » coché à la main. Semaine en cours à moitié remplie ; 9 semaines atteintes sur 12, celle du rush manquée.
  - Graphiques : Km par semaine (barres, FULL) ; Poids et moyenne 7 j (ligne, EXTENDED) ; Sommeil et humeur (deux séries, LINE) ; Calendrier de l'humeur (carte de chaleur, CONDENSED).
  - Automation Bilan de la semaine (rangée dans ce groupe), voir plus bas.
- Variables : `km_semaine` (formule, somme des sorties depuis lundi), `objectif_km` (constante 20), `poids_moyen_7j` (formule).

### Cuisine (tuile LINE) — groupes Repas, Placard

- Repas
  - Repas : suivi choix du moment (petit-déjeuner, déjeuner, dîner, en-cas, couleurs), SQUARE. Champs : Aliment (référence vers une fiche d'Aliments), Quantité (g). 3 à 4 repas par jour, 1 à 3 aliments chacun, ≈ 700 entrées. Aujourd'hui, les repas passés selon l'heure de l'installation.
  - Eau : suivi compteur (verres), MINIMAL, le compte du jour en cours.
  - Calories du jour : graphique, barres par jour et ligne d'objectif, EXTENDED.
- Placard
  - Aliments : donnée structurée, ≈ 30 fiches. Champs : kcal, protéines, glucides pour 100 g, Catégorie (choix).
  - Courses : liste, 14 articles dont 5 cochés gardés visibles.
  - Recettes : notes, 6 fiches.
- Hors groupe : automation Menu de la semaine.
- Variables : `kcal_jour` (formule, Σ quantité × kcal de l'aliment référencé ÷ 100), `objectif_calorique` (constante 2 300).

### Travail (tuile SQUARE) — sans groupes

- Heures : suivi durée par chronomètre, EXTENDED. L'entrée porte le client (Studio Brume, Librairie Le Rameau, Mairie de Villeurbanne). Champs : Projet (choix), Facturable (oui/non). 2 à 4 sessions par jour ouvré, la semaine de rush à 50 h. Un chronomètre tourne, lancé 40 min avant l'installation, sur Studio Brume. Un raccourci par client.
- Tâches : liste, 11 tâches dont 3 cochées.
- Carnet de projet : journal, ≈ 20 entrées dans la voix de Camille.
- Messages (un outil Messages = un message et son planning ; ses envois sont ses occurrences) :
  - Point facturation : chaque lundi à 9 h, avec son historique d'envois.
  - Relancer la Librairie : unique, dû ce matin, non lu → en attente.
  - Rappeler Studio Brume : unique, à l'installation + 1 min — la notification de la première minute.
- Heures par client : graphique, barres empilées par semaine, CONDENSED.
- Automation Point du matin.
- Variable : `heures_semaine` (formule, somme depuis lundi). Ce qu'elle compte d'un chronomètre en cours : celui du code, constaté à l'implémentation et dit dans sa description.

### Balcon (tuile CONDENSED) — sans groupes

- Plantes : donnée structurée, 6 fiches. Champs : Semé le (date), Exposition (choix), Arrosage (texte).
- Récoltes : suivi numérique (g), LINE, champ Plante (référence vers Plantes). ≈ 25 récoltes à partir de la semaine 5.
- Observations : suivi texte, MINIMAL.
- Arrosage : Messages, tous les 2 jours à 19 h, avec son historique.
- Rempoter le basilic : Messages, unique, samedi à venir.
- Carnet du balcon : journal, 10 entrées.
- Récoltes par plante : graphique, barres cumulées, FULL.

### Italien (tuile ICON) — sans groupes

- Soirée d'italien : questionnaire chaque soir à 20 h. Questions : Mots retenus (nombre), Difficulté (échelle), Phrase du jour (texte). ≈ 70 réponses, 6 jours manqués au rush ; celle du jour en attente si l'installation tombe après 20 h — l'horloge décide.
- Semaine d'italien : objectif, critères 5 soirées remplies et ≥ 40 mots retenus, en cours.
- Phrases utiles : notes, ≈ 15 blocs.
- Valise pour Rome : liste, 12 éléments dont 2 cochés.
- Mots retenus par semaine : graphique.

Les modes de tuile qui ne sont pas fixés ci-dessus se répartissent pour que les 7 apparaissent parmi les outils.

## L'IA

Les automations s'installent désactivées : active, une automation appelle l'IA aux frais de l'utilisateur. Leur passé est écrit.

- Bilan de la semaine (Course, groupe Analyse) : lundi 7 h, rattrapage limité à 12 h. Lit sorties, sommeil, objectif ; écrit une entrée de journal.
- Menu de la semaine (Cuisine, hors groupe) : dimanche 18 h. Lit Repas et Aliments ; ajoute des articles à Courses.
- Point du matin (Travail) : sans planning, lancée à la main. Lit les tâches et le chronomètre ; répond par un module de communication.

Chacune a 3 à 4 exécutions passées, de vraies sessions d'automation (message de départ, réponses, commandes et leurs résultats) : réussies, une rattrapée, une en échec avec son message d'erreur. Les entrées qu'elles ont écrites existent dans les outils.

Une session de chat « Préparer le semi » : des pointeurs vers Sorties et vers la zone Course, un module de communication répondu, une action de l'IA dans le fil.

Fournisseur des automations et des sessions : le premier fournisseur configuré ; s'il n'y en a aucun, l'identifiant du fournisseur par défaut, et activer ou exécuter échoue avec le message actuel du fournisseur non configuré.

## Notifications

Elles restent actives dans les outils de la démo : la démo les montre. Une dans la première minute (Rappeler Studio Brume). La démo ne demande pas la permission de notifier, l'app le fait déjà.

## La mécanique

- Reconnaissance : tout ce que la démo crée a un identifiant préfixé `demo-` (zones, outils, entrées, variables, automations, sessions, messages). Réinstaller supprime d'abord tout ce qui porte le préfixe, plus ce qui vit dans une zone ou un outil `demo-` (entrées saisies par l'utilisateur, outil ajouté dans une zone de démo).
- Groupe : « Démo » s'ajoute à `main_screen.zone_groups` s'il n'y est pas, et n'en est jamais retiré.
- Déclencheur : au démarrage, si `PackageInfo.lastUpdateTime` diffère de celui gardé, réinstaller puis garder le nouveau. Une première installation reçoit la démo. Avant l'accueil, avec un écran d'attente.
- Une transaction : un échec n'écrit rien, va aux journaux et s'affiche une fois ; l'app démarre sans démo.
- Réglages, catégorie Démo : « Installer la démo à chaque mise à jour » (oui par défaut ; non supprime la démo présente) ; bouton « Réinstaller maintenant », qui la remet à flot à l'instant.
- Opération `demo.install` du dispatcher, qui écrit par la base dans une transaction, comme l'import de sauvegarde.

## La forme

- `assets/demo/structure.json` : zones, configs d'outils, variables, automations, groupes ; identifiants symboliques (`demo-course-sorties`) et références croisées par eux ; aucun texte affiché, des clés.
- `assets/demo/texts-en.json`, `assets/demo/texts-fr.json` : tous les textes (noms, descriptions, options, journaux, notes, sessions), mêmes clés des deux côtés ; la langue suit celle du téléphone à l'installation. Les dates des textes sont relatives (« il y a 3 jours à 21 h 10 »).
- Un générateur Kotlin par série chiffrée (poids, sommeil, humeur, repas, heures…), graine fixe : la même démo à chaque installation, seules les dates glissent.

## Les garanties, en tests

- Chaque config et chaque entrée de la démo passe la validation du schéma de son type d'outil.
- Couverture : les 9 types d'outils, les 8 types de suivi, les 7 modes de tuile (outils et zones), chaque type de champ parmi les champs à soi, un groupe d'outils, un outil hors groupe, une automation rangée dans un groupe, un outil désactivé, un chronomètre en cours, un message en attente, un message dû dans la minute, un objectif en cours, une semaine d'objectif manquée.
- Les deux fichiers de textes ont exactement les mêmes clés, et chaque clé que la structure demande existe.
- Réinstaller ne touche à rien de ce qui n'est pas de la démo, et supprime tout ce qui l'est.
