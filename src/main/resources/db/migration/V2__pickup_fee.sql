-- The fee of a pickup is entered by hand by whoever registers it (usually the admin), like its rate; without one,
-- the configured pickup fee rule applies. fee_overridden says the fee was entered by hand.
-- Pre-production (1.0.0-rc.1): folded into V1__baseline.sql when rc.1 closes.

ALTER TABLE remittances ADD COLUMN fee_overridden BOOLEAN NOT NULL DEFAULT FALSE;
