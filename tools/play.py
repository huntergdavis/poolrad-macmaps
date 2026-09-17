#!/usr/bin/env python3
"""Scripted play, so a feature can be photographed against a real battle.

Built on tools/guest.py. Every step waits on the screen rather than on a
sleep: the Continue button is found by its own ink, a move is confirmed by the
Message window changing, and the game is only believed to be somewhere once the
picture has held still.

    tools/play.py --serial emulator-5584 boot
    tools/play.py --serial emulator-5584 load SampleParty
    tools/play.py --serial emulator-5584 tour
    tools/play.py --serial emulator-5584 walk 8 8 6 8
    tools/play.py --serial emulator-5584 guard      # the council-guard fight
    tools/play.py --serial emulator-5584 shot out.png

Emulator only, and it plays the game the way a player does: menus, keys and the
mouse. It never writes guest memory. See docs/GUEST_SCRIPTING.md.
"""

import argparse
import re
import subprocess
import sys
import time

import guest

PACKAGE = "com.hunterdavis.poolradmacmaps.ii"

# Guest furniture, in Android screen pixels on the 1200x1600 test emulator.
# Everything else is found by keyboard or menu, so this is the whole table.
MENU_BAR_Y = 646
FILE_MENU_X = 92
LOAD_ITEM_Y = 686
# The "Load Saved Game..." row of the open File menu. Enabled it is solid black
# and measures about 5700 dark pixels; greyed out, the Mac dithers it to about
# 1200. That is the only honest way to tell, because picking a disabled item
# does nothing and looks exactly like picking nothing.
LOAD_ROW = (90, 672, 300, 24)
LOAD_ENABLED_INK = 3000
# Whose menu bar is this? The game's runs "File Edit Character Options Windows"
# and reaches into this slice; the Finder's stops after Special and leaves it
# blank. Without this the Finder's own File menu passed for the game's -- its
# second item is Open, which is where Load Saved Game sits -- and a scripted
# load went type-selecting around the desktop instead.
#
# Counted at a grey threshold, not a black one. With no save loaded the game
# greys "Windows" out, and dithered grey has no pixels below 128 at all: the
# first version of this read 0 and called a perfectly good game the Finder.
# Below 200 the three cases are 826 (game, nothing loaded), 989 (game, in a
# battle) and 0 (Finder).
GAME_MENU_BAR = (480, 638, 120, 22)
GAME_MENU_GREY = 200
GAME_MENU_INK = 400
# Launching the game from the Finder, for a disk whose startup items do not do
# it. Click the open folder's title bar, type-select the application -- "Pool"
# sorts to "Pool of Radiance v1.1" ahead of PoolRad2 and the rest, because the
# space beats the R -- then File > Open, which is the Finder's second item.
FOLDER_TITLE = (400, 710)
FINDER_OPEN_Y = 710
GAME_PREFIX = "Pool"
# The OK button of the Mac's "this computer may not have been shut down
# properly" dialog, which is the first thing a cold boot shows. Clicked rather
# than answered with Return: a touch is delivered by position, but a key needs
# the window to hold focus, and under load this one sits on top with
# mCurrentFocus=null for minutes at a time. With no dialog there, that spot is
# bare Mac desktop and the click does nothing.
STARTUP_OK = (892, 1294)
MESSAGE = (200, 1230, 800, 250)
# The button row inside the Message window, which is where the game says what
# it wants next.
# Measured by scanning the row a line at a time: the Message window's own frame
# runs the full width at y 1474/1479/1480 and again from 1522, and would merge
# every button into one run. Between them, only a button puts ink in a column.
BUTTON_ROW = (20, 1483, 1000, 38)
# Just the buttons' top edges. Counting over the whole row made the mouse
# pointer a third button in a two-button question; the pointer sits low in the
# row and never on this line.
BUTTON_TOPS = (20, 1483, 1000, 3)
# The Message window's own frame: two rules that run the full width just above
# the buttons. In the game they measure about 2000 dark pixels; on the Finder's
# desktop, about 360. Without this check the desktop counted as one wide button
# and read as Continue -- and a tour once clicked the bare desktop 250 times
# because the game had quit underneath it.
# Somewhere in this band the Message window draws a rule right across itself.
# Pinning it to two named rows did not survive contact: the window sits a pixel
# differently from one state to the next, so a box over rows 1479-1480 caught
# both rules in one screenshot and only one in the next, and the game read as
# not running while Rolf was talking. Scan the band and look for any full-width
# rule instead.
FRAME_BAND = (20, 1470, 1000, 13)
FRAME_INK = 900
# The game's own coordinate and facing readout, e.g. "0,4 W". A move is only
# believed once this changes, which is the one signal that distinguishes a step
# from a bump into a wall. The clock beside it is deliberately outside the box,
# or every sample would differ.
POSITION = (505, 1000, 120, 40)

