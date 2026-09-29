# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre. Une ligne par item : ce qu'il faut faire, et ce qui le débloque.

## En cours

- Les outils manquants sont posés (sélection du cœur, RÉFÉRENCE, lecture du cœur, variables, Données structurées et import, Objectif, Questionnaire) ; rien n'a tourné sur un téléphone : passer `docs/design/device-checks.md`, puis élaguer de `docs/design/missing-tools.md` ce qui est codé, ses garanties devenant des tests (le Graphique y est encore en conception).
- Finir les briques : le plan d'action est `docs/design/bricks-plan.md` (neuf étapes, de la Planification rangée au cœur jusqu'à l'attente sur les tuiles), le modèle `docs/BRICKS.md`.
- La grille des outils d'une zone, conçue dans `docs/design/grid-layout.md` : les tuiles placées selon leur mode (aujourd'hui toutes en LINE), et leur déplacement aux flèches — après les briques.
- L'app en portrait seul (`android:screenOrientation="portrait"`) : retirer les branches paysage (`DefaultTheme.kt`, `NotesScreen.kt`), et dans `docs/design/device-checks.md` remplacer « tourner l'écran » par un autre déclencheur de recréation (« Ne pas conserver les activités », thème sombre) — la conservation d'état reste une règle. Un grand écran ignorera le verrou une fois la cible en API 36 : même affichage, rien de propre au paysage. Après la spec de la grille.
- Conformité F-Droid (`docs/design/fdroid-compliance.md`) : reste la fiche fastlane et la grille d'anti-features, au moment de la première release candidate — pas avant, la codebase bouge.

## En attente d'un déclencheur

- Choisir une entrée précise comme cible du pointeur dans son sélecteur (conçu dans `docs/design/pointer.md` : étiquette par type d'outil, liste, relecture de l'entrée) — si le besoin apparaît : un pointeur peut désigner une entrée (le Questionnaire en pose un), mais seule l'app le fait. La cible APP attend que l'IA sache lire les réglages de l'app.
- Les exécutions d'une automation décrites comme des entrées (champs déclarés, schéma généré, dates en ISO par ce schéma) — le jour où l'IA les lit ; ce ne sont pas des réglages.
- Une sauvegarde emporte-t-elle la clé d'API d'un fournisseur (réglage secret) ? — à trancher avant d'ouvrir l'export à un usage partagé.
- Défilement saccadé à la réouverture d'une longue session CHAT — à mesurer (recompositions de la liste, défilements automatiques successifs).
- Seuil de taille des données par automation — quand une automation légitime montre un `DATA_REFUSED` dans son historique d'exécution ; la valeur globale deviendra la valeur par défaut.
- Marquer les lignes que les migrations 13→14 et 14→15 n'ont pas su transformer, et le dire une fois au démarrage (jamais les supprimer) — si des lignes `MIGRATION` apparaissent en « Error » dans l'écran des journaux.
- Validation désactivée par défaut, que l'IA contourne donc sans rien demander (`docs/design/architecture-audit-debt.md`) — décision reportée le 2026-09-22.
- D'autres sources de l'attente (`docs/BRICKS.md`) : une automation qui attend une validation, un message de l'IA arrivé dans une session pendant qu'on était ailleurs — à concevoir.
- Joindre un fichier au message de départ d'une automation : son composeur n'a pas toujours de session, à laquelle un fichier joint appartient.
- Joindre une image à un message : un stockage de fichiers (le texte d'un fichier joint vit en base avec son message), et ce que chaque modèle d'IA accepte.
- Outils jamais livrés : le Graphique, en conception dans `docs/design/missing-tools.md`, et l'Alerte, laissée pour plus tard (outil ou event du cœur, sans doute une lecture et une condition).
- L'automation directe : des commandes que l'app exécute elle-même, sans IA. Premier usage connu, le relevé (une écriture qui accepte une valeur lue à l'exécution : un Suivi qui garde un chiffre de Calcul, `docs/design/missing-tools.md`) ; à concevoir avec les events du cœur et l'Alerte, qu'elle croise (`NOTES.md`, « Events et badges »), et à trancher : la date de l'entrée écrite, le doublon d'une exécution relancée.
- L'IA crée et modifie des automations — aujourd'hui seul l'utilisateur le peut ; l'historique des exécutions est à trancher avec (`NOTES.md`, « Automations et planification »). À faire comme pour les variables (`docs/design/missing-tools.md`) : elles apparaissent sous leur zone dans l'instantané `APP_STATE` et dans une commande qui les liste à la demande (id, nom, description), avec des opérations d'un service dédié.
- Le Suivi (tableau de l'historique) et Messages (cartes des envois) n'affichent pas les champs personnalisés de leurs entrées, qu'on ne voit qu'en modification — à trancher : où, et dans quelle disposition (`CustomFieldsDisplay`, `docs/DATA.md`).
- Les incarnations (`docs/design/incarnations.md`, identités de personae) — au premier export publié par personae.
- Supprimer un fournisseur d'IA ne regarde ni les automations ni les sessions qui le nomment (`AIProviderConfigService.deleteProviderConfig`) : refuser tant qu'une automation l'utilise, en la nommant ; une session passée reste lisible mais ne peut plus continuer, message à l'appui — avec les identités incarnées, qui demandent le même contrôle, écrit une fois pour les deux.
- Une valeur comparée n'a pas de type à elle (constante d'un terme, valeur d'un filtre) : elle prend celui du champ en face, et se relit sans rien dire dans le nouveau type si ce champ en change (un nombre devenu durée) — quand une modification de config change le type d'un champ que des conditions ou des filtres enregistrés visent.
- Streaming des réponses IA (Claude et OpenAI), avec le TCP keep-alive — quand des messages « requête envoyée, réponse perdue » s'accumulent dans les sessions : le réseau coupe les connexions restées silencieuses pendant la génération.

## Recette sur l'appareil

- `docs/design/device-checks.md`, classée par écran.
