---
name: md-outline-large-markdown
description: Use md-outline to inspect an outline or locate heading-bounded sections before targeted reads of large Markdown files. Use only when a large .md file must be navigated by heading or its outline is needed; do not use for small files, full-document reads, or body-text searches.
---

# Navigate large Markdown by heading

Use `md-outline` as a read-only navigation aid, not as a general Markdown reader.

## Decision boundary

Use this skill only when the Markdown file is large enough that reading it all would be wasteful and either:

- the task needs the document outline; or
- the task needs one or more specific sections that can be identified by heading.

Do not use this skill when:

- the Markdown file is small;
- the task requires the complete document;
- the target is ordinary body text rather than a heading; or
- a native read operation is already the simpler, sufficiently efficient choice.

In those cases, use the native read or search tools. If `md-outline` is unavailable, fall back to native tools; do not install or build it without authorization.

## Bundled executable

This skill includes `bin/md-outline.exe`, built for `x86_64-pc-windows-msvc`. On a compatible Windows host, resolve the executable relative to this `SKILL.md` and run it in place, quoting its path. For example:

```powershell
& '<skill-directory>\bin\md-outline.exe' p large-document.md
```

On another platform, use an already installed `md-outline` command if one is available. Otherwise fall back to native read/search tools. Do not install or compile another binary merely to follow this skill.

## Workflow

1. If the heading names are unknown, run `md-outline parse <FILE.md>` or its `p` alias to inspect the outline.
2. To locate specific sections, run `md-outline search '<REGEX>' <FILE.md>` or its `s` alias. The regular expression matches normalized heading text including its leading `#` characters.
3. Treat each returned `<start>-<end>` as an inclusive, one-based section range. Use the native read tool to read only that range; `md-outline` does not return the body.
4. Refine the expression when unrelated headings match. If multiple matches are relevant, read each reported range.
5. Rerun the command after edits before relying on old line ranges.

For a known exact heading, prefer an anchored expression such as:

```console
md-outline s '^## Installation$' guide.md
```

Quote and escape regular expressions for the active shell. Do not infer section content from the outline alone.

`md-outline generate`/`g` creates a Markdown TOC. It is outside this skill's targeted-reading workflow unless the user explicitly asks for a TOC.
