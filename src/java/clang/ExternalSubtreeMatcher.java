package clang;

import clang.swig.VectorLong;
import clang.swig.VectorVectorLong;
import eval.Control;
import eval.Tuple;
import static prof.Profile.profile;

public class ExternalSubtreeMatcher implements Control {
  private ClangEvaluationContext ctx;
  private Control cont;
  private MatcherInfo matcher;

  /**
     test - pairs of (column, tuple position) to test for equality
     assign - pairs of (column, tuple position) to assign
     consts - pairs of (column, value) of constants to assign
     arity - the arity of the external relation
  */
  public ExternalSubtreeMatcher(ClangEvaluationContext ctx,
                                MatcherInfo matcher,
                                Control cont) {
    this.ctx = ctx;
    this.cont = cont;
    this.matcher = matcher;
    this.ID = (cont.prettyPrint(0) + matcher.matcher).hashCode();
    if (matcher.kind != MatcherInfo.Kind.FROM)
      throw new RuntimeException("This should be an ExternalMatcher.");
  }

  @Override public void eval(Tuple t) {
    VectorVectorLong rows = ctx.lookupFrom(matcher.matcherId, t.get(matcher.fromNodeIdx));
    for (VectorLong row : rows) {
      for (int i = 0; i < matcher.resultMap.length; ++i) {
        if (matcher.resultMap[i] >= 0) {
          t.set(matcher.resultMap[i], row.get(i));
        }
      }

      cont.eval(t);
    }

    profile().addCounter("loop_count", String.format("%x", ID), rows.size());
  }

  private final int ID;

  @Override public String prettyPrint(int indent) {
    String s = Control.Util.indent(indent) + String.format("[%x] EXTERNAL FOR t IN %s:%s SUBTREE SUBTREE t[%d] WHERE ", ID, matcher.kind.name(), matcher.matcher, matcher.fromNodeIdx);
    for (int i = 0; i < matcher.resultMap.length; ++i) {
      if (matcher.resultMap[i] >= 0) {
        s += String.format("t[%d] := match[%d] ", matcher.resultMap[i], i);
      }
    }
    return s + "\n" + cont.prettyPrint(indent + 1);
  }
}
