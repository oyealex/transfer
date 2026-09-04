# `md-outline` 智能体工具说明

下面的文本可直接嵌入智能体的系统提示词。假设 `md-outline` 已安装并可从 `PATH` 调用。

```text
## Large Markdown navigation with `md-outline`

`md-outline` is a read-only navigation aid for large Markdown files. Use it only in either of these situations:

1. You need the outline of a large Markdown file.
2. You need to locate and read or search one or more specific sections of a large Markdown file by section heading.

Do not use `md-outline` for small Markdown files; use the native read tool instead. Do not use it when the task requires reading the entire Markdown document, even if the document is large; use the native read tool, chunked reads, or the environment's normal full-file workflow. Do not use `md-outline search` to search ordinary body text because it searches headings only.

Commands:

- `md-outline parse <FILE.md>...` (alias `p`) prints each document's headings with one-based source line numbers.
- `md-outline search <REGEX> <FILE.md>...` (alias `s`) matches a Rust regular expression against normalized heading text, including the leading `#` characters. It prints inclusive section ranges as `<start>-<end>:<heading>`.
- `md-outline generate <FILE.md>...` (alias `g`) generates a Markdown TOC. It is not needed for locating or reading section bodies.

For a targeted section read:

1. Run `md-outline search '<heading-regex>' <FILE.md>` to locate candidate sections. Use `parse` first only when the heading names or document structure are unknown.
2. Inspect the returned heading and inclusive line range. A section ends immediately before the next heading of the same or a higher level, or at the final line of the file.
3. Use the native read tool to read only the returned line range. `md-outline` returns outline metadata, not the section body.
4. If several headings match, refine the regular expression or read each relevant returned range. Do not infer section contents from headings alone.

Prefer an anchored expression when the title is known, for example `md-outline s '^## Installation$' guide.md`. Remember that `#` is part of the searchable heading text. Quote and escape the expression according to the active shell.

If `md-outline` is unavailable or fails, fall back to native read/search tools. Do not install, build, or modify tools unless the user has authorized that work. After a file is edited, rerun `parse` or `search` before relying on previously reported line ranges.
```

## 关键边界

这段提示词刻意不把 `md-outline` 定义为通用 Markdown 阅读器。它只优化“大文件中按章节标题定位”的场景：先用大纲缩小范围，再由原生 read 工具读取正文。对于小文件、必须阅读全文、或需要搜索正文关键词的任务，直接使用原生工具更合适。
