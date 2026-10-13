package org.jobits.ottos.beneficiaries;

import org.jobits.ottos.beneficiaries.domain.Beneficiary;
import org.jobits.ottos.branches.ZoneService.MunicipalityInfo;
import org.jobits.ottos.customers.CustomerService.CustomerInfo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Beneficiaries and their links to customers. Other modules use {@link #find(UUID)} and {@link #isLinked}. */
public interface BeneficiaryService {

    Optional<BeneficiaryInfo> find(UUID id);

    boolean isLinked(UUID customerId, UUID beneficiaryId);

    /** The beneficiary and the customers that send to them. */
    BeneficiaryDetail get(UUID id);

    List<BeneficiaryInfo> search(String text);

    /**
     * Beneficiaries (active or not) whose phone ends with the same last 8 digits as this one, however it is written
     * ("56733312", "+53 5 4752090"): Cuban numbers are 8 digits after the country code. For matching old records.
     */
    List<BeneficiaryInfo> findByPhone(String phone);

    List<BeneficiaryInfo> ofCustomer(UUID customerId);

    /** Creates a beneficiary and links it to the customer who sends to them. */
    BeneficiaryInfo create(UUID customerId, BeneficiaryData data, UUID actorId);

    /**
     * Creates a beneficiary no customer is linked to yet. It is found by the global search and linked to a customer
     * later, at the latest when that customer sends them a remittance.
     */
    BeneficiaryInfo create(BeneficiaryData data, UUID actorId);

    BeneficiaryInfo update(UUID id, BeneficiaryData data);

    /** Links an existing beneficiary to another customer. Linking twice is harmless. */
    void link(UUID customerId, UUID beneficiaryId);

    void unlink(UUID customerId, UUID beneficiaryId);

    /**
     * Deletes the beneficiary for every customer: it is deactivated, disappears from searches and customer lists, and
     * can no longer receive remittances or be linked. Past and open remittances keep their copy of its data.
     * Deleting twice is harmless.
     */
    void delete(UUID id);

    /** Undoes {@link #delete}: the beneficiary is active again, with the links to customers it had. */
    BeneficiaryInfo restore(UUID id);

    /** A beneficiary's data, to create or update one. municipalityCode: a known municipality (UNKNOWN_MUNICIPALITY). */
    record BeneficiaryData(String fullName, String phone, String alternatePhone, String street, String houseNumber,
                           String betweenStreets, String neighborhood, String municipalityCode, String reference,
                           String documentNumber, String notes) {

        Beneficiary.Details toDetails() {
            return new Beneficiary.Details(fullName, phone, alternatePhone, street, houseNumber, betweenStreets,
                    neighborhood, municipalityCode, reference, documentNumber, notes);
        }
    }

    record BeneficiaryInfo(UUID id, String fullName, String phone, String alternatePhone, String street,
                    String houseNumber, String betweenStreets, String neighborhood,
                    String municipalityCode, String municipalityName, String provinceName,
                    String reference, String documentNumber, String notes, boolean active,
                    Instant createdAt) {

        static BeneficiaryInfo of(Beneficiary b, MunicipalityInfo municipality) {
            Beneficiary.Details d = b.details();
            return new BeneficiaryInfo(b.getId(), d.fullName(), d.phone(), d.alternatePhone(), d.street(),
                    d.houseNumber(), d.betweenStreets(), d.neighborhood(), d.municipalityCode(),
                    municipality == null ? null : municipality.name(),
                    municipality == null ? null : municipality.provinceName(),
                    d.reference(), d.documentNumber(), d.notes(), b.isActive(), b.getCreatedAt());
        }

        /** One-line address for receipts and delivery lists. */
        public String addressLine() {
            return Stream.of(
                            street + (houseNumber == null ? "" : " #" + houseNumber),
                            betweenStreets == null ? null : "e/ " + betweenStreets,
                            neighborhood, municipalityName, provinceName)
                    .filter(part -> part != null && !part.isBlank())
                    .collect(Collectors.joining(", "));
        }
    }

    record BeneficiaryDetail(BeneficiaryInfo beneficiary, List<CustomerInfo> customers) {
    }
}
