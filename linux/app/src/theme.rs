// SPDX-License-Identifier: GPL-3.0-or-later
//! Port of lava_note's `LavaColorScheme.kt` (also used by the Android client).
//!
//! Most roles mix a fixed neutral with the accent. Compose's `lerp` mixes in
//! Oklab, so the same is done here; `design/palette-fixture.json` pins the
//! expected output for both implementations.

/// Accent presets shared with Android (`ui/AppearancePreferences.kt`).
pub const ACCENT_PRESETS: [u32; 5] = [0x1976D2, 0x42A5F5, 0x3F51B5, 0x0097A7, 0x0D47A1];

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Rgb(pub f32, pub f32, pub f32);

impl Rgb {
    pub const fn hex(value: u32) -> Self {
        Rgb(
            ((value >> 16) & 0xff) as f32 / 255.0,
            ((value >> 8) & 0xff) as f32 / 255.0,
            (value & 0xff) as f32 / 255.0,
        )
    }

    pub fn to_u8(self) -> [u8; 3] {
        let channel = |v: f32| (v.clamp(0.0, 1.0) * 255.0 + 0.5) as u8;
        [channel(self.0), channel(self.1), channel(self.2)]
    }
}

const WHITE: Rgb = Rgb::hex(0xFFFFFF);
const BLACK: Rgb = Rgb::hex(0x000000);
const LIGHT_SURFACE: Rgb = Rgb::hex(0xF7F7F8);
const DARK_SURFACE: Rgb = Rgb::hex(0x1F1F21);

fn to_linear(c: f32) -> f32 {
    if c <= 0.04045 {
        c / 12.92
    } else {
        ((c + 0.055) / 1.055).powf(2.4)
    }
}

fn from_linear(c: f32) -> f32 {
    if c <= 0.003_130_8 {
        c * 12.92
    } else {
        1.055 * c.powf(1.0 / 2.4) - 0.055
    }
}

fn to_oklab(c: Rgb) -> [f32; 3] {
    let (r, g, b) = (to_linear(c.0), to_linear(c.1), to_linear(c.2));
    let l = (0.412_221_46 * r + 0.536_332_55 * g + 0.051_445_995 * b).cbrt();
    let m = (0.211_903_5 * r + 0.680_699_5 * g + 0.107_396_96 * b).cbrt();
    let s = (0.088_302_46 * r + 0.281_718_85 * g + 0.629_978_7 * b).cbrt();
    [
        0.210_454_26 * l + 0.793_617_8 * m - 0.004_072_047 * s,
        1.977_998_5 * l - 2.428_592_2 * m + 0.450_593_7 * s,
        0.025_904_037 * l + 0.782_771_77 * m - 0.808_675_77 * s,
    ]
}

fn from_oklab([lightness, a, b]: [f32; 3]) -> Rgb {
    let l = (lightness + 0.396_337_78 * a + 0.215_803_76 * b).powi(3);
    let m = (lightness - 0.105_561_346 * a - 0.063_854_17 * b).powi(3);
    let s = (lightness - 0.089_484_18 * a - 1.291_485_5 * b).powi(3);
    Rgb(
        from_linear(4.076_741_7 * l - 3.307_711_6 * m + 0.230_969_94 * s),
        from_linear(-1.268_438 * l + 2.609_757_4 * m - 0.341_319_38 * s),
        from_linear(-0.004_196_086_3 * l - 0.703_418_6 * m + 1.707_614_7 * s),
    )
}

/// `accentOverlay(base, accent, amount)` from LavaColorScheme.kt.
pub fn mix(base: Rgb, other: Rgb, amount: f32) -> Rgb {
    let t = amount.clamp(0.0, 1.0);
    let (a, b) = (to_oklab(base), to_oklab(other));
    from_oklab([
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t,
    ])
}

macro_rules! schemes {
    ($accent:ident; $($role:ident: $light:expr, $dark:expr;)*) => {
        /// Material 3 color roles used by the Slint UI (`ui/theme.slint`).
        #[derive(Clone, Copy, Debug)]
        pub struct Scheme { $(pub $role: Rgb,)* }

        impl Scheme {
            #[allow(non_snake_case)]
            pub fn new(dark: bool, accent: Rgb) -> Self {
                let $accent = accent;
                if dark {
                    Scheme { $($role: $dark,)* }
                } else {
                    Scheme { $($role: $light,)* }
                }
            }

            pub fn to_slint(self) -> crate::LavaColors {
                crate::LavaColors { $($role: slint_color(self.$role),)* }
            }

            /// Roles in camelCase, matching Compose's `ColorScheme` property names.
            #[cfg(test)]
            pub fn roles(&self) -> Vec<(String, Rgb)> {
                vec![$((camel(stringify!($role)), self.$role),)*]
            }
        }
    };
}

