-- Each pickup can carry its own rate (contract: docs/contracts/pickup-rate.md). Pickups are done when the business
-- wants cash, usually 1x1 and for large amounts, with no earnings; Sales enters the rate of the moment when registering
-- one. rate_overridden says whether a remittance's rate was entered by hand instead of the configured one.

ALTER TABLE remittances ADD COLUMN rate_overridden BOOLEAN NOT NULL DEFAULT FALSE;
