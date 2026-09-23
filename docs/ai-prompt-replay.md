# Rejeu du prompt L1

Procédure à lancer dans une session CHAT réelle après toute modification du prompt L1 (`ai_prompt_chunks.xml`) ou du pipeline qui le sert — `AICommandProcessor`, `CommandTransformer`, `CommandExecutor.formatResultData`. `docs/AI.md` l'exige : le L1 est la seule description que l'IA reçoit de l'API de commandes, et rien ne le compile.

Ce que ce rejeu ajoute au reste : `scripts/check_prompt_examples.py`, qui tourne à chaque `./run test`, confronte les **exemples JSON** du prompt au code qu'ils décrivent. Il dit que le prompt ne promet rien que le code ne tienne. Il ne dit pas ce que l'IA en fait — ni si elle demande un schéma avant d'agir, ni dans quelle langue elle répond. C'est ce que couvre le rejeu, et lui seul.

Le prompt ci-dessous et sa grille sont à **mettre à jour quand le L1 bouge** : une grille qui décrit un prompt périmé se lit comme un échec là où il n'y en a pas.

## Précautions

- Tout ce que le test crée vit dans la zone qu'il crée lui-même, et disparaît à la dernière étape. **Ne pas remplacer « Test L1 » par une zone existante** : l'étape 10 supprime la zone et tout ce qu'elle contient.
- Si l'étape 10 échoue à demander confirmation, la suppression a quand même lieu. Sans danger ici, puisque la zone est celle du test.

## Le prompt

```
Tu es en test. Je vérifie que tes instructions correspondent au code de l'app,
après trois changements du prompt. Réponds en français.

Règles du test :
- Travaille uniquement dans la zone que tu crées à l'étape 1. Ne modifie et ne
  supprime rien d'autre, sous aucun prétexte.
- Après chaque étape numérotée, dis en une ligne ce que tu as fait et ce que le
  système t'a répondu.
- Si une commande est refusée, ne contourne pas en silence : recopie le message
  d'erreur reçu tel quel, puis corrige.

1. Crée une zone « Test L1 » avec une description et une icône de ton choix.

2. Avant de rien créer dedans, récupère le schéma de configuration et le schéma
   de données d'un suivi numérique. Dis-moi les deux identifiants exacts que tu
   as demandés.

3. Crée dans cette zone un suivi numérique « Poids test », en kg, avec deux
   champs supplémentaires : un champ texte « Humeur », d'une longueur d'un
   paragraphe, et un champ date-et-heure « Pesé le ». Ne choisis pas toi-même
   leur identifiant technique.

4. Relis la configuration de l'outil et donne-moi les identifiants techniques
   que l'app a attribués aux deux champs.

5. Ajoute trois entrées, datées d'avant-hier, d'hier et d'aujourd'hui, chacune
   avec un poids, une humeur et une valeur pour « Pesé le ». Recopie les
   horodatages que tu as envoyés.

6. Relis ces entrées sur les sept derniers jours jusqu'à maintenant, en ne
   demandant que l'identifiant, l'horodatage, la valeur du poids, l'humeur et
   « Pesé le ». Recopie l'horodatage et le « Pesé le » de la première entrée,
   exactement tels que tu les as reçus.

7. Refais la même lecture en demandant deux entrées par page, et donne-moi la
   page 2.

8. Fais maintenant une erreur exprès : refais la lecture en réclamant le
   conteneur des données en entier au lieu de champs précis. Recopie le refus
   mot pour mot, puis corrige.

9. Renomme le champ « Humeur » en « Humeur du jour », sans perdre les valeurs
   déjà saisies. Relis une entrée pour me le prouver.

10. Demande-moi une confirmation explicite avant d'agir, puis supprime la zone
    « Test L1 » et tout ce qu'elle contient.
```

## Grille de lecture

| Étape | Attendu | Échec |
|---|---|---|
| tout | Réponse **en français** | Bascule en anglais — le prompt est passé à l'anglais le 2026-09-23, c'est le risque principal de ce rejeu-ci |
| 1 | Icône prise dans Lucide | Un nom d'icône inventé |
| 2 | `tracking_config_numeric` et `tracking_data_numeric` demandés **avant** toute création | Création d'abord, schéma après, ou jamais |
| 3 | `"type": "TEXT"` avec `"config": {"length": "MEDIUM"}`, **sans clé `name`** | `TEXT_MEDIUM` ou `TEXT_UNLIMITED` (types morts depuis la migration v25→v26), ou un `name` inventé → refus `Nom technique inconnu` |
| 4 | Deux identifiants snake_case attribués par l'app | Ceux que l'IA avait proposés |
| 5 | Horodatages en ISO 8601 **avec décalage**, « Pesé le » compris | Millisecondes brutes, ou une date sans décalage |
| 6 | `fields` liste `data.value` et `custom_fields.<id>` ; période en `period_start`/`period_end` **à la racine** ; ce qui revient est de l'ISO | Un objet `period` imbriqué ; un `custom_fields` de type date qui revient en nombre brut → la frontière des dates fuit |
| 7 | `page` et `limit` | `offset`, qui sera refusé |
| 8 | Refus lisible disant que le conteneur n'est pas un champ, puis reprise correcte | Refus incompréhensible, ou boucle |
| 9 | `display_name` modifié, `name` conservé, valeurs intactes | `name` modifié → refus attendu. Valeurs perdues → c'est le renommage destructeur déjà porté par `TODO.md` et épinglé par `FieldConfigComparatorTest`, atteint par un autre chemin : à consigner |
| 10 | Un vrai dialogue de validation avant la suppression | Suppression sans demander — à rapprocher de la validation désactivée par défaut, dette d'audit reportée le 2026-09-22 |
