package io.github.douira.glsl_transformer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;

import au.com.origin.snapshots.Expect;
import au.com.origin.snapshots.annotations.SnapshotName;
import au.com.origin.snapshots.junit5.SnapshotExtension;
import io.github.douira.glsl_transformer.ast.node.Version;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.transform.*;
import io.github.douira.glsl_transformer.parser.ParsingException;
import io.github.douira.glsl_transformer.test_util.*;
import io.github.douira.glsl_transformer.test_util.TestResourceManager.DirectoryLocation;

@ExtendWith({ SnapshotExtension.class })
public class ParsingTest extends TestWithSingleASTTransformer {
  private Expect expect;
  private Exception storeException;
  private SingleASTTransformer<JobParameters> manager;

  @BeforeEach
  void setupManager() {
    manager = new SingleASTTransformer<>((tu) -> {
      tu.parseAndInjectNode(manager, ASTInjectionPoint.BEFORE_ALL, "f;");
    });
  }

  @Test
  void testGetLexer() {
    assertNotNull(manager.getParser(), "It should have a parser");
  }

  @Test
  void testGetParser() {
    assertNotNull(manager.getParser(), "It should have a lexer");
  }

  void assertParseErrorType(Class<? extends RecognitionException> type, Executable executable, String message) {
    assertThrows(ParseCancellationException.class, () -> {
      try {
        executable.execute();
      } catch (ParseCancellationException exception) {
        storeException = exception;
        throw exception;
      } catch (ParsingException exception) {
        storeException = exception.getCause();
        throw exception.getCause();
      }
    });
    assertSame(type, storeException.getCause().getClass(),
        "It should throw a ParseCancellationException with the cause " + type.getSimpleName());
  }

  @Test
  void testTransform() {
    assertEquals(
        "f; a; b; c; d; ",
        manager.transform("a;//present\nb;c;d;"));

    assertParseErrorType(
        InputMismatchException.class, () -> manager.transform(
            "//present"),
        "It should throw on an incomplete input");
    assertParseErrorType(
        NoViableAltException.class, () -> manager.transform(
            "foo"),
        "It should throw when there is no viable alternative while parsing the input");
    assertParseErrorType(
        LexerNoViableAltException.class, () -> manager.transform(
            "§"),
        "It should throw when there is no viable alternative while tokenizing the input");

    // FailedPredicateException is difficult to test and may never actually occur
  }

  @Test
  @SnapshotName("testGlslangErrors")
  void testGlslangErrors() {
    class CollectingErrorListener extends BaseErrorListener {
      private List<String> errors = new ArrayList<>();

      @Override
      public void syntaxError(
          Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
          String msg, RecognitionException e) throws ParseCancellationException {
        errors.add(
            line + ":" + charPositionInLine + "; " +
                (offendingSymbol == null
                    ? "<no symbol>"
                    : offendingSymbol instanceof CommonToken token
                        ? token.toString(recognizer)
                        : offendingSymbol.toString())
                + "; " + msg + "; " +
                (e == null
                    ? "<no exception>"
                    : e.getClass().getSimpleName() + ":" + e.getMessage()));
      }
    }

    TestResourceManager
        .getDirectoryResources(DirectoryLocation.GLSLANG_TESTS)
        .forEach(resource -> {
          var t = new SingleASTTransformer<>(SingleASTTransformer.IDENTITY_TRANSFORMATION);
          t.setPrintType(PrintType.INDENTED);
          t.setThrowParseErrors(false);
          t.setSLLOnly();
          var collectingListener = new CollectingErrorListener();
          t.getLexer().addErrorListener(collectingListener);
          t.getParser().addErrorListener(collectingListener);
          t.getLexer().enableIncludeDirective = true;

          var content = resource.content();
          var expectScenario = expect.scenario(resource.getScenarioName());

          Exception astException = null;
          try {
            t.transform(content);
          } catch (Exception e) {
            astException = e;
          }

          expectScenario.toMatchSnapshot(
              SnapshotUtil.inputOutputSnapshot(
                  content,
                  String.join("\n", collectingListener.errors) +
                      (astException == null ? "" : "\n" + astException)));

        });
  }

