@file:Suppress("ConstantConditionIf")

import TestPrograms.Companion.ignoreKeyword
import TestPrograms.Companion.testingKeyword
import arrow.core.getOrElse
import ic.org.CompileResult
import ic.org.WACCCompiler
import ic.org.util.containsAll
import ic.org.util.joinLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumingThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.fail
import java.io.File
import kotlin.time.ExperimentalTime
import reference.ReferenceCompilerAPI as Ref

/**
 * This test class will scan the wacc_examples directory, and attempt to compile all files, one
 * by one. If the compiler crashes with  a [NotImplementedError] exception (due to a [TODO] call)
 * then that compile is ignored.
 * Otherwise, we create a test and check what the return code and compiler output was. If it as
 * expected (read from the comments of the file itself) that test passes. For any other outcome
 * (a crash, or unexpected compiler output or error code) the test fails.
 *
 * Additionally, a file that does not have the string [testingKeyword] or [ignoreKeyword] in its
 * path inside the project will be ignored, this allowing us to select which files testing should
 * be enabled for.
 *
 * [ignoreKeyword] takes precedence. Ie, if a directory has [testingKeyword] but a subdirectory has
 * [ignoreKeyword], the directory will be tested except for the subdirectory/
 *
 */
@ExperimentalTime
class TestPrograms {

  companion object {
    // Testing constants
    private const val waccExamplesPath = "./wacc_examples/"
    private const val testOutputKeywords = false
    private const val testingKeyword = "TEST"
    private const val ignoreKeyword = "IGNORE"
    private const val testSemanticsOnly = false
    private const val input = "Hello"
  }

  private val waccFiles =
    File(waccExamplesPath).walk()
      .filter { it.isFile && ".wacc" in it.path }
      .filter { "TEST" in it.canonicalPath }
      .filterNot { "IGNORE" in it.canonicalPath }
      .map { it.asProgram() }
      .toList()

  /**
   * Tests whether [WACCCompiler] can compile  [program] with the expected compile output (so no
   * runtime execution tests) according to [testOutputKeywords].
   *
   * If [doCheckOnly] is false, it will also check the assembly output of the program is the same as the
   * reference compiler's.
   */
  @ExperimentalTime
  private fun test(program: WACCProgram, doCheckOnly: Boolean) {
    val filename = program.file.absolutePath
    val canonicalPath = program.file.canonicalPath
    val expRef = if (!doCheckOnly) CoroutineScope(Dispatchers.IO).async { Ref.ask(program.file, input) } else null
    val res: CompileResult = try {
      WACCCompiler(filename).compile(doCheckOnly)
    } catch (e: Throwable) {
      expRef?.cancel()
      assumeFalse(e is NotImplementedError)
      // If we hit an unimplemented case, ignore this test. Otherwise, we must have crashed
      // for some other reason. So fail the test case.
      System.err.println("Failed to compile $canonicalPath with exception:")
      fail(e)
    }
    assertEquals(program.expectedReturn, res.exitCode) {
      "Bad exit code while comipiling\n $canonicalPath,\n compiler output: \n${res.msg}"
    }
    assumingThat(testOutputKeywords) {
      assertTrue(res.msg.containsAll(program.expectedKeyWords))
    }
    // If we are not doing only checks, also check with the reference compiler assembly output
    if (!doCheckOnly) runBlocking {
      val (expectedAss, expectedOut, expectedCode) = expRef!!.await()
      val actualAss = res.out.getOrElse { fail("Compilation unsuccessful") }
      if (expectedAss != actualAss) {
        val (actualOut, actualCode) = Ref.run(actualAss, filename, input)
        println("Expected assembly:       Actual:\n")
        println(expectedAss.sideToSideWith(actualAss) + '\n')
        assertEquals(expectedOut, actualOut) { "Non matching program output for $canonicalPath" }
        // assertEquals(expectedCode, actualCode) { "Non matching program output code for $canonicalPath" } TODO check codes
      } else
        println("Expected and actual assembly outputs are identical")
    }
    println("Test successful (compiler exit code ${res.exitCode}). Compiler output:\n${res.msg}\n")
    println("Compiled WACC:\n${program.file.readText()}")
  }

  /**
   * Takes every [WACCProgram] in [waccFiles] and creates a [DynamicTest] with [test]
   * Every one of these [DynamicTest]s are the unit tests that show up in the report.
   *
   * It only checks a program syntactically and semantically.
   */
  @ExperimentalTime
  @TestFactory
  fun semanticallyCheckPrograms() = waccFiles.map { prog ->
    DynamicTest.dynamicTest(prog.file.canonicalPath) { test(prog, doCheckOnly = true) }
  }

  /**
   * Takes every [WACCProgram] in [waccFiles] and creates a [DynamicTest] with [test]
   * Every one of these [DynamicTest]s are the unit tests that show up in the report.
   *
   * It does only check tests that are supposed to preoduce assembly, ie, are valid.
   */
  @ExperimentalTime
  @TestFactory
  fun compileCheckPrograms() = waccFiles
    .filterNot { testSemanticsOnly }
    .filterNot { "invalid" in it.file.path }
    .map { prog ->
      DynamicTest.dynamicTest(prog.file.canonicalPath) { test(prog, doCheckOnly = false) }
    }
}
