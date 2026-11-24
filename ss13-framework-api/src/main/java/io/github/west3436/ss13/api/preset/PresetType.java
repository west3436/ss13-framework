package io.github.west3436.ss13.api.preset;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * How a {@link PresetOption}'s values are checked, stored in the preset file and shown in
 * commands. Use {@link #BOOLEAN}, {@link #ofEnum} or {@link #number}, or implement it for another
 * type.
 *
 * @param <T> the type of the values
 */
public interface PresetType<T> {
    /**
     * A value from the preset file or a command argument, or empty if it isn't one. Accepts the
     * stored form and its text, such as {@code true} and {@code "true"}.
     *
     * @param raw what the file or the command gave; may be null
     * @return the value, or empty
     */
    Optional<T> parse(@Nullable Object raw);

    /**
     * The value as the preset file stores it: a {@link Boolean}, a {@link Number} or a {@link
     * String}.
     *
     * @param value the value
     * @return the stored form
     */
    Object write(T value);

    /**
     * The value as commands show it and accept it.
     *
     * @param value the value
     * @return its text, such as {@code "0.5"}
     */
    String format(T value);

    /**
     * The values the file's comment says are valid, such as {@code "true or false"}.
     *
     * @return the description
     */
    String describe();

    /**
     * Values to suggest while a command is typed.
     *
     * @return the suggestions, possibly none
     */
    List<String> suggestions();

    /** {@code true} or {@code false}. */
    PresetType<Boolean> BOOLEAN = new PresetType<>() {
        @Override
        public Optional<Boolean> parse(@Nullable Object raw) {
            if (raw instanceof Boolean b) {
                return Optional.of(b);
            }
            if (raw instanceof String s) {
                String t = s.trim();
                if (t.equalsIgnoreCase("true")) {
                    return Optional.of(true);
                }
                if (t.equalsIgnoreCase("false")) {
                    return Optional.of(false);
                }
            }
            return Optional.empty();
        }

        @Override
        public Object write(Boolean value) {
            return value;
        }

        @Override
        public String format(Boolean value) {
            return value.toString();
        }

        @Override
        public String describe() {
            return "true or false";
        }

        @Override
        public List<String> suggestions() {
            return List.of("true", "false");
        }
    };

    /**
     * One of an enum's constants, stored by name and read ignoring case.
     *
     * @param type the enum class
     * @param <E> the enum
     * @return the type
     */
    static <E extends Enum<E>> PresetType<E> ofEnum(Class<E> type) {
        Objects.requireNonNull(type, "type");
        E[] constants = type.getEnumConstants();
        List<String> names = Arrays.stream(constants).map(Enum::name).toList();
        return new PresetType<>() {
            @Override
            public Optional<E> parse(@Nullable Object raw) {
                if (raw instanceof String s) {
                    String t = s.trim();
                    for (E constant : constants) {
                        if (constant.name().equalsIgnoreCase(t)) {
                            return Optional.of(constant);
                        }
                    }
                }
                return type.isInstance(raw) ? Optional.of(type.cast(raw)) : Optional.empty();
            }

            @Override
            public Object write(E value) {
                return value.name();
            }

            @Override
            public String format(E value) {
                return value.name();
            }

            @Override
            public String describe() {
                if (names.size() == 1) {
                    return names.getFirst();
                }
                return String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
            }

            @Override
            public List<String> suggestions() {
                return names;
            }
        };
    }

    /**
     * A finite number from {@code min} to {@code max}.
     *
     * @param min the smallest value
     * @param max the largest value
     * @param unit the unit the file's comment names after the range, such as {@code "mol/h"}, or
     *     an empty string
     * @return the type
     * @throws IllegalArgumentException if {@code min} is more than {@code max} or either isn't
     *     finite
     */
    static PresetType<Double> number(double min, double max, String unit) {
        Objects.requireNonNull(unit, "unit");
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("not a range: " + min + " to " + max);
        }
        return new PresetType<>() {
            @Override
            public Optional<Double> parse(@Nullable Object raw) {
                double v;
                if (raw instanceof Number n) {
                    v = n.doubleValue();
                } else if (raw instanceof String s) {
                    try {
                        v = Double.parseDouble(s.trim());
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                } else {
                    return Optional.empty();
                }
                return Double.isFinite(v) && v >= min && v <= max ? Optional.of(v) : Optional.empty();
            }

            @Override
            public Object write(Double value) {
                return value;
            }

            @Override
            public String format(Double value) {
                return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }

            @Override
            public String describe() {
                String range = "a number from " + format(min) + " to " + format(max);
                return unit.isEmpty() ? range : range + " " + unit;
            }

            @Override
            public List<String> suggestions() {
                return List.of();
            }
        };
    }
}
