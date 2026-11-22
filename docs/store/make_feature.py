"""Play Store feature graphic (1024 x 500): the dumbbell mark, the name and one line."""
import sys
from PIL import Image, ImageDraw, ImageFont
import make_icon as mi  # reuses the icon geometry

W, H, SS = 1024, 500, 4
BG, BLUE, INK, MUTED = (13, 14, 16), (61, 132, 255), (244, 245, 247), (150, 156, 166)
im = Image.new("RGB", (W * SS, H * SS), BG)
d = ImageDraw.Draw(im)

# Dumbbell on the left, 300 px wide, vertically centred.
gw = 300 / (896 - 128)
ox, oy = 92, H / 2 - 512 * gw
for p in getattr(mi.glyph, "geoms", [mi.glyph]):
    d.polygon([((ox + (x - 128) * gw) * SS, (oy + y * gw) * SS) for x, y in p.exterior.coords], fill=BLUE)

F = "/usr/share/fonts/opentype/inter/"
title = ImageFont.truetype(F + "Inter-Bold.otf", 84 * SS)
sub = ImageFont.truetype(F + "Inter-Medium.otf", 30 * SS)
tx = 460
d.text((tx * SS, 182 * SS), "Lift Log", font=title, fill=INK)
d.text((tx * SS, 290 * SS), "Simple, private workout tracking", font=sub, fill=MUTED)
im.resize((W, H), Image.LANCZOS).save("feature-1024x500.png")
print("saved")
