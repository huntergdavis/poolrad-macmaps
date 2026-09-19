/* Exercise the production model-aware visitor coordinator. Device stubs let
 * this assert inclusion independently of save/restore round-trip equality. */
#include "../android/minivmac/src/main/jni/src/GLOBGLUE.c"
#include <assert.h>
#include <stdio.h>
static unsigned devices;
#define DEVICE(name, bit) \
    void name(PoolRadStateVisitor visit, void *ctx) { \
        assert(!(devices & (1u << bit))); devices |= 1u << bit; \
    }
DEVICE(VIA1_VisitState, 0)
DEVICE(VIA2_VisitState, 1)
DEVICE(RTC_VisitState, 2)
DEVICE(Sony_VisitState, 3)
DEVICE(SCC_VisitState, 4)
DEVICE(IWM_VisitState, 5)
DEVICE(ADB_VisitState, 6)
DEVICE(Sound_VisitState, 7)
DEVICE(Video_VisitState, 8)
int main(void) {
    GlobGlue_VisitDevices(NULL, NULL);
    assert(devices == 0x1ff);
    puts("PASS: production Mac II traversal visits all nine configured devices");
    return 0;
}
