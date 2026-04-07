-- Seed example public holiday: Kite Festival (Uttarayan) on 14th January
INSERT INTO public_holidays (name, holiday_date, description, created_by, created_at, updated_at)
SELECT 'Kite Festival (Uttarayan)',
       '2026-01-14',
       'Uttarayan — the kite festival celebrated across Gujarat. All employees have a company holiday on this day.',
       'admin',
       NOW(),
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM public_holidays
    WHERE holiday_date = '2026-01-14' AND LOWER(name) = 'kite festival (uttarayan)'
);
