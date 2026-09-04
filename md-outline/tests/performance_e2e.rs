use std::fs::{self, File};
use std::io::{BufWriter, Write};
use std::path::Path;
use std::process::{Command, Stdio};
use std::time::{Duration, Instant};

const KIB: usize = 1024;
const MIB: usize = 1024 * KIB;
const CASES: [(&str, usize, usize); 3] = [
    ("small-10KiB.md", 10 * KIB, 15),
    ("medium-1MiB.md", MIB, 7),
    ("large-10MiB.md", 10 * MIB, 3),
];

const DOCUMENT_BLOCK: &[u8] = br#"# Product documentation

This paragraph contains ordinary prose, a [link](https://example.com), **strong
text**, `inline code`, punctuation, and enough content to resemble documentation.

## Installation

Run the installation command and verify its output. The exact command depends on
the platform, environment, package manager, and selected deployment profile.

```shell
# This heading-like line is code and must remain ignored.
printf 'sample output'
```

### Configuration

Configuration values can contain Unicode text, escaped characters, URLs, and
other Markdown constructs. Repeated content makes benchmark files deterministic.

#### Troubleshooting

Use logs and diagnostics to identify failures without changing unrelated state.

"#;

/// Runs the compiled release CLI rather than calling parser library functions.
#[test]
#[ignore = "performance test: run explicitly with --ignored --nocapture"]
fn parse_release_binary_across_file_sizes() {
    let fixture_dir = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("target")
        .join("performance-fixtures");
    fs::create_dir_all(&fixture_dir).unwrap();

    for (name, bytes, _) in CASES {
        write_sized_markdown(&fixture_dir.join(name), bytes);
    }

    // Warm up process and filesystem paths before collecting measurements.
    run_parse(&fixture_dir.join(CASES[0].0));

    println!("file,size_bytes,runs,median_ms,throughput_MiB_s");
    for (name, bytes, runs) in CASES {
        let file = fixture_dir.join(name);
        let mut samples = Vec::with_capacity(runs);
        for _ in 0..runs {
            let started = Instant::now();
            run_parse(&file);
            samples.push(started.elapsed());
        }
        samples.sort_unstable();
        let median = samples[samples.len() / 2];
        let throughput = bytes as f64 / MIB as f64 / median.as_secs_f64();
        println!(
            "{name},{bytes},{runs},{:.3},{throughput:.2}",
            duration_ms(median)
        );
    }

    println!("fixtures={}", fixture_dir.display());
}

fn write_sized_markdown(path: &Path, target_bytes: usize) {
    let mut writer = BufWriter::new(File::create(path).unwrap());
    let full_blocks = target_bytes / DOCUMENT_BLOCK.len();
    let remaining = target_bytes % DOCUMENT_BLOCK.len();

    for _ in 0..full_blocks {
        writer.write_all(DOCUMENT_BLOCK).unwrap();
    }
    writer.write_all(&DOCUMENT_BLOCK[..remaining]).unwrap();
    writer.flush().unwrap();

    assert_eq!(fs::metadata(path).unwrap().len(), target_bytes as u64);
}

fn run_parse(file: &Path) {
    let output = Command::new(env!("CARGO_BIN_EXE_md-outline"))
        .arg("parse")
        .arg(file)
        .stdout(Stdio::null())
        .output()
        .unwrap();

    assert!(
        output.status.success(),
        "parse failed for {}: {}",
        file.display(),
        String::from_utf8_lossy(&output.stderr)
    );
}

fn duration_ms(duration: Duration) -> f64 {
    duration.as_secs_f64() * 1_000.0
}
