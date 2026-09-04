//! Markdown outline parsing and rendering.

use pulldown_cmark::{Event, HeadingLevel, Parser, Tag, TagEnd};
use regex::Regex;
use std::collections::{HashMap, HashSet};
use std::fmt::Write as _;

/// A heading extracted from the Markdown syntax tree.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Heading {
    /// One-based source line number.
    pub line: usize,
    /// Heading depth in the range 1..=6.
    pub level: u8,
    /// Visible heading text, without the leading `#` markers.
    pub title: String,
}

impl Heading {
    /// Return the normalized heading as it appears in CLI output.
    pub fn display_title(&self) -> String {
        format!("{} {}", "#".repeat(self.level.into()), self.title)
    }
}

/// The outline AST needed by the CLI, together with source extent information.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Outline {
    pub headings: Vec<Heading>,
    pub total_lines: usize,
}

/// A heading whose natural anchor was already used earlier in the same file.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AnchorCollision {
    pub line: usize,
    pub title: String,
    pub original_anchor: String,
    pub generated_anchor: String,
}

/// Generated Markdown and any anchor collisions encountered while producing it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GeneratedToc {
    pub markdown: String,
    pub collisions: Vec<AnchorCollision>,
}

#[derive(Debug)]
struct PendingHeading {
    line: usize,
    level: u8,
    title: String,
}

/// Parse a Markdown document and extract real heading nodes.
///
/// Since headings come from the Markdown parser rather than line-based matching,
/// heading-like text inside fenced or indented code blocks is ignored.
pub fn parse_outline(source: &str) -> Outline {
    let line_starts = line_starts(source);
    let mut headings = Vec::new();
    let mut pending: Option<PendingHeading> = None;

    for (event, range) in Parser::new(source).into_offset_iter() {
        match event {
            Event::Start(Tag::Heading { level, .. }) => {
                pending = Some(PendingHeading {
                    line: source_line(&line_starts, range.start),
                    level: heading_level(level),
                    title: String::new(),
                });
            }
            Event::End(TagEnd::Heading(_)) => {
                if let Some(mut heading) = pending.take() {
                    heading.title = heading.title.trim().to_owned();
                    headings.push(Heading {
                        line: heading.line,
                        level: heading.level,
                        title: heading.title,
                    });
                }
            }
            Event::Text(text)
            | Event::Code(text)
            | Event::InlineMath(text)
            | Event::DisplayMath(text) => {
                if let Some(heading) = pending.as_mut() {
                    heading.title.push_str(&text);
                }
            }
            Event::FootnoteReference(label) => {
                if let Some(heading) = pending.as_mut() {
                    let _ = write!(heading.title, "[^{label}]");
                }
            }
            Event::SoftBreak | Event::HardBreak => {
                if let Some(heading) = pending.as_mut() {
                    heading.title.push(' ');
                }
            }
            _ => {}
        }
    }

    Outline {
        headings,
        total_lines: source_line_count(source, &line_starts),
    }
}

/// Render all headings for one file.
pub fn render_parse(file_name: &str, outline: &Outline) -> String {
    let mut output = String::with_capacity(file_name.len() + outline.headings.len() * 32);
    output.push_str(file_name);
    output.push('\n');

    for heading in &outline.headings {
        let _ = writeln!(output, "{}:{}", heading.line, heading.display_title());
    }

    output
}

/// Render headings matching `pattern`, including their inclusive section ranges.
pub fn render_search(file_name: &str, outline: &Outline, pattern: &Regex) -> String {
    let section_ends = section_end_lines(outline);
    let mut output = String::with_capacity(file_name.len() + outline.headings.len() * 40);
    output.push_str(file_name);
    output.push('\n');

    for (heading, end_line) in outline.headings.iter().zip(section_ends) {
        let title = heading.display_title();
        if pattern.is_match(&title) {
            let _ = writeln!(output, "{}-{end_line}:{title}", heading.line);
        }
    }

    output
}

/// Render an embeddable Markdown table of contents for one file.
pub fn render_generate(file_name: &str, outline: &Outline) -> String {
    generate_toc(file_name, outline).markdown
}

/// Generate an embeddable Markdown table of contents and report anchor collisions.
pub fn generate_toc(file_name: &str, outline: &Outline) -> GeneratedToc {
    let mut output = String::with_capacity(file_name.len() + outline.headings.len() * 48);
    let mut collisions = Vec::new();
    let mut levels = Vec::with_capacity(6);
    let mut used_slugs = HashSet::with_capacity(outline.headings.len());
    let mut next_suffix = HashMap::new();
    output.push_str(file_name);
    output.push('\n');

    for heading in &outline.headings {
        while levels.last().is_some_and(|level| *level > heading.level) {
            levels.pop();
        }
        if levels.last() != Some(&heading.level) {
            levels.push(heading.level);
        }

        let base_slug = heading_slug(&heading.title);
        let slug = unique_slug(base_slug.clone(), &mut used_slugs, &mut next_suffix);
        if slug != base_slug {
            collisions.push(AnchorCollision {
                line: heading.line,
                title: heading.title.clone(),
                original_anchor: base_slug,
                generated_anchor: slug.clone(),
            });
        }
        let label = escape_link_label(&heading.title);
        let indent = "  ".repeat(levels.len() - 1);
        let _ = writeln!(output, "{indent}- [{label}](#{slug})");
    }

    GeneratedToc {
        markdown: output,
        collisions,
    }
}

