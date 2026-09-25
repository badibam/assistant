# Recette sur l'appareil

Ce que la suite de tests ne voit pas : les écrans, les migrations sur une vraie base, l'IA en vrai. Une ligne dit quoi faire et quoi voir ; le pourquoi est dans le commit. Une vérification faite sort de la liste. Un commit qui demande une vérification ajoute une ligne sous l'écran qu'il touche.

## Mise à jour et démarrage

- Installer la mise à jour par-dessus la version du téléphone (migrations jusqu'à la base 40) : l'app démarre, l'historique des conversations est intact, chaque zone garde ses outils, ses groupes et son icône, les outils et leurs icônes s'ouvrent, une config portant un champ DATE ou DATETIME s'enregistre.
- Réglages après la mise à jour : format (fuseau, début de semaine, 24 h) inchangé et enregistrable, validation avec ses quatre choix, limites IA à 10, 20, 15 000 et 100 000.
- Écran des journaux : il s'ouvre, et filtré sur « Error » il montre aussi les erreurs anciennes. Y chercher des lignes `MIGRATION` et `No JSON form`.
- Volume du journal : compter les lignes par niveau sur deux minutes d'usage normal, pour voir ce que produit encore le DEBUG.

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
- Rotation pendant la composition : deux blocs, un pointeur ouvert sur le second avec zone, outil, contexte et période choisis. Après la rotation, la fenêtre et les choix sont là, et le pointeur arrive dans le second bloc.
- Prompt L1 modifié : rejouer `docs/ai-prompt-replay.md`.
- Mode avion puis message CHAT : échec immédiat avec un message réseau, rien ne part (les NOTES disaient que l'appel partait quand même).
- Interrompre pendant que l'IA réfléchit, réseau coupé ou non : le composeur revient tout de suite, avec « Round IA interrompu » et la note de coût inconnu ; le coût de la session s'affiche en « ≥ ».

## Automations

- Rattrapage réel : automation programmée, app fermée plusieurs jours. Voir quelle session est reprise et ce que montre l'écran d'historique ; une donnée de la date prévue est écrite avec un timestamp ISO explicite.
- Créer une automation planifiée : sa prochaine exécution s'affiche sur sa carte. La fenêtre de rattrapage se saisit dans l'éditeur.
- Seuil de données bas : le refus apparaît dans l'historique d'exécution, et l'IA resserre sa requête.
- Lancer une automation, couper le réseau pendant l'appel, puis Stop : la session se ferme tout de suite. Sans Stop, elle attend le réseau si l'appel n'était pas parti, ou s'arrête avec « Requête envoyée, réponse perdue » et un coût en « ≥ » sur sa carte.

## Outils et saisie

- Créer et modifier une entrée de chaque type : tracking de chaque sorte, note insérée à une position, journal, occurrence Messages, et une entrée avec champs personnalisés.
- Une plage de champ personnalisé dont le début dépasse la fin est refusée à l'écran.
- Échelle : un champ de 1 à 10 affiche et enregistre « 7 », pas « 7.0 » ; une échelle de 0 à 5 par 0.5 s'arrête sur chaque demi-point et affiche « 3,5 » ; l'éditeur refuse une échelle de 1 à 10 par 2. Le curseur de note d'un tracking d'échelle se comporte comme avant.
- Sélecteurs de date et d'heure normaux dans l'entrée de journal, l'entrée de tracking, les champs DATE et DATETIME, le sélecteur de période. Un DATETIME déjà enregistré s'affiche comme une date.
- Journal, « Annuler » en modification : l'écran revient au texte stocké, et une entrée créée, rouverte puis annulée reste en place.
- Messages : créer puis modifier une récurrence, voir les messages partir ; mettre l'outil en pause, voir les envois s'arrêter.
- Messages, récurrence illisible (écrire en base un `schedule` portant `"enabled": true`) : avertissement sur l'écran de l'outil, occurrences déjà prévues gardées et envoyées, erreur dans la config, enregistrement refusé sans rien écraser.
- Zone, écran généré : créer puis modifier (nom, description, icône, groupe, groupes d'outils ajoutés, réordonnés, supprimés) ; un nom vide est refusé avec le message du service ; effacer la description et enregistrer, elle disparaît.
- Écran de config généré, pour chaque type d'outil, en création puis en modification : tous les réglages s'affichent et s'enregistrent (icône, groupe, zone, champs personnalisés avec chaque type et ses réglages, options de choix avec couleur, planification de Messages et ses durées) ; un nom vide est refusé avec le message du service ; la rotation garde la saisie.
- Tracking : passer de numérique à compteur garde les raccourcis et leur valeur, sans unité ; changer les bornes d'une échelle ou le type d'un champ personnalisé demande confirmation avec les nombres de valeurs retirées et d'entrées supprimées ; d'occurrence à numérique avec des entrées, la valeur à leur donner est demandée.

## Réglages et affichage

- Écran des limites IA : les quatre curseurs s'enregistrent et se retrouvent à la réouverture. Les curseurs du format vont jusqu'à 24 heures et 30 jours, et l'écran s'enregistre.
- Fuseau de l'app différent de celui du téléphone : l'historique range chaque entrée dans le jour affiché sur elle, le sélecteur de période et l'éditeur de planning montrent l'heure de l'app, « aujourd'hui » et « hier » du journal suivent. Un début de semaine changé est suivi par l'historique et les sélecteurs de période.
- Icônes : couleur du thème partout, et le sélecteur s'ouvre, cherche et parcourt une catégorie sans lenteur.
- Touche Retour : outil → zone → accueil → confirmation avant de fermer ; annule sur la création de zone, les réglages Claude/OpenAI et une entrée de journal en modification ; ferme les fenêtres sans effet de bord.
