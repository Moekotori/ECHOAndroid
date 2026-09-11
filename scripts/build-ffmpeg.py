#!/usr/bin/env python3
"""Build the pinned Media3 audio decoder dependencies (macOS/Linux, Python 3.9+)."""
import argparse
import hashlib
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import urllib.request

VERSION = "6.1.6"
SHA256 = "d4fcb164028dd3beee5d92c0ac72e46aac6973c75ea12dc14de07bf8f407370a"
DECODERS = "aac,mp3,ac3,eac3,truehd,dca,vorbis,opus,amrnb,amrwb,flac,alac,pcm_mulaw,pcm_alaw,dsd_lsbf,dsd_msbf,dsd_lsbf_planar,dsd_msbf_planar"
TARGETS = {
    "armeabi-v7a": ("arm", "armv7a-linux-androideabi", ["--cpu=armv7-a", "--extra-cflags=-march=armv7-a -mfloat-abi=softfp"]),
    "arm64-v8a": ("aarch64", "aarch64-linux-android", ["--cpu=armv8-a"]),
    "x86_64": ("x86_64", "x86_64-linux-android", ["--cpu=x86-64", "--disable-x86asm"]),
}


def run(command, directory, log):
    with log.open("a") as output:
        result = subprocess.run(command, cwd=directory, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"Command failed: {command[0]}\n{log.read_text(errors='replace')[-6000:]}\nFull log: {log}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ndk", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    args = parser.parse_args()
    host = {"Darwin": "darwin-x86_64", "Linux": "linux-x86_64"}.get(platform.system())
    if host is None:
        parser.error("Build using Linux/WSL or macOS with an Android NDK for that host.")
    ndk, work = args.ndk.resolve(), args.work.resolve()
    tools = ndk / "toolchains/llvm/prebuilt" / host / "bin"
    if not (tools / "clang").is_file():
        parser.error(f"Missing NDK toolchain: {tools}")
    identity = hashlib.sha256(Path(__file__).read_bytes() + (ndk / "source.properties").read_bytes() + str(ndk).encode()).hexdigest()
    native = work / "native"
    stamp = native / "build-id"
    required = [native / abi / "lib" / f"lib{lib}.a" for abi in TARGETS for lib in ("avcodec", "avutil", "swresample")]
    if stamp.exists() and stamp.read_text() == identity and all(p.is_file() for p in required):
        print("FFmpeg audio libraries are up to date.", flush=True)
        return
    stamp.unlink(missing_ok=True)
    downloads = work / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    archive = downloads / f"ffmpeg-{VERSION}.tar.xz"
    if not archive.exists():
        print(f"Downloading FFmpeg {VERSION} from ffmpeg.org", flush=True)
        partial = archive.with_suffix(".part")
        with urllib.request.urlopen(f"https://ffmpeg.org/releases/{archive.name}", timeout=60) as response, partial.open("wb") as output:
            shutil.copyfileobj(response, output)
        partial.replace(archive)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
        raise RuntimeError(f"FFmpeg source checksum mismatch: remove {archive} and retry.")
    source = work / f"ffmpeg-{VERSION}"
    if source.exists():
        shutil.rmtree(source)
    with tarfile.open(archive) as package:
        # Verified, pinned archive; still reject traversal and external links.
        for member in package.getmembers():
            target = (work / member.name).resolve()
            if source != target and source not in target.parents:
                raise RuntimeError(f"Unsafe archive path: {member.name}")
            if member.issym() or member.islnk():
                raise RuntimeError(f"Unexpected archive link: {member.name}")
        package.extractall(work)
    jobs = str(min(os.cpu_count() or 4, 8))
    for abi, (arch, triple, extra) in TARGETS.items():
        print(f"Building FFmpeg {VERSION}: {abi} ({jobs} jobs)", flush=True)
        build = work / "objects" / abi
        if build.exists():
            shutil.rmtree(build)
        build.mkdir(parents=True)
        prefix = native / abi
        if prefix.exists():
            shutil.rmtree(prefix)
        log = build / "build.log"
        configure = [str(source / "configure"), f"--prefix={prefix}",
            "--target-os=android", f"--arch={arch}", "--enable-cross-compile",
            f"--cc={tools / (triple + '26-clang')}", f"--cxx={tools / (triple + '26-clang++')}",
            f"--ar={tools / 'llvm-ar'}", f"--nm={tools / 'llvm-nm'}",
            f"--ranlib={tools / 'llvm-ranlib'}", f"--strip={tools / 'llvm-strip'}",
            "--enable-static", "--disable-shared", "--enable-pic", "--disable-autodetect",
            "--disable-everything", "--disable-programs", "--disable-doc",
            "--disable-avdevice", "--disable-avformat", "--disable-avfilter",
            "--disable-swscale", "--disable-postproc", "--disable-network",
            "--disable-gpl", "--disable-nonfree", "--disable-version3",
            "--enable-swresample", f"--enable-decoder={DECODERS}", *extra]
        run(configure, build, log)
        run(["make", f"-j{jobs}"], build, log)
        run(["make", "install-libs", "install-headers"], build, log)
    stamp.write_text(identity)
    print("FFmpeg audio libraries ready for all three ABIs.", flush=True)


if __name__ == "__main__":
    main()
