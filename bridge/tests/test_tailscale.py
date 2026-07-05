from __future__ import annotations

import unittest

from android_acp_bridge.tailscale import TailscaleState, build_websocket_endpoint, parse_status


class TailscaleTests(unittest.TestCase):
    def test_running_status_prefers_dns_name(self) -> None:
        status = parse_status(
            {
                "BackendState": "Running",
                "Self": {
                    "TailscaleIPs": ["100.64.0.10"],
                    "DNSName": "devbox.tailnet.ts.net.",
                },
            }
        )

        self.assertEqual(status.state, TailscaleState.RUNNING)
        self.assertEqual(status.preferred_endpoint_host, "devbox.tailnet.ts.net")
        self.assertEqual(build_websocket_endpoint(status, 4317), "ws://devbox.tailnet.ts.net:4317")

    def test_needs_login_when_auth_url_exists(self) -> None:
        status = parse_status({"BackendState": "NeedsLogin", "AuthURL": "https://login.tailscale.com/a/test"})

        self.assertEqual(status.state, TailscaleState.NEEDS_LOGIN)
        self.assertEqual(status.auth_url, "https://login.tailscale.com/a/test")

    def test_stopped_when_not_running(self) -> None:
        status = parse_status({"BackendState": "Stopped", "Self": {"TailscaleIPs": []}})

        self.assertEqual(status.state, TailscaleState.STOPPED)
        self.assertIsNone(build_websocket_endpoint(status, 4317))


if __name__ == "__main__":
    unittest.main()

