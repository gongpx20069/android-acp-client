from __future__ import annotations

import secrets
from typing import Any

from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

from . import __version__
from .agents import discover_agents
from .config import BridgeConfig
from .pairing import PairingStore


class DeviceInfo(BaseModel):
    name: str = Field(min_length=1)
    platform: str = Field(min_length=1)
    app_version: str = Field(alias="appVersion", min_length=1)


class PairingRedeemRequest(BaseModel):
    pairing_id: str = Field(alias="pairingId", min_length=1)
    pairing_token: str = Field(alias="pairingToken", min_length=1)
    device: DeviceInfo


class BridgeRuntime:
    def __init__(self, config: BridgeConfig, pairing_store: PairingStore, require_local_pairing_confirmation: bool = True) -> None:
        self.config = config
        self.pairing_store = pairing_store
        self.require_local_pairing_confirmation = require_local_pairing_confirmation
        self.device_tokens: set[str] = set()

    def issue_device_token(self) -> str:
        token = "dev_" + secrets.token_urlsafe(32)
        self.device_tokens.add(token)
        return token

    def is_device_token_valid(self, token: str) -> bool:
        return token in self.device_tokens

    def confirm_pairing(self, device: DeviceInfo) -> bool:
        if not self.require_local_pairing_confirmation:
            return True

        prompt = f"Allow {device.name} ({device.platform}) to pair with this machine? [y/N] "
        try:
            answer = input(prompt)
        except EOFError:
            return False
        return answer.strip().lower() in {"y", "yes"}


def create_app(runtime: BridgeRuntime) -> FastAPI:
    app = FastAPI(title="Android ACP Bridge", version=__version__)

    @app.get("/health")
    def health() -> dict[str, Any]:
        return {"status": "ok", "bridgeVersion": __version__}

    @app.get("/agents")
    def agents() -> dict[str, Any]:
        return {"agents": [agent.to_wire() for agent in discover_agents()]}

    @app.get("/workspaces")
    def workspaces() -> dict[str, Any]:
        return {
            "workspaces": [
                {
                    "id": workspace.id,
                    "displayName": workspace.display_name,
                    "absolutePath": workspace.absolute_path,
                }
                for workspace in runtime.config.workspaces
            ]
        }

    @app.post("/pairing/redeem")
    def redeem_pairing(request: PairingRedeemRequest) -> dict[str, str]:
        if not runtime.confirm_pairing(request.device):
            raise HTTPException(status_code=403, detail="Pairing was denied on the developer machine.")

        if not runtime.pairing_store.redeem(request.pairing_id, request.pairing_token):
            raise HTTPException(status_code=401, detail="Pairing token is invalid, expired, or already used.")

        return {
            "machineId": runtime.config.machine_name,
            "deviceToken": runtime.issue_device_token(),
            "bridgeFingerprint": runtime.config.bridge_fingerprint,
        }

    @app.websocket("/ws")
    async def websocket_endpoint(websocket: WebSocket) -> None:
        token = websocket.query_params.get("token")
        if token is None or not runtime.is_device_token_valid(token):
            await websocket.close(code=1008)
            return

        await websocket.accept()
        try:
            while True:
                message = await websocket.receive_json()
                await websocket.send_json({"type": "bridge.echo", "payload": message})
        except WebSocketDisconnect:
            return

    return app
