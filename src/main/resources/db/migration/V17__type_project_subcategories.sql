-- Split the formerly overloaded project_type. Known labels (including labels used
-- in old fixtures) and enum names are matched case-insensitively within their category.
-- Unknown / mismatched nonempty values are NOT guessed: retain the exact original
-- in this audit table and leave subcategory NULL. Review these rows manually.
-- OTHER retains its free project-type description and never gets a subcategory.
CREATE TABLE project_subcategory_migration_issues (
    source_table VARCHAR(100) NOT NULL,
    source_id UUID NOT NULL,
    category VARCHAR(50),
    legacy_value TEXT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source_table, source_id)
);

-- Frozen migration mapping; runtime dropdowns use ProjectSubCategory exclusively.
CREATE TEMPORARY TABLE subcategory_v17_mapping (
    legacy_value VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    enum_value VARCHAR(100) NOT NULL
) ON COMMIT DROP;
INSERT INTO subcategory_v17_mapping (legacy_value, category, enum_value) VALUES
    ('PRESENTATION_OR_REPORT', 'EDUCATION', 'PRESENTATION_OR_REPORT'),
    ('Präsentation oder Referat', 'EDUCATION', 'PRESENTATION_OR_REPORT'),
    ('Präsentation', 'EDUCATION', 'PRESENTATION_OR_REPORT'),
    ('Referat', 'EDUCATION', 'PRESENTATION_OR_REPORT'),
    ('Gruppenpräsentation', 'EDUCATION', 'PRESENTATION_OR_REPORT'),
    ('EXAM_PREPARATION', 'EDUCATION', 'EXAM_PREPARATION'),
    ('Prüfungslernplan', 'EDUCATION', 'EXAM_PREPARATION'),
    ('LEARNING_PLAN', 'EDUCATION', 'LEARNING_PLAN'),
    ('Lernplan', 'EDUCATION', 'LEARNING_PLAN'),
    ('TERM_PAPER', 'EDUCATION', 'TERM_PAPER'),
    ('Hausarbeit oder Seminararbeit', 'EDUCATION', 'TERM_PAPER'),
    ('Hausarbeit', 'EDUCATION', 'TERM_PAPER'),
    ('Seminararbeit', 'EDUCATION', 'TERM_PAPER'),
    ('THESIS', 'EDUCATION', 'THESIS'),
    ('Abschlussarbeit', 'EDUCATION', 'THESIS'),
    ('Bachelorarbeit', 'EDUCATION', 'THESIS'),
    ('Masterarbeit', 'EDUCATION', 'THESIS'),
    ('OTHER_EDUCATION', 'EDUCATION', 'OTHER_EDUCATION'),
    ('Sonstige Bildung', 'EDUCATION', 'OTHER_EDUCATION'),
    ('SOFTWARE_PROJECT', 'SOFTWARE_TECHNOLOGY', 'SOFTWARE_PROJECT'),
    ('Softwareprojekt', 'SOFTWARE_TECHNOLOGY', 'SOFTWARE_PROJECT'),
    ('WEB_OR_MOBILE_APP', 'SOFTWARE_TECHNOLOGY', 'WEB_OR_MOBILE_APP'),
    ('Webanwendung oder Mobile App', 'SOFTWARE_TECHNOLOGY', 'WEB_OR_MOBILE_APP'),
    ('EXTEND_EXISTING_APPLICATION', 'SOFTWARE_TECHNOLOGY', 'EXTEND_EXISTING_APPLICATION'),
    ('Bestehende Anwendung erweitern', 'SOFTWARE_TECHNOLOGY', 'EXTEND_EXISTING_APPLICATION'),
    ('WEBSITE', 'SOFTWARE_TECHNOLOGY', 'WEBSITE'),
    ('Website', 'SOFTWARE_TECHNOLOGY', 'WEBSITE'),
    ('DATABASE_PROJECT', 'SOFTWARE_TECHNOLOGY', 'DATABASE_PROJECT'),
    ('Datenbankprojekt', 'SOFTWARE_TECHNOLOGY', 'DATABASE_PROJECT'),
    ('HARDWARE_OR_RASPBERRY_PI_PROJECT', 'SOFTWARE_TECHNOLOGY', 'HARDWARE_OR_RASPBERRY_PI_PROJECT'),
    ('Hardware- oder Raspberry-Pi-Projekt', 'SOFTWARE_TECHNOLOGY', 'HARDWARE_OR_RASPBERRY_PI_PROJECT'),
    ('OTHER_SOFTWARE_AND_TECHNOLOGY', 'SOFTWARE_TECHNOLOGY', 'OTHER_SOFTWARE_AND_TECHNOLOGY'),
    ('Sonstige Software und Technik', 'SOFTWARE_TECHNOLOGY', 'OTHER_SOFTWARE_AND_TECHNOLOGY'),
    ('PRIVATE_CELEBRATION', 'EVENT', 'PRIVATE_CELEBRATION'),
    ('Private Feier', 'EVENT', 'PRIVATE_CELEBRATION'),
    ('WORKSHOP_TRAINING_OR_INFORMATION_EVENT', 'EVENT', 'WORKSHOP_TRAINING_OR_INFORMATION_EVENT'),
    ('Workshop, Schulung oder Informationsveranstaltung', 'EVENT', 'WORKSHOP_TRAINING_OR_INFORMATION_EVENT'),
    ('CLUB_OR_COMMUNITY_EVENT', 'EVENT', 'CLUB_OR_COMMUNITY_EVENT'),
    ('Vereins- oder Gemeinschaftsveranstaltung', 'EVENT', 'CLUB_OR_COMMUNITY_EVENT'),
    ('CONCERT_OR_PERFORMANCE', 'EVENT', 'CONCERT_OR_PERFORMANCE'),
    ('Konzert oder Aufführung', 'EVENT', 'CONCERT_OR_PERFORMANCE'),
    ('FLEA_MARKET_OR_SALES_EVENT', 'EVENT', 'FLEA_MARKET_OR_SALES_EVENT'),
    ('Flohmarkt oder Verkaufsaktion', 'EVENT', 'FLEA_MARKET_OR_SALES_EVENT'),
    ('FUNDRAISING_EVENT', 'EVENT', 'FUNDRAISING_EVENT'),
    ('Spendenaktion', 'EVENT', 'FUNDRAISING_EVENT'),
    ('TOURNAMENT_OR_COMPETITION', 'EVENT', 'TOURNAMENT_OR_COMPETITION'),
    ('Turnier oder Wettbewerb', 'EVENT', 'TOURNAMENT_OR_COMPETITION'),
    ('STUDY_EVENT', 'EVENT', 'STUDY_EVENT'),
    ('Studienveranstaltung', 'EVENT', 'STUDY_EVENT'),
    ('OTHER_EVENT', 'EVENT', 'OTHER_EVENT'),
    ('Sonstige Veranstaltung', 'EVENT', 'OTHER_EVENT'),
    ('MOVING', 'HOME', 'MOVING'),
    ('Umzug', 'HOME', 'MOVING'),
    ('RENOVATION_OR_HOME_PROJECT', 'HOME', 'RENOVATION_OR_HOME_PROJECT'),
    ('Renovierung oder Wohnprojekt', 'HOME', 'RENOVATION_OR_HOME_PROJECT'),
    ('DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION', 'HOME', 'DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION'),
    ('Entrümpelung oder größere Haushaltsorganisation', 'HOME', 'DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION'),
    ('GARDEN_PROJECT', 'HOME', 'GARDEN_PROJECT'),
    ('Gartenprojekt', 'HOME', 'GARDEN_PROJECT'),
    ('OTHER_HOME', 'HOME', 'OTHER_HOME'),
    ('Sonstige Zuhause', 'HOME', 'OTHER_HOME'),
    ('WRITING_PROJECT', 'CREATIVE', 'WRITING_PROJECT'),
    ('Buch, Geschichte oder anderes Schreibprojekt', 'CREATIVE', 'WRITING_PROJECT'),
    ('PODCAST', 'CREATIVE', 'PODCAST'),
    ('Podcast', 'CREATIVE', 'PODCAST'),
    ('VIDEO_OR_SHORT_FILM_PROJECT', 'CREATIVE', 'VIDEO_OR_SHORT_FILM_PROJECT'),
    ('Video- oder Kurzfilmprojekt', 'CREATIVE', 'VIDEO_OR_SHORT_FILM_PROJECT'),
    ('PHOTO_OR_GRAPHIC_PROJECT', 'CREATIVE', 'PHOTO_OR_GRAPHIC_PROJECT'),
    ('Foto- oder Grafikprojekt', 'CREATIVE', 'PHOTO_OR_GRAPHIC_PROJECT'),
    ('MUSIC_PROJECT', 'CREATIVE', 'MUSIC_PROJECT'),
    ('Musikprojekt', 'CREATIVE', 'MUSIC_PROJECT'),
    ('EXHIBITION', 'CREATIVE', 'EXHIBITION'),
    ('Ausstellung', 'CREATIVE', 'EXHIBITION'),
    ('BLOG_OR_SOCIAL_MEDIA_CAMPAIGN', 'CREATIVE', 'BLOG_OR_SOCIAL_MEDIA_CAMPAIGN'),
    ('Blog oder Social-Media-Kampagne', 'CREATIVE', 'BLOG_OR_SOCIAL_MEDIA_CAMPAIGN'),
    ('BOARD_GAME_OR_CREATIVE_PROTOTYPE', 'CREATIVE', 'BOARD_GAME_OR_CREATIVE_PROTOTYPE'),
    ('Brettspiel oder kreativer Prototyp', 'CREATIVE', 'BOARD_GAME_OR_CREATIVE_PROTOTYPE'),
    ('OTHER_CREATIVE_PROJECT', 'CREATIVE', 'OTHER_CREATIVE_PROJECT'),
    ('Sonstige kreative Projekte', 'CREATIVE', 'OTHER_CREATIVE_PROJECT'),
    ('JOB_SEARCH_AND_APPLICATION', 'CAREER', 'JOB_SEARCH_AND_APPLICATION'),
    ('Jobsuche und Bewerbung', 'CAREER', 'JOB_SEARCH_AND_APPLICATION'),
    ('CREATE_PORTFOLIO', 'CAREER', 'CREATE_PORTFOLIO'),
    ('Portfolio erstellen', 'CAREER', 'CREATE_PORTFOLIO'),
    ('TRAINING_OR_CERTIFICATION', 'CAREER', 'TRAINING_OR_CERTIFICATION'),
    ('Eigene Weiterbildung oder Zertifizierung', 'CAREER', 'TRAINING_OR_CERTIFICATION'),
    ('ONBOARDING_PLAN', 'CAREER', 'ONBOARDING_PLAN'),
    ('Einarbeitungsplan', 'CAREER', 'ONBOARDING_PLAN'),
    ('PROFESSIONAL_PRESENTATION', 'CAREER', 'PROFESSIONAL_PRESENTATION'),
    ('Berufliche Präsentation', 'CAREER', 'PROFESSIONAL_PRESENTATION'),
    ('PROCESS_IMPROVEMENT', 'CAREER', 'PROCESS_IMPROVEMENT'),
    ('Prozessverbesserung', 'CAREER', 'PROCESS_IMPROVEMENT'),
    ('PRODUCT_OR_BUSINESS_IDEA', 'CAREER', 'PRODUCT_OR_BUSINESS_IDEA'),
    ('Produkt- oder Geschäftsidee', 'CAREER', 'PRODUCT_OR_BUSINESS_IDEA'),
    ('OTHER_CAREER', 'CAREER', 'OTHER_CAREER'),
    ('Sonstige Beruf und Karriere', 'CAREER', 'OTHER_CAREER'),
    ('FITNESS_OR_RUNNING_GOAL', 'HEALTH_PERSONAL_DEVELOPMENT', 'FITNESS_OR_RUNNING_GOAL'),
    ('Fitness- oder Laufziel', 'HEALTH_PERSONAL_DEVELOPMENT', 'FITNESS_OR_RUNNING_GOAL'),
    ('COMPETITION_PREPARATION', 'HEALTH_PERSONAL_DEVELOPMENT', 'COMPETITION_PREPARATION'),
    ('Wettkampfvorbereitung', 'HEALTH_PERSONAL_DEVELOPMENT', 'COMPETITION_PREPARATION'),
    ('NUTRITION_PROJECT', 'HEALTH_PERSONAL_DEVELOPMENT', 'NUTRITION_PROJECT'),
    ('Ernährungsprojekt', 'HEALTH_PERSONAL_DEVELOPMENT', 'NUTRITION_PROJECT'),
    ('HABIT_OR_PERSONAL_CHALLENGE', 'HEALTH_PERSONAL_DEVELOPMENT', 'HABIT_OR_PERSONAL_CHALLENGE'),
    ('Gewohnheits- oder persönliche Challenge', 'HEALTH_PERSONAL_DEVELOPMENT', 'HABIT_OR_PERSONAL_CHALLENGE'),
    ('DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE', 'HEALTH_PERSONAL_DEVELOPMENT', 'DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE'),
    ('Digital Detox oder Alltagsveränderung', 'HEALTH_PERSONAL_DEVELOPMENT', 'DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE'),
    ('OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT', 'HEALTH_PERSONAL_DEVELOPMENT', 'OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT'),
    ('Sonstige Gesundheit und persönliche Entwicklung', 'HEALTH_PERSONAL_DEVELOPMENT', 'OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT'),
    ('TRIP_OR_VACATION', 'TRAVEL', 'TRIP_OR_VACATION'),
    ('Reise oder Urlaub', 'TRAVEL', 'TRIP_OR_VACATION'),
    ('ROAD_TRIP', 'TRAVEL', 'ROAD_TRIP'),
    ('Roadtrip', 'TRAVEL', 'ROAD_TRIP'),
    ('FESTIVAL_OR_CONCERT_TRIP', 'TRAVEL', 'FESTIVAL_OR_CONCERT_TRIP'),
    ('Festival- oder Konzertbesuch', 'TRAVEL', 'FESTIVAL_OR_CONCERT_TRIP'),
    ('CAMPING_TRIP', 'TRAVEL', 'CAMPING_TRIP'),
    ('Campingreise', 'TRAVEL', 'CAMPING_TRIP'),
    ('BICYCLE_TOUR', 'TRAVEL', 'BICYCLE_TOUR'),
    ('Fahrradtour', 'TRAVEL', 'BICYCLE_TOUR'),
    ('OTHER_TRAVEL', 'TRAVEL', 'OTHER_TRAVEL'),
    ('Sonstige Reisen', 'TRAVEL', 'OTHER_TRAVEL');

