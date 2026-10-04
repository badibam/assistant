"""What each bench scenario must leave (docs/design/local-models.md), judged on the copies of the
base the app wrote before and after it (scripts/bench.py).

A check reads the expected answer from the base itself: the demo's figures come from a seed and its
dates slide with the install, so nothing here is a figure written in advance. Every check also
holds the rest of the base still: what the scenario does not aim at must be left as it was.

check(name, folder) -> (passed, details), details a list of what failed or was read.
"""

import json
import re
import sqlite3
import unicodedata
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

ZONE = ZoneInfo("Europe/Paris")  # the emulator's, which the bench does not change
# Tables whose rows the AI may change, compared row by row (updated_at aside)
WATCHED = ("tool_data", "tool_instances", "zones", "variables", "automations")


# ============================================================================================
# Reading the copies
# ============================================================================================

class Run:
    def __init__(self, folder):
        self.before = sqlite3.connect(folder / "before.db")
        self.after = sqlite3.connect(folder / "after.db")
        self.result = json.loads((folder / "result.json").read_text(encoding="utf-8"))
        self.session = self.after.execute(
            "SELECT id, created_at, end_reason FROM ai_sessions WHERE id NOT LIKE 'demo-%' ORDER BY created_at DESC LIMIT 1"
        ).fetchone()
        self.now = datetime.fromtimestamp(self.session[1] / 1000, ZONE)
        settings = json.loads(self.before.execute("SELECT settings FROM app_settings_categories WHERE category = 'format'").fetchone()[0])
        self.day_start_hour = settings.get("day_start_hour", 0)
        self.failures = []
        self.notes = []

    # ---- What the AI said ----

    def ai_text(self):
        """Every text of the AI in the session: what it said before and after acting, and a question asked."""
        parts = []
        for (parsed,) in self.after.execute(
                "SELECT ai_message_parsed_json FROM session_messages WHERE session_id = ? AND sender = 'AI' AND ai_message_parsed_json IS NOT NULL",
                (self.session[0],)):
            message = json.loads(parsed)
            parts += [message.get("pre_text") or "", message.get("post_text") or ""]
            if message.get("communication_module"):
                parts.append(json.dumps(message["communication_module"], ensure_ascii=False))
        return "\n".join(parts)

    # ---- Rows ----

    @staticmethod
    def _rows(db, tool):
        rows = []
        for id_, ts, name, data, extra, state in db.execute(
                "SELECT id, timestamp, name, data, extra, state FROM tool_data WHERE tool_instance_id = ?", (tool,)):
            rows.append({"id": id_, "timestamp": ts, "name": name, "data": json.loads(data or "{}"),
                         "extra": json.loads(extra or "{}") if extra else {}, "state": json.loads(state or "{}") if state else {}})
        return rows

    def rows(self, tool, after=True):
        return self._rows(self.after if after else self.before, tool)

    def new_rows(self, tool):
        old = {r["id"] for r in self.rows(tool, after=False)}
        return [r for r in self.rows(tool) if r["id"] not in old]

    def config(self, tool, after=True):
        row = (self.after if after else self.before).execute("SELECT config_json FROM tool_instances WHERE id = ?", (tool,)).fetchone()
        return json.loads(row[0]) if row else None

    def variable(self, id_, after=True):
        row = (self.after if after else self.before).execute("SELECT definition_json FROM variables WHERE id = ?", (id_,)).fetchone()
        return json.loads(row[0]) if row else None

    # ---- Periods, in the app's sense: a day starts at its start hour, a week on Monday ----

    def day_start(self, days_back=0):
        start = self.now.replace(hour=self.day_start_hour, minute=0, second=0, microsecond=0)
        if self.now < start:
            start -= timedelta(days=1)
        return start - timedelta(days=days_back)

    def week_start(self, weeks_back=0):
        today = self.day_start()
        return today - timedelta(days=today.weekday()) - timedelta(weeks=weeks_back)

    @staticmethod
    def ms(moment):
        return int(moment.timestamp() * 1000)

    # ---- What changed ----

    def changes(self):
        """(table, id) of every row added, removed or modified in the watched tables."""
        changed = set()
        for table in WATCHED:
            columns = [c[1] for c in self.before.execute(f"PRAGMA table_info({table})") if c[1] != "updated_at"]
            select = "SELECT " + ", ".join(f'"{c}"' for c in columns) + f" FROM {table}"
            before = {row[0]: row for row in self.before.execute(select)}
            after = {row[0]: row for row in self.after.execute(select)}
            for id_ in before.keys() | after.keys():
                if before.get(id_) != after.get(id_):
                    changed.add((table, id_))
        return changed

    def hold_still(self, allowed=lambda table, id_: False):
        unexpected = sorted(c for c in self.changes() if not allowed(*c))
        if unexpected:
            self.fail(f"changed beyond the request: {unexpected[:6]}{' …' if len(unexpected) > 6 else ''}")

    def fail(self, what):
        self.failures.append(what)

    def note(self, what):
        self.notes.append(what)


