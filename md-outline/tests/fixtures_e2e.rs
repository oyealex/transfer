use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Output};
use std::time::{SystemTime, UNIX_EPOCH};

const FIXTURES: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/fixtures");

#[test]
fn parse_handles_commonmark_heading_forms_and_rejections() {
    let output = run_in_fixtures(&["parse", "atx-and-setext.md"]);

    assert_success(&output);
    assert_eq!(
        stdout(&output),
        concat!(
            "atx-and-setext.md\n",
            "1:# H1\n",
            "2:## H2\n",
            "3:### H3\n",
            "4:#### H4\n",
            "5:##### H5\n",
            "6:###### H6\n",
            "10:# Setext H1\n",
            "13:## Setext H2\n",
            "16:### Three leading spaces\n",
            "19:# \n",
        )
    );
}

#[test]
fn parse_ignores_all_tested_code_and_html_block_forms() {
    let output = run_in_fixtures(&[
        "parse",
        "code-blocks.md",
        "containers-and-html.md",
        "no-headings.md",
    ]);

    assert_success(&output);
    assert_eq!(
        stdout(&output),
        concat!(
            "code-blocks.md\n",
            "1:# Visible before\n",
            "17:## Visible after\n",
            "\n",
            "containers-and-html.md\n",
            "1:# Quote heading\n",
            "5:## List heading\n",
            "7:### Nested list heading\n",
            "15:# Visible after HTML\n",
            "\n",
            "no-headings.md\n",
        )
    );
}

#[test]
fn parse_normalizes_inline_markup_and_preserves_unicode_text() {
    let output = run_in_fixtures(&["parse", "inline-and-unicode.md"]);

    assert_success(&output);
    assert_eq!(
        stdout(&output),
        concat!(
            "inline-and-unicode.md\n",
            "1:# Emphasis and strong\n",
            "3:## Link text and image alt\n",
            "5:### code() & escaped *star*\n",
            "7:#### 中文标题 🚀\n",
            "9:##### HTML text\n",
            "11:###### reference link\n",
        )
    );
}

#[test]
fn search_matches_hash_prefix_unicode_and_reports_nested_ranges() {
    let ranges = run_in_fixtures(&["search", r"^### Leaf match-\d$", "ranges.md"]);
    assert_success(&ranges);
    assert_eq!(
        stdout(&ranges),
        concat!(
            "ranges.md\n",
            "5-8:### Leaf match-1\n",
            "9-10:### Leaf match-2\n",
        )
    );

    let unicode = run_in_fixtures(&["search", r"^#### \p{Han}+标题", "inline-and-unicode.md"]);
    assert_success(&unicode);
    assert_eq!(
        stdout(&unicode),
        "inline-and-unicode.md\n7-13:#### 中文标题 🚀\n"
    );

    let no_match = run_in_fixtures(&["search", "does-not-exist", "no-headings.md"]);
    assert_success(&no_match);
    assert_eq!(stdout(&no_match), "no-headings.md\n");
}

#[test]
fn parse_reports_correct_lines_for_crlf_and_cr_files() {
    let fixture_dir = temporary_fixture_dir();
    fs::create_dir_all(&fixture_dir).unwrap();
    fs::write(
        fixture_dir.join("crlf.md"),
        "intro\r\n# CRLF\r\nbody\r\n## End",
    )
    .unwrap();
    fs::write(fixture_dir.join("cr.md"), "intro\r# CR\rbody\r## End").unwrap();

    let output = run_in(&fixture_dir, &["parse", "crlf.md", "cr.md"]);

    assert_success(&output);
    assert_eq!(
        stdout(&output),
        concat!(
            "crlf.md\n",
            "2:# CRLF\n",
            "4:## End\n",
            "\n",
            "cr.md\n",
            "2:# CR\n",
            "4:## End\n",
        )
    );
    fs::remove_dir_all(fixture_dir).unwrap();
}

