package cn.orangenode.forge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 架构规则的反例验证。
 *
 * <p>规则返回“无违规”可能只是因为扫描集合为空，因此这里同时提供反例与对照：
 * 同类规则在存在禁止依赖时必须报出违规，在依赖合法时必须通过。
 * 反例使用测试类自身已存在的依赖，不向生产代码加入临时类，也不需要额外恢复步骤。</p>
 */
class ModuleRuleCounterExampleTest {

    /**
     * 测试包内已编译的类，测试类自身依赖 JUnit。
     */
    private static JavaClasses testClasses;

    /**
     * 导入测试包内的类，供反例规则使用。
     */
    @BeforeAll
    static void importTestClasses() {
        testClasses = new ClassFileImporter()
                .importPackages("cn.orangenode.forge.architecture");
    }

    /**
     * 反例：测试类依赖 JUnit 时，禁止依赖 JUnit 的规则必须报出违规。
     */
    @Test
    @DisplayName("存在被禁止的依赖时规则报出违规")
    void ruleShouldReportViolationWhenDependencyExists() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..architecture..")
                .should().dependOnClassesThat().resideInAnyPackage("org.junit..");

        var result = rule.evaluate(testClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).isNotEmpty();
        assertThat(result.getFailureReport().toString()).contains("junit");
    }

    /**
     * 对照：依赖合法时同一写法的规则通过，证明报错来自依赖而非规则构造错误。
     */
    @Test
    @DisplayName("依赖合法时规则通过")
    void ruleShouldPassWhenDependencyIsAbsent() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..architecture..")
                .should().dependOnClassesThat().resideInAnyPackage("org.apache.ibatis..");

        assertThatCode(() -> rule.check(testClasses)).doesNotThrowAnyException();
    }
}
