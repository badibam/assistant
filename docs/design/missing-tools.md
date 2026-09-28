# Outils manquants

Conception commencée le 2026-09-27, après la refonte des champs (`docs/DATA.md`, « Champs et entrées ») : un type d'outil ne porte plus que ses façons rapides de créer une entrée et ses calculs sur plusieurs entrées.

## Tri (provisoire)

- **Liste** : livrée (`docs/TOOLS.md`).
- **Objectif** : un outil (section plus bas).
- **Calcul** et **Graphique** : deux outils, pas un. Calcul est une source (ses valeurs sont lues par les autres outils), Graphique une vue (sa sortie s'affiche). Ce qu'ils partagent vit au cœur : la sélection d'entrées et la lecture du cœur.
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
- **Filtres**, facultatifs : ceux du pointeur, avec leur composant, qui passent au cœur avec la sélection d'entrées (prérequis de Calcul).
- **Période** : celle de l'outil qui lit (la tentative d'un Objectif), jamais configurée par la lecture ; ce sont les entrées dont le `timestamp` y tombe. `dernière` est la dernière dans la période, pas la dernière connue.
- **Réductions** : dernière · somme · moyenne · min · max · compte.

  | Type | Réductions | Résultat |
  |---|---|---|
  | NUMERIC, DURÉE | dernière, somme, moyenne, min, max | son type |
  | SCALE | dernière, moyenne, min, max | nombre |
  | BOOLEAN, CHOICE, TEXT | dernière | son type |
  | DATE, DATETIME, TIME | dernière, la plus tôt, la plus tard | son type |
  | RANGE | aucune | – |
  | sans champ | compte | nombre |

  Pas de moyenne d'une heure : 23:30 et 00:30 donneraient 12:00.
- **Test** : les conditions que le type du résultat déclare pour les filtres (`EntryFilters.operatorsFor`).
- **Période vide** : `somme` et `compte` valent 0 ; les autres sont sans valeur, et un critère sans valeur n'est pas rempli.
- **La frontière avec le Calcul** : une source et la période de qui lit, c'est la lecture du cœur ; plusieurs sources combinées, ou une période propre (glissante, par tranche), c'est un Calcul. Un terme de Calcul est une lecture du cœur sans test, avec sa plage, et du type que lui donne sa réduction : la formule vérifie que les types se combinent (durée ÷ durée donne un nombre). Ouvert, pour la spec de Calcul : diviser par une durée (une vitesse, km ÷ durée), qui demanderait de choisir l'unité de la durée, le résultat étant un nombre.

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

## Calcul

Revu le 2026-09-28 : un Calcul combine plusieurs lectures du cœur ; ce que tout outil lit d'une seule source est la lecture du cœur (plus haut).

