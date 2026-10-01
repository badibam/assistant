# Modèles libres, locaux ou servis à part — conception

Conçu le 2026-10-01. But : que l'IA de l'app puisse tourner sur un modèle libre, sur le téléphone, un ordinateur ou un serveur à soi, pour la vie privée et pour ne dépendre d'aucun fournisseur propriétaire (cible F-Droid). Tous les usages sont visés, configuration des outils comprise. La crainte de départ : le prompt actuel (`ai_prompt_chunks.xml`, 50 Ko de source, assemblé par `PromptChunks` en degrés 1 et 2) serait trop complexe pour un petit modèle. Cette crainte n'est pas mesurée : le banc ci-dessous la mesure avant qu'on conçoive quoi que ce soit d'autre.

## Préalable : un fournisseur « compatible OpenAI »

Ollama, llama.cpp, vLLM, LM Studio et OpenRouter servent tous `/v1/chat/completions`. Le fournisseur OpenAI de l'app appelle `/v1/responses`, que ces serveurs ne servent pas tous, ou en partie : il reste tel quel, et un fournisseur à part sert tous les autres.

- **Deux variantes fixes**, standard et économique, comme Claude, OpenAI et DeepSeek : une par serveur (OpenRouter et Ollama pour le banc). Le stockage ne change pas. Des instances libres et nommées se feront au troisième serveur.
- **Réglages** : l'adresse, la clé (facultative : un Ollama local n'en demande pas), le modèle, choisi dans `GET /v1/models` comme aujourd'hui, et le forçage de sortie.
- **https seulement** : le prompt porte les données de l'utilisateur, et le but est que personne d'autre ne les lise. Un serveur à la maison passe par `tailscale serve` (certificat valide en `*.ts.net`, joignable hors de chez soi) ou un proxy inverse comme Caddy ; un certificat auto-signé ne passe pas, Android ignorant par défaut les autorités ajoutées par l'utilisateur. Non vérifié : que l'émulateur résolve un nom `*.ts.net` à travers la machine hôte, ce dont dépend la campagne de vitesse.
- **Forçage de sortie, trois valeurs** : aucun, JSON valide (`json_object`), schéma exact de la réponse de l'IA (`json_schema`, depuis `AIMessageSchemas`). Un serveur qui refuse le niveau choisi rend une erreur affichée telle quelle, jamais une redescente d'un niveau.
- **Coût** : la liste LiteLLM comme ailleurs ; un modèle qu'elle ignore a un coût inconnu, jamais nul, déjà le cas (`ModelPriceManager`).
- **Délai** : sans streaming, la réponse entière doit arriver en 10 minutes (`READ_TIMEOUT_MINUTES`). Un modèle sur processeur seul, qui lit lentement un long prompt, peut le dépasser : la campagne de vitesse le dira.

