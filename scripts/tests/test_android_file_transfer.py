from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class AndroidFileTransferTests(unittest.TestCase):
    def test_one_shot_upload_uses_the_document_picker_grant_without_persisting_it(self):
        activity = (ROOT / "app/android/src/app/omaapp/FileTransferActivity.java").read_text(
            encoding="utf-8"
        )
        bridge = (ROOT / "app/android/src/app/omaapp/PairingBridge.java").read_text(
            encoding="utf-8"
        )

        upload_start = activity.index("private void pickUpload()")
        upload_end = activity.index("private void loadRemoteFiles()", upload_start)
        picker = activity[upload_start:upload_end]
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", picker)
        self.assertNotIn("takePersistableUriPermission", activity)
        self.assertIn("openInputStream(uri)", bridge)
