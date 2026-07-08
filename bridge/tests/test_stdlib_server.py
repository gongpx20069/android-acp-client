from __future__ import annotations

import io
import unittest

from android_acp_bridge.stdlib_server import _read_websocket_text


def _masked_frame(opcode: int, payload: bytes) -> bytes:
    mask = b"\x01\x02\x03\x04"
    masked = bytes(byte ^ mask[index % 4] for index, byte in enumerate(payload))
    return bytes([0x80 | opcode, 0x80 | len(payload)]) + mask + masked


class StdlibWebSocketTests(unittest.TestCase):
    def test_read_websocket_text_replies_to_ping_then_reads_text(self) -> None:
        input_stream = io.BytesIO(
            _masked_frame(0x9, b"ping") +
            _masked_frame(0x1, b"hello")
        )
        output_stream = io.BytesIO()

        self.assertEqual(_read_websocket_text(input_stream, output_stream), "hello")

        pong = output_stream.getvalue()
        self.assertEqual(pong[:2], b"\x8a\x04")
        self.assertEqual(pong[2:], b"ping")


if __name__ == "__main__":
    unittest.main()
