package com.dyx.market.market.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * BM-017: market-service launcher code must not wire MQ jobs/listeners (message-job owns them).
 */
public class MarketServiceArchitectureTest {

    @Test
    public void market_must_not_depend_on_trigger_job_or_listener() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dyx.market.market");
        noClasses().that().resideInAPackage("com.dyx.market.market..")
                .should().dependOnClassesThat().resideInAPackage("com.dyx.market.trigger.job..")
                .because("XXL jobs belong to message-job-service")
                .check(classes);
        noClasses().that().resideInAPackage("com.dyx.market.market..")
                .should().dependOnClassesThat().resideInAPackage("com.dyx.market.trigger.listener..")
                .because("MQ listeners belong to message-job-service")
                .check(classes);
    }
}
