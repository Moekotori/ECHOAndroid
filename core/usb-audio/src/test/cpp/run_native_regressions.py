"""Host regression checks for production packetization (no DAC required).

Run with python3 core/usb-audio/src/test/cpp/run_native_regressions.py.
Only JNI array access, USB submission/completion and bus-speed ioctl are mocked.
The tested C++ functions are extracted unchanged from the production writer.
"""
from pathlib import Path
import os
import subprocess
import tempfile

source = (Path(__file__).resolve().parents[2] / 'main/cpp/echo_usb_isoc.cpp').read_text()


def section(start, end):
    return source[source.index(start):source.index(end)]


preamble = r'''
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cassert>
#include <cerrno>
#include <vector>
#define URB_COUNT 16
#define FEEDBACK_URB_COUNT 4
#define WRITE_FATAL -2
#define JNIEXPORT
#define JNICALL
#define JNI_ABORT 0
#define LOGE(...) ((void)0)
#define USBDEVFS_GET_SPEED 31
using jint=int; using jlong=int64_t; using jclass=void*; using jboolean=bool;
using jbyte=signed char; using jbyteArray=std::vector<jbyte>*;
struct JNIEnv {
 jbyte* GetByteArrayElements(jbyteArray p,void*) {return p->data();}
 int GetArrayLength(jbyteArray p) {return (int)p->size();}
 void ReleaseByteArrayElements(jbyteArray,jbyte*,int){}
};
struct Packet {unsigned int length; unsigned int actual_length; int status;};
struct usbdevfs_urb {Packet iso_frame_desc[8];};
static int bus_speed=3;
static int ioctl(int,int) {return bus_speed;}
'''
stubs = r'''
static std::vector<jbyte> transmitted;
static bool complete_usb=true;
static void reap(Writer* w){if(complete_usb) for(auto& s:w->slots) s.in_flight=0;}
static int submit_filled_urb(Writer* w,int i,int used,int packets){
 auto& s=w->slots[i];
 int total=0;
 for(int p=0;p<packets;p++) {
  assert(s.urb->iso_frame_desc[p].length % bytes_per_frame(w)==0);
  assert((int)s.urb->iso_frame_desc[p].length<=w->max_packet);
  total+=(int)s.urb->iso_frame_desc[p].length;
 }
 assert(total==used);
 transmitted.insert(transmitted.end(),s.buffer,s.buffer+used);
 s.in_flight=1;
 return 0;
}
'''
main = r'''
struct Fixture {
 Writer w{}; JNIEnv env;
 usbdevfs_urb urbs[16]{}; uint8_t storage[16][8192]{};
 Fixture(int rate,int interval,int sample_bytes=2,int max_packet=1024) {
  w.channels=2; w.bytes_per_sample=sample_bytes; w.sample_rate=rate; w.max_packet=max_packet;
  apply_bus_speed(&w,8000,interval);
  assert(packet_fits(&w));
  w.nominal_q16=((int64_t)rate<<16)/w.pps;
  w.feedback_q16=w.nominal_q16;
  for(int i=0;i<16;i++){w.slots[i].urb=&urbs[i];w.slots[i].buffer=storage[i];}
  transmitted.clear(); complete_usb=true;
 }
 int write(std::vector<jbyte>& data,int offset,int length,bool end=false) {
  return Java_app_echo_android_usbaudio_UsbIsochronousNative_nativeWrite(
   &env,nullptr,(jlong)&w,&data,offset,length,end);
 }
};
static void preserves_stream(int rate,int interval,int sample_bytes) {
 Fixture f(rate,interval,sample_bytes);
 const int frame=sample_bytes*2;
 std::vector<jbyte> input((10000+7)*frame);
 for(size_t i=0;i<input.size();i++) input[i]=(jbyte)(i*31+7);
 std::vector<jbyte> pending(32768);
 int supplied=0,remain=0;
 // Match the sink's bounded append/consume behavior across arbitrary decoder boundaries.
 for(int tries=0;supplied<(int)input.size() || remain;tries++) {
  assert(tries<10000);
  int append=std::min((int)input.size()-supplied,((int)pending.size()-remain)/frame*frame);
  append=std::min(append,137*frame);
  memcpy(pending.data()+remain,input.data()+supplied,append);
  supplied+=append; remain+=append;
  int written=f.write(pending,0,remain,supplied==(int)input.size());
  assert(written>=0 && written<=remain);
  remain-=written; memmove(pending.data(),pending.data()+written,remain);
 }
 assert(transmitted==input); // No lost, duplicated or padded PCM/DoP bytes.
 assert(f.w.submitted_frames==(int64_t)input.size()/frame);
}
int main() {
 {
  Fixture f(48000,1);
  std::vector<jbyte> data(32768,0x55);
  int consumed=0;
  for(int i=0;i<100;i++) {int n=f.write(data,consumed,(int)data.size()-consumed);consumed+=n;if(!n)break;}
  assert(consumed==32760);
  assert(f.write(data,consumed,8)==0);
  assert(f.write(data,consumed,8,true)==8);
  assert(transmitted==data);
  assert(f.w.submitted_frames==8192);
 }
 {
  Fixture f(48000,1);
  std::vector<jbyte> data(8,0x12);
  complete_usb=false;
  for(auto& s:f.w.slots)s.in_flight=1;
  assert(f.write(data,0,8,true)==0); // EOS respects backpressure.
  complete_usb=true;
  assert(f.write(data,0,8,true)==8);
  assert(transmitted==data);
 }
 for(int interval=1;interval<=4;interval++) {
  Fixture f(48000,interval);
  assert(f.w.packets_per_urb==(8>>(interval-1)));
  assert(next_packet_samples(&f.w)==(6<<(interval-1)));
  const uint8_t feedback[]={0,0,6,0}; // Six samples per HS microframe.
  apply_feedback(&f.w,feedback,4);
  assert(next_packet_samples(&f.w)==(6<<(interval-1)));
  preserves_stream(48000,interval,2);
  preserves_stream(44100,interval,3);
 }
 preserves_stream(176400,1,3); // DoP carrier width/rate; byte preservation includes markers.
 {
  bus_speed=2; Fixture f(48000,1,2,192);
  assert(f.w.pps==1000 && f.w.high_speed==0);
  assert(next_packet_samples(&f.w)==48);
  f.w.sample_rate=96000;
  assert(!packet_fits(&f.w)); // Never pretend FS is HS to make a packet fit.
 }
 {
  bus_speed=3; Fixture f(48000,4,2,192);
  assert(f.w.pps==8000 && f.w.high_speed==1);
  assert(next_packet_samples(&f.w)==48); // bInterval 4 means one packet per millisecond.
  f.w.max_packet=191; assert(!packet_fits(&f.w));
 }
 puts("PASS: tail/EOS, backpressure, exact PCM bytes, interval 1-4, feedback and bus speed");
}
'''
code = (preamble + section('struct UrbSlot', 'static size_t urb_alloc_size') + stubs
        + section('static void apply_feedback', 'static int submit_urb')
        + section('static void apply_bus_speed', 'extern "C" JNIEXPORT jlong JNICALL')
        + section('extern "C" JNIEXPORT jint JNICALL\nJava_app_echo_android_usbaudio_UsbIsochronousNative_nativeWrite',
                  'extern "C" JNIEXPORT void JNICALL\nJava_app_echo_android_usbaudio_UsbIsochronousNative_nativePrime')
        + main)
with tempfile.TemporaryDirectory(prefix='echo-usb-regression-') as directory:
    cpp = Path(directory) / 'regression.cpp'
    binary = Path(directory) / 'regression'
    cpp.write_text(code)
    subprocess.run([os.environ.get('CXX', 'clang++'), '-std=c++17', '-Wall', '-Wextra',
                    '-fsanitize=address,undefined', str(cpp), '-o', str(binary)], check=True)
    subprocess.run([str(binary)], check=True)
