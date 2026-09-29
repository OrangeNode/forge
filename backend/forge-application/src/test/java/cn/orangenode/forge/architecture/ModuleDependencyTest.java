package cn.orangenode.forge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模块方向约束检查。
 *
 * <p>校验计划中固定的依赖方向：core 不依赖框架与业务，framework 不反向依赖业务模块，
 * 业务模块之间不互相依赖。application 是唯一装配模块，可以依赖全部已装配模块。</p>
 *
 * <p>覆盖范围说明：当前已存在类的模块都会被扫描。M1 只有 core、framework、example
 * 与 application 含 Java 类，其余模块尚无代码，因此“业务模块互不依赖”等规则在
 * 出现对应类时才会产生实际约束力；层级与 Controller 访问 Mapper 等规则在 M2 补充。</p>
 *
 * <p>M3 修正：初版的“业务模块互不依赖”把同一组包同时写在“检查对象”和“禁止依赖”两侧，
 * system 一旦出现类，它对本模块实体与服务的正常依赖就被当成跨模块违规（实测报出 148 条）。
 * 现在按模块逐个构造规则，禁止依赖的集合排除模块自身。</p>
 */
class ModuleDependencyTest {

    /**
     * 业务模块包名前缀，供逐模块构造“不互相依赖”的规则使用。
     */
    private static final String[] BUSINESS_MODULE_PACKAGES = {
        "cn.orangenode.forge.system",
        "cn.orangenode.forge.file",
        "cn.orangenode.forge.audit",
        "cn.orangenode.forge.example"
    };

    /**
     * 业务模块包名，供反向依赖规则复用。
     */
    private static final String[] BUSINESS_PACKAGES = Arrays.stream(BUSINESS_MODULE_PACKAGES)
            .map(modulePackage -> modulePackage + "..")
            .toArray(String[]::new);

    /**
     * 已编译类集合，排除测试类与测试依赖代码。
     */
    private static JavaClasses productionClasses;

    /**
     * 合并多个包名数组，供规则一次声明全部禁止依赖的包。
     *
     * @param packageGroups 待合并的包名数组
     * @return 合并后的包名数组
     */
    private static String[] mergePackages(String[]... packageGroups) {
        List<String> merged = new ArrayList<>();
        for (String[] group : packageGroups) {
            merged.addAll(Arrays.asList(group));
        }
        return merged.toArray(new String[0]);
    }

    /**
     * 导入生产类，供各条规则复用。
     */
    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("cn.orangenode.forge");
    }

    /**
     * 验证 core 不依赖 Spring、业务模块或其他项目模块。
     */
    @Test
    @DisplayName("core 保持与框架和业务无关")
    void coreShouldStayIndependent() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("cn.orangenode.forge.core..")
                .should().dependOnClassesThat().resideInAnyPackage(mergePackages(
                        new String[] {
                            "org.springframework..",
                            "jakarta.servlet..",
                            "org.apache.ibatis..",
                            "com.baomidou..",
                            "cn.orangenode.forge.framework..",
                            "cn.orangenode.forge.application.."
                        },
                        BUSINESS_PACKAGES));

        rule.check(productionClasses);
    }

    /**
     * 验证 framework 不依赖任何业务模块。
     */
    @Test
    @DisplayName("framework 不反向依赖业务模块")
    void frameworkShouldNotDependOnBusinessModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("cn.orangenode.forge.framework..")
                .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_PACKAGES);

        rule.check(productionClasses);
    }

    /**
     * 验证业务模块之间不相互依赖。
     *
     * <p>跨模块协作必须通过公开契约，不能直接引用其他业务模块的内部实现。
     * 规则按模块逐个构造，禁止依赖的包集合排除模块自身，因此模块内部的分层依赖不会误报；
     * 尚无生产类的模块（如 file、audit）允许匹配为空，避免阶段推进中被误判为规则失效。</p>
     */
    @Test
    @DisplayName("业务模块之间不互相依赖")
    void businessModulesShouldNotDependOnEachOther() {
        for (String modulePackage : BUSINESS_MODULE_PACKAGES) {
            String[] otherModules = Arrays.stream(BUSINESS_MODULE_PACKAGES)
                    .filter(candidate -> !candidate.equals(modulePackage))
                    .map(candidate -> candidate + "..")
                    .toArray(String[]::new);
            ArchRule rule = noClasses()
                    .that().resideInAPackage(modulePackage + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(otherModules)
                    .because("模块之间只能通过公开契约协作，不能直接引用彼此实现")
                    .allowEmptyShould(true);

            rule.check(productionClasses);
        }
    }

    /**
     * 验证 core 不包含 Controller，避免业务代码写入基础模块。
     */
    @Test
    @DisplayName("core 中不出现 Web 控制器")
    void coreShouldNotContainControllers() {
        ArchRule rule = noClasses()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .or().areAnnotatedWith("org.springframework.stereotype.Controller")
                .should().resideInAPackage("cn.orangenode.forge.core..")
                .because("基础模块不承载业务接口");

        rule.check(productionClasses);
    }
}
