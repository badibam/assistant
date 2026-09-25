# Requête IA en cours

Spec d'implémentation : ce que devient un appel au fournisseur IA quand on l'interrompt, qu'on arrête la session, que le réseau tombe ou que le délai expire. Conçue le 2026-09-25. À élaguer une fois en place ; ses garanties deviennent des tests.

## Ce que fait le code aujourd'hui

- `AIEventProcessor.callAI` attend la réponse (`aiClient.query`) dans la boucle qui traite les changements d'état de la session (`AIEventProcessor.kt:76`). Tant que l'appel dure, aucun autre changement d'état n'est traité.
- Interrompre (chat) et Stop (automation) changent l'état, mais n'arrêtent pas l'appel. La réponse, une fois arrivée, est jetée (`AIEventProcessor.kt:620`). Une session interrompue ne revient à `IDLE` qu'à ce moment-là. Sans réseau, cela prend jusqu'à 2 + 2 min, ou jamais pour l'automation arrêtée observée sur l'appareil.
- Toute `IOException` est classée `AIFailure.NETWORK` (`AIFailure.kt:42`), et une automation réessaie alors toutes les 30 s. Un délai de lecture dépassé ou une connexion coupée en pleine réponse en font partie, alors que la requête est déjà partie et peut-être facturée.
- Délais OkHttp : 2 min pour la connexion, l'envoi et la lecture (`ClaudeProviderCore.kt:103`, `OpenAIProviderCore.kt:53`). Pas de streaming.

## Décisions

1. **Stop et Interrompre coupent l'appel tout de suite.** Rien de la réponse n'est gardé. En chat, Interrompre rend la main immédiatement ; en automation, Stop ferme la session immédiatement.
2. **L'appel sort de la boucle d'états.** `callAI` le lance comme une tâche à part qu'on peut annuler et garde de quoi l'annuler. La réponse revient comme un événement ordinaire. Interrompre et Stop annulent la tâche avant de changer d'état. Un seul appel en cours à la fois.
3. **Deux sortes d'échec réseau.**
   - *Jamais partie* (pas de connexion, hôte introuvable, délai de connexion dépassé) : rien n'est facturé. Le comportement actuel est gardé : une automation attend le réseau, le chat échoue tout de suite.
   - *Partie sans réponse* (délai de lecture dépassé, connexion coupée pendant la réponse) : traitée comme un refus du fournisseur. La session s'arrête, sans relance automatique.
4. **Délais** : connexion 15 s, lecture 10 min. La doc de l'API Anthropic (« Long requests ») ne fixe aucune limite côté serveur pour un appel sans streaming. C'est le SDK officiel qui refuse au-delà de 10 min prévues, et l'app ne l'utilise pas. Le risque nommé par cette doc est un réseau qui coupe une connexion restée silencieuse.
5. **Ce qu'on voit** : un message système par façon dont l'appel se termine, exclu du prompt : « Interrompu », « Arrêté », « Requête envoyée, réponse perdue ».
6. **Coût** : un appel coupé après son envoi est noté « coût inconnu », jamais 0. Le total de la session s'affiche alors comme une borne basse (« ≥ X »). Un appel coupé avant de partir n'a rien coûté.

## Garanties à tester

- Stop pendant un appel qui ne répond jamais : la session est fermée sans attendre de réponse.
- Interrompre pendant un appel en chat : la session est `IDLE` et accepte un nouveau message sans attendre de réponse.
- Une réponse qui arrive après une annulation n'est jamais enregistrée.
- Un échec *jamais partie* en automation mène à l'attente du réseau ; un échec *partie sans réponse* arrête la session.
- Une session qui contient un appel coupé après envoi n'affiche pas de total exact.

## À vérifier en l'implémentant

- NOTES rapporte qu'en chat sans réseau, « l'appel part comme si de rien n'était ». Pourtant le code vérifie le réseau avant l'appel (`AIEventProcessor.kt:579`, connecté et `VALIDATED`). À reproduire sur l'appareil pour savoir ce qui part vraiment.
- Le `max_tokens` Claude a trois valeurs par défaut : 2000 dans le commentaire (`ClaudeProviderCore.kt:151`), 8000 dans le schéma, 32000 dans le code quand le champ manque (`ClaudeExtensions.kt:42`). C'est la dernière qui compte. Elle fixe la durée possible d'un appel, donc à choisir avec le délai de lecture.
