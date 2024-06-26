package clang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import clang.swig.ClangClog;
import clang.swig.ClangClogBuilder;
import clang.swig.VectorLong;
import clang.swig.VectorString;
import clang.swig.VectorVectorLong;
import lang.c.pat.ast.ASTNode;
import lang.c.pat.ast.PatLangParserSEP;
import se.lth.sep.Category;

public class ClangClogTest {
  private static String[] inputCFiles = {
    "tests/clang/evaluation/src/loop_depth/call_in_loop.c",
    "tests/clang/evaluation/src/loop_depth/duff.c",
    "tests/clang/evaluation/src/loop_depth/loops.c"
  };

  public static ClangClog newClog(String ... srcs) {
    try {
      System.loadLibrary("clangClogSWIG");
    } catch (SecurityException | UnsatisfiedLinkError e) {
      Assumptions.assumeTrue(false);
    }

    List<String> tests = List.of(srcs);

    ClangClogBuilder builder = new ClangClogBuilder(new VectorString(tests));
    ClangClog clog = builder.build();
    if (!clog.init()) {
      fail("Error initializing clang-clog");
    }
    return clog;
  }

  private static <T extends ASTNode> List<MatcherBuilder> genMatcherBuilders(String s, Category c) {
    List<MatcherBuilder> matchers = new ArrayList<>();
    List<T> res = TypeTest.parse(s, c);
    for (T internalNode : res) {
      matchers.addAll(ClangEvaluationContext.genMatcherBuilder(internalNode));
    }
    return matchers;
  }

  private static List<Map<String, ClangClog.Loc>> matchPatternOnFile(String pat, Category c, String srcFile) {
    ClangClog clog = newClog(srcFile);
    List<MatcherBuilder> matchers = genMatcherBuilders(pat, c);
    assertTrue(!matchers.isEmpty());
    MatcherBuilder first = matchers.iterator().next();
    SortedSet<String> metavars = first.bindings();
    Map<MatcherBuilder, Long> matchHandles = new HashMap<>();
    for (MatcherBuilder b : matchers) {
      if (b.bindings().isEmpty()) {
        b.bind("$__root");
        metavars.add("$__root");
      }
      System.out.println("Registering '" + b.generate() + "'");
      long h = clog.registerMatcher(b.generate(), true);
      matchHandles.put(b, h);
      //assertEquals(metavars, b.bindings());
    }

    clog.runGlobalMatchers();

    List<Map<String, ClangClog.Loc>> matchResults = new ArrayList<>();

    for (MatcherBuilder b : matchers) {
      long h = matchHandles.get(b);
      VectorVectorLong result = clog.matchFromRoot(h);

      for (VectorLong row : result) {
        Iterator<Long> rit = row.iterator();
        Iterator<String> mvit = metavars.iterator();

        Map<String, ClangClog.Loc> matchResult = new HashMap<>();

        while (rit.hasNext() && mvit.hasNext()) {
          long nodeId = rit.next();
          ClangClog.Loc srcLoc = clog.srcLocation(nodeId);

          matchResult.put(mvit.next(), srcLoc);
        }

        assertFalse(rit.hasNext());
        assertFalse(mvit.hasNext());

        matchResults.add(matchResult);
      }
    }

    return matchResults;
  }

  @Test
  public void test1() {
    ClangClog clog = newClog(inputCFiles);
    long declMatcherHandle = clog.registerMatcher("decl().bind(\"d\")", true);
    assertTrue(declMatcherHandle >= 0);
    long forStmtHandle = clog.registerMatcher("forStmt().bind(\"f\")", false);
    assertTrue(forStmtHandle >= 0);
    clog.runGlobalMatchers();

    VectorVectorLong declResult = clog.matchFromRoot((int)declMatcherHandle);

    // for (VectorLong row : declResult) {
    //   for (long e : row) {
    //      System.out.print(e + " ");
    //   }
    //   System.out.println();
    // }
  }