# Exploration takes the number keys; combat takes the arrows. Both are the
# game's own input, sent as if typed.
STEPS = {"forward": "8", "left": "4", "right": "6", "back": "2"}


def settled(serial, quiet=1200, timeout=90000, region="message"):
    found = guest.settle(serial, region, quiet, timeout, 250)
    if found is None:
        raise SystemExit("The guest never settled")
    return found


def boot(serial):
    """Cold-start the app and take the guest to a game that can be loaded into.

    Force-stopped first, deliberately. Launching an app that is already running
    resumes whatever it was doing, and what it was doing may be a game in
    progress -- in which case Load Saved Game is greyed out and the next step
    fails for a reason that has nothing to do with booting.
    """
    guest.shell(serial, "am force-stop %s" % PACKAGE)
    time.sleep(2)
    guest.shell(serial, "monkey -p %s -c android.intent.category.LAUNCHER 1" % PACKAGE)
    # Wait for it to actually be in front, rather than letting the first
    # keystroke fail the foreground guard with a less useful message.
    waited = time.monotonic() + 120
    while time.monotonic() < waited:
        front = guest.foreground(serial)
        if front.startswith(guest.PACKAGE) or (
                not front and guest.resumed(serial).startswith(guest.PACKAGE)):
            break
        time.sleep(2)
    else:
        raise SystemExit("PoolRad would not come to the front; %s is there instead. "
                         "If force-stopping that does not help, reboot the emulator."
                         % (guest.foreground(serial) or guest.resumed(serial) or "something"))
    # The Mac may complain it was not shut down properly; Return dismisses it,
    # and sending Return when there is no dialog costs nothing.
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        # Ask before knocking. Sending Return to a game that is already up
        # starts one -- and then Load Saved Game is greyed out and the next
        # step fails, having been told the boot succeeded.
        if game_menu_ready(serial):
            print("booted")
            return
        settled(serial, quiet=2500, timeout=240000, region="guest")
        # Click the dialog's OK, then send Return as well. Return alone left
        # the Mac sitting on that dialog for a whole five-minute timeout,
        # because the window was on top without holding focus and every key
        # was dropped in silence.
        guest.click(serial, *STARTUP_OK)
        guest.send(serial, "input keyevent ENTER")
        time.sleep(2)
        # Some disks start the game themselves and some leave you in the Finder.
        if not game_frontmost(serial):
            launch_from_finder(serial)
            settled(serial, quiet=2500, timeout=240000, region="guest")
    raise SystemExit("The game never reached its menu bar")


def launch_from_finder(serial):
    """Open the game from the Finder, for disks that do not start it themselves."""
    guest.click(serial, *FOLDER_TITLE)
    time.sleep(1)
    guest.send(serial, "input text %s" % GAME_PREFIX)
    time.sleep(1)
    guest.drag(serial, FILE_MENU_X, MENU_BAR_Y, FILE_MENU_X + 58, FINDER_OPEN_Y)


def game_frontmost(serial, screen=None):
    """True when Pool of Radiance owns the menu bar, rather than the Finder."""
    screen = screen or guest.grab(serial)
    return screen.ink(GAME_MENU_BAR, GAME_MENU_GREY) >= GAME_MENU_INK


def load_item_enabled(serial, patience=4.0):
    """Open the File menu, hold it, and report whether Load Saved Game is live.

    Polled rather than sampled once. Measured ink is 5741 for the enabled item,
    1185 for the greyed one and under 1000 for anything that is not an open menu
    at all, so the reading itself is unambiguous -- but under load the menu can
    still be drawing 0.8s after the mouse goes down, and a single early sample
    read as greyed on a boot that had in fact just succeeded.

    Leaves the menu open; the caller releases, over the item or over the title.
    """
    guest.send(serial, "input motionevent DOWN %d %d" % (FILE_MENU_X, MENU_BAR_Y))
    guest.send(serial, "input motionevent MOVE %d %d" % (FILE_MENU_X, LOAD_ITEM_Y))
    deadline = time.monotonic() + patience
    while True:
        if guest.grab(serial).ink(LOAD_ROW) >= LOAD_ENABLED_INK:
            return True
        if time.monotonic() >= deadline:
            return False
        time.sleep(0.4)


def close_menu(serial):
    guest.send(serial, "input motionevent UP %d %d" % (FILE_MENU_X, MENU_BAR_Y))
    time.sleep(0.6)


