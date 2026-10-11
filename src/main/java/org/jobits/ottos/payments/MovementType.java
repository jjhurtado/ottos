package org.jobits.ottos.payments;

public enum MovementType {
    /** External → business: the sender paid a remittance. */
    REMITTANCE_PAYMENT,
    /** Business → courier: cash handed to a courier. */
    COURIER_FUNDING,
    /** Courier → external: cash delivered to a beneficiary. */
    DELIVERY_PAYOUT,
    /** External → courier: cash collected from a beneficiary. */
    PICKUP_COLLECTION,
    /** Courier → business: cash a courier returned. */
    COURIER_RETURN,
    /** External → business: money put into the box (capital, the CUP side of an exchange…). */
    BUSINESS_DEPOSIT,
    /** Business → external: money taken out of the box (expenses, the USD side of an exchange…). */
    BUSINESS_WITHDRAWAL,
    /** A courier changed cash to another currency: the currency they gave away leaves their cash. */
    COURIER_EXCHANGE_OUT,
    /** A courier changed cash to another currency: the currency they got enters their cash. */
    COURIER_EXCHANGE_IN
}
