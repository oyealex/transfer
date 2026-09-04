# md-outline

一个以 Markdown 语法解析器为基础的轻量大纲解析、搜索 CLI。代码块中的伪标题不会被识别。

## 构建

```console
cargo build --release
```

生成的程序位于 `target/release/md-outline`（Windows 下为 `md-outline.exe`）。

## 使用

查看两个命令各自的详细说明和输出格式：

```console
md-outline parse --help
md-outline search --help
md-outline generate --help
```

不提供 `md-outline help` 子命令。

输出一个或多个文件的标题及行号：

```console
md-outline parse file1.md file2.md
```

用 Rust 正则表达式搜索完整标题（匹配文本包含开头的 `#`）：

```console
md-outline search '^### title\d' file1.md file2.md
```

搜索结果中的范围是闭区间。章节终点为下一个同级或更高层级标题的上一行；如果没有后续边界，则为文件最后一行。

标题中的强调、链接等行内 Markdown 会解析为可见文本。例如 `## A **bold** title` 输出为 `## A bold title`。

`parse`、`search`、`generate` 分别提供 `p`、`s`、`g` 短别名：

```console
md-outline p file.md
md-outline s '^## ' file.md
md-outline g file.md
```

生成可嵌入文档的 Markdown TOC：

```console
md-outline generate file.md
```

TOC 使用嵌套列表和 GitHub 风格标题锚点。每个文件独立计算锚点：第一次出现的锚点保持不变，后续冲突依次增加 `-1`、`-2` 后缀。全部输入文件处理完成后，命令会通过 stderr 一次性汇总存在冲突的文件、行号及实际生成的锚点；原 Markdown 文件及标题文本不会被修改。

## 测试

功能测试通过编译后的 CLI 执行，样本文档位于 `tests/fixtures`：

```console
cargo test --release --test fixtures_e2e
```

只针对 `parse` 子命令运行 10 KiB、1 MiB、10 MiB 三档性能测试：

```console
cargo test --release --test performance_e2e -- --ignored --nocapture
```

性能样本会确定性生成到 `target/performance-fixtures`，结果包含中位耗时和吞吐率。小文件运行 15 次、中型文件运行 7 次、大文件运行 3 次；测试输出丢弃到空设备，避免终端渲染影响结果。

## 智能体集成

- 可嵌入系统提示词的工具说明：`docs/md-outline-agent-tool-guidance.md`
- 带 Windows x86_64 release 二进制的独立 Skill：`skills/md-outline-large-markdown`

两种集成方式都将适用范围限制为大型 Markdown 的大纲读取或按章节标题定位。小型文件、需要读取全文或搜索正文内容时，应使用智能体的原生 read/search 工具。