ALTER TABLE projects RENAME COLUMN project_type TO other_project_type_description;
ALTER TABLE projects ALTER COLUMN other_project_type_description DROP NOT NULL;
ALTER TABLE projects ADD COLUMN subcategory VARCHAR(100);

UPDATE projects p SET subcategory = m.enum_value
FROM subcategory_v17_mapping m
WHERE lower(btrim(p.other_project_type_description)) = lower(m.legacy_value)
  AND p.category = m.category;

INSERT INTO project_subcategory_migration_issues (source_table, source_id, category, legacy_value)
SELECT 'projects', p.id, p.category, p.other_project_type_description
FROM projects p
WHERE nullif(btrim(p.other_project_type_description), '') IS NOT NULL
  AND p.category IS DISTINCT FROM 'OTHER' AND p.subcategory IS NULL;

UPDATE projects
SET other_project_type_description = CASE WHEN category = 'OTHER'
    THEN nullif(btrim(other_project_type_description), '') ELSE NULL END;

ALTER TABLE projects ADD CONSTRAINT ck_projects_subcategory CHECK (
    subcategory IS NULL OR (category IS NOT NULL AND (
        (category = 'EDUCATION' AND subcategory IN ('PRESENTATION_OR_REPORT', 'EXAM_PREPARATION', 'LEARNING_PLAN', 'TERM_PAPER', 'THESIS', 'OTHER_EDUCATION'))
        OR
        (category = 'SOFTWARE_TECHNOLOGY' AND subcategory IN ('SOFTWARE_PROJECT', 'WEB_OR_MOBILE_APP', 'EXTEND_EXISTING_APPLICATION', 'WEBSITE', 'DATABASE_PROJECT', 'HARDWARE_OR_RASPBERRY_PI_PROJECT', 'OTHER_SOFTWARE_AND_TECHNOLOGY'))
        OR
        (category = 'EVENT' AND subcategory IN ('PRIVATE_CELEBRATION', 'WORKSHOP_TRAINING_OR_INFORMATION_EVENT', 'CLUB_OR_COMMUNITY_EVENT', 'CONCERT_OR_PERFORMANCE', 'FLEA_MARKET_OR_SALES_EVENT', 'FUNDRAISING_EVENT', 'TOURNAMENT_OR_COMPETITION', 'STUDY_EVENT', 'OTHER_EVENT'))
        OR
        (category = 'HOME' AND subcategory IN ('MOVING', 'RENOVATION_OR_HOME_PROJECT', 'DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION', 'GARDEN_PROJECT', 'OTHER_HOME'))
        OR
        (category = 'CREATIVE' AND subcategory IN ('WRITING_PROJECT', 'PODCAST', 'VIDEO_OR_SHORT_FILM_PROJECT', 'PHOTO_OR_GRAPHIC_PROJECT', 'MUSIC_PROJECT', 'EXHIBITION', 'BLOG_OR_SOCIAL_MEDIA_CAMPAIGN', 'BOARD_GAME_OR_CREATIVE_PROTOTYPE', 'OTHER_CREATIVE_PROJECT'))
        OR
        (category = 'CAREER' AND subcategory IN ('JOB_SEARCH_AND_APPLICATION', 'CREATE_PORTFOLIO', 'TRAINING_OR_CERTIFICATION', 'ONBOARDING_PLAN', 'PROFESSIONAL_PRESENTATION', 'PROCESS_IMPROVEMENT', 'PRODUCT_OR_BUSINESS_IDEA', 'OTHER_CAREER'))
        OR
        (category = 'HEALTH_PERSONAL_DEVELOPMENT' AND subcategory IN ('FITNESS_OR_RUNNING_GOAL', 'COMPETITION_PREPARATION', 'NUTRITION_PROJECT', 'HABIT_OR_PERSONAL_CHALLENGE', 'DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE', 'OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT'))
        OR
        (category = 'TRAVEL' AND subcategory IN ('TRIP_OR_VACATION', 'ROAD_TRIP', 'FESTIVAL_OR_CONCERT_TRIP', 'CAMPING_TRIP', 'BICYCLE_TOUR', 'OTHER_TRAVEL'))
    ))
);

