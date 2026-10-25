"""Exercise the complete production USB writer with fake JNI and USB ioctls.

Run on Linux/WSL: CXX=g++ python3 core/usb-audio/src/test/cpp/run_transport_diagnostics.py
No DAC is required; this checks counters and lifecycle semantics, not hardware behavior.
"""
from pathlib import Path
import os
import subprocess
import tempfile

production = Path(__file__).resolve().parents[2] / "main/cpp"
fake_jni = r"""
#pragma once
#include <cstdint>
#include <vector>
#define JNIEXPORT
#define JNICALL
#define JNI_ABORT 0
#define JNI_TRUE true
using jint=int; using jlong=int64_t; using jclass=void*; using jboolean=bool;
using jbyte=signed char;
using jbyteArray=std::vector<jbyte>*;
using jlongArray=std::vector<jlong>*;
struct JNIEnv {
 jbyte* GetByteArrayElements(jbyteArray p,void*) {return p->data();}
 int GetArrayLength(jbyteArray p) {return (int)p->size();}
 int GetArrayLength(jlongArray p) {return (int)p->size();}
 void ReleaseByteArrayElements(jbyteArray,jbyte*,int){}
 void SetLongArrayRegion(jlongArray p,int start,int count,const jlong* values) {
  for(int i=0;i<count;i++) (*p)[start+i]=values[i];
 }
};
"""
test = r"""
#include <cassert>
#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <deque>
#include <unordered_set>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>
#include "jni.h"
static std::deque<usbdevfs_urb*> completed;
static std::unordered_set<usbdevfs_urb*> submitted;
static int fail_submit=0,fail_reap=0,ioctls=0;
static int test_ioctl(int,unsigned long request,...) {
 ++ioctls;
 if(request==_IO('U',31)) return 3;
 va_list args;va_start(args,request);void* value=va_arg(args,void*);va_end(args);
 if(request==USBDEVFS_SUBMITURB) {
  if(fail_submit){errno=fail_submit;fail_submit=0;return -1;}
  submitted.insert((usbdevfs_urb*)value);return 0;
 }
 if(request==USBDEVFS_REAPURBNDELAY) {
  if(fail_reap){errno=fail_reap;fail_reap=0;return -1;}
  if(completed.empty()){errno=EAGAIN;return -1;}
  auto* u=completed.front();completed.pop_front();submitted.erase(u);
  *(usbdevfs_urb**)value=u;return 0;
 }
 if(request==USBDEVFS_DISCARDURB) {
  auto* u=(usbdevfs_urb*)value;
  if(submitted.count(u)) {
   u->status=-ENOENT;
   for(int p=0;p<u->number_of_packets;p++)u->iso_frame_desc[p].status=-ENOENT;
   completed.push_back(u);
  }
  return 0;
 }
 return 0;
}
#define ioctl test_ioctl
#include "echo_usb_isoc.cpp"
#undef ioctl
static void complete(usbdevfs_urb* u,int status=0) {
 u->status=status;
 for(int p=0;p<u->number_of_packets;p++) {
  u->iso_frame_desc[p].actual_length=u->iso_frame_desc[p].length;
  u->iso_frame_desc[p].status=0;
 }
 completed.push_back(u);
}
int main() {
 JNIEnv env;
 auto create=[&](int feedback) {
  return Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeCreate(
   &env,nullptr,3,1,192,48000,2,2,8000,feedback,4,1,16);
 };
 auto snapshot=[&](jlong handle) {
  std::vector<jlong> values(17);
  int calls=ioctls;
  Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeSnapshot(&env,nullptr,handle,&values);
  assert(calls==ioctls); // Diagnostics must never reap or perform USB I/O.
  return values;
 };
 auto handle=create(0);assert(handle);
 auto* writer=(Writer*)handle;
 Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeSetPlaybackActive(&env,nullptr,handle,true);
 std::vector<jbyte> pcm(192,0x42);
 assert(Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeWrite(&env,nullptr,handle,&pcm,0,192,false)==192);
 assert(snapshot(handle)[0]==48);
 auto* audio=writer->slots[0].urb;
 complete(audio,-EIO);audio->iso_frame_desc[0].status=-EPIPE;
 reap(writer);
 auto values=snapshot(handle);
 assert(values[5]==1 && values[7]==1 && values[14]==4 && values[15]==-EPIPE);
 reap(writer);assert(snapshot(handle)[10]==0); // EAGAIN is normal backpressure.
 fail_reap=EIO;reap(writer);assert(snapshot(handle)[10]==1);
 fail_submit=EIO;
 assert(Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeWrite(&env,nullptr,handle,&pcm,0,192,false)==-1);
 assert(snapshot(handle)[9]==1);
 assert(Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeWrite(&env,nullptr,handle,&pcm,0,192,false)==192);
 Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeFlush(&env,nullptr,handle);
 values=snapshot(handle);
 assert(values[7]==1 && values[9]==1 && values[10]==1 && !values[16]);
 assert(!writer->metrics.active); // Deliberate cancellation cannot become a fatal transport error.
 Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeClose(&env,nullptr,handle);

 handle=create(0x81);assert(handle);writer=(Writer*)handle;
 auto* feedback=writer->feedback_slots[0].urb;
 writer->feedback_slots[0].buffer[0]=0;writer->feedback_slots[0].buffer[1]=0;
 writer->feedback_slots[0].buffer[2]=6;writer->feedback_slots[0].buffer[3]=0;
 complete(feedback);reap(writer);
 assert(snapshot(handle)[6]==1 && snapshot(handle)[13]==0);
 const uint8_t legacy_feedback[]={0,128,1}; // Retain the existing 3-byte fallback on HS devices.
 apply_feedback(writer,legacy_feedback,3);
 assert(snapshot(handle)[13]==0);
 complete(feedback);feedback->iso_frame_desc[0].actual_length=2;reap(writer);
 assert(snapshot(handle)[13]==1);
 complete(feedback);feedback->iso_frame_desc[0].status=-EPROTO;reap(writer);
 values=snapshot(handle);
 assert(values[8]==1 && values[14]==6 && values[15]==-EPROTO);
 Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeClose(&env,nullptr,handle);
 assert(submitted.empty() && completed.empty());
 puts("PASS: native snapshots, audio/feedback errors, submission/reap failures, EAGAIN and cancellation");
}
"""
with tempfile.TemporaryDirectory(prefix="echo-usb-health-") as directory:
    out = Path(directory)
    (out / "android").mkdir()
    (out / "jni.h").write_text(fake_jni)
    (out / "android/log.h").write_text(
        "#define ANDROID_LOG_INFO 4\n#define ANDROID_LOG_ERROR 6\n"
        "#define __android_log_print(...) ((void)0)\n")
    (out / "test.cpp").write_text(test)
    binary = out / "regression"
    subprocess.run([os.environ.get("CXX", "g++"), "-std=c++17", "-Wall", "-Wextra",
                    "-fsanitize=address,undefined", "-I", str(out), "-I", str(production),
                    str(out / "test.cpp"), "-o", str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
