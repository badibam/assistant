# Recette sur l'appareil

Ce que la suite de tests ne voit pas : les écrans, les migrations sur une vraie base, l'IA en vrai. Une ligne dit quoi faire et quoi voir ; le pourquoi est dans le commit. Une vérification faite sort de la liste. Un commit qui demande une vérification ajoute une ligne sous l'écran qu'il touche. L'app est en portrait seul : « recréer l'écran », c'est basculer le thème sombre, ou activer « Ne pas conserver les activités » puis revenir à l'app.

## Mise à jour et démarrage

- Après la migration 60 (tables `mcp_clients` et `mcp_tokens`) : l'app démarre, sans erreur de Room dans le journal.
- Après la migration 61 : Bilan quotidien, Journal de rêves et Histoire personnelle s'ouvrent, leur champ de texte supplémentaire en place, et l'IA les lit (TOOL_DATA, SCHEMA).
- Après la migration 62 : rien ne bouge à l'accueil ni dans les zones ; le journal dit combien de groupes perdus ont été vidés.
- Après la migration 63 : les zones s'ouvrent, leurs icônes neutres ; une sauvegarde faite avant s'importe de même.
- Importer une sauvegarde faite avant la mise à jour (base 52 ou plus ancienne) : chaque zone montre ses outils dans le même ordre, l'accueil ses zones de même ; les récurrences et les pointeurs se relisent. Juste après, l'écran des journaux filtré sur « Error » ne montre aucune ligne `MIGRATION`.
- Un pointeur qui filtrait un instant par « = » (avant la migration 52) est refusé en le disant : rien ne part en silence.

- Après le passage à Compose 1.12 (BOM 2026.09.00) : parcourir l'accueil, une zone, un outil de chaque type, sa config, Réglages, le chat, dans les deux thèmes ; les fenêtres de dialogue, les listes qui défilent, le glisser-déposer et le clavier se comportent comme avant.

## Démo

- Réglages › Démo : désactiver « Installer la démo à chaque mise à jour » et enregistrer ne supprime rien, la démo reste ; réinstaller l'APK la laisse telle quelle ; réactiver le réglage n'installe rien avant la mise à jour suivante.
- Réglages › Démo : « Réinstaller » puis revenir en arrière aussitôt : une bande en haut de chaque écran dit « Installation de la démo » et son étape jusqu'à la fin, puis un toast dit qu'elle est terminée ; la démo est entière. Revenir dans Réglages › Démo pendant l'installation : les boutons attendent, la roue et l'étape s'affichent.
- Ajouter une entrée dans un outil de la démo et un outil dans une de ses zones, puis Réglages › Démo › « Supprimer la démo » : la démo et ces deux ajouts disparaissent, le reste de l'app est intact ; « Réinstaller » la remet entière.
- Chaque suivi de la démo, sauf Repas, montre un raccourci d'ajout rapide nommé comme ses entrées : Verre ajoute 1, Pesée et Récolte ouvrent la saisie dans leur unité, Nuit lance un chrono.
- Pendant un import d'un gros fichier (Données structurées), lancer « Réinstaller » la démo ou exporter une sauvegarde : refusé, le message nomme l'import en cours ; la bande compte les lignes écrites par 500.

## Chat IA

