# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- Les outils manquants sont posés (sélection du cœur, RÉFÉRENCE, lecture du cœur, variables, Données structurées et import, Objectif, Questionnaire) ; rien n'a tourné sur un téléphone : passer `docs/design/device-checks.md`, puis élaguer de `docs/design/missing-tools.md` ce qui est codé, ses garanties devenant des tests (le Graphique compris, codé le 2026-09-29).
- La grille a passé sa recette sur l'appareil le 2026-10-01 : élaguer `docs/design/grid-layout.md`, ses garanties devenant des tests.
- Le thème rétro, conçu dans `docs/design/retro-theme.md`, avec la grille. Dans l'ordre : dessiner dans `/mnt/data/OUTILS/cartouche-font` (la police de Saylune, sortie dans un projet à elle) les glyphes qui manquent à l'assistant, un item de son `TODO.md`, puis en copier `cartouche_regular.ttf` et `cartouche_thin.ttf`, et les deux Big si les boutons en glyphe se confirment ; les espacements, les onglets, l'interrupteur, les couleurs Material et un style de texte gras (les messages non lus de la tuile Messages, en style libellé en attendant) entrés au contrat des thèmes ; la taille de case donnée par le thème ; les sons de l'interface au contrat ; puis le thème, ses palettes réglées au banc.
- Les échéances de la Liste ont passé leur recette sur l'appareil le 2026-10-01 : élaguer `docs/design/list-due-dates.md`, ses garanties devenant des tests.
- La démo a passé sa recette sur l'appareil le 2026-10-01 : élaguer `docs/design/demo.md`, ses garanties devenant des tests.
- Modèles libres (`docs/design/local-models.md`) : après la démo, sur laquelle il joue, monter le banc (tests d'instrumentation sur l'émulateur, `./run bench`) qui fait jouer à l'app une vingtaine de scénarios, et lancer la campagne 1 (prompt actuel, sortie forcée au schéma, un modèle par classe de machine via OpenRouter). Les niveaux de prompt se décident sur son résultat.
- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.
- Les conditions typées à l'écriture, conçues dans `docs/design/typed-conditions.md` : le type de chaque côté d'une condition connu sans lire de données, les constantes de l'IA traduites à l'entrée, la vérification au service, et le refus d'un changement dont dépend une condition ailleurs.
- Le formulaire du Graphique : chaque niveau d'emboîtement retire de la largeur des deux côtés, au fond tout tient dans une colonne étroite, et le filtre d'une colonne de grille est enfoui six niveaux plus bas (couche › colonnes › colonne › lu › Filtres) sans signe en surface — à concevoir.
- Le graphique Calories de Panorama met 3,3 à 3,8 s à se lire (grille par jour sur 30 jours, en arrière-plan) — comprendre où va le temps.
- Le formulaire du Graphique montre « Empilement : Aucun » sur le canal horizontal d'une couche dont le vertical est en `normalize` : vérifier qu'il lit l'empilement du bon canal.

## En attente d'un déclencheur

