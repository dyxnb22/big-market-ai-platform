package com.dyx.market.message.job.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * GOV-D02：message-job 负责 MQ/XXL-Job，不得暴露业务 HTTP Controller，
 * 也不得依赖 trigger.http 控制器层。
 */
public class MessageJobArchitectureTest {

    @Test
    public void message_job_must_not_depend_on_trigger_http() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dyx.market.message.job");
        noClasses().that().resideInAPackage("com.dyx.market.message.job..")
                .should().dependOnClassesThat().resideInAPackage("com.dyx.market.trigger.http..")
                .because("HTTP Controllers belong to market (and other API launchers), not message-job")
                .check(classes);
    }

    @Test
    public void message_job_must_not_define_rest_controllers() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dyx.market.message.job");
        ArchRule rule = noClasses().that().resideInAPackage("com.dyx.market.message.job..")
                .should().beAnnotatedWith(RestController.class)
                .because("message-job must not expose business HTTP Controllers");
        rule.check(classes);
    }

    @Test
    public void account_owned_dao_access_must_use_the_registered_reconcile_exception() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dyx.market.message.job");

        Set<String> accountOwnedDaos = new HashSet<>(Arrays.asList(
                "com.dyx.market.infrastructure.dao.IUserCreditAccountDao",
                "com.dyx.market.infrastructure.dao.IUserCreditOrderDao",
                "com.dyx.market.infrastructure.dao.IRaffleActivityOrderDao",
                "com.dyx.market.infrastructure.dao.IRaffleQuotaDecrementLedgerDao"
        ));
        String registeredException =
                "com.dyx.market.message.job.config.CreditPayDeliveryReconcileJob";

        for (JavaClass javaClass : classes) {
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getFullName();
                if (accountOwnedDaos.contains(target)
                        && !registeredException.equals(javaClass.getFullName())) {
                    throw new AssertionError("Unregistered message-job dependency on account-owned DAO: "
                            + javaClass.getFullName() + " -> " + target
                            + "; add a typed RPC or an explicit temporary exception");
                }
            }
        }
    }
}
