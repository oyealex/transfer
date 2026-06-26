#!/usr/bin/env python3
import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import traceback
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

CASE_RE = re.compile(r"\b(PUB-\d{3}|HID-(?:L\d|E2E)-\d{2})\b")
DISPLAY_RE = re.compile(r'@DisplayName\s*\(\s*"((?:\\.|[^"\\])*)"\s*\)', re.S)
METHOD_RE = re.compile(
    r'(?:@\w+(?:\s*\([^)]*\))?\s*)*'
    r'(?:(?:public|protected|private)\s+)?void\s+([A-Za-z_]\w*)\s*\(',
    re.S,
)
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)
CLASS_RE = re.compile(r"\bclass\s+([A-Za-z_]\w*)\b")


@dataclass(frozen=True)
class Case:
    case_id: str
    class_name: str
    method_name: str
    order: int


class UserFacingError(RuntimeError):
    pass


def debug(args: argparse.Namespace | None, message: str) -> None:
    if args is not None and getattr(args, "debug", False):
        print(f"[score-debug] {message}", file=sys.stderr)


def tail_text(text: str, max_lines: int = 80) -> str:
    lines = text.splitlines()
    if len(lines) <= max_lines:
        return "\n".join(lines)
    return "\n".join([f"... omitted {len(lines) - max_lines} lines ...", *lines[-max_lines:]])


def case_ids(cases: list[Case]) -> str:
    return ", ".join(case.case_id for case in sorted(cases, key=lambda case: case_sort_key(case.case_id)))


def has_valid_project_code(root: Path) -> bool:
    return (root / "code" / "pom.xml").is_file()


def find_project_root(explicit: str | None) -> Path:
    if explicit:
        root = Path(explicit).resolve()
        if has_valid_project_code(root):
            return root
        raise UserFacingError(
            "评分失败：指定的项目根目录未发现有效项目代码。"
            f"路径：{root}；期望存在 code/pom.xml。"
        )

    if os.environ.get("PROJECT_ROOT"):
        root = Path(os.environ["PROJECT_ROOT"]).resolve()
        if has_valid_project_code(root):
            return root
        raise UserFacingError(
            "评分失败：PROJECT_ROOT 指向的项目根目录未发现有效项目代码。"
            f"路径：{root}；期望存在 code/pom.xml。"
        )

    cwd = Path.cwd()
    candidates = [cwd, *cwd.parents, Path("/app/code")]

    for candidate in candidates:
        if has_valid_project_code(candidate):
            return candidate.resolve()

    raise UserFacingError(
        "评分失败：未发现有效项目代码。请使用 --project-root 指向选手项目根目录；"
        "该目录下必须存在 code/pom.xml。"
    )


def find_existing(paths: list[Path]) -> Path | None:
    for path in paths:
        if (path / "pom.xml").is_file():
            return path
    return None


def require_reference_test_project(label: str, paths: list[Path]) -> Path:
    found = find_existing(paths)
    if found is not None:
        return found

    expected = " 或 ".join(str(path) for path in paths)
    raise UserFacingError(
        f"评分失败：Skill references 中缺少{label}。期望存在：{expected}，且目录下包含 pom.xml。"
    )


def patch_parent_relative_path(pom_path: Path, code_pom: Path, args: argparse.Namespace | None = None) -> None:
    tree = ET.parse(pom_path)
    root = tree.getroot()

    namespace = ""
    if root.tag.startswith("{"):
        namespace = root.tag[1:].split("}", 1)[0]
        ET.register_namespace("", namespace)

    def q(name: str) -> str:
        return f"{{{namespace}}}{name}" if namespace else name

    parent = root.find(q("parent"))
    if parent is None:
        return

    relative_path = parent.find(q("relativePath"))
    if relative_path is None:
        relative_path = ET.SubElement(parent, q("relativePath"))

    patched_path = os.path.relpath(code_pom, pom_path.parent)
    relative_path.text = patched_path
    tree.write(pom_path, encoding="utf-8", xml_declaration=True)
    debug(args, f"patched parent.relativePath in {pom_path}: {patched_path}")


