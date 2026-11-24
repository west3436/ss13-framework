package io.github.west3436.ss13.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.ss13.testkit.PlayerTextScan.Found;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** The scan finds text and keys in each way {@link PlayerTextSamples} gets them to a player. */
class PlayerTextScanTest {
    /** A unit's symbol reads the same in any language's text around it, as a mod's own test allows. */
    private static final Set<String> UNIT_SYMBOLS = Set.of("kPa", "K");

    @Test
    void the_scan_finds_text_however_it_gets_there() throws IOException {
        Set<String> expected = Set.of(
                "Direct",
                "Concatenated ",
                "Local",
                "Either",
                "Or",
                "Constant",
                "Array",
                "Passed",
                "Returned",
                "Enum field",
                "Record field",
                "Appended",
                "Drawn",
                "Floating",
                "Argument",
                "Client argument",
                "Fallback",
                "Format ",
                "Argument of a format",
                "Captured",
                "Command error",
                "Substring");
        Set<String> found = hardCoded(scan(PlayerTextSamples.FLAGGED)).stream()
                .map(Found::text)
                .collect(Collectors.toSet());
        assertEquals(new TreeSet<>(expected), new TreeSet<>(found));
    }

    @Test
    void the_scan_allows_symbols_numbers_units_and_composed_values() throws IOException {
        assertEquals(Set.of(), hardCoded(scan(PlayerTextSamples.ALLOWED)));
    }

    @Test
    void the_scan_finds_keys_through_helpers() throws IOException {
        Set<String> keys =
                scan(PlayerTextSamples.KEYS).keys().stream().map(Found::text).collect(Collectors.toSet());
        assertEquals(
                new TreeSet<>(Set.of(
                        "sample.plain",
                        "sample.client",
                        "sample.either",
                        "sample.or",
                        "sample.prefix.helper",
                        "sample.prefix.enum")),
                new TreeSet<>(keys));
    }

    @Test
    void the_scan_says_where_it_found_each_string() throws IOException {
        Set<String> places = scan(PlayerTextSamples.FLAGGED).withLetters().stream()
                .filter(found -> found.text().equals("Direct"))
                .map(Found::where)
                .collect(Collectors.toSet());
        assertEquals(1, places.size(), "places of \"Direct\": " + places);
        String where = places.iterator().next();
        assertTrue(
                where.matches("io/github/west3436/ss13/testkit/PlayerTextSamples\\.java:\\d+"),
                "\"Direct\" was found at " + where);
    }

    @Test
    void a_directory_of_classes_scans_like_the_classes() {
        Path classes = Path.of(System.getProperty("ss13.testkit.testClasses"));
        Set<String> found = PlayerTextScan.of(classes, notFollowed()).withLetters().stream()
                .map(Found::text)
                .collect(Collectors.toSet());
        assertTrue(found.contains("Direct"), "the directory's scan found " + found);
    }

    private static Set<Found> hardCoded(PlayerTextScan scan) {
        Set<Found> out = new TreeSet<>();
        for (Found found : scan.withLetters()) {
            if (!UNIT_SYMBOLS.contains(found.text().strip())) {
                out.add(found);
            }
        }
        return out;
    }

    private static Set<String> notFollowed() {
        return Set.of(PlayerTextSamples.Diagnostic.class.getName().replace('.', '/') + ".toString");
    }

    private static PlayerTextScan scan(List<Class<?>> samples) throws IOException {
        List<byte[]> files = new ArrayList<>();
        for (Class<?> sample : samples) {
            String file = sample.getName().substring(sample.getPackageName().length() + 1) + ".class";
            try (InputStream in = sample.getResourceAsStream(file)) {
                files.add(in.readAllBytes());
            }
        }
        return new PlayerTextScan(files, notFollowed());
    }
}
