# Rejeu du prompt L1

Procédure à lancer dans une session CHAT réelle après toute modification du prompt L1 (`ai_prompt_chunks.xml`) ou du pipeline qui le sert — `AICommandProcessor`, `CommandTransformer`, `CommandExecutor.formatResultData`. `docs/AI.md` l'exige : le L1 est la seule description que l'IA reçoit de l'API de commandes, et rien ne le compile.

Ce que ce rejeu ajoute au reste : `scripts/check_prompt_examples.py`, qui tourne à chaque `./run test`, confronte les **exemples JSON** du prompt au code qu'ils décrivent. Il dit que le prompt ne promet rien que le code ne tienne. Il ne dit pas ce que l'IA en fait — ni si elle demande un schéma avant d'agir, ni dans quelle langue elle répond. C'est ce que couvre le rejeu, et lui seul.

Le prompt ci-dessous et sa grille sont à **mettre à jour quand le L1 bouge** : une grille qui décrit un prompt périmé se lit comme un échec là où il n'y en a pas.

## Précautions

- Tout ce que le test crée vit dans la zone qu'il crée lui-même. **Ne pas remplacer « Test L1 » par une zone existante.** Rien n'est supprimé par le test : la zone reste, pour qu'on puisse relire ses données après coup. La supprimer à la main une fois le rejeu lu, avant le suivant — deux zones du même nom rendent les réponses de l'IA ambiguës.

## Le prompt

Trois messages, envoyés l'un après l'autre dans la même session, chacun une fois la réponse au précédent arrivée. La limite d'allers-retours d'une session CHAT se compte depuis le dernier message de l'utilisateur : les dix étapes d'un seul tenant la dépasseraient, trois messages restent chacun en dessous.

Message 1 :

```
Tu es en test. Je vérifie que tes instructions correspondent au code de l'app.
Le test tient en trois messages ; celui-ci est le premier. Réponds en français.

Règles du test, valables pour les trois messages :
- Travaille uniquement dans la zone que tu crées à l'étape 1. Ne modifie et ne
  supprime rien d'autre, sous aucun prétexte.
- Enchaîne toutes les étapes d'un même message sans me rendre la main entre
  elles.
- Après chaque étape numérotée, dis en une ligne ce que tu as fait et ce que le
  système t'a répondu.
- Si une commande est refusée, ne contourne pas en silence : recopie le message
  d'erreur reçu tel quel, puis corrige.

1. Crée une zone « Test L1 » avec une description et une icône que tu choisis
   en cherchant parmi les icônes disponibles.

2. Avant de rien créer dedans, récupère le schéma de configuration d'un suivi
   numérique. Dis-moi l'identifiant exact que tu as demandé.

3. Crée dans cette zone un suivi numérique « Poids test », en kg, avec deux
   champs supplémentaires : un champ texte « Humeur », d'une longueur d'un
   paragraphe, et un champ date-et-heure « Pesé le ». Ne choisis pas toi-même
   leur identifiant technique.

4. Relis la configuration de l'outil et donne-moi les identifiants techniques
   que l'app a attribués aux deux champs.
```

Message 2 :

```
Deuxième message du test, mêmes règles.

5. Récupère le schéma de données de « Poids test », puis ajoute trois entrées,
   datées d'avant-hier, d'hier et d'aujourd'hui, chacune avec un poids, une
   humeur et une valeur pour « Pesé le ». Recopie les horodatages que tu as
   envoyés.

6. Relis ces entrées sur les sept derniers jours jusqu'à maintenant, en ne
   demandant que l'identifiant, l'horodatage, la valeur du poids, l'humeur et
   « Pesé le ». Recopie l'horodatage, l'humeur et le « Pesé le » de la première
   entrée, exactement tels que tu les as reçus.

7. Refais la même lecture en demandant deux entrées par page, et donne-moi la
   page 2.

8. Fais maintenant une erreur exprès : refais la lecture en réclamant le
   conteneur des données en entier au lieu de champs précis. Recopie le refus
   mot pour mot, puis corrige.
```

