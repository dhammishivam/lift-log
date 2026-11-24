"""Builds the extended exercise library from free-exercise-db (public domain, github.com/yuhonas/free-exercise-db).

Names are rewritten in Hevy's style ("Hip Thrust (Barbell)") so Hevy imports link to them, entries that duplicate the
core library are dropped, and the instructions become short form cues.
Writes shared/exlib.txt (name|muscle|equipment|type|cue). Run tools/exlib_sync.py afterwards.
"""
import json, re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
db = json.load(open(ROOT / "shared/free-exercise-db.json"))
lib = (ROOT / "composeApp/src/commonMain/kotlin/app/liftlog/model/Library.kt").read_text()
core_txt = lib[lib.index('"""') + 3: lib.index('"""', lib.index('"""') + 3)]
core = [l.split("|") for l in core_txt.strip().split("\n")]

MUSCLE = {"quadriceps": "Quads", "shoulders": "Shoulders", "abdominals": "Abs", "chest": "Chest", "hamstrings": "Hamstrings",
          "triceps": "Triceps", "biceps": "Biceps", "lats": "Back", "middle back": "Back", "lower back": "Back", "calves": "Calves",
          "forearms": "Forearms", "glutes": "Glutes", "traps": "Traps", "adductors": "Adductors", "neck": "Neck", "abductors": "Glutes"}
EQUIP = {"barbell": "Barbell", "dumbbell": "Dumbbell", "cable": "Cable", "machine": "Machine", "body only": "Bodyweight",
         "kettlebells": "Kettlebell", "bands": "Band", "medicine ball": "Medicine Ball", "exercise ball": "Swiss Ball",
         "e-z curl bar": "EZ Bar", "other": "Other", None: "Other"}
# Words that name the equipment, removed from the name because the equipment goes in brackets.
EQWORDS = {"Barbell": [r"barbells?"], "Dumbbell": [r"dumbbells?", r"\bdb\b"], "Cable": [r"cables?", r"pulley"],
           "Machine": [r"\bmachine\b", r"\bleverage\b", r"\blever\b"], "Kettlebell": [r"kettlebells?", r"\bkb\b"], "Band": [r"(with |using )?(resistance )?bands?"],
           "Medicine Ball": [r"medicine balls?", r"med ball"], "Swiss Ball": [r"(on |with )?(an )?(exercise|stability|swiss) balls?"],
           "EZ Bar": [r"e-?z[ -]?(curl )?bar", r"ez[ -]?bar"]}
SING = {"rows": "row", "curls": "curl", "raises": "raise", "presses": "press", "flyes": "fly", "flys": "fly", "flies": "fly",
        "extensions": "extension", "squats": "squat", "lunges": "lunge", "pulldowns": "pulldown", "shrugs": "shrug",
        "crunches": "crunch", "kickbacks": "kickback", "dips": "dip", "pushdowns": "pushdown", "deadlifts": "deadlift",
        "pullups": "pull up", "pull-ups": "pull up", "push-ups": "push up", "pushups": "push up", "chin-ups": "chin up",
        "situps": "sit up", "sit-ups": "sit up", "thrusts": "thrust", "swings": "swing", "jacks": "jack", "jumps": "jump",
        "snatches": "snatch", "cleans": "clean", "twists": "twist", "rollouts": "rollout", "hyperextensions": "hyperextension",
        "pullovers": "pullover", "steps": "step", "step-ups": "step up", "lifts": "lift", "pulls": "pull", "walks": "walk"}
SMALL = {"a", "an", "and", "the", "of", "on", "to", "with", "in", "from", "for", "or", "off", "over", "up", "at", "by"}
SKIP_EQ = {"foam roll"}

def title(s):
    out = []
    for i, w in enumerate(s.split(" ")):
        if not w: continue
        lw = w.lower()
        if i and lw in SMALL and lw not in ("up",): out.append(lw)
        elif re.fullmatch(r"[a-z]+(-[a-z]+)*", lw): out.append("-".join(p.capitalize() for p in lw.split("-")))
        else: out.append(w[0].upper() + w[1:])
    return " ".join(out)