def copy_test_project(
    source: Path | None,
    destination: Path,
    code_pom: Path,
    args: argparse.Namespace | None = None,
    label: str = "test",
) -> Path | None:
    if source is None or not source.exists():
        debug(args, f"{label}: test project source not found")
        return None

    debug(args, f"{label}: copying test project from {source} to {destination}")
    shutil.copytree(
        source,
        destination,
        ignore=shutil.ignore_patterns("target", ".git", ".idea", "*.iml"),
    )
    patch_parent_relative_path(destination / "pom.xml", code_pom, args)
    return destination


def discover_cases(
    test_project: Path | None,
    args: argparse.Namespace | None = None,
    label: str = "test",
) -> list[Case]:
    if test_project is None:
        debug(args, f"{label}: skipped case discovery because project is missing")
        return []

    cases = []
    order = 0
    java_root = test_project / "src" / "test" / "java"
    debug(args, f"{label}: discovering cases under {java_root}")

    for java_file in sorted(java_root.rglob("*.java")):
        text = java_file.read_text(encoding="utf-8", errors="ignore")
        package_match = PACKAGE_RE.search(text)
        class_match = CLASS_RE.search(text)
        if not package_match or not class_match:
            continue

        class_name = f"{package_match.group(1)}.{class_match.group(1)}"

        for display_match in DISPLAY_RE.finditer(text):
            case_match = CASE_RE.search(display_match.group(1))
            if not case_match:
                continue

            method_match = METHOD_RE.search(text, display_match.end())
            if not method_match:
                continue

            cases.append(
                Case(
                    case_id=case_match.group(1),
                    class_name=class_name,
                    method_name=method_match.group(1),
                    order=order,
                )
            )
            order += 1

    debug(args, f"{label}: discovered {len(cases)} cases: {case_ids(cases) if cases else '(none)'}")
    return cases


def build_case_index(cases: list[Case]) -> dict[str, dict[str, str]]:
    by_method = {}
    by_full = {}

    for case in cases:
        by_method[case.method_name] = case.case_id
        by_full[f"{case.class_name}#{case.method_name}"] = case.case_id
        by_full[f"{case.class_name.split('.')[-1]}#{case.method_name}"] = case.case_id

    return {"by_method": by_method, "by_full": by_full}


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def testcase_name_candidates(name: str) -> list[str]:
    candidates = [name.strip()]
    for separator in ("(", "["):
        if separator in name:
            candidates.append(name.split(separator, 1)[0].strip())
    return list(dict.fromkeys(candidate for candidate in candidates if candidate))


def resolve_case_id(class_name: str, test_name: str, index: dict[str, dict[str, str]]) -> str | None:
    direct_match = CASE_RE.search(test_name)
    if direct_match:
        return direct_match.group(1)

    for candidate in testcase_name_candidates(test_name):
        full = f"{class_name}#{candidate}"
        simple = f"{class_name.split('.')[-1]}#{candidate}"

        if full in index["by_full"]:
            return index["by_full"][full]
        if simple in index["by_full"]:
            return index["by_full"][simple]
        if candidate in index["by_method"]:
            return index["by_method"][candidate]

    return None


def parse_surefire_reports(
    test_project: Path | None,
    cases: list[Case],
    args: argparse.Namespace | None = None,
    label: str = "test",
) -> dict[str, bool]:
    statuses = {}
    if test_project is None:
        debug(args, f"{label}: skipped Surefire parsing because project is missing")
        return statuses

    index = build_case_index(cases)
    report_dir = test_project / "target" / "surefire-reports"
    reports = sorted(report_dir.glob("TEST-*.xml"))
    debug(args, f"{label}: parsing Surefire reports from {report_dir}")
    debug(args, f"{label}: found {len(reports)} XML reports")

    for report in reports:
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            debug(args, f"{label}: failed to parse XML report {report}")
            continue

        for testcase in root.iter():
            if local_name(testcase.tag) != "testcase":
                continue
            class_name = testcase.attrib.get("classname", "")
            test_name = testcase.attrib.get("name", "")
            case_id = resolve_case_id(class_name, test_name, index)
            if not case_id:
                debug(args, f"{label}: could not map testcase {class_name}#{test_name}")
                continue

            markers = [local_name(child.tag) for child in list(testcase)]
            passed = not any(
                marker in {"failure", "error", "skipped"}
                for marker in markers
            )
            statuses[case_id] = statuses.get(case_id, True) and passed
            debug(args, f"{label}: {case_id} -> {'passed' if passed else 'failed'} from {class_name}#{test_name}")

    missing = sorted({case.case_id for case in cases} - set(statuses), key=case_sort_key)
    if missing:
        debug(args, f"{label}: no Surefire testcase result for: {', '.join(missing)}")
    return statuses


