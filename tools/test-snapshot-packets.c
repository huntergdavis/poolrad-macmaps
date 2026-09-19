/* Exercise the production SCC snapshot code, including private packet storage.
 * Compile with variants/macII/cfg and src include paths, function/data sections,
 * --gc-sections, AddressSanitizer and UndefinedBehaviorSanitizer.
 */
#include "../android/minivmac/src/main/jni/src/SCCEMDEV.c"
#include <assert.h>
#include <stdio.h>

ui3p LT_TxBuffer, LT_RxBuffer;
ui4r LT_TxBuffSz;
ui5r LT_RxBuffSz;
ui3b LT_NodeHint;
#if LT_MayHaveEcho
blnr CertainlyNotMyPacket;
#endif

struct Cursor { unsigned char bytes[8192]; size_t at; int restore, valid; };
static void transfer(void *context, void *field, ui5b size) {
    struct Cursor *c = context;
    assert(c->at + size <= sizeof(c->bytes));
    if (c->restore == 1) memcpy(field, c->bytes + c->at, size);
    else if (c->restore == 2) c->valid &= SCC_ValidateSnapshotField(field, c->bytes + c->at);
    else memcpy(c->bytes + c->at, field, size);
    c->at += size;
}
int main(void) {
    unsigned char oldTx[LT_TxBfMxSz] = {3,5,7}, newTx[LT_TxBfMxSz] = {0};
    unsigned char oldRx[5] = {11,13,17,19,23};
    struct Cursor c = {{0},0,0,1};
    LT_TxBuffer=oldTx; LT_TxBuffSz=3; LT_RxBuffer=oldRx; LT_RxBuffSz=5;
    my_node_address=37; LTAddrSrchMd=trueblnr; rx_data_offset=1;
    assert(SCC_PrepareSnapshot());
    SCC_VisitState(transfer,&c);
    LT_TxBuffer=newTx; LT_RxBuffer=nullpr; LT_TxBuffSz=0; LT_RxBuffSz=0;
    my_node_address=0; LTAddrSrchMd=falseblnr;
    c.at=0; c.restore=1; SCC_VisitState(transfer,&c); SCC_AfterRestore();
    assert(LT_TxBuffSz==3 && memcmp(newTx,oldTx,3)==0);
    assert(LT_RxBuffer!=oldRx && LT_RxBuffSz==5 && memcmp(LT_RxBuffer,oldRx,5)==0);
    assert(my_node_address==37 && LTAddrSrchMd && rx_data_offset==1);
    LT_RxBuffer[1]=41;
    assert(SCC_PrepareSnapshot()); // Receive pointer now aliases restored storage.
    assert(SnapshotRx[0]==11 && SnapshotRx[1]==41 && SnapshotRx[4]==23);
    c.at=0; c.restore=0; SCC_VisitState(transfer,&c);
    c.at=0; c.restore=2; c.valid=1; SCC_VisitState(transfer,&c); assert(c.valid);
    ui4r badTx=LT_TxBfMxSz+1;
    ui5b badRx=LT_TxBfMxSz+1;
    ui3b badPresence=2;
    assert(!SCC_ValidateSnapshotField(&LT_TxBuffSz,(const ui3b *)&badTx));
    assert(!SCC_ValidateSnapshotField(&SnapshotRxSize,(const ui3b *)&badRx));
    assert(!SCC_ValidateSnapshotField(&SnapshotHasRx,&badPresence));
    LT_TxBuffSz=LT_TxBfMxSz+1; assert(!SCC_PrepareSnapshot());
    LT_TxBuffSz=0; LT_RxBuffSz=LT_TxBfMxSz+1; assert(!SCC_PrepareSnapshot());
    puts("PASS: packet bytes survive new host buffers, receive aliasing, and length preflight");
    return 0;
}
