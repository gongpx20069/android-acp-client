from __future__ import annotations

import sys
from pathlib import Path

MINIMUM_PYTHON = (3, 11)


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    _require_supported_python(sys.version_info[:2], sys.executable)

    bridge_root = Path(__file__).resolve().parent
    sys.path.insert(0, str(bridge_root / "src"))

    if not args:
        args = ["start"]

    try:
        from android_acp_bridge.main import main as bridge_main
    except ModuleNotFoundError as exc:
        print(
            f"Missing Python package '{exc.name}'. Install the bridge dependencies first; "
            "see bridge/README.md for Conda, uv, and Python venv commands.",
            file=sys.stderr,
        )
        return 1
    return bridge_main(args)


def _require_supported_python(version: tuple[int, int], executable: str) -> None:
    if version >= MINIMUM_PYTHON:
        return
    current = ".".join(str(part) for part in version)
    required = ".".join(str(part) for part in MINIMUM_PYTHON)
    raise SystemExit(
        f"AgentLink bridge requires Python {required} or newer, but {executable} is Python {current}. "
        "Activate a supported Conda/uv/venv environment, then run the command again."
    )


if __name__ == "__main__":
    raise SystemExit(main())
