"""Read one public signer identity from a successful apksigner verify report."""

import re
import sys
from pathlib import Path


def read_certificate(report: str) -> str:
    lines = [line.strip() for line in report.splitlines()]
    if "Verifies" not in lines or lines.count("Number of signers: 1") != 1:
        raise ValueError("Expected a verified APK with exactly one signer")
    if any(line.startswith("Number of signers:") and line != "Number of signers: 1" for line in lines):
        raise ValueError("Conflicting signer counts")
    pattern = re.compile(
        r"(?:Signer #1|V[0-9]+(?:\.[0-9]+)? Signer:) certificate SHA-256 digest: ([0-9a-fA-F]{64})"
    )
    certificates = set()
    for line in lines:
        if "certificate SHA-256 digest:" not in line:
            continue
        match = pattern.fullmatch(line)
        if match is None:
            raise ValueError("Unrecognized signer certificate line")
        certificates.add(match.group(1).lower())
    if len(certificates) != 1:
        raise ValueError("Expected one unambiguous signer certificate")
    return certificates.pop()


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("Usage: read-apk-certificate.py SIGNATURE_REPORT")
        print(read_certificate(Path(sys.argv[1]).read_text(encoding="utf-8")))
    except (ValueError, OSError) as error:
        sys.exit(str(error))