def run_command(
    command: list[str],
    cwd: Path,
    log_path: Path,
    timeout_seconds: int,
    args: argparse.Namespace | None = None,
) -> int:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    debug(args, f"running command in {cwd}: {shlex.join(command)}")
    debug(args, f"command log: {log_path}")

    try:
        completed = subprocess.run(
            command,
            cwd=cwd,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout_seconds,
            check=False,
        )
        log_path.write_text("$ " + shlex.join(command) + "\n" + completed.stdout, encoding="utf-8")
        debug(args, f"command exit code: {completed.returncode}")
        if getattr(args, "debug", False) and completed.stdout:
            print("[score-debug] command output tail:", file=sys.stderr)
            print(tail_text(completed.stdout), file=sys.stderr)
        return completed.returncode
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout if isinstance(exc.stdout, str) else ""
        log_path.write_text(
            "$ " + shlex.join(command) + "\nTIMEOUT\n" + output,
            encoding="utf-8",
        )
        debug(args, f"command timed out after {timeout_seconds} seconds")
        if getattr(args, "debug", False) and output:
            print("[score-debug] timeout output tail:", file=sys.stderr)
            print(tail_text(output), file=sys.stderr)
        return 124
    except FileNotFoundError as exc:
        log_path.write_text(str(exc), encoding="utf-8")
        debug(args, f"command failed before execution: {exc}")
        return 127


def case_sort_key(case_id: str) -> tuple:
    if case_id.startswith("PUB-"):
        return (0, int(case_id.split("-")[1]))

    e2e = re.match(r"HID-E2E-(\d+)", case_id)
    if e2e:
        return (1, 99, int(e2e.group(1)))

    level = re.match(r"HID-L(\d)-(\d+)", case_id)
    if level:
        return (1, int(level.group(1)), int(level.group(2)))

    return (2, case_id)