def game_menu_ready(serial):
    """True once File -> Load Saved Game is not merely present but enabled.

    Checking only that a menu opened was not enough: the Finder has a File menu
    too, and so does a game already in progress, where the item is greyed. Both
    reported a successful boot and then failed at the next step for reasons that
    had nothing to do with booting.
    """
    if not game_frontmost(serial):
        return False
    ready = load_item_enabled(serial)
    close_menu(serial)
    return ready


def load(serial, save, folder="PoolRadSave"):
    """File -> Load Saved Game, then the standard file dialog by type-select.

    Only works straight after `boot`. Once a game is running the game greys
    Load Saved Game out -- only Quit stays enabled -- and picking a disabled
    item does nothing at all. That failed silently once: the folder and save
    names were then typed into the running game, where a stray letter opened
    the camp menu and the party was found altering its marching order.
    """
    # Hold the menu open, read whether the item is live, and only then let go
    # over it. Comparing the screen before and after does not work: the game
    # animates a campfire, so something is always different.
    if not game_frontmost(serial):
        raise SystemExit("Pool of Radiance is not the front application; the "
                         "Finder is. Its File menu is not the game's. Run `boot`.")
    if not load_item_enabled(serial):
        close_menu(serial)
        raise SystemExit("Load Saved Game is greyed out, which it is whenever a "
                         "game is already running. Run `boot` first.")
    guest.send(serial, "input motionevent UP %d %d" % (FILE_MENU_X + 58, LOAD_ITEM_Y))
    settled(serial, quiet=1200, region="guest")
    for name in (folder, save):
        guest.send(serial, "input text %s" % subprocess.list2cmdline([name]))
        time.sleep(0.8)
        guest.send(serial, "input keyevent ENTER")
        settled(serial, quiet=1500)
    print("loaded", save)


def walk(serial, moves):
    """One exploration step per move, each confirmed by the Message window."""
    box = ",".join(str(v) for v in POSITION)
    moved = 0
    for move in moves:
        key = STEPS.get(move, move)
        before = guest.grab(serial).digest(POSITION)
        guest.send(serial, "input text %s" % key)
        if guest.await_change(serial, box, before, 6000, 200) is None:
            print("blocked:", move)      # a wall, or a prompt took the key
        else:
            moved += 1
        if steady_state(serial)[0] == "continue":
            tour(serial)
    print("walked %d of %d" % (moved, len(moves)))


def row_box():
    return ",".join(str(v) for v in BUTTON_TOPS)


def button_spans(screen, column=10, floor=6):
    """Where the Message window's buttons are, left to right.

    The game says what it wants by how many buttons it draws: one is Continue,
    two is a yes/no question, four is an encounter (Combat/Wait/Flee/Advance)
    and six is ordinary exploration. Counting them is steadier than recognising
    any wording, and it does not care where the mouse pointer is parked.
    """
    x, y, width, height = BUTTON_TOPS
    spans, start = [], None
    for offset in range(0, width, column):
        inked = screen.ink((x + offset, y, column, height)) > floor
        if inked and start is None:
            start = offset
        elif not inked and start is not None:
            spans.append((x + start, x + offset))
            start = None
    if start is not None:
        spans.append((x + start, x + width))
    return spans


def playing(screen):
    """True when the game's Message window is on screen at all."""
    x, y, width, height = FRAME_BAND
    return any(screen.ink((x, y + row, width, 1)) >= FRAME_INK for row in range(height))


def state(screen):
    """What the game is asking for, by how many buttons it is offering."""
    if not playing(screen):
        return "no game"
    return {0: "busy", 1: "continue", 2: "question",
            4: "encounter", 6: "explore"}.get(len(button_spans(screen)), "unknown")


KNOWN = ("continue", "question", "encounter", "explore")


def steady_state(serial, tries=30):
    """The same recognised answer twice running.

    A frame taken the instant a move lands catches the game mid-redraw: the row
    is briefly empty, or half of it is painted. Judged on one frame that read as
    an encounter on every first step, and a half-painted exploration row -- Area,
    Cast, View and nothing yet -- counts as three buttons, which is not a state
    the game has. So wait for the same recognised answer twice rather than
    believing the first thing seen or giving up on a frame in transit.
    """
    last, screen = None, None
    for _ in range(tries):
        screen = guest.grab(serial)
        now = state(screen)
        if now == last and now in KNOWN:
            return now, screen
        last = now
        time.sleep(0.5)
    if last == "no game":
        # Waited for it rather than judging the first frame: straight after a
        # load the game spends a few seconds drawing, and there is genuinely no
        # Message window yet.
        raise SystemExit("Pool of Radiance is not running: its Message window "
                         "never appeared. Run `boot` and `load`.")
    return last, screen


