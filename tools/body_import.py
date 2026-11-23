"""Builds shared/body.json from react-native-body-highlighter's male front/back artwork (MIT, © 2022 ELABBASSI Hicham).

  npm pack react-native-body-highlighter && tar xzf react-native-body-highlighter-*.tgz
  python3 -I tools/body_extract.py package/dist/assets /tmp/raw.json      # reads the paths as text, runs nothing
  python3 -I tools/body_import.py /tmp/raw.json /tmp/bbox.json           # bbox.json: [x,y,w,h] per path, same order
  python3 tools/sync_body.py

Each artwork part is mapped to one of Lift Log's regions (logic/Muscles.kt REGIONS); parts that aren't muscles
(head, hands, knees, feet…) become the base figure. Sub-parts are told apart by size: the tall upper-back piece is
the lats, the small ones the upper back; the small gluteal piece is glute medius (abductors), and so on.
"""
import json, sys
raw = json.load(open(sys.argv[1])); boxes = json.load(open(sys.argv[2]))
BASE = {"head", "hair", "neck", "knees", "hands", "ankles", "feet"}
SIMPLE = {"chest": "Chest", "obliques": "Obliques", "abs": "Abs", "biceps": "Biceps", "triceps": "Triceps", "trapezius": "Traps",
          "adductors": "Adductors", "quadriceps": "Quads", "tibialis": "Calves", "calves": "Calves", "forearm": "Forearms",
          "hamstring": "Hamstrings"}
out = {"front": {"vb": [0, 0, 724, 1448], "base": [], "regions": {}}, "back": {"vb": [724, 0, 724, 1448], "base": [], "regions": {}}}
k = 0
for view in ("front", "back"):
    for part in raw[view]:
        for side in ("common", "left", "right"):
            for d in part["path"].get(side, []):
                x, y, w, h = boxes[k]; k += 1
                s = part["slug"]
                if s in BASE: out[view]["base"].append(d); continue
                if s == "deltoids": r = "Front delts" if view == "front" else "Rear delts"
                elif s == "upper-back": r = "Lats" if h > 150 else "Upper back"
                elif s == "lower-back": r = "Lower back" if h > 100 else "Obliques"
                elif s == "gluteal": r = "Glutes" if h > 100 else "Abductors"
                else: r = SIMPLE[s]
                out[view]["regions"].setdefault(r, []).append(d)
out["credit"] = "Body map artwork: react-native-body-highlighter, © 2022 ELABBASSI Hicham, MIT licence."
json.dump(out, open("shared/body.json", "w"), indent=1)
print({v: {r: len(p) for r, p in out[v]["regions"].items()} for v in ("front", "back")})
