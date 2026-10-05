# Le serveur MCP — conception

Conçu le 2026-10-05. Une IA extérieure — un chat claude.ai, d'abord — lit et écrit dans l'app avec les commandes de l'IA intégrée, par un serveur MCP. Le serveur est générique : Claude est un client parmi d'autres, et rien dans l'app ne le nomme.

## Le chemin

- **Le serveur tourne dans l'app**, sur le téléphone : les données n'existent que là.
- **Un client distant ne peut pas joindre le téléphone** : claude.ai appelle depuis les serveurs d'Anthropic (`160.79.104.0/21`), il lui faut une adresse publique en HTTPS, et un téléphone en 4G ne reçoit aucune connexion. Tailscale Funnel ne tourne pas sur Android (il demande la CLI de Tailscale), et sans machine allumée en permanence, rien ne peut le faire tourner pour le téléphone.
- **Un relais sur l'hébergement OVH**, simple tuyau : il met en file toute requête HTTP reçue à son adresse (méthode, chemin, en-têtes, corps), attend la réponse que l'app dépose, la rend, efface les deux. Sans réponse dans le délai : 503. Il ne connaît ni MCP, ni OAuth, ni les commandes ; son seul secret est celui qu'il partage avec l'app, pour qu'elle seule lise la file et y réponde. Il voit passer les données en clair (le HTTPS s'arrête chez lui) et n'en garde rien. Un projet à part (`php-heberge-leger`). À mesurer : combien de temps l'hébergement tient une requête ouverte.
- **Passer plus tard à un relais qui ne lit rien** (un serveur loué qui fait passer les octets chiffrés, le certificat sur le téléphone) ne change que l'adresse dans l'app.

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
- Toute adresse de retour est acceptée et montrée : la sécurité tient au code, que seul a celui qui voit la page, et à PKCE, pas au nom ni au domaine.
- Jeton d'accès d'une heure, renouvelé par le client ; jeton de renouvellement révoqué après 90 jours sans usage. Les clients autorisés se listent et se révoquent dans les réglages.
- Le relais limite le nombre de requêtes.

## Ce que voit le client

- **Un outil MCP par commande, tiré du catalogue des commandes** (préalable, ci-dessous) : son nom, le schéma de ses paramètres, sa description, sa marque lecture ou écriture (`readOnlyHint`, `destructiveHint`). Toutes les commandes de l'IA intégrée, structure comprise ; le client règle outil par outil ce qu'il autorise d'office ou demande.
- **`app_context`**, en lecture : les notions de l'app, L2 et L3. Sa description : « une fois, au début d'une conversation ; ensuite, les outils de lecture ciblés ». Pas d'`instructions` du serveur ni d'envoi d'office : seuls les outils et leurs réponses arrivent au modèle à coup sûr.
- **Chaque réponse d'outil porte la date et l'heure**, au fuseau de l'app.
- Une commande aux identifiants inconnus est refusée comme aujourd'hui, l'erreur ajoutant qu'ils s'obtiennent par `app_context`.
- Les paramètres se lisent comme ceux de l'IA intégrée (ISO, périodes relatives), par le même chemin.

## Ce qui s'écrit

- Une source nouvelle (`Source`) pour le client externe ; chaque écriture porte aussi le nom que le client déclare. L'historique (`undo-history.md`) la garde et l'annule comme les autres.
- Pas de validation par l'app : elle se donne dans le client, outil par outil, là où est la personne.

## Le catalogue des commandes, préalable

Chaque commande déclarée une fois : nom, schéma des paramètres, description, lecture ou écriture. En dérivent la liste des outils MCP, la vérification des paramètres dans `AICommandProcessor` (à la place de `TOOL_DATA_PARAMS` et de ses pareilles) et la partie « commandes » du prompt L1. Le L1 se coupe en trois : les commandes (du catalogue), les notions de l'app (communes aux deux IA, rendues par `app_context`), le protocole de l'enveloppe JSON (propre à l'IA intégrée).
