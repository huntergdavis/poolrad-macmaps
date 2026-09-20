/* Synthetic fixtures, production chip emulation: no game data. */
#define POOLRAD_AUDIO_TEST 1
#include "../android/minivmac/src/main/jni/src/ASCEMDEV.c"
#include "../android/minivmac/src/main/jni/src/POOLRAD_AUDIO.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static blnr output;
static unsigned begins, ends, pulses;
static tbSoundSamp samples[512];
blnr MySound_OutputEnabled(void) { return output; }
tpSoundSamp MySound_BeginWrite(ui4r n, ui4r *actual) { ++begins; *actual=n; return samples; }
void MySound_EndWrite(ui4r n) { ++ends; }
void VIA2_iCB1_ASC_interrupt_PulseNtfy(void) { ++pulses; }
void DoReportAbnormalID(ui4r id) {}
typedef struct { unsigned char bytes[4096]; size_t at; int restoring; } State;
static void visit(void *context, void *p, ui5b n) {
    State *s=context;
    assert(s->at+n <= sizeof(s->bytes));
    if (s->restoring) memcpy(p,s->bytes+s->at,n);
    else memcpy(s->bytes+s->at,p,n);
    s->at+=n;
}
static State capture(void) { State s={0}; Sound_VisitState(visit,&s); return s; }
static void restore(State s) { s.at=0; s.restoring=1; Sound_VisitState(visit,&s); }
static void put32(unsigned char *p, unsigned v) {
    p[0]=v>>24;p[1]=v>>16;p[2]=v>>8;p[3]=v;
}
static void options(void) {
    unsigned char ram[65536]={0}, before[65536];
    const char name[]="Pool of Radiance v1.1";
    assert(poolrad_audio_options(NULL,0)==-1);
    ram[0x910]=sizeof(name)-1;memcpy(ram+0x911,name,sizeof(name)-1);
    put32(ram+0x904,0x8000);
    for(unsigned v=0;v<16;v++) {
        put32(ram+0x8000-0x6228,v);
        memcpy(before,ram,sizeof(ram));
        assert(poolrad_audio_options(ram,sizeof(ram))==(int)v);
        assert(!memcmp(before,ram,sizeof(ram)));
    }
    put32(ram+0x8000-0x6228,16);assert(poolrad_audio_options(ram,sizeof(ram))==-1);
    put32(ram+0x904,0x8001);assert(poolrad_audio_options(ram,sizeof(ram))==-1);
    put32(ram+0x904,0x100);assert(poolrad_audio_options(ram,sizeof(ram))==-1);
    put32(ram+0x904,0xffffff);assert(poolrad_audio_options(ram,sizeof(ram))==-1);
    put32(ram+0x904,0x8000);assert(poolrad_audio_options(ram,0x930)==-1);
    ram[0x911]='X';assert(poolrad_audio_options(ram,sizeof(ram))==-1);
    int last=-1;
    assert(poolrad_audio_observe(ram,sizeof(ram),&last)==-1);
    ram[0x911]='P';put32(ram+0x8000-0x6228,15);
    assert(poolrad_audio_observe(ram,sizeof(ram),&last)==15);
    /* Repeated background Finder worlds and invalid states cannot unmute. */
    ram[0x910]=6;memcpy(ram+0x911,"Finder",6);put32(ram+0x904,0x7000);
    for(unsigned i=0;i<1000;i++) assert(poolrad_audio_observe(ram,sizeof(ram),&last)==15);
    assert(poolrad_audio_observe(NULL,0,&last)==15);
    ram[0x910]=sizeof(name)-1;memcpy(ram+0x911,name,sizeof(name)-1);
    put32(ram+0x904,0x8000);put32(ram+0x8000-0x6228,14);
    assert(poolrad_audio_observe(ram,sizeof(ram),&last)==14);
}
int main(void) {
    options();
    unsigned counts[]={0,1,22,23,24,369,370,511,512,1023};
    unsigned checked=0;
    for(unsigned mode=0;mode<4;mode++)
    for(unsigned stereo=0;stereo<2;stereo++)
    for(unsigned playing=0;playing<2;playing++)
    for(unsigned a=0;a<10;a++)
    for(unsigned b=0;b<10;b++)
    for(unsigned tick=0;tick<16;tick++) {
        SoundReg801=mode;SoundReg802=stereo*2;SoundReg804=(a+b)&15;
        SoundReg_Volume=tick%8;ASC_Playing=playing;
        ASC_FIFO_Out=65530;ASC_FIFO_InA=ASC_FIFO_Out+counts[a];ASC_FIFO_InB=ASC_FIFO_Out+counts[b];
        for(unsigned i=0;i<sizeof(ASC_SampBuff);i++) ASC_SampBuff[i]=(i*17+a)&255;
        for(unsigned ch=0;ch<4;ch++) {
            do_put_mem_long(ASC_ChanA[ch].phase,0xffff0000u+ch*12345u);
            do_put_mem_long(ASC_ChanA[ch].freq,0x12345678u+ch*987u);
        }
        State initial=capture();
        output=1;begins=ends=pulses=0;ASC_SubTick(tick);
        assert(begins==1 && ends==1);
        State expected=capture();unsigned expectedPulses=pulses;
        restore(initial);
        output=0;begins=ends=pulses=0;ASC_SubTick(tick);
        State actual=capture();
        assert(begins==0 && ends==0);
        assert(pulses==expectedPulses && actual.at==expected.at);
        assert(!memcmp(actual.bytes,expected.bytes,actual.at));
        ++checked;
    }
    printf("PASS: read-only sound options; %u audible/muted device states and interrupts match; zero muted sample buffers\n",checked);
}
