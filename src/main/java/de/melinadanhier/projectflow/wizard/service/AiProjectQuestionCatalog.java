package de.melinadanhier.projectflow.wizard.service;

import de.melinadanhier.projectflow.plancontainer.project.model.ProjectSubCategory;
import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import de.melinadanhier.projectflow.wizard.model.AiProjectQuestion;
import de.melinadanhier.projectflow.wizard.model.AiQuestionType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Central, UI-independent definition of the additional AI wizard input. */
public final class AiProjectQuestionCatalog {

    private static final int TEXT_LIMIT = 1000;
    private static final Map<ProjectSubCategory, List<AiProjectQuestion>> BY_SUBCATEGORY = buildCatalog();
    private static final List<AiProjectQuestion> GENERIC = questions(
            q("desiredOutcome", "Konkretes Ziel oder gewünschtes Ergebnis"),
            q("currentSituation", "Aktueller Ausgangsstand"),
            q("relevantConditions", "Relevante Rahmenbedingungen"),
            q("specialConstraints", "Besondere Einschränkungen")
    );

    private AiProjectQuestionCatalog() { }

    public static List<AiProjectQuestion> questionsFor(
            TemplateCategory category, ProjectSubCategory subcategory) {
        if (category == null || category == TemplateCategory.OTHER || subcategory == null
                || subcategory.getCategory() != category) {
            return GENERIC;
        }
        return BY_SUBCATEGORY.getOrDefault(subcategory, GENERIC);
    }

    public static Map<String, String> sanitize(
            TemplateCategory category, ProjectSubCategory subcategory, Map<String, String> submitted) {
        if (submitted == null || submitted.isEmpty()) {
            return Map.of();
        }
        Set<String> allowed = questionsFor(category, subcategory).stream()
                .map(AiProjectQuestion::key).collect(Collectors.toSet());
        Map<String, String> result = new LinkedHashMap<>();
        submitted.forEach((key, value) -> {
            if (allowed.contains(key) && value != null && !value.isBlank()) {
                result.put(key, value.trim());
            }
        });
        return Map.copyOf(result);
    }

    public static boolean containsUnknownKey(
            TemplateCategory category, ProjectSubCategory subcategory, Map<String, String> submitted) {
        if (submitted == null) {
            return false;
        }
        Set<String> allowed = questionsFor(category, subcategory).stream()
                .map(AiProjectQuestion::key).collect(Collectors.toSet());
        return submitted.keySet().stream().anyMatch(key -> !allowed.contains(key));
    }

