-- Business reports (today: the monthly sales report, contract docs/contracts/monthly-report.md).

INSERT INTO permissions (code, module, description) VALUES
    ('reports:read', 'reporting', 'View the business reports, such as the monthly sales report');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, 'reports:read' FROM roles r WHERE r.code IN ('ADMIN', 'SALES');
