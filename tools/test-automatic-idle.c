/* Synthetic stack and code signatures only; no ROM/game fixture. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD_IDLE.h"
static unsigned char ram[1 << 20];
enum { A5 = 0x10000, MENU = 0x20000, EVENTS = 0x30000,
       CHILD = 0x80000, EVENT_FRAME = 0x80100, MENU_FRAME = 0x80200 };
static void put32(unsigned at, uint32_t v) {
    ram[at]=v>>24;ram[at+1]=v>>16;ram[at+2]=v>>8;ram[at+3]=v;
}
static void fixture(void) {
    memset(ram,0,sizeof ram);
    const char name[]="Pool of Radiance v1.1";
    ram[0x910]=sizeof name-1;memcpy(ram+0x911,name,sizeof name-1);
    put32(0x904,A5);
    ram[A5-POOLRAD_ENGINE_BACK]=4;
    ram[A5-POOLRAD_MENU_STATE_BACK+1]=2;
    put32(A5+0x4c0,0x00064ef9);put32(A5+0x4c4,MENU);
    put32(A5+0x2d0,0x00024ef9);put32(A5+0x2d4,EVENTS);
    put32(MENU,0x4e56ff98);put32(EVENTS,0x4e56ffb4);
    put32(MENU+0x52c,0x4ead02d2);put32(MENU+0x530,0x548f1d40);
    put32(CHILD,EVENT_FRAME);put32(CHILD+4,EVENTS+0xdc);
    put32(EVENT_FRAME,MENU_FRAME);put32(EVENT_FRAME+4,MENU+0x530);
}
int main(int argc,char **argv) {
    (void)poolrad_display_probe;(void)poolrad_walk_probe;
    (void)poolrad_walk_observe;(void)poolrad_walk_reset;
    fixture();
    unsigned char original[sizeof ram];memcpy(original,ram,sizeof ram);
    assert(poolrad_idle_wait(ram,sizeof ram,CHILD)==MENU_FRAME);
    assert(memcmp(original,ram,sizeof ram)==0);
    for(unsigned engine=0;engine<256;engine++) {
        fixture();ram[A5-POOLRAD_ENGINE_BACK]=engine;
        assert(!!poolrad_idle_wait(ram,sizeof ram,CHILD)==(engine>=2&&engine<=5));
    }
    fixture();put32(CHILD+4,EVENTS+0xf2);
    assert(poolrad_idle_wait(ram,sizeof ram,CHILD)==MENU_FRAME);
    const unsigned rejected[]={A5-POOLRAD_STARTUP_BACK,A5-POOLRAD_LOADED_BACK,
        A5-POOLRAD_PENDING_INPUT_BACK,A5-POOLRAD_MENU_STATE_BACK,
        MENU_FRAME-1,MENU_FRAME-4,A5+0x4c0,A5+0x2d0,
        MENU,MENU+0x52c,MENU+0x530,EVENTS};
    for(unsigned i=0;i<sizeof rejected/sizeof *rejected;i++) {
        fixture();ram[rejected[i]]^=1;
        assert(poolrad_idle_wait(ram,sizeof ram,CHILD)==0);
    }
    fixture();put32(EVENT_FRAME+4,MENU+0x532); // a different/timed caller
    assert(!poolrad_idle_wait(ram,sizeof ram,CHILD));
    fixture();put32(CHILD+4,EVENTS+0x200); // handling an event, not fetching one
    assert(!poolrad_idle_wait(ram,sizeof ram,CHILD));
    for(unsigned address=0;address<0x110000;address+=0x1111) {
        fixture();put32(CHILD,address);
        assert(!poolrad_idle_wait(ram,sizeof ram,CHILD));
    }
    fixture();put32(EVENT_FRAME,CHILD); // cyclic/backwards stack
    assert(!poolrad_idle_wait(ram,sizeof ram,CHILD));
    fixture();ram[0x911]='X';
    assert(!poolrad_idle_wait(ram,sizeof ram,CHILD));
    fixture();
    for(unsigned n=0;n<MENU_FRAME+30;n+=4093)
        assert(!poolrad_idle_wait(ram,n,CHILD));
    poolrad_idle_tracker t={0};
    assert(!poolrad_idle_observe(&t,1000,MENU_FRAME,0));
    assert(!poolrad_idle_observe(&t,5999,MENU_FRAME,0));
    assert(poolrad_idle_observe(&t,6000,MENU_FRAME,0));
    assert(!poolrad_idle_observe(&t,6001,0,0)); // still screen but no proven wait
    assert(!poolrad_idle_observe(&t,6002,MENU_FRAME,1)); // key, mouse, audio, disk
    assert(!poolrad_idle_observe(&t,6003,MENU_FRAME,0));
    assert(!poolrad_idle_observe(&t,11002,MENU_FRAME,0));
    assert(poolrad_idle_observe(&t,11003,MENU_FRAME,0));
    assert(!poolrad_idle_observe(&t,12000,MENU_FRAME+4,0)); // a new wait
    assert(!poolrad_idle_observe(&t,100,MENU_FRAME+4,0)); // clock reversal
    poolrad_idle_activity(&t,200);
    assert(!poolrad_idle_observe(&t,5200,MENU_FRAME,0)); // resume/restore starts fresh
    if(argc==3) {
        FILE *f=fopen(argv[1],"rb");assert(f);fseek(f,0,SEEK_END);long n=ftell(f);rewind(f);
        unsigned char *live=malloc(n);assert(live);assert(fread(live,1,n,f)==(size_t)n);fclose(f);
        uint32_t result=poolrad_idle_wait(live,n,strtoul(argv[2],0,0));
        printf("Live waiting frame: %x\n",result);assert(result);free(live);
    }
    puts("Automatic idle: proven waits, busy/unknown refusals, bounded stacks and quiet-time checks passed.");
}