    private static Map<ProjectSubCategory, List<AiProjectQuestion>> buildCatalog() {
        Map<ProjectSubCategory, List<AiProjectQuestion>> map = new LinkedHashMap<>();

        put(map, ProjectSubCategory.PRESENTATION_OR_REPORT,
                "topicOrOutcome", "Thema oder gewünschtes Ergebnis", "targetAudience", "Zielgruppe",
                "contentRequirements", "Bekannte inhaltliche Vorgaben", "desiredDeliverables", "Gewünschte Bestandteile",
                "specialRequirements", "Besondere Anforderungen");
        put(map, List.of(ProjectSubCategory.EXAM_PREPARATION, ProjectSubCategory.LEARNING_PLAN),
                "learningGoal", "Lernziel oder Prüfung", "topics", "Themen oder Stoffumfang",
                "currentKnowledge", "Aktueller Kenntnisstand", "availableStudyTime", "Verfügbare Lernzeit oder Einschränkungen",
                "focusAreas", "Besondere Schwerpunkte");
        put(map, ProjectSubCategory.TERM_PAPER,
                "topicOrQuestion", "Thema oder Fragestellung", "scopeRequirements", "Umfang oder bekannte Vorgaben",
                "currentProgress", "Aktueller Stand", "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.THESIS,
                "researchQuestion", "Thema oder Forschungsfrage", "currentProgress", "Aktueller Stand",
                "methodology", "Methodik oder praktische Bestandteile", "formalRequirements", "Formale oder inhaltliche Vorgaben",
                "specialConstraints", "Besondere Einschränkungen");
        put(map, ProjectSubCategory.STUDY_EVENT,
                "eventGoalFormat", "Ziel und Format", "audienceSize", "Zielgruppe oder erwartete Besucherzahl",
                "venueTechnology", "Vorhandener Ort oder Technik", "specialRequirements", "Besondere Anforderungen");

        put(map, ProjectSubCategory.SOFTWARE_PROJECT,
                "goalAndScope", "Ziel und Funktionsumfang", "requirements", "Vorhandene Anforderungen",
                "technologies", "Festgelegte Technologien", "currentState", "Aktueller Ausgangsstand",
                "technicalConstraints", "Technische Einschränkungen");
        put(map, ProjectSubCategory.WEB_OR_MOBILE_APP,
                "usageScenario", "Zielgruppe oder Nutzungsszenario", "coreFeatures", "Zentrale Funktionen",
                "technicalRequirements", "Technische Vorgaben", "applicationArchitecture", "Frontend, Backend und Datenhaltung",
                "externalInterfaces", "Externe Schnittstellen");
        put(map, ProjectSubCategory.EXTEND_EXISTING_APPLICATION,
                "currentState", "Ausgangszustand", "desiredExtension", "Gewünschte Erweiterung",
                "affectedComponents", "Betroffene Komponenten", "technicalDependencies", "Technische Abhängigkeiten",
                "technicalConstraints", "Besondere Einschränkungen");
        put(map, ProjectSubCategory.WEBSITE,
                "goalAndAudience", "Ziel und Zielgruppe", "pagesAndContent", "Gewünschte Inhalte oder Seiten",
                "currentState", "Vorhandener Ausgangszustand", "designAndTechnicalRequirements", "Technische oder gestalterische Vorgaben");
        put(map, ProjectSubCategory.DATABASE_PROJECT,
                "purposeAndRequirements", "Zweck und Anforderungen", "dataEntities", "Bekannte Daten oder Entitäten",
                "technicalRequirements", "Technische Vorgaben", "currentState", "Aktueller Ausgangsstand");
        put(map, ProjectSubCategory.HARDWARE_OR_RASPBERRY_PI_PROJECT,
                "useCase", "Ziel oder Anwendungsfall", "requiredComponents", "Vorhandene oder benötigte Komponenten",
                "availableHardware", "Vorhandene Hardware", "technicalConstraints", "Technische Einschränkungen");

        put(map, ProjectSubCategory.PRIVATE_CELEBRATION,
                "occasionOutcome", "Anlass oder gewünschtes Ergebnis", "guestCount", "Gästezahl",
                "venue", "Ort oder ob ein Ort feststeht", "budget", "Budget, falls vorhanden",
                "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.WORKSHOP_TRAINING_OR_INFORMATION_EVENT,
                "targetAudience", "Zielgruppe", "learningGoal", "Lern- oder Informationsziel", "topics", "Themen oder Inhalte",
                "visitorCount", "Erwartete Besucherzahl", "venueTechnology", "Raum oder Technik",
                "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.CLUB_OR_COMMUNITY_EVENT,
                "eventGoal", "Veranstaltungsziel", "participants", "Erwartete Besucher oder Beteiligte", "venue", "Ort",
                "permits", "Organisatorische Vorgaben oder Genehmigungen", "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.CONCERT_OR_PERFORMANCE,
                "program", "Art oder Programm", "contributors", "Beteiligte Mitwirkende", "currentProgress", "Vorbereitungsstand",
                "venueTechnology", "Raum oder Technik", "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.FLEA_MARKET_OR_SALES_EVENT,
                "typeAndScope", "Art und Umfang", "goods", "Waren oder Angebot", "venue", "Stand oder Ort",
                "requirements", "Besondere Vorgaben");
        put(map, ProjectSubCategory.FUNDRAISING_EVENT,
                "fundraisingGoal", "Spendenziel", "targetAudience", "Zielgruppe", "campaignFormat", "Aktionsform",
                "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.TOURNAMENT_OR_COMPETITION,
                "competitionType", "Art des Wettbewerbs", "competitionParticipantCount", "Teilnehmerzahl des Wettbewerbs",
                "rules", "Regeln", "venueEquipment", "Ort oder Ausstattung", "specialRequirements", "Besondere Anforderungen");

        put(map, ProjectSubCategory.MOVING,
                "movingSituation", "Ausgangs- und Zielsituation", "householdScope", "Umfang des Haushalts",
                "externalHelp", "Umzugsunternehmen oder externe Hilfe", "transportOptions", "Transportmöglichkeiten",
                "specialConditions", "Besondere Rahmenbedingungen");
        put(map, ProjectSubCategory.RENOVATION_OR_HOME_PROJECT,
                "affectedRooms", "Betroffene Räume oder Fläche", "plannedWork", "Konkret geplante Arbeiten",
                "desiredOutcome", "Gewünschtes Ergebnis", "executionMode", "Eigenleistung oder Handwerksbetriebe",
                "budgetMaterials", "Budget oder Materialien", "specialConstraints", "Besondere Einschränkungen");
        put(map, ProjectSubCategory.DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION,
                "affectedAreas", "Betroffene Bereiche", "scope", "Umfang", "disposalOptions", "Entsorgung, Verkauf oder Spenden",
                "specialConstraints", "Besondere Einschränkungen");
        put(map, ProjectSubCategory.GARDEN_PROJECT,
                "goalArea", "Ziel oder Bereich", "scope", "Umfang", "materialsPlants", "Vorhandene Materialien oder Pflanzen",
                "seasonalConstraints", "Saisonale Einschränkungen");

        put(map, ProjectSubCategory.WRITING_PROJECT,
                "typeGenre", "Art oder Genre", "desiredOutcome", "Gewünschtes Ergebnis", "scope", "Ungefährer Umfang",
                "targetAudience", "Zielgruppe", "currentProgress", "Aktueller Stand", "requirements", "Besondere Vorgaben");
        put(map, ProjectSubCategory.PODCAST,
                "topicAudience", "Thema oder Zielgruppe", "format", "Einzelprojekt oder Serie", "scope", "Umfang",
                "availableTechnology", "Vorhandene Technik", "currentProgress", "Aktueller Stand");
        put(map, ProjectSubCategory.VIDEO_OR_SHORT_FILM_PROJECT,
                "ideaGoal", "Idee oder Ziel", "contributors", "Mitwirkende", "scope", "Umfang",
                "availableTechnology", "Vorhandene Technik", "currentProgress", "Aktueller Stand");
        put(map, ProjectSubCategory.PHOTO_OR_GRAPHIC_PROJECT,
                "goalStyle", "Ziel oder Stil", "subjects", "Inhalte oder Motive", "scope", "Umfang",
                "availableResources", "Vorhandene Technik oder Materialien");
        put(map, ProjectSubCategory.MUSIC_PROJECT,
                "typeAndScope", "Art und Umfang", "desiredOutcome", "Gewünschtes Ergebnis", "contributors", "Mitwirkende",
                "currentProgress", "Aktueller Stand", "availableTechnology", "Vorhandene Technik");
        put(map, ProjectSubCategory.EXHIBITION,
                "goalAudience", "Ziel und Zielgruppe", "exhibits", "Inhalte oder Exponate", "venue", "Ort oder Fläche",
                "currentProgress", "Aktueller Stand", "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.BLOG_OR_SOCIAL_MEDIA_CAMPAIGN,
                "goalAudience", "Ziel und Zielgruppe", "channels", "Geplante Kanäle", "contentScope", "Inhalte und Umfang",
                "currentProgress", "Aktueller Stand", "specialRequirements", "Besondere Vorgaben");
        put(map, ProjectSubCategory.BOARD_GAME_OR_CREATIVE_PROTOTYPE,
                "ideaGoal", "Idee oder Ziel", "targetAudience", "Zielgruppe", "prototypeScope", "Umfang des Prototyps",
                "currentProgress", "Aktueller Stand", "availableMaterials", "Vorhandene Materialien");

        put(map, ProjectSubCategory.JOB_SEARCH_AND_APPLICATION,
                "targetRoles", "Zielrollen", "industryFocus", "Branche oder Schwerpunkte", "availableDocuments", "Vorhandene Unterlagen",
                "applicationGoal", "Gewünschter Umfang oder Bewerbungsziel", "constraints", "Relevante Einschränkungen");
        put(map, ProjectSubCategory.CREATE_PORTFOLIO,
                "goalAudience", "Ziel oder Zielgruppe", "availableContent", "Vorhandene Inhalte",
                "desiredComponents", "Gewünschte Bestandteile", "requirements", "Technische oder gestalterische Vorgaben");
        put(map, ProjectSubCategory.TRAINING_OR_CERTIFICATION,
                "qualificationGoal", "Lern- oder Qualifikationsziel", "currentKnowledge", "Vorhandene Kenntnisse",
                "contentScope", "Inhalte oder Umfang", "examDeadline", "Prüfung oder Deadline");
        put(map, ProjectSubCategory.PROCESS_IMPROVEMENT,
                "currentProcess", "Aktueller Prozess", "problem", "Problem", "targetState", "Gewünschter Zielzustand",
                "conditions", "Bekannte Rahmenbedingungen");
        put(map, ProjectSubCategory.PRODUCT_OR_BUSINESS_IDEA,
                "idea", "Idee", "targetAudience", "Zielgruppe", "projectOutcome", "Gewünschtes Ergebnis dieses Projekts",
                "currentProgress", "Aktueller Stand", "conditions", "Relevante Rahmenbedingungen");
        put(map, ProjectSubCategory.ONBOARDING_PLAN,
                "onboardingGoal", "Einarbeitungsziel", "roleAndTopics", "Rolle und relevante Themen",
                "currentSituation", "Aktueller Ausgangsstand", "availableResources", "Vorhandene Ansprechpersonen oder Unterlagen",
                "conditions", "Relevante Rahmenbedingungen");
        put(map, ProjectSubCategory.PROFESSIONAL_PRESENTATION,
                "topicOutcome", "Thema oder gewünschtes Ergebnis", "targetAudience", "Zielgruppe",
                "contentRequirements", "Inhaltliche Vorgaben", "desiredDeliverables", "Gewünschte Bestandteile",
                "specialRequirements", "Besondere Anforderungen");

        put(map, ProjectSubCategory.FITNESS_OR_RUNNING_GOAL,
                "goal", "Ziel", "currentLevel", "Aktueller Ausgangsstand", "availableTime", "Verfügbare Zeit",
                "userStatedConstraints", "Von dir genannte relevante Einschränkungen");
        put(map, ProjectSubCategory.HABIT_OR_PERSONAL_CHALLENGE,
                "desiredChange", "Gewünschte Veränderung", "currentSituation", "Aktueller Stand",
                "dailyConditions", "Relevante Alltagsbedingungen");
        put(map, ProjectSubCategory.COMPETITION_PREPARATION,
                "competitionGoal", "Wettkampf oder Ziel", "currentLevel", "Aktueller Ausgangsstand",
                "availableTime", "Verfügbare Zeit", "userStatedConstraints", "Von dir genannte relevante Einschränkungen");
        put(map, ProjectSubCategory.NUTRITION_PROJECT,
                "desiredChange", "Gewünschte planungsbezogene Veränderung", "currentSituation", "Aktueller Stand",
                "dailyConditions", "Relevante Alltagsbedingungen", "userStatedConstraints", "Von dir genannte Einschränkungen");
        put(map, ProjectSubCategory.DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE,
                "desiredChange", "Gewünschte Veränderung", "currentSituation", "Aktueller Stand",
                "affectedAreas", "Betroffene Alltagsbereiche", "dailyConditions", "Relevante Alltagsbedingungen");

        put(map, ProjectSubCategory.TRIP_OR_VACATION,
                "destination", "Reiseziel", "budget", "Budget", "transport", "Transport oder Anreise",
                "accommodation", "Unterkunft", "activities", "Gewünschte Aktivitäten oder Schwerpunkte",
                "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.ROAD_TRIP,
                "routeDestinations", "Route oder Reiseziele", "budget", "Budget", "transport", "Fahrzeug oder Transport",
                "accommodation", "Unterkünfte", "activities", "Gewünschte Stopps oder Aktivitäten",
                "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.CAMPING_TRIP,
                "destination", "Reiseziel", "budget", "Budget", "transport", "Anreise",
                "campingEquipment", "Vorhandene Campingausrüstung", "activities", "Aktivitäten",
                "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.FESTIVAL_OR_CONCERT_TRIP,
                "eventDestination", "Festival, Konzert oder Reiseziel", "budget", "Budget", "transport", "Anreise",
                "accommodation", "Unterkunft", "tickets", "Ticket- oder Buchungsstand", "specialRequirements", "Besondere Anforderungen");
        put(map, ProjectSubCategory.BICYCLE_TOUR,
                "routeDestinations", "Route oder Reiseziele", "scope", "Strecke oder Umfang", "availableEquipment", "Vorhandene Ausrüstung",
                "accommodation", "Unterkunft, falls vorgesehen", "specialRequirements", "Besondere Anforderungen");
        return Map.copyOf(map);
    }

    private static AiProjectQuestion q(String key, String label) {
        return new AiProjectQuestion(key, label, null, AiQuestionType.TEXTAREA, false, TEXT_LIMIT);
    }

    private static List<AiProjectQuestion> questions(AiProjectQuestion... questions) {
        return List.of(questions);
    }

    private static void put(Map<ProjectSubCategory, List<AiProjectQuestion>> map,
                            ProjectSubCategory category, String... keyLabels) {
        put(map, List.of(category), keyLabels);
    }

    private static void put(Map<ProjectSubCategory, List<AiProjectQuestion>> map,
                            List<ProjectSubCategory> categories, String... keyLabels) {
        AiProjectQuestion[] questions = new AiProjectQuestion[keyLabels.length / 2];
        for (int i = 0; i < keyLabels.length; i += 2) {
            questions[i / 2] = q(keyLabels[i], keyLabels[i + 1]);
        }
        List<AiProjectQuestion> definition = questions(questions);
        categories.forEach(category -> map.put(category, definition));
    }
}
