package de.melinadanhier.projectflow.plancontainer.project.model.classification;

import de.melinadanhier.projectflow.plancontainer.template.model.ProjectCategory;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectSubCategory {
    PRESENTATION_OR_REPORT("Präsentation oder Referat", ProjectCategory.EDUCATION),
    EXAM_PREPARATION("Prüfungslernplan", ProjectCategory.EDUCATION),
    LEARNING_PLAN("Lernplan", ProjectCategory.EDUCATION),
    TERM_PAPER("Hausarbeit oder Seminararbeit", ProjectCategory.EDUCATION),
    THESIS("Abschlussarbeit", ProjectCategory.EDUCATION),
    OTHER_EDUCATION("Sonstige Bildung", ProjectCategory.EDUCATION),
    SOFTWARE_PROJECT("Softwareprojekt", ProjectCategory.SOFTWARE_TECHNOLOGY),
    WEB_OR_MOBILE_APP("Webanwendung oder Mobile App", ProjectCategory.SOFTWARE_TECHNOLOGY),
    EXTEND_EXISTING_APPLICATION("Bestehende Anwendung erweitern", ProjectCategory.SOFTWARE_TECHNOLOGY),
    WEBSITE("Website", ProjectCategory.SOFTWARE_TECHNOLOGY),
    DATABASE_PROJECT("Datenbankprojekt", ProjectCategory.SOFTWARE_TECHNOLOGY),
    HARDWARE_OR_RASPBERRY_PI_PROJECT("Hardware- oder Raspberry-Pi-Projekt", ProjectCategory.SOFTWARE_TECHNOLOGY),
    OTHER_SOFTWARE_AND_TECHNOLOGY("Sonstige Software und Technik", ProjectCategory.SOFTWARE_TECHNOLOGY),
    PRIVATE_CELEBRATION("Private Feier", ProjectCategory.EVENT),
    WORKSHOP_TRAINING_OR_INFORMATION_EVENT("Workshop, Schulung oder Informationsveranstaltung", ProjectCategory.EVENT),
    CLUB_OR_COMMUNITY_EVENT("Vereins- oder Gemeinschaftsveranstaltung", ProjectCategory.EVENT),
    CONCERT_OR_PERFORMANCE("Konzert oder Aufführung", ProjectCategory.EVENT),
    FLEA_MARKET_OR_SALES_EVENT("Flohmarkt oder Verkaufsaktion", ProjectCategory.EVENT),
    FUNDRAISING_EVENT("Spendenaktion", ProjectCategory.EVENT),
    TOURNAMENT_OR_COMPETITION("Turnier oder Wettbewerb", ProjectCategory.EVENT),
    STUDY_EVENT("Studienveranstaltung", ProjectCategory.EVENT),
    OTHER_EVENT("Sonstige Veranstaltung", ProjectCategory.EVENT),
    MOVING("Umzug", ProjectCategory.HOME),
    RENOVATION_OR_HOME_PROJECT("Renovierung oder Wohnprojekt", ProjectCategory.HOME),
    DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION("Entrümpelung oder größere Haushaltsorganisation", ProjectCategory.HOME),
    GARDEN_PROJECT("Gartenprojekt", ProjectCategory.HOME),
    OTHER_HOME("Sonstige Zuhause", ProjectCategory.HOME),
    WRITING_PROJECT("Buch, Geschichte oder anderes Schreibprojekt", ProjectCategory.CREATIVE),
    PODCAST("Podcast", ProjectCategory.CREATIVE),
    VIDEO_OR_SHORT_FILM_PROJECT("Video- oder Kurzfilmprojekt", ProjectCategory.CREATIVE),
    PHOTO_OR_GRAPHIC_PROJECT("Foto- oder Grafikprojekt", ProjectCategory.CREATIVE),
    MUSIC_PROJECT("Musikprojekt", ProjectCategory.CREATIVE),
    EXHIBITION("Ausstellung", ProjectCategory.CREATIVE),
    BLOG_OR_SOCIAL_MEDIA_CAMPAIGN("Blog oder Social-Media-Kampagne", ProjectCategory.CREATIVE),
    BOARD_GAME_OR_CREATIVE_PROTOTYPE("Brettspiel oder kreativer Prototyp", ProjectCategory.CREATIVE),
    OTHER_CREATIVE_PROJECT("Sonstige kreative Projekte", ProjectCategory.CREATIVE),
    JOB_SEARCH_AND_APPLICATION("Jobsuche und Bewerbung", ProjectCategory.CAREER),
    CREATE_PORTFOLIO("Portfolio erstellen", ProjectCategory.CAREER),
    TRAINING_OR_CERTIFICATION("Eigene Weiterbildung oder Zertifizierung", ProjectCategory.CAREER),
    ONBOARDING_PLAN("Einarbeitungsplan", ProjectCategory.CAREER),
    PROFESSIONAL_PRESENTATION("Berufliche Präsentation", ProjectCategory.CAREER),
    PROCESS_IMPROVEMENT("Prozessverbesserung", ProjectCategory.CAREER),
    PRODUCT_OR_BUSINESS_IDEA("Produkt- oder Geschäftsidee", ProjectCategory.CAREER),
    OTHER_CAREER("Sonstige Beruf und Karriere", ProjectCategory.CAREER),
    FITNESS_OR_RUNNING_GOAL("Fitness- oder Laufziel", ProjectCategory.HEALTH_PERSONAL_DEVELOPMENT),
    COMPETITION_PREPARATION("Wettkampfvorbereitung", ProjectCategory.HEALTH_PERSONAL_DEVELOPMENT),
    NUTRITION_PROJECT("Ernährungsprojekt", ProjectCategory.HEALTH_PERSONAL_DEVELOPMENT),
    HABIT_OR_PERSONAL_CHALLENGE("Gewohnheits- oder persönliche Challenge", ProjectCategory.HEALTH_PERSONAL_DEVELOPMENT),
    DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE("Digital Detox oder Alltagsveränderung", ProjectCategory.HEALTH_PERSONAL_DEVELOPMENT),
    OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT("Sonstige Gesundheit und persönliche Entwicklung", ProjectCategory.HEALTH_PERSONAL_DEVELOPMENT),
    TRIP_OR_VACATION("Reise oder Urlaub", ProjectCategory.TRAVEL),
    ROAD_TRIP("Roadtrip", ProjectCategory.TRAVEL),
    FESTIVAL_OR_CONCERT_TRIP("Festival- oder Konzertbesuch", ProjectCategory.TRAVEL),
    CAMPING_TRIP("Campingreise", ProjectCategory.TRAVEL),
    BICYCLE_TOUR("Fahrradtour", ProjectCategory.TRAVEL),
    OTHER_TRAVEL("Sonstige Reisen", ProjectCategory.TRAVEL);

    private final String label;
    private final ProjectCategory category;

    /** Declaration order is the stable business order used by every dropdown. */
    public static List<ProjectSubCategory> forCategory(ProjectCategory category) {
        return Arrays.stream(values()).filter(value -> value.category == category).toList();
    }

    public static boolean isValidFor(ProjectCategory category, ProjectSubCategory subcategory) {
        return subcategory == null || subcategory.category == category;
    }

    public boolean isOther() {
        return name().startsWith("OTHER_");
    }
}
