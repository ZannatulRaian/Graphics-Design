from PIL import Image
import numpy as np

IN_PATH = r"C:\Users\Zannatul Rayanh\.cursor\projects\f-Final-part-2\assets\c__Users_Zannatul_Rayanh_AppData_Roaming_Cursor_User_workspaceStorage_bb3df789a4761395dc15be7551d43c72_images_Screenshot_2026-03-16_134422-b1dc0fb5-a771-4882-9f7f-ff6d695fadf4.png"
OUT_PATH = r"F:\Final part 2\assets\screenshot_cleaned.png"

img = Image.open(IN_PATH).convert("RGB")
a = np.array(img)
R, G, B = a[...,0], a[...,1], a[...,2]

# Detect the painted blue markup (bright saturated blue/cyan)
mask = (
    (B > 150) & (G > 70) & (R < 120) &
    ((B - R) > 80) & ((B - G) > 20)
)
# Also catch darker edges of the same stroke
mask |= ((B > 120) & (G > 60) & (R < 100) & ((B - R) > 60))

# Simple dilation to cover stroke thickness
m = mask.astype(np.uint8)
for _ in range(3):
    p = np.pad(m, 1, mode='edge')
    m = (
        p[:-2,:-2] | p[:-2,1:-1] | p[:-2,2:] |
        p[1:-1,:-2] | p[1:-1,1:-1] | p[1:-1,2:] |
        p[2:,:-2] | p[2:,1:-1] | p[2:,2:]
    ).astype(np.uint8)
mask = m.astype(bool)

h, w = a.shape[:2]

# Sample a dark-blue background color from the right panel (avoid mask)
x1, x2 = int(w*0.55), int(w*0.85)
y1, y2 = int(h*0.05), int(h*0.85)
region = a[y1:y2, x1:x2]
region_mask = mask[y1:y2, x1:x2]

candidates = region[~region_mask]
if candidates.size < 1000:
    candidates = a[~mask]

bg = np.median(candidates.reshape(-1,3), axis=0).astype(np.uint8)

out = a.copy()
out[mask] = bg

Image.fromarray(out).save(OUT_PATH)
print('saved', OUT_PATH)
print('bg', tuple(int(x) for x in bg))
