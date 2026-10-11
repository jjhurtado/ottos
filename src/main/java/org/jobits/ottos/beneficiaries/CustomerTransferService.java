package org.jobits.ottos.beneficiaries;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Exports and imports customers with their beneficiaries (contract: docs/contracts/customer-transfer.md). The spreadsheet
 * is read and written by the frontend; this works with JSON. A customer already exists when name, phone and address
 * match; a beneficiary when name, phone, street, house number and municipality match. Existing ones are not modified.
 */
public interface CustomerTransferService {

    /** Every customer, by name, with every linked beneficiary (inactive ones too). Recorded in the audit log. */
    CustomerExport exportAll(UUID actorId);

    /**
     * Imports each customer with its beneficiaries in its own transaction; a rejected item does not stop the rest.
     * With dryRun, the same result is computed and nothing is saved.
     */
    ImportResult importCustomers(boolean dryRun, List<ImportCustomer> customers, UUID actorId);

    record CustomerExport(Instant exportedAt, List<ExportedCustomer> customers) {
    }

    record ExportedCustomer(UUID id, String fullName, String phone, String email, String documentType,
                            String documentNumber, String address, String notes, boolean active, Instant createdAt,
                            List<ExportedBeneficiary> beneficiaries) {
    }

    record ExportedBeneficiary(UUID id, String fullName, String phone, String alternatePhone, String street,
                               String houseNumber, String betweenStreets, String neighborhood, String municipalityCode,
                               String municipalityName, String reference, String documentNumber, String notes,
                               boolean active) {
    }

    /** ref: anything the frontend uses to locate the row in its file; returned as is. */
    record ImportCustomer(String ref, String fullName, String phone, String email, String documentType,
                          String documentNumber, String address, String notes, List<ImportBeneficiary> beneficiaries) {
    }

    /** municipality: code (2302) or name (Plaza de la Revolución, ignoring case and accents). */
    record ImportBeneficiary(String ref, String fullName, String phone, String alternatePhone, String street,
                             String houseNumber, String betweenStreets, String neighborhood, String municipality,
                             String reference, String documentNumber, String notes) {
    }

    enum ItemStatus {
        CREATED,
        EXISTING,
        REJECTED
    }

    record ImportResult(boolean dryRun, Summary summary, List<CustomerResult> results) {
    }

    record Summary(long customersCreated, long customersExisting, long customersRejected, long beneficiariesCreated,
                   long beneficiariesExisting, long beneficiariesRejected, long linksCreated) {
    }

    /** customerId is null when rejected, or in a dry run for a customer that would be created. */
    record CustomerResult(int index, String ref, ItemStatus status, UUID customerId, String code, String message,
                          List<BeneficiaryResult> beneficiaries) {
    }

    /** linked: the link to the customer was created by this import. */
    record BeneficiaryResult(int index, String ref, ItemStatus status, UUID beneficiaryId, boolean linked, String code,
                             String message) {
    }
}
