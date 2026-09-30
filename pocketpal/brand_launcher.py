"""Add a small Jarvis spark badge to PocketPal's Android launcher artwork.

The upstream images remain the base of every density and round variant.
Run after applying jarvis-pocketpal.patch in a reconstructed checkout.
"""

from pathlib import Path
import sys

from PIL import Image, ImageDraw


def brand(path: Path) -> None:
    with Image.open(path) as source:
        icon = source.convert("RGBA")

    size = min(icon.size)
    scale = size / 108.0
    cx, cy = round(size * 0.73), round(size * 0.73)
    radius = round(size * 0.15)
    ring = max(1, round(2.5 * scale))
    canvas = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse(
        (cx - radius - ring, cy - radius - ring,
         cx + radius + ring, cy + radius + ring),
        fill="#102b2b",
    )
    draw.ellipse(
        (cx - radius, cy - radius, cx + radius, cy + radius),
        fill="#31d9b3",
    )
    reach = radius * 0.70
    waist = max(1, radius * 0.17)
    draw.polygon(
        [(cx, cy - reach), (cx + waist, cy - waist),
         (cx + reach, cy), (cx + waist, cy + waist),
         (cx, cy + reach), (cx - waist, cy + waist),
         (cx - reach, cy), (cx - waist, cy - waist)],
        fill="#ffffff",
    )
    icon = Image.alpha_composite(icon, canvas)
    icon.save(path, format="PNG", optimize=True)


def main(root: Path) -> None:
    paths = sorted((root / "android/app/src/main/res").glob("mipmap-*/ic_launcher*.png"))
    if len(paths) != 10:
        raise RuntimeError(f"Expected 10 launcher images, found {len(paths)}")
    for path in paths:
        brand(path)
        print(path.relative_to(root))


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else "."))
