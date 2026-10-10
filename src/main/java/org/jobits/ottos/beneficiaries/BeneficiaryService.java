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

    List<BeneficiaryInfo> ofCustomer(UUID customerId);

    /** Creates a beneficiary and links it to the customer who sends to them. */
    BeneficiaryInfo create(UUID customerId, Beneficiary.Details details, UUID actorId);

    BeneficiaryInfo update(UUID id, Beneficiary.Details details);

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