def tidy(name, eq):
    n = name.replace("’", "'").replace("–", "-")
    smith = bool(re.search(r"smith", n, re.I))
    if smith: n = re.sub(r"smith machine|smith", "", n, flags=re.I); eq = "Smith Machine"
    for p in EQWORDS.get(eq, []): n = re.sub(p, " ", n, flags=re.I)
    n = re.sub(r"\s+-\s*|\s*-\s+", " - ", n)  # a spaced dash separates a variant; "Single-Arm" stays one word
    n = re.sub(r"\bside lateral\b", "Lateral", n, flags=re.I)
    n = re.sub(r"\s+", " ", n).strip(" -,")
    n = re.sub(r"^(with|using) ", "", n, flags=re.I)
    n = re.sub(r" (with|using|on)$", "", n, flags=re.I)
    ws = n.split(" ")
    ws = [SING.get(w.lower(), w) for w in ws]
    n = title(" ".join(ws))
    n = n.replace("Push-Up", "Push Up").replace("Pull-Up", "Pull Up").replace("Chin-Up", "Chin Up").replace("Sit-Up", "Sit Up")
    n = n.replace("One-Arm", "Single Arm").replace("One Arm", "Single Arm").replace("One-Leg", "Single Leg").replace("One Leg", "Single Leg")
    n = re.sub(r"\s+", " ", n).strip(" -")
    return n, eq

def key(name):
    n = name.lower().replace("(", " ").replace(")", " ")
    n = re.sub(r"[^a-z0-9 ]", " ", n)
    ws = [{"triceps": "tricep", "biceps": "bicep", "one": "single", "legged": "leg"}.get(w, SING.get(w, w)) for w in n.split() if w not in SMALL]
    ws = [w for w in " ".join(ws).split()]
    return " ".join(sorted(ws))

def kind(x, name, eq):
    n = name.lower()
    if x["category"] == "cardio":
        return "cd" if re.search(r"run|treadmill|bik|cycl|row|elliptical|walk|jog|stair|swim|ski", n) else "dur"
    if re.search(r"plank|\bhold\b|isometric|wall sit|dead hang|l-sit|static", n): return "dur"
    if x["category"] == "strongman" and re.search(r"drag|sled|walk|push|carry", n) and eq == "Other": return "dur"
    if eq == "Bodyweight": return "wbw" if "weighted" in n else "bw"
    if eq in ("Band", "Other", "Swiss Ball") and not re.search(r"press|curl|row|squat|deadlift|carry|throw|toss", n): return "bw"
    return "wr"

def cue(steps):
    out = []
    for s in steps:
        s = re.sub(r"\s*This will be your starting position\.?", "", s, flags=re.I).strip()
        s = re.sub(r"\s+", " ", s)
        if not s or re.search(r"repeat for the recommended|recommended amount of rep|repeat (the movement|for the)", s, re.I): continue
        for sent in re.split(r"(?<=[.!?])\s+", s):
            if sent and not re.search(r"recommended amount|starting position", sent, re.I): out.append(sent)
        if sum(len(o) for o in out) > 220: break
    txt = ""
    for o in out:
        if len(txt) + len(o) > 260: break
        txt = (txt + " " + o).strip()
    return txt.replace("|", "/")

have = {key(c[0]) for c in core}
rows, seen = [], set(have)
for x in sorted(db, key=lambda x: x["name"]):
    if x["category"] == "stretching" or x.get("equipment") in SKIP_EQ: continue
    eq = EQUIP.get(x.get("equipment"), "Other")
    name, eq = tidy(x["name"], eq)
    if len(name) < 3: continue
    full = name if eq in ("Bodyweight", "Other") else f"{name} ({eq})"
    k = key(full)
    if k in seen or key(name) in seen: continue
    seen.add(k)
    pm = (x["primaryMuscles"] or ["other"])[0]
    mus = "Cardio" if x["category"] == "cardio" else MUSCLE.get(pm, "Other")
    rows.append([full, mus, eq, kind(x, full, eq), cue(x.get("instructions") or [])])

out = ROOT / "shared/exlib.txt"
out.write_text("\n".join("|".join(r) for r in rows) + "\n")
print(len(core), "core +", len(rows), "new =", len(core) + len(rows), "→", out)
