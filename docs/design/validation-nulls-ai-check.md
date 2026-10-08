# Recette : les null à l'écriture, par une session IA

Écrit le 2026-10-08, pour la règle posée le même jour (`docs/DATA.md`, « Usage de `name` et `timestamp` ») : à la création et pour un objet écrit en entier, une clé à null est une valeur non donnée, retirée avant de valider et d'enregistrer (`JsonNulls`) ; le validateur vérifie ce qu'on lui donne ; les éléments d'une liste restent tels qu'envoyés ; en modification, une clé à null efface le champ. À supprimer une fois passé, ses lignes de `device-checks.md` (« Validation ») avec.

## Avant

- La démo installée, en français (l'outil Fractionné de Course en fait partie).
- Une session CHAT ouverte, un fournisseur qui suit bien les consignes (Claude Sonnet, par exemple). Les validations demandées par l'IA : les accepter.
- Coller le message ci-dessous tel quel.

## Le message

```
Session de test de l'app, pas une demande réelle. Fais les étapes une par une, dans l'ordre, chacune par une commande à part, en écrivant exactement le JSON indiqué pour les valeurs à null — c'est ce qui est testé, ne les retire pas et ne les remplace pas. Après chaque étape, note : la commande envoyée, accepté ou refusé, et le message d'erreur mot pour mot s'il y en a un. Une étape refusée ne t'arrête pas : passe à la suivante. À la fin, rends un tableau : étape, résultat, message.

1. Crée une zone « Test null » (description non donnée).
2. Dans cette zone, crée un outil Suivi numérique « Mesure test », unité « u », avec `"description": null` dans sa config, et deux champs supplémentaires : « Note » (texte court, facultatif) et « Étiquettes » (choix multiple, options « a » et « b », facultatif).
3. Crée une entrée dans « Mesure test » : valeur 3, avec dans ses champs supplémentaires `"note": null` (n'utilise pas d'autre forme).
4. Crée une entrée dans « Mesure test » : valeur 4, note « bonjour », étiquettes `["a", null]` exactement.
5. Modifie l'entrée de l'étape 3 : note « ajoutée ».
6. Modifie la même entrée : `"note": null`, pour effacer la note.
7. Modifie la config de « Mesure test » : renvoie la config entière, avec `"description": null`.
8. Lis les deux entrées de « Mesure test » et la config de l'outil : dis pour chacune si une clé y vaut null, ou si la clé est absente.
9. Dans l'outil Fractionné (zone Course), appelle l'opération complete pour une séance qui n'était pas prévue : datée d'hier à 18 h 30, avec `"duration": null`.
10. Lis l'entrée créée à l'étape 9 : dis si `duration` est absent ou vaut null.
11. Pose-moi une question par un module de communication : un champ « Commentaire » (texte, facultatif) et un champ « Note sur 5 » (nombre, obligatoire). J'y répondrai.
12. Supprime la zone « Test null ».
```

À l'étape 11, laisser « Commentaire » vide, mettre 4 à « Note sur 5 », valider.

## Ce qui est attendu

| Étape | Attendu |
|---|---|
| 1 | Acceptée. |
| 2 | Acceptée ; la config n'a pas de clé `description`. |
| 3 | Acceptée ; l'entrée n'a pas de clé `note`. |
| 4 | **Refusée**, le message dit que l'élément null des étiquettes n'est pas une valeur permise : un élément de liste n'est jamais retiré. |
| 5 | Acceptée. |
| 6 | Acceptée ; la note est effacée (clé absente), pas écrite à null. |
| 7 | Acceptée ; la config n'a pas de clé `description`. |
| 8 | Aucune clé à null nulle part : les clés données à null sont absentes. |
| 9 | Acceptée. |
| 10 | `duration` absent. |
| 11 | La réponse est acceptée sans le commentaire ; l'IA reçoit la note 4. |
| 12 | Acceptée. |

Sur l'écran : l'entrée de l'étape 3 et celle de l'étape 9 (historique du Fractionné) n'affichent nulle part le mot « null ». Une étape refusée autrement qu'à l'étape 4, ou une clé à null lue à l'étape 8 ou 10 : le message d'erreur et le tableau de l'IA vont dans le commit qui corrige.