  @Test
  void testWithJobParameters() {
    var man = new SingleASTTransformer<>(SingleASTTransformer.IDENTITY_TRANSFORMATION);
    assertNull(man.getJobParameters(), "It should start with no job parameters");
    var parameters = JobParameters.EMPTY;
    man.withJobParameters(parameters,
        () -> {
          assertSame(parameters, man.getJobParameters(), "It should contain the job parameters");
          return null;
        });
    assertNull(man.getJobParameters(), "It should delete the job parameters after use");
    var result = new Object();
    assertSame(result, man.withJobParameters(parameters,
        () -> {
          assertSame(parameters, man.getJobParameters(), "It should contain the job parameters again");
          return result;
        }), "It should return the value of the supplier function");
  }

  @Test
  void testLexerVersionOptionKeywords() {
    assertThrows(ParseCancellationException.class, () -> {
      p.parseSeparateExternalDeclaration("void foo(sampler2D sample) { }");
    }, "It should throw if keywords are used as identifiers.");
    assertThrows(ParseCancellationException.class, () -> {
      p.getLexer().version = Version.GLSL40;
      p.parseSeparateExternalDeclaration("void foo(sampler2D sample) { }");
    }, "It should throw if keywords are used as identifiers.");
    assertDoesNotThrow(() -> {
      p.getLexer().version = Version.GLSL33;
      p.parseSeparateExternalDeclaration("void foo(sampler2D sample) { }");
    }, "It should not throw if disabled keywords are used as identifiers.");
  }

  @Test
  void testLexerOptionalFeatures() {
    // Test for `enableCustomDirective`
    var customDirective = "#custom directive\n";
    assertThrows(ParseCancellationException.class, () -> {
      p.getLexer().enableCustomDirective = false;
      p.parseSeparateExternalDeclaration(customDirective);
    }, "It should throw if `enableCustomDirective` is not enabled.");
    assertDoesNotThrow(() -> {
      p.getLexer().enableCustomDirective = true;
      p.parseSeparateExternalDeclaration(customDirective);
    }, "It should not throw if `enableCustomDirective` is enabled.");

    // Test for `enableIncludeDirective`
    var includeDirective = "#include <file>\n";
    assertThrows(ParseCancellationException.class, () -> {
      p.getLexer().enableIncludeDirective = false;
      p.parseSeparateExternalDeclaration(includeDirective);
    }, "It should throw if `enableIncludeDirective` is not enabled.");
    assertDoesNotThrow(() -> {
      p.getLexer().enableIncludeDirective = true;
      p.parseSeparateExternalDeclaration(includeDirective);
    }, "It should not throw if `enableIncludeDirective` is enabled.");

    // Test for `enableStrings`
    var stringDeclaration = "void main() { foo(\"bar\"); }";
    assertThrows(ParseCancellationException.class, () -> {
      p.getLexer().enableStrings = false;
      p.parseSeparateExternalDeclaration(stringDeclaration);
    }, "It should throw if `enableStrings` is not enabled.");
    assertDoesNotThrow(() -> {
      p.getLexer().enableStrings = true;
      p.parseSeparateExternalDeclaration(stringDeclaration);
    }, "It should not throw if `enableStrings` is enabled.");

    // Test for `enableMeshShaders`
    assertThrows(ParseCancellationException.class, () -> {
      p.getLexer().enableMeshShaders = false;
      p.parseSeparateExternalDeclaration("taskNV out Task { vec3 origin; };");
    }, "It should throw on taskNV if enableMeshShaders is false.");
    assertDoesNotThrow(() -> {
      p.getLexer().enableMeshShaders = true;
      p.parseSeparateExternalDeclaration("taskNV out Task { vec3 origin; };");
    }, "It should not throw on taskNV if enableMeshShaders is false.");
  }
}
