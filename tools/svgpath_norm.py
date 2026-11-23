"""Rewrites SVG path data with explicit separators (e.g. arc flags written as "01.67" → "0 1 .67"),
so every path parser reads it the same way."""
import re
CMD = re.compile(r"[MmZzLlHhVvCcSsQqTtAa]")
NUM = re.compile(r"[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?")
ARGS = {"m": 2, "z": 0, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7}
def norm(d: str) -> str:
    out, i, cmd = [], 0, None
    while i < len(d):
        ch = d[i]
        if ch in " ,\n\t": i += 1; continue
        if CMD.match(ch): cmd = ch; out.append(ch); i += 1; continue
        n = ARGS[cmd.lower()]
        k = 0
        while k < n:
            while i < len(d) and d[i] in " ,\n\t": i += 1
            if cmd.lower() == "a" and k in (3, 4):  # flags: a single 0 or 1
                out.append(d[i]); i += 1
            else:
                m = NUM.match(d, i); out.append(m.group(0)); i = m.end()
            k += 1
        # implicit repeats continue with the same command (moveto repeats become lineto, which is fine to keep as is)
    s = ""
    for t in out:
        s += t if CMD.match(t) else (" " + t if s and not CMD.match(s[-1]) else t)
    return s
if __name__ == "__main__":
    assert norm("M1 2a.84.84 0 01.67.65") == "M1 2a.84 .84 0 0 1 .67 .65", norm("M1 2a.84.84 0 01.67.65")
    print("ok")
