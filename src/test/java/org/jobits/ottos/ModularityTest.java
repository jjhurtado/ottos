package org.jobits.ottos;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails when a module uses another module's internal classes (sub-packages) or when dependency cycles appear.
 * This is the module discipline of the modular monolith, checked on every build.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(OttosApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void printModules() {
        modules.forEach(System.out::println);
    }
}