def norm(text):
    """Lower case, accents off, for a name to be found in what the AI wrote."""
    return "".join(c for c in unicodedata.normalize("NFD", text.lower()) if unicodedata.category(c) != "Mn")


def numbers_in(text):
    """Every number written in [text], a decimal comma read as a point, a space between thousands
    ("1 274", also a no-break space) read as one number; "6 h 50" and "6 heures 50" also read as 6.83."""
    grouped = re.findall(r"\d{1,3}(?:[   ]\d{3})+(?:[.,]\d+)?", text)
    found = [float(re.sub(r"[   ]", "", n).replace(",", ".")) for n in grouped]
    found += [float(n.replace(",", ".")) for n in re.findall(r"\d+(?:[.,]\d+)?", text)]
    found += [int(h) + int(m) / 60 for h, m in re.findall(r"(\d+)\s*(?:h|heures?)\s*(\d{1,2})", text)]
    return found


def says(run, expected, tolerance, what):
    """The AI's text holds a number within [tolerance] of one of [expected]."""
    expected = [e for e in expected if e is not None]
    found = numbers_in(run.ai_text())
    run.note(f"{what}: expected {[round(e, 2) for e in expected]}")
    if not any(abs(f - e) <= tolerance for f in found for e in expected):
        run.fail(f"{what} not in the answer")


def at_today(run, ts):
    return run.ms(run.day_start()) <= ts <= run.ms(run.now) + 3_600_000


# ============================================================================================
# Entry
# ============================================================================================

def entry_water(run):
    added = sum(r["data"].get("value", 0) for r in run.new_rows("demo-kitchen-water") if at_today(run, r["timestamp"]))
    if added != 2:
        run.fail(f"water added today: {added}, not 2")
    run.hold_still(lambda t, i: t == "tool_data" and i in {r["id"] for r in run.new_rows("demo-kitchen-water")})


def entry_run(run):
    new = run.new_rows("demo-course-runs")
    if len(new) != 1:
        run.fail(f"{len(new)} runs added, not 1")
    else:
        r = new[0]
        moment = datetime.fromtimestamp(r["timestamp"] / 1000, ZONE)
        if r["data"].get("value") != 7.5:
            run.fail(f"distance {r['data'].get('value')}")
        if r["extra"].get("duration") != 42 * 60_000:
            run.fail(f"duration {r['extra'].get('duration')}")
        if r["extra"].get("kind") != "footing":
            run.fail(f"kind {r['extra'].get('kind')}")
        if r["extra"].get("feeling") != 4:
            run.fail(f"feeling {r['extra'].get('feeling')}")
        if not (at_today(run, r["timestamp"]) and moment.hour < 12):
            run.fail(f"dated {moment:%Y-%m-%d %H:%M}, not this morning")
    run.hold_still(lambda t, i: t == "tool_data" and i in {r["id"] for r in new})


