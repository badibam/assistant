# Un seul système de champs

Conception en cours, commencée le 2026-09-25. Regroupe les items de `NOTES.md` qui pointent vers la même généralisation : types durée et tags, valeur par défaut, validation du choix, « les fields partout », questionnaire, réponses des modules de communication, Données structurées.

## Décisions

1. **Toute valeur saisie dans une entrée est un champ.** Le contenu d'une entrée — ce que l'utilisateur ou l'IA saisit (`content` du journal et d'une note, `title` et `content` d'un message, la valeur du tracking) — est déclaré avec les mêmes types de champ que les champs personnalisés : mêmes contraintes, même schéma généré, même saisie, même affichage, mêmes migrations. Un type d'outil déclare ses champs fixes, qui ne se suppriment ni ne se renomment ; l'utilisateur en ajoute d'autres.
2. **L'état n'est pas un champ.** Ce que l'app et les actions produisent sur une entrée (`status`, `read`, `archived`, `notification_sent`, `triggered_by` des messages, `position` d'une note) ne s'écrit pas par un formulaire : il s'écrit par son action (marquer lu, déplacer) ou par le système.
3. **Les valeurs sont rangées par auteur**, en trois objets JSON d'une entrée :
   - `data` — les champs fixes, déclarés par le type d'outil ;
   - `extra` — les champs ajoutés par l'utilisateur (l'actuel `custom_fields`, renommé) ;
   - `state` — l'état, décrit par le schéma du type d'outil.
   Le type d'outil et l'utilisateur font évoluer leurs champs chacun de leur côté (montée de version de l'app pour l'un, changement de config pour l'autre) : dans un même objet, tout champ fixe ajouté plus tard pourrait entrer en collision avec un champ déjà créé par un utilisateur.
4. **L'IA voit les trois au même niveau.** Elle lit et filtre `data.x`, `extra.x` et `state.x` avec la même liste de chemins (`FieldPatternGrammar`) ; un filtre, pour l'IA comme dans le pointeur, porte sur tout chemin décrit par le schéma, pas seulement sur un champ. La séparation ne joue qu'à l'écriture.
5. **Un type de champ dit ce qu'est une valeur ; un outil dit comment on crée ses entrées et ce qu'il en calcule.** Le type de champ porte la forme de la valeur, ses contraintes, son schéma, sa saisie dans un formulaire, son affichage, sa description pour l'IA et sa conversion vers un autre type. L'outil porte ses façons rapides de créer une entrée (boutons +/−, chronomètre, raccourcis), ses calculs sur plusieurs entrées (total, moyenne) et l'ordre de ses entrées.
6. **Le tracking perd ses types propres.** numeric, text, scale, choice et boolean deviennent les types de champ du même nom. Le compteur devient un champ NUMERIC entier avec le mode « boutons +/− » du tracking ; le timer, un champ DURÉE avec le mode « chronomètre » du tracking.
7. **Un champ DURÉE peut être « en cours ».** Deux opérations du dispatcher (du genre `tool_data.start_duration` / `stop_duration`) : le départ écrit l'instant dans `state` de l'entrée (`state.running.<champ>`), l'arrêt écrit le temps écoulé dans le champ et retire la clé. Le temps écoulé se calcule à la lecture. La vérité est en base : un chronomètre survit à l'app tuée. L'IA passe par les mêmes opérations. Le tracking n'y ajoute que ses boutons et sa règle « démarrer une activité arrête celle qui tourne dans le même outil ». Aujourd'hui seul le tracking montre un bouton démarrer.
8. **Le type DURÉE stocke des millisecondes, sans unité.** L'unité vit dans la config du champ et décide de la saisie et de l'affichage : une précision (la plus petite unité saisie et affichée) et une forme (composée « 1 h 25 min » ou unité unique « 85 min »). Pas de mois ni d'année : ce ne sont pas des durées fixes ; si le besoin vient, c'est un autre type (une période de calendrier, avec son départ). Face à l'IA, une durée est en ISO 8601 (`PT1H25M`), marquée dans le schéma de stockage par un format du genre `duration-millis` d'où `SchemaModelView` dérive la vue de l'IA, et convertie aux mêmes endroits que les dates.

## Écarté

- Fusionner `data` et `custom_fields` en un seul objet : collision permanente entre noms fixes et noms de l'utilisateur, et un outil ne pourrait plus refuser une clé inconnue (`additionalProperties: false`). Les gains attendus venaient de la déclaration unique des champs, pas du stockage.
- Garder l'état dans `data` : même collision, et l'IA devrait deviner, clé par clé, ce qui se saisit.
- Des colonnes communes pour `position` ou `archived` : pas de deuxième outil qui en ait besoin aujourd'hui.
- Le chronomètre porté par le type DURÉE lui-même : il doit survivre à la fermeture du formulaire, ce qui est le rôle de `state`, pas du type.
- `user` comme nom de `extra` : s'opposerait à « IA » dans le reste de l'app.

## État du code au 2026-09-25

- Types de champ (`core/fields/FieldType.kt`) : TEXT, NUMERIC, SCALE, CHOICE, BOOLEAN, RANGE, DATE, TIME, DATETIME. Types propres au tracking : numeric, scale, choice, boolean, text, counter, timer, chacun avec son schéma de données (`tracking_data_<type>`).
- `custom_fields` est une colonne de `tool_data`, lue dans 44 fichiers du code principal et 11 de test.
- Une seule requête SQL lit dans le JSON : `json_extract(data, '$.status')` pour les messages.
- Un nom de champ utilisateur vient de son libellé, sans préfixe (`FieldNameGenerator`).
- Le compteur stocke `increment` (entier signé) par entrée ; le timer, `duration_seconds`. Le chronomètre en cours ne vit qu'en mémoire (`TimerManager`, `TimerState`) : l'app tuée perd l'heure de départ et laisse l'entrée à 0.

## Ouvert

- La valeur du tracking est un champ fixe dont le type dépend de la config de l'instance ; ses raccourcis (`items`) portent un nom, une quantité par défaut et une unité, et l'unité est aujourd'hui stockée par entrée.
- `raw` du tracking : dérivé, à ne plus stocker (dette du manifeste).
- Les copies `common_title`, `common_content`, `priority` sur une occurrence de message : champs ou état.
- Types à ajouter : tags / pastille, étiquette du tracking.
- Réglages d'un champ : valeur par défaut, validation du choix contre ses options, label affiché ou non.
- Questionnaire, réponses multiples et ordonnées des modules de communication, Données structurées : à exprimer avec les champs.
- La config des outils en champs : plus tard, un chantier à part.
- Les migrations : tracking (forme des données, secondes → millisecondes), `custom_fields` → `extra`, état sorti de `data` vers `state`.
