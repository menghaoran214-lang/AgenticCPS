import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("utf8_integrity", Path(__file__).resolve().parents[1] / "check_utf8_integrity.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

def test_utf8_chinese():
    assert checker.check_bytes("valid.md", "中文内容".encode("utf-8")) == []

def test_invalid_utf8():
    assert "invalid UTF-8" in checker.check_bytes("invalid.md", b"\xff")[0]

def test_bom():
    assert "BOM" in checker.check_bytes("bom.md", b"\xef\xbb\xbfHello")[0]

def test_replacement_character():
    assert "U+FFFD" in checker.check_bytes("bad.md", "\ufffd".encode("utf-8"))[0]