def entry_sleep(run):
    new = run.new_rows("demo-course-sleep")
    if len(new) != 1:
        run.fail(f"{len(new)} nights added, not 1")
    else:
        r = new[0]
        if r["data"].get("value") != (6 * 60 + 50) * 60_000:
            run.fail(f"duration {r['data'].get('value')}")
        if r["extra"].get("quality") != 3:
            run.fail(f"quality {r['extra'].get('quality')}, not the middle of 1-5")
        # The night just past: from yesterday evening to this morning
        if not run.ms(run.day_start(1).replace(hour=18)) <= r["timestamp"] <= run.ms(run.now):
            run.fail(f"dated {datetime.fromtimestamp(r['timestamp'] / 1000, ZONE):%Y-%m-%d %H:%M}, not last night")
    run.hold_still(lambda t, i: t == "tool_data" and i in {r["id"] for r in new})


def entry_meal(run):
    new = run.new_rows("demo-kitchen-meals")
    got = sorted((r["extra"].get("food", {}).get("id"), r["extra"].get("quantity"), r["data"].get("value")) for r in new)
    want = sorted([("demo-food-chicken", 120, "lunch"), ("demo-food-sweet_potato", 150, "lunch")])
    if got != want:
        run.fail(f"meals added {got}, not {want}")
    if not all(at_today(run, r["timestamp"]) for r in new):
        run.fail("a meal not dated today")
    run.hold_still(lambda t, i: t == "tool_data" and i in {r["id"] for r in new})


def entry_shopping(run):
    new = run.new_rows("demo-kitchen-shopping")
    if not (len(new) == 1 and "parmesan" in norm(new[0]["name"])):
        run.fail(f"items added {[r['name'] for r in new]}, not one parmesan")
    milk = next(r for r in run.rows("demo-kitchen-shopping") if r["id"] == "demo-shop-milk")
    if "checked_at" not in milk["state"]:
        run.fail("milk not checked")
    run.hold_still(lambda t, i: t == "tool_data" and (i == "demo-shop-milk" or i in {r["id"] for r in new}))


def entry_ambiguous(run):
    new = {tool: run.new_rows(tool) for tool in ("demo-balcony-observations", "demo-balcony-notebook")}
    written = [(tool, r) for tool, rows in new.items() for r in rows]
    # The note may be the entry's name, its value, or both ("Tomates cerises": "commencent à rougir")
    if len(written) != 1 or "tomate" not in norm((written[0][1]["name"] or "") + json.dumps(written[0][1]["data"], ensure_ascii=False)):
        run.fail(f"{len(written)} notes about tomatoes, not 1 in Observations or the balcony notebook")
    run.hold_still(lambda t, i: t == "tool_data" and i in {r["id"] for _, r in written})


# ============================================================================================
# Reading — nothing written
# ============================================================================================

def read_late_tasks(run):
    late = [r["name"] for r in run.rows("demo-work-tasks", after=False)
            if "checked_at" not in r["state"] and r["data"].get("due_at") and r["data"]["due_at"] < run.ms(run.now)]
    text = norm(run.ai_text())
    run.note(f"late: {late}")
    missing = [name for name in late if norm(name) not in text]
    if missing:
        run.fail(f"late tasks not named: {missing}")
    run.hold_still()


def read_km_week(run):
    since = run.ms(run.week_start())
    km = sum(r["data"]["value"] for r in run.rows("demo-course-runs", after=False) if r["timestamp"] >= since)
    says(run, [km], 0.1, "km this week")
    run.hold_still()


def read_weight_7d(run):
    weights = run.rows("demo-course-weight", after=False)

    def average(since):
        values = [r["data"]["value"] for r in weights if r["timestamp"] >= since]
        return sum(values) / len(values) if values else None

    # The variable's window (six days back, at the day's start), or seven days back from now
    says(run, [average(run.ms(run.day_start(6))), average(run.ms(run.now - timedelta(days=7)))], 0.06, "weight average")
    run.hold_still()


