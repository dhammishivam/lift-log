"""Reads the body path data out of react-native-body-highlighter's asset files as plain text (no code is run)."""
import re, json, sys
def parse(path):
    t = open(path, encoding="utf-8").read()
    out = []
    # each part: { slug: "chest", ..., path: { left: [...], right: [...], common: [...] } }
    for m in re.finditer(r'slug:\s*"([^"]+)"(.*?)(?=slug:\s*"|\Z)', t, re.S):
        slug, body = m.group(1), m.group(2)
        parts = {}
        for side in ("common", "left", "right"):
            sm = re.search(side + r':\s*\[(.*?)\]', body, re.S)
            if sm: parts[side] = re.findall(r'"([^"]+)"', sm.group(1))
        out.append({"slug": slug, "path": parts})
    return out
src = sys.argv[1]
json.dump({"front": parse(src + "/bodyFront.js"), "back": parse(src + "/bodyBack.js")}, open(sys.argv[2], "w"))
