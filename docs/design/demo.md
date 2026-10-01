# Démo

Une démo installée par l'app elle-même : un groupe de zones « Démo » qui raconte la vie d'une personne fictive et montre tout ce que l'app sait faire, ses données à flot au moment de l'installation. Elle remplace la zone « Panorama » créée à la main par l'IA pendant une recette (copie de la base du téléphone, `tmp/db`, 2026-09-30).

## Le récit

Camille, 34 ans, graphiste indépendante à Lyon. Prépare un semi-marathon dans 6 semaines, surveille sommeil et alimentation, cultive un balcon, apprend l'italien avant un voyage à Rome. Le prénom ne dit pas le genre, la démo non plus.

12 semaines d'historique. Les données se tiennent entre elles : une semaine de rush au travail (≈ 50 h) fait baisser le sommeil et l'humeur, sauter une sortie, manquer l'objectif de la semaine et 6 soirées d'italien ; le carnet de projet la raconte.

Chaque description de zone et d'outil dit sa place dans l'ensemble : ce qu'il lit, qui le lit, ce que ça permet (« l'objectif de la semaine compte les sorties d'ici »). Chaque description de zone dit aussi que ce qu'on y ajoute disparaît à la prochaine mise à jour.

## Les variables

Toutes nommées avec le suffixe `_demo` : le nom d'une variable est unique dans l'app, et le suffixe garantit qu'aucune de l'utilisateur ne croise celles de la démo, avec les mêmes noms sur tous les téléphones. Les noms ne se traduisent pas.

## Les zones

Toutes dans le groupe de zones « Démo » (« Demo » en anglais), leurs tuiles dans les quatre modes qu'une zone prend (`ZonePositions.MODES`) : Course et Travail côte à côte en CONDENSED, Cuisine en LINE dessous, Balcon en MINIMAL et Italien en ICON en bas.

### Course (tuile CONDENSED) — groupes Entraînement, Corps, Analyse

- Entraînement
  - Sorties : suivi numérique (km), EXTENDED. Champs : Durée (durée), Type (choix : footing, fractionné, sortie longue, côtes), Ressenti (échelle 1–5). ≈ 3 par semaine, sortie longue de 8 à 16 km, semaine creuse au rush. Raccourcis « Footing 8 km », « Fractionné ».
  - Renfo : suivi occurrence, MINIMAL, champ Exercices (texte long). 1 à 2 par semaine.
  - Étirements : suivi oui/non, MINIMAL, presque chaque jour, avec des trous.
- Corps
  - Poids : suivi numérique (kg), LINE, 2 à 3 pesées par semaine, de 71,8 à 69,9 avec du bruit. Champ Pesé à (heure).
  - Sommeil : suivi durée, CONDENSED, une nuit par jour, 5 h 40 à 8 h 30, champ Qualité (échelle).
- Hors groupe
  - Humeur : suivi échelle 1–5, ICON, une note par jour, liée au sommeil.
- Analyse
  - Semaine d'entraînement : objectif, SQUARE. Critères : 3 sorties ; `km_semaine_demo` ≥ `objectif_km_demo` ; sommeil moyen ≥ 7 h ; « Pas de douleur » coché à la main. Semaine en cours à moitié remplie ; 9 semaines atteintes sur 12, celle du rush manquée.
  - Graphiques : Km par semaine (barres, FULL) ; Poids et moyenne 7 j (ligne, EXTENDED) ; Sommeil et humeur (deux séries, LINE) ; Calendrier de l'humeur (carte de chaleur, CONDENSED).
  - Carnet d'entraînement : journal, une entrée par semaine écrite par le Bilan de la semaine, dont les exécutions passées sont les auteures.
  - Automation Bilan de la semaine (rangée dans ce groupe), voir plus bas.
- Variables : `km_semaine_demo` (formule, somme des sorties depuis lundi), `objectif_km_demo` (constante 20), `poids_moyen_7j_demo` (formule).

### Cuisine (tuile LINE) — groupes Repas, Placard

