# Recette sur l'appareil

Ce que la suite de tests ne voit pas : les écrans, les migrations sur une vraie base, l'IA en vrai. Une ligne dit quoi faire et quoi voir ; le pourquoi est dans le commit. Une vérification faite sort de la liste. Un commit qui demande une vérification ajoute une ligne sous l'écran qu'il touche.

## Mise à jour et démarrage

- Installer la mise à jour par-dessus la version du téléphone (migrations jusqu'à la base 44) : l'app démarre, l'historique des conversations est intact, chaque zone garde ses outils, ses groupes et son icône, les outils et leurs icônes s'ouvrent, une config portant un champ DATE ou DATETIME s'enregistre.
- Réglages après la mise à jour : format (fuseau, début de semaine, 24 h) inchangé et enregistrable, validation avec ses quatre choix, limites IA à 10, 20, 15 000 et 100 000.
- Écran des journaux : il s'ouvre, et filtré sur « Error » il montre aussi les erreurs anciennes. Y chercher des lignes `MIGRATION` et `No JSON form`.
- Volume du journal : compter les lignes par niveau sur deux minutes d'usage normal, pour voir ce que produit encore le DEBUG.
- Après la migration 43 : un suivi numérique qui avait une unité dans les réglages de sa valeur la retrouve en tête de ses unités, et ses anciennes entrées l'affichent toujours ; un compteur garde son unité (« 3 verres »).

## Chat IA

- Session CHAT avec DeepSeek : elle passe, et son coût s'affiche sans « ≥ ».
- Après la mise à jour : une ancienne session affiche ses tokens et un coût en « ≥ » ; une nouvelle, un coût exact, le même dans la fiche de coût et sur la carte d'historique d'automation.
- Démarrer l'app en mode avion après l'avoir déjà utilisée en ligne : une session affiche quand même son coût (prix gardés sur le téléphone).
- Importer une sauvegarde faite avant la mise à jour : les conversations reviennent, leur coût en « ≥ ».
- « Quelle heure est-il ? », puis créer une entrée datée d'hier : la date est juste. Les résultats de données reçus par l'IA sont en ISO 8601.
- Après `CREATE_ZONE` puis `CREATE_TOOL`, l'IA enchaîne sans redemander la liste des zones ni des outils.
- Une IA qui enchaîne plus de 10 appels seule s'arrête et rend la main avec un message.
- Faire écrire par l'IA une valeur hors d'une échelle ou hors des options d'un choix : le refus lui revient.
- Faire créer et modifier des entrées de tracking par l'IA : `raw` est juste.
- Faire poser une récurrence sur un outil Messages par l'IA, et retirer la description d'une zone.
- Seuil de données à 5 000 : un pointeur sur un gros outil fait apparaître « Données volumineuses » ; « Envoyer » et « Refuser » font répondre l'IA avec ou sans les données. Même chose quand l'IA demande elle-même les données.
- Fermer l'app de force pendant une attente (validation, question, données volumineuses), la rouvrir : les boutons reviennent.
- Rotation pendant la composition : deux blocs, un pointeur ouvert sur le second avec un outil, ses cases et une période choisis. Après la rotation, la fenêtre et les choix sont là, et le pointeur arrive dans le second bloc.
- Sélecteur de pointeur dans le chat : descendre App › Zone › Outil puis remonter par le fil d'Ariane, ce qui n'a plus de sens au niveau atteint est effacé ; sur un outil, période, un filtre par valeur (nombre, choix, date) et deux champs cochés — la phrase du bas dit ce qui part, et l'IA reçoit exactement ça. Un filtre rempli puis la fenêtre validée sans « + » : il est gardé. Aucune case cochée avec un filtre : l'IA reçoit la requête sans les entrées et peut la lancer.
- Même sélecteur dans le message de départ d'une automation : période et filtre de date en valeurs relatives, recalculées à l'exécution suivante.
- Après la migration 44 : une ancienne conversation qui contenait des pointeurs les affiche encore, et les renvoyer dans une nouvelle session joint les mêmes données.
- Demander à l'IA une question à plusieurs volets (un choix, un texte, une date) : chaque champ se saisit avec son composant, Confirmer reste grisé tant qu'un champ obligatoire est vide, et la réponse réapparaît dans le fil champ par champ, la date affichée comme telle. Une confirmation sans champ : Confirmer et Annuler seuls. Tourner l'écran en cours de saisie : les valeurs restent.
- Validation active, faire créer deux entrées par l'IA puis en modifier une : la carte de validation montre chaque entrée champ par champ (date lisible, échelle en jauge, choix par son libellé), et pour la modification seulement les champs changés.
- Prompt L1 modifié : rejouer `docs/ai-prompt-replay.md`.
- Mode avion puis message CHAT : échec immédiat avec un message réseau, rien ne part (les NOTES disaient que l'appel partait quand même).
- Interrompre pendant que l'IA réfléchit, réseau coupé ou non : le composeur revient tout de suite, avec « Round IA interrompu » et la note de coût inconnu ; le coût de la session s'affiche en « ≥ ».
- Une question de l'IA avec un oui/non et une échelle obligatoires : rien n'est choisi au départ, les deux libellés portent un astérisque, Confirmer s'active une fois les deux répondus ; « non » se répond d'un toucher, le minimum d'une échelle vide aussi.
- Une question de l'IA : « Ajouter une précision » ouvre un texte, envoyé avec la réponse, et l'IA en tient compte. « Répondre par un message » débloque le composeur, le formulaire reste répondable ; envoyer un message avec un pointeur ferme le formulaire, « Module remplacé par un message » apparaît et l'IA répond au message. Tourner l'écran entre-temps garde la précision.
- Rejeu ciblé : « crée un suivi de poids, puis ajoute une pesée » — la config passe par `units`, l'entrée porte `data.unit`.

## Automations

- Rattrapage après la mise à jour : une automation programmée « sans limite » le reste, une fenêtre de 3 heures se relit « jusqu'à un délai » de 3 h. Dans l'éditeur : sans choix, l'enregistrement est refusé ; « jusqu'à un délai » demande la durée ; retirer la planification puis enregistrer retire aussi le rattrapage.
- Rattrapage réel : automation programmée, app fermée plusieurs jours. Voir quelle session est reprise et ce que montre l'écran d'historique ; une donnée de la date prévue est écrite avec un timestamp ISO explicite.
- Créer une automation planifiée : sa prochaine exécution s'affiche sur sa carte. La fenêtre de rattrapage se saisit dans l'éditeur.
- Seuil de données bas : le refus apparaît dans l'historique d'exécution, et l'IA resserre sa requête.
- Lancer une automation, couper le réseau pendant l'appel, puis Stop : la session se ferme tout de suite. Sans Stop, elle attend le réseau si l'appel n'était pas parti, ou s'arrête avec « Requête envoyée, réponse perdue » et un coût en « ≥ » sur sa carte.
- La tuile d'une automation dans sa zone : son nom lisible sur une ligne, et à côté deux petits boutons « On » / « Off », toucher le bouton choisi ne change rien, basculer ne fait pas clignoter l'écran ; les jours et les mois d'une planification sont des cases à cocher.

## Outils et saisie

- Créer et modifier une entrée de chaque type : tracking de chaque sorte, note insérée à une position, journal, occurrence Messages, et une entrée avec champs personnalisés.
- Une plage de champ personnalisé dont le début dépasse la fin est refusée à l'écran.
- Échelle : un champ de 1 à 10 affiche et enregistre « 7 », pas « 7.0 » ; une échelle de 0 à 5 par 0.5 s'arrête sur chaque demi-point et affiche « 3,5 » ; l'éditeur refuse une échelle de 1 à 10 par 2. Le curseur de note d'un tracking d'échelle se comporte comme avant.
- Sélecteurs de date et d'heure normaux dans l'entrée de journal, l'entrée de tracking, les champs DATE et DATETIME, le sélecteur de période. Un DATETIME déjà enregistré s'affiche comme une date.
- Journal, « Annuler » en modification : l'écran revient au texte stocké, et une entrée créée, rouverte puis annulée reste en place.
- Messages : créer puis modifier une récurrence, voir les messages partir ; mettre l'outil en pause, voir les envois s'arrêter.
- Lectures filtrées par les écrans : l'historique d'un suivi par période (jour, semaine, mois) montre les mêmes entrées qu'avant, et l'écran Messages range chaque message selon son état.
- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : avertissement sur l'écran de l'outil, occurrences déjà prévues gardées et envoyées, erreur dans la config, enregistrement refusé sans rien écraser.
- Zone, écran généré : créer puis modifier (nom, description, icône, groupe, groupes d'outils ajoutés, réordonnés, supprimés) ; un nom vide est refusé avec le message du service ; effacer la description et enregistrer, elle disparaît.
- Écran de config généré, pour chaque type d'outil, en création puis en modification : tous les réglages s'affichent et s'enregistrent (icône, groupe, zone, champs personnalisés avec chaque type et ses réglages, options de choix avec couleur, planification de Messages et ses durées) ; un nom vide est refusé avec le message du service ; la rotation garde la saisie.
- Tracking : passer de numérique à compteur garde les raccourcis et leur valeur, sans unité ; changer les bornes d'une échelle ou le type d'un champ personnalisé demande confirmation avec les nombres de valeurs retirées et d'entrées supprimées ; d'occurrence à numérique avec des entrées, la valeur à leur donner est demandée.
- Suivi numérique : taper « lb » dans une saisie l'enregistre en « lb », et la saisie suivante a « lb » dans sa liste d'unités (la première de la liste reste préremplie) ; même chose en cochant « ajouter aux raccourcis », le raccourci créé et « lb » gardé dans les unités ; un suivi sans unité reçoit la première depuis une saisie. La config d'un suivi numérique n'offre plus d'unité dans le bloc de la valeur ; celle d'un compteur et d'un champ nombre de l'utilisateur, si.
- Suivi oui/non ou échelle : la fenêtre de saisie part vide. Un champ oui/non, échelle ou plage facultatif laissé vide s'enregistre sans valeur, et l'historique n'affiche rien pour lui.
- Réglages nombre (bornes et pas d'une échelle, d'un nombre) : « 0 » s'affiche « 0 », un champ se vide entièrement, « 0.5 » se tape point compris.
- Config d'un outil : ses champs personnalisés viennent après ses réglages propres (valeur principale, unités, raccourcis).
- Glisser-déposer par la poignée : les notes (la position tient après rechargement, pas de retour visible à l'ancien ordre au lâcher), les champs personnalisés d'une config et les options d'un de ces champs (la liste imbriquée bouge seule, l'autre ne suit pas), un classement ; la page défile quand on tient un élément près du bord haut ou bas ; la saisie d'un champ de la config garde le focus pendant qu'on tape ; TalkBack propose « Monter » et « Descendre » sur la poignée.

## Réglages et affichage

- Icônes à la place des glyphes : les boutons d'action en icône (enregistrer, supprimer en corbeille, flèches de période, en-têtes) ont leur couleur de bouton et une taille lisible ; dans le chat, les enrichissements du composeur et d'un message, le résultat d'une commande et le compte à rebours de fermeture ; les boutons horaire et déclencheurs d'une automation ; le fournisseur actif ; l'avertissement d'une validation ; le chemin des sélecteurs de copie et de pointeur.
- Écrans de réglages générés (format, limites IA, validation, écran d'accueil) : chacun s'ouvre sur les valeurs stockées, s'enregistre et se retrouve à la réouverture ; fuseau et langue à « Aucun » suivent le téléphone ; un changement du fuseau ou du début de journée est suivi aussitôt par l'historique ; les groupes de zones ajoutés apparaissent sur l'accueil et dans l'écran de zone. L'écran de validation, qui était un bouchon, fonctionne.
- Fournisseurs d'IA, écran généré, pour Claude, OpenAI et DeepSeek : la clé s'affiche masquée et l'œil la montre ; les modèles se listent à l'ouverture pour une clé enregistrée, et après saisie d'une nouvelle clé ; effort obligatoire pour DeepSeek ; enregistrer, rouvrir, retrouver les valeurs ; une session de chat part avec le modèle choisi.
- Fuseau de l'app différent de celui du téléphone : l'historique range chaque entrée dans le jour affiché sur elle, le sélecteur de période et l'éditeur de planning montrent l'heure de l'app, « aujourd'hui » et « hier » du journal suivent. Un début de semaine changé est suivi par l'historique et les sélecteurs de période.
- Icônes : couleur du thème partout, et le sélecteur s'ouvre, cherche et parcourt une catégorie sans lenteur.
- Touche Retour : outil → zone → accueil → confirmation avant de fermer ; annule sur la création de zone, les réglages Claude/OpenAI et une entrée de journal en modification ; ferme les fenêtres sans effet de bord.