ALTER TABLE plan_templates RENAME COLUMN project_type TO other_project_type_description;
ALTER TABLE plan_templates ALTER COLUMN other_project_type_description DROP NOT NULL;
ALTER TABLE plan_templates ADD COLUMN subcategory VARCHAR(100);

UPDATE plan_templates p SET subcategory = m.enum_value
FROM subcategory_v17_mapping m
WHERE lower(btrim(p.other_project_type_description)) = lower(m.legacy_value)
  AND p.category = m.category;

INSERT INTO project_subcategory_migration_issues (source_table, source_id, category, legacy_value)
SELECT 'plan_templates', p.id, p.category, p.other_project_type_description
FROM plan_templates p
WHERE nullif(btrim(p.other_project_type_description), '') IS NOT NULL
  AND p.category IS DISTINCT FROM 'OTHER' AND p.subcategory IS NULL;

UPDATE plan_templates
SET other_project_type_description = CASE WHEN category = 'OTHER'
    THEN nullif(btrim(other_project_type_description), '') ELSE NULL END;

ALTER TABLE plan_templates ADD CONSTRAINT ck_plan_templates_subcategory CHECK (
    subcategory IS NULL OR (category IS NOT NULL AND (
        (category = 'EDUCATION' AND subcategory IN ('PRESENTATION_OR_REPORT', 'EXAM_PREPARATION', 'LEARNING_PLAN', 'TERM_PAPER', 'THESIS', 'OTHER_EDUCATION'))
        OR
        (category = 'SOFTWARE_TECHNOLOGY' AND subcategory IN ('SOFTWARE_PROJECT', 'WEB_OR_MOBILE_APP', 'EXTEND_EXISTING_APPLICATION', 'WEBSITE', 'DATABASE_PROJECT', 'HARDWARE_OR_RASPBERRY_PI_PROJECT', 'OTHER_SOFTWARE_AND_TECHNOLOGY'))
        OR
        (category = 'EVENT' AND subcategory IN ('PRIVATE_CELEBRATION', 'WORKSHOP_TRAINING_OR_INFORMATION_EVENT', 'CLUB_OR_COMMUNITY_EVENT', 'CONCERT_OR_PERFORMANCE', 'FLEA_MARKET_OR_SALES_EVENT', 'FUNDRAISING_EVENT', 'TOURNAMENT_OR_COMPETITION', 'STUDY_EVENT', 'OTHER_EVENT'))
        OR
        (category = 'HOME' AND subcategory IN ('MOVING', 'RENOVATION_OR_HOME_PROJECT', 'DECLUTTERING_OR_HOUSEHOLD_ORGANIZATION', 'GARDEN_PROJECT', 'OTHER_HOME'))
        OR
        (category = 'CREATIVE' AND subcategory IN ('WRITING_PROJECT', 'PODCAST', 'VIDEO_OR_SHORT_FILM_PROJECT', 'PHOTO_OR_GRAPHIC_PROJECT', 'MUSIC_PROJECT', 'EXHIBITION', 'BLOG_OR_SOCIAL_MEDIA_CAMPAIGN', 'BOARD_GAME_OR_CREATIVE_PROTOTYPE', 'OTHER_CREATIVE_PROJECT'))
        OR
        (category = 'CAREER' AND subcategory IN ('JOB_SEARCH_AND_APPLICATION', 'CREATE_PORTFOLIO', 'TRAINING_OR_CERTIFICATION', 'ONBOARDING_PLAN', 'PROFESSIONAL_PRESENTATION', 'PROCESS_IMPROVEMENT', 'PRODUCT_OR_BUSINESS_IDEA', 'OTHER_CAREER'))
        OR
        (category = 'HEALTH_PERSONAL_DEVELOPMENT' AND subcategory IN ('FITNESS_OR_RUNNING_GOAL', 'COMPETITION_PREPARATION', 'NUTRITION_PROJECT', 'HABIT_OR_PERSONAL_CHALLENGE', 'DIGITAL_DETOX_OR_DAILY_LIFE_CHANGE', 'OTHER_HEALTH_AND_PERSONAL_DEVELOPMENT'))
        OR
        (category = 'TRAVEL' AND subcategory IN ('TRIP_OR_VACATION', 'ROAD_TRIP', 'FESTIVAL_OR_CONCERT_TRIP', 'CAMPING_TRIP', 'BICYCLE_TOUR', 'OTHER_TRAVEL'))
    ))
);

