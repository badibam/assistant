# Outils manquants

Conception commencée le 2026-09-27, après la refonte des champs (`docs/DATA.md`, « Champs et entrées ») : un type d'outil ne porte plus que ses façons rapides de créer une entrée et ses calculs sur plusieurs entrées.

## Tri (provisoire)

- **Liste** : livrée (`docs/TOOLS.md`).
- **Objectif** : un outil (section plus bas).
- **Calcul** n'est pas un outil mais les variables du cœur (plus bas). **Graphique** est un outil, une vue (sa sortie s'affiche), qui lit la sélection d'entrées et les variables.
- **Alerte** : probablement pas un outil, mais un cas des événements du cœur (`NOTES.md`, « Events et badges ») — à confirmer.
- **Données structurées** et **questionnaire** : deux outils distincts, même si leurs entrées ne portent l'une et l'autre que des champs déclarés par l'utilisateur.

## La lecture du cœur

Conçue le 2026-09-28. Lire une valeur dans **une seule** instance, sur la période que donne l'outil qui lit ; utilisable par tout outil, d'abord par le critère mesuré d'Objectif.

```
[ instance › champ ]  où [ filtres ]  [ réduction ]  [ test ]
[ Poids › poids ]                     [ dernière ]   [ ≤ 80 ]
[ Sport ]  où [ durée ≥ 30 min ]      [ compte ]     [ ≥ 3 ]
```

