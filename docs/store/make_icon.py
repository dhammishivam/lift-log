"""Lift Log icon: a plain dumbbell, from the Gemini design (measured on its 1024 px canvas) with the tick removed."""
# Usage: python3 make_icon.py <foreground.xml> <icon-512.png> <preview-1024.png>
from shapely.geometry import LineString, box
from shapely.ops import unary_union
from PIL import Image, ImageDraw
import sys

R = 6  # corner radius measured on the original
# (x0, y0, x1, y1) on the 1024 canvas, symmetric about x = 511.5
rects = [
    (128, 473, 166, 551),   # left end stub
    (166, 389, 244, 635),   # left outer plate
    (256, 341, 342, 683),   # left inner plate
    (336, 486, 688, 538),   # bar: slimmer and joined to the inner plates
    (682, 341, 768, 683),   # right inner plate
    (780, 389, 858, 635),   # right outer plate
    (858, 473, 896, 551),   # right end stub
]

def rounded(x0, y0, x1, y1, r=R):
    return box(x0 + r, y0 + r, x1 - r, y1 - r).buffer(r, quad_segs=4)

shapes = [rounded(*r) for r in rects]
glyph = unary_union(shapes)

def poly_path(p):
    out = []
    for ring in [p.exterior, *p.interiors]:
        pts = list(ring.coords)[:-1]
        out.append("M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + "Z")
    return "".join(out)

def path_data(g):
    geoms = getattr(g, "geoms", [g])
    return "".join(poly_path(p) for p in geoms)

pd = path_data(glyph)
# Adaptive icon: 108dp canvas, keep the glyph inside the 66dp safe zone (scale 0.8 about the centre).
fg = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Lift Log mark: a plain dumbbell, from a Gemini design redrawn as vectors. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="1024" android:viewportHeight="1024">
    <group android:scaleX="0.8" android:scaleY="0.8" android:pivotX="512" android:pivotY="512">
        <path android:fillColor="#3D84FF" android:fillType="evenOdd"
            android:pathData="{pd}" />
    </group>
</vector>
'''

def render(size, scale_glyph, bg=(13, 14, 16), fg=(61, 132, 255), ss=4):
    S = size * ss
    im = Image.new("RGB", (S, S), bg)
    d = ImageDraw.Draw(im)
    k = S / 1024
    c = 512
    def tf(x, y):
        return ((c + (x - c) * scale_glyph) * k, (c + (y - c) * scale_glyph) * k)
    for p in getattr(glyph, "geoms", [glyph]):
        d.polygon([tf(*q) for q in p.exterior.coords], fill=fg)
        for hole in p.interiors:
            d.polygon([tf(*q) for q in hole.coords], fill=bg)
    return im.resize((size, size), Image.LANCZOS)

if __name__ == "__main__":
    open(sys.argv[1], "w").write(fg)
    render(512, 0.8).save(sys.argv[2])   # Play Store icon (Play rounds the corners itself)
    render(1024, 1.0).save(sys.argv[3])  # preview at the original framing
    print(len(pd), "chars of path data")
