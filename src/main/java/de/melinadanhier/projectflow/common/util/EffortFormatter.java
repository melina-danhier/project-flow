package de.melinadanhier.projectflow.common.util;

public final class EffortFormatter {

    private EffortFormatter() {
    }

    public static String formatMinutes(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "";
        }

        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;

        if (hours > 0 && remainingMinutes > 0) {
            return hours + " Std. " + remainingMinutes + " Min.";
        } else if (hours > 0) {
            return hours + " Std.";
        } else {
            return remainingMinutes + " Min.";
        }
    }
}