- Champs supplémentaires : « Ajoute une note dans Dev › Assistant » — l'IA remplit Catégorie et Portée sans lire le schéma (l'aperçu les nomme) ; une note créée sans eux, le résultat le dit (`extra_left_empty`) et l'IA le signale. Un outil changé de zone par l'IA : elle dit que son groupe est resté derrière.
- Couleurs d'icônes par l'IA : « Mets l'icône de la zone Santé en rose et celle du Poids en bleu » — CREATE/UPDATE_ZONE prend `icon_color`, l'outil la reçoit dans sa config ; « en gris » ou « en cyan » est refusé avec les huit noms.
- Zones par l'IA : « Crée une zone Voyages dans le groupe X, affichage minimal, avec les groupes d'outils A et B » — la zone arrive rangée et réglée ; un groupe de l'accueil inventé est refusé avec la liste des groupes, et l'IA se reprend. « Renomme le groupe d'outils A en C » passe par UPDATE_ZONE avec `renames`, et ses outils le suivent.
- Après le catalogue des commandes (`AICommands`) : « Combien de pesées cette semaine ? » passe par `TOOL_DATA` avec `period`, sans refus ; une commande à un paramètre inventé est refusée en le nommant, et l'IA la corrige au tour suivant ; le L1 (journal `Prompt data built`) garde sa taille d'avant, à quelques tokens près.
- Réglages d'une session de chat : l'interrupteur de validation, à droite de son libellé, bascule et la validation suit.
- Le chat ouvert, sans session puis dans une session : il couvre l'écran jusqu'en bas, la barre de navigation masquée comme ailleurs, revenant un instant au glissé du bord ; la touche Retour le ferme ; les bandeaux du haut ont leur couleur d'avant.
- Session CHAT avec DeepSeek : elle passe, et son coût s'affiche sans « ≥ ».
- « Importe ce fichier dans une nouvelle table » avec un CSV joint : l'IA crée la table, puis au tour suivant `IMPORT_PLAN` et `IMPORT_DATA` ; la carte de validation nomme le fichier, ses lignes et la table ; la table a ses colonnes et ses lignes.
- Le message de départ d'une automation ne propose pas le trombone.
- DeepSeek, sur une dizaine de réponses : une réponse dont le JSON est entouré de texte ou d'un bloc de code est exécutée sans `FORMAT_ERROR` ; un message système cite le texte écarté, et la réponse suivante ne le reprend pas.
- Session CHAT avec OpenAI : la première réponse passe (le mode JSON, `text.format`, est accepté par l'API Responses).
- « Coche Lait dans Courses » : l'IA appelle l'opération `check` et l'élément est coché ; dans une liste qui retire ce qui est coché, il disparaît.
- L'IA crée un Questionnaire sans `ai_message` : la création passe, avec le message par défaut.
- Après la mise à jour : une ancienne session affiche ses tokens et un coût en « ≥ » ; une nouvelle, un coût exact, le même dans la fiche de coût et sur la carte d'historique d'automation.
- Démarrer l'app en mode avion après l'avoir déjà utilisée en ligne : une session affiche quand même son coût (prix gardés sur le téléphone).
- Importer une sauvegarde faite avant la mise à jour : les conversations reviennent, leur coût en « ≥ ».
- « Quelle heure est-il ? », puis créer une entrée datée d'hier : la date est juste. Les résultats de données reçus par l'IA sont en ISO 8601.
- Après `CREATE_ZONE` puis `CREATE_TOOL`, l'IA enchaîne sans redemander la liste des zones ni des outils.
- Demander à l'IA une zone et un outil dedans en une fois : elle crée la zone, puis l'outil au tour suivant avec l'id reçu, sans id inventé dans la même réponse.
- Pointeur : le fil d'Ariane et les zones puis les outils s'affichent comme avant ; dans un outil, rien n'est listé en dessous ; passer d'un outil à celui d'une autre zone par le fil garde les cases et la période.
- Pointeur en chat, période « Depuis : date relative, il y a 2 jours, début » : « Soit : » montre la date obtenue, et après confirmation la phrase du pointeur dit cette date. Pointeur du message de départ d'une automation : « Par rapport à : l'heure prévue de l'exécution » s'affiche une fois, la période se relit « la veille », « le moment même ». Un filtre sur un champ DATE propose des jours, sans heure ni unité « heure ».
- Une IA qui enchaîne plus de 10 appels seule s'arrête et rend la main avec un message.
- Faire écrire par l'IA une valeur hors d'une échelle ou hors des options d'un choix : le refus lui revient.
- Faire créer et modifier des entrées de tracking par l'IA : `raw` est juste.
- Faire poser une récurrence sur un outil Messages par l'IA, et retirer la description d'une zone.
- Seuil de données à 5 000 : un pointeur sur un gros outil fait apparaître « Données volumineuses » ; « Envoyer » et « Refuser » font répondre l'IA avec ou sans les données. Même chose quand l'IA demande elle-même les données.
- Fermer l'app de force pendant une attente (validation, question, données volumineuses), la rouvrir : les boutons reviennent.
- Recréation pendant la composition : deux blocs, un pointeur ouvert sur le second avec un outil, ses cases et une période choisis. Après la recréation, la fenêtre et les choix sont là, et le pointeur arrive dans le second bloc.
- Sélecteur de pointeur dans le chat : descendre App › Zone › Outil puis remonter par le fil d'Ariane, ce qui n'a plus de sens au niveau atteint est effacé ; sur un outil, période, un filtre par valeur (nombre, choix, date) et deux champs cochés — la phrase du bas dit ce qui part, et l'IA reçoit exactement ça. Un filtre rempli puis la fenêtre validée sans « + » : il est gardé. Aucune case cochée avec un filtre : l'IA reçoit la requête sans les entrées et peut la lancer.
- Après la migration 45 : les anciennes conversations affichent leurs pointeurs avec les noms actuels. Renommer un outil pointé : le fil, sans quitter la discussion, l'aperçu de la session dans la liste et la carte du message de départ d'une automation montrent le nouveau nom ; supprimer un outil pointé : « Outil : supprimé ». Une automation pointant un outil renommé : son IA lit le nouveau nom avec l'id.
- Pointeur sur une zone avec « entrées » et une période : l'IA reçoit les entrées de chacun de ses outils sur cette période, chacune avec son schéma ; sans case cochée, elle reçoit les filtres de la période et peut lire outil par outil. Descendre dans un outil garde la période, remonter à la zone garde les entrées et la période.
- Filtre sur un champ texte (« contient » ou « est ») : sous la saisie, « Valeurs déjà saisies » propose les valeurs de l'outil, les plus fréquentes d'abord ; en toucher une remplit la valeur.
- Même sélecteur dans le message de départ d'une automation : période et filtre de date en valeurs relatives, recalculées à l'exécution suivante. Le résumé dit le côté de chaque borne : « entre le début de « 7 jours avant » et la fin de « le jour-même » », « Pesé le depuis le début de « la veille » ».
- Après la migration 46 : une ancienne conversation avec un pointeur à période s'affiche avec la même période ; une automation dont le départ pointe « la veille » lit encore la veille à son exécution suivante (le côté de chaque borne dans le résumé). Importer une sauvegarde d'avant : même chose.
- Demander à l'IA les entrées d'hier : elle écrit une `period` en dates relatives (`{"relative": …}` avec START et END), la lecture passe.
- Après la migration 44 : une ancienne conversation qui contenait des pointeurs les affiche encore, et les renvoyer dans une nouvelle session joint les mêmes données.
- Demander à l'IA une question à plusieurs volets (un choix, un texte, une date) : chaque champ se saisit avec son composant, Confirmer reste grisé tant qu'un champ obligatoire est vide, et la réponse réapparaît dans le fil champ par champ, la date affichée comme telle. Une confirmation sans champ : Confirmer et Annuler seuls. Recréer l'écran en cours de saisie : les valeurs restent.
- Demander à l'IA de créer une entrée puis de la modifier : elle modifie directement, avec l'id que la création lui a rendu, sans relire l'outil entre les deux.
- Faire créer puis modifier des entrées par l'IA, sans validation : sous chaque commande du message « actions exécutées », les entrées écrites s'affichent champ par champ (date lisible, échelle en jauge, choix par son libellé), la modification avec ses seuls champs changés. Supprimer ensuite l'outil : le fil dit que les valeurs ne peuvent plus s'afficher.
- Validation active, dans une nouvelle session, dire à l'IA d'ajouter une entrée à un outil existant sans rien lire avant : un message renvoie le schéma et dit l'écriture non faite, aucune carte de validation avant qu'elle la renvoie. Lui faire demander dans une même réponse le schéma et les entrées d'un outil : les deux lui arrivent.
- Validation active, faire créer deux entrées par l'IA puis en modifier une : la carte de validation montre chaque entrée champ par champ (date lisible, échelle en jauge, choix par son libellé), et pour la modification seulement les champs changés.
- Mode avion puis message CHAT : échec immédiat avec un message réseau, rien ne part (les NOTES disaient que l'appel partait quand même).
- Interrompre pendant que l'IA réfléchit, réseau coupé ou non : le composeur revient tout de suite, avec « Round IA interrompu » et la note de coût inconnu ; le coût de la session s'affiche en « ≥ ».
- Une question de l'IA avec un oui/non et une échelle obligatoires : rien n'est choisi au départ, les deux libellés portent un astérisque, Confirmer s'active une fois les deux répondus ; « non » se répond d'un toucher, le minimum d'une échelle vide aussi.
- Une question de l'IA : « Ajouter une précision » ouvre un texte, envoyé avec la réponse, et l'IA en tient compte. « Répondre par un message » débloque le composeur, le formulaire reste répondable ; envoyer un message avec un pointeur ferme le formulaire, « Module remplacé par un message » apparaît et l'IA répond au message. Recréer l'écran entre-temps garde la précision.
- Rejeu ciblé : « crée un suivi de poids, puis ajoute une pesée » — la config passe par `units`, l'entrée porte `data.unit`.
- Composeur en blocs : écrire, ajouter un pointeur puis « + Texte » — chaque bloc arrive après le bloc actif et le devient, le nouveau texte prend le clavier ; déplacer un bloc par sa poignée dans la zone qui défile ; supprimer le bloc actif (celui d'avant s'allume), puis le dernier (un texte vide reste) ; tourner l'écran avec un dialogue de pointeur ouvert en modification ; le message envoyé et sa relecture gardent l'ordre des blocs, sans les textes vides.
- Images (`docs/design/message-images.md`) : « Photo » ouvre l'appareil photo sans demande d'autorisation, « Galerie » le sélecteur de photos ; la vignette arrive après le bloc actif, l'image entière au toucher, droite pour une photo prise en portrait ; avec Claude, la réponse parle de la photo, et une question au tour suivant sur un détail de la photo trouve sa réponse ; DeepSeek : `deepseek-flash` reçoit l'image, `deepseek-v4-pro` (sans images) : le bloc le dit, l'envoi est refusé ; rouvrir et enregistrer une config Claude montre « Lit les images : oui » ; supprimer la session efface ses fichiers (`files/attachments`) ; exporter une sauvegarde avec images, la réimporter, les vignettes reviennent ; un ancien `.json` s'importe encore.

## Accès externe (serveur MCP)

Avec le relais en place (son projet), son adresse et son secret dans Réglages › Accès externe.

- « Ouvrir l'accès » sans adresse ni secret : refusé, le message le dit. Avec : la notification « Accès externe ouvert » paraît, l'écran dit « Ouvert ».
- La tuile « Accès externe » ajoutée aux réglages rapides : un toucher ouvre, un autre ferme ; son état suit l'écran.
- Un secret faux : l'accès se ferme, l'écran dit que le relais ne reconnaît pas l'app.
- Ajouter le connecteur dans claude.ai avec l'adresse que l'écran donne (`…/mcp`) : la page montre un code à 4 chiffres ; le téléphone notifie la demande, sa fenêtre nomme le client et le domaine de retour ; le bon code autorise, la page revient à claude.ai, le connecteur est connecté et le client paraît dans la liste.
- Trois codes faux : la demande est refusée, la page le dit. « Refuser » : de même.
- Dans un chat claude.ai : le premier appel est `app_context` (ou un autre outil refusé, qui le renvoie à `app_context`) ; une lecture d'entrées avec une période passe ; une création d'entrée paraît dans l'app ; chaque réponse commence par la date et l'heure.
- Ce que claude.ai fait des outils : les lectures marquées comme telles, l'autorisation outil par outil ; ce qu'il fait d'une session MCP d'une conversation à l'autre (instruit, pas exigé).
- 30 minutes sans appel : l'accès se ferme seul, la notification part. « Fermer » dans la notification : de même.
- Révoquer le client : l'appel suivant de claude.ai est refusé, il redemande l'autorisation.

## Automations

- Rattrapage après la mise à jour : une automation programmée « sans limite » le reste, une fenêtre de 3 heures se relit « jusqu'à un délai » de 3 h. Dans l'éditeur : sans choix, l'enregistrement est refusé ; « jusqu'à un délai » demande la durée ; retirer la planification puis enregistrer retire aussi le rattrapage.
- Rattrapage réel : automation programmée, app fermée plusieurs jours. Voir quelle session est reprise et ce que montre l'écran d'historique ; une donnée de la date prévue est écrite avec un timestamp ISO explicite.
- Créer une automation planifiée : sa prochaine exécution s'affiche sur sa carte. La fenêtre de rattrapage se saisit dans l'éditeur.
- Seuil de données bas : le refus apparaît dans l'historique d'exécution, et l'IA resserre sa requête.
- Lancer une automation, couper le réseau pendant l'appel, puis Stop : la session se ferme tout de suite. Sans Stop, elle attend le réseau si l'appel n'était pas parti, ou s'arrête avec « Requête envoyée, réponse perdue » et un coût en « ≥ » sur sa carte.
- La tuile d'une automation dans sa zone : son nom lisible sur une ligne, et à côté deux petits boutons « On » / « Off », toucher le bouton choisi ne change rien, basculer ne fait pas clignoter l'écran ; les jours et les mois d'une planification sont des cases à cocher.

## Outils et saisie

- Liste réglée en « Éléments courts dans la tuile », dans les deux thèmes : 8 éléments en étendu, 24 en carré, une ligne chacun ; chaque case se coche sans toucher sa voisine (la case d'une ligne fait environ la moitié de celle d'avant). Réglage retiré : la tuile revient à 4 éléments sur deux lignes.
- Créer et modifier une entrée de chaque type : tracking de chaque sorte, note insérée à une position, journal, occurrence Messages, et une entrée avec champs personnalisés.
- Une plage de champ personnalisé dont le début dépasse la fin est refusée à l'écran.
- Champ DATE et DATETIME d'une entrée : une date personnalisée s'enregistre ; « Date relative, la veille, début » et « Maintenant » enregistrent la date qu'affiche « Soit : », qui revient en date personnalisée à la réouverture ; un champ facultatif se vide par sa croix, un champ obligatoire n'en a pas.
- Échelle : un champ de 1 à 10 affiche et enregistre « 7 », pas « 7.0 » ; une échelle de 0 à 5 par 0.5 s'arrête sur chaque demi-point et affiche « 3,5 » ; l'éditeur refuse une échelle de 1 à 10 par 2. Le curseur de note d'un tracking d'échelle se comporte comme avant.
- Sélecteurs de date et d'heure normaux dans l'entrée de journal, l'entrée de tracking, les champs DATE et DATETIME, le sélecteur de période. Un DATETIME déjà enregistré s'affiche comme une date.
- Journal, « Annuler » en modification : l'écran revient au texte stocké, et une entrée créée, rouverte puis annulée reste en place.
- Messages : créer puis modifier une récurrence, voir les messages partir ; mettre l'outil en pause, voir les envois s'arrêter.
- Messages, listes « à venir » et « reçus » : un indicateur pendant le chargement, jamais « rien de prévu » avant d'avoir lu.
- Messages, onglets « reçus » et « à venir » : ils basculent comme avant, l'onglet choisi souligné.
- Tuile Messages : un message non lu en gras, à la taille du texte courant, au-dessus des lus en petit.
- Messages, « Notif du matin » : après la mise à jour, les deux messages prévus en double ne le sont plus (un par jour). Changer plusieurs fois la récurrence de suite : jamais plus d'un message prévu par heure de la récurrence, et l'app ne rame pas.
- Lectures filtrées par les écrans : l'historique d'un suivi par période (jour, semaine, mois) montre les mêmes entrées qu'avant, et l'écran Messages range chaque message selon son état.
- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : avertissement sur l'écran de l'outil, occurrences déjà prévues gardées et envoyées, erreur dans la config, enregistrement refusé sans rien écraser.
- Zone, écran généré : créer puis modifier (nom, description, icône, groupe, groupes d'outils ajoutés, réordonnés, supprimés) ; un nom vide est refusé avec le message du service ; effacer la description et enregistrer, elle disparaît.
- Écran de config généré, pour chaque type d'outil, en création puis en modification : tous les réglages s'affichent et s'enregistrent (icône, groupe, zone, champs personnalisés avec chaque type et ses réglages, options de choix avec couleur, planification de Messages et ses durées) ; un nom vide est refusé avec le message du service ; une recréation garde la saisie.
- Tracking : passer de numérique à compteur garde les raccourcis et leur valeur, sans unité ; changer les bornes d'une échelle ou le type d'un champ personnalisé demande confirmation avec les nombres de valeurs retirées et d'entrées supprimées ; d'occurrence à numérique avec des entrées, la valeur à leur donner est demandée.
- Suivi numérique : taper « lb » dans une saisie l'enregistre en « lb », et la saisie suivante a « lb » dans sa liste d'unités (la première de la liste reste préremplie) ; même chose en cochant « ajouter aux raccourcis », le raccourci créé et « lb » gardé dans les unités ; un suivi sans unité reçoit la première depuis une saisie. La config d'un suivi numérique n'offre plus d'unité dans le bloc de la valeur ; celle d'un compteur et d'un champ nombre de l'utilisateur, si.
- Modifier une entrée (suivi, note, journal, message) et vider un de ses champs personnalisés, le dernier compris : après l'enregistrement, le champ est vide, pas revenu à son ancienne valeur.
- Valeur par défaut d'un champ personnalisé : dans la config, la case prend la forme du champ (options d'un choix, curseur d'une échelle) et suit les options ajoutées ; une valeur hors des options est refusée à l'enregistrement. Une nouvelle entrée (suivi, note, journal) part avec elle, effacée elle ne revient pas, une recréation ne la remet pas ; toucher un raccourci de suivi l'enregistre ; une entrée modifiée n'est pas touchée. L'IA la voit dans le schéma, et un module de communication dont un champ en porte une part prérempli.
- Suivi oui/non ou échelle : la fenêtre de saisie part vide. Un champ oui/non, échelle ou plage facultatif laissé vide s'enregistre sans valeur, et l'historique n'affiche rien pour lui.
- Réglages nombre (bornes et pas d'une échelle, d'un nombre) : « 0 » s'affiche « 0 », un champ se vide entièrement, « 0.5 » se tape point compris. Le pas d'une échelle part à « 1 », son défaut ; il s'efface entièrement, et vide son libellé dit « Pas (par défaut : 1) ».
- Notes : activer « Titres » dans la config ; la fenêtre d'une note a un champ Titre facultatif, la carte et la tuile montrent le titre au-dessus du texte. Vider le titre d'une note l'efface. Désactiver « Titres » : plus aucun titre ni champ ; le réactiver : les titres reviennent.
- Config d'un outil : ses champs personnalisés viennent après ses réglages propres (valeur principale, unités, raccourcis).
- Glisser-déposer par la poignée : les notes (la position tient après rechargement, pas de retour visible à l'ancien ordre au lâcher), les champs personnalisés d'une config et les options d'un de ces champs (la liste imbriquée bouge seule, l'autre ne suit pas), un classement ; la page défile quand on tient un élément près du bord haut ou bas ; la saisie d'un champ de la config garde le focus pendant qu'on tape ; TalkBack propose « Monter » et « Descendre » sur la poignée.
- Listes de blocs dans une config (champs personnalisés, options d'un choix avec leur pastille, raccourcis d'un suivi, moments d'une récurrence) : fermées à l'ouverture de l'écran, une ligne de résumé chacune (« Sans nom » si vide) ; toucher la ligne ouvre et referme, plusieurs ouvertes à la fois ; un bloc ajouté s'ouvre ; un bloc déplacé ou une suppression au-dessus garde ouverts les bons blocs ; une recréation garde ce qui est ouvert.

- Liste : créer une liste, ajouter des éléments par le champ du bas ; cocher, voir l'élément descendre sous le trait ; décocher, le voir reprendre sa place ; glisser un élément parmi les non cochés, la place tient après rechargement ; ouvrir un élément, le renommer, remplir un champ personnalisé (visible sur sa ligne), le supprimer ; « Tout décocher » ; avec « Un élément coché est supprimé aussitôt » réglé, cocher supprime l'élément, depuis l'écran comme depuis la tuile ; l'ajout se fait en bas, sous le titre « Ajouter un élément », le champ « Contenu » puis les champs personnalisés à remplir sous le nom, à leur valeur par défaut, qui y reviennent après l'ajout ; un trait sépare la liste de ce formulaire, et un autre les cochés des autres ; une recréation garde la saisie en cours et l'élément ouvert.
- Liste et IA : demander à l'IA d'ajouter trois éléments puis d'en cocher un ; ils apparaissent, l'élément coché en bas.

- Champs personnalisés affichés : sur la liste des entrées d'un journal (un par ligne, le nom à la taille du titre, sous un trait qui les sépare du texte), sur une entrée ouverte (un bloc par champ), sur une carte de note et sur une ligne de liste (deux par ligne, le nom en petit) ; un champ « Toujours afficher » vide y dit « Aucune valeur » ; un texte long prend toute la largeur. Décocher « Afficher le nom des champs » dans la config retire les noms partout ; une Liste neuve part sans les noms, les autres outils avec.

- Saisie des champs personnalisés (fenêtre d'un élément de liste, formulaire d'ajout, note, entrée de journal, envoi de Messages, entrée de suivi) : plus de titre « Champs personnalisés », le nom de chaque champ en petit au-dessus de sa saisie, un trait entre deux champs.

## Réglages et affichage

- Couleurs d'icônes (`icon-colors.md`), dans les deux thèmes et les deux modes : dans la config d'une zone et d'un outil, la rangée de choix sous l'icône, l'icône à côté de son sélecteur qui change aussitôt ; à l'enregistrement, la tuile et l'en-tête de la page dans la couleur ; « aucune » rend l'icône neutre. Thème par défaut : une icône neutre a sa pastille grise, les titres des tuiles alignés ; les marques (attente, chrono) au coin de la pastille. Les icônes, dans leur couleur, devant chaque zone et chaque outil du choix d'un pointeur ou d'un champ RÉFÉRENCE, de la duplication, et de la liste des zones dans la config d'un outil ou d'une automation (et devant la zone choisie dans le champ).
- Mode d'édition d'une grille, thème rétro, dans une zone et sur l'accueil : une tuile choisie reste pleine, les autres tuiles et le reste de l'écran tramés en damier, sans transparence ; les pointillés des cases visibles à travers les tuiles tramées, jusque sous une tuile de plusieurs cases, et toujours au défilement. Thème par défaut : les tuiles et le reste de l'écran pâlis comme avant.
- Choix de la zone dans la config d'un outil et dans une automation, dans les deux thèmes : les zones dans l'ordre de l'accueil, sous le titre de leur groupe puis « Sans groupe » ; un titre ne se choisit pas ; deux zones de même nom se choisissent chacune.
- Groupes de l'accueil (Réglages › Écran principal) : renommer un groupe qui a des zones, enregistrer — ses zones restent dedans sous le nouveau nom. Échanger deux noms : chaque zone garde sa section. Supprimer un groupe qui a des zones : refusé, en nommant ces zones. Supprimer puis rajouter le même nom : refusé de même s'il a des zones.
- Groupes d'outils d'une zone (modifier la zone) : renommer un groupe — ses outils, automations et variables le suivent ; supprimer un groupe utilisé : refusé, en nommant ce qui l'utilise.
- Changer un outil de zone dans sa config : le groupe se vide aussitôt, le sélecteur propose ceux de la zone choisie ; enregistré, l'outil est dans ce groupe-là ou hors groupe.
- Portrait seul : tourner le téléphone sur n'importe quel écran (accueil, zone, outil, chat, sélecteur de date et d'heure) ne change rien ; les sélecteurs de date et d'heure s'ouvrent sur le calendrier et le cadran.
- Icônes à la place des glyphes : les boutons d'action en icône (enregistrer, supprimer en corbeille, flèches de période, en-têtes) ont leur couleur de bouton et une taille lisible ; dans le chat, les enrichissements du composeur et d'un message, le résultat d'une commande et le compte à rebours de fermeture ; les boutons horaire et déclencheurs d'une automation ; le fournisseur actif ; l'avertissement d'une validation ; le chemin des sélecteurs de copie et de pointeur.
- Écrans de réglages générés (format, limites IA, validation, écran d'accueil) : chacun s'ouvre sur les valeurs stockées, s'enregistre et se retrouve à la réouverture ; fuseau et langue à « Aucun » suivent le téléphone ; un changement du fuseau ou du début de journée est suivi aussitôt par l'historique ; les groupes de zones ajoutés apparaissent sur l'accueil et dans l'écran de zone. L'écran de validation, qui était un bouchon, fonctionne.
- Fournisseurs d'IA, écran généré, pour Claude, OpenAI et DeepSeek : la clé s'affiche masquée et l'œil la montre ; les modèles se listent à l'ouverture pour une clé enregistrée, et après saisie d'une nouvelle clé ; effort obligatoire pour DeepSeek ; enregistrer, rouvrir, retrouver les valeurs ; une session de chat part avec le modèle choisi.
- Fuseau de l'app différent de celui du téléphone : l'historique range chaque entrée dans le jour affiché sur elle, le sélecteur de période et l'éditeur de planning montrent l'heure de l'app, « aujourd'hui » et « hier » du journal suivent. Un début de semaine changé est suivi par l'historique et les sélecteurs de période.
- Icônes : couleur du thème partout, et le sélecteur s'ouvre, cherche et parcourt une catégorie sans lenteur.
- Couleurs d'état données par le thème : l'écran des journaux montre chaque niveau en pastille puis en libellé (erreur en rouge, avertissement en orange, info en mauve, verbose et debug en gris) ; la pastille d'une carte d'exécution (terminée, interrompue, en erreur) garde sa couleur, le triangle d'une validation sensible prend l'orange doux de la palette.
- Copier un outil ou une automation : le sélecteur s'ouvre en carte, la zone puis l'élément choisis ressortent mis en avant, le chargement tourne au milieu.
- Réglages, « Interface utilisateur » : un écran de réglages avec « Sons de l'interface », activé ; le désactiver, enregistrer, rouvrir le retrouve désactivé. Le thème par défaut reste muet dans les deux cas.
- Boutons désactivés (enregistrer un formulaire incomplet, un bouton d'action grisé) : toujours sans effet au toucher ; le bouton actif, lui, répond comme avant.
- Espacements nommés, thème par défaut : rien ne bouge, sauf des écarts de 2 à 8 dp, à juger acceptables, là où une valeur rare a été arrondie : les lignes d'une validation et l'avertissement en retrait, les valeurs d'un champ affichées (choix ordonné, tags), les blocs d'une note sur sa tuile, les puces d'une condition et des variables, la carte des variables, le Graphique, une feuille des données structurées, une carte d'exécution, le chat flottant vide.
- Touche Retour : outil → zone → accueil → confirmation avant de fermer ; annule sur la création de zone, les réglages Claude/OpenAI et une entrée de journal en modification ; ferme les fenêtres sans effet de bord.

## Champs RÉFÉRENCE

- Champ RÉFÉRENCE restreint aux entrées d'un seul outil (`aliment`) : le choix s'ouvre dans cet outil, la recherche filtre les entrées, toucher une entrée l'ajoute au fil d'Ariane et « Confirmer » l'enregistre. Restreint à plusieurs outils : ils sont listés à l'app avec leur zone. Acceptant zones et outils : « Confirmer » n'est actif que sur une zone ou un outil atteint.
- Ajouter à un outil un champ de l'utilisateur de type Référence, limité aux entrées d'un autre outil : le choix liste ses entrées avec une recherche ; l'entrée enregistrée affiche le nom de la fiche, renommer la fiche change l'affichage, la supprimer affiche « supprimé » et l'entrée reste modifiable.
- Même champ sans restriction : choisir d'abord un outil puis son entrée. Avec les sortes Zone et Outil : leurs listes s'affichent.
- Retirer l'outil autorisé de la config : la confirmation compte les valeurs retirées.
- Faire lire ces entrées par l'IA : chaque référence arrive avec `name` ; lui faire écrire une référence vers un id inventé : refusée avec la raison.

## Variables

- Sélection (pointeur, lecture d'une variable) : le navigateur est ouvert tant qu'aucun outil n'est choisi ; un outil choisi, il se replie en « Outil (Zone) — Changer », qui le rouvre sur la zone ; au pointeur, une zone choisie montre déjà sa période tout en listant ses outils. Rouvrir une variable enregistrée montre son outil replié, sa période et ses filtres. Les filtres d'une lecture et des Données structurées n'offrent plus « Choisir les champs ».
- Filtres d'un pointeur : ajouter un filtre par valeur (nombre, choix, texte avec ses valeurs proposées, entre deux bornes), une période sur un champ date, « sans réponse » ; chacun se relit dans la liste, part à l'IA, et la requête de l'IA avec ses filtres s'affiche lisible sur sa carte. Un instant n'offre pas « = ».
- Terme d'une formule : passer de constante à variable puis à lecture et revenir ; une constante se tape comme un nombre ; une variable se choisit en descendant dans sa zone, son nom s'affiche ; enregistrer puis rouvrir garde chaque terme.
- Champ RÉFÉRENCE qui accepte les variables, et critère « variable » d'un Objectif : descendre dans une zone montre ses outils puis ses variables (« Variable » en petit), groupe par groupe sous le titre de chaque groupe, « Hors groupe » en dernier ; une zone sans groupe, une seule liste. Choisir une variable l'enregistre, son nom s'affiche, et le critère la lit à la fin de la tentative.
- Lecture d'une variable : sur un champ numérique, la liste des réductions (dernière, somme, moyenne, min, max) ; sur un champ texte ou « compter les entrées », une ligne « Réduction : … » sans liste.
- Choix d'un champ (filtres du pointeur, lecture d'une variable, champ d'un critère d'Objectif, import CSV, tri des Données structurées) : chaque champ s'affiche par son nom seul ; deux champs de même nom (un champ personnalisé « Note » et un champ « Note ») montrent leur chemin, et choisir l'un garde bien celui-là.
- Éditeur de formule : placer le curseur au milieu de la formule et toucher un nom ou un opérateur : il s'insère là, espacé, et le curseur se place juste après ; saisir au clavier dans n'importe quel champ de texte de l'app (accents, correction automatique) se comporte comme avant.
- La période d'une lecture sur l'écran d'une variable : « Par rapport à : l'instant lu », « Depuis » et « Jusqu'à » avec « Le moment même » et « Sans limite » ; une période enregistrée se relit telle qu'elle a été réglée.
- Faire créer par l'IA une variable « kcal du jour » (somme par entrée de Repas à travers la référence aliment) : elle apparaît dans `APP_STATE` d'une nouvelle session, `VARIABLES` la liste avec sa formule sous les noms actuels, `READING` rend sa valeur maintenant et la veille ; un repas sans aliment la fait échouer en le disant.
- Faire écrire à l'IA une formule avec un nom inconnu, ou deux variables qui se lisent l'une l'autre : refusées, le chemin de la boucle nommé.
- Dans une zone, « + » puis Variable : créer « objectif » (constante 2100 kcal), puis « kcal » (formule `mange`, terme lecture de Repas, formule par entrée, somme, du jour-même · début au moment même) et « reste » (`objectif - kcal`) : chaque groupe montre ses variables sur une ligne avec la valeur actuelle ; ajouter un repas met la ligne à jour ; toucher une ligne rouvre la variable, un nom inconnu tapé dans la formule s'affiche en erreur sous la saisie ; recréer l'écran en cours d'édition garde le brouillon.
- Supprimer « kcal » : « reste » affiche « pas de valeur — variable supprimée ».
- Après la migration 49 : le réglage de validation « Modifications des variables » est coché si « Modifications des zones » l'était ; coché, une variable que crée l'IA attend ta validation.
- Après la migration 48 : les anciennes sessions s'ouvrent ; le bouton de chat d'une carte d'automation ouvre une conversation dont la saisie porte son message de départ, pointeurs compris, à envoyer ou modifier.
- Après la migration 47 : l'app démarre, une sauvegarde exportée contient `variables` et se réimporte.

## Données structurées

- Une table neuve, vide : « Importer un fichier » est le bouton principal ; après un import, il redevient secondaire et reste là.
- Créer une table « Aliments » avec deux champs (kcal_100g, catégorie) : « + » ouvre une fiche vide, rien n'apparaît avant « Enregistrer » ; une fiche nommée « pomme » alors que « Pomme » existe est refusée en nommant l'existante.
- Le tableau montre le nom et les deux champs ; toucher un en-tête trie, retoucher inverse. Toucher une ligne ouvre la fiche ; glisser mène aux voisines dans l'ordre et le filtre du tableau ; la ligne repliée de l'en-tête dit la position (« 3 / 12 »).
- Filtrer « catégorie = fruit », ouvrir une fiche, lui changer sa catégorie : elle reste affichée jusqu'à ce qu'on la quitte. Recréer l'écran : filtre, tri et fiche ouverte restent ; quitter l'outil et revenir : tout repart à zéro.
- La tuile compte les fiches.
- « Importer un fichier » sur une table d'aliments avec un CSV (nom ; kcal en décimale virgule ; une date jour/mois ; une catégorie à quelques valeurs) : chaque colonne est proposée avec son type, son écriture et un exemple lu ; un fichier dont les dates ne tranchent pas jour/mois le signale ; importer crée les champs dans l'ordre, met à jour les fiches dont le nom existe, et le compte-rendu nomme les lignes refusées. Réimporter le même fichier : tout est mis à jour, rien n'est dupliqué. Un import avec une colonne nouvelle se termine (les appels imbriqués dans la transaction ne bloquent pas), et l'écran relit la table une fois fini.

## Objectif

- Notifications : toucher celle d'une tentative à valider, d'une invitation à remplir ou d'un message ouvre l'app sur la zone puis l'outil, à sa plus ancienne entrée qui attend ; un outil supprimé depuis ouvre l'app telle quelle.
- Icône des notifications : dans la barre d'état et le volet, celle de l'outil qui envoie (un message, un élément de liste à échéance, une tentative à valider, un questionnaire à remplir), en silhouette nette, avec le thème par défaut comme avec le rétro.
- Toucher la tuile d'un Questionnaire marqué d'un point ouvre le formulaire de l'invitation à remplir la plus ancienne.
- Attente : un Questionnaire avec une invitation à remplir montre un point à côté de son nom, et sa zone aussi à l'accueil ; la remplir fait disparaître le point sans quitter l'écran.
- Critères : un critère lu « Repas › kcal, somme ≤ 2100 » se juge sur la journée de la tentative sans toucher à la période (« Aucune choisie : celle de la tentative ») ; « ≤ » une variable à droite se juge aussi ; un critère saisi (Durée « ≥ 7 h », Choix « = bonne ») se remplit dans la tentative et se juge ; retirer la valeur saisie (bouton du groupe) ramène un critère lu. La ligne d'un critère dit sa valeur face à ce qu'elle compare, avec une jauge pour un nombre ou une durée.
- Objectif, Questionnaire et Messages : la récurrence de la config s'affiche en une ligne de résumé sous le titre propre à l'outil, s'édite par le bouton « Configurer la récurrence » et son dialogue ; « Aucune » la retire. Le résumé est le même que sur la carte et l'éditeur d'une automation.
- Créer un objectif ponctuel sans échéance avec trois critères (une variable « kcal » ≤ 2100, un champ « poids, dernière » ≤ 80, une saisie oui/non indispensable), au moins 2 : sa tentative s'ouvre dès l'enregistrement ; chaque critère montre sa valeur face à sa condition, la saisie oui/non se coche sur place ; « Valider » avant d'avoir saisi est refusé en nommant le critère ; après, la tentative est réussie ou échouée, « par vous ».
- Une tentative validée : la modifier par l'IA est refusé ; « Rouvrir » la remet à valider et garde la date de réouverture.
- Renommer un critère saisi : sa valeur reste ; le supprimer puis le recréer : la confirmation de la config compte la valeur retirée.
- Un objectif récurrent quotidien : une tentative par jour ; à la fin de la période, une notification et « À valider (1) » ; sans validation, expirée après le délai. Arrêté, plus aucune ne s'ouvre.
- Faire valider par l'IA : « par l'IA ».

## Questionnaire

- Un questionnaire de trois questions (échelle, texte, oui/non) : « Remplir maintenant » pose une question par écran ; quitter en route n'écrit rien ; terminer crée l'entrée remplie, datée de maintenant.
- Planifié chaque jour à une heure passée : au tick, une entrée « à remplir » datée de cette heure et une notification ; répondre à la première question puis quitter : la réponse reste, et « Remplir » reprend à la deuxième. Une absence de plusieurs jours : autant d'entrées, « Tout ignorer » les passe en ignorées.
- « Avec l'IA » sur une entrée à remplir : le chat s'ouvre, sa saisie porte le message et les pointeurs vers l'outil et l'entrée ; envoyé, l'IA pose les questions, écrit les réponses et marque l'entrée remplie.
- Toucher une entrée de l'historique : ses réponses en entier, modifiables.

## Graphique

- Créer un Graphique à la main : « Une vue », une couche « Une grille de pas », pas « Jour », période « Il y a 29 jours, début » → « Le moment même » ; une colonne `kcal` lisant une variable ; marque « Barres », x : « Pas », y : `kcal`. Enregistrer : trente barres, les jours sans valeur en trou hachuré ; toucher un trou : sa cause et les entrées à corriger, dont chacune ouvre son outil.
- Mettre le poids du même côté que les kcal : l'enregistrement est refusé et propose l'autre côté.
- Dans le formulaire, le choix d'une colonne d'un canal ne propose que celles de sa couche (après ses transformations) ; les colonnes d'un `fold` proposent celles d'avant lui.
- Ajouter une entrée au suivi que lit le graphique, revenir : le graphique l'a prise. Recréer l'écran : le graphique se redessine à l'identique.
- Thème rétro : les graduations en pointillé d'un pixel sur deux, les axes pleins.

