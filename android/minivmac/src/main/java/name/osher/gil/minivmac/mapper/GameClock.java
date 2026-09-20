package name.osher.gil.minivmac.mapper;

import java.util.Locale;

/** Original game's counters, never Android time or extrapolated elapsed time. */
public final class GameClock {
    public final int day, hour, minute;

    private GameClock(int day, int hour, int minute) {
        this.day = day; this.hour = hour; this.minute = minute;
    }

    static GameClock parse(byte[] packet) {
        if ((packet[3] != '6' && packet[3] != '7') || packet.length != PoolRadState.packetSize(packet[3]) || packet[1204] != 1
                || packet[1211] != 0 || packet[24] < 1 || packet[24] > 4) return null;
        long day = ((packet[1205] & 255L) << 24) | ((packet[1206] & 255L) << 16)
                | ((packet[1207] & 255L) << 8) | (packet[1208] & 255L);
        int hour = packet[1209] & 255, minute = packet[1210] & 255;
        return day >= 1 && day <= 92160 && hour < 24 && minute < 60
                ? new GameClock((int) day, hour, minute) : null;
    }

    public String label() {
        return String.format(Locale.US, "Day %d · %d:%02d %s", day,
                hour % 12 == 0 ? 12 : hour % 12, minute, hour < 12 ? "am" : "pm");
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof GameClock)) return false;
        GameClock clock = (GameClock) other;
        return day == clock.day && hour == clock.hour && minute == clock.minute;
    }

    @Override public int hashCode() { return (day * 24 + hour) * 60 + minute; }
}
