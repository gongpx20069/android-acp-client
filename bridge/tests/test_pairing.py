from __future__ import annotations

import base64
import json
import unittest
from datetime import timedelta

from android_acp_bridge.pairing import PairingStore, build_pairing_payload, encode_pairing_deep_link, render_terminal_qr


class PairingTests(unittest.TestCase):
    def test_pairing_token_is_one_time_use(self) -> None:
        store = PairingStore()
        token = store.create()

        self.assertTrue(store.redeem(token.pairing_id, token.pairing_token))
        self.assertFalse(store.redeem(token.pairing_id, token.pairing_token))

    def test_expired_token_is_rejected(self) -> None:
        store = PairingStore()
        token = store.create(ttl=timedelta(seconds=-1))

        self.assertFalse(store.redeem(token.pairing_id, token.pairing_token))

    def test_deep_link_contains_base64url_payload(self) -> None:
        store = PairingStore()
        token = store.create()
        payload = build_pairing_payload(
            machine_name="devbox",
            endpoint="ws://100.64.0.10:4317",
            token=token,
            bridge_fingerprint="sha256:test",
        )

        link = encode_pairing_deep_link(payload)
        encoded = link.split("data=", 1)[1]
        decoded = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4))
        body = json.loads(decoded)

        self.assertEqual(body["type"], "acp-bridge-pairing")
        self.assertEqual(body["machineName"], "devbox")
        self.assertEqual(body["endpoint"], "ws://100.64.0.10:4317")

    def test_terminal_qr_is_rendered(self) -> None:
        qr = render_terminal_qr("acpclient://pair?data=test")

        self.assertIn("##", qr)
        self.assertGreater(len(qr.splitlines()), 1)


if __name__ == "__main__":
    unittest.main()