#[test]
fn failures_use_stderr_and_nonzero_exit_status() {
    let bad_regex = run_in_fixtures(&["search", "[", "ranges.md"]);
    assert!(!bad_regex.status.success());
    assert!(stdout(&bad_regex).is_empty());
    assert!(stderr(&bad_regex).contains("invalid regular expression"));

    let missing_file = run_in_fixtures(&["parse", "missing.md"]);
    assert!(!missing_file.status.success());
    assert!(stdout(&missing_file).is_empty());
    assert!(stderr(&missing_file).contains("cannot read missing.md"));
}

#[test]
fn short_aliases_are_equivalent_to_their_full_commands() {
    for (full, alias, arguments) in [
        ("parse", "p", vec!["ranges.md"]),
        ("search", "s", vec!["Leaf", "ranges.md"]),
        ("generate", "g", vec!["inline-and-unicode.md"]),
    ] {
        let mut full_args = vec![full];
        full_args.extend(&arguments);
        let mut alias_args = vec![alias];
        alias_args.extend(&arguments);

        let full_output = run_in_fixtures(&full_args);
        let alias_output = run_in_fixtures(&alias_args);
        assert_success(&full_output);
        assert_success(&alias_output);
        assert_eq!(alias_output.stdout, full_output.stdout);
        assert_eq!(alias_output.stderr, full_output.stderr);
    }
}

#[test]
fn generate_outputs_nested_links_and_unique_github_style_anchors() {
    let output = run_in_fixtures(&["generate", "toc.md", "no-headings.md"]);

    assert!(output.status.success(), "{}", stderr(&output));
    assert_eq!(
        stdout(&output),
        concat!(
            "toc.md\n",
            "- [Project \\[Guide\\]](#project-guide)\n",
            "  - [Install & Run](#install--run)\n",
            "    - [Advanced mode](#advanced-mode)\n",
            "  - [Duplicate](#duplicate)\n",
            "  - [Duplicate](#duplicate-1)\n",
            "- [Project Guide](#project-guide-1)\n",
            "  - [中文章节 🚀](#中文章节-)\n",
            "\n",
            "no-headings.md\n",
        )
    );
    assert_eq!(
        stderr(&output),
        concat!(
            "md-outline: warning: duplicate heading anchors detected; generated suffixed anchors:\n",
            "  toc.md:9: #duplicate -> #duplicate-1 (Duplicate)\n",
            "  toc.md:11: #project-guide -> #project-guide-1 (Project Guide)\n",
        )
    );
}

#[test]
fn generate_warns_once_after_collecting_collisions_from_all_files() {
    let output = run_in_fixtures(&["g", "toc.md", "toc-no-collision.md", "toc-another.md"]);

    assert!(output.status.success(), "{}", stderr(&output));
    let warning = stderr(&output);
    assert_eq!(warning.matches("md-outline: warning:").count(), 1);
    assert!(warning.contains("toc.md:9: #duplicate -> #duplicate-1"));
    assert!(warning.contains("toc.md:11: #project-guide -> #project-guide-1"));
    assert!(warning.contains("toc-another.md:3: #repeat -> #repeat-1"));
    assert!(!warning.contains("toc-no-collision.md"));
}

fn run_in_fixtures(args: &[&str]) -> Output {
    run_in(Path::new(FIXTURES), args)
}

fn run_in(directory: &Path, args: &[&str]) -> Output {
    Command::new(env!("CARGO_BIN_EXE_md-outline"))
        .current_dir(directory)
        .args(args)
        .output()
        .unwrap()
}

fn assert_success(output: &Output) {
    assert!(output.status.success(), "{}", stderr(output));
    assert!(stderr(output).is_empty(), "{}", stderr(output));
}

fn stdout(output: &Output) -> String {
    String::from_utf8(output.stdout.clone()).unwrap()
}

fn stderr(output: &Output) -> String {
    String::from_utf8_lossy(&output.stderr).into_owned()
}

fn temporary_fixture_dir() -> PathBuf {
    let nonce = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    std::env::temp_dir().join(format!("md-outline-e2e-{}-{nonce}", std::process::id()))
}
