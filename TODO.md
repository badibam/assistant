# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre.

## Chantier en cours

- **Alignement du pipeline TOOL_DATA** — la doc IA (`ai_prompt_chunks.xml`) est une interface que rien ne teste, et chaque divergence doc↔code produit un échec silencieux côté IA. Détail des sept points dans `docs/design/post-refactor-audit.md`, partie A. Le plus grave : l'objet `period: {start, end}` est documenté mais ignoré par le transformer, ce qui peut rendre tout l'historique là où l'IA croit lire un mois.
- **Conformité F-Droid** — spec dans `docs/design/fdroid-compliance.md`. Premier bloquant : `LICENSE.txt` est en CC BY-NC-SA, non libre au sens DFSG ; la décision actée est GPL-3.0.

## Dette constatée

Les huit points de `docs/design/post-refactor-audit.md`, partie B, chacun avec son statut (vérifié ou soupçon). Les deux à trancher en priorité :

- Vérifier si l'event sourcing existe réellement : `docs/DATA.md` l'annonce obligatoire, aucun event store n'a été trouvé dans les chemins d'écriture lus. Si c'est une aspiration, corriger la doc.
- Renommer un custom field détruit les valeurs historiques (`Removed + Added` → `STRIP_FIELD`). Migration rename-aware à écrire avant que ça morde sur des données réelles.

## Bugs

- Erreur probable dans le calcul des coûts.
- Crash probablement dû aux logs : faire le ménage, réduire les doublons et les dumps volumineux.
- Notes, réordonnancement : impossible de remonter au-dessus des premières entrées par moments. Revoir le système d'ordre entièrement.
- Notes, réordonnancement : l'écran remonte en haut après l'action.
- Composer : quand le bloc de texte est vide, les pointers ne s'intègrent pas toujours. Soit désactiver les boutons pointer sans bloc actif, soit créer le bloc à l'ajout du pointer.
- Le téléchargement ne se fait pas en arrière-plan ; revoir aussi les fichiers partiels et la logique de retry.
- Schémas : demander le schéma et les données dans le même message fait considérer le schéma comme déjà disponible, alors qu'il doit l'être *avant* la requête. Vérifié sur l'ancien pipeline d'exécutions — à revérifier sur `tool_data`.
- Doc IA : l'obligation de demander le schéma avant la requête est-elle énoncée pour les requêtes de données comme elle l'était pour les exécutions ?

## Fonctionnalités

- Outil « Données structurées » — spec dans `docs/design/structured-data-tooltype.md`.
- Sources de données externes configurables (API web, capteurs, fichiers) — spec dans `docs/design/external-data-sources.md`.
- Grille de placement des outils dans les zones — spec dans `docs/design/grid-layout.md`, modes d'affichage des Messages dans `docs/design/messages-display-modes.md`.
- Filtrage des requêtes à opérateurs (`where`, `orderBy`, jointures) — spec dans `docs/design/query-parameters.md`, écrite en 2025 et jamais implémentée. Trois de ses idées ont été livrées autrement : `select` est devenu `fields`, `offset` est devenu `page`, les agrégations sont devenues l'opération `stats`. À reprendre ou à jeter, pas à appliquer telle quelle.
- Custom fields : type `duration` (unité de stockage à trancher — secondes ou h/min/s, et jusqu'où on va).
- Système d'événements dans le core, comme les automations (section par zone, intégré au `CoreScheduler`), pour lier les outils entre eux. Au passage, clarifier le vocabulaire des déclencheurs d'automation, géré de façon inconsistante.
- POINTER : sélection multiple de champs (bascule « filtrer les champs » qui déroule la liste).
- Compte des tokens avant envoi, via `/v1/messages/count_tokens` chez Claude — à voir pour les autres providers. `TokenCalculator` (171 lignes) et son bloc de strings existent déjà mais ne sont appelés de nulle part : soit ce chantier les reprend, soit ils partent.
- Dupliquer un outil.
- Outils prévus par la vision produit mais jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste. C'est la chaîne de valeur annoncée par `README.md`, et donc le différenciateur non prouvé du projet.

## Améliorations

- Prompt : commande pour récupérer le nom de toutes les icônes.
- UI : résumer le message système de confirmation de complétion, et la confirmation renvoyée par l'IA.
- UI : « + texte » insère après le bloc actif, et en dernier seulement s'il n'y a pas de bloc actif.
- UI : vraies icônes Lucide pour les boutons d'action.
- UI : labels relatifs complets dans le pointer d'automation (« le jour même », « début/fin du jour même »).
- UI : affichage différencié des messages UI / IA / SYSTEM.
- Notes : copier dans le presse-papier. Idem pour les messages, dont les détails de commande sont aujourd'hui ignorés à la copie.
- Interrompre un message IA en cours doit rendre la main immédiatement (CHAT et AUTOMATION).
- Système d'intégrité des données à l'échelle de l'app, notamment pour les migrations de config inachevées.
- Découper `shared.xml` en plusieurs fichiers.
- Commandes IA `create`/`update` d'instance d'outil : les schémas ne sont pas `systemManaged`, donc l'IA doit viser juste. Ajouter au moins une validation de cohérence entre les trois identifiants.
- Vérifier l'intégration de la validation de config aux niveaux app, zone et outil.
- Champ `raw` de Tracking : ajouté par le service, présent dans les données requêtées mais absent du schéma, alors que le schéma guide aussi les requêtes de champs. Étudier `systemManaged` et la conversion récursive écartée.
- Sélecteur de tooltype (enrichments USE et CREATE) piloté par schéma, sur le modèle du type de tracking.
- Timezone : `existingConfig?.timezone ?: TimeZone.getDefault().id` dans le schedule — passer par la config app et vérifier qu'il ne reste ni valeur en dur ni repli.
- Vérifier que la sérialisation de l'export dans `BackupService` a suivi les dernières migrations, et traiter la linéarisation export/import.
- `strings_generated.xml` est un fichier généré, versionné dans le dépôt.
- Traduction de la doc en anglais, et multilingue le cas échéant.
- Templates prédéfinis et configurables pour les outils.

## Sagesse

- Pull des canaux restants : `universel` (21 commits), `android` (8), `fdroid` (6). `dev_base` est conforme, sans dette.
- Revoir l'organisation des dossiers de `/mnt/data/OUTILS/assistant` : le dépôt est le sous-dossier `App/`, ce qui oblige le registry à pointer un sous-chemin. Décider si le dépôt remonte d'un cran.
