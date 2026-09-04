use clap::{Parser, Subcommand};
use md_outline::{AnchorCollision, generate_toc, parse_outline, render_parse, render_search};
use regex::Regex;
use std::fs;
use std::io::{self, Write};
use std::path::PathBuf;
use std::process::ExitCode;

#[derive(Debug, Parser)]
#[command(
    name = "md-outline",
    bin_name = "md-outline",
    version,
    about,
    disable_help_subcommand = true
)]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    /// Parse and print the outline of one or more Markdown files.
    #[command(
        visible_alias = "p",
        long_about = "Parse the outline of one or more Markdown files.\n\nHeadings are identified by the Markdown parser, so heading-like text inside fenced or indented code blocks is ignored. Inline Markdown in a heading is normalized to its visible text.",
        after_long_help = "OUTPUT FORMAT:\n  <file>\n  <line>:<heading>\n\n  Each file is printed as a separate section, with one blank line between files.\n  <line> is the one-based source line number. <heading> includes its normalized\n  leading # characters.\n\nEXAMPLE:\n  md-outline parse file1.md file2.md\n\n  file1.md\n  1:# Title\n  10:## Child\n\n  file2.md\n  3:# Another title"
    )]
    Parse {
        /// Markdown files to parse.
        #[arg(required = true, value_name = "FILE.md")]
        files: Vec<PathBuf>,
    },
    /// Search headings with a regular expression and print section ranges.
    #[command(
        visible_alias = "s",
        long_about = "Search headings in one or more Markdown files with a Rust regular expression.\n\nThe expression is matched against the complete normalized heading, including its leading # characters. Headings are identified by the Markdown parser, so heading-like text inside code blocks is ignored.",
        after_long_help = "OUTPUT FORMAT:\n  <file>\n  <start>-<end>:<heading>\n\n  Each file is printed as a separate section, with one blank line between files.\n  <start> and <end> form an inclusive, one-based line range. A section ends on\n  the line before the next heading of the same or a higher level, or on the final\n  line of the file. <heading> includes its normalized leading # characters.\n\nEXAMPLE:\n  md-outline search '^### title\\d' file1.md file2.md\n\n  file1.md\n  71-150:### title1\n\n  file2.md\n  100-299:### title2"
    )]
    Search {
        /// Regular expression matched against the normalized heading, including its # prefix.
        #[arg(value_name = "REGEX")]
        title_key_word: String,
        /// Markdown files to search.
        #[arg(required = true, value_name = "FILE.md")]
        files: Vec<PathBuf>,
    },
    /// Generate an embeddable Markdown table of contents.
    #[command(
        visible_alias = "g",
        long_about = "Generate an embeddable Markdown table of contents for one or more Markdown files.\n\nEach heading becomes a nested Markdown list item with a link to its GitHub-style heading anchor. Skipped heading levels are normalized so the result remains a valid nested list. Duplicate anchors receive numeric suffixes.",
        after_long_help = "OUTPUT FORMAT:\n  <file>\n  - [<heading>](#<anchor>)\n    - [<child heading>](#<child-anchor>)\n\n  Each file is printed as a separate section, with one blank line between files.\n  Inline Markdown is normalized to visible text. Link labels are escaped where needed.\n  Anchors are lowercased, whitespace becomes '-', and punctuation is removed.\n\nDUPLICATE ANCHORS:\n  Anchor uniqueness is calculated separately for each file. The first occurrence keeps\n  its natural anchor; later collisions receive -1, -2, and subsequent suffixes. After\n  every input file has been processed, one warning summary listing all collisions is\n  written to stderr. Markdown source files and visible heading text are not modified.\n\nEXAMPLE:\n  md-outline generate guide.md\n\n  guide.md\n  - [Introduction](#introduction)\n    - [Install & Run](#install--run)"
    )]
    Generate {
        /// Markdown files for which to generate tables of contents.
        #[arg(required = true, value_name = "FILE.md")]
        files: Vec<PathBuf>,
    },
}

fn main() -> ExitCode {
    match run(Cli::parse()) {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            eprintln!("md-outline: {error}");
            ExitCode::FAILURE
        }
    }
}

fn run(cli: Cli) -> Result<(), String> {
    let stdout = io::stdout();
    let mut output = io::BufWriter::new(stdout.lock());

    match cli.command {
        Command::Parse { files } => process_files(&mut output, &files, |name, outline| {
            render_parse(name, outline)
        }),
        Command::Search {
            title_key_word,
            files,
        } => {
            let pattern = Regex::new(&title_key_word).map_err(|error| {
                format!("invalid regular expression {title_key_word:?}: {error}")
            })?;
            process_files(&mut output, &files, |name, outline| {
                render_search(name, outline, &pattern)
            })
        }
        Command::Generate { files } => process_generate_files(&mut output, &files),
    }
}

fn process_files(
    output: &mut impl Write,
    files: &[PathBuf],
    render: impl Fn(&str, &md_outline::Outline) -> String,
) -> Result<(), String> {
    for (index, file) in files.iter().enumerate() {
        let source = fs::read_to_string(file)
            .map_err(|error| format!("cannot read {}: {error}", file.display()))?;
        let outline = parse_outline(&source);

        if index > 0 {
            writeln!(output).map_err(write_error)?;
        }
        write!(output, "{}", render(&file.display().to_string(), &outline)).map_err(write_error)?;
    }

    output.flush().map_err(write_error)
}

fn process_generate_files(output: &mut impl Write, files: &[PathBuf]) -> Result<(), String> {
    let mut collisions: Vec<(String, AnchorCollision)> = Vec::new();

    for (index, file) in files.iter().enumerate() {
        let source = fs::read_to_string(file)
            .map_err(|error| format!("cannot read {}: {error}", file.display()))?;
        let outline = parse_outline(&source);
        let file_name = file.display().to_string();
        let generated = generate_toc(&file_name, &outline);

        if index > 0 {
            writeln!(output).map_err(write_error)?;
        }
        write!(output, "{}", generated.markdown).map_err(write_error)?;
        collisions.extend(
            generated
                .collisions
                .into_iter()
                .map(|collision| (file_name.clone(), collision)),
        );
    }

    output.flush().map_err(write_error)?;
    write_collision_warning(&collisions)
}

fn write_collision_warning(collisions: &[(String, AnchorCollision)]) -> Result<(), String> {
    if collisions.is_empty() {
        return Ok(());
    }

    let stderr = io::stderr();
    let mut warning = io::BufWriter::new(stderr.lock());
    writeln!(
        warning,
        "md-outline: warning: duplicate heading anchors detected; generated suffixed anchors:"
    )
    .map_err(write_error)?;
    for (file, collision) in collisions {
        writeln!(
            warning,
            "  {}:{}: #{} -> #{} ({})",
            file,
            collision.line,
            collision.original_anchor,
            collision.generated_anchor,
            collision.title
        )
        .map_err(write_error)?;
    }
    warning.flush().map_err(write_error)
}

fn write_error(error: io::Error) -> String {
    format!("cannot write output: {error}")
}
