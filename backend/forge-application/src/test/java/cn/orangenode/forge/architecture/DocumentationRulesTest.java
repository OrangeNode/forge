package cn.orangenode.forge.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.DefaultLogger;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.AutomaticBean.OutputStreamOptions;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TextBlock;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 使用构建的真实 Checkstyle 配置验证注释规则，补充中文与多行要求。
 */
class DocumentationRulesTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * 扫描父工程声明的全部模块源码，排除构建产物及生成代码目录。
     */
    @Test
    void handwrittenSourcesShouldHaveChineseDocumentation() throws Exception {
        Path backend = Path.of(System.getProperty("forge.backend.directory"));
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var modules = factory.newDocumentBuilder().parse(backend.resolve("pom.xml").toFile())
                .getElementsByTagName("module");
        var sources = new java.util.ArrayList<File>();
        for (int i = 0; i < modules.getLength(); i++) {
            Path module = backend.resolve(modules.item(i).getTextContent());
            for (String directory : List.of("src/main/java", "src/test/java")) {
                Path sourceRoot = module.resolve(directory);
                if (Files.exists(sourceRoot)) {
                    try (var files = Files.walk(sourceRoot)) {
                        sources.addAll(files.filter(path -> path.toString().endsWith(".java"))
                                .map(Path::toFile).toList());
                    }
                }
            }
        }
        assertThat(sources).isNotEmpty();
        assertThat(check(sources)).isEmpty();
    }

    /**
     * 验证各种手写方法遗漏注释都会失败，包含注解、接口与紧凑构造器。
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "private void sample() {}",
        "protected void sample() {}",
        "void sample() {}",
        "public void sample() {}",
        "Sample() {}",
        "@Override public String toString() { return \"\"; }",
        "@org.junit.jupiter.api.Test void sample() {}",
        "/** 接口职责。 */\ninterface Contract {\n void sample();\n}",
        "/** 数据职责。 */\nrecord Value(int id) {\n Value {}\n}",
        "public int getValue() { return 1; }"
    })
    void missingDocumentationShouldFail(String member) throws Exception {
        assertThat(checkFixture(member)).contains("MissingJavadocMethod");
    }

    /**
     * 验证单行、纯英文、空注释及仅继承说明不能替代中文多行职责说明。
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "/** 中文单行。 */",
        "/**\n * English only.\n */",
        "/**\n * {@inheritDoc}\n */",
        "/**\n *\n */"
    })
    void invalidDocumentationShouldFail(String comment) throws Exception {
        assertThat(checkFixture(comment + "\nprivate void sample() {}"))
                .contains("ChineseDocumentation");
    }

    /**
     * 验证中文多行注释通过，Lambda 和未生成的 Lombok 方法不被误报。
     */
    @Test
    void documentedMethodsAndLambdaShouldPass() throws Exception {
        assertThat(checkFixture("""
                @lombok.Getter private int value;
                /**
                 * 创建检查样例。
                 */
                Sample() {}
                /**
                 * 返回样例描述。
                 */
                @Override public String toString() { return "sample"; }
                /**
                 * 在回调中完成样例操作。
                 */
                private void sample() { Runnable callback = () -> {}; }
                """)).isEmpty();
    }

    /**
     * 将反例写入自动清理的临时目录，避免污染生产源码和提交内容。
     */
    private String checkFixture(String member) throws Exception {
        Path file = temporaryDirectory.resolve("Sample.java");
        Files.writeString(file, "/** 样例职责。 */\nclass Sample {\n" + member + "\n}\n");
        return check(List.of(file.toFile()));
    }

    /**
     * 加载同一份构建规则并追加窄范围中文检查，返回有违规时的完整诊断。
     */
    private String check(List<File> files) throws Exception {
        Path configuration = Path.of(System.getProperty("forge.backend.directory"), "checkstyle.xml");
        var config = (DefaultConfiguration) ConfigurationLoader.loadConfiguration(
                configuration.toString(), new PropertiesExpander(new Properties()),
                ConfigurationLoader.IgnoredModulesOptions.OMIT);
        var walker = new DefaultConfiguration("TreeWalker");
        var chinese = new DefaultConfiguration(ChineseDocumentationCheck.class.getName());
        chinese.addMessage("documentation.chinese", "职责说明必须包含中文，方法和构造器必须使用多行 Javadoc。");
        walker.addChild(chinese);
        config.addChild(walker);
        var output = new ByteArrayOutputStream();
        var checker = new Checker();
        try {
            checker.setModuleClassLoader(getClass().getClassLoader());
            checker.addListener(new DefaultLogger(output, OutputStreamOptions.NONE));
            checker.configure(config);
            return checker.process(files) == 0 ? "" : output.toString(StandardCharsets.UTF_8);
        } finally {
            checker.destroy();
        }
    }

    /**
     * 只检查真实声明关联的 Javadoc，不用正则表达式猜测 Java 方法边界。
     */
    public static class ChineseDocumentationCheck extends AbstractCheck {

        private static final Pattern CHINESE = Pattern.compile("[\\p{IsHan}]");

        /**
         * 声明需要中文职责说明的类型和手写方法节点。
         */
        @Override
        public int[] getDefaultTokens() {
            return new int[] {TokenTypes.METHOD_DEF, TokenTypes.CTOR_DEF,
                TokenTypes.COMPACT_CTOR_DEF, TokenTypes.ANNOTATION_FIELD_DEF,
                TokenTypes.CLASS_DEF, TokenTypes.INTERFACE_DEF, TokenTypes.ENUM_DEF,
                TokenTypes.RECORD_DEF, TokenTypes.ANNOTATION_DEF};
        }

        /**
         * 限定可配置节点，避免误检查字段与 Lambda。
         */
        @Override
        public int[] getAcceptableTokens() {
            return getDefaultTokens();
        }

        /**
         * 保证所有约定声明均参与检查。
         */
        @Override
        public int[] getRequiredTokens() {
            return getDefaultTokens();
        }

        /**
         * 检查职责正文含中文，并验证方法注释的起止标记独占行。
         */
        @Override
        public void visitToken(DetailAST ast) {
            TextBlock comment = getFileContents().getJavadocBefore(ast.getLineNo());
            if (comment == null) {
                return;
            }
            String[] lines = comment.getText();
            String description = String.join("\n", lines).split("(?m)^\\s*\\*\\s*@", 2)[0];
            boolean method = ast.getType() == TokenTypes.METHOD_DEF
                    || ast.getType() == TokenTypes.CTOR_DEF
                    || ast.getType() == TokenTypes.COMPACT_CTOR_DEF
                    || ast.getType() == TokenTypes.ANNOTATION_FIELD_DEF;
            boolean multiline = lines.length >= 3 && lines[0].strip().equals("/**")
                    && lines[lines.length - 1].strip().equals("*/");
            if (!CHINESE.matcher(description).find() || (method && !multiline)) {
                log(ast, "documentation.chinese");
            }
        }
    }
}
