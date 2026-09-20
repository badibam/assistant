# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre.

## Chantier en cours

- **Conformité F-Droid** — spec dans `docs/design/fdroid-compliance.md`. Premier bloquant : l'auto-updater, qui télécharge des APK depuis GitHub, est à sortir de la variante F-Droid.

## Dette constatée

Quatre points dans `docs/design/architecture-audit-debt.md`, tous vérifiés, aucun mécanique : deux sont transversaux (chaînes JSON aux frontières, conventions de nommage des params de service), deux sont des décisions à prendre plutôt que du code à écrire (format du prompt L1, validation désactivée par défaut).


- **Clés en snake_case** — la règle est posée dans `docs/reference.md` et gardée par `./scripts/check_key_case.py` ; 112 clés restent à renommer, listées dans `scripts/key_case_baseline.txt`. Les quatre tables IA sont faites. Reste : le contenu des JSON stockés (`tokens_json` et `cost_json` portent encore `totalUncachedInputTokens`, `modelId`, `inputCost`…, donc une transformation de données), les noms de réglages dans `app_settings_categories`, le vocabulaire des définitions de champs (`minValue`, `trueLabel`…), et en dernier l'enveloppe de réponse du modèle (`preText`, `dataCommands`, `actionCommands`…), qui ne se vérifie qu'en session réelle. Brancher aussi le contrôle sur `./run` une fois la tâche de tests stabilisée.

## Divers

- Limite d'aller-retours d'une automation : décider si le compteur doit arrêter toutes les boucles ou seulement celle qui produit quelque chose. `maxAutonomousRoundtrips` n'est lu que sur `ActionsExecuted` ; les boucles d'erreur de format, d'échec d'action, de relance, et le retour de requêtes de données l'ignorent — quatre chemins, coupés seulement par le chien de garde au bout de dix minutes de temps actif. Mesuré par `AIStateMachineRoundtripLimitTest` et `AIStateMachineResponseRoutingTest`.
- Rejeter une complétion laisse `awaitingCompletionConfirmation` à vrai. Une automation renvoyée au travail qui répond `completed=true` sans aucune commande est lue comme une deuxième revendication et se termine. Toute réponse portant une commande remet le drapeau à zéro d'abord, donc le cas est étroit. Décider si un rejet doit annuler la revendication. Mesuré par `AIStateMachineUserInteractionTest`.
- `SessionActivationRequested` ne teste que la phase, pas `isSlotAvailable()`. Un CHAT activé à qui personne n'a encore parlé est à IDLE avec un identifiant de session, donc une seconde activation l'écrase. Mesuré par `AIStateMachineLifecycleTest`.
- Le rattrapage du temps hors réseau ne s'applique jamais. `AIState` documente `lastNetworkAvailableTime` comme servant à retirer le temps hors réseau du délai global, et `calculateActiveTime` le retire bien — mais seulement quand la phase est `WAITING_NETWORK_RETRY`, sur laquelle `shouldTimeout` est déjà ressorti quelques lignes plus haut. La branche est inatteignable, le champ n'est lu que là, et le budget de dix minutes est du temps de montre depuis le début de la session. Une automation huit minutes hors réseau est donc arrêtée peu après son retour, avec une minute de travail effectif. Les commentaires disent que le retrait devrait avoir lieu : décider, puis rendre la branche atteignable ou retirer le champ. Mesuré par `SessionSlotPolicyTest`.
- Une programmation au 29 février s'arrête après l'année bissextile où elle a été posée. `calculateYearlyRecurrent` cherche l'année en cours, passe le février trop court, puis son repli ne regarde que l'année suivante, la trouve courte aussi et abandonne — au lieu de continuer jusqu'à la bissextile d'après. Mesuré par `ScheduleCalculatorTest`.
- La première heure du lendemain est choisie en triant les chaînes, pas les heures : sans zéro initial, `"14:00"` passe avant `"9:00"` et le report tombe l'après-midi. L'éditeur écrit en HH:mm, donc rien dans l'app ne produit ça, mais rien ne le refuse non plus — une programmation venue de l'IA ou d'une sauvegarde restaurée serait lue ainsi. Décider entre normaliser à la lecture et refuser à la validation. Mesuré par `ScheduleCalculatorTest`.
- Étendre la suite de tests, dans cet ordre : `FieldConfigComparator` et `MigrationStrategy` (la migration rename-aware) ; `DateUtils` et `DateTimeConverter` ; un corpus de vraies réponses IA figées pour `JsonNormalizer` → `CommandTransformer` → `ActionValidator`. Les migrations Room sont le seul cas qui justifie de l'instrumenté (`MigrationTestHelper`) : 17 migrations enchaînées, aucune testée, et elles tournent une fois sur des données réelles.
- Journal : « Annuler » en modification repasse en consultation sans recharger l'entrée, qui affiche alors la saisie abandonnée jusqu'à la sortie de l'écran (`JournalEntryScreen.kt`, `handleCancel`). Rien n'est enregistré.
- La touche Retour du téléphone ferme l'app depuis l'écran d'un outil, au lieu de revenir à la zone.
- Le compositeur de message perd sa fenêtre d'enrichissement ouverte à la rotation (le texte, lui, survit) : ses blocs reçoivent de nouveaux identifiants à chaque recréation, et l'état du sélecteur de portée n'a pas de forme sauvegardable.
- Compte des tokens avant envoi, via `/v1/messages/count_tokens` chez Claude — à voir pour les autres providers. `TokenCalculator` (171 lignes) et son bloc de strings existent déjà mais ne sont appelés de nulle part : soit ce chantier les reprend, soit ils partent.
- Outils prévus par la vision produit mais jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste.

## À vérifier sur l'appareil

- Rejouer les exemples du prompt L1 dans une session CHAT réelle : la règle est posée dans `docs/AI.md`, mais les exemples corrigés (périodes ISO, pagination par page, grammaire des champs) n'ont été vérifiés que sur lecture du code.
- Exécutions d'automation manquées : vérifier sur l'appareil le passage en base 22→23, la saisie de la fenêtre dans l'éditeur, et un rattrapage réel (automation programmée, app fermée plusieurs jours). `ScheduleCalculatorTest` couvre le calcul de la prochaine échéance, pas la logique de rattrapage elle-même, qui vit dans `AutomationScheduler` : l'item reste entier tant que celle-ci n'est pas testée.
- Champs personnalisés : vérifier la création d'un champ depuis l'écran de configuration, maintenant que le nom technique est attribué par le service et non plus envoyé par le formulaire.
- Montées de base 23→26 : `tool_instances`, `ai_provider_configs`, puis `ai_sessions`, `automations` et `session_messages` sont recréées pour renommer leurs colonnes en snake_case. Vérifier sur l'appareil que tout survit : outils, provider actif, historique des sessions CHAT et des automations, et les deux cascades (zone supprimée → outils, session supprimée → messages).
