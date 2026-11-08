from pathlib import Path
import subprocess, shutil, sys
module=Path(__file__).resolve().parents[1]
vendor=module/'third_party/sound-effects/engine'
output=Path(sys.argv[1]).resolve();output.mkdir(parents=True,exist_ok=True)
compiler=shutil.which('g++') or shutil.which('clang++')
if not compiler: raise SystemExit('A C++20 compiler is required')
exe=output/('pc-eq-test.exe' if sys.platform=='win32' else 'pc-eq-test')
subprocess.run([compiler,'-std=c++20','-O1','-I'+str(vendor),'-I'+str(module/'src/main/cpp'),
    str(module/'src/test/cpp/PcEqualizerTest.cpp'),str(module/'src/main/cpp/EchoPcEqualizer.cpp'),
    str(vendor/'EqProcessor.cpp'),'-o',str(exe)],check=True)
subprocess.run([str(exe)],check=True,timeout=30)
