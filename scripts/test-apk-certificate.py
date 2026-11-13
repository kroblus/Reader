"""Regression coverage for old and scheme-labelled apksigner output."""

import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("apk_certificate", Path(__file__).with_name("read-apk-certificate.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
read_certificate = module.read_certificate
CERT = "f1a230a1f20d3041c808fabd8c150985949e6d830ce53b96cab00aa5e1efa5a5"
HEADER = "Verifies\nNumber of signers: 1\n"


class CertificateReportTest(unittest.TestCase):
    def test_legacy_output(self):
        self.assertEqual(CERT, read_certificate(HEADER + f"Signer #1 certificate SHA-256 digest: {CERT}\n"))

    def test_published_v034_format(self):
        self.assertEqual(CERT, read_certificate(HEADER + f"V3.0 Signer: certificate SHA-256 digest: {CERT}\n"))

    def test_same_identity_across_schemes(self):
        self.assertEqual(CERT, read_certificate(HEADER + "\n".join(
            f"V{scheme} Signer: certificate SHA-256 digest: {CERT}" for scheme in ("2", "3.0", "3.1")
        )))

    def test_uppercase_and_windows_lines(self):
        self.assertEqual(CERT, read_certificate((HEADER + f"V3.0 Signer: certificate SHA-256 digest: {CERT.upper()}\n").replace("\n", "\r\n")))

    def test_missing_certificate_is_rejected(self):
        with self.assertRaises(ValueError):
            read_certificate(HEADER)

    def test_malformed_or_unknown_certificate_is_rejected(self):
        for line in (f"Signer #1 certificate SHA-256 digest: {CERT[:-1]}",
                     f"Unknown certificate SHA-256 digest: {CERT}",
                     f"Signer #2 certificate SHA-256 digest: {CERT}"):
            with self.subTest(line=line), self.assertRaises(ValueError):
                read_certificate(HEADER + line)

    def test_multiple_identities_are_rejected(self):
        with self.assertRaises(ValueError):
            read_certificate(HEADER + f"V2 Signer: certificate SHA-256 digest: {CERT}\nV3.0 Signer: certificate SHA-256 digest: {'a' * 64}")

    def test_unverified_or_multiple_signer_reports_are_rejected(self):
        for header in ("", "DOES NOT VERIFY\nNumber of signers: 1\n", "Verifies\nNumber of signers: 2\n",
                       HEADER + "Number of signers: 2\n"):
            with self.subTest(header=header), self.assertRaises(ValueError):
                read_certificate(header + f"Signer #1 certificate SHA-256 digest: {CERT}")


if __name__ == "__main__":
    unittest.main()