  static class Checker {
    private Iterator<Map<String, ClangClog.Loc>> iterator;
    private Map<String, ClangClog.Loc> currentMap;

    public static Checker begin(Collection<Map<String, ClangClog.Loc>> result) {
      Checker ret = new Checker();
      ret.iterator = result.iterator();
      ret.currentMap = ret.iterator.next();
      return ret;
    }

    public Checker check(String metavar,
                      long startLine,
                      long endLine) {

      assertTrue(currentMap.containsKey(metavar));
      ClangClog.Loc loc = currentMap.get(metavar);
      assertEquals(startLine, loc.getStartLine());
      assertEquals(endLine, loc.getEndLine());
      return this;
    }

    public Checker check(String metavar, long line) {
      return check(metavar, line, line);
    }

    public Checker check(String metavar,
                      long startLine,
                      long startCol,
                      long endLine,
                      long endCol) {
      check(metavar, startLine, endLine);
      assertEquals(startCol, currentMap.get(metavar).getStartCol());
      assertEquals(endCol, currentMap.get(metavar).getEndCol());
      return this;
    }

    public Checker next() {
      assertTrue(iterator.hasNext());
      currentMap = iterator.next();
      return this;
    }

    public void end() {
      assertFalse(iterator.hasNext());
    }
  }

  public static void dumpResults(Collection<Map<String, ClangClog.Loc>> results) {
    for (Map<String, ClangClog.Loc> r : results) {
      for (var entry : r.entrySet()) {
        String mv = entry.getKey();
        ClangClog.Loc loc = entry.getValue();
        System.out.println(mv + ": " + loc.getFilename() + ", " + loc.getStartLine() + ":" + loc.getStartCol() +
                           " - " + loc.getEndLine() + ":" + loc.getEndCol());
      }
    }
  }

