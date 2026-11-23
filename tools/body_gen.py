import json, re
W=200
def mir(d):
    # mirror path x coords: x -> W - x. Path uses absolute commands M,C,L,Q,Z with "x,y" pairs.
    def f(m): return f"{W-float(m.group(1)):g},{m.group(2)}"
    return re.sub(r"(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)", f, d)
# Left-half shapes (viewer's left), mirrored for the other side.
SIL_L = ("M100,8 C88,8 82,18 82,30 C82,40 86,48 91,52 L91,60 C84,64 72,66 62,70 C52,74 46,82 45,94 "
         "C44,110 44,124 42,140 C40,156 36,176 33,196 C31,212 30,226 31,236 C29,242 27,250 30,256 C34,258 38,252 40,246 "
         "C42,236 44,226 47,214 C51,196 54,178 57,160 C59,148 61,136 63,124 C64,140 66,156 67,172 C67,186 66,198 65,210 "
         "C62,236 63,262 67,290 C68,300 69,310 69,320 C68,344 70,366 74,386 C74,394 70,402 72,408 C78,412 88,412 92,408 "
         "C92,398 90,390 90,382 C92,360 93,338 92,316 C93,304 94,294 95,282 C96,260 97,240 99,222 L100,222")
def full(left):
    # left half from top-center going down the outside and back up the inner leg to the crotch; mirror and join.
    right = mir(left)
    # reverse right path: simplest is to draw both halves as separate filled shapes closing at the centre line.
    return left + " L100,8 Z", right + " L100,8 Z"
F = {  # front muscles, left half
 "Shoulders": ["M66,72 C56,74 49,82 48,94 C48,102 50,108 53,112 C57,102 62,94 70,88 C74,84 73,76 66,72 Z"],
 "Chest": ["M98,80 C90,76 78,78 71,86 C64,93 62,104 65,112 C73,120 89,122 98,117 Z"],
 "Biceps": ["M54,114 C49,126 47,140 49,154 C53,158 59,156 61,150 C63,137 63,124 60,112 Z"],
 "Forearms": ["M49,160 C43,174 39,192 38,214 C41,218 45,218 47,214 C51,198 56,182 57,162 C55,158 51,158 49,160 Z"],
 "Abs": ["M89,124 L98,123 L98,138 L89,139 C88,134 88,128 89,124 Z", "M89,142 L98,141 L98,156 L89,157 Z", "M89,160 L98,159 L98,174 L89,175 Z",
         "M89,178 L98,177 L98,204 C94,200 91,192 89,184 Z",
         "M68,118 C70,140 72,164 76,190 C80,188 84,182 86,174 L86,126 C80,122 74,120 68,118 Z"],
 "Quads": ["M70,214 C64,236 64,264 70,292 C73,296 76,297 78,296 C77,270 77,240 80,214 C77,212 73,212 70,214 Z",
           "M82,214 C79,240 80,270 82,294 C85,296 88,294 89,290 C92,270 94,246 93,226 C90,218 86,214 82,214 Z",
           "M90,272 C95,278 96,289 92,298 C87,298 85,288 90,272 Z"],
 "Adductors": ["M95,218 C98,224 99,240 98,256 C95,250 92,238 91,226 C92,222 93,220 95,218 Z"],
 "Calves": ["M74,314 C70,332 71,354 76,374 C79,376 82,374 83,370 C84,350 84,330 82,314 C80,310 76,310 74,314 Z",
            "M86,318 C89,332 89,350 86,364 C84,350 84,334 86,318 Z"],
}
B = {  # back muscles, left half
 "Traps": ["M100,50 C94,56 82,64 66,72 C78,78 90,86 99,106 L100,106 Z"],
 "Shoulders": ["M64,74 C55,77 49,84 48,96 C48,104 50,110 53,114 C57,104 62,96 69,90 C72,84 70,77 64,74 Z"],
 "Back": ["M98,106 C92,98 84,92 74,90 C76,98 82,106 90,112 C94,114 97,114 98,112 Z",
          "M72,92 C65,100 63,112 65,126 C68,144 76,162 88,176 L94,170 C92,156 92,140 96,124 C90,118 80,108 72,92 Z",
          "M96,128 C93,144 92,162 94,180 C95,188 97,194 99,198 L99,126 Z"],
 "Triceps": ["M54,116 C49,128 47,142 49,156 C53,160 59,158 61,152 C63,138 63,126 60,114 Z"],
 "Forearms": ["M49,162 C43,176 39,194 38,214 C41,218 45,218 47,214 C51,198 56,184 57,164 C55,160 51,160 49,162 Z"],
 "Glutes": ["M99,204 C87,199 73,203 68,216 C65,230 71,242 84,244 C92,244 98,238 99,230 Z"],
 "Hamstrings": ["M69,250 C65,268 67,288 73,302 C76,304 79,304 81,302 C80,286 80,266 81,251 C77,251 73,251 69,250 Z",
                "M83,251 C82,268 82,286 84,302 C87,304 90,302 91,298 C95,278 95,260 93,248 C90,250 87,251 83,251 Z"],
 "Calves": ["M75,310 C69,322 69,342 75,354 C80,352 83,342 84,328 C84,318 81,310 75,310 Z",
            "M87,312 C91,322 93,336 89,352 C85,346 84,330 86,318 Z"],
}
out={"sil":[SIL_L, mir(SIL_L)],"front":{},"back":{}}
for view,D in (("front",F),("back",B)):
    for m,ps in D.items():
        out[view][m]=ps+[mir(p) for p in ps if "L98," not in p and "L99," not in p or m in("Abs",)]
# abs blocks and centre shapes touching centre: mirror them too (they're left halves of pairs)
for view,D in (("front",F),("back",B)):
    for m,ps in D.items():
        out[view][m]=ps+[mir(p) for p in ps]
json.dump(out,open("body.json","w"),indent=1)
def svg(view, cnt, fill="#3b82f6"):
    mx=max(cnt.values()) if cnt else 1
    s=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 420" width="260">'
    s+=''.join(f'<path d="{d}" fill="#2a2d33"/>' for d in out["sil"])
    for m,ps in out[view].items():
        v=cnt.get(m,0); op=0.18 if v==0 else 0.35+0.65*v/mx
        col="#5b6270" if v==0 else fill
        s+=''.join(f'<path d="{d}" fill="{col}" fill-opacity="{op}" stroke="#15171b" stroke-width="0.8"/>' for d in ps)
    return s+'</svg>'
c={"Chest":10,"Shoulders":6,"Triceps":5,"Back":8,"Quads":9,"Abs":3,"Calves":2,"Biceps":4,"Glutes":5,"Hamstrings":4,"Traps":2}
open("preview.html","w").write('<body style="background:#111;display:flex;gap:20px;padding:20px">'+svg("front",c)+svg("back",c)+'</body>')
