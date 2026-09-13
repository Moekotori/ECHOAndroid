"""Release workflow helper: versioning, temporary signing config, updater manifest."""
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import sys


def version_code(version):
    if not re.fullmatch(r"[0-9]{2}\.[1-9][0-9]?\.[1-9][0-9]?", version):
        raise ValueError("Expected YY.M.D, for example 26.9.13")
    year, month, day = map(int, version.split('.'))
    datetime.date(2000 + year, month, day)
    # Keep the visible version date-only; one release per date.
    return (year * 10000 + month * 100 + day) * 100 + 1


def property_escape(value):
    encoded = value.encode('utf-16-be')
    return ''.join(f'\\u{int.from_bytes(encoded[i:i+2], "big"):04x}' for i in range(0, len(encoded), 2))


def main():
    version = os.environ['RELEASE_VERSION']
    code = version_code(version)
    if '--metadata' in sys.argv:
        directory = Path('app/build/outputs/apk/release')
        output = json.loads((directory / 'output-metadata.json').read_text())
        if len(output['elements']) != 1:
            raise ValueError('Expected one universal APK')
        element = output['elements'][0]
        if element['versionCode'] != code or element['versionName'] != version:
            raise ValueError('APK version differs from requested release')
        apk = directory / element['outputFile']
        with apk.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        manifest = dict(schemaVersion=1, versionCode=code, versionName=version,
                        apkUrl=f'https://github.com/moekotori/echoandroid/releases/download/v{version}/{apk.name}',
                        size=apk.stat().st_size, sha256=digest)
        (directory / 'update.json').write_text(json.dumps(manifest, indent=2) + '\n')
        return
    names = ['KEYSTORE_BASE64', 'STORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD']
    if not all(os.environ.get(name) for name in names):
        raise ValueError('Configure all four Android signing secrets before releasing')
    Path('release-signing.jks').write_bytes(base64.b64decode(os.environ['KEYSTORE_BASE64'], validate=True))
    values = {'storeFile': 'release-signing.jks', 'storePassword': os.environ['STORE_PASSWORD'],
              'keyAlias': os.environ['KEY_ALIAS'], 'keyPassword': os.environ['KEY_PASSWORD']}
    Path('keystore.properties').write_text(''.join(f'{k}={property_escape(v)}\n' for k, v in values.items()))
    with open(os.environ['GITHUB_ENV'], 'a') as env:
        env.write(f'RELEASE_VERSION={version}\nRELEASE_CODE={code}\n')


if __name__ == '__main__':
    main()
