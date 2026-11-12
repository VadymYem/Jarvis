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
import jarvis_work
import os


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

    def verify_work(self, paths, tests=None):
        previous = Path.cwd()
        try:
            os.chdir(self.root)
            return jarvis_work.verify(paths, tests)
        finally:
            os.chdir(previous)

    def test_work_executes_tests_against_repaired_python(self):
        (self.root / 'app.py').write_text('def answer(): return 1\n')
        (self.root / 'test_app.py').write_text('import unittest\nfrom app import answer\nclass TestApp(unittest.TestCase):\n    def test_answer(self): self.assertEqual(answer(), 2)\n')
        self.assertFalse(self.verify_work(['app.py'], ['test_app.py'])['passed'])
        (self.root / 'app.py').write_text('def answer(): return 2\n')
        report = self.verify_work(['app.py'], ['test_app.py'])
        self.assertTrue(report['passed'], report)
        self.assertTrue(report['fully_tested'])

    def test_python_syntax_alone_does_not_claim_functional_testing(self):
        (self.root / 'app.py').write_text('def answer(): return 1\n')
        report = self.verify_work(['app.py'])
        self.assertTrue(report['passed'], report)
        self.assertFalse(report['fully_tested'])
        self.assertTrue(report['unsupported'])

    def test_work_rejects_incomplete_and_invalid_artifacts(self):
        (self.root / 'index.html').write_text('<html><head></head><body><img src="missing.png"></body></html>')
        self.assertFalse(self.verify_work(['index.html'])['passed'])
        (self.root / 'bad.py').write_text('def broken(')
        self.assertFalse(self.verify_work(['bad.py'])['passed'])
        self.assertFalse(self.verify_work(['../outside.txt'])['passed'])
        self.assertFalse(self.verify_work([])['passed'])
        (self.root / 'test_empty.py').write_text('x = 1')
        self.assertFalse(self.verify_work(['test_empty.py'], ['test_empty.py'])['passed'])

    def test_work_reports_real_artifact_checks(self):
        result = self.run_script('from jarvis_artifacts import create_image, create_pdf, create_docx\ncreate_image("card.png", title="Їжак")\ncreate_pdf("report.pdf", "Український текст")\ncreate_docx("report.docx", "Тест")')
        self.assertIsNone(result['error'])
        report = self.verify_work(['card.png', 'report.pdf', 'report.docx'])
        self.assertTrue(report['passed'], report)
        self.assertEqual(len(report['checks']), 3)
        self.assertFalse(report['fully_tested'])

    def test_text_formats_are_preserved(self):
        for extension in ("txt", "py", "html", "js", "css", "json", "csv", "md"):
            file = self.root / f"source.{extension}"
            file.write_text("first\n    Український текст\n", encoding="utf-8")
            self.assertEqual(jarvis_documents.extract(file), "first\n    Український текст\n")


if __name__ == "__main__":
    unittest.main()
