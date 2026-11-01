// SPDX-License-Identifier: GPL-3.0-or-later
use crate::t;
use anyhow::{Context, Result, bail, ensure};
use std::{
    io::{Read, Write},
    process::{Command, Stdio},
    time::{Duration, Instant},
};
const LIMIT: usize = 8 * 1024 * 1024;

// Bound both process lifetime and output. Child pipes are read concurrently so
// a large image cannot deadlock the child on a full stdout pipe.
pub fn read_clipboard() -> Result<(String, Vec<u8>)> {
    let types = run("wl-paste", &["--list-types"], None, 4096)?;
    let types = String::from_utf8(types)?;
    let (mime, offered) = select_type(&types)?;
    let data = run(
        "wl-paste",
        &["--no-newline", "--type", offered],
        None,
        LIMIT,
    )?;
    Ok((mime.into(), data))
}
fn select_type(types: &str) -> Result<(&str, &str)> {
    if types.lines().any(|t| t == "image/png") {
        return Ok(("image/png", "image/png"));
    }
    if let Some(offered) = types
        .lines()
        .find(|t| *t == "text/plain" || t.starts_with("text/plain;"))
    {
        return Ok(("text/plain", offered));
    }
    bail!(t(
        "Clipboard is not text or a PNG image",
        "剪贴板不是文字或 PNG 图片"
    ))
}

pub fn write_clipboard(mime: &str, bytes: &[u8]) -> Result<()> {
    ensure!(
        ["text/plain", "image/png"].contains(&mime) && bytes.len() <= LIMIT,
        "invalid clipboard payload"
    );
    run("wl-copy", &["--type", mime], Some(bytes), 4096)?;
    Ok(())
}
fn run(program: &str, args: &[&str], input: Option<&[u8]>, limit: usize) -> Result<Vec<u8>> {
    let mut child = Command::new(program)
        .args(args)
        .stdin(if input.is_some() {
            Stdio::piped()
        } else {
            Stdio::null()
        })
        .stdout(if input.is_some() {
            Stdio::null()
        } else {
            Stdio::piped()
        })
        .stderr(Stdio::null())
        .spawn()
        .with_context(|| {
            format!(
                "{program}: {}",
                t(
                    "cannot run; install wl-clipboard and use a Wayland session",
                    "无法运行；请安装 wl-clipboard 并使用 Wayland 会话"
                )
            )
        })?;
    let reader = child.stdout.take().map(|stdout| {
        std::thread::spawn(move || {
            let mut data = Vec::new();
            stdout.take((limit + 1) as u64).read_to_end(&mut data)?;
            Ok::<_, std::io::Error>(data)
        })
    });
    let writer = input.map(|input| {
        let mut stdin = child.stdin.take().unwrap();
        let bytes = input.to_vec();
        std::thread::spawn(move || stdin.write_all(&bytes))
    });
    let start = Instant::now();
    let exit = loop {
        if let Some(exit) = child.try_wait()? {
            break exit;
        }
        if start.elapsed() > Duration::from_secs(3) {
            let _ = child.kill();
            let _ = child.wait();
            bail!("{program}: {}", t("timed out", "超时"));
        }
        std::thread::sleep(Duration::from_millis(10));
    };
    if let Some(writer) = writer {
        writer
            .join()
            .map_err(|_| anyhow::anyhow!("clipboard writer failed"))??;
    }
    let output = match reader {
        Some(reader) => reader
            .join()
            .map_err(|_| anyhow::anyhow!("clipboard reader failed"))??,
        None => Vec::new(),
    };
    ensure!(output.len() <= limit, "clipboard exceeds size limit");
    ensure!(
        exit.success(),
        "{program}: {}",
        t(
            "failed; check the Wayland clipboard",
            "失败；请检查 Wayland 剪贴板"
        )
    );
    Ok(output)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn request_actual_offered_mime_and_canonicalize_wire_type() {
        assert_eq!(
            select_type("text/plain;charset=utf-8\nUTF8_STRING\n").unwrap(),
            ("text/plain", "text/plain;charset=utf-8")
        );
        assert_eq!(
            select_type("text/plain\nimage/png\n").unwrap(),
            ("image/png", "image/png")
        );
        assert!(select_type("text/html\napplication/pdf\n").is_err());
    }
}