Message 3 :

```
Troisième et dernier message du test, mêmes règles.

9. Renomme le champ « Humeur » en « Humeur du jour », sans perdre les valeurs
   déjà saisies. Relis une entrée pour me le prouver, en recopiant son humeur.

10. Demande-moi une confirmation explicite avant d'agir, pour une action de ton
    choix dans la zone « Test L1 ».
```

## Grille de lecture

| Étape | Attendu | Échec |
|---|---|---|
| tout | Réponse **en français** | Bascule en anglais — le prompt est en anglais depuis le 2026-09-23 |
| tout | Les étapes d'un message s'enchaînent sans rendre la main | Il faut relancer l'IA au milieu d'un message : `keep_control` n'est pas posé quand il le faudrait |
| 1 | `ICONS` avec `query` (plusieurs mots) et/ou `categories`, puis un nom pris dans les résultats ; l'icône visible sur la zone à l'accueil | Un nom inventé → refus renvoyant à `ICONS` ; une zone sans icône ; `ICONS` appelée sans paramètre en boucle |
| 2 | `tracking_config_numeric` demandé **avant** toute création | Création d'abord, schéma après, ou jamais. Le schéma de données ne se demande pas ici : il exige le `tool_instance_id` d'un outil qui n'existe pas encore |
| 3 | `"type": "TEXT"` avec `config.length` à `MEDIUM` (250) ou `LONG` (1500), **sans clé `name`** | `TEXT_MEDIUM` ou `TEXT_UNLIMITED` (types morts depuis la migration v25→v26). Un `name` envoyé n'est pas refusé à la création, il est gardé : c'est l'IA qui n'a pas suivi le L1 |
| 4 | Deux identifiants snake_case attribués par l'app | Aucun identifiant — les champs ont été enregistrés sans nom, ce que la création faisait avant le 2026-09-23 ; ou ceux que l'IA avait proposés |
| 5 | `tracking_data_numeric` demandé avec le `tool_instance_id` ; chaque entrée porte un `name` ; horodatages en ISO 8601 **avec décalage**, « Pesé le » compris | Une entrée sans `name` → refus ; millisecondes brutes, ou une date sans décalage |
| 6 | `fields` liste `data.quantity` et `custom_fields.<id>` ; `period_start`/`period_end` **à la racine** ; l'humeur et le « Pesé le » **reviennent**, le second en ISO ; l'en-tête de période est en ISO avec décalage | Un objet `period` imbriqué ; des `custom_fields` absents → la création en lot les perd encore (corrigé le 2026-09-24) ; un « Pesé le » en nombre brut → la frontière des dates fuit |
| 7 | `page` et `limit` | `offset`, qui sera refusé |
| 8 | Refus lisible disant que le conteneur n'est pas un champ, puis reprise correcte | Refus incompréhensible, ou boucle |
| 9 | `display_name` modifié, `name` conservé, l'humeur relue intacte | `name` modifié → refus attendu. Valeur perdue → c'est le renommage destructeur déjà porté par `TODO.md` et épinglé par `FieldConfigComparatorTest`, atteint par un autre chemin : à consigner |
| 10 | `validation_request` posé, et un vrai dialogue de validation avant d'agir | L'action passe sans dialogue — à rapprocher de la validation désactivée par défaut, dette d'audit reportée le 2026-09-22 |
| limite | Si la limite d'allers-retours tombe, un message système le dit à l'écran | Plus rien ne se passe après un message envoyé, sans rien à l'écran |

## Relire le rejeu

La conversation et les données se relisent dans la base du téléphone : `adb exec-out run-as com.assistant.debug cat databases/assistant_database` (et ses fichiers `-wal` et `-shm`) dans `tmp/`, puis la table `session_messages` de la dernière session de `ai_sessions`. Ce que l'IA a reçu est dans `system_message_json`, ce qu'elle a envoyé dans `ai_message_json`, et ce qui a vraiment été enregistré dans `tool_data` — c'est là que s'est vue la perte des champs personnalisés, que la réponse du système annonçait comme un succès.