- Repas
  - Repas : suivi choix du moment (petit-déjeuner, déjeuner, dîner, en-cas, couleurs), SQUARE. Champs : Aliment (référence vers une fiche d'Aliments), Quantité (g). 3 à 4 repas par jour, 1 à 3 aliments chacun, ≈ 700 entrées. Aujourd'hui, les repas passés selon l'heure de l'installation.
  - Eau : suivi compteur (verres), MINIMAL, le compte du jour en cours.
  - Calories du jour : graphique, barres par jour et ligne d'objectif, EXTENDED.
- Placard
  - Aliments : donnée structurée, ≈ 30 fiches, dont « Riz » et « Poulet » (que le banc cite). Champs : kcal, protéines, glucides pour 100 g, Catégorie (choix).
  - Courses : liste, 14 articles dont 5 cochés gardés visibles ; « lait » parmi les non cochés.
  - Recettes : notes, 6 fiches.
- Hors groupe : automation Menu de la semaine.
- Variables : `kcal_jour_demo` (formule, Σ quantité × kcal de l'aliment référencé ÷ 100), `objectif_calorique_demo` (constante 2 300).

### Travail (tuile CONDENSED) — sans groupes

- Heures : suivi durée par chronomètre, EXTENDED. L'entrée porte le client (Studio Brume, Librairie Le Rameau, Mairie de Villeurbanne). Champs : Projet (choix), Facturable (oui/non). 2 à 4 sessions par jour ouvré, la semaine de rush à 50 h. Un chronomètre tourne, lancé 40 min avant l'installation, sur Studio Brume. Un raccourci par client.
- Tâches : liste avec échéances (`docs/design/list-due-dates.md`), 11 tâches dont 3 cochées. « Relancer la Librairie », échue ce matin, en retard → en attente ; « Rappeler Studio Brume », à l'installation + 1 min — la notification de la première minute ; d'autres avec une échéance à venir ou sans.
- Carnet de projet : journal, ≈ 20 entrées dans la voix de Camille.
- Point facturation : Messages (un outil = un flux et son planning), chaque lundi à 9 h, avec son historique d'envois.
- Heures par client : graphique, barres empilées par semaine, CONDENSED.
- Automation Point du matin.
- Variable : `heures_semaine_demo` (formule, somme depuis lundi). Ce qu'elle compte d'un chronomètre en cours : celui du code, constaté à l'implémentation et dit dans sa description.

### Balcon (tuile MINIMAL) — sans groupes

- Plantes : donnée structurée, 6 fiches. Champs : Semé le (date), Exposition (choix), Arrosage (texte).
- Récoltes : suivi numérique (g), LINE, champ Plante (référence vers Plantes). ≈ 25 récoltes à partir de la semaine 5.
- Observations : suivi texte, MINIMAL.
- Arrosage : Messages, lundi, mercredi, vendredi et dimanche à 19 h (la brique de planning n'a pas « tous les N jours »), avec son historique.
- À faire au balcon : liste avec échéances, dont « Rempoter le basilic » samedi à venir.
- Engrais tomates : Messages, le 1er du mois à 10 h, avec son historique ; sa description renvoie à « Engrais mis ».
- Engrais mis : suivi occurrence, une entrée par mois — ce qui a été fait, à côté du rappel qui le demande.
- Carnet du balcon : journal, 10 entrées.
- Récoltes par semaine : graphique, barres par semaine, FULL.

### Italien (tuile ICON) — sans groupes

- Soirée d'italien : questionnaire chaque soir à 20 h. Questions : Mots retenus (nombre), Difficulté (échelle), Phrase du jour (texte). ≈ 70 réponses, 6 jours manqués au rush ; celle du jour en attente si l'installation tombe après 20 h — l'horloge décide.
- Semaine d'italien : objectif, critères 5 soirées remplies et ≥ 40 mots retenus, en cours.
- Phrases utiles : notes, ≈ 15 blocs.
- Valise pour Rome : liste, 12 éléments dont 2 cochés.
- Mots retenus par semaine : graphique.

Les modes de tuile qui ne sont pas fixés ci-dessus se répartissent pour que les 7 apparaissent parmi les outils.

## L'IA

Les automations s'installent désactivées : active, une automation appelle l'IA aux frais de l'utilisateur. Chacune a sa session de départ, dont le message est la consigne ; aucune n'a de passé.

- Bilan de la semaine (Course, groupe Analyse) : lundi 7 h, rattrapage limité à 12 h. Lit sorties, sommeil, objectif ; écrit une entrée au Carnet d'entraînement, avec les km de la semaine passée.
- Menu de la semaine (Cuisine, hors groupe) : dimanche 18 h. Lit Repas et Aliments ; ajoute des articles à Courses.
- Point du matin (Travail) : sans planning, lancée à la main. Lit les tâches et le chronomètre ; répond par un module de communication.

Ni exécutions passées ni session de chat : les services n'écrivent une session et ses messages qu'au présent, et le format d'un message de l'IA ne se vérifie que sur un appareil. Les bilans du Carnet d'entraînement sont là sans la session qui les aurait écrits.

Fournisseur des automations : le premier fournisseur configuré ; s'il n'y en a aucun, le premier de la liste, et activer ou exécuter échoue avec le message actuel du fournisseur non configuré.

## Notifications

Elles restent actives dans les outils de la démo : la démo les montre. Une dans la première minute : l'échéance de « Rappeler Studio Brume », dans Tâches. La démo ne demande pas la permission de notifier, l'app le fait déjà.

## La mécanique

- Reconnaissance : tout ce que la démo crée a un identifiant préfixé `demo-` (zones, outils, entrées, variables, automations, sessions, messages). Réinstaller supprime d'abord tout ce qui porte le préfixe, plus ce qui vit dans une zone ou un outil `demo-` (entrées saisies par l'utilisateur, outil ajouté dans une zone de démo).
- Groupe : « Démo » s'ajoute à `main_screen.zone_groups` s'il n'y est pas, et n'en est jamais retiré.
- Déclencheur : au démarrage, si `PackageInfo.lastUpdateTime` diffère de celui gardé, réinstaller puis garder le nouveau. Une première installation reçoit la démo. Avant l'accueil, avec un écran d'attente.
- Toute la démo ou rien : un échec supprime ce qui a été construit, va aux journaux et s'affiche une fois ; l'app démarre sans démo.
- Réglages, catégorie Démo : « Installer la démo à chaque mise à jour » (oui par défaut ; il n'agit qu'à la mise à jour suivante et ne supprime jamais rien : désactivé, la démo présente reste telle quelle) ; « Réinstaller maintenant », qui la remet à flot à l'instant ; « Supprimer la démo », après confirmation, sans toucher au réglage.
- Opération `demo.install` du dispatcher, qui construit la démo par les services, avec l'origine de l'app (seule à pouvoir donner un identifiant `demo-`) ; une étape refusée arrête tout et supprime ce qui a été construit, en nommant l'étape. Sa progression s'affiche au démarrage comme depuis les réglages.

## La forme

- `assets/demo/structure.json` : zones, configs d'outils, variables, automations, groupes ; identifiants symboliques (`demo-course-sorties`) et références croisées par eux ; aucun texte affiché, des clés.
- `assets/demo/texts-en.json`, `assets/demo/texts-fr.json` : tous les textes (noms, descriptions, options, journaux, notes, sessions), mêmes clés des deux côtés ; la langue suit celle du téléphone à l'installation. Les dates des textes sont relatives (« il y a 3 jours à 21 h 10 »).
- Un générateur Kotlin par série chiffrée (poids, sommeil, humeur, repas, heures…), graine fixe : la même démo à chaque installation, seules les dates glissent.

## Ce que le banc des modèles libres lit

`docs/design/local-models.md` écrit ses scénarios contre la démo : un changement de nom ou de contenu qu'ils citent (Eau, Sorties, Sommeil, Repas et les fiches riz et poulet, Courses et son « lait » non coché, Observations, Carnet du balcon, Tâches et « Relancer la Librairie » en retard, Heures et Studio Brume, `poids_moyen_7j_demo`, `objectif_km_demo`, Carnet d'entraînement, les trois automations) se reporte dans le banc.

## Les garanties, en tests

- Chaque config et chaque entrée de la démo passe la validation du schéma de son type d'outil.
- Couverture : les 9 types d'outils, les 8 types de suivi, les 7 modes de tuile d'outil et les 4 de zone, chaque type de champ parmi les champs à soi, un groupe d'outils, un outil hors groupe, une automation rangée dans un groupe, un chronomètre en cours, un message non lu, un élément de liste en retard, une échéance dans la minute, un objectif en cours, une semaine d'objectif manquée.
- Les deux fichiers de textes ont exactement les mêmes clés, et chaque clé que la structure demande existe.
- Réinstaller ne touche à rien de ce qui n'est pas de la démo, et supprime tout ce qui l'est.
