# Modèles libres, locaux ou servis à part — conception

Conçu le 2026-10-01. But : que l'IA de l'app puisse tourner sur un modèle libre, sur le téléphone, un ordinateur ou un serveur à soi, pour la vie privée et pour ne dépendre d'aucun fournisseur propriétaire (cible F-Droid). Tous les usages sont visés, configuration des outils comprise. La crainte de départ : le prompt actuel (`ai_prompt_chunks.xml`, 50 Ko de source, assemblé par `PromptChunks` en degrés 1 et 2) serait trop complexe pour un petit modèle. Cette crainte n'est pas mesurée : le banc ci-dessous la mesure avant qu'on conçoive quoi que ce soit d'autre.

## Préalable : l'adresse du fournisseur OpenAI réglable

Ollama, llama.cpp, vLLM, LM Studio et OpenRouter parlent tous le protocole OpenAI. Le fournisseur OpenAI de l'app a son adresse en dur (dette enregistrée au `manifest.md`, avec la liste des prix) : la rendre réglable suffit à brancher n'importe lequel. Restent à trancher avec elle : la liste des modèles et le prix d'un serveur inconnu.

## Le banc

- **L'app joue les scénarios elle-même**, sur l'émulateur ou le téléphone, par un point d'entrée de débogage : une zone de test remise à zéro, la vraie session, le vrai pipeline, autant de tours qu'il en faut. Aucun résultat n'est figé ni simulé : seul l'app sait ce que répond une requête, et deux requêtes justes écrites par un modèle ne sont presque jamais identiques.
- **Deux adresses** :
  - OpenRouter, pour cartographier les capacités par taille de modèle : une clé, des centaines de modèles libres, un filtre sur l'hébergeur (forçage par schéma accepté) et sur la compression du modèle (pour tester un modèle compressé comme il le serait sur la machine visée).
  - Ollama sur l'ordinateur, pour la vitesse avec le vrai prompt : l'émulateur joint la machine hôte à `10.0.2.2`. Le téléphone comme machine de calcul demande un modèle dans l'app elle-même ; hors de ce banc.
- **La réussite se juge sur l'état final de la zone de test** : chaque scénario porte ses vérifications (« une entrée de 300 dans Calories, datée d'aujourd'hui »). Un chemin différent mais valable réussit. Une question en lecture seule se vérifie au chiffre juste dans la réponse. La session est gardée pour relire les échecs.
- **Une vingtaine de scénarios, en 4 familles** calquées sur les usages : saisie, lecture, automation, configuration. Chaque famille va du simple au retors (date relative, champ personnalisé, outil ambigu). La carte qui en sort dit quelle famille tient à quelle taille.
- **Classes de machines** (ordres de grandeur courants, à confirmer par le banc) : téléphone 1-4B ; ordinateur portable 16-32 Go jusqu'à 14B, et les modèles MoE de 30B sur 32 Go ; serveur avec GPU de 24 Go jusqu'à 32B ; hébergé, 70B et plus.

## Campagne 1

Le prompt actuel (degrés 1 et 2), avec et sans forçage de la sortie par le schéma de la réponse de l'IA (que l'app génère déjà), sur toute l'échelle des tailles. Deux variables seulement. Elle dit à quelle taille le prompt casse, et si c'est la forme (JSON invalide) ou le fond (mauvaise commande, mauvais paramètre) qui cède.

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
