"""Check that the universal Android APK contains the playback libraries for each supported ABI."""

import argparse
from pathlib import Path
from zipfile import ZipFile


ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64"}
PLAYBACK_LIBRARIES = {"libecho_dsp.so", "libecho_usb_isoc.so", "libffmpegJNI.so"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()

    with ZipFile(args.apk) as apk:
        native_entries = {
            entry.filename for entry in apk.infolist()
            if entry.file_size > 0 and entry.filename.startswith("lib/") and entry.filename.endswith(".so")
        }

    missing = {f"lib/{abi}/{library}" for abi in ABIS for library in PLAYBACK_LIBRARIES} - native_entries
    unexpected_abis = {entry.split("/")[1] for entry in native_entries} - ABIS
    if missing:
        print("Missing APK libraries: " + ", ".join(sorted(missing)))
    if unexpected_abis:
        print("Unexpected APK ABIs: " + ", ".join(sorted(unexpected_abis)))
    if missing or unexpected_abis:
        raise SystemExit(1)

    print(f"{args.apk.name}: playback libraries verified for {', '.join(sorted(ABIS))}")


if __name__ == "__main__":
    main()