def score(args: argparse.Namespace) -> dict:
    project_root = find_project_root(args.project_root)
    skill_root = Path(args.skill_root).resolve() if args.skill_root else Path(__file__).resolve().parents[1]
    code_pom = project_root / "code" / "pom.xml"

    maven = shlex.split(args.maven_cmd or os.environ.get("MAVEN_CMD", "mvn")) or ["mvn"]
    default_maven_settings = skill_root / "references" / "maven-settings.xml"
    maven_settings_value = args.maven_settings or os.environ.get("MAVEN_SETTINGS")
    if maven_settings_value:
        maven_settings = Path(maven_settings_value).expanduser().resolve()
    elif default_maven_settings.is_file():
        maven_settings = default_maven_settings.resolve()
    else:
        maven_settings = None
    maven_command = [*maven]
    if maven_settings is not None:
        maven_command.extend(["-s", str(maven_settings)])
    timeout_seconds = int(args.timeout_seconds or os.environ.get("JUDGE_TIMEOUT_SECONDS", "1200"))
    surefire_version = args.surefire_version or os.environ.get("JUDGE_SUREFIRE_VERSION", "3.2.5")
    maven_quiet = [] if args.debug else ["-q"]

    debug(args, f"project root: {project_root}")
    debug(args, f"skill root: {skill_root}")
    debug(args, f"code pom: {code_pom}")
    debug(args, f"maven settings: {maven_settings if maven_settings else '(default Maven settings)'}")
    debug(args, f"maven command prefix: {shlex.join(maven_command)}")
    debug(args, f"timeout seconds: {timeout_seconds}")
    debug(args, f"surefire version: {surefire_version}")

    public_source = require_reference_test_project(
        "公开黑盒用例",
        [skill_root / "references" / "test-cases"],
    )
    hidden_source = require_reference_test_project(
        "非公开黑盒用例",
        [skill_root / "references" / "test-cases-internal"],
    )

    debug(args, f"public source: {public_source if public_source else '(missing)'}")
    debug(args, f"hidden source: {hidden_source if hidden_source else '(missing)'}")

    def run_in_work_dir(work_dir: Path) -> dict:
        debug(args, f"work dir: {work_dir}")
        logs_dir = Path(args.log_dir).resolve() if args.log_dir else work_dir / "logs"
        debug(args, f"logs dir: {logs_dir}")

        public_project = copy_test_project(public_source, work_dir / "public-test-cases", code_pom, args, "public")
        hidden_project = copy_test_project(hidden_source, work_dir / "hidden-test-cases-internal", code_pom, args, "hidden")

        public_cases = discover_cases(public_project, args, "public")
        hidden_cases = discover_cases(hidden_project, args, "hidden")
        all_cases = public_cases + hidden_cases

        statuses = {case.case_id: False for case in all_cases}
        debug(args, f"initial result count: {len(statuses)}")

        install_rc = run_command(
            maven_command + ["-B", *maven_quiet, "-f", str(code_pom), "-Dmaven.test.skip=true", "install"],
            project_root,
            logs_dir / "install.log",
            timeout_seconds,
            args,
        )

        if install_rc == 0:
            if public_project is not None:
                public_rc = run_command(
                    maven_command
                    + [
                        "-B",
                        *maven_quiet,
                        "-f",
                        str(public_project / "pom.xml"),
                        "-Dmaven.test.failure.ignore=true",
                        "-DfailIfNoTests=false",
                        "test-compile",
                        f"org.apache.maven.plugins:maven-surefire-plugin:{surefire_version}:test",
                    ],
                    public_project,
                    logs_dir / "public-tests.log",
                    timeout_seconds,
                    args,
                )
                debug(args, f"public test command exit code: {public_rc}")
                statuses.update(parse_surefire_reports(public_project, public_cases, args, "public"))

            if hidden_project is not None:
                hidden_rc = run_command(
                    maven_command
                    + [
                        "-B",
                        *maven_quiet,
                        "-f",
                        str(hidden_project / "pom.xml"),
                        "-Dmaven.test.failure.ignore=true",
                        "-DfailIfNoTests=false",
                        "test-compile",
                        f"org.apache.maven.plugins:maven-surefire-plugin:{surefire_version}:test",
                    ],
                    hidden_project,
                    logs_dir / "hidden-tests.log",
                    timeout_seconds,
                    args,
                )
                debug(args, f"hidden test command exit code: {hidden_rc}")
                statuses.update(parse_surefire_reports(hidden_project, hidden_cases, args, "hidden"))
        else:
            debug(args, "install command failed; all discovered cases remain failed")

        ordered_case_ids = sorted(statuses.keys(), key=case_sort_key)
        passed_count = sum(1 for case_id in ordered_case_ids if statuses[case_id])
        debug(args, f"final passed count: {passed_count}/{len(ordered_case_ids)}")
        return {
            "results": [
                {"case_id": case_id, "passed": bool(statuses[case_id])}
                for case_id in ordered_case_ids
            ]
        }

    if args.keep_work_dir:
        work_dir = Path(tempfile.mkdtemp(prefix="shophub-judge-"))
        debug(args, f"keeping work dir after exit: {work_dir}")
        return run_in_work_dir(work_dir)

    with tempfile.TemporaryDirectory(prefix="shophub-judge-") as tmp:
        return run_in_work_dir(Path(tmp))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", default=None)
    parser.add_argument("--skill-root", default=None)
    parser.add_argument("--debug", action="store_true", help="print diagnostic logs to stderr")
    parser.add_argument("--keep-work-dir", action="store_true", help="keep copied test projects and logs")
    parser.add_argument("--log-dir", default=None, help="write Maven logs to this directory")
    parser.add_argument("--maven-cmd", default=None, help="override Maven command prefix")
    parser.add_argument("--maven-settings", default=None, help="override Maven settings.xml path")
    parser.add_argument("--timeout-seconds", default=None, help="per-command timeout")
    parser.add_argument("--surefire-version", default=None, help="Surefire plugin version used for JUnit 5 tests")
    args = parser.parse_args()

    try:
        payload = score(args)
    except UserFacingError as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(2)
    except Exception:
        if args.debug:
            traceback.print_exc(file=sys.stderr)
        print("评分失败：评分脚本执行异常。使用 --debug 可查看详细堆栈。", file=sys.stderr)
        sys.exit(1)

    print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