fn heading_slug(title: &str) -> String {
    title
        .chars()
        .flat_map(char::to_lowercase)
        .filter_map(|character| {
            if character.is_alphanumeric() || matches!(character, '-' | '_') {
                Some(character)
            } else if character.is_whitespace() {
                Some('-')
            } else {
                None
            }
        })
        .collect()
}

fn unique_slug(
    base: String,
    used: &mut HashSet<String>,
    next_suffix: &mut HashMap<String, usize>,
) -> String {
    if used.insert(base.clone()) {
        return base;
    }

    let suffix = next_suffix.entry(base.clone()).or_insert(1);
    loop {
        let candidate = format!("{base}-{suffix}");
        *suffix += 1;
        if used.insert(candidate.clone()) {
            return candidate;
        }
    }
}

fn escape_link_label(title: &str) -> String {
    let mut escaped = String::with_capacity(title.len());
    for character in title.chars() {
        if matches!(character, '\\' | '[' | ']' | '*' | '_' | '`') {
            escaped.push('\\');
        }
        escaped.push(character);
    }
    escaped
}

fn section_end_lines(outline: &Outline) -> Vec<usize> {
    let mut ends = vec![outline.total_lines; outline.headings.len()];
    let mut next_line_at_level = [None; 7];

    for (index, heading) in outline.headings.iter().enumerate().rev() {
        let next_boundary = next_line_at_level[1..=usize::from(heading.level)]
            .iter()
            .flatten()
            .min()
            .copied();

        if let Some(line) = next_boundary {
            ends[index] = line - 1;
        }
        next_line_at_level[usize::from(heading.level)] = Some(heading.line);
    }

    ends
}

fn line_starts(source: &str) -> Vec<usize> {
    let mut starts = Vec::with_capacity(source.len() / 40 + 1);
    starts.push(0);
    let bytes = source.as_bytes();

    for (index, byte) in bytes.iter().enumerate() {
        if *byte == b'\n' || (*byte == b'\r' && bytes.get(index + 1) != Some(&b'\n')) {
            starts.push(index + 1);
        }
    }
    starts
}

fn source_line(starts: &[usize], byte_offset: usize) -> usize {
    starts.partition_point(|start| *start <= byte_offset)
}

fn source_line_count(source: &str, starts: &[usize]) -> usize {
    if source.is_empty() {
        0
    } else {
        starts.len() - usize::from(source.ends_with(['\r', '\n']))
    }
}

fn heading_level(level: HeadingLevel) -> u8 {
    match level {
        HeadingLevel::H1 => 1,
        HeadingLevel::H2 => 2,
        HeadingLevel::H3 => 3,
        HeadingLevel::H4 => 4,
        HeadingLevel::H5 => 5,
        HeadingLevel::H6 => 6,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_atx_and_setext_headings_but_ignores_code_blocks() {
        let markdown = concat!(
            "# Top\n",
            "\n",
            "```md\n",
            "## Not a heading\n",
            "```\n",
            "\n",
            "Setext title\n",
            "------------\n",
            "\n",
            "    ### Also code\n",
            "\n",
            "### A **bold** and `code` title\n",
        );

        let outline = parse_outline(markdown);

        assert_eq!(
            outline.headings,
            vec![
                Heading {
                    line: 1,
                    level: 1,
                    title: "Top".into()
                },
                Heading {
                    line: 7,
                    level: 2,
                    title: "Setext title".into()
                },
                Heading {
                    line: 12,
                    level: 3,
                    title: "A bold and code title".into()
                },
            ]
        );
        assert_eq!(outline.total_lines, 12);
    }

    #[test]
    fn computes_section_ranges_using_next_same_or_parent_heading() {
        let markdown = concat!(
            "# One\n",
            "body\n",
            "## Child\n",
            "body\n",
            "### Grandchild\n",
            "body\n",
            "## Sibling\n",
            "body\n",
            "# Two\n",
            "last\n",
        );
        let outline = parse_outline(markdown);
        let pattern = Regex::new("One|Child|Grandchild|Sibling|Two").unwrap();

        assert_eq!(
            render_search("doc.md", &outline, &pattern),
            concat!(
                "doc.md\n",
                "1-8:# One\n",
                "3-6:## Child\n",
                "5-6:### Grandchild\n",
                "7-8:## Sibling\n",
                "9-10:# Two\n",
            )
        );
    }

    #[test]
    fn regex_can_match_hash_prefix() {
        let outline = parse_outline("# One\n## Two\n### Three\n");
        let pattern = Regex::new(r"^## ").unwrap();

        assert_eq!(
            render_search("doc.md", &outline, &pattern),
            "doc.md\n2-3:## Two\n"
        );
    }

    #[test]
    fn parse_render_uses_one_based_lines() {
        let outline = parse_outline("intro\r\n\r\n# First\r\ntext\r\n## Second");

        assert_eq!(
            render_parse("windows.md", &outline),
            "windows.md\n3:# First\n5:## Second\n"
        );
        assert_eq!(outline.total_lines, 5);
    }

    #[test]
    fn supports_standalone_carriage_return_line_endings() {
        let outline = parse_outline("# First\rbody\r## Second\rlast");

        assert_eq!(
            render_parse("old-mac.md", &outline),
            "old-mac.md\n1:# First\n3:## Second\n"
        );
        assert_eq!(outline.total_lines, 4);
    }
}
