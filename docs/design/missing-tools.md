# Outils manquants

Conception commencée le 2026-09-27, après la refonte des champs (`docs/DATA.md`, « Champs et entrées ») : un type d'outil ne porte plus que ses façons rapides de créer une entrée et ses calculs sur plusieurs entrées.

## Tri (provisoire)

- **Liste** : livrée (`docs/TOOLS.md`).
- **Objectif** : un outil ; ce qu'est une entrée reste à dire (critère, point d'étape, évaluation).
- **Calcul** et **Graphique** : deux outils, pas un. Calcul est une source (ses résultats sont lus par les autres outils), Graphique une vue (sa sortie s'affiche). Ce qu'ils partagent vit au cœur : la désignation des entrées (le pointeur) et les agrégations.
- **Alerte** : probablement pas un outil, mais un cas des événements du cœur (`NOTES.md`, « Events et badges ») — à confirmer.
- **Données structurées**, **questionnaire** : candidats au même rang.

## Calcul

- **Outil actif** (`docs/TOOLS.md`) : il s'exécute selon sa planification (celle des Messages et des automations, rattrapage compris) et écrit ses résultats comme des entrées ordinaires, datées, lisibles par `tool_data.get`, le pointeur, les graphiques, les alertes et l'IA.
- **Trois périodes indépendantes** : la fréquence d'exécution (la planification), la portée (ce qu'une exécution lit : les 30 derniers jours, la semaine écoulée, tout l'historique) et le découpage (un résultat pour toute la portée, ou un par jour, semaine, mois).
- **Un résultat porte la période qu'il couvre** quand sa formule lit une période : sa période de découpage, ou sa portée sans découpage. Un calcul qui lit un état n'a qu'une date.
- **Même période de découpage = même résultat** : une nouvelle exécution le remplace. Rattraper une saisie tardive, c'est une portée plus large que le découpage ; recalculer un historique, une exécution à la main sur une portée élargie. Pas de recalcul automatique quand une source change : il pourra s'ajouter sans migrer les résultats.
- **Un recalcul écrase le résultat**, sans garder l'ancienne valeur : c'est à qui agit sur une valeur de la noter (une alerte garde la valeur qui l'a déclenchée, une note de l'IA cite le chiffre lu).
- **Recalcul automatique** : un réglage, « recalculer pendant [jamais / une durée / toujours] après la fin de la période », « jamais » par défaut. Quand une entrée d'une source est écrite (`DataChangeNotifier`), chaque résultat dont la période couverte contient sa date (l'ancienne et la nouvelle si elle change) est recalculé sur sa propre période, s'il n'est pas figé. Il met à jour des résultats existants, n'en crée pas. Un Calcul qui lit un Calcul suit de lui-même. Un recalcul à la main passe outre le figement.
- **Planification** : celle des Messages et des automations (`ScheduleSettings`, `ScheduleCalculator`, `ScheduleConfigEditor`, `CoreScheduler`), et le rattrapage des automations : une fenêtre de retard admis, et « la plus récente seulement ».
- **Ce qu'il calcule** : des entrées nommées et une formule arithmétique sur ces noms (`mange - depense`). Une entrée nommée agrège (somme, moyenne, minimum, maximum, nombre) une expression calculée pour chaque entrée d'un outil désigné ; une constante est une entrée nommée. L'expression peut suivre une référence (`data.value × ref(extra.aliment).kcal_100g ÷ 100`).
- **Plusieurs sorties nommées** : chacune sa formule sur les mêmes entrées nommées, et son unité. Une exécution écrit une entrée de résultat qui porte toutes les sorties, chacune un champ de `data` (`data.kcal`, `data.proteines`), lu et filtré comme un champ de suivi.
- **La formule s'écrit en texte** (`(mange - depense) / 7`), avec des boutons qui insèrent les noms et une vérification à chaque frappe qui nomme l'erreur. `+ - × ÷`, parenthèses, nombres ; une fonction ne s'ajoute que pour un cas réel. L'app lit la formule, ne l'exécute jamais comme du code.
- **Données manquantes** : une somme ou un nombre sur aucune entrée vaut 0 ; une moyenne, un minimum, un maximum n'a pas de valeur. Un terme sans valeur ou une division par zéro laisse la sortie sans valeur, jamais 0. Une entrée dont la référence ne mène nulle part est écartée, et le résultat compte les entrées écartées. Le résultat s'écrit toujours, avec la raison d'une sortie sans valeur : un calcul qui n'avait rien se distingue d'un calcul qui n'a pas tourné.

## Prérequis de Calcul

- **REFERENCE** (`unified-fields.md`) : sans lui, une entrée ne dit pas à quelle fiche elle correspond, et le calcul entrée par entrée (la nutrition) est impossible.
- **Une sélection d'entrées au cœur** : un outil, des filtres (période comprise), des champs, avec sa forme enregistrée, sa partie d'écran et sa lecture. Le pointeur d'un message devient cette sélection plus ce qui ne regarde que l'IA (joindre ou mentionner, viser l'app ou une zone) ; Calcul utilise la sélection seule. Aujourd'hui `PointerConfig`, `PointerSelector` et `EnrichmentProcessor` mêlent les deux et vivent dans le code de l'IA.
- **Une règle de rattrapage au cœur** : « fenêtre de retard admis + la plus récente seulement », sortie de `core/ai/scheduling/CatchUpPolicy` vers le planificateur du cœur, utilisée par les automations, Calcul et Messages (sa fenêtre, sans « la plus récente seulement »). Le réglage prend un seul nom, `catch_up_window` : `validity_window` des Messages migre (configs, sauvegardes à l'import, prompt L1 et son rejeu).

## Graphique

- **Il dessine, ne calcule pas** : aucun regroupement ni agrégation. Un total par jour est un Calcul découpé par jour, que le Graphique dessine ; un nombre n'a ainsi qu'une origine, lisible aussi par les alertes et l'IA.
- **Grammaire** : un sous-ensemble de Vega-Lite, que l'IA connaît déjà, dessiné nativement en Compose (pas de vue web : le thème garde l'apparence). Deux écarts : les données viennent d'une sélection d'entrées de l'app, jamais recopiées dans la config ; une couleur est un nom de la palette (`TagColor`). La config reste déclarée en champs, pour que son formulaire soit généré comme les autres.
- **Ouvert** : le sous-ensemble retenu (marques, couches, échelles, période affichée).
