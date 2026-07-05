from __future__ import annotations

import shutil
from dataclasses import dataclass


@dataclass(frozen=True)
class AgentInfo:
    id: str
    display_name: str
    status: str

    def to_wire(self) -> dict[str, str]:
        return {"id": self.id, "displayName": self.display_name, "status": self.status}


def discover_agents() -> list[AgentInfo]:
    return [
        AgentInfo("claude-code", "Claude Code", _cli_status("claude")),
        AgentInfo("copilot-cli", "GitHub Copilot CLI", _cli_status("copilot")),
    ]


def _cli_status(command: str) -> str:
    return "available" if shutil.which(command) else "missing"

