package de.melinadanhier.projectflow.plancontainer.project.model;

import de.melinadanhier.projectflow.plancontainer.template.model.TemplateCategory;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectSubCategory {
    PRESENTATION_OR_REPORT("Präsentation oder Referat", TemplateCategory.EDUCATION),
    EXAM_PREPARATION("Prüfungslernplan", TemplateCategory.EDUCATION),
    LEARNING_PLAN("Lernplan", TemplateCategory.EDUCATION),
    TERM_PAPER("Hausarbeit oder Seminararbeit", TemplateCategory.EDUCATION),
    THESIS("Abschlussarbeit", TemplateCategory.EDUCATION),
    OTHER_EDUCATION("Sonstige Bildung", TemplateCategory.EDUCATION),
    SOFTWARE_PROJECT("Softwareprojekt", TemplateCategory.SOFTWARE_TECHNOLOGY),
    WEB_OR_MOBILE_APP("Webanwendung oder Mobile App", TemplateCategory.SOFTWARE_TECHNOLOGY),
    EXTEND_EXISTING_APPLICATION("Bestehende Anwendung erweitern", TemplateCategory.SOFTWARE_TECHNOLOGY),
    WEBSITE("Website", TemplateCategory.SOFTWARE_TECHNOLOGY),
    DATABASE_PROJECT("Datenbankprojekt", TemplateCategory.SOFTWARE_TECHNOLOGY),
    HARDWARE_OR_RASPBERRY_PI_PROJECT("Hardware- oder Raspberry-Pi-Projekt", TemplateCategory.SOFTWARE_TECHNOLOGY),
    OTHER_SOFTWARE_AND_TECHNOLOGY("Sonstige Software und Technik", TemplateCategory.SOFTWARE_TECHNOLOGY),
    PRIVATE_CELEBRATION("Private Feier", TemplateCategory.EVENT),
    WORKSHOP_TRAINING_OR_INFORMATION_EVENT("Workshop, Schulung oder Informationsveranstaltung", TemplateCategory.EVENT),
    CLUB_OR_COMMUNITY_EVENT("Vereins- oder Gemeinschaftsveranstaltung", TemplateCategory.EVENT),
    CONCERT_OR_PERFORMANCE("Konzert oder Aufführung", TemplateCategory.EVENT),
    FLEA_MARKET_OR_SALES_EVENT("Flohmarkt oder Verkaufsaktion", TemplateCategory.EVENT),
    FUNDRAISING_EVENT("Spendenaktion", TemplateCategory.EVENT),
    TOURNAMENT_OR_COMPETITION("Turnier oder Wettbewerb", TemplateCategory.EVENT),
    STUDY_EVENT("Studienveranstaltung", TemplateCategory.EVENT),
    OTHER_EVENT("Sonstige Veranstaltung", TemplateCategory.EVENT),
    MOVING("Umzug", TemplateCategory.HOME),
    RENOVATION_OR_HOME_PROJECT("Renovierung oder Wohnprojekt", TemplateCategory.HOME),
    DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION("Entrümpelung oder größere Haushaltsorganisation", TemplateCategory.HOME),
    GARDEN_PROJECT("Gartenprojekt", TemplateCategory.HOME),
    OTHER_HOME("Sonstige Zuhause", TemplateCategory.HOME),
    WRITING_PROJECT("Buch, Geschichte oder anderes Schreibprojekt", TemplateCategory.CREATIVE),
    PODCAST("Podcast", TemplateCategory.CREATIVE),
    VIDEO_OR_SHORT_FILM_PROJECT("Video- oder Kurzfilmprojekt", TemplateCategory.CREATIVE),
    PHOTO_OR_GRAPHIC_PROJECT("Foto- oder Grafikprojekt", TemplateCategory.CREATIVE),
    MUSIC_PROJECT("Musikprojekt", TemplateCategory.CREATIVE),
    EXHIBITION("Ausstellung", TemplateCategory.CREATIVE),
    BLOG_OR_SOCIAL_MEDIA_CAMPAIGN("Blog oder Social-Media-Kampagne", TemplateCategory.CREATIVE),
    BOARD_GAME_OR_CREATIVE_PROTOTYPE("Brettspiel oder kreativer Prototyp", TemplateCategory.CREATIVE),
    OTHER_CREATIVE_PROJECT("Sonstige kreative Projekte", TemplateCategory.CREATIVE),
    JOB_SEARCH_AND_APPLICATION("Jobsuche und Bewerbung", TemplateCategory.CAREER),
    CREATE_PORTFOLIO("Portfolio erstellen", TemplateCategory.CAREER),
    TRAINING_OR_CERTIFICATION("Eigene Weiterbildung oder Zertifizierung", TemplateCategory.CAREER),
    ONBOARDING_PLAN("Einarbeitungsplan", TemplateCategory.CAREER),
    PROFESSIONAL_PRESENTATION("Berufliche Präsentation", TemplateCategory.CAREER),
    PROCESS_IMPROVEMENT("Prozessverbesserung", TemplateCategory.CAREER),
    PRODUCT_OR_BUSINESS_IDEA("Produkt- oder Geschäftsidee", TemplateCategory.CAREER),
    OTHER_CAREER("Sonstige Beruf und Karriere", TemplateCategory.CAREER),
    FITNESS_OR_RUNNING_GOAL("Fitness- oder Laufziel", TemplateCategory.HEALTH_PERSONAL_DEVELOPMENT),
    COMPETITION_PREPARATION("Wettkampfvorbereitung", TemplateCategory.HEALTH_PERSONAL_DEVELOPMENT),
    NUTRITION_PROJECT("Ernährungsprojekt", TemplateCategory.HEALTH_PERSONAL_DEVELOPMENT),
    HABIT_OR_PERSONAL_CHALLENGE("Gewohnheits- oder persönliche Challenge", TemplateCategory.HEALTH_PERSONAL_DEVELOPMENT),
    DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE("Digital Detox oder Alltagsveränderung", TemplateCategory.HEALTH_PERSONAL_DEVELOPMENT),
    OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT("Sonstige Gesundheit und persönliche Entwicklung", TemplateCategory.HEALTH_PERSONAL_DEVELOPMENT),
    TRIP_OR_VACATION("Reise oder Urlaub", TemplateCategory.TRAVEL),
    ROAD_TRIP("Roadtrip", TemplateCategory.TRAVEL),
    FESTIVAL_OR_CONCERT_TRIP("Festival- oder Konzertbesuch", TemplateCategory.TRAVEL),
    CAMPING_TRIP("Campingreise", TemplateCategory.TRAVEL),
    BICYCLE_TOUR("Fahrradtour", TemplateCategory.TRAVEL),
    OTHER_TRAVEL("Sonstige Reisen", TemplateCategory.TRAVEL);

    private final String label;
    private final TemplateCategory category;

    /** Declaration order is the stable business order used by every dropdown. */
    public static List<ProjectSubCategory> forCategory(TemplateCategory category) {
        return Arrays.stream(values()).filter(value -> value.category == category).toList();
    }

    public static boolean isValidFor(TemplateCategory category, ProjectSubCategory subcategory) {
        return subcategory == null || subcategory.category == category;
    }
}
