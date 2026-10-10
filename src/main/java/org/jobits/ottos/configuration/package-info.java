/**
 * Configuration: every configurable value of the business, managed from one place. Settings (minimum amount per
 * remittance, default delivery days) and, per corridor (currency pair such as USD-CUP), the exchange-rate and fee-rule
 * history. Values are read by other modules; the rates module calculates quotes with them.
 * <p>
 * Public API: {@link org.jobits.ottos.configuration.ConfigurationService} and
 * {@link org.jobits.ottos.configuration.FeeType}. Everything in sub-packages is internal.
 * Phase 1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Configuration")
package org.jobits.ottos.configuration;
