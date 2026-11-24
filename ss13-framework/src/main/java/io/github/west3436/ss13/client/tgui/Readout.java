package io.github.west3436.ss13.client.tgui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/**
 * Pressures and temperatures as a GUI or an item's tooltip shows them, in the player's units. It
 * refers to no client-only class, so an item, which a dedicated server also loads, can use it.
 *
 * <p>Values come in kPa and K, and are shown in the {@link Units} installed with {@link #useUnits}:
 * kPa and °C until a mod that lets players choose installs its own.
 */
public final class Readout {
    /** The units a reading is shown in. Each method takes a value in kPa or K and gives it in its unit. */
    public interface Units {
        /** A pressure in kPa, in this unit. */
        default double pressure(double kpa) {
            return kpa;
        }

        /** The pressure unit's symbol, such as {@code kPa}. */
        default String pressureSymbol() {
            return "kPa";
        }

        /** A temperature in K, in this unit. */
        default double temperature(double kelvin) {
            return kelvin - 273.15;
        }

        /** The temperature unit's symbol, such as {@code °C}. */
        default String temperatureSymbol() {
            return "°C";
        }
    }

    /** The defaults: kPa and °C. */
    public static final Units DEFAULT_UNITS = new Units() {};

    /** A reading above zero and under this is a trace ({@link #traceNumber}). */
    private static final BigDecimal TRACE_BELOW = new BigDecimal("0.1");

    private static final MathContext TWO_FIGURES = new MathContext(2, RoundingMode.HALF_UP);

    /** The decimals of a trace's reading that is no trace. */
    private static final int TRACE_DECIMALS = 3;

    /** A trace is written with no more decimals than this past a reading's own. */
    private static final int MORE_TRACE_DECIMALS = 4;

    private static volatile Units units = DEFAULT_UNITS;

    private Readout() {}

    /**
     * Shows readings in other units from now on. The mod that owns the player's choice of units
     * installs them, once, while it loads; the last installed is the one used.
     */
    public static void useUnits(Units chosen) {
        units = Objects.requireNonNull(chosen, "chosen");
    }

    /** The units readings are shown in. */
    public static Units units() {
        return units;
    }

    /** A pressure in kPa, with its unit. */
    public static String pressure(double kpa) {
        return pressureNumber(kpa) + " " + units.pressureSymbol();
    }

    /**
     * A gas's partial pressure in kPa, with its unit: as {@link #pressure}, except that a trace keeps
     * two significant figures ({@link #traceNumber}), so that a little of a gas doesn't read as none.
     */
    public static String partialPressure(double kpa) {
        return traceNumber(units.pressure(kpa)) + " " + units.pressureSymbol();
    }

    /** A pressure in kPa, as a number in the player's unit. */
    public static String pressureNumber(double kpa) {
        return number(units.pressure(kpa));
    }

    /** A temperature in K, with its unit. */
    public static String temperature(double kelvin) {
        return temperatureNumber(kelvin) + " " + units.temperatureSymbol();
    }

    /** A temperature in K, as a number in the player's unit. */
    public static String temperatureNumber(double kelvin) {
        String text = String.format(Locale.ROOT, "%,.1f", units.temperature(kelvin));
        return text.equals("-0.0") ? "0.0" : text;
    }

    /**
     * A share, such as a gas's of a mixture, as a number of percent: 78.1, 0.93 or 0.0050. A trace
     * keeps two significant figures ({@link #traceNumber}).
     */
    public static String percentNumber(double fraction) {
        return traceNumber(fraction * 100);
    }

    /**
     * A reading of a trace: as {@link #number}, except that a trace, above zero and under 0.1 in the
     * unit shown, keeps two significant figures: 0.0020 and 0.00093, where {@link #number} would
     * write 0.002 and 0.001. It has at most four decimals more than the three {@link #number} gives
     * it, so 0.000019 is written so, and a trace that is zero at seven decimals reads 0.000.
     */
    static String traceNumber(double value) {
        if (value > 0 && value < 0.1) {
            BigDecimal exact = BigDecimal.valueOf(value);
            BigDecimal figures = exact.round(TWO_FIGURES);
            if (figures.compareTo(TRACE_BELOW) < 0) {
                // The decimals of the leading figure and one more: 0.002 is written 0.0020.
                int places = 2 - figures.precision() + figures.scale();
                int most = TRACE_DECIMALS + MORE_TRACE_DECIMALS;
                if (places <= most) {
                    return figures.setScale(places, RoundingMode.UNNECESSARY).toPlainString();
                }
                BigDecimal floored = exact.setScale(most, RoundingMode.HALF_UP);
                return floored.signum() == 0 ? decimals(0) : floored.toPlainString();
            }
            return decimals(value);
        }
        return number(value);
    }

    private static String decimals(double value) {
        return String.format(Locale.ROOT, "%." + TRACE_DECIMALS + "f", value);
    }

    /**
     * A value with as many decimals as its size needs: thousands with separators and none, tens with
     * one unless it's whole, down to three for the smallest thresholds, such as 0.005 kPa.
     */
    public static String number(double value) {
        double size = Math.abs(value);
        if (size == 0) {
            return "0";
        }
        if (size >= 1000 || (size >= 10 && Math.abs(value - Math.rint(value)) < 1e-9)) {
            return String.format(Locale.ROOT, "%,.0f", value);
        }
        if (size >= 10) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, size >= 0.1 ? "%.2f" : "%.3f", value);
    }
}
