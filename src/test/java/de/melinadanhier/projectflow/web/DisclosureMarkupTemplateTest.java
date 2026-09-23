package de.melinadanhier.projectflow.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DisclosureMarkupTemplateTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern DETAILS_TAG = Pattern.compile("<details\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROPDOWN_MENU = Pattern.compile(
            "<div\\b(?=[^>]*class=\\\"[^\\\"]*pf-dropdown__menu[^\\\"]*\\\")[^>]*>",
            Pattern.CASE_INSENSITIVE);

    @Test
    void detailsIsReservedForGenuineContentDisclosures() throws IOException {
        try (var paths = Files.walk(TEMPLATES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".html")).toList()) {
                String html = Files.readString(path);
                var matcher = DETAILS_TAG.matcher(html);
                while (matcher.find()) {
                    assertThat(matcher.group())
                            .as("details usage in %s", path)
                            .satisfiesAnyOf(
                                    tag -> assertThat(tag).contains("pf-study-task__details"),
                                    tag -> assertThat(tag).contains("pf-study-info-details"));
                }
            }
        }
    }

    @Test
    void dropdownMenusAreInitiallyHiddenUntilTheirButtonOpensThem() throws IOException {
        try (var paths = Files.walk(TEMPLATES)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".html")).toList()) {
                String html = Files.readString(path);
                var matcher = DROPDOWN_MENU.matcher(html);
                while (matcher.find()) {
                    assertThat(matcher.group())
                            .as("dropdown menu in %s", path)
                            .contains("hidden");
                }
            }
        }
    }
}
