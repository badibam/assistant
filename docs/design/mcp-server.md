# Le serveur MCP — conception

Conçu le 2026-10-05. Une IA extérieure — un chat claude.ai, d'abord — lit et écrit dans l'app avec les commandes de l'IA intégrée, par un serveur MCP. Le serveur est générique : Claude est un client parmi d'autres, et rien dans l'app ne le nomme.

## Le chemin

- **Le serveur tourne dans l'app**, sur le téléphone : les données n'existent que là.
- **Un client distant ne peut pas joindre le téléphone** : claude.ai appelle depuis les serveurs d'Anthropic (`160.79.104.0/21`), il lui faut une adresse publique en HTTPS, et un téléphone en 4G ne reçoit aucune connexion. Tailscale Funnel ne tourne pas sur Android (il demande la CLI de Tailscale), et sans machine allumée en permanence, rien ne peut le faire tourner pour le téléphone.
- **Un relais sur l'hébergement OVH**, simple tuyau : il met en file toute requête HTTP reçue à son adresse (méthode, chemin, en-têtes, corps), attend la réponse que l'app dépose, la rend, efface les deux. Sans réponse dans le délai : 503. Il ne connaît ni MCP, ni OAuth, ni les commandes ; son seul secret est celui qu'il partage avec l'app, pour qu'elle seule lise la file et y réponde. Il voit passer les données en clair (le HTTPS s'arrête chez lui) et n'en garde rien. Un projet à part : `relay` (`/mnt/data/OUTILS/relay`, facette `php-heberge-leger`), servi à la racine de son sous-domaine, puisqu'il passe le chemin tel quel. Mesuré le 2026-10-05 : l'hébergement tient une requête ouverte 120 s et la coupe vers 160 s (`relay/docs/reference.md`).
- **Passer plus tard à un relais qui ne lit rien** (un serveur loué qui fait passer les octets chiffrés, le certificat sur le téléphone) ne change que l'adresse dans l'app.

## Le contrat du relais

Ce que le relais et l'app se doivent, pour que chacun s'écrive sans l'autre. `<base>` est l'adresse publique du relais (`https://relay.badibam.fr`).

- **Côté client, toute requête** à `<base>/…`, sauf `<base>/_relay/…`, est mise en file telle quelle : un id tiré au hasard, la méthode, le chemin sous `<base>`, la chaîne de requête, les en-têtes (sans ceux du transport : `Host`, `Connection`, `Content-Length`…), le corps. Le relais tient la requête ouverte jusqu'à la réponse de l'app, au plus `REPLY_TIMEOUT` (25 s), puis répond 503 et efface.
- **Côté app**, tout appel à `<base>/_relay/…` porte `Authorization: Bearer <secret du relais>` ; sans lui, 404, comme une adresse qui n'existe pas.
  - `GET <base>/_relay/next?wait=<s>` : la plus ancienne requête en attente, en JSON, retirée de la file ; sans requête, le relais attend jusqu'à `wait` secondes (au plus ce que l'hébergement tient) et répond 204.
  - `POST <base>/_relay/reply/<id>` : la réponse, en JSON ; le relais la rend au client et l'efface. 404 si le client n'attend plus.
- **Le JSON d'une requête** : `{"id", "method", "path", "query", "headers": {nom: valeur}, "body"}`, le corps en base64. **D'une réponse** : `{"status", "headers": {nom: valeur}, "body"}`, de même.
- **Rien ne reste** : une requête retirée par l'app n'est plus dans la file, une réponse rendue est effacée, une requête que l'app n'a pas prise en `REPLY_TIMEOUT` aussi.
- **Limites** : un corps de 1 Mo au plus dans chaque sens (413 au-delà), un nombre de requêtes par minute et par adresse, une file bornée (503 pleine).
- **Pas de flux** : l'app répond toujours d'un bloc en `application/json` (MCP le permet) et refuse le `GET` d'un flux SSE par 405 ; le relais n'a jamais à faire passer de réponse en morceaux.
- Le secret du relais se règle dans sa config (hors du dépôt) et dans l'app.

