/*
	JNIGLUE.c

	Copyright (C) 2012 Gil Osher

	You can redistribute this file and/or modify it under the terms
	of version 2 of the GNU General Public License as published by
	the Free Software Foundation.  You should have received a copy
	of the license along with this file; see the file COPYING.

	This file is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	license for more details.
*/

/*
	Java Native Interface GLUE
*/

#include "OSGCOMUI.h"
#include "OSGCOMUD.h"

#ifdef WantOSGLUJNI

#include <stdatomic.h>
#include "EMULATION_WAIT.h"
#include "POOLRAD.h"
#include "POOLRAD_AUDIO.h"
#include "POOLRAD_IDLE.h"
#include "POOLRAD_SKIP.h"
#include "POOLRAD_WHEEL.h"
#include "POOLRAD_PARTY.h"
#include "POOLRAD_SELECTION.h"
#include "POOLRAD_MESSAGE.h"
#include "POOLRAD_COMBAT.h"
#include "POOLRAD_SAVESTATE.h"

IMPORTFUNC ui3p GetRamForSnapshot(ui5b *size);
IMPORTFUNC ui5r PoolRadGetAddressRegister(ui3r index);
IMPORTPROC PoolRadSaveCPUState(ui3p buf);
IMPORTPROC PoolRadRestoreCPUState(const ui3b *buf);
#ifndef PoolRadCPUStateSize
#define PoolRadCPUStateSize 128
#endif

#define BLACK 0xFF000000
#define WHITE 0xFFFFFFFF
#undef ABS
#define ABS(x) (((x)>0)? (x) : -(x))

#undef CLAMP
#define CLAMP(x, lo, hi) (((x) > (hi))? (hi) : (((x) < (lo))? (lo) : (x)))

LOCALVAR atomic_int gBackgroundFlag = 0;
LOCALVAR atomic_int CurSpeedStopped = 1;
LOCALVAR emulation_wait EmulationWait = EMULATION_WAIT_INITIALIZER;
/* Publish host controls without racing the guest's legacy flags. */
enum { HostReset = 1, HostInterrupt = 2, HostRequestOff = 4, HostForceOff = 8 };
LOCALVAR atomic_int HostCommands = 0;
LOCALVAR atomic_int HostActivity = 0, HostMouseHeld = 0;
LOCALVAR atomic_uint HostKeysHeld[4];
LOCALVAR blnr AutomaticIdle = falseblnr;
LOCALVAR poolrad_idle_tracker IdleTracker;
LOCALVAR atomic_int AutoSkipMessages = 0;
LOCALVAR poolrad_skip_tracker SkipTracker;
#if EmASC || EmClassicSnd
IMPORTFUNC blnr PoolRadSoundBusy(void);
#endif
LOCALFUNC uint64_t IdleNow(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    return (uint64_t)now.tv_sec * 1000 + now.tv_nsec / 1000000;
}
LOCALPROC WakeEmulation(void) { emulation_wait_wake(&EmulationWait); }
LOCALPROC GuestActivity(void) {
    atomic_store(&HostActivity, 1);
    WakeEmulation();
}
LOCALFUNC jboolean RequestWork(atomic_int *request)
{
    jboolean accepted = atomic_exchange(request, 1) == 0 ? JNI_TRUE : JNI_FALSE;
    WakeEmulation();
    return accepted;
}

GLOBALVAR ui3b CurMouseButton = falseblnr;

LOCALVAR blnr initDone = falseblnr;

// java
JNIEnv * jEnv;
jmethodID jSonyTransfer, jSonyGetSize, jSonyEject, jSonyGetName, jSonyMakeNewDisk, jSonyInsert2;
jmethodID jWarnMsg;
jmethodID jInitScreen, jUpdateScreen;
jmethodID jMySoundInit, jMySoundUnInit, jPlaySound, jMySoundStart, jMySoundStop;
jmethodID jGetClipboardText, jSetClipboardText;
jmethodID jRamSnapshot;
jmethodID jSaveState;
jmethodID jStateRestored;
jmethodID jCanRestoreState;
jmethodID jPollStartupRestore, jEmulationReady, jAutomaticIdle;
LOCALVAR blnr StartupRestoreFinished = falseblnr;
LOCALVAR atomic_int WantRamSnapshot = 0;
LOCALVAR atomic_int WantSaveState = 0;
LOCALVAR atomic_int WantRestoreState = 0;
LOCALVAR ui3p gRestoreBuf = nullpr;   /* raw save-state bytes waiting to apply */
LOCALVAR ui5b gRestoreLen = 0;
jmethodID jMapSample;
LOCALVAR atomic_int WantMapSample = 0;
LOCALVAR poolrad_walk_tracker MapWalkTracker = {0};
jmethodID jWheelSample;
LOCALVAR atomic_int WantWheelSample = 0;
jmethodID jPartySample;
LOCALVAR atomic_int WantPartySample = 0;

jmethodID jMessageSample;
LOCALVAR atomic_int WantMessageSample = 0;

jmethodID jCombatSample;
LOCALVAR atomic_int WantCombatSample = 0;
jfieldID sInitOk;
jobject mCore;

/* --- some simple utilities --- */

GLOBALPROC MyMoveBytes(anyp srcPtr, anyp destPtr, si5b byteCount)
{
    memcpy((char *)destPtr, (char *)srcPtr, byteCount);
}

/* --- control mode and internationalization --- */

#define NeedCell2PlainAsciiMap 1

#include "INTLCHAR.h"

/* --- sending debugging info to file --- */

#if dbglog_HAVE

#ifndef dbglog_ToStdErr
#define dbglog_ToStdErr 1
#endif

#if ! dbglog_ToStdErr
LOCALVAR FILE *dbglog_File = NULL;
#endif

LOCALFUNC blnr dbglog_open0(void)
{
#if dbglog_ToStdErr
    return trueblnr;
#else
    dbglog_File = fopen("dbglog.txt", "w");
    return (NULL != dbglog_File);
#endif
}

LOCALPROC dbglog_write0(char *s, uimr L)
{
#if dbglog_ToStdErr
    __android_log_print(ANDROID_LOG_INFO, "Mini vMac", "%.*s", L, s);
    //(void) fwrite(s, 1, L, stderr);
#else
    if (dbglog_File != NULL) {
        (void) fwrite(s, 1, L, dbglog_File);
    }
#endif
}

LOCALPROC dbglog_close0(void)
{
#if ! dbglog_ToStdErr
    if (dbglog_File != NULL) {
        fclose(dbglog_File);
        dbglog_File = NULL;
    }
#endif
}

#endif

#if 0
#pragma mark -
#pragma mark Time, Date, Location
#endif

LOCALVAR ui5b TrueEmulatedTime = 0;

#include "DATE2SEC.h"

#define TicksPerSecond 1000000

LOCALVAR blnr HaveTimeDelta = falseblnr;
LOCALVAR ui5b TimeDelta;

LOCALVAR ui5b NewMacDateInSeconds;

LOCALVAR ui5b LastTimeSec;
LOCALVAR ui5b LastTimeUsec;

LOCALPROC GetCurrentTicks(void)
{
    struct timeval t;

    gettimeofday(&t, NULL);
    if (! HaveTimeDelta) {
        time_t Current_Time;
        struct tm *s;

        (void) time(&Current_Time);
        s = localtime(&Current_Time);
        TimeDelta = Date2MacSeconds(s->tm_sec, s->tm_min, s->tm_hour,
                                    s->tm_mday, 1 + s->tm_mon, 1900 + s->tm_year) - t.tv_sec;
#if 0 && AutoTimeZone /* how portable is this ? */
        CurMacDelta = ((ui5b)(s->tm_gmtoff) & 0x00FFFFFF)
			| ((s->tm_isdst ? 0x80 : 0) << 24);
#endif
        HaveTimeDelta = trueblnr;
    }

    NewMacDateInSeconds = t.tv_sec + TimeDelta;
    LastTimeSec = (ui5b)t.tv_sec;
    LastTimeUsec = (ui5b)t.tv_usec;
}

#define MyInvTimeStep 16626 /* TicksPerSecond / 60.14742 */

LOCALVAR ui5b NextTimeSec;
LOCALVAR ui5b NextTimeUsec;

LOCALPROC IncrNextTime(void)
{
    NextTimeUsec += MyInvTimeStep;
    if (NextTimeUsec >= TicksPerSecond) {
        NextTimeUsec -= TicksPerSecond;
        NextTimeSec += 1;
    }
}

LOCALPROC InitNextTime(void)
{
    NextTimeSec = LastTimeSec;
    NextTimeUsec = LastTimeUsec;
    IncrNextTime();
}

LOCALPROC StartUpTimeAdjust(void)
{
    GetCurrentTicks();
    InitNextTime();
}

LOCALFUNC si5b GetTimeDiff(void)
{
    return ((si5b)(LastTimeSec - NextTimeSec)) * TicksPerSecond
           + ((si5b)(LastTimeUsec - NextTimeUsec));
}

LOCALPROC UpdateTrueEmulatedTime(void)
{
    si5b TimeDiff;

    GetCurrentTicks();

    TimeDiff = GetTimeDiff();
    if (TimeDiff >= 0) {
        if (TimeDiff > 16 * MyInvTimeStep) {
            /* emulation interrupted, forget it */
            ++TrueEmulatedTime;
            InitNextTime();
        } else {
            do {
                ++TrueEmulatedTime;
                IncrNextTime();
                TimeDiff -= TicksPerSecond;
            } while (TimeDiff >= 0);
        }
    } else if (TimeDiff < - 16 * MyInvTimeStep) {
        /* clock goofed if ever get here, reset */
        InitNextTime();
    }
}

LOCALFUNC blnr CheckDateTime(void)
{
    if (CurMacDateInSeconds != NewMacDateInSeconds) {
        CurMacDateInSeconds = NewMacDateInSeconds;
        return trueblnr;
    } else {
        return falseblnr;
    }
}

LOCALFUNC blnr InitLocationDat(void)
{
    GetCurrentTicks();
    CurMacDateInSeconds = NewMacDateInSeconds;

    return trueblnr;
}

#if 0
#pragma mark -
#pragma mark Sound
#endif

#if MySoundEnabled
#define kLn2SoundBuffers 4 /* kSoundBuffers must be a power of two */
#define kSoundBuffers (1 << kLn2SoundBuffers)
#define kSoundBuffMask (kSoundBuffers - 1)

#define DesiredMinFilledSoundBuffs 3
/*
    if too big then sound lags behind emulation.
    if too small then sound will have pauses.
*/

