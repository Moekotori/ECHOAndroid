"""Import only the portable, actually used PC sound-effect DSP and its notices.

Requires authenticated gh access to the canonical PC repository. No host/UI code.
Run manually; Android builds and playback never download code.
"""
import base64
import concurrent.futures
import hashlib
import json
import re
from pathlib import Path
import subprocess

REVISION = 'a8f1bd0091db9fd103144909ff36257d6de54b2f'
REPOSITORY = 'repos/moekotori/echosteam/'
MODULE = Path(__file__).resolve().parents[1]
DESTINATION = MODULE / 'third_party/sound-effects/engine'
NATIVE = 'native/audio-engine/'

def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', REPOSITORY + path], text=True))

portable = ['AdditionalSoundEffects.cpp', 'AdditionalSoundEffects.h', 'CharacterSoundEffects.cpp',
            'CharacterSoundEffects.h', 'ListeningColorEffects.cpp', 'ListeningColorEffects.h',
            'ModulationSoundEffects.cpp', 'ModulationSoundEffects.h', 'PairedSoundEffect.h', 'Bs2bCrossfeed.h',
            'EqProcessor.cpp', 'EqProcessor.h', 'EqBand.h', 'EqTypes.h', 'EqAuditionProcessor.h', 'EqSvf.h',
            'HeadphoneCorrectionParameters.h', 'DspMeasurement.h', 'DspSafetyLimiter.h',
            'DeEsserProcessor.cpp', 'DeEsserProcessor.h', 'DynamicsReduction.h', 'buffer.h']
files = [(NATIVE + name, name) for name in portable]
for folder in api('contents/' + NATIVE + 'third_party?ref=' + REVISION):
    if folder['name'] not in ('signalsmith', 'airwindows-tape', 'airwindows-colors', 'airwindows-modulation', 'airwindows-character'):
        continue
    tree = api('git/trees/' + folder['sha'] + '?recursive=1')['tree']
    for entry in tree:
        if entry['type'] != 'blob' or entry['path'].endswith('.gitattributes'):
            continue
        relative = 'third_party/' + folder['name'] + '/' + entry['path']
        files.append((NATIVE + relative, relative))

def download(item):
    source, relative = item
    metadata = api('contents/' + source + '?ref=' + REVISION)
    data = base64.b64decode(metadata['content'])
    target = DESTINATION / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return {'path': relative, 'sha256': hashlib.sha256(data).hexdigest()}

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    hashes = list(pool.map(download, files))
for name in ('0001-stfx-arm-denormals.patch', '0002-eq-local-headroom.patch'):
    patch = DESTINATION.parent / 'patches' / name
    subprocess.run(['git', 'apply', '--directory=' + DESTINATION.relative_to(MODULE.parents[1]).as_posix(), str(patch)], cwd=MODULE.parents[1], check=True)
for entry in hashes:
    entry['upstream_sha256'] = entry['sha256']
    entry['sha256'] = hashlib.sha256((DESTINATION / entry['path']).read_bytes()).hexdigest()
catalogue = api('contents/src/shared/types/soundEffects.ts?ref=' + REVISION)
catalogue_text = base64.b64decode(catalogue['content']).decode('utf-8').split('] as const;')[0]
preset_ids = re.findall(r"'([a-z]+)'", catalogue_text)
(DESTINATION.parent / 'sources.json').write_text(json.dumps({
    'repository': 'https://github.com/moekotori/echosteam', 'revision': REVISION,
    'license': 'AGPL-3.0-only for ECHO adaptation; see each third-party LICENSE.txt',
    'patches': ['patches/0001-stfx-arm-denormals.patch', 'patches/0002-eq-local-headroom.patch'],
    'presets': preset_ids,
    'files': hashes,
}, indent=2) + '\n', encoding='utf-8')

notice_dir = MODULE / 'third_party/licenses/sound-effects'
notice_dir.mkdir(parents=True, exist_ok=True)
for folder in ('airwindows-tape', 'airwindows-colors', 'airwindows-modulation', 'airwindows-character'):
    (notice_dir / (folder + '-LICENSE.txt')).write_bytes((DESTINATION / 'third_party' / folder / 'LICENSE.txt').read_bytes())
for folder in ('basics', 'dsp'):
    (notice_dir / ('signalsmith-' + folder + '-LICENSE.txt')).write_bytes((DESTINATION / 'third_party/signalsmith' / folder / 'LICENSE.txt').read_bytes())
(notice_dir / 'signalsmith-stfx-LICENSE.txt').write_bytes((DESTINATION / 'third_party/signalsmith/basics/include/stfx/LICENSE.txt').read_bytes())
(notice_dir / 'NOTICE.txt').write_text(
    'ECHO Android sound effects: portable native algorithms and fixed preset parameters\n'
    'ported from https://github.com/moekotori/echosteam at ' + REVISION + '.\n'
    'ECHO portions are AGPL-3.0-only. No Electron, TypeScript, PC host or transport is included.\n'
    'Signalsmith Audio DSP/basics and Airwindows retain their MIT notices; STFX uses Boost Software License 1.0.\n'
    'Pinned upstream sources and ECHO adaptations are recorded in the vendored sources.json files.\n'
    'Android adaptation: correct ARM32 FPSCR/AArch64 FPCR denormal control and restore the original register.\n'
    'Android EQ adaptation: per-instance local clipping protection can be disabled to preserve intermediate headroom before the existing final output limiter.\n', encoding='utf-8')
print('Imported', len(files), 'portable DSP/dependency files at', REVISION)
