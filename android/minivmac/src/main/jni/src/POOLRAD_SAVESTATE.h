/*
	POOLRAD_SAVESTATE.h

	A whole-machine save state for the companion's fast save/load.

	The one design rule that keeps this correct: save and restore never have
	their own field lists. Both walk a single visitor, PoolRadVisitState, which
	names every piece of mutable state exactly once. A save visitor copies each
	piece out; a restore visitor copies each piece in; a measure visitor adds up
	the sizes. Because there is only one ordering, save and restore cannot drift
	apart -- the classic way a hand-written save state corrupts a resume.

	Completeness needs device-state review and behavioural resume tests.
	PoolRadSaveStateSelfTest checks round-trip equality of the included fields;
	it cannot detect state that was omitted from both captures.

	A save state is tied to this exact emulator build and its disks, which is
	inherent to save states and fine for a single-purpose app.
*/

#ifndef POOLRAD_SAVESTATE_H
#define POOLRAD_SAVESTATE_H

/*
	Called once per piece of state. On save, copy `size` bytes from `data`
	into the stream; on restore, copy them into `data`; on measure, just add
	`size`. `data` is NULL only for the RAM block, which the coordinator holds
	directly.
*/
typedef void (*PoolRadStateVisitor)(void *ctx, void *data, ui5b size);

/* Each device module names its own state; defined beside that state. */
EXPORTPROC VIA1_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC VIA2_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC RTC_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTFUNC blnr SCC_PrepareSnapshot(void);
EXPORTPROC SCC_AfterRestore(void);
EXPORTFUNC blnr SCC_ValidateSnapshotField(void *field, const ui3b *bytes);
EXPORTPROC SCC_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC Sound_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC Sony_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC ADB_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC Video_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC IWM_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC GlobGlue_AfterRestore(void);
EXPORTFUNC ui5b PoolRadMachineModel(void);
EXPORTPROC GlobGlue_VisitState(PoolRadStateVisitor visit, void *ctx);
EXPORTPROC GlobGlue_VisitDevices(PoolRadStateVisitor visit, void *ctx);

/*
	How many bytes a save state occupies, including the fixed header, the CPU
	block, all device and global state, and RAM.
*/
EXPORTFUNC ui5b PoolRadSaveStateSize(void);

/*
	Write a save state into `buf` (at least PoolRadSaveStateSize() bytes).
	Returns the number of bytes written, or 0 if `cap` is too small.
*/
EXPORTFUNC ui5b PoolRadSaveState(ui3p buf, ui5b cap);

/*
	Restore from a save state. Returns trueblnr on success. RAM and every
	device are put back first; the CPU last, so m68k_setpc rebuilds its fetch
	pointers against the RAM that is already in place.
*/
EXPORTFUNC blnr PoolRadRestoreState(const ui3b *buf, ui5b len);

/*
	Capture the machine, restore it in place, and capture again; return
	trueblnr when the two captures are byte-identical. A mismatch means the included state did not round-trip exactly.
	Equality alone does not establish completeness. Debug builds only.
*/
EXPORTFUNC blnr PoolRadSaveStateSelfTest(void);

#endif
