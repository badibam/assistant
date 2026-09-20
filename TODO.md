# TODO

Travail ouvert. Un item disparaît d'ici dès qu'il est fait — le commit en est le registre.

## Chantier en cours

- **Conformité F-Droid** — spec dans `docs/design/fdroid-compliance.md`. Premier bloquant : l'auto-updater, qui télécharge des APK depuis GitHub, est à sortir de la variante F-Droid.

## Dette constatée

Les points de `docs/design/post-refactor-audit.md`, chacun avec son statut (vérifié ou soupçon). À trancher en priorité :


## Divers

- Journal : « Annuler » en modification repasse en consultation sans recharger l'entrée, qui affiche alors la saisie abandonnée jusqu'à la sortie de l'écran (`JournalEntryScreen.kt`, `handleCancel`). Rien n'est enregistré.
- La touche Retour du téléphone ferme l'app depuis l'écran d'un outil, au lieu de revenir à la zone.
- Le compositeur de message perd sa fenêtre d'enrichissement ouverte à la rotation (le texte, lui, survit) : ses blocs reçoivent de nouveaux identifiants à chaque recréation, et l'état du sélecteur de portée n'a pas de forme sauvegardable.
- Compte des tokens avant envoi, via `/v1/messages/count_tokens` chez Claude — à voir pour les autres providers. `TokenCalculator` (171 lignes) et son bloc de strings existent déjà mais ne sont appelés de nulle part : soit ce chantier les reprend, soit ils partent.
- Outils prévus par la vision produit mais jamais livrés : Calcul, Graphique, Alerte, Objectif, Liste.

## À vérifier sur l'appareil

- Rejouer les exemples du prompt L1 dans une session CHAT réelle : la règle est posée dans `docs/AI.md`, mais les exemples corrigés (périodes ISO, pagination par page, grammaire des champs) n'ont été vérifiés que sur lecture du code.
- Exécutions d'automation manquées : vérifier sur l'appareil le passage en base 22→23, la saisie de la fenêtre dans l'éditeur, et un rattrapage réel (automation programmée, app fermée plusieurs jours).
- Champs personnalisés : vérifier la création d'un champ depuis l'écran de configuration, maintenant que le nom technique est attribué par le service et non plus envoyé par le formulaire.
