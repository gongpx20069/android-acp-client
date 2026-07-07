from __future__ import annotations

import secrets
import threading
from dataclasses import dataclass
from typing import Any, Callable, Protocol

from . import __version__
from .acp_agent import AcpAgentError, AcpAgentManager, AcpPromptRequest
from .agents import discover_agents
from .config import BridgeConfig
from .pairing import PairingStore


@dataclass(frozen=True)
class DeviceInfo:
    name: str
    platform: str
    app_version: str


class PairingDeniedError(Exception):
    pass


class InvalidPairingTokenError(Exception):
    pass


@dataclass
class PendingApproval:
    options: list[dict[str, Any]]
    condition: threading.Condition
    decision: str | None = None


class AgentManager(Protocol):
    def prompt(self, request: AcpPromptRequest, permission_callback: Callable[[dict[str, Any]], str] | None = None) -> list[dict[str, Any]]:
        ...

    def list_sessions(self, agent_id: str, workspace_path: str) -> list[dict[str, Any]]:
        ...

    def load_session(self, chat_id: str, agent_id: str, workspace_path: str, session_id: str) -> list[dict[str, Any]]:
        ...

    def set_config_option(self, chat_id: str, config_id: str, value: str) -> list[dict[str, Any]]:
        ...


class BridgeRuntime:
    def __init__(
        self,
        config: BridgeConfig,
        pairing_store: PairingStore,
        require_local_pairing_confirmation: bool = True,
        agent_manager: AgentManager | None = None,
    ) -> None:
        self.config = config
        self.pairing_store = pairing_store
        self.require_local_pairing_confirmation = require_local_pairing_confirmation
        self.device_tokens: set[str] = set()
        self.agent_manager = agent_manager or AcpAgentManager()
        self._pending_approvals: dict[str, PendingApproval] = {}
        self._approval_lock = threading.Lock()

    def health_response(self) -> dict[str, Any]:
        return {"status": "ok", "bridgeVersion": __version__}

    def agents_response(self) -> dict[str, Any]:
        return {"agents": [agent.to_wire() for agent in discover_agents()]}

    def workspaces_response(self) -> dict[str, Any]:
        return {
            "workspaces": [
                {
                    "id": workspace.id,
                    "displayName": workspace.display_name,
                    "absolutePath": workspace.absolute_path,
                }
                for workspace in self.config.workspaces
            ]
        }

    def redeem_pairing(self, pairing_id: str, pairing_token: str, device: DeviceInfo) -> dict[str, str]:
        if not self.confirm_pairing(device):
            raise PairingDeniedError("Pairing was denied on the developer machine.")

        if not self.pairing_store.redeem(pairing_id, pairing_token):
            raise InvalidPairingTokenError("Pairing token is invalid, expired, or already used.")

        return {
            "machineId": self.config.machine_name,
            "deviceToken": self.issue_device_token(),
            "bridgeFingerprint": self.config.bridge_fingerprint,
        }

    def issue_device_token(self) -> str:
        token = "dev_" + secrets.token_urlsafe(32)
        self.device_tokens.add(token)
        return token

    def is_device_token_valid(self, token: str) -> bool:
        return token in self.device_tokens

    def websocket_responses(self, payload: Any, emit: Callable[[dict[str, Any]], None] | None = None) -> list[dict[str, Any]]:
        if not isinstance(payload, dict):
            return [{"type": "bridge.echo", "payload": payload}, {"type": "bridge.done"}]

        message_type = payload.get("type")
        if message_type == "chat.prompt":
            return self._chat_prompt_updates(payload, emit)
        if message_type == "session.list":
            return self._session_list_response(payload)
        if message_type == "session.load":
            return self._session_load_response(payload)
        if message_type == "session.setConfigOption":
            return self._session_set_config_option_response(payload)
        if message_type == "approval.decide":
            return self._approval_decision_updates(payload)
        return [{"type": "bridge.echo", "payload": payload}, {"type": "bridge.done"}]

    def confirm_pairing(self, device: DeviceInfo) -> bool:
        if not self.require_local_pairing_confirmation:
            return True

        prompt = f"Allow {device.name} ({device.platform}) to pair with this machine? [y/N] "
        try:
            answer = input(prompt)
        except EOFError:
            return False
        return answer.strip().lower() in {"y", "yes"}

    def _chat_prompt_updates(self, payload: dict[str, Any], emit: Callable[[dict[str, Any]], None] | None) -> list[dict[str, Any]]:
        chat_id = _string_or_default(payload.get("chatId"), "unknown-chat")
        prompt = _string_or_default(payload.get("content"), "")
        agent_id = _string_or_default(payload.get("agentId"), "copilot-cli")
        workspace_path = _string_or_default(payload.get("workspacePath"), "")
        try:
            updates = self.agent_manager.prompt(
                AcpPromptRequest(
                    chat_id=chat_id,
                    agent_id=agent_id,
                    workspace_path=workspace_path,
                    prompt=prompt,
                ),
                permission_callback=lambda message: self._request_permission(chat_id, message, emit),
            )
        except AcpAgentError as exc:
            updates = [
                {
                    "type": "session/update",
                    "chatId": chat_id,
                    "update": {
                        "sessionUpdate": "tool_call_update",
                        "toolCallId": "agent_start",
                        "title": "Agent runtime",
                        "kind": "execute",
                        "status": "failed",
                        "content": {"error": str(exc)},
                    },
                }
            ]
        for update in updates:
            update.setdefault("chatId", chat_id)
        return updates + [{"type": "bridge.done", "chatId": chat_id}]

    def _approval_decision_updates(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        approval_id = _string_or_default(payload.get("approvalId"), "unknown-approval")
        decision = _string_or_default(payload.get("decision"), "unknown")
        resolved = self._resolve_approval(approval_id, decision)
        return [
            {
                "type": "session/update",
                "update": {
                    "sessionUpdate": "tool_call_update",
                    "toolCallId": approval_id,
                    "title": "Approval decision",
                    "kind": "approval",
                    "status": "completed" if resolved else "failed",
                    "content": {"decision": decision, "resolved": resolved},
                },
            },
            {"type": "bridge.done"},
        ]

    def _session_list_response(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        agent_id = _string_or_default(payload.get("agentId"), "copilot-cli")
        workspace_path = _string_or_default(payload.get("workspacePath"), "")
        try:
            sessions = self.agent_manager.list_sessions(agent_id, workspace_path)
            return [{"type": "session.list.result", "sessions": sessions}, {"type": "bridge.done"}]
        except AcpAgentError as exc:
            return [{"type": "session.list.result", "sessions": [], "error": str(exc)}, {"type": "bridge.done"}]

    def _session_load_response(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        chat_id = _string_or_default(payload.get("chatId"), "unknown-chat")
        agent_id = _string_or_default(payload.get("agentId"), "copilot-cli")
        workspace_path = _string_or_default(payload.get("workspacePath"), "")
        session_id = _string_or_default(payload.get("sessionId"), "")
        try:
            updates = self.agent_manager.load_session(chat_id, agent_id, workspace_path, session_id)
        except AcpAgentError as exc:
            updates = [
                {
                    "type": "session/update",
                    "chatId": chat_id,
                    "update": {
                        "sessionUpdate": "tool_call_update",
                        "toolCallId": "session_load",
                        "title": "Resume session",
                        "kind": "other",
                        "status": "failed",
                        "content": {"error": str(exc)},
                    },
                }
            ]
        for update in updates:
            update.setdefault("chatId", chat_id)
        return updates + [{"type": "bridge.done", "chatId": chat_id}]

    def _request_permission(self, chat_id: str, message: dict[str, Any], emit: Callable[[dict[str, Any]], None] | None) -> str:
        params = message.get("params") if isinstance(message.get("params"), dict) else {}
        options = params.get("options") if isinstance(params, dict) and isinstance(params.get("options"), list) else []
        tool_call = params.get("toolCall") if isinstance(params, dict) and isinstance(params.get("toolCall"), dict) else {}
        approval_id = "approval_" + secrets.token_urlsafe(12)
        pending = PendingApproval(options=options, condition=threading.Condition())
        with self._approval_lock:
            self._pending_approvals[approval_id] = pending

        requested = {
            "type": "approval.requested",
            "approvalId": approval_id,
            "chatId": chat_id,
            "action": _string_or_default(tool_call.get("kind"), "tool_permission"),
            "summary": _string_or_default(tool_call.get("title"), "Agent requests permission"),
            "details": tool_call,
            "options": options,
        }
        if emit is not None:
            emit(requested)

        with pending.condition:
            pending.condition.wait(timeout=300)
            decision = pending.decision or "denied"

        with self._approval_lock:
            self._pending_approvals.pop(approval_id, None)

        return _select_permission_option(options, decision)

    def _resolve_approval(self, approval_id: str, decision: str) -> bool:
        with self._approval_lock:
            pending = self._pending_approvals.get(approval_id)
        if pending is None:
            return False
        with pending.condition:
            pending.decision = decision
            pending.condition.notify_all()
        return True

    def _session_set_config_option_response(self, payload: dict[str, Any]) -> list[dict[str, Any]]:
        chat_id = _string_or_default(payload.get("chatId"), "unknown-chat")
        config_id = _string_or_default(payload.get("configId"), "")
        value = _string_or_default(payload.get("value"), "")
        try:
            updates = self.agent_manager.set_config_option(chat_id, config_id, value)
        except AcpAgentError as exc:
            updates = [
                {
                    "type": "session/update",
                    "chatId": chat_id,
                    "update": {
                        "sessionUpdate": "tool_call_update",
                        "toolCallId": "set_config_option",
                        "title": "Set config",
                        "kind": "other",
                        "status": "failed",
                        "content": {"error": str(exc)},
                    },
                }
            ]
        for update in updates:
            update.setdefault("chatId", chat_id)
        return updates + [{"type": "bridge.done", "chatId": chat_id}]


def parse_device_info(value: Any) -> DeviceInfo | None:
    if not isinstance(value, dict):
        return None

    name = value.get("name")
    platform = value.get("platform")
    app_version = value.get("appVersion")
    if not isinstance(name, str) or not name.strip():
        return None
    if not isinstance(platform, str) or not platform.strip():
        return None
    if not isinstance(app_version, str) or not app_version.strip():
        return None

    return DeviceInfo(name=name.strip(), platform=platform.strip(), app_version=app_version.strip())


def _string_or_default(value: Any, default: str) -> str:
    return value if isinstance(value, str) else default


def _select_permission_option(options: list[dict[str, Any]], decision: str) -> str:
    target_prefix = "allow" if decision == "approved" else "reject"
    fallback = "allow-once" if decision == "approved" else "reject-once"
    match = next((item for item in options if isinstance(item, dict) and str(item.get("kind", "")).startswith(target_prefix)), None)
    if match is None and options:
        match = options[0]
    return str((match or {}).get("optionId", fallback))