-- Older versions sometimes stored a JSON string inside JSONB. Unwrap first.
UPDATE ai_plan_generation_workflows
SET confirmed_snapshot = (confirmed_snapshot #>> '{}')::jsonb
WHERE jsonb_typeof(confirmed_snapshot) = 'string';

-- Abort instead of destroying an unexpected payload shape.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM ai_plan_generation_workflows
               WHERE jsonb_typeof(confirmed_snapshot) IS DISTINCT FROM 'object') THEN
        RAISE EXCEPTION 'V17: Unexpected confirmed_snapshot shape; inspect before migrating';
    END IF;
END $$;

INSERT INTO project_subcategory_migration_issues (source_table, source_id, category, legacy_value)
SELECT 'ai_plan_generation_workflows', w.id, w.confirmed_snapshot ->> 'category',
       w.confirmed_snapshot ->> 'projectType'
FROM ai_plan_generation_workflows w
WHERE nullif(btrim(w.confirmed_snapshot ->> 'projectType'), '') IS NOT NULL
  AND (w.confirmed_snapshot ->> 'category') IS DISTINCT FROM 'OTHER'
  AND NOT EXISTS (SELECT 1 FROM subcategory_v17_mapping m
      WHERE lower(btrim(w.confirmed_snapshot ->> 'projectType')) = lower(m.legacy_value)
        AND w.confirmed_snapshot ->> 'category' = m.category);

UPDATE ai_plan_generation_workflows w
SET confirmed_snapshot = (w.confirmed_snapshot - 'projectType') || jsonb_build_object(
    'subcategory', (SELECT m.enum_value FROM subcategory_v17_mapping m
        WHERE lower(btrim(w.confirmed_snapshot ->> 'projectType')) = lower(m.legacy_value)
          AND w.confirmed_snapshot ->> 'category' = m.category LIMIT 1),
    'otherProjectTypeDescription', CASE WHEN w.confirmed_snapshot ->> 'category' = 'OTHER'
        THEN nullif(btrim(w.confirmed_snapshot ->> 'projectType'), '') ELSE NULL END),
    snapshot_version = 'ai-wizard-v3';