- **Instance › champ** : pas de champ pour `compte`, qui compte des entrées.
- **Filtres**, facultatifs : ceux du pointeur, avec leur composant, qui passent au cœur avec la sélection d'entrées (prérequis des variables).
- **Période** : réglée avec le sélecteur de période, relative à la référence du contexte (Le temps relatif, plus bas) ; l'Objectif la préremplit à la période de sa tentative (« Il y a 6 jours, début » → « Référence » pour sept jours). Ce sont les entrées dont le `timestamp` y tombe. `dernière` est la dernière dans la période ; la dernière connue est une période sans début (« Sans limite » → « Référence »).
- **Réductions** : dernière · somme · moyenne · min · max · compte.

  | Type | Réductions | Résultat |
  |---|---|---|
  | NUMERIC, DURÉE | dernière, somme, moyenne, min, max | son type |
  | SCALE | dernière, moyenne, min, max | son type |
  | BOOLEAN, CHOICE, TEXT | dernière | son type |
  | DATE, DATETIME, TIME | dernière, la plus tôt, la plus tard | son type |
  | RANGE | aucune | – |
  | sans champ | compte | nombre |

  « Son type » s'entend avec ses réglages : la moyenne d'une SCALE 1–10 est une SCALE 1–10, dont le pas ne vaut que pour la saisie (6,5 s'affiche tel quel). Pas de moyenne d'une heure : 23:30 et 00:30 donneraient 12:00.
- **Test** : une condition (plus bas) posée sur le résultat.
- **La condition, une notion du cœur** : un opérateur et une valeur, permis selon le type de champ (`EntryFilters.operatorsFor`), qui s'évalue en SQL sur des entrées ou sur une valeur déjà lue. Un filtre est un chemin de champ et une condition ; un critère mesuré, une lecture et une condition ; plus tard une Alerte, un event. Un seul composant la saisit, `ConditionInput`, sorti de `PointerFiltersDialog` où sa saisie est privée aujourd'hui.
- **Une valeur ou un échec.** Sans entrée dans la période, `somme` et `compte` valent 0 ; les autres réductions échouent (« poids : aucune entrée dans la période ») : ne pas s'être pesé n'est pas peser plus de 80 kg. Une entrée sans réponse au champ réduit, ou à un champ que lit une formule par entrée, fait échouer de même ; l'écarter se dit dans la lecture, par le filtre « avec réponse » (« moyenne de l'humeur, où humeur avec réponse »). Un échec porte ses causes en données (terme, raison, champ, entrées) et se propage : une formule qui lit un terme en échec échoue avec ses causes. Le cœur l'affiche partout de même (`FieldValue` : la cause, qui mène aux entrées à corriger) et le rend tel quel à l'IA ; chaque lecteur ne décide que de ce qu'il en fait : un critère d'Objectif en échec n'est ni rempli ni non rempli, et la tentative reste sans verdict (à valider, puis expirée) ; un Graphique dessine un trou marqué ; un relevé d'automation directe n'écrit rien, et son exécution échoue avec la cause dans son historique ; l'IA, en chat ou en automation IA, reçoit l'échec comme une réponse et en décide.
- **Trois couches au cœur** : RÉFÉRENCE (une chose) → sélection (ses entrées : période, filtres, champs) → lecture (une valeur ou un échec). Chacune sert telle quelle : une RÉFÉRENCE au champ `aliment` d'un repas, une sélection au Graphique et au pointeur, une lecture au terme de variable et à l'IA. Tester une lecture n'est pas une couche : c'est une condition de filtre (`EntryFilters.operatorsFor`) posée sur son résultat, ce que fait le critère mesuré d'Objectif. La cible d'une condition est une constante ou une lecture, saisie avec le même composant (« kcal ≤ `objectif_calorique` », « score ≥ `score_mois_precedent` ») ; si la cible est en échec, la condition l'est aussi. Pour l'instant dans un critère seulement : un filtre garde des constantes jusqu'à un cas réel.
- **Une porte, `readings.read`** (service du cœur `readings`) : une variable, elle l'évalue ; un champ, elle le réduit sur les entrées. Un lecteur ne connaît qu'elle, et une variable la prend elle-même pour ses termes ; l'IA lit une variable par une commande `READING`, dates en ISO comme pour `TOOL_DATA` ; une lecture de champ, elle la fait elle-même sur les entrées que `TOOL_DATA` lui donne.
- **Deux formes d'appel** : une lecture de champ reçoit une `selection` (source, période, filtres : la période y est, remplie par le lecteur), le `field` et la `reduction` ; une variable, son nom et `at`, la liste des instants où la lire : un seul pour une lecture (l'Instant qu'elle porte, résolu), plusieurs pour le Graphique et l'IA ; elle rend une liste de même longueur, une valeur ou un échec par instant. Comme une requête groupée, elle épargne à qui lit une plage (le Graphique, l'IA) une commande par point.
- **Sans historique** : ce qu'une variable consulte sans historique (une fiche, une constante) se lit tel qu'il est aujourd'hui : relu plus tard, le bilan du 12 prend les kcal corrigées depuis ; garder le chiffre d'alors est un relevé (variables).
- **La frontière avec les variables** : une source, c'est la lecture du cœur ; plusieurs sources combinées, ou une valeur nommée que d'autres relisent, c'est une variable. Un terme de variable est une lecture du cœur sans test, avec sa période, et du type que lui donne sa réduction.

## Le temps relatif

- **Une référence, fournie par le contexte** : un choix relatif (« la veille ») se résout par rapport à elle, comme `resolveRelativePeriod` le fait déjà avec l'heure prévue d'une automation. Automation : l'heure prévue de l'exécution ; Objectif : la fin de la tentative ; terme de variable : l'instant lu.
- **Une seule notion de « maintenant » par contexte.** Sans référence (le chat, la saisie d'une entrée), l'horloge sert de référence au moment du choix, et ce qui s'enregistre est une date fixe : « hier » n'est que l'étiquette d'une période fixe par rapport à l'horloge. Avec référence, un choix relatif s'enregistre comme une description, résolue à chaque fois, et l'horloge n'est pas proposée.
- **Les étiquettes ne composent jamais la référence** : le sélecteur l'affiche une fois, « Par rapport à : fin de la tentative », une chaîne que le contexte fournit et qui se lit seule ; les étiquettes relatives restent les mêmes partout (« Le jour-même », « La veille », « Il y a 2 jours »), traduites une fois. Un résumé hors du sélecteur met la référence à part (« poids, dernière · la veille · réf. : fin de la tentative »). Aucune grammaire à assembler.
- **Un sélecteur d'instant, un seul**, pour toute date de l'app : la saisie DATE ou DATETIME d'une entrée, la cible d'une condition sur une date, chaque borne d'une période, l'instant qu'écrira un relevé. Il propose :
  - une **date relative** : une unité (heure, jour, semaine, mois, année) et un décalage (« La veille », « Il y a 6 jours »), et son **moment**, début ou fin de cette période, que le contexte préremplit et qu'on change à volonté ;
  - une **date personnalisée**, toujours absolue, avec l'heure si le champ est un DATETIME : un instant précis, sans moment, qui ignore la référence ;
  - **maintenant** ; dans un contexte avec référence, c'est elle, étiquetée « Le moment même » ;
  - **sans limite**, pour une borne.

  Sans référence, un choix relatif se résout tout de suite par rapport à l'horloge et c'est la date obtenue qui s'enregistre (une entrée notée « La veille, début ») ; avec référence, il s'enregistre comme description. Le moment est dit explicitement : il remplace la convention où l'opérateur d'une condition choisissait le début ou la fin (`FilterValues`).
- **Une période est deux instants**, préremplis au début et à la fin.
- **« = » sur un DATETIME n'est pas proposé** : « échéance = la veille, début » ne garderait que minuit pile ; « entre » dit ce qu'on veut (« entre la veille, début, et la veille, fin »). Sur une DATE, « = » compare deux jours et reste.

## Les sélecteurs, recomposés

Les briques de base :

| Brique | Ce qu'elle choisit | Selon le contexte |
|---|---|---|
| **Instant** | une date relative (unité, décalage, début ou fin), une date personnalisée, maintenant (la référence s'il y en a une), sans limite | la référence et son nom, la précision (DATE, DATETIME) |
| **Chose** | par le fil d'Ariane App › Zone › (Outil ou Variable) › Entrée | les sortes permises (zone, outil, variable, entrée) |
| **Champ** | un champ des entrées d'un outil, ou aucun pour compter | les types permis |
| **Réduction** | dernière, somme, moyenne, min, max, compte… | les réductions permises par le type du champ |
| **Condition** | un opérateur et une cible | les opérateurs permis par le type du champ |

Les briques composées :

```
Période              = Instant (début) + Instant (fin)
Filtre               = Champ + Condition
Sélection d'entrées  = Chose (un outil) + Période + Filtres + choix des champs
Lecture              = Sélection d'entrées + Champ + Réduction
                     | Chose (une variable) + Instant (par défaut : Le moment même ;
                         masqué pour une constante)
Condition            = opérateur + cible, la cible étant :
                         une constante, saisie par la saisie du type du champ
                           (un Instant pour une DATE ou un DATETIME)
                       | une Lecture
```

Ceux qui les utilisent :

| Qui | Assemblage |
|---|---|
| Champ RÉFÉRENCE d'une entrée (`aliment`) | Chose (une entrée, restreinte à des outils) |
| Champ DATE ou DATETIME d'une entrée | Instant, sans référence |
| Pointeur d'un message à l'IA | Chose (app, zone, outil, entrée) + Période + Filtres + champs + joindre ou mentionner |
| Critère d'Objectif | valeur (une Lecture, ou un champ saisi dans la tentative) + Condition |
| Terme de variable | Lecture, constante ou autre variable, sa plage étant une Période relative à la référence |
| Graphique | Sélection d'entrées ou variables (le détail est ouvert) |
| Relevé (automation directe) | Chose (une variable) + un champ cible dans un Suivi + Instant de l'entrée écrite |

La référence traverse le tout : chaque Instant relatif se résout par rapport à celle que le contexte fournit.

## Les formes enregistrées

Décidé le 2026-09-29.

- **Un Instant** : une date fixe garde sa forme stockée (millisecondes pour un DATETIME, `"2026-09-15"` pour une DATE) ; un relatif est un objet, `{"relative": {"unit": "DAY", "offset": -1, "edge": "START"}}`, ou `{"relative": "NOW"}` pour la référence ; sans limite, la borne est absente. La présence de `relative` dit seule qu'une valeur se résout, jamais le type du champ ni l'allure d'une chaîne ; l'opérateur d'une condition ne choisit plus de bord.
- **Une période** : `{"start": Instant, "end": Instant}`, chaque borne facultative, hors des filtres (`"period"` à côté de `"filters"`).
- **Une sélection d'entrées** : `{"target": RÉFÉRENCE, "period", "filters", "fields"}`, lue par un seul parseur du cœur partout où elle sert. `period`, `filters` et `fields` ne valent que pour une instance d'outil, sauf la période d'une zone, appliquée à chacun de ses outils ; le service refuse le reste en disant pourquoi.
- **Un pointeur** : `{"selection": …, "attach": {"config", "entries"}}`, la sélection sous sa propre clé, que le cœur lit sans connaître le pointeur.
- **L'IA écrit la même forme**, une date fixe en ISO comme partout ailleurs : elle recopie ce qu'elle lit (termes d'une variable, pointeurs, critères), et `CommandTransformer` ne fait que convertir l'ISO. La notation `"-7_DAY"` et la règle du bord par l'opérateur (`FilterValues`) disparaissent.

## L'import

Conçu le 2026-09-28, pour les Données structurées d'abord ; au cœur, parce que le futur outil API écrira par le même chemin, dans tout outil.

- **Un format commun : des lignes de valeurs texte nommées par leur colonne.** Seule la lecture de la source lui est propre : un CSV le donne presque tel quel (en-tête et lignes), une API l'obtiendra par sa config côté app. Tout le reste est commun.
- **Le service n'applique qu'une déclaration complète.** Pour chaque colonne : sa cible (la clé qui reconnaît une entrée existante, le nom pour les Données structurées ; un champ existant ; un nouveau champ, avec son type et sa config ; ou ignorée) et son écriture. Il refuse une déclaration incomplète en nommant ce qui manque. Une entrée reconnue par sa clé est mise à jour, les autres sont créées ; un doublon de clé dans le fichier est refusé.
- **Une cellule vide est une absence de réponse**, jamais 0 ni faux. Une cellule qui ne se lit pas refuse sa ligne, avec la raison ; les autres lignes passent. Le compte-rendu dit : créées, mises à jour, refusées ligne par ligne.
- **Les nouveaux champs se créent dans l'ordre des colonnes**, dans la même transaction que les entrées : un échec n'écrit rien.
- **Chaque type de champ porte la liste fermée et nommée de ses écritures** (NUMERIC : décimale virgule, décimale point ; DATE : jour/mois/année, mois/jour/année, ISO ; DURÉE : h:min, min:s, h:min:s, `1h25`, `85 min`, ISO ; BOOLEAN : oui/non, true/false, 1/0, x/vide ; CHOICE : une valeur, ou plusieurs séparées par `;` ou `,`…). Il sait **lire** une cellule dans une écriture donnée (la valeur, ou une erreur qui dit pourquoi), et **reconnaître** une colonne : les écritures qui en lisent toutes les cellules. Une écriture absente de la liste n'est acceptée nulle part ; elle s'ajoute au type le jour où un vrai fichier la porte.
- **La détection est une opération à part, qui propose.** Pour une colonne qui correspond à un champ existant, elle cherche l'écriture ; pour une nouvelle, aussi le type, en interrogeant les types du plus exigeant au plus permissif (TEXT en dernier, qui lit tout) ; CHOICE se propose quand les valeurs différentes sont peu nombreuses, ses options tirées du fichier. Elle lit tout le fichier : une seule ligne `28/09` tranche jour/mois. Si plusieurs écritures lisent tout avec des résultats différents, elle ne choisit pas. Un en-tête `kcal [NUMERIC]` fixe le type (les noms des types de l'app), l'écriture restant détectée.
- **Trois chemins, une porte.** L'écran appelle la détection et montre la déclaration à confirmer : une ligne par colonne (cible, type, écriture, un exemple choisi pour montrer la lecture : « 03/04/2026 → 3 avril 2026 »), l'ordre des nouveaux champs, les ambiguïtés à trancher, les lignes qui seraient refusées ; tout se corrige avant d'importer. L'IA écrit la déclaration elle-même, écritures comprises, et peut partir de la détection. L'API aura la sienne dans sa config.
- **Une nouvelle table** est une table vide où toutes les colonnes sont nouvelles ; « créer depuis un fichier » enchaîne la création de l'outil et l'import.

## Les variables

Décidé le 2026-09-29 : Calcul n'est pas un type d'outil (il n'a pas d'entrées, et son seul rôle est de donner des valeurs aux autres) mais une fonctionnalité du cœur. Une **variable** est une valeur nommée que tout lecteur lit, conditions, Objectif, Graphique, IA, plus tard Alerte et events, sans type d'outil entre eux : une **constante** (`objectif_calorique = 2100`) ou une **formule** sur des lectures du cœur. Rien n'est enregistré : elle se calcule à chaque lecture. Une variable vit dans une zone, comme un outil, et le sélecteur la trouve sous sa zone ; elle lit n'importe quelle zone, et se range là où elle sert le plus (`bilan`, qui lit Repas et Sport, dans Alimentation). Une variable porte une seule formule, avec ses termes : ce qui se partage se définit une fois comme variable et se lit par les autres (`mange`, puis `bilan = mange - depense`) ; ce qui se répète seulement (la période des termes de `kcal`, `prot`, `gluc`, `lip`) se recopie en dupliquant une variable. Pas de groupe de variables.

- **Dans la zone, avec les outils et les automations** : dans chaque groupe, les variables tiennent dans un seul composant compact, une par ligne, qui montre son nom et sa valeur actuelle (« kcal · 1 204 kcal », l'horloge servant de référence), ou son échec avec la cause. Elle relit sa valeur quand un outil qu'elle lit change (`DataChangeNotifier`). Toucher une ligne ouvre l'écran de la variable, où l'on voit et modifie formule, termes et leurs périodes, type et réglages. Le bouton d'ajout de la zone propose outil, automation ou variable, constante ou formule.
- **L'IA** : elle trouve les variables dans l'instantané de l'app envoyé d'office au premier message (`APP_STATE`, niveau 3, qui enchaîne `zones.list`, `tools.list_all` et `variables.list_all`), sous leur zone à côté des outils, et à la demande par une commande `VARIABLES` (`variables.list` pour une zone), sur le modèle de `TOOL_INSTANCES`, qui montre aussi une variable créée pendant la session : nom, type, unité, formule en texte avec la description de ses termes, jamais la valeur, que l'instantané figerait ; elle la lit par `READING`. Elle crée et modifie une variable par les opérations d'un service du cœur dédié, `variables`, formule écrite en texte, avec la même vérification qu'à l'écran et sous la validation de ses actions ; une variable ne déclenche ni n'écrit rien, contrairement à une automation.
- **Chaque terme porte sa période**, la brique Période, relative à l'instant où on lit la variable ; il n'y a pas de réglage de découpage :

  | Besoin | Période du terme |
  |---|---|
  | le jour en cours | Le jour-même · début → Le moment même |
  | le bilan de la veille | La veille · début → La veille · fin |
  | les 7 derniers jours | Il y a 6 jours · début → Le moment même |
  | un état (la taille) | Sans limite → Le moment même |

  Exemple, `kcal` : un terme `mange` = Repas, Le jour-même · début → Le moment même, somme de `quantité × aliment.kcal_100g / 100` ; formule `mange`, unité « kcal ». L'âge demande en plus une fonction de date.
- **La même formule à deux échelles, deux variables** (« bilan du jour », « bilan du mois »).
- **Ce qu'une formule calcule** : des termes nommés, chacun une lecture du cœur sans test avec sa plage, une constante ou une autre variable, et la formule sur ces noms (`mange - depense`). Un terme peut réduire, au lieu d'un champ, une formule évaluée dans chaque entrée, qui lit les champs de l'entrée et ceux de la fiche que désigne une RÉFÉRENCE, un seul saut (`mange` = somme, sur Repas, de `quantité × aliment.kcal_100g / 100`).
- **La formule s'écrit en texte** (`(mange - depense) / 7`), avec des boutons qui insèrent les noms et une vérification à chaque frappe qui ne nomme que ce qui empêche de calculer (un nom inconnu, une parenthèse qui manque). `+ - × ÷`, parenthèses, nombres ; une fonction ne s'ajoute que pour un cas réel. Premier cas : `heures()`, `minutes()`, `secondes()` changent une durée en nombre (vitesse = `km / heures(temps)`, quand l'allure `temps / km` reste une durée) ; écrit sans elles, `km / temps` divise par des millisecondes. Qui écrit la formule répond de son sens. L'app lit la formule, ne l'exécute jamais comme du code.
- **Une formule est un champ calculé** : un nom, un type, les réglages de ce type ; elle s'affiche, se teste et se décrit à l'IA comme tout champ. Le type et les réglages se déduisent de ce qu'elle lit, jamais pour refuser : une réduction garde ceux de sa source, une opération ceux que ses deux côtés partagent (km + km, durée × 2), sinon un nombre nu (`km / heures(temps)`), `compte` un nombre. L'utilisateur complète ce que la déduction ne donne pas (l'unité « km/h ») et corrige un réglage déduit ; la déduction se refait quand la formule change. Il change aussi le type, vers un type de même forme : SCALE → nombre (sans les bornes), nombre → SCALE (en donnant bornes et libellés), nombre → DURÉE (en disant l'unité du nombre).
- **Données manquantes** : un terme en échec, une division par zéro, une entrée dont la référence ne mène nulle part font échouer la valeur (lecture du cœur), jamais 0 ni un total partiel, qu'un Objectif compterait (« mange : pas de valeur — 1 entrée de « Repas » sans aliment trouvé »).
- **Garder une valeur dans le temps, un relevé** : un Suivi ordinaire, où une automation directe (sans IA : elle exécute elle-même ses commandes) écrit la variable `imc` dans `data.value` sur un instant relatif à son heure prévue. Utile seulement quand la valeur doit rester celle d'alors : une variable se relit à n'importe quel jour passé. Le relevé n'est qu'un usage de l'automation directe, qui n'est pas conçue et croise les events du cœur et l'Alerte (`TODO.md`).

## Prérequis des variables

- **REFERENCE** (`unified-fields.md`) : sans lui, une entrée ne dit pas à quelle fiche elle correspond, et le calcul entrée par entrée (la nutrition) est impossible.
- **Une sélection d'entrées au cœur** : une RÉFÉRENCE, des filtres, des champs, avec sa forme enregistrée, sa partie d'écran et sa lecture. Le pointeur d'un message devient cette sélection plus ce qui ne regarde que l'IA (joindre ou mentionner) ; la lecture du cœur l'utilise seule. Aujourd'hui `PointerConfig`, `PointerSelector` et `EnrichmentProcessor` mêlent les deux et vivent dans le code de l'IA.

## Données structurées

- **Ses colonnes sont des champs de l'utilisateur** (`extra`), déclarés dans la config de l'instance ; le type d'outil n'en déclare aucun, `data` reste vide. Le nom est obligatoire, la date absente. Ce qui en fait un outil est ailleurs : son écran et ses façons rapides de créer des entrées.
- **Le nom est unique dans l'instance**, sans compter la casse ni les espaces autour : c'est par lui qu'on retrouve une fiche (l'IA, le choix d'une RÉFÉRENCE, un import relancé qui met à jour au lieu de dupliquer). Le type d'outil le déclare dans `getEntryFields`, à côté de l'usage du nom ; `ToolDataService` refuse le doublon à toute écriture, dans sa transaction, en nommant l'entrée existante, et le filtre sur `name` compare de la même façon. Une mécanique du cœur : un service propre au type d'outil ne voit pas `tool_data.*`.
- **La vue d'ensemble est un tableau** : le nom puis les N premiers champs de la config (réglage, 2 par défaut ; les choisir, c'est les ranger dans l'éditeur des champs), leurs noms une fois dans l'en-tête fixe, les lignes ne portant que les valeurs, chacune affichée par son type de champ. Une cellule longue revient à la ligne, un champ vide la laisse vide. Toucher un en-tête trie par sa colonne.
- **Une fiche a son écran**, ouvert au toucher d'une ligne : tous ses champs en `EXPANDED`, et un glissement mène aux fiches voisines.
- **Un en-tête de filtre commun aux deux vues** : recherche par nom, filtres (le composant du pointeur) et tri, replié en une ligne qui les résume avec la position (« catégorie = fruit · kcal ↑ · 12 / 48 »), déplié au toucher. On ne parcourt que les fiches filtrées. Il vit le temps de la visite de l'outil : il survit aux allers-retours entre les vues et à la rotation, et repart à zéro quand on quitte l'outil. Une fiche modifiée qui sort du filtre reste affichée jusqu'à ce qu'on la quitte.
- **L'édition se fait dans l'écran de la fiche**, comme `JournalEntryScreen` : « Modifier » passe toute la fiche en saisie, « Enregistrer » l'écrit en une fois ; le glissement est coupé pendant l'édition. « + » sur le tableau ouvre une fiche vide en édition, et rien n'est écrit avant « Enregistrer » (le nom obligatoire et unique interdit l'entrée créée d'avance du Journal). Supprimer, depuis la fiche, après confirmation. Aucune modification dans les cellules du tableau.
- **Remplir en masse** : l'import du cœur (plus haut), le nom servant de clé.
- **La tuile**, une ligne pour l'instant : le nom de la table et son nombre de fiches.

## Questionnaire

- **Ses questions sont les champs de l'utilisateur** d'une entrée datée ; ce qui en fait un outil : la passation, une question par écran avec « suivant » ; l'invitation planifiée ; et, presque gratuite, la passation par l'IA en CHAT, par les modules de communication, qui parlent les mêmes types de champs. Pas de question conditionnelle au départ.
- **À la demande ou planifié, comme Messages.** Planifié, par `ScheduleConfig` et `CoreScheduler` : à l'heure prévue, l'app crée l'entrée « à remplir », sans réponse, datée de cette heure, et notifie ; la notification ouvre la passation. Rempli le lendemain, il reste daté du moment qu'il décrit ; l'instant où il a été rempli s'enregistre dans l'état. À la demande, rien n'est écrit avant la fin de la passation.
- **Une passation interrompue** : planifiée, chaque « suivant » enregistre sa réponse dans l'entrée, qui reste « à remplir » et reprend à la première question sans réponse ; à la demande, elle est perdue. Une question passée reste sans réponse ; l'entrée peut être remplie avec des trous.
- **États** : à remplir, remplie, ignorée — ignorée à la main, un trou assumé qui reste dans l'historique. Aucun délai : planifié, il est censé être rempli.
- **Un onglet « À remplir (n) »**, présent tant qu'il y en a, comme celui d'Objectif, avec « Tout ignorer » pour le retour d'une absence.
- **La passation par l'IA part de l'utilisateur**, jamais d'une planification (une automation tourne sans lui) : « Avec l'IA », sur la notification et sur l'écran, ouvre une nouvelle session CHAT dont la saisie est préremplie, et l'utilisateur envoie. Le texte est un réglage du questionnaire, « Message à l'IA », prérempli d'une consigne générique modifiable (une question à la fois, relancer une réponse floue, montrer les valeurs avant de les enregistrer) ; l'app y joint le pointeur vers l'entrée à remplir, ou vers l'outil pour une passation à la demande. Un CHAT suspend l'automation en cours, qui reprend après. L'entrée finit « remplie » par une opération du questionnaire, commune à l'écran et à l'IA.
- **Ouvrir un CHAT prérempli prend un contenu** (texte et pointeurs), d'où qu'il vienne : la carte d'une automation y passe son message de départ, et le préremplissage d'`AIScreen` par `seed_id` disparaît. Le `seed_id` des sessions AUTOMATION, qui copient leur départ à chaque exécution, reste.
- **L'écran** : « Remplir maintenant » en haut, « Avec l'IA », l'onglet « À remplir », puis l'historique, du plus récent au plus ancien, chaque entrée avec son titre et son état, sans filtre ; toucher une entrée l'ouvre en lecture (`EXPANDED`), et on y modifie une réponse. Les tendances sont l'affaire du Graphique.
- **La tuile**, une ligne : le nom, le nombre à remplir s'il y en a, sinon la dernière réponse en relatif ; touchée quand une entrée attend, elle ouvre sa passation.
- **Son titre** : le nom du questionnaire et le moment prévu en relatif (« il y a 13 h », `FormatUtils.formatRelativeTimePast`), calculé à l'affichage, jamais enregistré.

## Graphique

- **Il dessine, ne calcule pas** : aucun regroupement ni agrégation. Un total par jour est une variable dont le terme porte le jour-même, lue à chaque jour, que le Graphique dessine ; un nombre n'a ainsi qu'une origine, lisible aussi par les alertes et l'IA.
- **Grammaire** : un sous-ensemble de Vega-Lite, que l'IA connaît déjà, dessiné nativement en Compose (pas de vue web : le thème garde l'apparence). Deux écarts : les données viennent d'une sélection d'entrées de l'app, jamais recopiées dans la config ; une couleur est un nom de la palette (`TagColor`). La config reste déclarée en champs, pour que son formulaire soit généré comme les autres.
- **Ouvert** : le sous-ensemble retenu (marques, couches, échelles, période affichée).

## Objectif

Sources : la spec d'origine (arbre objectif → sous-objectifs → items, poids relatifs, seuil de réussite, validation obligatoire en succès ou échec) et le cas « journée-type » testé pendant la refonte des exécutions, retrouvés dans l'historique (`documentation/1 - Synthèse.txt`, `SPECS_REFONTE_EXECUTIONS.md`).

- **Une entrée est une tentative sur une période** : ouverte active, remplie au fil de la période, validée en succès ou échec. Un objectif ponctuel n'a qu'une tentative ; un objectif récurrent en a une par période, créée par la planification commune. La définition vit dans la config, et la tentative en garde une copie : modifier l'objectif ne change pas le jugement des tentatives passées.
- **Un critère est une valeur et une condition** (la condition du cœur, sur tout type). La valeur est *lue* : une lecture de champ sur la période de la tentative (« poids, dernière ») ou une variable ; en échec, le critère n'est ni rempli ni non rempli et la tentative n'a pas de verdict (lecture du cœur). Ou *saisie* : un champ de tout type que le critère déclare, rempli dans la tentative par l'utilisateur ou l'IA, à l'écran, en CHAT ou par une automation (le chemin de l'IA qui juge sans qu'on le lui demande : planifiée en fin de période, un pointeur vers les données à lire), avec un texte facultatif « comment remplir ». La case à cocher est une saisie BOOLEAN « = oui » ; « Sommeil » une saisie DURÉE « ≥ 7 h » : on écrit le fait, la condition juge. Un jugement humain ou de l'IA passe par la valeur saisie et ses consignes (« ton du journal », SCALE 1–10 remplie par l'IA, « ≥ 7 »), jamais par la condition, qui reste calculable.
- **Deux niveaux au plus** : des critères, ou des sous-objectifs qui en contiennent, l'objectif pouvant mêler critères directs et sous-objectifs. Aucune dépendance entre critères.
- **Réussi ou raté, par comptage** : chaque nœud (critère, sous-objectif, objectif) est réussi ou raté, un critère mesuré compris (pas de réussite partielle). Un nœud qui a des enfants demande « au moins N » d'entre eux, tous par défaut, et peut en marquer **indispensables**, critère ou sous-objectif : un indispensable raté fait rater son parent, et lui seul ; pour qu'il fasse rater l'objectif, chaque niveau entre eux l'est aussi. Un indispensable compte dans les N (« au moins 2 sur 3, dont A » : A et B suffisent, B et C non). Un nœud ne se juge que sur ses enfants. Ni poids, ni pourcentage, ni seuil.
- **La vie d'une tentative** : `active` → `à valider` → `réussie` ou `échouée`, ou `expirée`. Un objectif ponctuel a un début et une échéance facultative : sans échéance, sa tentative reste active jusqu'à ce qu'on la valide et n'expire pas. Pendant la période, les critères déclarés se cochent, les mesurés se mettent à jour, et on peut valider avant l'échéance. À l'échéance, l'app montre le verdict calculé, qui attend d'être confirmé : un critère oublié se rattrape là, au lieu de devenir un faux échec. Sans validation après un délai réglable, la tentative devient `expirée`, distincte d'un échec. Valide l'utilisateur ou l'IA ; le verdict se réserve à l'utilisateur par le réglage de champ (`unified-fields.md`).
- **Une tentative validée ou expirée est verrouillée** : le service refuse toute modification, d'où qu'elle vienne, et les valeurs mesurées restent celles lues à la validation. Une erreur se corrige en la **rouvrant** (retour à `à valider`), un acte explicite qui reste dans l'historique de l'entrée, jamais en la supprimant.
- **Ouverture et durée, deux réglages.** Un objectif récurrent ouvre ses tentatives par la planification commune (`ScheduleConfig`) ; la durée d'une tentative se règle à part (jours, semaines, mois) : ouvert chaque mois, « la première semaine sans sucre » dure une semaine. Sans durée, une tentative court jusqu'à la prochaine ouverture. Une seule tentative active à la fois : la config refuse une durée plus longue que l'écart entre deux ouvertures. Un interrupteur marche/arrêt, comme les automations : à l'arrêt, plus aucune tentative ne s'ouvre, celle en cours continue.
- **Une tentative à valider ne bloque pas la suivante** : la période suivante s'ouvre à l'heure, sinon les périodes glisseraient.
- **L'écran** : la tentative en cours en haut — les critères rangés par sous-objectif, chacun avec son compte ; chaque critère a sa ligne d'évaluation, sa valeur (saisissable si elle est saisie) face à sa condition (« 82 kg / ≤ 80 »), avec une jauge pour une valeur ordonnée (nombre, durée, échelle, compte) ; les indispensables se repèrent ; le compte de l'objectif, le temps restant, « Valider ». En dessous, l'historique : une frise de pastilles par verdict pour un récurrent, chaque tentative ouverte en lecture seule avec « Rouvrir ». Un onglet « À valider (n) » n'apparaît que quand des tentatives y attendent, et disparaît avec la dernière validée.
- **La tuile**, une ligne pour l'instant (les autres modes attendent la grille) : nom ; pour un seul critère, sa ligne d'évaluation (« 1204 / ≤ 2100 kcal »), sinon le compte vers ce qui est demandé (« 1 / 2 »), puis « Atteint » tant que la tentative est en cours, « Réussi » ou « Échoué » au verdict ; les dernières pastilles pour un récurrent, et le nombre de tentatives à valider.
- **Ce qu'une tentative enregistre** : la copie de la définition ; pour un critère saisi, sa valeur, champ de l'entrée ; pour un critère lu, la dernière valeur lue et l'instant de la lecture ; rempli ou non se dérive de la valeur et de la condition ; pour la tentative, sa période, son état, et à la validation le verdict, qui a validé et quand. Les comptes et la réussite des sous-objectifs se dérivent des critères, jamais enregistrés ; le verdict l'est, il fait partie du jugement verrouillé.
- **Deux opérations dédiées, pour l'écran comme pour l'IA** : `goal.validate` (calcule et fige le verdict et les valeurs lues, enregistre qui et quand) et `goal.reopen`. Une valeur saisie s'écrit par l'écriture ordinaire (`tool_data.update`) ; une méthode du type d'outil que `ToolDataService` appelle à chaque écriture, comme `settleEntries`, mais qui peut refuser, refuse d'écrire une tentative verrouillée et une valeur lue. L'automation qui évalue un objectif est créée par l'utilisateur : l'IA ne crée pas encore d'automations.
- **Prévenir** : une notification quand une tentative passe `à valider`, par le canal des Messages, une seule par tentative, coupable par un réglage (une automation qui valide à la place de l'utilisateur). Le délai avant expiration est un réglage de l'objectif, 7 jours par défaut.

## Prérequis d'Objectif

- **La lecture du cœur**, et donc la sélection d'entrées sortie du pointeur (prérequis des variables).
