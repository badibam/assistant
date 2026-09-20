# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre.

## Chantier en cours

- **Conformité F-Droid** — spec dans `docs/design/fdroid-compliance.md`. Premier bloquant : l'auto-updater, qui télécharge des APK depuis GitHub, est à sortir de la variante F-Droid.

## Dette constatée

Quatre points dans `docs/design/architecture-audit-debt.md`, tous vérifiés, aucun mécanique : deux sont transversaux (chaînes JSON aux frontières, conventions de nommage des params de service), deux sont des décisions à prendre plutôt que du code à écrire (format du prompt L1, validation désactivée par défaut).


## Divers

- Limite d'aller-retours d'une automation : décider si le compteur doit arrêter toutes les boucles ou seulement celle qui produit quelque chose. `maxAutonomousRoundtrips` n'est lu que sur `ActionsExecuted` ; les boucles d'erreur de format, d'échec d'action, de relance, et le retour de requêtes de données l'ignorent — quatre chemins, coupés seulement par le chien de garde au bout de dix minutes de temps actif. Mesuré par `AIStateMachineRoundtripLimitTest` et `AIStateMachineResponseRoutingTest`.
- Rejeter une complétion laisse `awaitingCompletionConfirmation` à vrai. Une automation renvoyée au travail qui répond `completed=true` sans aucune commande est lue comme une deuxième revendication et se termine. Toute réponse portant une commande remet le drapeau à zéro d'abord, donc le cas est étroit. Décider si un rejet doit annuler la revendication. Mesuré par `AIStateMachineUserInteractionTest`.
- `SessionActivationRequested` ne teste que la phase, pas `isSlotAvailable()`. Un CHAT activé à qui personne n'a encore parlé est à IDLE avec un identifiant de session, donc une seconde activation l'écrase. Mesuré par `AIStateMachineLifecycleTest`.
- Étendre la suite de tests, dans cet ordre : `AISessionScheduler` ; `ScheduleCalculator` (ce qui vide la vérification de rattrapage ci-dessous) ; `FieldConfigComparator` et `MigrationStrategy` (la migration rename-aware) ; `DateUtils` et `DateTimeConverter` ; un corpus de vraies réponses IA figées pour `JsonNormalizer` → `CommandTransformer` → `ActionValidator`. Les migrations Room sont le seul cas qui justifie de l'instrumenté (`MigrationTestHelper`) : 14 migrations enchaînées, aucune testée, et elles tournent une fois sur des données réelles.
- Journal : « Annuler » en modification repasse en consultation sans recharger l'entrée, qui affiche alors la saisie abandonnée jusqu'à la sortie de l'écran (`JournalEntryScreen.kt`, `handleCancel`). Rien n'est enregistré.
- La touche Retour du téléphone ferme l'app depuis l'écran d'un outil, au lieu de revenir à la zone.
- Le compositeur de message perd sa fenêtre d'enrichissement ouverte à la rotation (le texte, lui, survit) : ses blocs reçoivent de nouveaux identifiants à chaque recréation, et l'état du sélecteur de portée n'a pas de forme sauvegardable.
- Compte des tokens avant envoi, via `/v1/messages/count_tokens` chez Claude — à voir pour les autres providers. `TokenCalculator` (171 lignes) et son bloc de strings existent déjà mais ne sont appelés de nulle part : soit ce chantier les reprend, soit ils partent.
- Outils prévus par la vision produit mais jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste.

## À vérifier sur l'appareil

- Rejouer les exemples du prompt L1 dans une session CHAT réelle : la règle est posée dans `docs/AI.md`, mais les exemples corrigés (périodes ISO, pagination par page, grammaire des champs) n'ont été vérifiés que sur lecture du code.
- Exécutions d'automation manquées : vérifier sur l'appareil le passage en base 22→23, la saisie de la fenêtre dans l'éditeur, et un rattrapage réel (automation programmée, app fermée plusieurs jours).
- Champs personnalisés : vérifier la création d'un champ depuis l'écran de configuration, maintenant que le nom technique est attribué par le service et non plus envoyé par le formulaire.