La dette du `manifest.md` (adresse OpenAI en dur, prix d'un serveur inconnu) se ferme avec ce fournisseur : l'adresse en dur reste celle d'OpenAI, qui en est une.

## Le banc

- **L'app joue les scénarios elle-même** : la vraie session, le vrai pipeline, autant de tours qu'il en faut.
- **Lancement** : des tests d'instrumentation (`androidTest/`), un par scénario, le modèle et le niveau de forçage en arguments, lancés par `./run bench`, qui rassemble les résultats dans le terminal et un fichier. Rien dans l'app livrée. **Sur l'émulateur seulement** : ces tests écrivent dans la base de l'app.
- **Base de départ : la démo** (`docs/design/demo.md`). Avant chaque scénario : base vidée, fournisseur reconfiguré depuis les arguments, démo installée (`demo.install`). Le modèle cherche le bon outil parmi une vingtaine, comme en vrai, et les scénarios s'écrivent contre des données connues. Le banc se construit après la démo. Aucun résultat n'est figé ni simulé : seul l'app sait ce que répond une requête, et deux requêtes justes écrites par un modèle ne sont presque jamais identiques.
- **Deux adresses** :
  - OpenRouter, pour cartographier les capacités par taille de modèle : une clé, des centaines de modèles libres, un filtre sur l'hébergeur (forçage par schéma accepté) et sur la compression du modèle (pour tester un modèle compressé comme il le serait sur la machine visée).
  - Ollama sur l'ordinateur, pour la vitesse avec le vrai prompt : servi en https (ci-dessus), donc pas par l'adresse `10.0.2.2` de l'émulateur, qui est en http. Le téléphone comme machine de calcul demande un modèle dans l'app elle-même ; hors de ce banc.
- **La réussite se juge sur l'état final de la zone de test** : chaque scénario porte ses vérifications (« une entrée de 300 dans Calories, datée d'aujourd'hui »). Un chemin différent mais valable réussit. Une question en lecture seule se vérifie au chiffre juste dans la réponse. La session est gardée pour relire les échecs.
- **Une vingtaine de scénarios, en 4 familles** calquées sur les usages : saisie, lecture, automation, configuration. Chaque famille va du simple au retors (date relative, champ personnalisé, outil ambigu). La carte qui en sort dit quelle famille tient à quelle taille.
- **Classes de machines** (ordres de grandeur courants, à confirmer par le banc) : téléphone 1-4B ; ordinateur portable 16-32 Go jusqu'à 14B, et les modèles MoE de 30B sur 32 Go ; serveur avec GPU de 24 Go jusqu'à 32B ; hébergé, 70B et plus.

## Campagne 1

Le prompt actuel (degrés 1 et 2), avec et sans forçage de la sortie par le schéma de la réponse de l'IA (que l'app génère déjà), sur toute l'échelle des tailles. Deux variables seulement. Elle dit à quelle taille le prompt casse, et si c'est la forme (JSON invalide) ou le fond (mauvaise commande, mauvais paramètre) qui cède. L'app lit un objet JSON unique entouré de texte en écartant le texte (`ResponseEnvelope.split`, message système `TEXT_OUTSIDE_JSON`) : le banc compte ces réponses à part, sans quoi la forme paraîtrait tenir quand elle cède.

## Hypothèses de niveaux de prompt

Aucune n'est choisie. La campagne 1 dit lesquelles valent d'être mesurées.

- **Prompt par portée de tâche** (saisie, lecture, configuration complète) plutôt que par taille de modèle : sert aussi les gros modèles, qui coûtent moins. Utile si la carte montre des familles qui tiennent à petite taille et d'autres non.
- **Session limitée à une zone ou un outil**, qui n'embarque que ses commandes et schémas. Utile si l'échec vient du volume plus que de la difficulté.
- **Forçage par schéma seul, sans toucher au prompt** : si la campagne 1 montre que la forme cède avant le fond, ce levier peut suffire.
- **Documentation à la demande** : un sommaire, et une commande « aide sur X ». Fragile chez les petits modèles ; à mesurer avant d'y croire.
- **Exemples choisis selon la demande** plutôt que tous envoyés (le degré 3 est aujourd'hui coupé).
- **Aiguillage** : la demande classée d'abord, un mini-prompt spécialisé ensuite. Remplir le schéma d'une entrée connue est une tâche que les petits modèles font bien.
- **Appel de fonctions natif** au lieu de commandes écrites dans du texte : les modèles libres récents y sont entraînés. Change le format de réponse de l'IA, donc tous les fournisseurs.
- **Modèles mélangés** : un local pour le courant, un gros pour le complexe, choisi par session ou par automation. Une escalade automatique sur échec de validation serait un mécanisme de repli : à valider explicitement.
- **Fine-tuning léger** sur le langage de commandes de l'app, avec des exemples générés par un gros modèle à partir du L1 : prompt minuscule, mais à refaire à chaque évolution de l'API. En dernier recours.