#define kLnOneBuffLen 9
#define kLnAllBuffLen (kLn2SoundBuffers + kLnOneBuffLen)
#define kOneBuffLen (1UL << kLnOneBuffLen)
#define kAllBuffLen (1UL << kLnAllBuffLen)
#define kLnOneBuffSz (kLnOneBuffLen + kLn2SoundSampSz - 3)
#define kLnAllBuffSz (kLnAllBuffLen + kLn2SoundSampSz - 3)
#define kOneBuffSz (1UL << kLnOneBuffSz)
#define kAllBuffSz (1UL << kLnAllBuffSz)
#define kOneBuffMask (kOneBuffLen - 1)
#define kAllBuffMask (kAllBuffLen - 1)
#define dbhBufferSize (kAllBuffSz + kOneBuffSz)

LOCALVAR tpSoundSamp TheSoundBuffer = nullpr;
LOCALVAR ui4b ThePlayOffset = 0;
LOCALVAR ui4b TheFillOffset = 0;
LOCALVAR ui4b MinFilledSoundBuffs = kSoundBuffers;
LOCALVAR ui4b TheWriteOffset = 0;
LOCALVAR blnr SoundOutputActive = falseblnr;
LOCALVAR blnr SoundOutputRequested = falseblnr;
LOCALVAR int SoundOptions = -2;
LOCALVAR int LastGameSoundOptions = -1;
LOCALVAR unsigned long long SoundSamples = 0, SoundTransfers = 0;

GLOBALFUNC blnr MySound_OutputEnabled(void) { return SoundOutputActive; }

/* Called only on the emulation thread, after restore and before another tick.
 * No preference writes: the game's own master Sounds flag is authoritative. */
LOCALPROC MySound_UpdateOutput(void)
{
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    int options = poolrad_audio_observe(ram, size, &LastGameSoundOptions);
    blnr wanted = !CurSpeedStopped && StartupRestoreFinished
        && (options < 0 || !(options & 1));
    if (wanted != SoundOutputRequested) {
        SoundOutputRequested = wanted;
        if (wanted) {
            if ((*jEnv)->CallBooleanMethod(jEnv, mCore, jMySoundInit)) {
                (*jEnv)->CallVoidMethod(jEnv, mCore, jMySoundStart);
                SoundOutputActive = trueblnr;
            }
        } else {
            SoundOutputActive = falseblnr;
            (*jEnv)->CallVoidMethod(jEnv, mCore, jMySoundUnInit);
        }
        __android_log_print(ANDROID_LOG_INFO, "PoolRad.Audio",
            "output=%d options=%d samples=%llu transfers=%llu",
            SoundOutputActive, options, SoundSamples, SoundTransfers);
    }
    if (options != SoundOptions) {
        SoundOptions = options;
        __android_log_print(ANDROID_LOG_INFO, "PoolRad.Audio",
            "game-options=%d output=%d samples=%llu transfers=%llu",
            options, SoundOutputActive, SoundSamples, SoundTransfers);
    }
}

#if 4 == kLn2SoundSampSz
LOCALPROC ConvertSoundBlockToNative(tpSoundSamp p)
{
	int i;

	for (i = kOneBuffLen; --i >= 0; ) {
		*p++ -= 0x8000;
	}
}
#else
#define ConvertSoundBlockToNative(p)
#endif

LOCALPROC MySound_WriteOut(void)
{
    int retry_count = 32;
    if (!SoundOutputActive) return;

    label_retry:
    if (--retry_count > 0) {

        tpSoundSamp NextPlayPtr;
        ui4b PlayNowSize = 0;
        ui4b MaskedFillOffset = ThePlayOffset & kOneBuffMask;

        if (MaskedFillOffset != 0) {
            /* take care of left overs */
            PlayNowSize = kOneBuffLen - MaskedFillOffset;
            NextPlayPtr = TheSoundBuffer + (ThePlayOffset & kAllBuffMask);
        } else if (0 != ((TheFillOffset - ThePlayOffset) >> kLnOneBuffLen)) {
            PlayNowSize = kOneBuffLen;
            NextPlayPtr = TheSoundBuffer + (ThePlayOffset & kAllBuffMask);
        } else {
            /* nothing to play now */
        }

        if (0 != PlayNowSize) {
            ++SoundTransfers;
            jbyteArray jBuffer = (*jEnv)->NewByteArray(jEnv, PlayNowSize);
            if (jBuffer != NULL) {
                (*jEnv)->SetByteArrayRegion(jEnv, jBuffer, 0, PlayNowSize, (jbyte *) NextPlayPtr);
                int err = (*jEnv)->CallIntMethod(jEnv, mCore, jPlaySound, jBuffer);
                (*jEnv)->DeleteLocalRef(jEnv, jBuffer);
                if (err >= 0) {
                    ThePlayOffset += err;
                    goto label_retry;
                }
            }
        }
    }
}

LOCALFUNC blnr MySound_EndWrite0(ui4r actL)
{
    blnr v;

    TheWriteOffset += actL;

    if (0 != (TheWriteOffset & kOneBuffMask)) {
        v = falseblnr;
    } else {
        /* just finished a block */

        TheFillOffset = TheWriteOffset;

        v = trueblnr;
    }

    return v;
}

GLOBALPROC MySound_EndWrite(ui4r actL)
{
    if (!SoundOutputActive) return;
    if (MySound_EndWrite0(actL)) {
        ConvertSoundBlockToNative(TheSoundBuffer
                                          + ((TheFillOffset - kOneBuffLen) & kAllBuffMask));
        MySound_WriteOut();
    }
}

GLOBALFUNC tpSoundSamp MySound_BeginWrite(ui4r n, ui4r *actL)
{
    if (!SoundOutputActive) { *actL = n; return nullpr; }
    ui4b ToFillLen = kAllBuffLen - (TheWriteOffset - ThePlayOffset);
    ui4b WriteBuffContig = kOneBuffLen - (TheWriteOffset & kOneBuffMask);

    if (WriteBuffContig < n) {
        n = WriteBuffContig;
    }
    if (ToFillLen < n) {
        /* overwrite previous buffer */
        TheWriteOffset -= kOneBuffLen;
    }

    *actL = n;
    SoundSamples += n;
    return TheSoundBuffer + (TheWriteOffset & kAllBuffMask);
}

LOCALPROC MySound_SecondNotify(void)
{
    if (!SoundOutputActive) return;
    if (MinFilledSoundBuffs <= kSoundBuffers) {
        if (MinFilledSoundBuffs > DesiredMinFilledSoundBuffs) {
            IncrNextTime();
        } else if (MinFilledSoundBuffs < DesiredMinFilledSoundBuffs) {
            ++TrueEmulatedTime;
        }
        MinFilledSoundBuffs = kSoundBuffers + 1;
    }
}

#endif

#if 0
#pragma mark -
#pragma mark Paramter buffers
#endif

#include "COMOSGLU.h"

#include "PBUFSTDC.h"

#include "CONTROLM.h"

/* --- text translation --- */

#if IncludePbufs
/* this is table for Windows, any changes needed for X? */
LOCALVAR const ui3b Native2MacRomanTab[] = {
        0xAD, 0xB0, 0xE2, 0xC4, 0xE3, 0xC9, 0xA0, 0xE0,
        0xF6, 0xE4, 0xB6, 0xDC, 0xCE, 0xB2, 0xB3, 0xB7,
        0xB8, 0xD4, 0xD5, 0xD2, 0xD3, 0xA5, 0xD0, 0xD1,
        0xF7, 0xAA, 0xC5, 0xDD, 0xCF, 0xB9, 0xC3, 0xD9,
        0xCA, 0xC1, 0xA2, 0xA3, 0xDB, 0xB4, 0xBA, 0xA4,
        0xAC, 0xA9, 0xBB, 0xC7, 0xC2, 0xBD, 0xA8, 0xF8,
        0xA1, 0xB1, 0xC6, 0xD7, 0xAB, 0xB5, 0xA6, 0xE1,
        0xFC, 0xDA, 0xBC, 0xC8, 0xDE, 0xDF, 0xF0, 0xC0,
        0xCB, 0xE7, 0xE5, 0xCC, 0x80, 0x81, 0xAE, 0x82,
        0xE9, 0x83, 0xE6, 0xE8, 0xED, 0xEA, 0xEB, 0xEC,
        0xF5, 0x84, 0xF1, 0xEE, 0xEF, 0xCD, 0x85, 0xF9,
        0xAF, 0xF4, 0xF2, 0xF3, 0x86, 0xFA, 0xFB, 0xA7,
        0x88, 0x87, 0x89, 0x8B, 0x8A, 0x8C, 0xBE, 0x8D,
        0x8F, 0x8E, 0x90, 0x91, 0x93, 0x92, 0x94, 0x95,
        0xFD, 0x96, 0x98, 0x97, 0x99, 0x9B, 0x9A, 0xD6,
        0xBF, 0x9D, 0x9C, 0x9E, 0x9F, 0xFE, 0xFF, 0xD8
};
#endif

#if IncludePbufs
LOCALFUNC tMacErr NativeTextToMacRomanPbuf(const char *x, tPbuf *r)
{
    if (NULL == x) {
        return mnvm_miscErr;
    } else {
        ui3p p;
        ui5b L = strlen(x);

        p = (ui3p)malloc(L);
        if (NULL == p) {
            return mnvm_miscErr;
        } else {
            ui3b *p0 = (ui3b *)x;
            ui3b *p1 = (ui3b *)p;
            int i;

            for (i = L; --i >= 0; ) {
                ui3b v = *p0++;
                if (v >= 128) {
                    v = Native2MacRomanTab[v - 128];
                } else if (10 == v) {
                    v = 13;
                }
                *p1++ = v;
            }

            return PbufNewFromPtr(p, L, r);
        }
    }
}
#endif

