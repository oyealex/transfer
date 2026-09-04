use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

#[test]
fn parse_and_search_multiple_files_end_to_end() {
    let fixture_dir = temporary_fixture_dir();
    fs::create_dir_all(&fixture_dir).unwrap();
    let first = fixture_dir.join("first.md");
    let second = fixture_dir.join("second.md");
    fs::write(
        &first,
        "# First\ntext\n```md\n## hidden\n```\n## Child 12\nbody\n",
    )
    .unwrap();
    fs::write(&second, "# Second\ntext\n### Child 34\nlast\n").unwrap();

    let parse = run(&["parse", path(&first), path(&second)]);
    assert!(
        parse.status.success(),
        "{}",
        String::from_utf8_lossy(&parse.stderr)
    );
    assert_eq!(
        String::from_utf8(parse.stdout).unwrap(),
        format!(
            "{}\n1:# First\n6:## Child 12\n\n{}\n1:# Second\n3:### Child 34\n",
            first.display(),
            second.display()
        )
    );

    let search = run(&["search", r"Child \d+", path(&first), path(&second)]);
    assert!(
        search.status.success(),
        "{}",
        String::from_utf8_lossy(&search.stderr)
    );
    assert_eq!(
        String::from_utf8(search.stdout).unwrap(),
        format!(
            "{}\n6-7:## Child 12\n\n{}\n3-4:### Child 34\n",
            first.display(),
            second.display()
        )
    );

    fs::remove_dir_all(fixture_dir).unwrap();
}

#[test]
fn uses_flag_help_for_subcommands_and_has_no_help_command() {
    let help_command = run(&["help"]);
    assert!(!help_command.status.success());
    assert!(
        String::from_utf8_lossy(&help_command.stderr).contains("unrecognized subcommand 'help'")
    );

    let parse_help = run(&["parse", "--help"]);
    assert!(parse_help.status.success());
    let parse_text = String::from_utf8(parse_help.stdout).unwrap();
    assert!(parse_text.contains("Usage: md-outline parse <FILE.md>..."));
    assert!(parse_text.contains("OUTPUT FORMAT:"));
    assert!(parse_text.contains("<line>:<heading>"));
    assert!(parse_text.contains("md-outline parse file1.md file2.md"));

    let search_help = run(&["search", "--help"]);
    assert!(search_help.status.success());
    let search_text = String::from_utf8(search_help.stdout).unwrap();
    assert!(search_text.contains("Usage: md-outline search <REGEX> <FILE.md>..."));
    assert!(search_text.contains("<start>-<end>:<heading>"));
    assert!(search_text.contains("inclusive, one-based line range"));
    assert!(search_text.contains("md-outline search '^### title\\d' file1.md file2.md"));

    let root_help = run(&["--help"]);
    assert!(root_help.status.success());
    let root_text = String::from_utf8(root_help.stdout).unwrap();
    assert!(root_text.contains("parse     Parse and print"));
    assert!(root_text.contains("[alias: p]"));
    assert!(root_text.contains("[alias: s]"));
    assert!(root_text.contains("[alias: g]"));

    let generate_help = run(&["g", "--help"]);
    assert!(generate_help.status.success());
    let generate_text = String::from_utf8(generate_help.stdout).unwrap();
    assert!(generate_text.contains("Usage: md-outline generate <FILE.md>..."));
    assert!(generate_text.contains("- [<heading>](#<anchor>)"));
    assert!(generate_text.contains("DUPLICATE ANCHORS:"));
    assert!(generate_text.contains("one warning summary listing all collisions"));
    assert!(generate_text.contains("written to stderr"));
}

fn run(args: &[&str]) -> std::process::Output {
    Command::new(env!("CARGO_BIN_EXE_md-outline"))
        .args(args)
        .output()
        .unwrap()
}

fn path(path: &Path) -> &str {
    path.to_str().unwrap()
}

fn temporary_fixture_dir() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    std::env::temp_dir().join(format!("md-outline-test-{}-{nonce}", std::process::id()))
}
