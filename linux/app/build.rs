// SPDX-License-Identifier: GPL-3.0-or-later
fn main() {
    let config = slint_build::CompilerConfiguration::new()
        .with_style("material".into())
        // Gettext catalogs without per-component contexts: i18n/<lang>/LC_MESSAGES/lava-connect.po
        .with_bundled_translations("i18n")
        .with_default_translation_context(slint_build::DefaultTranslationContext::None);
    slint_build::compile_with_config("ui/main.slint", config).expect("compile Slint UI");
    println!("cargo:rerun-if-changed=i18n");
}
