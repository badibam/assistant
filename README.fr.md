[English](README.md) · **Français**

# Treelune

**Suivez ce qui compte pour vous — sport, repas, tâches, humeur — avec des outils que vous assemblez vous-même, et une IA qui peut les lire, les remplir et les régler avec vous.**

Vos repas calculent vos calories du jour, qu'un objectif compare à votre cible et qu'un graphique dessine : les outils se nourrissent entre eux.

Application Android, libre, sans compte ni publicité. Vos données restent sur votre téléphone.

<!-- Captures d'écran : l'accueil, une zone de la démo, un graphique, une conversation avec l'IA, rangées dans fastlane/metadata/android/en-US/images/phoneScreenshots/ -->

## Ce que vous pouvez faire

### Noter

Vous rangez votre vie en **zones** (Santé, Travail, Cuisine…), et dans chaque zone vous posez les **outils** dont vous avez besoin :

<!-- tools -->
- **Suivi** — Des chiffres, des notes, des choix, des chronos ou des mémos vocaux, notés au fil des jours : poids, sommeil, humeur, séances
- **Notes** — Des notes, chacune avec son titre, rangées là où elles servent
- **Journal** — Un carnet daté, écrit ou dicté
- **Messages** — Des rappels et des notifications, une fois ou selon un planning
- **Liste** — Ce qui reste à faire, coché au fur et à mesure, avec des échéances si vous voulez
- **Données structurées** — Des fiches faites de vos champs, retrouvées par leur nom : aliments, livres, plantes
- **Objectif** — Un objectif jugé chaque jour, semaine ou mois sur les critères que vous fixez, lus dans vos entrées
- **Questionnaire** — Des questions posées une par écran, quand vous voulez ou à heures prévues
- **Graphique** — Vos entrées et vos chiffres dessinés en courbes, barres, camemberts ou calendriers
- **Séance** — Des étapes préparées à l'avance, suivies à l'écran avec un minuteur et un signal à chaque changement : un entraînement, une recette
<!-- /tools -->

Chaque outil se règle : ses champs, ses unités, ses rappels, son affichage sur la zone.

### Relier

Un outil peut lire ceux des autres. Une **variable** tire un chiffre de vos entrées (les calories du jour, les kilomètres de la semaine) ; un Objectif la compare à sa cible, un Graphique la dessine, l'IA la lit. Une entrée peut **pointer** vers une autre : un repas vers la fiche de l'aliment, une récolte vers sa plante.

### Avec une IA

L'IA dispose des mêmes gestes que vous : elle lit vos outils, y ajoute des entrées, en crée de nouveaux, les règle.

- Vous lui parlez dans une conversation, en lui joignant d'un geste un outil ou une zone, sur la période qui vous intéresse.
- Vous pouvez exiger qu'elle vous montre ce qu'elle va faire et attende votre accord avant toute modification : pour toute l'app, une zone, un outil ou une conversation.
- Quand il lui manque une information, elle vous pose la question sous forme de champ à remplir.
- Une **automation** la fait travailler seule, sur un planning : « chaque dimanche soir, fais-moi le bilan de la semaine ».
- Claude peut aussi se brancher à l'app depuis l'extérieur, comme connecteur, et travailler sur vos outils depuis claude.ai.

Vous apportez votre propre clé d'API, chez l'un de ces fournisseurs :

<!-- providers -->
**Claude**, **OpenAI**, **DeepSeek**
<!-- /providers -->

ou chez tout service qui parle comme l'API d'OpenAI (Ollama, LM Studio, OpenRouter…). L'app affiche ce que chaque échange vous coûte.

### À votre goût

Vous disposez les tuiles de chaque zone comme vous voulez, vous choisissez l'icône et la couleur de chaque outil, et l'apparence de toute l'app :

<!-- themes -->
- **Par défaut** — Clair et sobre, dans la teinte de votre choix
- **Rétro** — Du pixel art : des pixels entiers, une police dessinée, des icônes en pixels, ses propres sons
- **Cosy** — Comme un téléphone dans un jeu doux : rond et dodu, tout rebondit, ses propres sons
<!-- /themes -->

## Commencer

Téléchargez l'APK de la dernière version sur la [page des releases](https://github.com/badibam/treelune/releases), ou construisez l'app depuis les sources (voir plus bas). Elle n'est pas encore sur F-Droid.

Au premier lancement, une **démo** vous attend : douze semaines de la vie de Camille, qui prépare un semi-marathon, travaille en indépendante, cuisine et jardine sur son balcon. Elle montre à quoi ressemble l'app une fois construite. Le **Guide** (le livre en haut de l'accueil) vous emmène ensuite pas à pas : la démo, votre première zone, votre premier outil, puis l'IA et les automations.

## Vos données

- Tout est enregistré sur votre téléphone. Aucun compte, aucune télémétrie, aucune publicité.
- Rien ne part vers une IA sans que vous l'ayez demandé : quand vous lui écrivez, ou quand une automation que vous avez programmée tourne. Ce qui part alors va au seul fournisseur que vous avez choisi, avec votre clé.
- Pour afficher le coût des échanges, l'app télécharge une liste publique des prix des modèles (LiteLLM, sur GitHub). Elle n'envoie rien.
- Le connecteur, s'il est activé, passe par un relais HTTPS dont vous donnez l'adresse.
- Vous sauvegardez et restaurez tout en un fichier, vous importez un tableau CSV dans un outil.

## État du projet

En développement actif : l'app sert tous les jours, mais tout peut encore changer, la forme des données comprise — gardez des sauvegardes.

<!-- status -->
- Version 0.5.0
- Android 8.0 ou plus récent
- Langues : anglais, français
<!-- /status -->

## Pour les développeurs

Kotlin, Jetpack Compose et Room. Tout ce qui agit sur les données — l'interface, l'IA, le planificateur — passe par un même point d'entrée, ce qui donne à l'IA exactement les gestes de l'utilisateur. Un type d'outil s'ajoute sans toucher au cœur de l'app.

```bash
git clone https://github.com/badibam/treelune.git
cd treelune
./run            # le menu : construire, installer, tester…
./run build      # l'APK de debug
```

La documentation technique commence à [`docs/reference.md`](docs/reference.md).

## Licence

[GPL-3.0](LICENSE)
