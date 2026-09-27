import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('release_helper', Path(__file__).with_name('prepare-android-release.py'))
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class AndroidReleaseMetadataTest(unittest.TestCase):
    def test_existing_first_release_stays_compatible(self):
        self.assertEqual(helper.version_code('26.9.27'), 26092701)
        self.assertEqual(helper.release_tag('26.9.27'), 'v26.9.27')

    def test_same_day_revision_is_upgradable_without_changing_display_version(self):
        self.assertEqual(helper.version_code('26.9.27', 2), 26092702)
        self.assertEqual(helper.release_tag('26.9.27', 2), 'v26.9.27-r2')
        self.assertLess(helper.version_code('26.9.27', 99), helper.version_code('26.9.28'))

    def test_invalid_revision_and_date_are_rejected(self):
        for revision in (0, -1, 100):
            with self.assertRaises(ValueError):
                helper.version_code('26.9.27', revision)
        for version in ('26.2.30', '26.09.27', '26.9.27.2', '26.9.27\nBAD=value'):
            with self.assertRaises(ValueError):
                helper.version_code(version)

    def test_manifest_points_to_revision_asset_and_matches_the_built_apk(self):
        with tempfile.TemporaryDirectory() as temporary:
            original = Path.cwd()
            try:
                os.chdir(temporary)
                directory = Path('app/build/outputs/apk/release')
                directory.mkdir(parents=True)
                apk = directory / 'ECHOAndroid-26.9.27-release.apk'
                apk.write_bytes(b'release fixture')
                (directory / 'output-metadata.json').write_text(json.dumps({'elements': [{
                    'versionCode': 26092702, 'versionName': '26.9.27', 'outputFile': apk.name,
                }]}))
                with patch.dict(os.environ, RELEASE_VERSION='26.9.27', RELEASE_REVISION='2'), patch('sys.argv', ['helper', '--metadata']):
                    helper.main()
                metadata = json.loads((directory / 'update.json').read_text())
                self.assertEqual(metadata['versionCode'], 26092702)
                self.assertIn('/v26.9.27-r2/', metadata['apkUrl'])
                self.assertEqual(metadata['size'], apk.stat().st_size)
                self.assertEqual(metadata['sha256'], hashlib.sha256(apk.read_bytes()).hexdigest())
            finally:
                os.chdir(original)


if __name__ == '__main__':
    unittest.main()