- **Lu à la demande, rien d'enregistré** : un Calcul ne garde que ses formules, dans sa config. Chacune est une valeur que l'instance expose, comme elle expose ses opérations (`getOperations`) : un Objectif, un Graphique ou l'IA la choisit (« Calcul Santé, lu en imc ») sans cas particulier pour le type Calcul.
- **Le lecteur donne un instant t, le Calcul porte les périodes**, et chaque terme de sa formule a sa plage, relative à un ancrage :
  - **sans tranche** (le glissant), l'ancrage est t : les N jours finissant à t, valable à t, sans limite ;
  - **avec tranche** (le calendaire : jour, semaine, mois, dont le début est fixé dans l'absolu), t tombe toujours au milieu d'une tranche, et un réglage choisit celle qui répond : la tranche qui contient t, partielle, ou la dernière finie avant t (un bilan). L'ancrage est cette tranche : dans la tranche, valable à sa fin, les N jours finissant à sa fin, sans limite.

  Un état se lit ainsi dans une formule : la taille sans limite, la pesée valable à l'ancrage. L'âge demande en plus une fonction de date.
- **La même formule à deux échelles, deux Calculs** (« bilan du jour », « bilan du mois »).
- **Une tentative à cheval sur deux tranches** n'est pas un trou : l'utilisateur fait lire à son Objectif un Calcul dont le découpage lui correspond.
- **Ce qu'il calcule** : des termes nommés, chacun une lecture du cœur sans test avec sa plage, ou une constante, et des formules sur ces noms (`mange - depense`), chacune une valeur exposée avec son unité. Un terme peut suivre une référence entrée par entrée (`data.value × ref(extra.aliment).kcal_100g ÷ 100`) : à reprendre à la spec.
- **La formule s'écrit en texte** (`(mange - depense) / 7`), avec des boutons qui insèrent les noms et une vérification à chaque frappe qui nomme l'erreur, types compris. `+ - × ÷`, parenthèses, nombres ; une fonction ne s'ajoute que pour un cas réel. L'app lit la formule, ne l'exécute jamais comme du code.
- **Données manquantes** : un terme sans valeur ou une division par zéro laisse la valeur sans valeur, jamais 0, et la lecture dit pourquoi. Une entrée dont la référence ne mène nulle part est écartée, et comptée.
- **Garder une valeur dans le temps** : un Suivi ordinaire, alimenté par une automation qui écrit `data.value = {Santé → imc}` sur un instant relatif à son heure prévue. Manquent une automation sans IA et une écriture qui accepte une valeur lue à l'exécution (`TODO.md`).

## Prérequis de Calcul

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

- **Il dessine, ne calcule pas** : aucun regroupement ni agrégation. Un total par jour est un Calcul découpé par jour, que le Graphique dessine ; un nombre n'a ainsi qu'une origine, lisible aussi par les alertes et l'IA.
- **Grammaire** : un sous-ensemble de Vega-Lite, que l'IA connaît déjà, dessiné nativement en Compose (pas de vue web : le thème garde l'apparence). Deux écarts : les données viennent d'une sélection d'entrées de l'app, jamais recopiées dans la config ; une couleur est un nom de la palette (`TagColor`). La config reste déclarée en champs, pour que son formulaire soit généré comme les autres.
- **Ouvert** : le sous-ensemble retenu (marques, couches, échelles, période affichée).

## Objectif

Sources : la spec d'origine (arbre objectif → sous-objectifs → items, poids relatifs, seuil de réussite, validation obligatoire en succès ou échec) et le cas « journée-type » testé pendant la refonte des exécutions, retrouvés dans l'historique (`documentation/1 - Synthèse.txt`, `SPECS_REFONTE_EXECUTIONS.md`).

