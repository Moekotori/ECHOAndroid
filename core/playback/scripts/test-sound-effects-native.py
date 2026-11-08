"""Short native signal regression; uses the same portable sources as Android JNI."""
from pathlib import Path
import shutil
import subprocess
import sys
import json
import re

module = Path(__file__).resolve().parents[1]
root = module / 'third_party/sound-effects/engine'
catalogue = json.loads((root.parent / 'sources.json').read_text())['presets']
model = module.parents[1] / 'core/model/src/main/java/app/echo/android/model/playback/EchoSoundEffect.kt'
android_ids = re.findall(r'\("([a-z]+)"\)', model.read_text())
if android_ids != catalogue or len(catalogue) != 22:
    raise SystemExit('Android sound-effect IDs differ from the pinned PC catalogue')
output = Path(sys.argv[1]).resolve()
output.mkdir(parents=True, exist_ok=True)
compiler = shutil.which('g++') or shutil.which('clang++')
if not compiler:
    raise SystemExit('A C++20 g++ or clang++ compiler is required')
sources = [module / 'src/test/cpp/SoundEffectEngineTest.cpp', module / 'src/main/cpp/EchoSoundEffectEngine.cpp']
sources += [root / name for name in ('AdditionalSoundEffects.cpp','CharacterSoundEffects.cpp','ListeningColorEffects.cpp',
    'ModulationSoundEffects.cpp','EqProcessor.cpp','DeEsserProcessor.cpp')]
sources += list((root / 'third_party').glob('airwindows-*/*.cpp'))
executable = output / ('sound-effect-test.exe' if sys.platform == 'win32' else 'sound-effect-test')
command = [compiler, '-std=c++20', '-O1', '-I'+str(root), '-I'+str(root/'third_party/signalsmith/dsp/include'),
    '-I'+str(module/'src/main/cpp'), *map(str, sources), '-o', str(executable)]
subprocess.run(command, check=True)
subprocess.run([str(executable)], check=True, timeout=30)
