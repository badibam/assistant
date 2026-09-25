# Coût IA par appel

Spec d'implémentation : chaque appel à l'IA garde le coût qu'il a eu, et le coût d'une session se calcule à la lecture. Conçue le 2026-09-25. À élaguer une fois en place ; ses garanties deviennent des tests.

## Ce que fait le code aujourd'hui

- Chaque message IA garde ses quatre compteurs de tokens (entrée hors cache, écriture en cache, lecture en cache, sortie). Le décompte par fournisseur est juste : OpenAI inclut le cache lu dans son entrée, et `OpenAIExtensions.kt` le retire.
- Le coût n'existe qu'au niveau de la session. `AIEventProcessor.updateSessionTokensAndCost` cumule les tokens dans `ai_sessions.tokens_json`, puis recalcule tout `cost_json` au tarif du moment, avec le modèle configuré au moment du dernier appel. Un changement de tarif ou de modèle réécrit le coût des appels passés.
- Un tarif introuvable au moment d'un appel met `cost_json` à `null` : le coût de toute la session disparaît. Un prix de cache manquant compte 0 (`?: 0.0`).
- `ModelPriceManager` télécharge la liste LiteLLM une fois, au lancement de l'écran principal, et la garde en mémoire seulement. Un échec laisse la liste vide jusqu'au redémarrage. Seul l'écran de config Claude/DeepSeek la télécharge à nouveau (au plus une fois par heure). Une automation lancée en arrière-plan par WorkManager ne passe pas par l'écran principal : déduit du code, non vérifié sur l'appareil, elle tourne sans prix.
- `mapProviderModelToLiteLLMId` attend `"claude"` et `"openai"`, alors que les identifiants sont `claude_standard`, etc. : tout passe par la branche par défaut, qui cherche le nom du modèle tel quel. Ça suffit pour Claude, OpenAI et DeepSeek (`deepseek-chat` est dans la liste).
- `calls_with_unknown_usage` dans `tokens_json` compte les appels coupés ou perdus après leur envoi.

## Décisions

1. **Le coût d'un appel est celui du moment où il a été fait.** Il ne suit ni les changements de tarif ni les changements de modèle faits ensuite.
2. **Chaque message IA stocke le modèle et ses quatre prix unitaires** au moment de l'appel. Le modèle est celui de la config (le fournisseur Claude refuse déjà une réponse d'un autre modèle). Le coût n'est pas stocké : c'est tokens × prix.
3. **Un prix manquant est stocké `null`, jamais 0.** Il rend l'appel « coût inconnu » seulement si sa catégorie a des tokens : OpenAI n'a pas de prix d'écriture en cache, et n'en écrit jamais.
4. **Paliers** : quand la liste LiteLLM a un prix `…_above_<N>k_tokens` et que l'entrée de l'appel (hors cache + écrite + lue) dépasse N milliers, ce sont ces prix qui sont stockés. Les variantes `_flex`, `_priority` et `_batches` sont ignorées : l'app n'utilise pas ces modes.
5. **Les prix sont disponibles quand on en a besoin.** La dernière liste téléchargée est copiée sur le téléphone et relue au démarrage, avec ou sans réseau, écran principal ou pas. Plus vieille qu'un jour, elle est retéléchargée en arrière-plan au moment d'un appel. Un modèle absent de la liste a un prix inconnu, jamais ajouté après coup.
6. **La session ne stocke plus de totaux.** `tokens_json` et `cost_json` disparaissent. Tokens et coût se calculent à la lecture en additionnant les messages, par une requête SQL pour la liste de l'historique d'automation.
7. **Un appel au coût inconnu vient d'un fait porté par un message** : un message IA dont un prix utile est `null`, ou le message système d'un appel coupé ou perdu après l'envoi, qui reçoit une marque « usage inconnu ». `calls_with_unknown_usage` disparaît avec `tokens_json`. Dès qu'un appel de la session a un coût inconnu, le total s'affiche en « ≥ ».
8. **Migration** : colonnes de modèle et de prix sur les messages, marque « usage inconnu », suppression de `tokens_json` et `cost_json`, sauvegardes comprises. Les messages déjà enregistrés reçoivent des prix inconnus : leur session affiche ses tokens et un total en « ≥ ».

## Garanties à tester

- Changer le tarif ou le modèle après un appel ne change pas le coût de cet appel.
- Un prix `null` sur une catégorie à 0 token ne rend pas l'appel inconnu ; sur une catégorie avec des tokens, si.
- Un appel au-delà du seuil d'un palier stocke les prix du palier ; en deçà, les prix de base.
- Au démarrage sans réseau, la copie locale fournit les prix.
- Le total d'une session est la somme de ses appels, en « ≥ » dès qu'un appel est inconnu.
- Une sauvegarde d'avant la migration s'importe, avec des prix inconnus.
