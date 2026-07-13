from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


LAUNCHER_PATH = Path(__file__).resolve().parents[1] / "run.py"
LAUNCHER_SPEC = importlib.util.spec_from_file_location("bridge_launcher", LAUNCHER_PATH)
assert LAUNCHER_SPEC is not None
assert LAUNCHER_SPEC.loader is not None
launcher = importlib.util.module_from_spec(LAUNCHER_SPEC)
LAUNCHER_SPEC.loader.exec_module(launcher)


class LauncherTests(unittest.TestCase):
    def test_supported_python_is_accepted(self) -> None:
        launcher._require_supported_python((3, 11), "python")

    def test_unsupported_python_has_actionable_error(self) -> None:
        with self.assertRaisesRegex(SystemExit, "Activate a supported Conda/uv/venv environment"):
            launcher._require_supported_python((3, 10), "python")


if __name__ == "__main__":
    unittest.main()
