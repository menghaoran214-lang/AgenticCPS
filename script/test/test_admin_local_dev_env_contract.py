"""Contract tests for the admin frontend local development environment.

A clean checkout must be able to run the default development commands
(`pnpm dev`, `pnpm dev:e2e`) without relying on the git-ignored
`frontend/admin-vue3/.env.local` file, which must never leak into a
production or container build.
"""

import json
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ADMIN_DIR = ROOT / "frontend/admin-vue3"
PACKAGE_JSON = ADMIN_DIR / "package.json"

VITE_MODE_PATTERN = re.compile(r"--mode[= ]+(?P<mode>[A-Za-z0-9_.-]+)")
ENV_ASSIGNMENT_PATTERN = re.compile(
    r"^\s*(?P<key>[A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?P<value>.*)$"
)


def read_scripts() -> dict:
    return json.loads(PACKAGE_JSON.read_text(encoding="utf-8"))["scripts"]


def env_file_for_mode(mode: str) -> Path:
    return ADMIN_DIR / f".env.{mode}"


def parse_env(path: Path) -> dict:
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.strip().startswith("#"):
            continue
        match = ENV_ASSIGNMENT_PATTERN.match(line)
        if match:
            values[match.group("key")] = match.group("value").strip()
    return values


def git(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
    )


def is_git_ignored(relative_path: str) -> bool:
    return git("check-ignore", "-q", relative_path).returncode == 0


def tracked_paths() -> set:
    output = git("ls-files", "-z").stdout or ""
    return {entry for entry in output.split("\0") if entry}


def vite_scripts():
    """Scripts that run Vite to produce or serve a bundle.

    `vite preview` only serves an already built bundle, so its mode does not
    decide which API configuration ends up in the shipped JavaScript.
    """
    for name, script in read_scripts().items():
        if "vite" not in script or "vite preview" in script:
            continue
        yield name, script


def mode_of(script: str) -> str:
    match = VITE_MODE_PATTERN.search(script)
    assert match, f"Vite script must declare an explicit mode: {script}"
    return match.group("mode")


def test_default_development_commands_use_a_tracked_mode_file():
    scripts = read_scripts()

    for script_name in ("dev", "dev:e2e"):
        script = scripts[script_name]
        mode = mode_of(script)
        env_path = env_file_for_mode(mode)
        relative_path = env_path.relative_to(ROOT).as_posix()

        assert env_path.exists(), f"{relative_path} is missing"
        assert not is_git_ignored(relative_path), (
            f"{relative_path} must stay version controlled, otherwise a clean "
            "checkout cannot resolve the API configuration"
        )
        assert relative_path in tracked_paths(), (
            f"{relative_path} is not tracked by git"
        )

        values = parse_env(env_path)
        assert "VITE_BASE_URL" in values, f"{relative_path} must define VITE_BASE_URL"
        assert "VITE_API_URL" in values, f"{relative_path} must define VITE_API_URL"


def test_every_vite_script_resolves_to_a_tracked_env_file_with_api_config():
    checked = 0

    for script_name, script in vite_scripts():
        mode = mode_of(script)
        env_path = env_file_for_mode(mode)
        relative_path = env_path.relative_to(ROOT).as_posix()

        assert env_path.exists(), (
            f"{script_name} uses mode '{mode}' but {relative_path} is missing"
        )
        assert relative_path in tracked_paths(), (
            f"{script_name} uses mode '{mode}' but {relative_path} is not tracked"
        )

        values = parse_env(env_path)
        assert "VITE_BASE_URL" in values, f"{relative_path} must define VITE_BASE_URL"
        assert "VITE_API_URL" in values, f"{relative_path} must define VITE_API_URL"
        checked += 1

    assert checked >= 6, "the vite script inventory shrank unexpectedly"


def test_local_env_file_stays_ignored_and_untracked():
    relative_path = "frontend/admin-vue3/.env.local"

    assert is_git_ignored(relative_path), (
        f"{relative_path} must stay git ignored so a developer machine cannot "
        "override the production or container configuration"
    )
    assert relative_path not in tracked_paths(), (
        f"{relative_path} must not be version controlled"
    )


def test_every_mode_declares_the_captcha_switch_explicitly():
    """A developer machine .env.local must never disable the captcha.

    Vite loads `.env.local` for every mode, so any mode that does not declare
    `VITE_APP_CAPTCHA_ENABLE` itself inherits the machine local value.
    """
    modes = {mode_of(script) for _, script in vite_scripts()}

    for mode in sorted(modes):
        env_path = env_file_for_mode(mode)
        values = parse_env(env_path)
        assert "VITE_APP_CAPTCHA_ENABLE" in values, (
            f"{env_path.name} must declare VITE_APP_CAPTCHA_ENABLE explicitly"
        )

        expected = "false" if mode == "localdev" else "true"
        assert values["VITE_APP_CAPTCHA_ENABLE"] == expected, (
            f"{env_path.name} must set VITE_APP_CAPTCHA_ENABLE={expected}"
        )
