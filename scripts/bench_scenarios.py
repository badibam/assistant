"""The bench's scenarios (docs/design/local-models.md), on the demo in French.

A chat scenario is the message the user sends; an automation scenario is a demo automation run
now. What each must leave is checked by scripts/bench_checks.py, under the same name.
"""

SCENARIOS = {
    # Entry
    "entry_water": {"kind": "chat", "message": "J'ai bu 2 verres d'eau"},
    "entry_run": {"kind": "chat", "message": "Footing de 7,5 km ce matin en 42 minutes, ressenti 4"},
    "entry_sleep": {"kind": "chat", "message": "Cette nuit j'ai dormi 6 h 50, qualité moyenne"},
    "entry_meal": {"kind": "chat", "message": "Ce midi : 150 g de riz et 120 g de poulet"},
    "entry_shopping": {"kind": "chat", "message": "Ajoute du parmesan aux courses et coche le lait"},
    "entry_ambiguous": {"kind": "chat", "message": "Note : les tomates cerises commencent à rougir"},
    # Reading
    "read_late_tasks": {"kind": "chat", "message": "Quelles tâches sont en retard ?"},
    "read_km_week": {"kind": "chat", "message": "Combien de km j'ai couru cette semaine ?"},
    "read_weight_7d": {"kind": "chat", "message": "Mon poids moyen sur les 7 derniers jours ?"},
    "read_billable_hours": {"kind": "chat", "message": "Combien d'heures facturables pour Studio Brume ce mois-ci ?"},
    "read_kcal_yesterday": {"kind": "chat", "message": "Combien de calories j'ai mangé hier ?"},
    # Automation
    "auto_morning": {"kind": "automation", "automation_id": "demo-auto-morning"},
    "auto_menu": {"kind": "automation", "automation_id": "demo-auto-weekly-menu"},
    "auto_review": {"kind": "automation", "automation_id": "demo-auto-weekly-review"},
    # Configuration
    "config_km_target": {"kind": "chat", "message": "Monte mon objectif kilométrique à 25"},
    "config_coffee": {"kind": "chat", "message": "Crée un suivi \"Café\" en compteur dans Cuisine"},
    "config_elevation": {"kind": "chat", "message": "Ajoute un champ Dénivelé, en mètres, aux Sorties"},
    "config_trail": {"kind": "chat", "message": "Ajoute \"trail\" aux types de sortie"},
    "config_reading_zone": {"kind": "chat", "message": "Crée une zone Lecture avec un suivi des pages lues et un graphique des pages par semaine"},
}