schemes! {
    A;
    primary: A, A;
    on_primary: WHITE, WHITE;
    primary_container: mix(WHITE, A, 0.18), mix(DARK_SURFACE, A, 0.38);
    on_primary_container: mix(BLACK, A, 0.32), mix(WHITE, A, 0.12);
    secondary: mix(A, BLACK, 0.08), mix(A, WHITE, 0.28);
    on_secondary: WHITE, Rgb::hex(0x002F4A);
    secondary_container: mix(WHITE, A, 0.14), mix(DARK_SURFACE, A, 0.25);
    on_secondary_container: mix(BLACK, A, 0.28), mix(WHITE, A, 0.10);
    tertiary: Rgb::hex(0x0277BD), Rgb::hex(0x81D4FA);
    on_tertiary: WHITE, Rgb::hex(0x003547);
    tertiary_container: Rgb::hex(0xCBEAFF), Rgb::hex(0x0B4054);
    on_tertiary_container: Rgb::hex(0x001E2B), Rgb::hex(0xCBEAFF);
    surface: LIGHT_SURFACE, DARK_SURFACE;
    on_surface: Rgb::hex(0x171C22), Rgb::hex(0xE6E8EC);
    surface_variant: mix(LIGHT_SURFACE, A, 0.10), mix(DARK_SURFACE, A, 0.08);
    on_surface_variant: Rgb::hex(0x414852), Rgb::hex(0xC1C7D0);
    surface_dim: mix(LIGHT_SURFACE, BLACK, 0.05), Rgb::hex(0x1B1B1D);
    surface_bright: Rgb::hex(0xFCFCFD), mix(DARK_SURFACE, A, 0.16);
    surface_container_lowest: WHITE, Rgb::hex(0x19191B);
    surface_container_low: mix(LIGHT_SURFACE, A, 0.04), mix(DARK_SURFACE, A, 0.04);
    surface_container: mix(LIGHT_SURFACE, A, 0.07), mix(DARK_SURFACE, A, 0.07);
    surface_container_high: mix(LIGHT_SURFACE, A, 0.10), mix(DARK_SURFACE, A, 0.11);
    surface_container_highest: mix(LIGHT_SURFACE, A, 0.14), mix(DARK_SURFACE, A, 0.15);
    outline: Rgb::hex(0x717985), Rgb::hex(0x8A929E);
    outline_variant: mix(Rgb::hex(0xC1C8D2), A, 0.08), mix(Rgb::hex(0x3D4652), A, 0.08);
    inverse_surface: Rgb::hex(0x2C3138), Rgb::hex(0xE6E8EC);
    inverse_on_surface: Rgb::hex(0xF0F1F5), Rgb::hex(0x25272B);
    inverse_primary: mix(A, WHITE, 0.55), mix(A, BLACK, 0.10);
    error: Rgb::hex(0xBA1A1A), Rgb::hex(0xFFB4AB);
    on_error: WHITE, Rgb::hex(0x690005);
    error_container: Rgb::hex(0xFFDAD6), Rgb::hex(0x93000A);
    on_error_container: Rgb::hex(0x410002), Rgb::hex(0xFFDAD6);
}

#[cfg(test)]
fn camel(snake: &str) -> String {
    let mut out = String::new();
    let mut upper = false;
    for c in snake.chars() {
        if c == '_' {
            upper = true;
        } else if upper {
            out.extend(c.to_uppercase());
            upper = false;
        } else {
            out.push(c);
        }
    }
    out
}

pub fn slint_color(c: Rgb) -> slint::Color {
    let [r, g, b] = c.to_u8();
    slint::Color::from_rgb_u8(r, g, b)
}

#[cfg(test)]
mod tests {
    use super::*;

    const FIXTURE: &str = include_str!("../../../design/palette-fixture.json");

    fn hex(c: Rgb) -> String {
        let [r, g, b] = c.to_u8();
        format!("#{r:02X}{g:02X}{b:02X}")
    }

    fn fixture_value(accent: u32) -> serde_json::Value {
        let scheme = |dark| {
            Scheme::new(dark, Rgb::hex(accent))
                .roles()
                .into_iter()
                .map(|(role, c)| (role, serde_json::Value::String(hex(c))))
                .collect::<serde_json::Map<_, _>>()
        };
        serde_json::json!({
            "accent": format!("#{accent:06X}"),
            "light": scheme(false),
            "dark": scheme(true),
        })
    }

    /// Regenerate with `LAVA_WRITE_PALETTE_FIXTURE=1 cargo test -p lava-connect palette`.
    #[test]
    fn palette_matches_fixture() {
        let actual = fixture_value(ACCENT_PRESETS[0]);
        if std::env::var_os("LAVA_WRITE_PALETTE_FIXTURE").is_some() {
            let path = concat!(
                env!("CARGO_MANIFEST_DIR"),
                "/../../design/palette-fixture.json"
            );
            std::fs::write(path, serde_json::to_string_pretty(&actual).unwrap() + "\n").unwrap();
            return;
        }
        let expected: serde_json::Value = serde_json::from_str(FIXTURE).unwrap();
        for mode in ["light", "dark"] {
            for (role, value) in expected[mode].as_object().unwrap() {
                let parse = |v: &serde_json::Value| {
                    u32::from_str_radix(&v.as_str().unwrap()[1..], 16).unwrap()
                };
                let (want, got) = (parse(value), parse(&actual[mode][role]));
                for shift in [16, 8, 0] {
                    let delta = ((want >> shift) & 0xff) as i32 - ((got >> shift) & 0xff) as i32;
                    assert!(
                        delta.abs() <= 1,
                        "{mode}.{role}: expected {value}, got {}",
                        actual[mode][role]
                    );
                }
            }
        }
    }

    #[test]
    fn mixing_endpoints_and_gray_round_trip() {
        assert_eq!(mix(WHITE, BLACK, 0.0).to_u8(), [255, 255, 255]);
        assert_eq!(mix(WHITE, BLACK, 1.0).to_u8(), [0, 0, 0]);
        assert_eq!(
            mix(LIGHT_SURFACE, LIGHT_SURFACE, 0.5).to_u8(),
            [0xF7, 0xF7, 0xF8]
        );
    }
}
