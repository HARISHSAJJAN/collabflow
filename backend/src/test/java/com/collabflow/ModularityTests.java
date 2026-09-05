package com.collabflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * The automated version of the manual {@code grep -rl "import com.collabflow.<mod>.internal."}
 * check run by hand after every phase since Phase 1 (see every phase's commit message and
 * docs/architecture.md's phase log) - now a real, CI-enforced build gate instead of a habit
 * that could be forgotten. Fails the build if any module reaches into another module's
 * {@code .internal} package, or if the module structure is otherwise invalid (a cycle, an
 * undeclared dependency).
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(CollabFlowApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void everyModuleWasDiscovered() {
        // A cheap sanity check that the module scan itself is working (all thirteen domain +
        // infrastructure packages under com.collabflow are picked up), not just that verify()
        // didn't throw - a mis-scoped scan could otherwise make this test suite pass for the
        // wrong reason (verifying zero or one module's boundaries, not all of them).
        long moduleCount = MODULES.stream().count();
        assertThat(moduleCount).isEqualTo(13);
    }
}
