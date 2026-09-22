package de.melinadanhier.projectflow.ai.model.precheck;

import de.melinadanhier.projectflow.wizard.service.AiProjectQuestionCatalog;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Zentrale Auflösung von internen Feldschlüsseln (aus Backend, KI-Schema und Wizard)
 * zu verständlichen deutschen Bezeichnungen für die Pre-Check-Darstellung.
 */
public final class PreCheckFieldLabelResolver {

    public static final String DEFAULT_FALLBACK_LABEL = "Weitere Angabe";
    private static final String PROJECT_SPECIFIC_PREFIX = "projectSpecificAnswers.";
    private static final Pattern USER_CORRECTION_PATTERN = Pattern.compile("^userPreCheckCorrection\\d+$");

    private static final Map<String, String> KNOWN_FIELD_LABELS = Map.ofEntries(
            // Allgemeine Wizard- und Pre-Check-Felder
            Map.entry("workingTime", "Verfügbare Arbeitszeit"),
            Map.entry("availableWorkingTime", "Verfügbare Arbeitszeit"),
            Map.entry("availableTime", "Verfügbare Arbeitszeit"),
            Map.entry("startDate", "Startdatum"),
            Map.entry("endDate", "Enddatum"),
            Map.entry("durationDays", "Dauer"),
            Map.entry("duration", "Dauer"),
            Map.entry("projectGoal", "Projektziel"),
            Map.entry("constraints", "Rahmenbedingungen"),
            Map.entry("additionalInformation", "Weitere Hinweise"),
            Map.entry("category", "Kategorie"),
            Map.entry("subcategory", "Unterkategorie"),
            Map.entry("collaborationMode", "Projektart"),
            Map.entry("title", "Titel"),
            Map.entry("description", "Beschreibung"),
            Map.entry("rejectedElements", "Abgelehnte Elemente"),

            // Prägnante Kurzbezeichnungen für häufige projektspezifische Frage-Schlüssel
            Map.entry("scope", "Umfang"),
            Map.entry("topic", "Thema"),
            Map.entry("targetAudience", "Zielgruppe"),
            Map.entry("contentRequirements", "Inhaltliche Vorgaben"),
            Map.entry("desiredDeliverables", "Gewünschte Ergebnisse"),
            Map.entry("examSubject", "Prüfungsfach"),
            Map.entry("examTopics", "Prüfungsthemen"),
            Map.entry("currentKnowledge", "Bisheriger Wissensstand"),
            Map.entry("examDate", "Prüfungsdatum"),
            Map.entry("learningGoal", "Lernziel"),
            Map.entry("learningTopics", "Lerninhalte"),
            Map.entry("focusAreas", "Schwerpunkte"),
            Map.entry("paperTopic", "Thema der Arbeit"),
            Map.entry("paperRequirements", "Vorgaben zur Arbeit"),
            Map.entry("currentProgress", "Bisheriger Arbeitsstand"),
            Map.entry("researchQuestion", "Forschungsfrage"),
            Map.entry("methodology", "Methodik & Vorgehen"),
            Map.entry("formalRequirements", "Formale Vorgaben"),
            Map.entry("educationGoal", "Bildungsziel"),
            Map.entry("technicalExperience", "Technische Erfahrung"),
            Map.entry("goalAndScope", "Funktionen & Umfang"),
            Map.entry("requirements", "Anforderungen"),
            Map.entry("technologies", "Technologien"),
            Map.entry("currentState", "Aktueller Stand"),
            Map.entry("budget", "Budget"),
            Map.entry("venue", "Veranstaltungsort"),
            Map.entry("movingSituation", "Umzugssituation"),
            Map.entry("householdScope", "Umfang des Haushalts"),
            Map.entry("transportAndHelp", "Transport & Helfer"),
            Map.entry("declutterOrRenovate", "Vorarbeiten & Renovieren")
    );

    private PreCheckFieldLabelResolver() {
    }

    /**
     * Löst einen internen Feldschlüssel zu einem verständlichen deutschen Anzeigelabel auf.
     *
     * @param fieldKey der interne Schlüssel (z. B. "workingTime", "projectSpecificAnswers.scope")
     * @return das verständliche deutsche Label oder "Weitere Angabe" als Fallback
     */
    public static String resolveLabel(String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            return DEFAULT_FALLBACK_LABEL;
        }

        String key = fieldKey.trim();
        if (key.startsWith(PROJECT_SPECIFIC_PREFIX)) {
            key = key.substring(PROJECT_SPECIFIC_PREFIX.length());
        }

        // 1. Feste Zuordnung bekannter Standardfelder und prägnanter Frage-Schlüssel
        String label = KNOWN_FIELD_LABELS.get(key);
        if (label != null) {
            return label;
        }

        // 2. Musterbasierte Erkennung von Nutzer-Korrekturen
        if (USER_CORRECTION_PATTERN.matcher(key).matches()) {
            return "Ergänzung zur Planung";
        }

        // 3. Dynamische Abfrage des bestehenden YAML-Fragekatalogs
        try {
            var catalogLabel = AiProjectQuestionCatalog.findQuestionLabel(key);
            if (catalogLabel.isPresent() && !catalogLabel.get().isBlank()) {
                return catalogLabel.get();
            }
        } catch (Exception ignored) {
            // Sicherer Fallback bei Katalogproblemen
        }

        // 4. Sicherer deutscher Fallback für alle unbekannten oder zukünftigen Schlüssel
        return DEFAULT_FALLBACK_LABEL;
    }
}