def press_button(serial, screen, index):
    x1, x2 = button_spans(screen)[index]
    guest.click(serial, (x1 + x2) // 2, BUTTON_ROW[1] + BUTTON_ROW[3] // 2)


def tour(serial, limit=250):
    """Rolf's introduction, and any other prompt that only wants Continue.

    The limit is high on purpose: Rolf's walk around Phlan is well over a
    hundred prompts, and a limit of 60 stopped in the middle of it.
    """
    clicks = 0
    while clicks < limit:
        now, screen = steady_state(serial)
        if now != "continue":
            break
        press_button(serial, screen, 0)
        clicks += 1
    if clicks == limit:
        raise SystemExit("Still asking for Continue after %d clicks" % limit)
    print("continued", clicks)
    return clicks


def answer(serial, screen, yes=False):
    """A yes/no question. No by default: a script should not spend the party's money."""
    press_button(serial, screen, 0 if yes else 1)


def wander(serial, limit=120):
    """Walk until a fight starts.

    No route and no geography: it goes forward while it can and turns when it
    cannot, which is all that is needed to meet a wandering monster. Prompts on
    the way are answered -- Continue clicked, questions declined -- so an inn
    asking for a platinum piece does not end the run.
    """
    box = ",".join(str(v) for v in POSITION)
    blocked = 0
    for move in range(limit):
        now, screen = steady_state(serial)
        if now == "encounter":
            print("encounter after %d moves" % move)
            return move
        if now == "continue":
            tour(serial)
            continue
        if now == "question":
            # Turn away afterwards. Walking into the inn is what raised the
            # question, and the turn key sent while the prompt was up went
            # nowhere, so without this the party declines, steps back into the
            # same doorway and asks again until the move budget runs out.
            answer(serial, screen)
            guest.await_change_screen(serial, row_box(), screen.digest(BUTTON_TOPS), 6000, 300)
            guest.send(serial, "input text %s" % STEPS["right" if blocked % 2 else "left"])
            blocked += 1
            continue
        if now != "explore":
            raise SystemExit("Gave up on a row of %d buttons (%s)"
                             % (len(button_spans(screen)), now))
        before = screen.digest(POSITION)
        guest.send(serial, "input text %s" % STEPS["forward"])
        if guest.await_change_screen(serial, box, before, 4000, 400) is None:
            # A wall. Turn rather than keep pushing at it, alternating so the
            # party does not settle into a two-square loop.
            guest.send(serial, "input text %s" % STEPS["right" if blocked % 2 else "left"])
            blocked += 1
    print("no encounter in %d moves" % limit)
    return None


def fight(serial):
    """Answer an encounter with Combat, which is the leftmost button."""
    now, screen = steady_state(serial)
    if now != "encounter":
        raise SystemExit("Not at an encounter; the row shows %s" % now)
    press_button(serial, screen, 0)
    settled(serial, quiet=1500, timeout=60000)
    print("in combat")


def battle(serial, limit=200):
    """Wander to a fight and enter it, which is the whole point of this file."""
    if wander(serial, limit) is None:
        raise SystemExit("Never met anything")
    fight(serial)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    sub = parser.add_subparsers(dest="scenario", required=True)
    sub.add_parser("boot")
    sub.add_parser("load").add_argument("save", nargs="?", default="SampleParty")
    sub.add_parser("tour").add_argument("--limit", type=int, default=250)
    sub.add_parser("walk").add_argument("moves", nargs="+")
    sub.add_parser("wander").add_argument("--limit", type=int, default=120)
    sub.add_parser("fight")
    sub.add_parser("battle").add_argument("--limit", type=int, default=200)
    sub.add_parser("state")
    sub.add_parser("shot").add_argument("path")
    args = parser.parse_args(argv)
    if not guest.SERIAL.match(args.serial):
        raise SystemExit("Refusing a non-emulator target: %s" % args.serial)

    if args.scenario == "boot":
        boot(args.serial)
    elif args.scenario == "load":
        load(args.serial, args.save)
    elif args.scenario == "tour":
        tour(args.serial, args.limit)
    elif args.scenario == "walk":
        walk(args.serial, args.moves)
    elif args.scenario == "wander":
        wander(args.serial, args.limit)
    elif args.scenario == "fight":
        fight(args.serial)
    elif args.scenario == "battle":
        battle(args.serial, args.limit)
    elif args.scenario == "state":
        print(steady_state(args.serial)[0])
    elif args.scenario == "shot":
        guest.grab(args.serial).png(args.path)
        print("wrote", args.path)
    return 0


if __name__ == "__main__":
    sys.path.insert(0, __file__.rsplit("/", 1)[0])
    sys.exit(main())
