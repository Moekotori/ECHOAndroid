#include "EchoPcEqualizer.h"
#include <vector>
#include <cmath>
#include <iostream>
#include <cstdlib>
using echo_android::PcEqualizer;
static void check(bool x, const char* message) { if (!x) { std::cerr << message << '\n'; std::exit(1); } }
static std::vector<float> config(int count = 1) {
    std::vector<float> p(6 + count * 12); p[0]=1; p[3]=1000; p[4]=1; p[5]=count;
    for(int i=0;i<count;++i) { float* b=p.data()+6+i*12; b[1]=1000;b[3]=1;b[4]=12;b[5]=1;b[8]=-24;b[9]=2;b[10]=5;b[11]=80; }
    return p;
}
static double gain(std::vector<float> p, float amplitude = .01f, bool opposite = false, int observed = 0) {
    PcEqualizer eq(48000,2,p); std::vector<float> b(2048); double in=0,out=0;
    for(int n=0;n<48;++n) {
        for(int i=0;i<1024;++i) { const float x=amplitude*std::sin((n*1024+i)*6.283185307179586*1000/48000); b[i*2]=x; b[i*2+1]=opposite?-x:x; if(n>40) in+=x*x; }
        eq.process(b.data(),1024,true);
        for(int i=0;i<1024;++i) { check(std::isfinite(b[i*2])&&std::isfinite(b[i*2+1]),"non-finite EQ output"); if(n>40) out+=b[i*2+observed]*b[i*2+observed]; }
    }
    return std::sqrt(out/in);
}
int main() {
    auto p=config(); p[8]=6;
    check(std::abs(gain(p)-std::pow(10.,6./20))<.02,"PC bell response differs");
    p[12]=1; check(gain(p)>1.9 && std::abs(gain(p,.01,false,1)-1)<.01,"left scope affected right");
    p[12]=3; check(gain(p)>1.9 && std::abs(gain(p,.01,true)-1)<.01,"mid scope did not isolate mono");
    p[12]=4; check(std::abs(gain(p)-1)<.01 && gain(p,.01,true)>1.9,"side scope did not isolate stereo difference");
    p=config(); p[13]=2; check(gain(p,.5)>1.5,"upward dynamic EQ did not boost above-threshold input");
    check(std::abs(gain(p,.001)-1)<.01,"upward EQ changed below-threshold input");
    p[13]=1; check(gain(p,.5)<.8,"downward dynamic EQ did not attenuate loud input");
    p=config();p[8]=6;p[1]=-6;check(std::abs(gain(p,.7)-1)<.03,"preamp headroom was clipped before EQ");
    p=config(32); for(int i=0;i<31;++i) p[6+i*12+5]=0; p[6+31*12+2]=6;
    check(gain(p)>1.9,"legacy 32nd band was truncated");
    p=config();p[8]=20;p[9]=100;check(std::isfinite(gain(p)),"extended headphone-correction parameters rejected");
    p=config(); PcEqualizer q(48000,2,p); p[8]=-6;check(q.update(p),"live parameter update rejected");
    std::vector<float> b(2048,.1f);q.process(b.data(),1024,true);q.process(b.data(),1024,false);check(!q.processing(),"EQ did not return to bypass");q.reset();
    bool rejected=false;p=config(5);for(int i=0;i<5;++i)p[6+i*12+7]=1;
    try {PcEqualizer bad(48000,2,p);}catch(...){rejected=true;}check(rejected,"more than 4 dynamic bands accepted");
    std::cout<<"PC EQ native: bell response, L/R/M/S, up/down dynamics, 32-band compatibility, live update and bypass passed\n";
}