def read_billable_hours(run):
    since = run.ms(run.day_start().replace(day=1))
    rows = [r for r in run.rows("demo-work-hours", after=False)
            if r["name"] == "Studio Brume" and r["extra"].get("billable") and r["timestamp"] >= since]
    done = sum(r["data"].get("value", 0) for r in rows) / 3_600_000
    running = sum(run.ms(run.now) - r["state"]["running"]["data"]["value"] for r in rows if "running" in r["state"]) / 3_600_000
    says(run, [done, done + running], 0.05, "billable hours")
    run.hold_still()


def read_kcal_yesterday(run):
    kcal = {r["id"]: r["extra"]["kcal_100g"] for r in run.rows("demo-kitchen-foods", after=False)}
    meals = run.rows("demo-kitchen-meals", after=False)

    def eaten(start, end):
        return sum(r["extra"]["quantity"] * kcal[r["extra"]["food"]["id"]] / 100
                   for r in meals if start <= r["timestamp"] < end)

    # The app's day (from its start hour), or the calendar day
    app_day = eaten(run.ms(run.day_start(1)), run.ms(run.day_start()))
    midnight = run.day_start().replace(hour=0)
    calendar_day = eaten(run.ms(midnight - timedelta(days=1)), run.ms(midnight))
    expected = [app_day, calendar_day]
    found = numbers_in(run.ai_text())
    run.note(f"kcal yesterday: expected {[round(e) for e in expected]}")
    if not any(abs(f - e) <= 0.02 * e for f in found for e in expected):
        run.fail("kcal yesterday not in the answer")
    run.hold_still()


# ============================================================================================
# Automation — the session ends completed
# ============================================================================================

def completed(run):
    if run.session[2] != "COMPLETED":
        run.fail(f"session ended {run.session[2]}, not COMPLETED")


def auto_morning(run):
    completed(run)
    if norm("Relancer la Librairie") not in norm(run.ai_text()):
        run.fail("the late task 'Relancer la Librairie' not named")
    run.hold_still()


def auto_menu(run):
    completed(run)
    new = run.new_rows("demo-kitchen-shopping")
    waiting = {norm(r["name"]) for r in run.rows("demo-kitchen-shopping", after=False) if "checked_at" not in r["state"]}
    if len(new) < 3:
        run.fail(f"{len(new)} items added, fewer than 3")
    doubled = [r["name"] for r in new if norm(r["name"]) in waiting]
    if doubled:
        run.fail(f"items already on the list added again: {doubled}")
    run.hold_still(lambda t, i: t == "tool_data" and i in {r["id"] for r in new})


def auto_review(run):
    completed(run)
    new = run.new_rows("demo-course-notebook")
    last_week = [r["data"]["value"] for r in run.rows("demo-course-runs", after=False)
                 if run.ms(run.week_start(1)) <= r["timestamp"] < run.ms(run.week_start())]
    km = sum(last_week)
    run.note(f"km last week: {round(km, 1)}")
    if len(new) != 1:
        run.fail(f"{len(new)} notebook entries, not 1")
    elif not any(abs(f - km) <= 0.1 for f in numbers_in(json.dumps(new[0]["data"], ensure_ascii=False) + new[0]["name"])):
        run.fail("the entry does not give last week's km")
    run.hold_still(lambda t, i: t == "tool_data" and i in {r["id"] for r in new})


# ============================================================================================
# Configuration
# ============================================================================================

def config_km_target(run):
    definition = run.variable("demo-var-km-target")
    if not definition or definition.get("value") != 25:
        run.fail(f"km target {definition and definition.get('value')}, not 25")
    run.hold_still(lambda t, i: (t, i) == ("variables", "demo-var-km-target"))


