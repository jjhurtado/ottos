-- Data fix (user's decision, 2026-10-13): three tickets of the old WhatsApp records were pickups ("RECOGER") but were
-- imported as delivered remittances. Each one is voided (kept for the record, with the reason) and registered again
-- as a historical pickup on the same date, with no fee, the same beneficiary and customer, and no cash movement
-- (imported remittances never moved cash). Idempotent and harmless where the tickets were never imported (a fresh
-- database, the tests): it only acts on remittances with idempotency key historical:<ref> that are not voided yet.

DO $$
DECLARE
    refs   TEXT[] := ARRAY['chat-20261006-210318', 'chat-20260609-132726', 'chat-20260731-111256'];
    ref    TEXT;
    orig   RECORD;
    new_id UUID;
    new_code TEXT;
    reason TEXT := 'Era una recogida importada como remesa por error; registrada de nuevo como recogida';
    rate_id UUID;
    rule_id UUID;
BEGIN
    SELECT id INTO rate_id FROM exchange_rates
    WHERE corridor_code = 'USD-USD' AND remittance_type = 'PICKUP' ORDER BY valid_from DESC LIMIT 1;
    SELECT id INTO rule_id FROM fee_rules
    WHERE corridor_code = 'USD-USD' AND remittance_type = 'PICKUP' ORDER BY valid_from DESC LIMIT 1;

    FOREACH ref IN ARRAY refs LOOP
        SELECT * INTO orig FROM remittances
        WHERE idempotency_key = 'historical:' || ref AND voided_at IS NULL;
        CONTINUE WHEN NOT FOUND;

        -- Void the wrong remittance
        UPDATE remittances
        SET voided_at = CURRENT_TIMESTAMP, voided_by = orig.created_by, void_reason = reason,
            open_incident_reason = NULL, open_incident_note = NULL, open_incident_at = NULL, open_incident_by = NULL,
            review_reason = NULL, review_flagged_at = NULL, review_action_id = NULL,
            updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = orig.id;
        INSERT INTO remittance_events (id, remittance_id, type, from_status, to_status, note, actor_id, occurred_at)
        VALUES (gen_random_uuid(), orig.id, 'VOIDED', orig.status, orig.status, reason, orig.created_by, CURRENT_TIMESTAMP);
        INSERT INTO audit_log (id, occurred_at, actor_id, action, target_type, target_id, target_label, details)
        VALUES (gen_random_uuid(), CURRENT_TIMESTAMP, NULL, 'REMITTANCE_VOIDED', 'REMITTANCE', orig.id::text, orig.code,
                json_build_object('code', orig.code, 'reason', reason, 'by', 'migration V5')::text);

        -- Register it again as a pickup: the courier collected the amount; nothing delivered, no fee
        new_id := gen_random_uuid();
        LOOP
            SELECT 'OT-' || string_agg(substr('ABCDEFGHJKMNPQRSTUVWXYZ23456789', 1 + floor(random() * 31)::int, 1), '')
            INTO new_code FROM generate_series(1, 6);
            EXIT WHEN NOT EXISTS (SELECT 1 FROM remittances r WHERE r.code = new_code);
        END LOOP;
        INSERT INTO remittances (
            id, code, type, status, customer_id, beneficiary_id, beneficiary_name, beneficiary_phone,
            beneficiary_alternate_phone, beneficiary_address, beneficiary_municipality_code, beneficiary_reference,
            corridor_code, source_currency, target_currency, amount, fee, fee_overridden, total, rate,
            rate_overridden, amount_to_deliver, exchange_rate_id, fee_rule_id, pin, courier_id, expected_date,
            postponements, notes, idempotency_key, created_at, created_by, completed_at, completed_by, updated_at,
            version)
        VALUES (
            new_id, new_code, 'PICKUP', orig.status, orig.customer_id, orig.beneficiary_id, orig.beneficiary_name,
            orig.beneficiary_phone, orig.beneficiary_alternate_phone, orig.beneficiary_address,
            orig.beneficiary_municipality_code, orig.beneficiary_reference,
            'USD-USD', 'USD', 'USD', orig.amount, 0, TRUE, orig.amount, 1,
            FALSE, orig.amount, rate_id, rule_id, lpad(floor(random() * 1000000)::int::text, 6, '0'), NULL,
            orig.expected_date, 0, 'Recogida de los registros antiguos (' || ref || '), corregida desde ' || orig.code,
            'historical:' || ref || ':pickup', orig.created_at, orig.created_by, orig.completed_at, orig.completed_by,
            CURRENT_TIMESTAMP, 0);
        INSERT INTO remittance_events (id, remittance_id, type, to_status, new_date, note, actor_id, occurred_at)
        VALUES (gen_random_uuid(), new_id, 'CREATED', orig.status, orig.expected_date,
                'Recogida de los registros antiguos (' || ref || '), corregida desde ' || orig.code,
                orig.created_by, orig.created_at);
        INSERT INTO audit_log (id, occurred_at, actor_id, action, target_type, target_id, target_label, details)
        VALUES (gen_random_uuid(), CURRENT_TIMESTAMP, NULL, 'HISTORICAL_REMITTANCE_CREATED', 'REMITTANCE',
                new_id::text, new_code,
                json_build_object('code', new_code, 'type', 'PICKUP', 'amount', orig.amount, 'fee', 0, 'currency', 'USD',
                                  'occurredAt', orig.created_at, 'correctedFrom', orig.code, 'by', 'migration V5')::text);
    END LOOP;
END $$;
