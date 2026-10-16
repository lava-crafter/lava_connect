# Application icon

`icon.png` is the user-provided 512×512 temporary source artwork. Android debug
and release share this icon; adaptive launchers crop it to their system mask.

Regenerate the Android density variants, Linux desktop PNG and tray pixmap with
`bash scripts/generate-icons.sh` (requires ImageMagick and Python 3). Normal builds
use the checked-in resources and do not require these image-generation tools.
