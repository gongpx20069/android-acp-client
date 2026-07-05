from __future__ import annotations

import json
import shutil
import subprocess
from dataclasses import dataclass
from enum import StrEnum
from typing import Any, Protocol


class TailscaleState(StrEnum):
    CLI_MISSING = "tailscale_cli_missing"
    NEEDS_LOGIN = "tailscale_needs_login"
    STOPPED = "tailscale_stopped"
    RUNNING = "tailscale_running"
    ERROR = "tailscale_error"


@dataclass(frozen=True)
class TailscaleStatus:
    state: TailscaleState
    backend_state: str | None = None
    tailscale_ips: tuple[str, ...] = ()
    dns_name: str | None = None
    auth_url: str | None = None
    user: str | None = None
    message: str | None = None

    @property
    def preferred_endpoint_host(self) -> str | None:
        if self.dns_name:
            return self.dns_name.rstrip(".")
        return self.tailscale_ips[0] if self.tailscale_ips else None


class CommandRunner(Protocol):
    def __call__(self, args: list[str], timeout: int) -> subprocess.CompletedProcess[str]:
        ...


def default_runner(args: list[str], timeout: int) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)


def get_status(runner: CommandRunner = default_runner, timeout: int = 5) -> TailscaleStatus:
    if shutil.which("tailscale") is None:
        return TailscaleStatus(
            state=TailscaleState.CLI_MISSING,
            message="Tailscale CLI was not found. Install Tailscale or use an explicit non-Tailscale endpoint.",
        )

    try:
        completed = runner(["tailscale", "status", "--json"], timeout)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return TailscaleStatus(state=TailscaleState.ERROR, message=str(exc))

    if completed.returncode != 0:
        return TailscaleStatus(
            state=TailscaleState.ERROR,
            message=(completed.stderr or completed.stdout or "tailscale status failed").strip(),
        )

    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError as exc:
        return TailscaleStatus(state=TailscaleState.ERROR, message=f"Invalid tailscale JSON: {exc}")

    return parse_status(payload)


def parse_status(payload: dict[str, Any]) -> TailscaleStatus:
    backend_state = _string_or_none(payload.get("BackendState"))
    auth_url = _string_or_none(payload.get("AuthURL"))
    user = _extract_user(payload)
    self_node = payload.get("Self") if isinstance(payload.get("Self"), dict) else {}
    tailscale_ips = tuple(ip for ip in self_node.get("TailscaleIPs", []) if isinstance(ip, str))
    dns_name = _string_or_none(self_node.get("DNSName"))

    if backend_state == "Running" and tailscale_ips:
        return TailscaleStatus(
            state=TailscaleState.RUNNING,
            backend_state=backend_state,
            tailscale_ips=tailscale_ips,
            dns_name=dns_name,
            auth_url=auth_url,
            user=user,
        )

    if backend_state == "NeedsLogin" or auth_url:
        return TailscaleStatus(
            state=TailscaleState.NEEDS_LOGIN,
            backend_state=backend_state,
            tailscale_ips=tailscale_ips,
            dns_name=dns_name,
            auth_url=auth_url,
            user=user,
            message="Tailscale is installed but needs login. Run `tailscale up` or `tailscale up --qr`.",
        )

    return TailscaleStatus(
        state=TailscaleState.STOPPED,
        backend_state=backend_state,
        tailscale_ips=tailscale_ips,
        dns_name=dns_name,
        auth_url=auth_url,
        user=user,
        message="Tailscale is installed but not running. Run `tailscale up`.",
    )


def build_websocket_endpoint(status: TailscaleStatus, port: int) -> str | None:
    host = status.preferred_endpoint_host
    if not host:
        return None
    return f"ws://{host}:{port}"


def _extract_user(payload: dict[str, Any]) -> str | None:
    user_value = payload.get("User")
    if isinstance(user_value, str):
        return user_value

    self_node = payload.get("Self") if isinstance(payload.get("Self"), dict) else {}
    user_id = self_node.get("UserID")
    user_map = payload.get("User") if isinstance(payload.get("User"), dict) else {}
    if user_id is not None and isinstance(user_map, dict):
        user_entry = user_map.get(str(user_id))
        if isinstance(user_entry, dict):
            return _string_or_none(user_entry.get("LoginName")) or _string_or_none(user_entry.get("DisplayName"))
    return None


def _string_or_none(value: Any) -> str | None:
    return value if isinstance(value, str) and value else None

