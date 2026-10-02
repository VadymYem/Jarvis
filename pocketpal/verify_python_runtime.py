#!/usr/bin/env python3
"""Exercise the Android Python entry points with real files before Gradle."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile


source = Path(sys.argv.pop(1) if len(sys.argv) > 1 else "jarvis-src")
sys.dont_write_bytecode = True
sys.path.insert(0, str((source / "android/app/src/main/python").resolve()))
import jarvis_documents
import jarvis_runner


class PythonRuntimeTests(unittest.TestCase):
    def run_script(self, code):
        return json.loads(jarvis_runner.run(code, str(self.root)))

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def test_artifacts_have_real_bytes_and_round_trip_unicode(self):
        result = self.run_script('''from jarvis_artifacts import create_image, create_pdf, create_doc, create_docx
create_image("artifacts/card.png", title="Українська ніч", body="Вітаю, Авторе!")
create_pdf("artifacts/report.pdf", "Український документ", title="Звіт")
create_doc("artifacts/report.doc", "Привіт, Україно! 🎵")
create_docx("artifacts/report.docx", "Привіт, Україно! 🎵", title="Звіт")
from pathlib import Path
Path("artifacts/code.py").write_text("def hello():\\n    return 'Привіт'\\n", encoding="utf-8")
''')
        self.assertIsNone(result["error"])
        artifacts = {item["path"]: item for item in result["artifacts"]}
        self.assertEqual(set(artifacts), {"artifacts/card.png", "artifacts/report.pdf", "artifacts/report.doc", "artifacts/report.docx", "artifacts/code.py"})
        from PIL import Image
        from pypdf import PdfReader
        with Image.open(self.root / "artifacts/card.png") as image:
            self.assertEqual(image.size, (1024, 1024))
            image.verify()
        self.assertEqual(len(PdfReader(self.root / "artifacts/report.pdf").pages), 1)
        for extension in ("doc", "docx"):
            self.assertIn("Привіт, Україно! 🎵", jarvis_documents.extract(self.root / f"artifacts/report.{extension}"))
        self.assertEqual(jarvis_documents.extract(self.root / "artifacts/code.py"), "def hello():\n    return 'Привіт'\n")
        with zipfile.ZipFile(self.root / "artifacts/report.docx") as archive:
            self.assertIn("word/_rels/document.xml.rels", archive.namelist())
        for artifact in artifacts.values():
            self.assertGreater(artifact["size"], 0)
            self.assertTrue(artifact["uri"].startswith(self.root.as_uri()))

    def test_cyrillic_encodings(self):
        for encoding in ("utf-8", "utf-16", "cp1251"):
            file = self.root / f"{encoding}.txt"
            file.write_bytes("Українська мова\nДругий рядок".encode(encoding))
            self.assertEqual(jarvis_documents.extract(file), "Українська мова\nДругий рядок")

    def test_failed_script_can_be_corrected_on_next_call(self):
        failed = self.run_script("raise ValueError('помилка')")
        self.assertIn("ValueError", failed["error"])
        result = self.run_script("from pathlib import Path\nPath('fixed.txt').write_text('Виправлено', encoding='utf-8')")
        self.assertIsNone(result["error"])
        self.assertEqual(result["created"], ["fixed.txt"])

    def test_modified_existing_artifact_is_returned(self):
        self.run_script("from pathlib import Path\nPath('note.txt').write_text('one')")
        result = self.run_script("from pathlib import Path\nPath('note.txt').write_text('new content')")
        self.assertEqual(result["created"], ["note.txt"])
        self.assertEqual(result["artifacts"][0]["size"], 11)

    def test_text_formats_are_preserved(self):
        for extension in ("txt", "py", "html", "js", "css", "json", "csv", "md"):
            file = self.root / f"source.{extension}"
            file.write_text("first\n    Український текст\n", encoding="utf-8")
            self.assertEqual(jarvis_documents.extract(file), "first\n    Український текст\n")


if __name__ == "__main__":
    unittest.main()
