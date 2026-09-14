#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD_WHEEL.h"

static unsigned char ram[0x20000], out[POOLRAD_WHEEL_SIZE];
static uint32_t a5, input, wheel, child, stack, ret;
static void fixture(uint32_t shift) {
    memset(ram,0,sizeof(ram));
    a5=0x14000+shift; input=0x13000+shift; wheel=input+66;
    child=input-304; stack=child-16; ret=0x6000+shift;
    ram[0x910]=21; memcpy(ram+0x911,"Pool of Radiance v1.1",21);
    prw_put32(ram+0x904,a5); prw_put32(ram+child,input);
    prw_put32(ram+input,wheel); prw_put32(ram+input+4,ret);
    static const unsigned char code[]={0x48,0x6e,0xff,0xd6,0x3f,0x3c,0,0x28,
        0x3f,0x3c,0,0x0a,0x48,0x79,0,0,0,0,0x4e,0xad,0,0xaa,
        0x4f,0xef,0,0x0c,0x1b,0x6e,0xff,0xd3,0xa1,0x7d,0x70,0,
        0x10,0x2e,0xff,0xd5,0xc0,0xfc,0,0x0c,0x41,0xed,0xfd,0xb8,
        0xd1,0xc0,0x2f,0x10,0x48,0x6e,0xff,0xd6,0x4e,0xad,1,0x32,0x50,0x8f,0x4a,0x40,0x67,0x3e};
    memcpy(ram+ret-22,code,sizeof(code));
    prw_put32(ram+ret-8,0x7000+shift);
    memcpy(ram+0x7000+shift,"Input the code word: ",21);
    prw_put32(ram+input+8,0x7000+shift);
    memcpy(ram+input+12,"\x00\x0a\x00\x28",4);
    prw_put32(ram+input+16,wheel-42);
    ram[wheel-43]=7; ram[wheel-44]=1;
    prw_put32(ram+a5-0x248+7*12,0x7100+shift);
    memcpy(ram+0x7100+shift,"JUNGLE",7);
    ram[input-0x119]=1; ram[input-0x117]=16;
    prw_put32(ram+input-0x108,0x7200+shift);
    prw_put32(ram+0x7200+shift+160,0x7300+shift);
    prw_put32(ram+0x7300+shift,0x7400+shift);
    prw_put32(ram+a5-0x5f4e,0x7500+shift); ram[a5-0x5f4a]=40;
    prw_put32(ram+0x7400+shift+62,0x7500+shift);
    prw_put32(ram+0x7500+shift,0x7600+shift);
}
static int probe(void) { return poolrad_wheel_probe(ram,sizeof(ram),child,stack,out); }
int main(void) {
    fixture(0); assert(probe()); assert(!strcmp((char *)out+20,"JUNGLE"));
    assert(out[16]==7 && out[17]==1 && out[18]==0);
    memcpy(ram+0x7600,"JUN",3); ram[0x7400+61]=ram[0x7400+33]=ram[0x7400+35]=3;
    assert(probe()); assert(!strcmp((char *)out+28,"JUN"));
    assert(ram[wheel-42]==0); /* Deferred output buffer must NOT hide manual typing. */
    ram[0x7600]=1; assert(!probe());
    fixture(0x8000); assert(probe()); assert(prw_u32(out+4)==a5);
    fixture(0); ram[0x911]='X'; assert(!probe());
    fixture(0); ram[wheel-43]=13; assert(!probe());
    fixture(0); ram[wheel-44]=0; assert(!probe());
    fixture(0); ram[wheel-44]=4; assert(!probe());
    fixture(0); ram[ret]=0; assert(!probe());
    fixture(0); ram[ret-4]=0; assert(!probe());
    fixture(0); ram[0x7000]='X'; assert(!probe());
    fixture(0); prw_put32(ram+input+16,wheel-40); assert(!probe());
    fixture(0); prw_put32(ram+child,child); assert(!probe());
    fixture(0); prw_put32(ram+child,a5); assert(!probe()); // Stale valid frame outside active chain.
    fixture(0); assert(!poolrad_wheel_probe(ram,sizeof(ram),child,child+2,out));
    fixture(0); assert(!poolrad_wheel_probe(ram,sizeof(ram),child+1,stack,out));
    fixture(0); prw_put32(ram+a5-0x248+7*12,sizeof(ram)-2); assert(!probe());
    fixture(0); ram[0x7100]='W'; assert(!probe());
    fixture(0); ram[0x7400+61]=41; assert(!probe());
    fixture(0); ram[0x7400+33]=1; assert(!probe());
    fixture(0); ram[input-0x119]=0; assert(!probe());
    fixture(0); prw_put32(ram+0x7400+62,0x7510); assert(!probe());
    fixture(0); assert(!poolrad_wheel_probe(ram,16,child,stack,out));
    assert(!poolrad_wheel_probe(NULL,sizeof(ram),child,stack,out));
    assert(!poolrad_wheel_probe(ram,sizeof(ram),child,stack,NULL));
    puts("wheel probe: live frames, relocation, input bounds, signatures and stale-frame rejection pass");
    return 0;
}