#if IncludePbufs
/* this is table for Windows, any changes needed for X? */
LOCALVAR const ui3b MacRoman2NativeTab[] = {
        0xC4, 0xC5, 0xC7, 0xC9, 0xD1, 0xD6, 0xDC, 0xE1,
        0xE0, 0xE2, 0xE4, 0xE3, 0xE5, 0xE7, 0xE9, 0xE8,
        0xEA, 0xEB, 0xED, 0xEC, 0xEE, 0xEF, 0xF1, 0xF3,
        0xF2, 0xF4, 0xF6, 0xF5, 0xFA, 0xF9, 0xFB, 0xFC,
        0x86, 0xB0, 0xA2, 0xA3, 0xA7, 0x95, 0xB6, 0xDF,
        0xAE, 0xA9, 0x99, 0xB4, 0xA8, 0x80, 0xC6, 0xD8,
        0x81, 0xB1, 0x8D, 0x8E, 0xA5, 0xB5, 0x8A, 0x8F,
        0x90, 0x9D, 0xA6, 0xAA, 0xBA, 0xAD, 0xE6, 0xF8,
        0xBF, 0xA1, 0xAC, 0x9E, 0x83, 0x9A, 0xB2, 0xAB,
        0xBB, 0x85, 0xA0, 0xC0, 0xC3, 0xD5, 0x8C, 0x9C,
        0x96, 0x97, 0x93, 0x94, 0x91, 0x92, 0xF7, 0xB3,
        0xFF, 0x9F, 0xB9, 0xA4, 0x8B, 0x9B, 0xBC, 0xBD,
        0x87, 0xB7, 0x82, 0x84, 0x89, 0xC2, 0xCA, 0xC1,
        0xCB, 0xC8, 0xCD, 0xCE, 0xCF, 0xCC, 0xD3, 0xD4,
        0xBE, 0xD2, 0xDA, 0xDB, 0xD9, 0xD0, 0x88, 0x98,
        0xAF, 0xD7, 0xDD, 0xDE, 0xB8, 0xF0, 0xFD, 0xFE
};
#endif

#if IncludePbufs
LOCALFUNC blnr MacRomanTextToNativePtr(tPbuf i, blnr IsFileName,
                                       ui3p *r)
{
    ui3p p;
    void *Buffer = PbufDat[i];
    ui5b L = PbufSize[i];

    p = (ui3p)malloc(L + 1);
    if (p != NULL) {
        ui3b *p0 = (ui3b *)Buffer;
        ui3b *p1 = (ui3b *)p;
        int j;

        if (IsFileName) {
            for (j = L; --j >= 0; ) {
                ui3b x = *p0++;
                if (x < 32) {
                    x = '-';
                } else if (x >= 128) {
                    x = MacRoman2NativeTab[x - 128];
                } else {
                    switch (x) {
                        case '/':
                        case '<':
                        case '>':
                        case '|':
                        case ':':
                            x = '-';
                        default:
                            break;
                    }
                }
                *p1++ = x;
            }
            if ('.' == p[0]) {
                p[0] = '-';
            }
        } else {
            for (j = L; --j >= 0; ) {
                ui3b x = *p0++;
                if (x >= 128) {
                    x = MacRoman2NativeTab[x - 128];
                } else if (13 == x) {
                    x = '\n';
                }
                *p1++ = x;
            }
        }
        *p1 = 0;

        *r = p;
        return trueblnr;
    }
    return falseblnr;
}
#endif

LOCALPROC NativeStrFromCStr(char *r, char *s)
{
    ui3b ps[ClStrMaxLength];
    int i;
    int L;

    ClStrFromSubstCStr(&L, ps, s);

    for (i = 0; i < L; ++i) {
        r[i] = Cell2PlainAsciiMap[ps[i]];
    }

    r[L] = 0;
}

#if EmLocalTalk
LOCALFUNC blnr EntropyGather(void)
{
    {
        ui5b dat[2];
        int fd;

        if (-1 == (fd = open("/dev/urandom", O_RDONLY))) {
#if dbglog_HAVE
            dbglog_writeCStr("open /dev/urandom fails");
            dbglog_writeNum(errno);
            dbglog_writeCStr(" (");
            dbglog_writeCStr(strerror(errno));
            dbglog_writeCStr(")");
            dbglog_writeReturn();
#endif
        } else {

            if (read(fd, &dat, sizeof(dat)) < 0) {
#if dbglog_HAVE
                dbglog_writeCStr("open /dev/urandom fails");
                dbglog_writeNum(errno);
                dbglog_writeCStr(" (");
                dbglog_writeCStr(strerror(errno));
                dbglog_writeCStr(")");
                dbglog_writeReturn();
#endif
            } else {

#if dbglog_HAVE
                dbglog_writeCStr("dat: ");
                dbglog_writeHex(dat[0]);
                dbglog_writeCStr(" ");
                dbglog_writeHex(dat[1]);
                dbglog_writeReturn();
#endif

                e_p[0] ^= dat[0];
                e_p[1] ^= dat[1];
                /*
                    if "/dev/urandom" is working correctly,
                    this should make the previous contents of e_p
                    irrelevant. if it is completely broken, like
                    returning 0, this will not make e_p any less
                    random.
                */

#if dbglog_HAVE
                dbglog_writeCStr("ep: ");
                dbglog_writeHex(e_p[0]);
                dbglog_writeCStr(" ");
                dbglog_writeHex(e_p[1]);
                dbglog_writeReturn();
#endif
            }

            close(fd);
        }
    }

    return trueblnr;
}
#endif

#if EmLocalTalk

#include "LOCALTLK.h"

#endif

#if 0
#pragma mark -
#pragma mark Floppy Driver
#endif

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    notifyDiskInserted
 * Signature: (IZ)V
 */
