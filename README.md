# AgentLink

[English](README.md) | [中文](README.zh-CN.md)

AgentLink lets you control remote coding agents from an Android phone. Your developer machine runs a small bridge and the agent CLI; your phone becomes the control surface for chats, approvals, session resume, model selection, and agent updates.

AgentLink does not require opening inbound firewall ports on your developer machine.

## Install the Android app

Download and install the latest APK from the latest release:

**[Download AgentLink APK from Releases](https://github.com/gongpx20069/android-agent-link/releases/latest)**

On the release page, download the APK asset named like `agentlink-0.0.x.apk`.

If you previously installed a debug build, Android may require uninstalling it once before installing the signed release APK. After that, signed releases can update in place.

## Supported coding agents

AgentLink talks to agents through ACP (Agent Client Protocol). The bridge currently exposes agents that are explicitly wired in `bridge/src/android_acp_bridge/agents.py` and `acp_agent.py`.

| Agent | Current status | Required command on the developer machine | Notes |
| --- | --- | --- | --- |
| **GitHub Copilot CLI** | Supported now | `copilot --acp` | Primary tested path. The bridge launches `copilot --acp --allow-all --add-dir <workspace>`. |
| **Claude Code** | Supported now when installed | `claude --acp` | Exposed in the app when `claude` is on PATH. Requires a Claude Code CLI version that supports ACP. |
| **Gemini CLI** | Not wired yet | `gemini --acp` | Gemini CLI has ACP mode, but AgentLink does not yet expose `gemini-cli` in the bridge agent list. |
| **OpenAI Codex CLI** | Not wired yet | varies | No stable AgentLink integration yet. Needs an ACP-compatible CLI command before it can be added. |
| **Cursor Agent / Cursor CLI** | Not wired yet | varies | Not currently exposed by the bridge. |
| **Aider** | Not wired yet | `aider` | Aider is a popular coding assistant, but AgentLink does not currently have an ACP adapter for it. |
| **Qoder / QCode** | Not wired yet | not confirmed | No public AgentLink-compatible ACP CLI command is wired today. |
| **Alibaba Tongyi Lingma / CodeFuse** | Not wired yet | not confirmed | Public docs do not currently provide a stable `--acp` command for AgentLink to launch. |
| **ByteDance Doubao / MarsCode** | Not wired yet | not confirmed | No AgentLink-compatible ACP CLI command is wired today. |
| **Baidu Comate** | Not wired yet | not confirmed | No AgentLink-compatible ACP CLI command is wired today. |
| **CodeGeeX** | Not wired yet | not confirmed | No AgentLink-compatible ACP adapter is wired today. |

In the app, missing agents may still appear as unavailable/missing depending on bridge discovery. Only the “Supported now” agents can be selected for working chats today.

## Install the bridge

The bridge requires Python 3.11 or newer and never installs its own Python environment or packages at startup. Choose one installation method below and run it from the `bridge` directory.

### Conda

```powershell
cd bridge
conda env create -f environment.yml
conda activate android-acp-bridge
```

### uv

```powershell
cd bridge
uv venv --python 3.12
.\.venv\Scripts\Activate.ps1
uv pip install -r requirements.txt
```

### Python venv and pip

```powershell
cd bridge
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

Use `requirements-all.txt` instead of `requirements.txt` to install every optional backend. After installation, use the `android-acp-bridge` command shown below. The source helper `python .\run.py ...` forwards to the same CLI using the current Python environment; it does not create an environment or install packages.

See [`bridge/README.md`](bridge/README.md) for dependency groups and development details.

## Start the bridge

Run bridge commands from the repository root on your developer machine.

### Option A: Microsoft Dev Tunnels (default)

This authenticated relay is the default because Android does not need a Tailscale, ZeroTier, or other companion networking app. You only need to sign in to Microsoft Dev Tunnels on the developer machine.

```powershell
android-acp-bridge start
```

The bridge will:

1. Find `devtunnel` or download `bridge\.tools\devtunnel.exe` on Windows.
2. Ask you to sign in with device-code login if needed.
3. Create or reuse a private `agentlink` tunnel.
4. Start the tunnel host and local bridge.
5. Print a pairing link and QR code for Android.

Do **not** enable anonymous/public Dev Tunnel access. AgentLink uses a short-lived `X-Tunnel-Authorization` token in the pairing QR.

### Option B: Tailscale

Tailscale is an optional private-network transport. Install the Tailscale app on both the Android device and developer machine, sign both into the same tailnet, and keep both connected.

```powershell
android-acp-bridge start --transport tailscale
```

The bridge checks Tailscale, runs `tailscale up --qr` if login is needed, waits for a Tailscale IP, then prints a pairing link and QR code.

ZeroTier has the same device requirement: both the developer machine and Android need the ZeroTier client and must join the same network. AgentLink does not currently automate ZeroTier setup.

If Windows blocks Tailscale install with organization policy / exit code `1625`, install Tailscale through your company software portal, ask an administrator to approve `Tailscale.Tailscale`, or use the official installer from <https://tailscale.com/download/windows>.

### Local testing only

```powershell
android-acp-bridge start --transport local
```

This is only useful for local/manual tests. It does not make your developer machine reachable from your phone by itself.

## Pair a machine

1. Open AgentLink on Android.
2. Go to **Machines**.
3. Tap **Scan QR**.
4. Scan the QR printed by the bridge.
5. Confirm pairing on the developer machine when prompted.
6. Use **Test Connection** to verify the bridge is reachable.

The QR contains a short-lived pairing token. If it expires, restart or re-run the bridge command and scan again.

## Start a chat

1. Go to **Chats**.
2. Tap **New Chat**.
3. Select the paired machine.
4. Select an agent, such as GitHub Copilot CLI.
5. Enter the remote workspace path, for example:

```text
C:\Repos\my-project
```

6. Create the chat and send a prompt.

AgentLink shows streaming agent replies, tool activity cards, model/command chips, and approval requests.

## Resume an existing session

In **New Chat**, switch to **Existing session**, load sessions from the selected machine/agent, and open one.

AgentLink asks the bridge for a recent-history snapshot instead of showing the full old replay. The number of recent messages can be adjusted in **Settings**.

## Approvals

When the agent requests permission for a command or risky operation, AgentLink shows it in the **Approvals** tab. Approve or deny from the phone; the bridge continues only after the decision is received.

## Troubleshooting

### Android cannot connect after scanning

- Make sure the bridge process is still running.
- For Dev Tunnels, re-run the bridge command if the connect token expired.
- For Tailscale, make sure Android and the developer machine are in the same tailnet.
- Check that the Android machine card uses the expected endpoint.

### Dev Tunnel login or tunnel creation fails

Run:

```powershell
.\bridge\.tools\devtunnel.exe user login -d
```

or, if `devtunnel` is on PATH:

```powershell
devtunnel user login -d
```

Then retry:

```powershell
android-acp-bridge start --transport devtunnel
```

### Copilot or Claude agent is not available

Install and sign in to the CLI on the developer machine, then confirm one of these commands works:

```powershell
copilot --acp
claude --acp
```

## For contributors

This README is for users. Development and agent instructions live in:

- `CLAUDE.md`
- `docs/README.md`
- `docs/architecture.md`
- `docs/acp-bridge-contract.md`
- `docs/android-app.md`
- `docs/security-model.md`
