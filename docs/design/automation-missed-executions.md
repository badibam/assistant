# Exécutions d'automation manquées

Spec transitoire : que fait une automation programmée quand l'app n'a pas tourné à l'heure prévue. À élaguer une fois implémentée.

## Ce qui a déclenché la question

Le 2026-09-18 à 00:54, la release 0.3.15 rouverte après 47 jours sans tourner a lancé à la suite 47 sessions de l'automation « Améliorations transcriptions » (quotidienne, 04:05), une par jour manqué du 2026-08-03 au 2026-09-18 : 231 appels Claude, 6,63 $ selon le calcul de coût de l'app. Mesuré sur l'export de la release (`tmp/assistant_backup_20260918_095325_v0.3.15.json`) et sur les statistiques réseau du téléphone.

Deux causes, toutes deux encore présentes dans `develop` :

1. `AutomationScheduler.getNextSession` calcule la prochaine exécution à partir de la dernière exécution terminée (ou de `updatedAt`), sans aucune limite : chaque occurrence passée est « due » et part à son tour.
2. Les périodes relatives (« hier », « cette semaine ») sont résolues sur l'heure du téléphone (`resolveRelativePeriod`, `PeriodSelector.kt`), pas sur l'heure prévue de l'exécution. Les 47 sessions ont donc lu les mêmes données au lieu de celles de leur jour.

## Décisions

### 1. Les données lues partent de l'heure prévue

`resolveRelativePeriod` prend une date de référence en paramètre au lieu de lire l'horloge. Pour une session AUTOMATION, c'est `scheduledExecutionTime` ; pour CHAT, l'heure actuelle. Vaut pour les deux moments de résolution : les enrichissements du message de départ (`AIEventProcessor.executeEnrichments`) et les requêtes de données de l'IA (`executeDataQueries` → `AICommandProcessor`). Le marqueur `NOW` suit la même règle.

Effet de bord voulu : une session qui franchit minuit (ou l'heure de début de journée) résout aujourd'hui « aujourd'hui » différemment entre son début et ses requêtes suivantes ; ancrée sur l'heure prévue, elle devient cohérente.

### 2. Le prompt donne les deux dates

La dernière ligne du prompt (`ai_prompt_current_datetime`, ajoutée par `ClaudeExtensions` et `OpenAIExtensions`) garde l'heure actuelle. Les sessions AUTOMATION reçoivent en plus, juste à côté, `Date prévue pour cette exécution : …`, toujours, même sans retard.

Ce que l'IA fait ou dit (actions, rappels, messages à l'utilisateur) reste au présent : c'est l'heure actuelle qui compte là. La séparation repose sur ces deux lignes.

### 3. Deux réglages par automation, présentés en un seul bloc

```
Si l'app n'a pas tourné à l'heure prévue
  Rattraper jusqu'à : [ 2 ] [ semaines v ]   (ou illimité)
  [x] Exécuter chaque occurrence manquée  (sinon : seulement la plus récente)
```

- **Fenêtre de validité** : au-delà de ce retard, une occurrence est sautée. Même notion que `validity_window_minutes` de Messages. Stockée en minutes, saisie en nombre + unité (minutes, heures, jours, semaines), « illimitée » possible. Obligatoire, sans valeur par défaut : les automations vont de l'heure à plusieurs semaines.
- **Rattraper chaque occurrence ou seulement la plus récente** : reprend le champ `dismissOlderInstances` existant (`true` = seulement la plus récente), aujourd'hui stocké partout mais lu par personne.

Les deux ne se déduisent pas l'un de l'autre :

| Automation | Fenêtre | Rattrapage |
|---|---|---|
| Point du matin | 2 h | la plus récente |
| Résumé par jour | 2 semaines | chaque occurrence |
| Améliorations transcriptions | illimitée | la plus récente |

Écartés :
- **Un nombre maximum d'occurrences seul** : ne sait pas dire « trop tard » (un point du matin vieux de 3 jours part quand même). Ses seules valeurs utiles sont 1 et infini, c'est-à-dire la case à cocher.
- **Déduire le rattrapage de la présence de périodes relatives dans le message de départ** : l'IA demande aussi ses données en cours de session, et le texte libre dit souvent « analyse la journée » ; la règle se tromperait sans le dire.

Une occurrence sautée laisse une ligne de log, pas de trace dans l'historique de l'automation (l'historique est fait de sessions ; une session vide « sautée » serait une forme de plus à gérer partout).

### 4. Scheduler

La recherche de la prochaine exécution part au plus tôt de `maintenant − fenêtre` (plus rien à sauter en boucle). Avec « seulement la plus récente », parmi les occurrences dues, seule la dernière est lancée.

### 5. Données d'avant ce changement

Les deux réglages ne concernent que les automations programmées. Une automation programmée qui n'a pas la fenêtre reçoit : fenêtre illimitée, seulement la plus récente. Au retour d'une longue absence elle part donc une fois, et l'utilisateur ajuste ensuite. C'est une règle de lecture des anciennes données, appliquée au même endroit par la migration de base et par l'import d'une sauvegarde (`BackupService`) qui ne contient pas le champ, pas un état « à configurer » à entretenir dans le scheduler et l'écran.

## Point ouvert

- **Datation des actions de l'IA** : non vérifié si les actions utilisent des périodes relatives ou des dates calculées par l'IA elle-même. Dans le premier cas, leur résolution doit rester sur l'heure actuelle et le point 1 doit les distinguer des requêtes de données.
