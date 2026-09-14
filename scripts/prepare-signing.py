"""Materialize CI secrets only for the release build; never log their values."""
import base64
import os
from pathlib import Path

root = Path(__file__).resolve().parents[1]
key = Path(os.environ["RUNNER_TEMP"]) / "przepisy-signing.jks"
key.write_bytes(base64.b64decode(os.environ["SIGNING_KEY_BASE64"], validate=True))
key.chmod(0o600)


def escape(value):
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("=", "\\=").replace(":", "\\:").replace(" ", "\\ ")


properties = {
    "storeFile": str(key),
    "storePassword": os.environ["SIGNING_STORE_PASSWORD"],
    "keyAlias": os.environ["SIGNING_KEY_ALIAS"],
    "keyPassword": os.environ["SIGNING_KEY_PASSWORD"],
}
target = root / "keystore.properties"
target.write_text("\n".join(f"{k}={escape(v)}" for k, v in properties.items()) + "\n")
target.chmod(0o600)