  @Test
  public void test2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("int (*$f)(int);", PatLangParserSEP.n_declaration, "tests/clang/clog/src/funcptr.c");
    dumpResults(results);
    Checker c = Checker.begin(results);
    c.check("$f", 1, 1).next();
    c.check("$f", 5, 10, 5, 22).next();
    c.check("$f", 12, 12).end();
  }

  @Test
  public void test3() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("int (*(*$f)(int))(int);", PatLangParserSEP.n_declaration, "tests/clang/clog/src/funcptr.c");
    dumpResults(results);
    Checker c = Checker.begin(results);
    c.check("$f", 7, 7).next();
    c.check("$f", 13, 13).end();
  }

  @Test
  public void test4() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("int (*$a[10])(int);", PatLangParserSEP.n_declaration, "tests/clang/clog/src/funcptr.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$a", 16, 16)
      .next()
      .check("$a", 17, 17)
      .end();
  }

  @Test
  public void test5() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("while($cond) $body", PatLangParserSEP.n_statement, "tests/clang/clog/src/loops.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$body", 2, 6)
      .check("$cond", 2, 10, 2, 10)
      .next()
      .check("$body", 4, 4)
      .check("$cond", 3, 12, 3, 12)
      .end();
  }

  @Test
  public void test6() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("do $body while($cond);", PatLangParserSEP.n_statement, "tests/clang/clog/src/loops.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$body", 8, 10)
      .check("$cond", 10, 12, 10, 12)
      .next()
      .check("$body", 12, 6, 12, 6)
      .check("$cond", 12, 15, 12, 15)
      .end();
  }

  @Test
  public void test7() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("for ($init; $cond; $inc) $body", PatLangParserSEP.n_statement, "tests/clang/clog/src/loops.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$body", 17, 17)
      .check("$init", 17, 17)
      .check("$cond", 17, 17)
      .check("$inc", 17, 17)
      .end();
  }

  @Test
  public void test8() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("for (; ; ) $body", PatLangParserSEP.n_statement, "tests/clang/clog/src/loops.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$body", 20, 22)
      .end();
  }

  @Test
  public void test9() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("for ($init; ; ) $body", PatLangParserSEP.n_statement, "tests/clang/clog/src/loops.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$init", 28, 28)
      .check("$body", 28, 30)
      .end();
  }

  @Test
  public void test10() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("for (; $cond; ) $body", PatLangParserSEP.n_statement, "tests/clang/clog/src/loops.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$body", 32, 34)
      .check("$cond", 32, 32)
      .end();
  }


  @Test
  public void test11() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("for (; ; $inc) $body", PatLangParserSEP.n_statement, "tests/clang/clog/src/loops.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$body", 36, 38)
      .check("$inc", 36, 36)
      .end();
  }

  @Test
  public void test12() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("if ($cond) $then", PatLangParserSEP.n_statement, "tests/clang/clog/src/if.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$then", 2, 9)
      .check("$cond", 2, 2)
      .end();
  }

  @Test
  public void test13() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("if ($cond) $then else $else", PatLangParserSEP.n_statement, "tests/clang/clog/src/if.c");
    dumpResults(results);

    Checker.begin(results)
      .check("$then", 3, 5)
      .check("$else", 5, 7)
      .check("$cond", 3, 3)
      .end();
  }

  @Test
  public void testCompoundStmt() {
    // match compound statements with single substatement
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("{ $stmt }", PatLangParserSEP.n_statement, "tests/clang/clog/src/block.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$stmt", 7, 7)
      .end();

  }

  @Test
  public void testCompoundStmt2() {
    // match compound statements with precisely 2 substatements
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("{ $stmt1 $stmt2 }", PatLangParserSEP.n_statement, "tests/clang/clog/src/block.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$stmt1", 11, 11)
      .check("$stmt2", 12, 12)
      .end();

  }

  @Test
  public void testCompoundStmtGap() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("{ .. }", PatLangParserSEP.n_statement, "tests/clang/clog/src/block.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$__root", 1, 20)
      .next()
      .check("$__root", 3, 3)
      .next()
      .check("$__root", 4, 4)
      .next()
      .check("$__root", 6, 8)
      .next()
      .check("$__root", 10, 13)
      .next()
      .check("$__root", 15, 19)
      .end();
  }

  @Test
  public void testCompoundStmtEmpty() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("{ }", PatLangParserSEP.n_statement, "tests/clang/clog/src/block.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$__root", 3, 3)
      .next()
      .check("$__root", 4, 4)
      .end();
  }

  @Test
  public void testCompoundStmtGap2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("{ $stmt1 ..  $stmt2 .. $stmt3 .. $stmt4 }", PatLangParserSEP.n_statement, "tests/clang/clog/src/block.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$stmt1", 3, 3)
      .check("$stmt2", 4, 4)
      .check("$stmt3", 6, 8)
      .check("$stmt4", 10, 13)
      .next()
      .check("$stmt1", 3, 3)
      .check("$stmt2", 4, 4)
      .check("$stmt3", 6, 8)
      .check("$stmt4", 15, 19)
      .next()
      .check("$stmt1", 3, 3)
      .check("$stmt2", 4, 4)
      .check("$stmt3", 10, 13)
      .check("$stmt4", 15, 19)
      .next()
      .check("$stmt1", 3, 3)
      .check("$stmt2", 6, 8)
      .check("$stmt3", 10, 13)
      .check("$stmt4", 15, 19)
      .next()
      .check("$stmt1", 4, 4)
      .check("$stmt2", 6, 8)
      .check("$stmt3", 10, 13)
      .check("$stmt4", 15, 19)
      .end();
  }

  @Test
  public void testSwitchStmt() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("switch ($sw) { case $e1 : $stmt1 ..  default : $stmt2 .. case $e3 : break; }", PatLangParserSEP.n_statement, "tests/clang/clog/src/switch.c");
    dumpResults(results);

    Checker.begin(results)
      .check("$sw", 2, 2)
      .check("$e1", 3, 3)
      .check("$e3", 8, 8)
      .check("$stmt1", 3,3)
      .check("$stmt2", 7, 7)
      .end();
  }

  @Test
  public void testReturnStmt1() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("return;", PatLangParserSEP.n_statement, "tests/clang/clog/src/return.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$__root", 3, 3)
      .end();
  }

  @Test
  public void testReturnStmt2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("return $e;", PatLangParserSEP.n_statement, "tests/clang/clog/src/return.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$e", 9, 9)
      .end();
  }

  @Test
  public void testStructDecl() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("struct $s { int $f1; int $f2; };", PatLangParserSEP.n_declaration, "tests/clang/clog/src/struct.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$s", 1, 4)
      .check("$f1", 2, 2)
      .check("$f2", 3, 3)
      .end();
  }

  @Test
  public void testStructRef() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("struct $s $v;", PatLangParserSEP.n_declaration, "tests/clang/clog/src/struct.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$s", 1, 4)
      .check("$v", 1, 4)
      .next()
      .check("$s", 1, 4)
      .check("$v", 6, 6)
      .end();
  }

  @Test
  public void testStructRefPtr() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("struct $s *$v;", PatLangParserSEP.n_declaration, "tests/clang/clog/src/struct.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$s", 1, 4)
      .check("$v", 8, 8)
      .end();
  }

  @Test
  public void testFuncDecl1() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("int $f(int x);", PatLangParserSEP.n_declaration, "tests/clang/clog/src/fun.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$f", 1, 1)
      .end();
  }

  @Test
  public void testFuncDecl2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("int $f(int x, ..);", PatLangParserSEP.n_declaration, "tests/clang/clog/src/fun.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$f", 1, 1)
      .end();
  }

  @Test
  public void testFuncDef1() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("int f(int x) {..}", PatLangParserSEP.n_function_definition, "tests/clang/clog/src/fun.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$__root", 3, 3)
      .next()
      .check("$__root", 5, 7)
      .end();
  }

  @Test
  public void testFuncDef2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("int f(int x, ..) {..}", PatLangParserSEP.n_function_definition, "tests/clang/clog/src/fun.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$__root", 3, 3)
      .next()
      .check("$__root", 5, 7)
      .next()
      .check("$__root", 9, 10)
      .end();
  }

  @Test
  public void testDeclRefVar() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("y", PatLangParserSEP.n_expression, "tests/clang/clog/src/expr.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$__root", 3, 3)
      .end();
  }

  @Test
  public void testDeclRefFunc() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("foo", PatLangParserSEP.n_expression, "tests/clang/clog/src/expr.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$__root", 7, 7)
      .end();
  }

  @Test
  public void testCall() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("$f($a1)", PatLangParserSEP.n_expression, "tests/clang/clog/src/call.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$f", 6, 6)
      .check("$a1", 6, 6)
      .next()
      .check("$f", 7, 7)
      .check("$a1", 7, 7)
      .end();
  }

  @Test
  public void testKRDef1() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("void $f() {..}", PatLangParserSEP.n_function_definition,
        "tests/clang/clog/src/kr.c");
    dumpResults(results);
    dumpResults(results);
    Checker.begin(results)
      .check("$f", 5)
      .end();

  }


  @Test
  public void testKRDef2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile("void $f(void) {..}",
        PatLangParserSEP.n_function_definition,
        "tests/clang/clog/src/kr.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$f", 7)
      .end();
  }

  @Test
  public void testKRDecl1() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile(
        "void $f();", PatLangParserSEP.n_declaration,
        "tests/clang/clog/src/kr.c");
    dumpResults(results);
    Checker.begin(results)
        .check("$f", 1)
        .end();
  }


  @Test
  public void testKRDecl2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile(
        "void $f(void);", PatLangParserSEP.n_declaration,
        "tests/clang/clog/src/kr.c");
    dumpResults(results);
    Checker.begin(results)
        .check("$f", 2)
        .end();

  }

  @Test
  public void testMember1() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile(
        "$s.$f.$g", PatLangParserSEP.n_expression,
        "tests/clang/clog/src/member.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$f", 7)
      .check("$g", 2)
      .check("$s", 19)
      .next()
      .check("$f", 7)
      .check("$g", 2)
      .check("$s", 20)
      .next()
      .check("$f", 7)
      .check("$g", 3)
      .check("$s", 21)
      .next()
      .check("$f", 7)
      .check("$g", 3)
      .check("$s", 22)
      .end();
  }

  @Test
  public void testMember2() {
    List<Map<String, ClangClog.Loc>> results = matchPatternOnFile(
        "$s.$f->x", PatLangParserSEP.n_expression,
        "tests/clang/clog/src/member.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$s", 26)
      .check("$f", 11)
      .next()
      .check("$s", 27)
      .check("$f", 11)
      .end();
  }

  @Test
  public void testDecl() {
    List<Map<String, ClangClog.Loc>> results =
      matchPatternOnFile("int .., $x[$_], ..;",
                         PatLangParserSEP.n_statement,
                         "tests/clang/clog/src/decl.c");
    dumpResults(results);
    Checker.begin(results)
      .check("$x", 2)
      .end();
  }

  @Test
  public void testSingleDecl1() {
    List<Map<String, ClangClog.Loc>> results =
      matchPatternOnFile("int $x[$_]",
                         PatLangParserSEP.n_single_declaration,
                         "tests/clang/clog/src/decl.c");

    dumpResults(results);
    Checker.begin(results)
      .check("$x", 2, 4, 2, 14)
      .end();
  }

  @Test
  public void testSingleDecl2() {
    List<Map<String, ClangClog.Loc>> results =
      matchPatternOnFile("int $x",
                         PatLangParserSEP.n_single_declaration,
                         "tests/clang/clog/src/decl.c");

    dumpResults(results);
    Checker.begin(results)
      .check("$x", 2, 4, 2, 8)
      .end();
  }

  @Test
  public void testSingleDecl3() {
    List<Map<String, ClangClog.Loc>> results =
      matchPatternOnFile("int $x = $e",
                         PatLangParserSEP.n_single_declaration,
                         "tests/clang/clog/src/decl.c");

    dumpResults(results);
    Checker.begin(results)
      .check("$x", 3, 4, 3, 12)
      .check("$e", 3, 12, 3, 12)
      .next()
      .check("$x", 3, 4, 3, 23)
      .check("$e", 3, 19, 3, 23)
      .end();
  }

  @Test
  public void testSingleDecl4() {
    List<Map<String, ClangClog.Loc>> results =
      matchPatternOnFile("int *$p",
                         PatLangParserSEP.n_single_declaration,
                         "tests/clang/clog/src/decl.c");

    dumpResults(results);
    Checker.begin(results)
      .check("$p", 4, 4, 4, 9)
      .end();
  }

  @Test
  public void testSingleDecl5() {
    List<Map<String, ClangClog.Loc>> results =
      matchPatternOnFile("int **$p = $e",
                         PatLangParserSEP.n_single_declaration,
                         "tests/clang/clog/src/decl.c");

    dumpResults(results);
    Checker.begin(results)
      .check("$p", 4, 4, 4, 19)
      .check("$e", 4, 18, 4, 19)
      .end();
  }


  @Test
  public void testSingleDecl6() {
    List<Map<String, ClangClog.Loc>> results =
      matchPatternOnFile("$t **$p = $e",
                         PatLangParserSEP.n_single_declaration,
                         "tests/clang/clog/src/decl.c");
    Checker.begin(results)
      .check("$t", 0, 0, 0, 0)
      .check("$p", 4, 4, 4, 19)
      .check("$e", 4, 18, 4, 19)
      .end();
    dumpResults(results);

  }

}
