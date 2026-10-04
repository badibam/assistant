#!/usr/bin/env python3
"""The bench (docs/design/local-models.md): free models played on the app's scenarios.

The app plays each scenario itself on the emulator (app/src/androidTest/.../BenchScenario.kt):
the base emptied, the OpenAI-compatible provider set to OpenRouter and the model, the demo
installed, the message sent or the automation run. It leaves a copy of the base before and
after, and how the session ended; this script fetches them and judges them
(scripts/bench_checks.py).

    scripts/bench.py play <model> <scenario> [<forcing>]   one scenario, its files kept under tmp/bench/;
                                                         forcing none|json|schema, schema by default
    scripts/bench.py run [--forcing none,schema] [--only s1,s2] <model> ...
        every scenario on each model and forcing level, judged as it goes, under tmp/bench/<date>/:
        each play's verdict in its folder, the table and the costs rewritten after each one
    scripts/bench.py resume [<folder>]   an interrupted campaign, the latest by default: the plays
        that have their verdict are kept, the others played

The OpenRouter key comes from OPENROUTER_API_KEY, else from .env at the main repository's root.
"""

import base64
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
# The main checkout, where .env lives, whichever worktree runs this
MAIN_ROOT = Path(subprocess.run(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"],
                                cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()).parent
# In the main checkout: a worktree's tmp/ goes with it when it is removed
OUT = MAIN_ROOT / "tmp" / "bench"

APP = "com.assistant.debug"
RUNNER = f"{APP}.test/androidx.test.runner.AndroidJUnitRunner"
TEST = "com.assistant.bench.BenchScenario"
BASE_URL = "https://openrouter.ai/api/v1"
FORCING = "schema"
# The campaign's levels: forcing helps one model and breaks another, so each plays both
CAMPAIGN_FORCINGS = ("none", "schema")
# A whole scenario: a normal call answers within a minute, so a stalled provider costs this at most
TIMEOUT_MINUTES = 6

sys.path.insert(0, str(Path(__file__).resolve().parent))
from bench_scenarios import SCENARIOS  # noqa: E402
from bench_checks import check  # noqa: E402


def api_key():
    key = os.environ.get("OPENROUTER_API_KEY")
    if key:
        return key
    env = MAIN_ROOT / ".env"
    if env.is_file():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.startswith("OPENROUTER_API_KEY="):
                return line.split("=", 1)[1].strip()
    sys.exit("No OpenRouter key: set OPENROUTER_API_KEY, or write it in .env at the repository root")


def adb(*args, check=True, capture=True):
    return subprocess.run(["adb", "-s", serial(), *args], capture_output=capture, text=True, check=check)


_SERIAL = None


def serial():
    """The emulator, and never a phone: the bench empties the app's base."""
    global _SERIAL
    if _SERIAL is None:
        lines = subprocess.run(["adb", "devices"], capture_output=True, text=True, check=True).stdout.splitlines()[1:]
        emulators = [l.split()[0] for l in lines if l.startswith("emulator-") and l.split()[-1] == "device"]
        if len(emulators) != 1:
            sys.exit(f"The bench needs exactly one running emulator, found {len(emulators)}. "
                     "Start one: emulator -avd Pixel_4a -no-window")
        _SERIAL = emulators[0]
    return _SERIAL


def prepare_device():
    """The emulator in French (the demo takes the phone's language), the app and the test installed."""
    if adb("shell", "getprop", "persist.sys.locale").stdout.strip() != "fr-FR":
        adb("root")
        adb("wait-for-device")
        adb("shell", "setprop", "persist.sys.locale", "fr-FR")
        adb("shell", "setprop", "ctl.restart", "zygote")
        time.sleep(10)
        while adb("shell", "getprop", "sys.boot_completed", check=False).stdout.strip() != "1":
            time.sleep(3)
    env = {**os.environ, "ANDROID_SERIAL": serial()}
    done = subprocess.run([str(ROOT / "gradlew"), "-q", ":app:installDebug", ":app:installDebugAndroidTest"],
                          cwd=ROOT, env=env, check=False)
    if done.returncode != 0:
        sys.exit("Installing the app and the bench test failed")


def play(model, name, key, into, forcing=FORCING):
    """Plays one scenario on one model; its files land in [into]. Returns the result read back.

    [forcing] other than the campaign's is for diagnosing a model that fails with it.
    """
    scenario = SCENARIOS[name]
    args = ["-e", "class", TEST, "-e", "base_url", BASE_URL, "-e", "api_key", key, "-e", "model", model,
            "-e", "output_forcing", forcing, "-e", "timeout_minutes", str(TIMEOUT_MINUTES), "-e", "kind", scenario["kind"]]
    if scenario["kind"] == "chat":
        args += ["-e", "message_b64", base64.b64encode(scenario["message"].encode("utf-8")).decode("ascii")]
    else:
        args += ["-e", "automation_id", scenario["automation_id"]]
    ran = adb("shell", "am", "instrument", "-w", *args, RUNNER, check=False)

    if into.exists():
        shutil.rmtree(into)
    into.mkdir(parents=True)
    (into / "instrument.txt").write_text(ran.stdout.replace(key, "<key>") + ran.stderr.replace(key, "<key>"), encoding="utf-8")
    for name_ in ("before.db", "after.db", "result.json"):
        pulled = subprocess.run(["adb", "-s", serial(), "exec-out", "run-as", APP, "cat", f"files/bench/{name_}"],
                                capture_output=True, check=False)
        if pulled.returncode == 0 and pulled.stdout:
            (into / name_).write_bytes(pulled.stdout)
    result = into / "result.json"
    if not result.is_file():
        return {"status": "error", "detail": "no result: see instrument.txt"}
    return json.loads(result.read_text(encoding="utf-8"))


def prices():
    """$ per token of each OpenRouter model: input, cached input, output."""
    import urllib.request
    with urllib.request.urlopen(f"{BASE_URL}/models", timeout=30) as reply:
        models = json.load(reply)["data"]

    def num(pricing, key):
        return float(pricing.get(key) or 0)
    return {m["id"]: (num(m["pricing"], "prompt"), num(m["pricing"], "input_cache_read") or num(m["pricing"], "prompt"),
                      num(m["pricing"], "completion")) for m in models}


def cost(folder, price):
    """What the scenario's calls cost, from the tokens the app stored and the model's prices."""
    import sqlite3
    if not (folder / "after.db").is_file():
        return 0.0
    db = sqlite3.connect(folder / "after.db")
    uncached, cached, output = db.execute(
        "SELECT COALESCE(SUM(input_tokens), 0), COALESCE(SUM(cache_read_tokens), 0), COALESCE(SUM(output_tokens), 0) "
        "FROM session_messages WHERE session_id NOT LIKE 'demo-%'").fetchone()
    return uncached * price[0] + cached * price[1] + output * price[2]


def mute(outcome, folder):
    """Whether the play timed out without one answer of the AI: the provider, not the model."""
    import sqlite3
    if outcome.get("status") != "timeout" or not (folder / "after.db").is_file():
        return False
    answers = sqlite3.connect(folder / "after.db").execute(
        "SELECT COUNT(*) FROM session_messages WHERE sender = 'AI' AND session_id NOT LIKE 'demo-%'").fetchone()[0]
    return answers == 0


def write_json(path, data):
    """Written whole or not at all: a resumed campaign trusts every verdict it finds."""
    part = path.with_name(path.name + ".part")
    part.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
    os.replace(part, path)


def new_campaign(models, forcings, names):
    """A campaign's folder, with what it plays, so that it can be resumed."""
    unknown = [m for m in models if m not in prices()]
    if unknown:
        sys.exit(f"Unknown OpenRouter models: {unknown}")
    folder = OUT / time.strftime("%Y-%m-%d_%H%M")
    folder.mkdir(parents=True)
    write_json(folder / "campaign.json", {"models": models, "forcings": forcings, "scenarios": names})
    return folder


def progress(folder):
    """(plays with a verdict, plays planned) of a campaign."""
    plan = json.loads((folder / "campaign.json").read_text(encoding="utf-8"))
    plays = [(m, f, n) for m in plan["models"] for f in plan["forcings"] for n in plan["scenarios"]]
    done = sum((folder / model_dir(m) / f / n / "verdict.json").is_file() for m, f, n in plays)
    return done, len(plays)


def unfinished():
    """The latest campaign that has plays left, or None."""
    for folder in sorted(OUT.glob("*/campaign.json"), reverse=True):
        done, total = progress(folder.parent)
        if done < total:
            return folder.parent
    return None


def campaign(folder, key):
    """Plays what [folder]'s campaign plans and has no verdict yet, judged as it goes.

    Each play's verdict lands in its folder once judged; summary.json and summary.md are rewritten
    after each, so an interrupted campaign leaves its table so far and resumes where it stopped.
    """
    plan = json.loads((folder / "campaign.json").read_text(encoding="utf-8"))
    models, forcings, names = plan["models"], plan["forcings"], plan["scenarios"]
    table = prices()
    done, total = progress(folder)
    if done:
        print(f"Reprise de {folder.name} : {done}/{total} déjà joués", flush=True)
    results = []
    for model in models:
        for forcing in forcings:
            for name in names:
                into = folder / model_dir(model) / forcing / name
                verdict = into / "verdict.json"
                if verdict.is_file():
                    results.append(json.loads(verdict.read_text(encoding="utf-8")))
                    continue
                outcome = play(model, name, key, into, forcing)
                # A provider that never answered says nothing of the model: played once more
                if mute(outcome, into):
                    outcome = play(model, name, key, into, forcing)
                if mute(outcome, into):
                    outcome["status"] = "mute"
                try:
                    passed, details = check(name, into) if outcome.get("status") != "error" else (False, [outcome.get("detail")])
                except Exception as e:  # a copy the check cannot read is a failed play, said as such
                    passed, details = False, [f"check failed: {e!r}"]
                spent = cost(into, table[model])
                results.append({"model": model, "forcing": forcing, "scenario": name, "passed": passed,
                                "status": outcome.get("status"), "seconds": round(outcome.get("elapsed_ms", 0) / 1000),
                                "cost": spent, "details": details})
                write_json(verdict, results[-1])
                write_json(folder / "summary.json", results)
                write_table(folder, results, models, forcings, names)
                print(f"{'PASS' if passed else 'FAIL'}  {model:<34} {forcing:<6} {name:<22} "
                      f"{outcome.get('status', ''):<7} {results[-1]['seconds']:>4}s  ${spent:.4f}  {'; '.join(details)}", flush=True)
    write_json(folder / "summary.json", results)
    write_table(folder, results, models, forcings, names)
    print(f"\nTable: {folder / 'summary.md'}")


def mark(result):
    """✓ passed, ✗ failed, — the provider never answered (the model not judged)."""
    return "✓" if result["passed"] else "—" if result["status"] == "mute" else "✗"


def write_table(folder, results, models, forcings, names):
    """Scenario by model and level, the total per column, and what each column cost."""
    columns = [(m, f) for m in models for f in forcings]
    by = {(r["model"], r["forcing"], r["scenario"]): r for r in results}
    lines = ["| scénario | " + " | ".join(f"{m.split('/')[-1]} {f}" for m, f in columns) + " |",
             "|---|" + "---|" * len(columns)]
    for name in names:
        lines.append(f"| {name} | " + " | ".join(
            mark(by[(m, f, name)]) if (m, f, name) in by else "" for m, f in columns) + " |")
    lines.append("| **réussis** | " + " | ".join(
        f"{sum(by[(m, f, n)]['passed'] for n in names if (m, f, n) in by)}/{len(names)}" for m, f in columns) + " |")
    lines.append("| **coût** | " + " | ".join(
        f"${sum(by[(m, f, n)]['cost'] for n in names if (m, f, n) in by):.3f}" for m, f in columns) + " |")
    (folder / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def show(folder):
    """The conversation a scenario left: what the user sent, each answer of the AI, what the app replied."""
    import sqlite3
    db = sqlite3.connect(folder / "after.db")
    for sender, rich, parsed, system in db.execute(
            "SELECT sender, rich_content_json, ai_message_parsed_json, system_message_json FROM session_messages "
            "WHERE session_id NOT LIKE 'demo-%' ORDER BY timestamp"):
        if sender == "AI" and parsed:
            print("AI   ", json.dumps(json.loads(parsed), ensure_ascii=False)[:1500])
        elif sender == "SYSTEM" and system:
            message = json.loads(system)
            print("APP  ", message.get("type"), "-", (message.get("summary") or "")[:300])
        elif sender == "USER":
            print("USER ", (rich or "")[:300])
    result = folder / "result.json"
    if result.is_file():
        print("END  ", result.read_text(encoding="utf-8").replace("\n", " "))
    # What changed, counted by table and tool: rows added, modified, removed
    before = sqlite3.connect(folder / "before.db")
    for table in ("tool_data", "tool_instances", "zones", "variables"):
        owner = "tool_instance_id" if table == "tool_data" else "'-'"
        old = {r[0]: r for r in before.execute(f"SELECT id, {owner}, * FROM {table}")}
        new = {r[0]: r for r in db.execute(f"SELECT id, {owner}, * FROM {table}")}
        counts = {}
        for id_ in old.keys() | new.keys():
            kind = "added" if id_ not in old else "removed" if id_ not in new else "modified" if old[id_] != new[id_] else None
            if kind:
                key = ((new.get(id_) or old[id_])[1], kind)
                counts[key] = counts.get(key, 0) + 1
        for (owner_, kind), n in sorted(counts.items()):
            print(f"DIFF  {table} {owner_} {kind} {n}")
    return 0


def model_dir(model):
    return model.replace("/", "__")


# Campaign 1's models, one per class of machine (docs/design/local-models.md)
CAMPAIGN_MODELS = (
    ("téléphone", "mistralai/ministral-3b-2512"),
    ("portable 32 Go", "qwen/qwen3.6-35b-a3b"),
    ("GPU 24 Go", "google/gemma-4-31b-it"),
    ("serveur 64-128 Go", "openai/gpt-oss-120b"),
    ("hébergé seulement", "deepseek/deepseek-v4-flash"),
)
# For the estimate shown before playing: tokens sent per scenario, measured on the phone
# (one call ≈ 29k tokens) times calls per scenario, assumed
ESTIMATED_TOKENS_PER_PLAY = 3 * 29_000


def ask():
    """The campaign to play: an unfinished one resumed, or models and levels chosen in a menu, the
    estimated cost confirmed first."""
    try:
        import questionary
    except ImportError:
        sys.exit("questionary is missing: sudo apt install python3-questionary\nOr: scripts/bench.py run <model> ...")
    left = unfinished()
    if left is not None:
        done, total = progress(left)
        resume = questionary.select("Campagne", choices=[
            questionary.Choice(f"Reprendre {left.name} ({done}/{total} joués)", True),
            questionary.Choice("Nouvelle campagne", False)]).ask()
        if resume is None:
            return None
        if resume:
            return left
    models = questionary.checkbox("Modèles", choices=[
        questionary.Choice(f"{model}  ({machine})", model, checked=True) for machine, model in CAMPAIGN_MODELS]).ask()
    if not models:
        return None
    forcings = questionary.checkbox("Forçage", choices=[
        questionary.Choice(f, f, checked=f in CAMPAIGN_FORCINGS) for f in ("none", "json", "schema")]).ask()
    if not forcings:
        return None
    table = prices()
    plays = len(SCENARIOS) * len(forcings)
    estimate = sum(plays * ESTIMATED_TOKENS_PER_PLAY * table[m][0] for m in models)
    print(f"{plays * len(models)} scénarios joués, environ {plays * len(models) * 45 // 60} min, "
          f"coût estimé ≈ {estimate:.2f} $ (au plus, sans le cache)")
    if not questionary.confirm("Lancer ?", default=False).ask():
        return None
    return new_campaign(models, forcings, list(SCENARIOS))


def main(argv):
    if not argv:
        folder = ask()
        if folder is None:
            return 0
        key = api_key()
        prepare_device()
        campaign(folder, key)
        return 0
    if argv[0] == "show":
        return show(Path(argv[1]))
    if argv[0] == "resume":
        folder = Path(argv[1]) if len(argv) > 1 else unfinished()
        if folder is None:
            sys.exit("No unfinished campaign under tmp/bench/")
        key = api_key()
        prepare_device()
        campaign(folder, key)
        return 0
    if argv[0] not in ("play", "run"):
        print(__doc__)
        return 2
    key = api_key()
    prepare_device()
    if argv[0] == "play":
        model, name = argv[1], argv[2]
        forcing = argv[3] if len(argv) > 3 else FORCING
        into = OUT / "play" / model_dir(model) / f"{name}-{forcing}"
        result = play(model, name, key, into, forcing)
        print(json.dumps(result, indent=2, ensure_ascii=False))
        print(f"Files: {into}")
        return 0
    args = argv[1:]
    forcings, names = list(CAMPAIGN_FORCINGS), list(SCENARIOS)
    while args and args[0].startswith("--"):
        option, value = args[0], args[1]
        args = args[2:]
        if option == "--forcing":
            forcings = value.split(",")
        elif option == "--only":
            names = value.split(",")
            unknown = [n for n in names if n not in SCENARIOS]
            if unknown:
                sys.exit(f"Unknown scenarios: {unknown}")
        else:
            sys.exit(f"Unknown option {option}")
    if not args:
        sys.exit("Name at least one model")
    campaign(new_campaign(args, forcings, names), key)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