GLOBALPROC notifyDiskInserted (jint drive, jboolean locked) {
    DiskInsertNotify((ui4b)drive, locked?trueblnr:falseblnr);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    notifyDiskEjected
 * Signature: (I)V
 */
GLOBALPROC notifyDiskEjected (jint drive) {
    DiskEjectedNotify((ui4b)drive);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    notifyDiskEjected
 * Signature: ()V
 */
GLOBALPROC notifyDiskCreated () {
    vSonyNewDiskWanted = falseblnr;
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    getFirstFreeDisk
 * Signature: ()I
 */
GLOBALFUNC jint getFirstFreeDisk () {
    ui4b drive;
    if (!FirstFreeDisk(&drive)) return -1;
    return (jint)drive;
}

GLOBALFUNC jint getNumDrives () {
    return (jint)NumDrives;
}

// callbacks
GLOBALFUNC tMacErr vSonyTransfer(blnr IsWrite, ui3p Buffer,	tDrive Drive_No, ui5r Sony_Start, ui5r Sony_Count, ui5r *Sony_ActCount)
{
    poolrad_idle_activity(&IdleTracker, IdleNow());
    jobject jBuffer;
    jBuffer = (*jEnv)->NewDirectByteBuffer(jEnv, Buffer, (jlong)Sony_Count);
    ui5r actCount = (*jEnv)->CallIntMethod(jEnv, mCore, jSonyTransfer, (jboolean)IsWrite, jBuffer, (jint)Drive_No, (jint)Sony_Start, (jint)Sony_Count);
    (*jEnv)->DeleteLocalRef(jEnv, jBuffer);

    if (nullpr != Sony_ActCount) {
        *Sony_ActCount = actCount;
    }

    return (actCount >= 0 ? mnvm_noErr : -1);
}

GLOBALFUNC tMacErr vSonyGetSize(tDrive Drive_No, ui5r *Sony_Count)
{
    *Sony_Count = (*jEnv)->CallIntMethod(jEnv, mCore, jSonyGetSize, (jint)Drive_No);
    if (*Sony_Count < 0) return -1;
    return 0;
}

GLOBALFUNC tMacErr vSonyEject(tDrive Drive_No) {
    return (*jEnv)->CallIntMethod(jEnv, mCore, jSonyEject, (jint)Drive_No, JNI_FALSE);
}

#if IncludeSonyNew
GLOBALFUNC tMacErr vSonyEjectDelete(tDrive Drive_No)
{
    return (*jEnv)->CallIntMethod(jEnv, mCore, jSonyEject, (jint)Drive_No, JNI_TRUE);
}
#endif

LOCALPROC UnInitDrives(void)
{
    tDrive i;

    for (i = 0; i < NumDrives; ++i) {
        if (vSonyIsInserted(i)) {
            (void) vSonyEject(i);
        }
    }
}

#if IncludeSonyGetName
GLOBALFUNC tMacErr vSonyGetName(tDrive Drive_No, tPbuf *r)
{
    jstring result = (*jEnv)->CallObjectMethod(jEnv, mCore, jSonyGetName, (jint)Drive_No);

    if (NULL == result) {
        return -1;
    } else {
        const char *nativeString = (*jEnv)->GetStringUTFChars(jEnv, result, NULL);
        if (nativeString == NULL) {
            return -1;
        }
        tMacErr res = NativeTextToMacRomanPbuf(nativeString, r);
        (*jEnv)->ReleaseStringUTFChars(jEnv, result, nativeString);
        (*jEnv)->DeleteLocalRef(jEnv, result);
        return res;
    }
}
#endif

LOCALFUNC blnr Sony_InsertIth(int i)
{
    blnr v;

    if ((i > 9) || ! FirstFreeDisk(nullpr)) {
        v = falseblnr;
    } else {
        char s[] = "disk?.dsk";

        s[4] = '0' + i;

        jstring jdiskname = (*jEnv)->NewStringUTF(jEnv, s);
        v = (*jEnv)->CallBooleanMethod(jEnv, mCore, jSonyInsert2, jdiskname);
        (*jEnv)->DeleteLocalRef(jEnv, jdiskname);

    }

    return v;
}

LOCALFUNC blnr LoadInitialImages(void)
{
    if (! AnyDiskInserted()) {
        int i;

        for (i = 1; Sony_InsertIth(i); ++i) {
            /* stop on first error (including file not found) */
        }
    }

    return trueblnr;
}

#if IncludeSonyNew
LOCALPROC MakeNewDisk(ui5b L, char *drivename)
{
    jstring jdrivename = (*jEnv)->NewStringUTF(jEnv, drivename);
    (*jEnv)->CallIntMethod(jEnv, mCore, jSonyMakeNewDisk, (jint)L, jdrivename);
    (*jEnv)->DeleteLocalRef(jEnv, jdrivename);
}
#endif

#if IncludeSonyNew
LOCALPROC MakeNewDiskAtDefault(ui5b L)
{
    char s[ClStrMaxLength + 1];

    NativeStrFromCStr(s, "untitled.dsk");
    MakeNewDisk(L, s);
}
#endif

#if 0
#pragma mark -
#pragma mark Sound
#endif

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    MySound_Start0
 * Signature: ()V
 */
GLOBALPROC MySound_Start0 () {
#if MySoundEnabled
    /* Reset variables */
    ThePlayOffset = 0;
    TheFillOffset = 0;
    TheWriteOffset = 0;
    MinFilledSoundBuffs = kSoundBuffers + 1;
#endif
}

#if 0
#pragma mark -
#pragma mark Screen
#endif

LOCALVAR blnr WasInSpecialMode = falseblnr;

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    screenWidth
 * Signature: ()I
 */
GLOBALFUNC jint screenWidth () {
    return (jint)vMacScreenWidth;
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    screenHeight
 * Signature: ()I
 */
GLOBALFUNC jint screenHeight () {
    return (jint)vMacScreenHeight;
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    screenDepth
 * Signature: ()I
 */
GLOBALFUNC jint screenDepth () {
    return (jint)vMacScreenDepth;
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    getScreenUpdate
 * Signature: ()[I
 */
GLOBALFUNC jintArray getScreenUpdate () {
    si4b top, left, bottom, right;

    if (0 != SpecialModes) {
        top = 0;
        left = 0;
        bottom = vMacScreenHeight;
        right = vMacScreenWidth;
        WasInSpecialMode = trueblnr;
    } else if (WasInSpecialMode) {
        top = 0;
        left = 0;
        bottom = vMacScreenHeight;
        right = vMacScreenWidth;
        WasInSpecialMode = falseblnr;
    } else if (ScreenChangedBottom > ScreenChangedTop) {
        top = ScreenChangedTop;
        left = ScreenChangedLeft;
        bottom = ScreenChangedBottom;
        right = ScreenChangedRight;
    } else {
        // No change - return empty array.
        jintArray jArray = (*jEnv)->NewIntArray(jEnv, (jsize)0);
        return jArray;
    }

    int changesWidth = right - left;
    int changesHeight = bottom - top;
    jsize changesSize = changesWidth * changesHeight;
    int i,x,y;

    // create java array of changes: top, left, bottom, right, pixels...
    jintArray jArray = (*jEnv)->NewIntArray(jEnv, changesSize);
    jboolean arrayCopy = JNI_FALSE;
    jint *arr = (jint*)(*jEnv)->GetPrimitiveArrayCritical(jEnv, (jarray)jArray, &arrayCopy);
    jint *px = arr;

    ui3p curdrawbuff = GetCurDrawBuff();

    // convert pixels
#if 0 != vMacScreenDepth
    if (UseColorMode) {
#if 4 > vMacScreenDepth
        x = left;
        y = top*vMacScreenByteWidth;
        for(i=0; i < changesSize; i++) {
            int pixel = (((unsigned char*)curdrawbuff)[y+x]);
            px[i] = (0xFF000000 |
                     ((((unsigned int)CLUT_reds[pixel]  ) >> 8) << 16) |
                     ((((unsigned int)CLUT_greens[pixel]) >> 8) << 8 ) |
                     ((((unsigned int)CLUT_blues[pixel] ) >> 8)		 ));

            if (++x >= right) {
                x = left;
                y += vMacScreenByteWidth;
            }
        }
#else
        x = left;
    	y = top*vMacScreenByteWidth;
    	for(i=0; i < changesSize; i++) {
    		int pixel = ((unsigned char*)curdrawbuff)[y+x];
    		px[i] = pixel;//((pixel & 0xFF00) >> 8) | ((pixel & 0x00FF) << 8);

    		if (++x >= right) {
    			x = left;
    			y += vMacScreenByteWidth;
    		}
    	}
#endif
    } else {
#endif
        x = left;
        y = top*vMacScreenMonoByteWidth;
        for(i=0; i < changesSize; i++) {
            int pixel = ((((unsigned char*)curdrawbuff)[y+(x/8)]) << (x%8)) & 0x80;
            px[i] = pixel?BLACK:WHITE;

            if (++x >= right) {
                x = left;
                y += vMacScreenMonoByteWidth;
            }
        }
#if 0 != vMacScreenDepth
    }
#endif

    (*jEnv)->ReleasePrimitiveArrayCritical(jEnv, (jarray)jArray, (void*)arr, 0);
    return jArray;
}

/* Native owns the sleep decision; Java suspends its polling without clearing
 * the last visible map/note. Input only publishes activity and wakes the wait. */
LOCALPROC SetAutomaticIdle(blnr idle) {
    if (AutomaticIdle == idle) return;
    AutomaticIdle = idle;
    (*jEnv)->CallVoidMethod(jEnv, mCore, jAutomaticIdle, idle ? JNI_TRUE : JNI_FALSE);
    __android_log_print(ANDROID_LOG_INFO, "PoolRad.Idle", "automatic-idle=%d", idle);
}

LOCALPROC ReleaseSkippedKey(void) {
    /* Preserve a real Return key the player is currently holding. */
    if (!(atomic_load(&HostKeysHeld[1]) & (1u << 4))) Keyboard_UpdateKeyMap2(36, falseblnr);
}

LOCALPROC CancelMessageSkip(void) {
    if (SkipTracker.held) ReleaseSkippedKey();
    memset(&SkipTracker, 0, sizeof SkipTracker);
}

LOCALPROC ObserveMessageSkip(const unsigned char *ram, size_t size) {
    int busy = CurSpeedStopped || !StartupRestoreFinished || atomic_load(&gBackgroundFlag)
            || atomic_load(&HostCommands) || atomic_load(&HostActivity) || atomic_load(&HostMouseHeld)
            || atomic_load(&WantRestoreState);
    for (unsigned i=0;i<4;i++) busy |= atomic_load(&HostKeysHeld[i]) != 0;
    int enabled = atomic_load(&AutoSkipMessages);
    poolrad_notice notice = {0,0};
    if (enabled && !busy) notice=poolrad_skip_notice(ram,size,PoolRadGetAddressRegister(6));
    int action=poolrad_skip_observe(&SkipTracker,IdleNow(),notice,enabled,busy);
    if (action < 0) ReleaseSkippedKey();
    if (action > 0) {
        Keyboard_UpdateKeyMap2(36,trueblnr);
        poolrad_idle_activity(&IdleTracker,IdleNow());
        __android_log_print(ANDROID_LOG_INFO,"PoolRad.Skip","informational acknowledgement caller=%x",notice.caller);
    }
}

LOCALPROC ObserveAutomaticIdle(const unsigned char *ram, size_t size) {
    if (AutomaticIdle || CurSpeedStopped || !StartupRestoreFinished) return;
    /* The verified input loop animates its cursor. Redraws alone are not
     * gameplay; its call stack, input, disk and sound establish eligibility. */
    int busy = SkipTracker.held || atomic_load(&HostActivity) || atomic_load(&HostMouseHeld);
    for (unsigned i = 0; i < 4; i++) busy |= atomic_load(&HostKeysHeld[i]) != 0;
#if MySoundEnabled && (EmASC || EmClassicSnd)
    busy |= SoundOutputActive && PoolRadSoundBusy();
#endif
    uint32_t frame = busy ? 0 : poolrad_idle_wait(ram, size, PoolRadGetAddressRegister(6));
    if (poolrad_idle_observe(&IdleTracker, IdleNow(), frame, busy)) SetAutomaticIdle(trueblnr);
}

LOCALPROC MyDrawChangesAndClear(void)
{
    if (ScreenChangedBottom > ScreenChangedTop) {
        (*jEnv)->CallVoidMethod(jEnv, mCore, jUpdateScreen, (jint)ScreenChangedTop, (jint)ScreenChangedLeft,
                                (jint)ScreenChangedBottom, (jint)ScreenChangedRight);
        ScreenClearChanges();
    }
}

#if 0
#pragma mark -
#pragma mark Mouse
#endif

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    moveMouse
 * Signature: (II)V
 */
GLOBALPROC moveMouse (jint dx, jint dy) {
    HaveMouseMotion = trueblnr;
    MyMousePositionSetDelta(dx, dy);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setMousePos
 * Signature: (II)V
 */
GLOBALPROC setMousePos (jint x, jint y) {
    HaveMouseMotion = falseblnr;
    CurMouseH = CLAMP(x, 0, vMacScreenWidth);
    CurMouseV = CLAMP(y, 0, vMacScreenHeight);

    MyMousePositionSet(CurMouseH, CurMouseV);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setMouseButton
 * Signature: (Z)V
 */
GLOBALPROC setMouseButton (jboolean down) {
    atomic_store(&HostMouseHeld, down != 0);
    CurMouseButton = down?trueblnr:falseblnr;
    MyMouseButtonSet(CurMouseButton);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    getMouseX
 * Signature: ()I
 */
GLOBALFUNC jint getMouseX () {
    return (jint)CurMouseH;
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    getMouseY
 * Signature: ()I
 */
GLOBALFUNC jint getMouseY () {
    return (jint)CurMouseV;
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    getMouseButton
 * Signature: ()Z
 */
GLOBALFUNC jboolean getMouseButton () {
    return CurMouseButton?JNI_TRUE:JNI_FALSE;
}

#if 0
#pragma mark -
#pragma mark Keyboard
#endif

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setKeyDown
 * Signature: (I)V
 */
GLOBALPROC setKeyDown (jint key) {
    if (key >= 0 && key < 128) atomic_fetch_or(&HostKeysHeld[key / 32], 1u << (key % 32));
    Keyboard_UpdateKeyMap2(key, trueblnr);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setKeyUp
 * Signature: (I)V
 */
GLOBALPROC setKeyUp (jint key) {
    if (key >= 0 && key < 128) atomic_fetch_and(&HostKeysHeld[key / 32], ~(1u << (key % 32)));
    Keyboard_UpdateKeyMap2(key, falseblnr);
    GuestActivity();
}

#if 0
#pragma mark -
#pragma mark Basic Dialogs
#endif

LOCALPROC CheckSavedMacMsg(void)
{
    /* called only on quit, if error saved but not yet reported */

    if (nullpr != SavedBriefMsg) {
        /*char briefMsg0[ClStrMaxLength + 1];
        char longMsg0[ClStrMaxLength + 1];

        NativeStrFromCStr(briefMsg0, SavedBriefMsg);
        NativeStrFromCStr(longMsg0, SavedLongMsg);

        jstring jBriefMsg = (*jEnv)->NewStringUTF(jEnv, SavedBriefMsg);
        jstring jLongMsg = (*jEnv)->NewStringUTF(jEnv, SavedLongMsg);
        (*jEnv)->CallVoidMethod(jEnv, mCore, jWarnMsg, jBriefMsg, jLongMsg);
        (*jEnv)->DeleteLocalRef(jEnv, jBriefMsg);
        (*jEnv)->DeleteLocalRef(jEnv, jLongMsg);*/

        SavedBriefMsg = nullpr;
    }
}

/* --- clipboard --- */

#if IncludeHostTextClipExchange
LOCALVAR ui3p MyClipBuffer = NULL;
#endif

#if IncludeHostTextClipExchange
LOCALPROC FreeMyClipBuffer(void)
{
    if (MyClipBuffer != NULL) {
        free(MyClipBuffer);
        MyClipBuffer = NULL;
    }
}
#endif

#if IncludeHostTextClipExchange
GLOBALOSGLUFUNC tMacErr HTCEexport(tPbuf i)
{
    tMacErr err = mnvm_miscErr;

    FreeMyClipBuffer();
    if (MacRomanTextToNativePtr(i, falseblnr,
                                &MyClipBuffer))
    {
        // Convert Pbuf to a JNI string
        MacRomanTextToNativePtr(i, falseblnr, (ui3p *)&PbufDat[i]);
        jstring text = (*jEnv)->NewStringUTF(jEnv, (char *)MyClipBuffer);
        // Call Java method
        (*jEnv)->CallVoidMethod(jEnv, mCore, jSetClipboardText, text);
        (*jEnv)->DeleteLocalRef(jEnv, text);
        err = mnvm_noErr;
    }

    PbufDispose(i);

    return err;
}
#endif

#if IncludeHostTextClipExchange
LOCALPROC HTCEimport_do(void)
{
    // Call Java method to get clipboard text
    jstring result = (jstring) (*jEnv)->CallObjectMethod(jEnv, mCore, jGetClipboardText);

    if (result == NULL) {
        /* error */
    } else {
        FreeMyClipBuffer();
        // Convert Java string to C string
        const char *clipboardText = (*jEnv)->GetStringUTFChars(jEnv, result, NULL);
        size_t len = strlen(clipboardText);
        MyClipBuffer = (ui3p) malloc(len + 1);
        if (NULL == MyClipBuffer) {
            MacMsg(kStrOutOfMemTitle,
                   kStrOutOfMemMessage, falseblnr);
        } else {
            MyMoveBytes((anyp) clipboardText, (anyp) MyClipBuffer,
                        (si5b) len);
            MyClipBuffer[len] = 0;
        }

        (*jEnv)->ReleaseStringUTFChars(jEnv, result, clipboardText);
        (*jEnv)->DeleteLocalRef(jEnv, result);
    }
}
#endif

#if IncludeHostTextClipExchange
GLOBALOSGLUFUNC tMacErr HTCEimport(tPbuf *r)
{
    HTCEimport_do();

    return NativeTextToMacRomanPbuf((char *)MyClipBuffer, r);
}
#endif

#if 0
#pragma mark -
#pragma mark Emulation
#endif

LOCALPROC LeaveSpeedStopped(void)
{
    StartUpTimeAdjust();
}

LOCALPROC EnterSpeedStopped(void)
{
#if MySoundEnabled
    SoundOutputActive = falseblnr;
    SoundOutputRequested = falseblnr;
    (*jEnv)->CallVoidMethod(jEnv, mCore, jMySoundUnInit);
#endif
}

LOCALPROC CheckForSavedTasks(void)
{
    if (!atomic_load(&AutoSkipMessages) || atomic_load(&gBackgroundFlag)
            || atomic_load(&HostCommands) || atomic_load(&WantRestoreState)) CancelMessageSkip();
    if (atomic_exchange(&HostActivity, 0)) {
        CancelMessageSkip();
        poolrad_idle_activity(&IdleTracker, IdleNow());
        SetAutomaticIdle(falseblnr);
    }
    int commands = atomic_exchange(&HostCommands, 0);
    if (commands & HostReset) WantMacReset = trueblnr;
    if (commands & HostInterrupt) WantMacInterrupt = trueblnr;
    if (commands & HostRequestOff) RequestMacOff = trueblnr;
    if (commands & HostForceOff) ForceMacOff = trueblnr;
    if (RequestMacOff) {
        RequestMacOff = falseblnr;
        if (AnyDiskInserted()) {
            MacMsgOverride(kStrQuitWarningTitle,
                           kStrQuitWarningMessage);
        } else {
            ForceMacOff = trueblnr;
        }
    }

    if (ForceMacOff) {
        return;
    }

    if (CurSpeedStopped != (SpeedStopped || AutomaticIdle ||
                            atomic_load(&gBackgroundFlag)))
    {
        CurSpeedStopped = ! CurSpeedStopped;
        if (CurSpeedStopped) {
            EnterSpeedStopped();
        } else {
            LeaveSpeedStopped();
        }
    }

#if IncludeSonyNew
    if (vSonyNewDiskWanted) {
#if IncludeSonyNameNew
        if (vSonyNewDiskName != NotAPbuf) {
            ui3p NewDiskNameDat;
            if (MacRomanTextToNativePtr(vSonyNewDiskName, trueblnr,
                                        &NewDiskNameDat))
            {
                MakeNewDisk(vSonyNewDiskSize, (char *)NewDiskNameDat);
                free(NewDiskNameDat);
            }
            PbufDispose(vSonyNewDiskName);
            vSonyNewDiskName = NotAPbuf;
        } else
#endif
        {
            MakeNewDiskAtDefault(vSonyNewDiskSize);
        }
    }
#endif

    if ((nullpr != SavedBriefMsg) & ! MacMsgDisplayed) {
        MacMsgDisplayOn();
    }

    if (NeedWholeScreenDraw) {
        NeedWholeScreenDraw = falseblnr;
        ScreenChangedAll();
    }
}

/* --- main program flow --- */

/* UI thread queues a request; only the emulation thread copies live RAM. */
/* ---- whole-machine save state (see POOLRAD_SAVESTATE.h) ---- */

#define PRSS_MAGIC 0x50525353UL  /* "PRSS" */
#define PRSS_VERSION 3
#define PRSS_HEADER 24           /* magic, version, ramSize, totalLen, model, ROM checksum */
#define PRSS_CPU_AT PRSS_HEADER
#define PRSS_BULK_AT (PRSS_HEADER + PoolRadCPUStateSize)

struct PRSSCursor { ui3p buf; ui5b pos; ui5b cap; int mode; blnr ok; };
/* mode: 0 measure, 1 save, 2 restore, 3 validate bounded device fields */

LOCALPROC PRSSVisit(void *vctx, void *data, ui5b size)
{
	struct PRSSCursor *c = (struct PRSSCursor *)vctx;
	if (c->mode != 0) {
		if (c->pos + size > c->cap) { c->ok = falseblnr; return; }
		if (c->mode == 1) {
			memcpy(c->buf + c->pos, data, size);
		} else if (c->mode == 2) {
			memcpy(data, c->buf + c->pos, size);
		} else if (!SCC_ValidateSnapshotField(data, c->buf + c->pos)) {
            c->ok = falseblnr;
        }
	}
	c->pos += size;
}

/* The one ordering that save, restore and measure all walk. RAM last. */
LOCALPROC PRSSVisitAll(PoolRadStateVisitor visit, void *ctx)
{
	ui5b ramSize;
	ui3p ram = GetRamForSnapshot(&ramSize);
	GlobGlue_VisitState(visit, ctx);
	GlobGlue_VisitDevices(visit, ctx);
	/* GlobGlue_VisitState also captures the Mac II's separate video buffer,
	   which is not part of main RAM; see GLOBGLUE.c. */
	visit(ctx, ram, ramSize);
}

LOCALPROC PRSSPut32(ui3p p, ui5b v)
{
	p[0] = (ui3b)(v >> 24); p[1] = (ui3b)(v >> 16);
	p[2] = (ui3b)(v >> 8); p[3] = (ui3b)v;
}

LOCALFUNC ui5b PRSSGet32(const ui3b *p)
{
	return ((ui5b)p[0] << 24) | ((ui5b)p[1] << 16) | ((ui5b)p[2] << 8) | (ui5b)p[3];
}

GLOBALFUNC ui5b PoolRadSaveStateSize(void)
{
	struct PRSSCursor c;
	c.buf = nullpr; c.pos = 0; c.cap = 0; c.mode = 0; c.ok = trueblnr;
	PRSSVisitAll(PRSSVisit, &c);
	return PRSS_BULK_AT + c.pos;
}

GLOBALFUNC ui5b PoolRadSaveState(ui3p buf, ui5b cap)
{
	struct PRSSCursor c;
	ui5b ramSize;
	ui5b total = PoolRadSaveStateSize();
	(void) GetRamForSnapshot(&ramSize);
	if (cap < total || !SCC_PrepareSnapshot()) { return 0; }
	PRSSPut32(buf + 0, PRSS_MAGIC);
	PRSSPut32(buf + 4, PRSS_VERSION);
	PRSSPut32(buf + 8, ramSize);
	PRSSPut32(buf + 12, total);
	PRSSPut32(buf + 16, PoolRadMachineModel());
	PRSSPut32(buf + 20, PRSSGet32(ROM));
	PoolRadSaveCPUState(buf + PRSS_CPU_AT);
	c.buf = buf; c.pos = PRSS_BULK_AT; c.cap = cap; c.mode = 1; c.ok = trueblnr;
	PRSSVisitAll(PRSSVisit, &c);
	return c.ok ? c.pos : 0;
}

GLOBALFUNC blnr PoolRadRestoreState(const ui3b *buf, ui5b len)
{
	struct PRSSCursor c;
	ui5b ramSize;
	(void) GetRamForSnapshot(&ramSize);
	if (len < PRSS_BULK_AT) { return falseblnr; }
	if (PRSSGet32(buf + 0) != PRSS_MAGIC) { return falseblnr; }
	if (PRSSGet32(buf + 4) != PRSS_VERSION) { return falseblnr; }
	if (PRSSGet32(buf + 8) != ramSize) { return falseblnr; }
	if (PRSSGet32(buf + 12) != len) { return falseblnr; }
    if (PRSSGet32(buf + 16) != PoolRadMachineModel() || PRSSGet32(buf + 20) != PRSSGet32(ROM))
        return falseblnr;
	/* A truncated body must be refused before PRSSVisitAll changes any field. */
	if (len != PoolRadSaveStateSize()) { return falseblnr; }
    c.buf = (ui3p)buf; c.pos = PRSS_BULK_AT; c.cap = len; c.mode = 3; c.ok = trueblnr;
    PRSSVisitAll(PRSSVisit, &c);
    if (!c.ok) return falseblnr;
	/* RAM, devices and globals first; the CPU last, so m68k_setpc rebuilds
	   its fetch pointers against RAM that is already in place. */
	c.buf = (ui3p)buf; c.pos = PRSS_BULK_AT; c.cap = len; c.mode = 2; c.ok = trueblnr;
	PRSSVisitAll(PRSSVisit, &c);
	if (! c.ok) { return falseblnr; }
    poolrad_walk_reset(&MapWalkTracker); /* A restored machine is never traveled space. */
    SCC_AfterRestore();
    GlobGlue_AfterRestore();
#if 0 != vMacScreenDepth
    ColorMappingChanged = trueblnr;
#endif
	PoolRadRestoreCPUState(buf + PRSS_CPU_AT);
	return trueblnr;
}

GLOBALFUNC blnr PoolRadSaveStateSelfTest(void)
{
	ui5b size = PoolRadSaveStateSize();
	ui3p a = (ui3p)malloc(size);
	ui3p b = (ui3p)malloc(size);
	blnr ok = falseblnr;
	if ((a != nullpr) && (b != nullpr)) {
		if ((PoolRadSaveState(a, size) == size)
			&& PoolRadRestoreState(a, size)
			&& (PoolRadSaveState(b, size) == size))
		{
			ok = (memcmp(a, b, size) == 0) ? trueblnr : falseblnr;
			/* A self-consistent header with a short body used to begin changing
			   RAM before the visitor discovered the missing tail. Refuse first. */
			if (ok) {
				PRSSPut32(b + 12, size - 1);
				ok = !PoolRadRestoreState(b, size - 1)
					&& PoolRadSaveState(b, size) == size
					&& memcmp(a, b, size) == 0;
			}
		}
	}
	if (a != nullpr) { free(a); }
	if (b != nullpr) { free(b); }
	return ok;
}

/* ---- save/load, the real feature (F92) ---- */

/* UI thread asks; the emulation thread saves at the safe boundary below. */
GLOBALFUNC jboolean requestSaveState(void)
{
	return RequestWork(&WantSaveState);
}

/* UI thread hands over a raw save-state blob; copied here and applied at the
   boundary. One restore may be pending at a time. */
GLOBALFUNC jboolean requestRestoreState(const ui3b *data, ui5b len)
{
	if ((data == nullpr) || (len < 16) || (gRestoreBuf != nullpr)) {
		return JNI_FALSE;
	}
	gRestoreBuf = (ui3p) malloc(len);
	if (gRestoreBuf == nullpr) {
		return JNI_FALSE;
	}
	memcpy(gRestoreBuf, data, len);
	gRestoreLen = len;
	atomic_store(&WantRestoreState, 1);
	WakeEmulation();
	return JNI_TRUE;
}

LOCALPROC DeliverSaveState(void)
{
	ui5b sz;
	ui3p buf;
	jbyteArray arr = NULL;
	if (atomic_exchange(&WantSaveState, 0) == 0) return;
	/* Core serializes mounted-disk changes with this RAM copy and its ticket. */
	if ((*jEnv)->MonitorEnter(jEnv, mCore) != JNI_OK) {
		(*jEnv)->ExceptionClear(jEnv);
		(*jEnv)->CallVoidMethod(jEnv, mCore, jSaveState, NULL, NULL, 0, 0);
		return;
	}
	sz = PoolRadSaveStateSize();
	buf = (ui3p) malloc(sz);
	if (buf != nullpr) {
		ui5b n = PoolRadSaveState(buf, sz);
		arr = (*jEnv)->NewByteArray(jEnv, n);
		if (arr != NULL) {
			(*jEnv)->SetByteArrayRegion(jEnv, arr, 0, n, (const jbyte *) buf);
		} else {
			(*jEnv)->ExceptionClear(jEnv);
		}
		free(buf);
	}
	/* The same stopped boundary as the image above, not a later UI screenshot.
	 * Only copy a small raster here; PNG encoding happens on the Java IO worker. */
	jintArray preview = NULL;
	int pw = vMacScreenWidth < 384 ? vMacScreenWidth : 384;
	int ph = pw * vMacScreenHeight / vMacScreenWidth;
	if (ph > 288) { ph = 288; pw = ph * vMacScreenWidth / vMacScreenHeight; }
	ui3p draw = GetCurDrawBuff();
	if (arr != NULL && draw != nullpr) {
		jint *pixels = (jint *) malloc(pw * ph * sizeof(jint));
		if (pixels != NULL) {
			for (int y = 0; y < ph; y++) for (int x = 0; x < pw; x++) {
				int sx = x * vMacScreenWidth / pw, sy = y * vMacScreenHeight / ph;
#if 0 != vMacScreenDepth
				if (UseColorMode) {
					unsigned pixel = draw[sy * vMacScreenByteWidth + sx];
					pixels[y * pw + x] = 0xff000000u | ((CLUT_reds[pixel] >> 8) << 16)
						| ((CLUT_greens[pixel] >> 8) << 8) | (CLUT_blues[pixel] >> 8);
				} else
#endif
				{
					unsigned pixel = (draw[sy * vMacScreenMonoByteWidth + sx / 8] << (sx % 8)) & 0x80;
					pixels[y * pw + x] = pixel ? BLACK : WHITE;
				}
			}
			preview = (*jEnv)->NewIntArray(jEnv, pw * ph);
			if (preview != NULL) (*jEnv)->SetIntArrayRegion(jEnv, preview, 0, pw * ph, pixels);
			else (*jEnv)->ExceptionClear(jEnv);
			free(pixels);
		}
	}
	(*jEnv)->CallVoidMethod(jEnv, mCore, jSaveState, arr, preview, pw, ph);
	if (preview != NULL) (*jEnv)->DeleteLocalRef(jEnv, preview);
	if (arr != NULL) { (*jEnv)->DeleteLocalRef(jEnv, arr); }
	(*jEnv)->MonitorExit(jEnv, mCore);
}

LOCALPROC DeliverRestoreState(void)
{
	jboolean restored = JNI_FALSE;
	if (atomic_exchange(&WantRestoreState, 0) == 0) return;
	if (gRestoreBuf != nullpr) {
		if ((*jEnv)->MonitorEnter(jEnv, mCore) == JNI_OK) {
			jboolean verified = (*jEnv)->CallBooleanMethod(jEnv, mCore, jCanRestoreState);
			if ((*jEnv)->ExceptionCheck(jEnv)) {
				(*jEnv)->ExceptionClear(jEnv); verified = JNI_FALSE;
			}
			/* No disk write, mount or eject can intervene before the apply. */
			if (verified && PoolRadRestoreState(gRestoreBuf, gRestoreLen)) {
                GuestActivity(); /* Start the restored wait/activity afresh. */
				restored = JNI_TRUE;
				NeedWholeScreenDraw = trueblnr;
			}
			(*jEnv)->MonitorExit(jEnv, mCore);
		} else (*jEnv)->ExceptionClear(jEnv);
		free(gRestoreBuf); gRestoreBuf = nullpr; gRestoreLen = 0;
	}
	(*jEnv)->CallVoidMethod(jEnv, mCore, jStateRestored, restored);
}

GLOBALFUNC jboolean requestRamSnapshot(void)
{
    return RequestWork(&WantRamSnapshot);
}

LOCALPROC DeliverRamSnapshot(void)
{
    if (atomic_exchange(&WantRamSnapshot, 0) == 0) return;
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    jbyteArray snapshot = (*jEnv)->NewByteArray(jEnv, size);
    if (snapshot != NULL) {
        (*jEnv)->SetByteArrayRegion(jEnv, snapshot, 0, size,
                                   (const jbyte *)ram);
    } else {
        (*jEnv)->ExceptionClear(jEnv);
    }
    (*jEnv)->CallVoidMethod(jEnv, mCore, jRamSnapshot, snapshot);
    if (snapshot != NULL) (*jEnv)->DeleteLocalRef(jEnv, snapshot);
}

GLOBALFUNC jboolean requestMapSample(void)
{
    return RequestWork(&WantMapSample);
}

LOCALPROC DeliverMapSample(void)
{
    if (atomic_exchange(&WantMapSample, 0) == 0) return;
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    unsigned char data[POOLRAD_PROBE_SIZE];
    jbyteArray sample = NULL;
    if (poolrad_display_probe(ram, size, &MapWalkTracker, data)) {
        sample = (*jEnv)->NewByteArray(jEnv, POOLRAD_PROBE_SIZE);
        if (sample != NULL)
            (*jEnv)->SetByteArrayRegion(jEnv, sample, 0, POOLRAD_PROBE_SIZE, (const jbyte *)data);
        else
            (*jEnv)->ExceptionClear(jEnv);
    }
    (*jEnv)->CallVoidMethod(jEnv, mCore, jMapSample, sample);
    if (sample != NULL) (*jEnv)->DeleteLocalRef(jEnv, sample);
}

GLOBALFUNC jboolean requestWheelSample(void)
{
    return RequestWork(&WantWheelSample);
}

LOCALPROC DeliverWheelSample(void)
{
    if (atomic_exchange(&WantWheelSample, 0) == 0) return;
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    unsigned char data[POOLRAD_WHEEL_SIZE];
    jbyteArray sample = NULL;
    if (poolrad_wheel_probe(ram, size, PoolRadGetAddressRegister(6), PoolRadGetAddressRegister(7), data)) {
        sample = (*jEnv)->NewByteArray(jEnv, POOLRAD_WHEEL_SIZE);
        if (sample != NULL)
            (*jEnv)->SetByteArrayRegion(jEnv, sample, 0, POOLRAD_WHEEL_SIZE, (const jbyte *)data);
        else
            (*jEnv)->ExceptionClear(jEnv);
    }
    (*jEnv)->CallVoidMethod(jEnv, mCore, jWheelSample, sample);
    if (sample != NULL) (*jEnv)->DeleteLocalRef(jEnv, sample);
}

GLOBALFUNC jboolean requestPartySample(void)
{
    return RequestWork(&WantPartySample);
}

LOCALPROC DeliverPartySample(void)
{
    if (atomic_exchange(&WantPartySample, 0) == 0) return;
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    unsigned char data[POOLRAD_PARTY_SIZE], why[6] = {0};
    jbyteArray sample = NULL;
    if (poolrad_party_probe_why(ram, size, data, why)) {
        sample = (*jEnv)->NewByteArray(jEnv, POOLRAD_PARTY_SIZE);
        if (sample != NULL)
            (*jEnv)->SetByteArrayRegion(jEnv, sample, 0, POOLRAD_PARTY_SIZE, (const jbyte *)data);
        else
            (*jEnv)->ExceptionClear(jEnv);
    } else {
        /* Say which check refused rather than staying silent. Ten bytes, no
         * game content: a refusal code, how many roster links had passed, and
         * the heap address or block header the check actually rejected.
         */
        const unsigned char refusal[10] = {'P','R','P','X',
                why[0], why[1], why[2], why[3], why[4], why[5]};
        sample = (*jEnv)->NewByteArray(jEnv, (jsize) sizeof refusal);
        if (sample != NULL)
            (*jEnv)->SetByteArrayRegion(jEnv, sample, 0, (jsize) sizeof refusal, (const jbyte *)refusal);
        else
            (*jEnv)->ExceptionClear(jEnv);
    }
    (*jEnv)->CallVoidMethod(jEnv, mCore, jPartySample, sample);
    if (sample != NULL) (*jEnv)->DeleteLocalRef(jEnv, sample);
}

/* The only write. Guarded entirely inside poolrad_party_set_quick, which
 * refuses unless the whole party reads cleanly and the byte already holds a
 * value the field is allowed to have. Authorised by the owner on 2026-09-16;
 * see docs/DESIGN.md for what that does and does not cover.
 */
/* Called synchronously from Core.onPartySample on this same core thread. */
GLOBALFUNC jint partyTarget(jint member, jboolean forSheet)
{
    ui5b size; int x, y;
    ui3p ram = GetRamForSnapshot(&size);
    if (CurMouseButton || member < 0 || (forSheet && !poolrad_party_view_available(ram, size))
            || !poolrad_party_target(ram, size, (unsigned)member,
            vMacScreenWidth, vMacScreenHeight, &x, &y)) return -1;
    return (x << 16) | y;
}

GLOBALFUNC jboolean setPartyQuick(jint slot, jboolean on)
{
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    if (ram == NULL || slot < 0) return JNI_FALSE;
    int changed = poolrad_party_set_quick((unsigned char *) ram, size,
                                   (unsigned) slot, on == JNI_TRUE);
    if (changed) GuestActivity();
    return changed ? JNI_TRUE : JNI_FALSE;
}

/* F40: the second write, bandaging one Dying party member at a fight's end.
 * Guarded entirely inside poolrad_party_bandage; called on the core thread
 * from Core.onPartySample like setPartyQuick. */
GLOBALFUNC jint bandageParty(jint slot)
{
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    if (ram == NULL || slot < 0) return -1;
    int changed = poolrad_party_bandage((unsigned char *) ram, size, (unsigned) slot);
    if (changed > 0) GuestActivity();
    return changed;
}

/* F52: the third write, one member's full rest; guarded inside poolrad_party_rest. */
GLOBALFUNC jint restParty(jint slot)
{
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    if (ram == NULL || slot < 0) return -1;
    int changed = poolrad_party_rest((unsigned char *) ram, size, (unsigned) slot);
    /* Match bandage/quick: refresh activity after a successful helper write. */
    if (changed > 0) GuestActivity();
    return changed;
}

GLOBALFUNC jboolean requestMessageSample(void)
{
    return RequestWork(&WantMessageSample);
}

/* The text the game is displaying in its own Message window. Read-only, and
 * delivered even when unreadable so Java never mistakes silence for an empty
 * message. See POOLRAD_MESSAGE.h and docs/MESSAGE_MEMORY.md. */
LOCALPROC DeliverMessageSample(void)
{
    if (atomic_exchange(&WantMessageSample, 0) == 0) return;
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    unsigned char data[POOLRAD_MESSAGE_SIZE];
    jbyteArray sample = NULL;
    if (poolrad_message_probe(ram, size, data)) {
        sample = (*jEnv)->NewByteArray(jEnv, POOLRAD_MESSAGE_SIZE);
        if (sample != NULL)
            (*jEnv)->SetByteArrayRegion(jEnv, sample, 0, POOLRAD_MESSAGE_SIZE, (const jbyte *)data);
        else
            (*jEnv)->ExceptionClear(jEnv);
    }
    (*jEnv)->CallVoidMethod(jEnv, mCore, jMessageSample, sample);
    if (sample != NULL) (*jEnv)->DeleteLocalRef(jEnv, sample);
}

GLOBALFUNC jboolean requestCombatSample(void)
{
    return RequestWork(&WantCombatSample);
}

/* Where the game has placed each combatant on its own tactical grid. Read-only
 * and roster-checked; see POOLRAD_COMBAT.h and docs/COMBAT_MEMORY.md. */
LOCALPROC DeliverCombatSample(void)
{
    if (atomic_exchange(&WantCombatSample, 0) == 0) return;
    ui5b size;
    ui3p ram = GetRamForSnapshot(&size);
    unsigned char data[POOLRAD_COMBAT_SIZE];
    jbyteArray sample = NULL;
    if (poolrad_combat_probe(ram, size, data)) {
        sample = (*jEnv)->NewByteArray(jEnv, POOLRAD_COMBAT_SIZE);
        if (sample != NULL)
            (*jEnv)->SetByteArrayRegion(jEnv, sample, 0, POOLRAD_COMBAT_SIZE, (const jbyte *)data);
        else
            (*jEnv)->ExceptionClear(jEnv);
    }
    (*jEnv)->CallVoidMethod(jEnv, mCore, jCombatSample, sample);
    if (sample != NULL) (*jEnv)->DeleteLocalRef(jEnv, sample);
}

GLOBALOSGLUPROC DoneWithDrawingForTick(void)
{
#if EnableFSMouseMotion
    if (HaveMouseMotion) {
        AutoScrollScreen();
    }
#endif
    MyDrawChangesAndClear();
}

GLOBALOSGLUFUNC blnr ExtraTimeNotOver(void)
{
    UpdateTrueEmulatedTime();
    return TrueEmulatedTime == OnTrueTime;
}


GLOBALOSGLUPROC WaitForNextTick(void)
{
    /* Observe once per core-tick entry, not only on an Android map request.
     * All reads and the tracker stay on the emulator thread. Retry/paused
     * iterations below cannot create artificial continuity changes. */
    ui5b mapRamSize;
    ui3p mapRam = GetRamForSnapshot(&mapRamSize);
    poolrad_walk_observe(mapRam, mapRamSize, &MapWalkTracker);
    ObserveMessageSkip(mapRam, mapRamSize);
    ObserveAutomaticIdle(mapRam, mapRamSize);
    uint64_t wakeTicket;
    label_retry:
    wakeTicket = emulation_wait_ticket(&EmulationWait);
    CheckForSavedTasks();
    if (ForceMacOff) {
        return;
    }

    if (! StartupRestoreFinished && ! CurSpeedStopped) {
        jboolean held = (*jEnv)->CallBooleanMethod(jEnv, mCore, jPollStartupRestore);
        if ((*jEnv)->ExceptionCheck(jEnv)) {
            (*jEnv)->ExceptionClear(jEnv); held = JNI_FALSE;
        }
        if (! held) StartupRestoreFinished = trueblnr;
    }
    DeliverRamSnapshot();
    DeliverRestoreState();
    DeliverSaveState();
    DeliverMapSample();
    DeliverWheelSample();
    DeliverPartySample();
    DeliverMessageSample();
    DeliverCombatSample();

    if (CurSpeedStopped) {
        DoneWithDrawingForTick();
        emulation_wait_until_changed(&EmulationWait, wakeTicket);
        goto label_retry;
    }

    if (! StartupRestoreFinished) {
        struct timespec wait = {0, 10000000};
        DoneWithDrawingForTick();
        nanosleep(&wait, NULL);
        goto label_retry;
    }

    if (ExtraTimeNotOver()) {
        struct timespec rqt;
        struct timespec rmt;

        si5b TimeDiff = GetTimeDiff();
        if (TimeDiff < 0) {
            rqt.tv_sec = 0;
            rqt.tv_nsec = (- TimeDiff) * 1000;
            (void) nanosleep(&rqt, &rmt);
        }
        goto label_retry;
    }

#if MySoundEnabled
    MySound_UpdateOutput();
#endif
    if (CheckDateTime()) {
#if MySoundEnabled
        MySound_SecondNotify();
#endif
#if EnableDemoMsg
        DemoModeSecondNotify();
#endif
    }

#if UseMotionEvents
    if (! CaughtMouse)
#endif
    {
        //CheckMouseState();
    }

    OnTrueTime = TrueEmulatedTime;

#if dbglog_TimeStuff
    dbglog_writelnNum("WaitForNextTick, OnTrueTime", OnTrueTime);
#endif
}

/* --- platform independent code can be thought of as going here --- */

#include "PROGMAIN.h"

LOCALPROC ZapOSGLUVars(JNIEnv * env, jclass this, jobject core)
{
#if MySoundEnabled
    SoundOutputActive = falseblnr;
    SoundOutputRequested = falseblnr;
    SoundOptions = -2;
    LastGameSoundOptions = -1;
    SoundSamples = SoundTransfers = 0;
#endif
    //InitDrives();
    //ZapWinStateVars();

    ForceMacOff = falseblnr;
    atomic_store(&HostCommands, 0);
    atomic_store(&HostActivity, 0);
    atomic_store(&HostMouseHeld, 0);
    for (unsigned i = 0; i < 4; i++) atomic_store(&HostKeysHeld[i], 0);
    AutomaticIdle = falseblnr;
    atomic_store(&AutoSkipMessages, 0);
    memset(&SkipTracker, 0, sizeof SkipTracker);
    poolrad_idle_activity(&IdleTracker, IdleNow());
    CurSpeedStopped = trueblnr;
    StartupRestoreFinished = falseblnr;
    atomic_store(&WantRamSnapshot, 0);
    atomic_store(&WantSaveState, 0);
    atomic_store(&WantRestoreState, 0);
    if (gRestoreBuf != nullpr) { free(gRestoreBuf); gRestoreBuf = nullpr; }
    gRestoreLen = 0;
    atomic_store(&WantMapSample, 0);
    poolrad_walk_reset(&MapWalkTracker);
    atomic_store(&WantWheelSample, 0);
    atomic_store(&WantPartySample, 0);
    atomic_store(&WantMessageSample, 0);
    atomic_store(&WantCombatSample, 0);

    mCore = (*env)->NewGlobalRef(env, core);
    // get java method IDs
    jSonyTransfer = (*env)->GetMethodID(env, this, "sonyTransfer", "(ZLjava/nio/ByteBuffer;III)I");
    jSonyGetSize = (*env)->GetMethodID(env, this, "sonyGetSize", "(I)I");
    jSonyEject = (*env)->GetMethodID(env, this, "sonyEject", "(IZ)I");
    jSonyGetName = (*env)->GetMethodID(env, this, "sonyGetName", "(I)Ljava/lang/String;");
    jSonyMakeNewDisk = (*env)->GetMethodID(env, this, "sonyMakeNewDisk", "(ILjava/lang/String;)I");
    jSonyInsert2 = (*env)->GetMethodID(env, this, "sonyInsert2", "(Ljava/lang/String;)Z");
    jWarnMsg = (*env)->GetMethodID(env, this, "warnMsg", "(Ljava/lang/String;Ljava/lang/String;)V");
    jInitScreen = (*env)->GetMethodID(env, this, "initScreen", "()Z");
    jUpdateScreen = (*env)->GetMethodID(env, this, "updateScreen", "(IIII)V");
    jPlaySound = (*env)->GetMethodID(env, this, "playSound", "([B)I");
    jMySoundInit = (*env)->GetMethodID(env, this, "MySound_Init", "()Z");
    jMySoundUnInit = (*env)->GetMethodID(env, this, "MySound_UnInit", "()V");
    jMySoundStart = (*env)->GetMethodID(env, this, "MySound_Start", "()V");
    jMySoundStop = (*env)->GetMethodID(env, this, "MySound_Stop", "()V");
    jGetClipboardText = (*env)->GetMethodID(env, this, "getClipboardText", "()Ljava/lang/String;");
    jSetClipboardText = (*env)->GetMethodID(env, this, "setClipboardText", "(Ljava/lang/String;)V");
    jRamSnapshot = (*env)->GetMethodID(env, this, "onRamSnapshot", "([B)V");
	jSaveState = (*env)->GetMethodID(env, this, "onSaveState", "([B[III)V");
	jStateRestored = (*env)->GetMethodID(env, this, "onStateRestored", "(Z)V");
	jCanRestoreState = (*env)->GetMethodID(env, this, "canRestoreState", "()Z");
	jEmulationReady = (*env)->GetMethodID(env, this, "onEmulationReady", "()V");
    jAutomaticIdle = (*env)->GetMethodID(env, this, "onAutomaticIdle", "(Z)V");
	jPollStartupRestore = (*env)->GetMethodID(env, this, "pollStartupRestore", "()Z");
    jMapSample = (*env)->GetMethodID(env, this, "onMapSample", "([B)V");
    jWheelSample = (*env)->GetMethodID(env, this, "onWheelSample", "([B)V");
    jPartySample = (*env)->GetMethodID(env, this, "onPartySample", "([B)V");
    jMessageSample = (*env)->GetMethodID(env, this, "onMessageSample", "([B)V");
    jCombatSample = (*env)->GetMethodID(env, this, "onCombatSample", "([B)V");

    // initialize fields
    jfieldID sDiskPath, sDiskFile, sNumInsertedDisks;
    sDiskPath = (*env)->GetFieldID(env, this, "diskPath", "[Ljava/lang/String;");
    sDiskFile = (*env)->GetFieldID(env, this, "diskFile", "[Ljava/io/RandomAccessFile;");
    sNumInsertedDisks = (*env)->GetFieldID(env, this, "numInsertedDisks", "I");
    sInitOk = (*env)->GetFieldID(env, this, "initOk", "Z");

    // init drives
    jobjectArray diskPath = (*env)->NewObjectArray(env, NumDrives, (*env)->FindClass(env, "java/lang/String"), NULL);
    jobjectArray diskFile = (*env)->NewObjectArray(env, NumDrives, (*env)->FindClass(env, "java/io/RandomAccessFile"), NULL);
    (*env)->SetIntField(env, mCore, sNumInsertedDisks, 0);
    (*env)->SetObjectField(env, mCore, sDiskPath, diskPath);
    (*env)->SetObjectField(env, mCore, sDiskFile, diskFile);
    (*env)->DeleteLocalRef(env, diskPath);
    (*env)->DeleteLocalRef(env, diskFile);
}

LOCALPROC ReserveAllocAll(void)
{
    ReserveAllocOneBlock(&ROM, kROM_Size, 5, falseblnr);

    ReserveAllocOneBlock(&screencomparebuff,
                         vMacScreenNumBytes, 5, trueblnr);
#if UseControlKeys
    ReserveAllocOneBlock(&CntrlDisplayBuff,
                         vMacScreenNumBytes, 5, falseblnr);
#endif
#if WantScalingBuff
    ReserveAllocOneBlock(&ScalingBuff,
		ScalingBuffsz, 5, falseblnr);
#endif
#if WantScalingTabl
    ReserveAllocOneBlock(&ScalingTabl,
		ScalingTablsz, 5, falseblnr);
#endif

#if MySoundEnabled
    ReserveAllocOneBlock((ui3p *)&TheSoundBuffer,
                         dbhBufferSize, 5, falseblnr);
#endif

    EmulationReserveAlloc();
}

LOCALFUNC blnr AllocMyMemory(void)
{
    uimr n;
    blnr IsOk = falseblnr;

    ReserveAllocOffset = 0;
    ReserveAllocBigBlock = nullpr;
    ReserveAllocAll();
    n = ReserveAllocOffset;
    ReserveAllocBigBlock = (ui3p)calloc(1, n);
    if (NULL == ReserveAllocBigBlock) {
        MacMsg(kStrOutOfMemTitle, kStrOutOfMemMessage, trueblnr);
    } else {
        ReserveAllocOffset = 0;
        ReserveAllocAll();
        if (n != ReserveAllocOffset) {
            /* oops, program error */
        } else {
            IsOk = trueblnr;
        }
    }

    return IsOk;
}

LOCALPROC UnallocMyMemory(void)
{
    if (nullpr != ReserveAllocBigBlock) {
        free((char *)ReserveAllocBigBlock);
    }
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setWantMacReset
 * Signature: ()V
 */
GLOBALPROC setWantMacReset () {
    atomic_fetch_or(&HostCommands, HostReset);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setWantMacReset
 * Signature: ()V
 */
GLOBALPROC setWantMacInterrupt () {
    atomic_fetch_or(&HostCommands, HostInterrupt);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setRequestMacOff
 * Signature: ()V
 */
GLOBALPROC setRequestMacOff () {
    atomic_fetch_or(&HostCommands, HostRequestOff);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setForceMacOff
 * Signature: ()V
 */
GLOBALPROC setForceMacOff () {
    atomic_fetch_or(&HostCommands, HostForceOff);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    _resumeEmulation
 * Signature: ()V
 */
GLOBALPROC resumeEmulation () {
    atomic_store(&gBackgroundFlag, 0);
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    _pauseEmulation
 * Signature: ()V
 */
GLOBALPROC pauseEmulation () {
    atomic_store(&gBackgroundFlag, 1);
    WakeEmulation();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    isPaused
 * Signature: ()Z
 */
GLOBALFUNC jboolean isPaused () {
    return CurSpeedStopped?JNI_TRUE:JNI_FALSE;
}

GLOBALPROC setAutoSkipMessages(jboolean enabled) {
    if (atomic_exchange(&AutoSkipMessages, enabled != 0) != (enabled != 0)) GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    setSpeed
 * Signature: (I)V
 */
GLOBALPROC setSpeed (jint value) {
    SpeedValue = (ui3b)value;
    GuestActivity();
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    getSpeed
 * Signature: ()I
 */
GLOBALFUNC jint getSpeed () {
    return (jint)SpeedValue;
}

#if 0
#pragma mark -
#pragma mark Misc
#endif

LOCALFUNC tMacErr LoadMacRomFrom(void * romData, size_t romSize)
{
    tMacErr err;

    if (NULL == romData) {
        err = mnvm_fnfErr;
    } else {
        memcpy(ROM, romData, kROM_Size);
        err = ROM_IsValid();
    }

    return err;
}

LOCALFUNC blnr LoadMacRom(void * romData, size_t romSize)
{
    tMacErr err;

    if (mnvm_fnfErr == (err = LoadMacRomFrom(romData, romSize)))
    {
    }

    return trueblnr; /* keep launching Mini vMac, regardless */
}

LOCALFUNC blnr Screen_Init(void) {

#if 0 != vMacScreenDepth
    ColorModeWorks = trueblnr;
#endif

    return trueblnr;
}

LOCALFUNC blnr InitOSGLU(void * romData, size_t romSize)
{
    if (AllocMyMemory())
#if CanGetAppPath
        if (InitWhereAmI())
#endif
#if dbglog_HAVE
        if (dbglog_open())
#endif
        //if (ScanCommandLine())
        if (LoadMacRom(romData, romSize))
            if (LoadInitialImages())
#if UseActvCode
                if (ActvCodeInit())
#endif
                if (InitLocationDat())
                        if (Screen_Init())
                            if ((*jEnv)->CallBooleanMethod(jEnv, mCore, jInitScreen))
                                //if (KC2MKCInit())
#if EmLocalTalk
                                if (EntropyGather())
                                    if (InitLocalTalk())
#endif
                                        if (WaitForRom())
                                        {
                                            return trueblnr;
                                        }
    return falseblnr;
}

LOCALPROC UnInitOSGLU(void)
{
    if (MacMsgDisplayed) {
        MacMsgDisplayOff();
    }

#if EmLocalTalk
    UnInitLocalTalk();
#endif

    //RestoreKeyRepeat();
#if MayFullScreen
    //UngrabMachine();
#endif
#if MySoundEnabled
    (*jEnv)->CallVoidMethod(jEnv, mCore, jMySoundStop);
#endif
#if MySoundEnabled
    (*jEnv)->CallVoidMethod(jEnv, mCore, jMySoundUnInit);
#endif
#if IncludeHostTextClipExchange
    FreeMyClipBuffer();
#endif
#if IncludePbufs
    UnInitPbufs();
#endif
    UnInitDrives();

#if dbglog_HAVE
    dbglog_close();
#endif

#if CanGetAppPath
    UninitWhereAmI();
#endif
    UnallocMyMemory();

    CheckSavedMacMsg();

    initDone = falseblnr;
}

/*
 * Class:     name_osher_gil_minivmac_Core
 * Method:    init
 * Signature: (Ljava/nio/ByteBuffer;)V
 */
GLOBALFUNC jboolean init (JNIEnv *env, jclass this, jobject core, jobject romBuffer) {
    if (initDone) return JNI_FALSE;

    JavaVM *jvm;
    if ((*env)->GetJavaVM(env, &jvm)) return JNI_ERR;
    if ((*jvm)->GetEnv(jvm, (void **)&jEnv, JNI_VERSION_1_2)) return JNI_ERR;

    void * romData = (*env)->GetDirectBufferAddress(env, romBuffer);
    size_t romSize = (*env)->GetDirectBufferCapacity(env, romBuffer);

    ZapOSGLUVars(env, this, core);
    if (InitOSGLU(romData, romSize)) {
        // init ok
        initDone = trueblnr;
        (*env)->SetBooleanField(env, mCore, sInitOk, JNI_TRUE);
        (*env)->CallVoidMethod(env, mCore, jEmulationReady);

        ProgramMain();
    }
    (*env)->SetBooleanField(env, mCore, sInitOk, JNI_FALSE);
    UnInitOSGLU();
    (*env)->DeleteGlobalRef(env, mCore);

    return JNI_TRUE;
}

#endif /* WantOSGLUJNI */
