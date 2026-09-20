/* Synthetic signatures/stacks; no original game data. */
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include "../android/minivmac/src/main/jni/src/POOLRAD_SKIP.h"
static unsigned char ram[1<<20];
enum { A5=0x10000, ALERT=0x20000, GAME=0x30000, MENU=0x40000,
 EVENTS=0x50000, REPORTS=0x60000, CHILD=0x80000, FRAME=0x80100, FORMAT=0x80200, PRINT=0x80300 };
static void put(unsigned a,uint32_t v) { ram[a]=v>>24;ram[a+1]=v>>16;ram[a+2]=v>>8;ram[a+3]=v; }
static void entry(unsigned jump,unsigned seg,unsigned target,unsigned signature) {
 put(A5+jump,(seg<<16)|0x4ef9);put(A5+jump+4,target);put(target,signature);
}
static void fixture(void) {
 memset(ram,0,sizeof ram);const char name[]="Pool of Radiance v1.1";
 ram[0x910]=sizeof name-1;memcpy(ram+0x911,name,sizeof name-1);put(0x904,A5);
 entry(0x1078,14,ALERT+0x1c20,0x4e56fffc);
 entry(0xa0,2,GAME+0x1112,0x4e56ffec);
 put(ALERT+0x1aca,0x4e560000);put(ALERT+0x1c9c,0x4e56ffcc);
 put(ALERT+0x1ae2,0xa985301f);put(ALERT+0x1fda,0x4ebafaee);
 put(GAME+0x1a12,0x4ead107a);
 put(CHILD,FRAME);put(CHILD+4,ALERT+0x1ae4);
 put(FRAME,FORMAT);put(FRAME+4,ALERT+0x1fde);ram[FRAME+8]=0x27;ram[FRAME+9]=0x10;
 put(FORMAT,PRINT);put(FORMAT+4,ALERT+0x1c3c);put(PRINT+4,GAME+0x1a16);
}
static int matches(void) { return poolrad_skip_notice(ram,sizeof ram,CHILD).frame!=0; }
static void report(void) {
 fixture();memset(ram+FRAME+8,0,6);put(CHILD,FRAME);put(CHILD+4,EVENTS+0xdc);
 put(FRAME,FORMAT);put(FRAME+4,MENU+0x530);
 put(FORMAT,PRINT);put(FORMAT+4,REPORTS+0x5d46);ram[FORMAT-7]=1;
 put(FORMAT+12,0x70000);memcpy(ram+0x70000,"Continue",9);
 entry(0x4c0,6,MENU,0x4e56ff98);entry(0x2d0,2,EVENTS,0x4e56ffb4);
 entry(0x990,9,REPORTS+0x662e,0x4e56ffee);
 put(MENU+0x52c,0x4ead02d2);put(MENU+0x530,0x548f1d40);
 put(REPORTS+0x5d42,0x4ead04c2);put(REPORTS+0x6618,0x4ead04c2);
 ram[A5-POOLRAD_ENGINE_BACK]=6;ram[A5-POOLRAD_MENU_STATE_BACK+1]=2;
}
int main(int argc,char **argv) {
 (void)poolrad_display_probe;(void)poolrad_walk_probe;(void)poolrad_walk_observe;(void)poolrad_walk_reset;
 (void)poolrad_idle_wait;(void)poolrad_idle_observe;
 fixture();assert(matches());assert(poolrad_skip_notice(ram,sizeof ram,FRAME).frame==FRAME);
 unsigned char original[sizeof ram];memcpy(original,ram,sizeof ram);assert(matches());assert(!memcmp(original,ram,sizeof ram));
 const unsigned invalid[]={0x911,ALERT+0x1aca,ALERT+0x1c9c,A5+0x1078,ALERT+0x1c20,ALERT+0x1ae2,
 ALERT+0x1fda,FRAME+8,FRAME+9,FRAME+10,FORMAT+7,PRINT+7,GAME+0x1a12};
 for(unsigned i=0;i<sizeof invalid/sizeof *invalid;i++){fixture();ram[invalid[i]]^=1;assert(!matches());}
 const unsigned allowed[][5]={{0x300,3,0x35b4,0x4e56fff2,0x3444},
 {0x300,3,0x35b4,0x4e56fff2,0x345a},{0x7c8,4,0x315e,0x4e56fed6,0x333a},
 {0x898,7,0x1eb2,0x4e56ffe4,0x4248},{0x898,7,0x1eb2,0x4e56ffe4,0x425a},
 {0x898,7,0x1eb2,0x4e56ffe4,0x427e}};
 for(unsigned i=0;i<sizeof allowed/sizeof *allowed;i++) {
 fixture();entry(allowed[i][0],allowed[i][1],GAME+allowed[i][2],allowed[i][3]);
 put(GAME+allowed[i][4],0x4ead107a);put(PRINT+4,GAME+allowed[i][4]+4);assert(matches());
 }
 fixture();ram[FRAME+9]=0x14;assert(!matches()); // OK+Cancel confirmation 10004
 fixture();put(PRINT+4,GAME+0x1f82);assert(!matches()); // save error
 fixture();put(PRINT+4,GAME+0x1a48);assert(!matches()); // story Continue, unknown caller
 for(unsigned address=0;address<0x110000;address+=0x1111){fixture();put(FRAME,address);assert(!matches());}
 fixture();put(FORMAT,FRAME);assert(!matches());
 fixture();for(unsigned n=0;n<PRINT+8;n+=4093) assert(!poolrad_skip_notice(ram,n,CHILD).frame);
 fixture();ram[A5-POOLRAD_STARTUP_BACK]=1;assert(matches()); // actual character creation
 report();assert(matches());assert(!poolrad_idle_wait(ram,sizeof ram,CHILD)); // engine 6 stays awake
 put(FORMAT+4,REPORTS+0x661c);assert(matches());
 const unsigned manual[]={0x1a48,0x58ca,0x673e,0x618e,0x13a8};
 for(unsigned i=0;i<sizeof manual/sizeof *manual;i++){report();put(FORMAT+4,REPORTS+manual[i]);assert(!matches());}
 report();ram[A5-POOLRAD_STARTUP_BACK]=1;assert(!matches());
 report();ram[A5-POOLRAD_LOADED_BACK]=1;assert(!matches());
 report();ram[A5-POOLRAD_PENDING_INPUT_BACK]=1;assert(!matches());
 report();ram[FORMAT-7]=2;assert(!matches());report();ram[0x70000]='X';assert(!matches());
 poolrad_skip_tracker t={0};poolrad_notice n={FRAME,GAME+0x1a16},none={0,0};
 assert(!poolrad_skip_observe(&t,1000,n,0,0)); // default off
 assert(!poolrad_skip_observe(&t,1000,n,1,0));
 assert(!poolrad_skip_observe(&t,1349,n,1,0));
 assert(!poolrad_skip_observe(&t,1350,none,1,0)); // gap cannot send
 assert(poolrad_skip_observe(&t,1360,n,1,0)==1);
 assert(!poolrad_skip_observe(&t,1400,n,1,0));
 assert(poolrad_skip_observe(&t,1440,n,1,0)==-1);
 assert(!poolrad_skip_observe(&t,2000,n,1,0)); // never repeatedly press
 assert(!poolrad_skip_observe(&t,2400,none,1,0));
 assert(!poolrad_skip_observe(&t,2500,n,1,0));
 assert(poolrad_skip_observe(&t,2850,n,1,0)==1);
 assert(poolrad_skip_observe(&t,2851,n,0,0)==-1); // off releases immediately
 assert(!poolrad_skip_observe(&t,3000,n,1,0));
 assert(!poolrad_skip_observe(&t,3350,n,1,1)); // player/pause/restore cancels pending
 assert(!poolrad_skip_observe(&t,3351,n,1,0));
 assert(poolrad_skip_observe(&t,3701,n,1,0)==1);
 assert(poolrad_skip_observe(&t,3702,n,1,1)==-1); // interruption releases held key
 if(argc==3) { FILE*f=fopen(argv[1],"rb");assert(f);fseek(f,0,SEEK_END);long len=ftell(f);rewind(f);
 unsigned char*b=malloc(len);assert(b);assert(fread(b,1,len,f)==(size_t)len);fclose(f);
 n=poolrad_skip_notice(b,len,strtoul(argv[2],0,0));printf("Live notice frame=%x caller=%x\n",n.frame,n.caller);free(b);assert(n.frame); }
 puts("Auto-skip: informational notices, protected prompts, bounds and input cancellation passed.");
}
