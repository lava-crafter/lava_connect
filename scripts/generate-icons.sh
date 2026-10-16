#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$ROOT/assets/icon.png"
RES="$ROOT/android/app/src/main/res"
DESKTOP="$ROOT/linux/packaging/desktop"
command -v magick >/dev/null || { echo 'ImageMagick is required to regenerate icons' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'Python 3 is required to generate the tray pixmap' >&2; exit 1; }
test -f "$SOURCE" || { echo "Missing icon source: $SOURCE" >&2; exit 1; }
for item in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
    density="${item%:*}"
    size="${item#*:}"
    mkdir -p "$RES/mipmap-$density"
    magick "$SOURCE" -strip -resize "${size}x${size}" \
        "$RES/mipmap-$density/ic_launcher.png"
done
mkdir -p "$RES/drawable-nodpi" "$DESKTOP"
magick "$SOURCE" -strip -resize 512x512 "$RES/drawable-nodpi/ic_launcher_artwork.png"
cp "$RES/drawable-nodpi/ic_launcher_artwork.png" "$DESKTOP/lava-connect.png"
# StatusNotifierItem uses raw 8-bit ARGB in network byte order, not encoded PNG.
magick "$SOURCE" -strip -resize 32x32 -alpha on -depth 8 RGBA:- | \
    python3 -c '
import pathlib, sys
rgba = sys.stdin.buffer.read()
if len(rgba) != 32 * 32 * 4:
    raise SystemExit("Unexpected tray image size")
argb = bytearray(len(rgba))
argb[0::4], argb[1::4], argb[2::4], argb[3::4] = rgba[3::4], rgba[0::4], rgba[1::4], rgba[2::4]
pathlib.Path(sys.argv[1]).write_bytes(argb)
' "$DESKTOP/lava-connect-tray.argb"
printf 'Generated Android, Linux desktop and tray icons from %s\n' "$SOURCE"
