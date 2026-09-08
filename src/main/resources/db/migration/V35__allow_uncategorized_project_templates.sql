-- Templates without classification remain globally visible under "Sonstiges".
ALTER TABLE plan_templates
    ALTER COLUMN category DROP NOT NULL;