- **Une entrée est une tentative sur une période** : ouverte active, remplie au fil de la période, validée en succès ou échec avec son score. Un objectif ponctuel n'a qu'une tentative ; un objectif récurrent en a une par période, créée par la planification commune. La définition vit dans la config, et la tentative en garde une copie : modifier l'objectif ne change pas le jugement des tentatives passées.
- **Deux types de critère, selon qui décide de sa valeur.** *Déclaré* : l'utilisateur ou l'IA le juge et le coche, à l'écran, en CHAT ou par une automation (le chemin de l'IA qui juge sans qu'on le lui demande : planifiée en fin de période, un pointeur vers les données à lire) ; il peut porter un texte « comment le juger », lu par qui juge. *Mesuré* : l'app le coche par une lecture du cœur sur la période de la tentative (« poids, dernière, ≤ 80 »), ou par une valeur qu'expose un Calcul, testée de même.
- **Deux niveaux au plus** : des critères, ou des sous-objectifs qui en contiennent. Un sous-objectif regroupe à l'affichage et a son propre score, que l'objectif combine avec celui des autres. Aucune dépendance entre critères.
- **Le score** : un critère est rempli ou non, mesuré compris (pas de réussite partielle). Critères et sous-objectifs ont un poids parmi trois niveaux nommés (secondaire 1, normal 2, important 3). Le score d'un sous-objectif est la part pondérée de ses critères remplis ; celui de l'objectif, la moyenne pondérée de ses sous-objectifs et de ses critères directs, de 0 à 100 %. Le seuil de réussite est un pourcentage réglable, 100 % par défaut. Seule la tentative a un verdict : un critère **indispensable** non rempli la fait échouer quel que soit le score, où qu'il soit rangé.
- **La vie d'une tentative** : `active` → `à valider` → `réussie` ou `échouée`, ou `expirée`. Un objectif ponctuel a un début et une échéance facultative : sans échéance, sa tentative reste active jusqu'à ce qu'on la valide et n'expire pas. Pendant la période, les critères déclarés se cochent, les mesurés se mettent à jour, et on peut valider avant l'échéance. À l'échéance, l'app montre le verdict calculé, qui attend d'être confirmé : un critère oublié se rattrape là, au lieu de devenir un faux échec. Sans validation après un délai réglable, la tentative devient `expirée`, distincte d'un échec. Valide l'utilisateur ou l'IA ; le verdict se réserve à l'utilisateur par le réglage de champ (`unified-fields.md`).
- **Une tentative validée ou expirée est verrouillée** : le service refuse toute modification, d'où qu'elle vienne, et les valeurs mesurées restent celles lues à la validation. Une erreur se corrige en la **rouvrant** (retour à `à valider`), un acte explicite qui reste dans l'historique de l'entrée, jamais en la supprimant.
- **Ouverture et durée, deux réglages.** Un objectif récurrent ouvre ses tentatives par la planification commune (`ScheduleConfig`) ; la durée d'une tentative se règle à part (jours, semaines, mois) : ouvert chaque mois, « la première semaine sans sucre » dure une semaine. Sans durée, une tentative court jusqu'à la prochaine ouverture. Une seule tentative active à la fois : la config refuse une durée plus longue que l'écart entre deux ouvertures. Un interrupteur marche/arrêt, comme les automations : à l'arrêt, plus aucune tentative ne s'ouvre, celle en cours continue.
- **Une tentative à valider ne bloque pas la suivante** : la période suivante s'ouvre à l'heure, sinon les périodes glisseraient.
- **L'écran** : la tentative en cours en haut — les critères rangés par sous-objectif, chacun avec son score ; un critère déclaré a sa case, un mesuré montre la valeur lue face à sa condition (« 82 kg / ≤ 80 ») ; les indispensables se repèrent ; le score face au seuil, le temps restant, « Valider ». En dessous, l'historique : une frise de pastilles par verdict pour un récurrent, chaque tentative ouverte en lecture seule avec « Rouvrir ». Les poids ne se voient que dans la config. Un onglet « À valider (n) » n'apparaît que quand des tentatives y attendent, et disparaît avec la dernière validée.
- **La tuile**, une ligne pour l'instant (les autres modes attendent la grille) : nom, score face au seuil, état, les dernières pastilles pour un récurrent, et le nombre de tentatives à valider.
- **Ce qu'une tentative enregistre** : la copie de la définition ; pour chaque critère, rempli ou non, et pour un déclaré qui l'a coché, quand, et une justification facultative (l'IA en écrit toujours une), pour un mesuré la dernière valeur lue et l'instant de la lecture ; pour la tentative, sa période, son état, et à la validation le score, le verdict, qui a validé et quand. Les scores des sous-objectifs et celui d'une tentative active se dérivent des critères, jamais enregistrés ; le score final l'est, il fait partie du jugement verrouillé.
- **Des opérations dédiées, pour l'écran comme pour l'IA** : `goal.check` (cocher ou décocher un critère déclaré, justification facultative), `goal.validate`, `goal.reopen`. Chacune porte une règle que la modification générique ignore (un critère mesuré ne se coche pas à la main, une tentative verrouillée ne change pas, qui et quand s'enregistrent d'office, valider calcule et fige le score) : `tool_data.update` sur une tentative est refusé, avec une erreur qui renvoie à elles — par une méthode du type d'outil que `ToolDataService` appelle à chaque écriture, comme `settleEntries`, mais qui peut refuser. Un `goal.check` de l'IA sans justification est refusé. L'automation qui évalue un objectif est créée par l'utilisateur : l'IA ne crée pas encore d'automations.
- **Prévenir** : une notification quand une tentative passe `à valider`, par le canal des Messages, une seule par tentative, coupable par un réglage (une automation qui valide à la place de l'utilisateur). Le délai avant expiration est un réglage de l'objectif, 7 jours par défaut.

## Prérequis d'Objectif

- **La lecture du cœur**, et donc la sélection d'entrées sortie du pointeur (prérequis de Calcul).
