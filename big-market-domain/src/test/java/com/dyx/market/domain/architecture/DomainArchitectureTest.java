package com.dyx.market.domain.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * BM-017：domain 不得依赖 MyBatis DAO 包，只能通过端口/适配器访问基础设施。
 */
public class DomainArchitectureTest {

    @Test
    public void domain_must_not_depend_on_infrastructure_dao() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dyx.market.domain");
        noClasses().that().resideInAPackage("com.dyx.market.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.dyx.market.infrastructure.dao..")
                .because("DAO ownership is enforced via ports/adapters, not direct domain→dao coupling")
                .check(classes);
    }

    @Test
    public void domain_must_not_depend_on_trigger_job() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dyx.market.domain");
        noClasses().that().resideInAPackage("com.dyx.market.domain..")
                .should().dependOnClassesThat().resideInAPackage("com.dyx.market.trigger.job..")
                .because("XXL jobs belong to message-job trigger layer, not domain")
                .check(classes);
    }
}
