# Android ACP Bridge

Python MVP bridge for pairing Android ACP Client with a remote developer machine.

## Development

```powershell
cd bridge
python -m venv .venv
.\.venv\Scripts\python -m pip install -e .
.\.venv\Scripts\android-acp-bridge start
```

The bridge checks Tailscale status, starts a local HTTP/WebSocket server, creates a short-lived pairing token, and prints an Android pairing QR payload.

## Commands

```powershell
android-acp-bridge start
android-acp-bridge tailscale-status
android-acp-bridge pairing
```

`start` does not run `tailscale up` automatically by default. It prints guidance when Tailscale is missing, stopped, or needs login.