- L'ouverture d'une zone bloque encore l'écran ~1 s la première fois après le démarrage (version debug, 2026-10-01 : 6 graphiques, 18 tuiles) — à décomposer si ça gêne.
- Choisir une entrée précise comme cible du pointeur dans son sélecteur (conçu dans `docs/design/pointer.md` : étiquette par type d'outil, liste, relecture de l'entrée) — si le besoin apparaît : un pointeur peut désigner une entrée (le Questionnaire en pose un), mais seule l'app le fait. La cible APP attend que l'IA sache lire les réglages de l'app.
- Les exécutions d'une automation décrites comme des entrées (champs déclarés, schéma généré, dates en ISO par ce schéma) — le jour où l'IA les lit ; ce ne sont pas des réglages.
- Une sauvegarde emporte-t-elle la clé d'API d'un fournisseur (réglage secret) ? — à trancher avant d'ouvrir l'export à un usage partagé.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- Un filtre qui compare un champ à une variable ou à une lecture (« kcal > objectif_calorique ») : `EntryFilters.parse` le refuse, `ConditionPicker` n'offre qu'une valeur écrite — quand un pointeur ou une variable en a besoin ; le terme se lit alors une fois à la référence du contexte (`TermReader`).
- Le nombre d'entrées en attente sur la tuile, au lieu du point (`WaitingMark`, décidé « pour le moment » le 2026-09-29) — si le point ne suffit pas.
- D'autres sources de l'attente (`docs/BRICKS.md`) : une automation qui attend une validation, un message de l'IA arrivé dans une session pendant qu'on était ailleurs — à concevoir.
- Joindre un fichier au message de départ d'une automation : son composeur n'a pas toujours de session, à laquelle un fichier joint appartient.
- Joindre une image à un message : un stockage de fichiers (le texte d'un fichier joint vit en base avec son message), et ce que chaque modèle d'IA accepte.
- La brique de planning ne sait pas dire « tous les N jours / N semaines à partir d'une date », ni plusieurs jours d'un mois (« le 1er et le 15 ») — au premier rythme qu'on ne peut pas saisir.
- Une chose répétée dont on veut savoir qu'elle est faite (un médicament chaque jour) demande deux gestes : lire le message, cocher le suivi — un bouton « Fait » sur le message qui écrit dans un suivi (avec les automations directes), ou un type Rappels avec un champ planning par entrée ; quand ce double geste pèse.
- Un outil Agenda, pour des événements qui ont une heure et passent d'eux-mêmes (un rendez-vous), distinct d'un élément de liste à échéance qu'on coche — à concevoir au premier événement qu'on voudra noter dans l'app.
- Outil jamais livré : l'Alerte, laissée pour plus tard (outil ou event du cœur, sans doute une lecture et une condition).
- L'automation directe : des commandes que l'app exécute elle-même, sans IA. Premier usage connu, le relevé (une écriture qui accepte une valeur lue à l'exécution : un Suivi qui garde un chiffre de Calcul, `docs/design/missing-tools.md`) ; à concevoir avec les events du cœur et l'Alerte, qu'elle croise (`NOTES.md`, « Events et badges »), et à trancher : la date de l'entrée écrite, le doublon d'une exécution relancée.
- L'IA crée et modifie des automations — aujourd'hui seul l'utilisateur le peut ; l'historique des exécutions est à trancher avec (`NOTES.md`, « Automations et planification »). À faire comme pour les variables (`docs/design/missing-tools.md`) : elles apparaissent sous leur zone dans l'instantané `APP_STATE` et dans une commande qui les liste à la demande (id, nom, description), avec des opérations d'un service dédié.
- Le Suivi (tableau de l'historique) et Messages (cartes des envois) n'affichent pas les champs personnalisés de leurs entrées, qu'on ne voit qu'en modification — à trancher : où, et dans quelle disposition (`CustomFieldsDisplay`, `docs/DATA.md`).
- Des outils sur l'accueil (« Favoris en page d'accueil », `NOTES.md`) : une tuile de zone qui montre les résumés de certains de ses outils — à concevoir : lesquels, dans quel ordre, un outil montré à deux endroits.
- Les incarnations (`docs/design/incarnations.md`, identités de personae) — au premier export publié par personae.
- Supprimer un fournisseur d'IA ne regarde ni les automations ni les sessions qui le nomment (`AIProviderConfigService.deleteProviderConfig`) : refuser tant qu'une automation l'utilise, en la nommant ; une session passée reste lisible mais ne peut plus continuer, message à l'appui — avec les identités incarnées, qui demandent le même contrôle, écrit une fois pour les deux.
- Une opération lourde en trois temps (lire, calculer longtemps sans bloquer les autres opérations, écrire), le résultat rendu à celui qui l'a lancée, un seul calcul lourd à la fois dans toute l'app et arrêté si son écran se ferme — au premier calcul qui fige l'app.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.
- Le journal de l'app ne garde que ses ~12 000 dernières lignes, 24 minutes d'usage le 2026-10-01 (VERBOSE 7 562 dont 3 782 « Service result » du coordinateur, DEBUG 2 439) : les lignes utiles, migrations comprises, en sortent avant d'être lues. Réduire ce que VERBOSE et DEBUG écrivent, ou ne garder qu'eux en rotation courte.
- La mémoire d'un import grandit avec son fichier : le texte entier, toutes ses cellules (`CsvReader.read`), le plan de toutes les lignes et les fiches du lot sont tenus ensemble ; 64 659 lignes (5 Mo) ont atteint 276 Mo de tas Java pour une limite de 256 Mo le 2026-10-01, en 3 min 19 s. À lire au fil de l'eau et écrire par tranches dans la même transaction (le contrôle des doublons sur les seules clés), puis l'écran d'une table qui charge toutes ses fiches — au premier import qui plante, ou à une limite de mémoire plus basse.
- Le réglage « réservé à l'utilisateur » (`unified-fields.md`, Ouvert) couvre deux cas à séparer : une valeur saisie (champ de l'utilisateur, champ saisi d'un critère d'Objectif), écrite par `tool_data`, prend un réglage de champ `user_only` que le service vérifie par l'origine (`currentOrigin()`) et que le schéma de l'IA marque non modifiable ; le verdict d'un Objectif, calculé par `goal.validate`, n'est pas un champ et se réserve par un réglage de l'Objectif vérifié dans `goal.validate` et `goal.reopen` — la phrase de `missing-tools.md` qui l'envoie au réglage de champ est à corriger. Laissé de côté le 2026-10-01.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