## L'accès ouvert

- **À la main** : dans les réglages, ou par une tuile des réglages rapides d'Android. Un service au premier plan, sa notification qui dit l'état et le dernier appel, avec Fermer.
- **Fermé seul après 30 minutes sans appel**, chaque appel relançant le délai.
- **Ouvert, l'app tient une requête en attente chez le relais**, qui la garde jusqu'à un appel ou au délai ; l'app en relance une aussitôt.
- Fermé, rien ne passe, jeton valide ou non.

## L'autorisation

L'app est son propre serveur OAuth (description, enregistrement dynamique du client, page d'autorisation, jeton), servie à travers le relais. Le branchement, une fois :

1. L'accès ouvert sur le téléphone, on ajoute l'adresse du relais comme connecteur dans le client.
2. Le client s'enregistre ; son navigateur ouvre la page d'autorisation, servie par l'app, qui montre un code (« 4821 — tapez ce code sur votre téléphone ») et attend.
3. Le téléphone notifie la demande ; son écran montre le nom que le client se donne et le domaine où partira la réponse, tous deux « déclarés par le client », un champ pour le code, Autoriser et Refuser.
4. Le bon code autorise ; la page renvoie au client, qui reçoit son jeton.

- Trois codes faux annulent la demande ; une demande expire en 5 minutes ; une seule à la fois.
- Toute adresse de retour en https (ou sur la machine même) est acceptée et montrée : la sécurité tient au code, que seul a celui qui voit la page, et à PKCE, pas au nom ni au domaine.
- Jeton d'accès d'une heure, renouvelé par le client ; jeton de renouvellement révoqué après 90 jours sans usage. Les clients autorisés se listent et se révoquent dans les réglages.
- Le relais limite le nombre de requêtes.

## Ce que voit le client

- **Un outil MCP par commande, tiré du catalogue des commandes** (préalable, ci-dessous) : son nom, le schéma de ses paramètres, sa description, sa marque lecture ou écriture (`readOnlyHint`). Toutes les commandes de l'IA intégrée, structure comprise ; le client règle outil par outil ce qu'il autorise d'office ou demande.
- **`app_context`**, en lecture : les notions de l'app, L2 et L3, et un jeton de contexte signé par l'app, valable 24 heures. Sa description : « une fois, au début d'une conversation ; ensuite, les outils de lecture ciblés ». Pas d'`instructions` du serveur ni d'envoi d'office : seuls les outils et leurs réponses arrivent au modèle à coup sûr.
- **Tout autre outil exige ce jeton** en paramètre ; absent, faux ou expiré, l'appel est refusé (« appelle d'abord `app_context` »). Le modèle ne peut rien faire sans avoir lu le contexte, et une conversation reprise après 24 heures le relit. Rien ne dépend de la session MCP, qui n'est pas une conversation.
- **Chaque réponse d'outil porte la date et l'heure**, au fuseau de l'app.
- Une commande aux identifiants inconnus est refusée comme aujourd'hui, l'erreur ajoutant qu'ils s'obtiennent par `app_context`.
- Les paramètres se lisent comme ceux de l'IA intégrée (ISO, périodes relatives), par le même chemin.

## Ce qui s'écrit

- Une source nouvelle (`Source.EXTERNAL`) pour le client externe. Le nom que le client déclare ira avec chaque écriture le jour où l'historique (`undo-history.md`) existera pour la garder et l'annuler ; d'ici là, il n'a nulle part où aller.
- Pas de validation par l'app : elle se donne dans le client, outil par outil, là où est la personne.

## Le catalogue des commandes, préalable

Chaque commande déclarée une fois : nom, schéma des paramètres, description, lecture ou écriture. En dérivent la liste des outils MCP, la vérification des paramètres dans `AICommandProcessor` (à la place de `TOOL_DATA_PARAMS` et de ses pareilles) et la partie « commandes » du prompt L1. Le L1 se coupe en trois : les commandes (du catalogue), les notions de l'app (communes aux deux IA, rendues par `app_context`), le protocole de l'enveloppe JSON (propre à l'IA intégrée).