def config_coffee(run):
    new = run.after.execute(
        "SELECT id, tooltype, config_json FROM tool_instances WHERE zone_id = 'demo-kitchen' AND id NOT LIKE 'demo-%'"
    ).fetchall()
    fitting = [i for i, tooltype, config in new
               if tooltype == "tracking" and json.loads(config).get("type") == "counter" and "cafe" in norm(json.loads(config).get("name", ""))]
    if len(fitting) != 1 or len(new) != 1:
        run.fail(f"tools added in Cuisine: {[(t, json.loads(c).get('name'), json.loads(c).get('type')) for _, t, c in new]}")
    run.hold_still(lambda t, i: t == "tool_instances" and i in {n[0] for n in new})


def _runs_fields(run, after):
    return {f["name"]: f for f in run.config("demo-course-runs", after)["extra_fields"]}


def config_elevation(run):
    before, after = _runs_fields(run, False), _runs_fields(run, True)
    added = [f for name, f in after.items() if name not in before]
    if any(after.get(name) != f for name, f in before.items()):
        run.fail("an existing field of Sorties changed")
    if len(added) != 1:
        run.fail(f"{len(added)} fields added, not 1")
    else:
        f = added[0]
        if f["type"] != "NUMERIC" or "denivele" not in norm(f.get("display_name", "") + f["name"]):
            run.fail(f"field added: {f}")
        elif norm(str(f.get("config", {}).get("unit", ""))) not in ("m", "metres", "metre"):
            run.fail(f"unit {f.get('config', {}).get('unit')}, not metres")
    run.hold_still(lambda t, i: (t, i) == ("tool_instances", "demo-course-runs"))


def config_trail(run):
    before = _runs_fields(run, False)["kind"]["config"]["options"]
    after = (_runs_fields(run, True).get("kind") or {}).get("config", {}).get("options", [])
    if not all(o in after for o in before):
        run.fail("an existing kind changed or went")
    added = [o for o in after if o not in before]
    if len(added) != 1 or "trail" not in norm(json.dumps(added[0], ensure_ascii=False)):
        run.fail(f"kinds added: {added}")
    run.hold_still(lambda t, i: (t, i) == ("tool_instances", "demo-course-runs"))


def config_reading_zone(run):
    zones = run.after.execute("SELECT id, name FROM zones WHERE id NOT LIKE 'demo-%'").fetchall()
    zone = [z for z in zones if "lecture" in norm(z[1])]
    if len(zone) != 1:
        run.fail(f"zones added: {zones}")
        return run.hold_still(lambda t, i: True)
    tools = run.after.execute("SELECT id, tooltype, config_json FROM tool_instances WHERE zone_id = ?", (zone[0][0],)).fetchall()
    tracking = [i for i, tooltype, config in tools if tooltype == "tracking" and json.loads(config).get("type") == "numeric"]
    charts = [config for _, tooltype, config in tools if tooltype == "chart"]
    if len(tracking) != 1:
        run.fail(f"numeric trackings in the zone: {len(tracking)}")
    elif not any(tracking[0] in c and "week" in c.lower() for c in charts):
        run.fail("no chart by week reading the pages tracking")
    ids = {zone[0][0]} | {t[0] for t in tools}
    run.hold_still(lambda t, i: i in ids)


CHECKS = {f.__name__: f for f in (
    entry_water, entry_run, entry_sleep, entry_meal, entry_shopping, entry_ambiguous,
    read_late_tasks, read_km_week, read_weight_7d, read_billable_hours, read_kcal_yesterday,
    auto_morning, auto_menu, auto_review,
    config_km_target, config_coffee, config_elevation, config_trail, config_reading_zone,
)}


def check(name, folder):
    """(passed, details) for scenario [name] played into [folder]."""
    run = Run(folder)
    if run.result.get("status") == "asked":
        run.note("asked the user a question")
    CHECKS[name](run)
    return not run.failures, run.failures + run.notes
