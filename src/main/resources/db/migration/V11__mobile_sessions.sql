-- Phase 1 · Sessions of the mobile app.
--
-- A refresh token remembers where its session was opened (WEB or MOBILE): mobile access tokens last a whole working
-- day so the app works offline. replaced_by points to the token issued when this one was used, so a retry of a refresh
-- whose response was lost can be told apart from a stolen token (ottos.security.refresh-reuse-grace).

ALTER TABLE refresh_tokens ADD COLUMN client VARCHAR(10) NOT NULL DEFAULT 'WEB';
ALTER TABLE refresh_tokens ADD CONSTRAINT ck_refresh_tokens_client CHECK (client IN ('WEB', 'MOBILE'));
ALTER TABLE refresh_tokens ADD COLUMN replaced_by UUID;
