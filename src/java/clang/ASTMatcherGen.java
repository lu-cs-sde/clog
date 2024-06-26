package clang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static clang.MatcherBuilder.*;

class MatcherException extends RuntimeException {
  public MatcherException(String s) {
    super(s);
  }
}

public class ASTMatcherGen implements ASTVisitor {
  HashMap<AST.Node, MatcherBuilder> matcherMap = new HashMap<>();

  @Override public void visit(AST.Node n) {
    // do nothing
    System.err.println("Unhandled node kind: " + n.getClass().getSimpleName());
  }

  public MatcherBuilder lookup(AST.Node n) {
    MatcherBuilder b =  matcherMap.get(n);
    if (b == null) {
      n.acceptPO(this);
      b = matcherMap.get(n);
      if (b == null) {
        throw new RuntimeException("Node should have been visited.");
      }
    }

    return b;
  }

  public static void buildBindings(AST.Node n, MatcherBuilder b) {
    if (n.bindsMetaVar()) {
      AST.MetaVar mv = n.boundMetaVar();
      // ignore wildcard metavariables
      if (!mv.isWildcard())
        b.bind(n.boundMetaVar().getName());
    }
  }

  @Override public void visit(AST.VarDecl n) {
    MatcherBuilder b = match("varDecl");
    if (n.isNamed()) {
      b.add(match("hasName", cst(n.getName())));
    }

    if (n.explicitType != null) {
      b.add(match("hasType", lookup(n.explicitType)));
    }

    if (n.getInit() == null) {
      b.add(unless(match("hasInitializer", anything())));
    } else {
      b.add(match("hasInitializer", lookup(n.getInit())));
    }

    buildBindings(n, b);

    matcherMap.put(n, b);
  }

  @Override public void visit(AST.ParmVarDecl p) {
    MatcherBuilder b = match("parmVarDecl");
    if (p.isNamed())
      b.add(match("hasName", cst(p.getName())));

    if (p.explicitType != null) {
      b.add(match("hasType", lookup(p.explicitType)));
    }

    if (p.bindsMetaVar()) {
      b.bind(p.boundMetaVar().getName());
    }

    matcherMap.put(p, b);
  }

  public static void processTypeQualifiers(AST.Type t, MatcherBuilder b) {
    for (AST.TypeQualifier q : t.typeQuals) {
      switch (q) {
      case CONST:
        b.add(match("isConstQualified"));
        break;
      case VOLATILE:
        b.add(match("isVolatileQualified"));
        break;
      default:
        throw new MatcherException("Unsupported qualifier " + q + ".");
      }
    }
  }

  @Override public void visit(AST.BuiltinType n) {
    MatcherBuilder b = match("qualType");

    for (AST.BuiltinTypeKind kind : n.kinds) {
      switch (kind) {
      case CHAR:
        b.add(match("isAnyCharacter"));
        break;
      case SIGNED:
        b.add(match("isSignedInteger"));
        break;
      case UNSIGNED:
        b.add(match("isUnsignedInteger"));
        break;
      case INT:
        b.add(match("isInteger"));
        break;
      case VOID:
        b.add(match("isVoid"));
        break;
      default:
        throw new MatcherException("Unsupported builtin type " + kind + ".");
      }
    }

    processTypeQualifiers(n, b);

    buildBindings(n, b);

    matcherMap.put(n, b);
  }

  @Override public void visit(AST.PointerType p) {
    MatcherBuilder b = match("qualType");
    b.add(match("pointsTo", lookup(p.getPointeeType())));
    processTypeQualifiers(p, b);

    buildBindings(p, b);

    matcherMap.put(p, b);
  }

  @Override public void visit(AST.ArrayType a) {
    MatcherBuilder b = match("arrayType");
    b.add(match("hasElementType", lookup(a.getElementType())));
    processTypeQualifiers(a, b);

    buildBindings(a, b);

    matcherMap.put(a, b);
  }

  @Override public void visit(AST.FunctionProtoType p) {
    MatcherBuilder b = match("functionProtoType");

    boolean seenGap = false;
    for (int i = 0; i < p.getNumParamType(); ++i) {
      AST.Type pt = p.getParamType(i);
      if (pt.isGap()) {
        seenGap = true;
        break;
      }
    }

    if (!seenGap) {
      b.add(match("parameterCountIs", integer(p.getNumParamType())));
      for (int i = 0; i < p.getNumParamType(); ++i) {
        AST.Type pt = p.getParamType(i);
        b.add(match("hasParameterType", integer(i), lookup(pt)));
      }
    } else {
      MatcherBuilder distinct = match("paramTypeDistinct");
      for (int i = 0; i < p.getNumParamType(); ++i) {
        AST.Type pt = p.getParamType(i);
        if (!pt.isGap()) {
          distinct.add(lookup(pt));
        }
      }
      b.add(distinct);
    }

    if (p.variadic) {
      b.add(match("isVariadic"));
    }

    b.add(match("hasReturnType", lookup(p.getReturnType())));

    buildBindings(p, b);

    matcherMap.put(p, ignoringParens(b));
  }

  @Override
  public void visit(AST.FunctionNoProtoType p) {
    MatcherBuilder b = match("functionNoProtoType");
    b.add(match("hasReturnType", lookup(p.getReturnType())));
    buildBindings(p, b);
    matcherMap.put(p, ignoringParens(b));
  }

  @Override public void visit(AST.RecordRefType r) {
    MatcherBuilder b = match("recordDecl");
    if (r.isNamed())
      b.add(match("hasName", cst(r.name)));
    b.add(matchForRecordKind(r.kind));

    buildBindings(r, b);

    matcherMap.put(r, b);
  }

  @Override public void visit(AST.TypedefType t) {
    if (t.bindsMetaVar()) {
      MatcherBuilder b = match("qualType");
      buildBindings(t, b);
      matcherMap.put(t, b);
    } else if (t.isNamed()) {
      MatcherBuilder b = match("typedefType",
          match("hasDeclaration", match("typedefDecl", match("hasName", cst(t.getName())))));
      buildBindings(t, b);
      matcherMap.put(t, b);
    } else {
      throw new RuntimeException("TypedefType is not yet handled.");
    }
  }

  @Override public void visit(AST.Expr e) {
    MatcherBuilder b = match("expr");

    buildBindings(e, b);

    matcherMap.put(e, b);
  }

  @Override public void visit(AST.Stmt s) {
    MatcherBuilder b = match("stmt");

    buildBindings(s, b);

    matcherMap.put(s, b);
  }

  @Override public void visit(AST.WhileStmt s) {
    MatcherBuilder b = match("whileStmt");
    b.add(match("hasCondition", lookup(s.getCond())));
    b.add(match("hasBody", lookup(s.getBody())));

    buildBindings(s, b);

    matcherMap.put(s, b);
  }

  @Override public void visit(AST.DoStmt s) {
    MatcherBuilder b = match("doStmt");
    b.add(match("hasCondition", lookup(s.getCond())));
    b.add(match("hasBody", lookup(s.getBody())));

    buildBindings(s, b);

    matcherMap.put(s, b);
  }

  @Override public void visit(AST.ForStmt s) {
    MatcherBuilder b = match("forStmt");

    s.getInit().ifPresentOrElse(i -> b.add(match("hasLoopInit", lookup(i))),
                                () -> b.add(unless(match("hasLoopInit", anything()))));
    s.getCond().ifPresentOrElse(c -> b.add(match("hasCondition", lookup(c))),
                                () -> b.add(unless(match("hasCondition", anything()))));
    s.getIncr().ifPresentOrElse(i -> b.add(match("hasIncrement", lookup(i))),
                                () -> b.add(unless(match("hasIncrement", anything()))));
    b.add(match("hasBody", lookup(s.getBody())));

    buildBindings(s, b);

    matcherMap.put(s, b);
  }

  @Override public void visit(AST.IfStmt s) {
    MatcherBuilder b = match("ifStmt");
    b.add(match("hasCondition", lookup(s.getCond())));
    b.add(match("hasThen", lookup(s.getThen())));
    if (s.hasElse) {
      b.add(match("hasElse", lookup(s.getElse())));
    } else {
      b.add(unless(match("hasElse", anything())));
    }


    buildBindings(s, b);

    matcherMap.put(s, b);
  }

  @Override public void visit(AST.CompoundStmt s) {
    MatcherBuilder sub = match("subStatementDistinct");
    boolean hasGaps = false;
    for (int i = 0; i < s.getNumStmts(); ++i) {
      AST.Stmt subStmt = s.getStmt(i);
      if (subStmt.isGap()) {
        hasGaps = true;
      } else {
        sub.add(lookup(subStmt));
      }
    }

    MatcherBuilder b = match("compoundStmt");
    if (!hasGaps) {
      // exact match
      b.add(match("statementCountIs", integer(s.getNumStmts())));
    }

    b.add(sub);

    buildBindings(s, b);
    matcherMap.put(s, b);
  }


  @Override public void visit(AST.ReturnStmt s) {
    MatcherBuilder b = match("returnStmt");

    s.getRetValue().ifPresentOrElse(r -> b.add(match("has", lookup(r))),
                                    () -> b.add(unless(match("has", match("expr")))));

    buildBindings(s, b);
    matcherMap.put(s, b);
  }

  @Override public void visit(AST.SwitchStmt s) {
    MatcherBuilder b = match("switchStmt");

    b.add(match("hasCondition", lookup(s.getExpr())));
    b.add(match("hasSwitchBody", lookup(s.getStmt())));

    buildBindings(s, b);
    matcherMap.put(s, b);
  }

  @Override public void visit(AST.CaseStmt s) {
    MatcherBuilder b = match("caseStmt");
    b.add(match("hasCaseConstant", lookup(s.getCaseConst())));
    b.add(match("hasSubStmt", lookup(s.getStmt())));

    buildBindings(s, b);
    matcherMap.put(s, b);
  }

  @Override public void visit(AST.DefaultStmt s) {
    MatcherBuilder b = match("defaultStmt");
    b.add(match("hasSubStmt", lookup(s.getStmt())));

    buildBindings(s, b);
    matcherMap.put(s, b);
  }

  @Override public void visit(AST.BreakStmt s) {
    MatcherBuilder b = match("breakStmt");

    buildBindings(s, b);
    matcherMap.put(s, b);
  }

  @Override public void visit(AST.DeclStmt s) {
    MatcherBuilder b = match("declStmt");

    if (!s.bindsMetaVar()) {
      MatcherBuilder decls = match("declDistinct");
      boolean hasGaps = false;
      for (int i = 0; i < s.getNumDecl(); i++) {
        if (s.getDecl(i).isGap()) {
          hasGaps = true;
        } else {
          decls.add(lookup(s.getDecl(i)));
        }
      }

      if (!hasGaps)
        b.add(match("declCountIs", integer(s.getNumDecl())));

      b.add(decls);
    }

    buildBindings(s, b);
    matcherMap.put(s, b);
  }

  @Override public void visit(AST.GotoStmt g) {
    MatcherBuilder b = match("gotoStmt");
    b.add(match("hasLabel", lookup(g.getLabel())));
    buildBindings(g, b);
    matcherMap.put(g, b);
  }

  @Override public void visit(AST.LabelStmt l) {
    MatcherBuilder b = match("labelStmt");
    b.add(match("hasDeclaration", lookup(l.getLabel())));
    b.add(match("has", lookup(l.getSubstmt())));
    buildBindings(l, b);
    matcherMap.put(l, b);
  }

  @Override public void visit(AST.FieldDecl s) {
    MatcherBuilder b = match("fieldDecl");
    if (s.isNamed()) {
      b.add(match("hasName", cst(s.getName())));
    }

    if (s.explicitType != null) {
      b.add(match("hasType", lookup(s.explicitType)));
    }

    if (s.isBitfield) {
      b.add(match("isBitField"));
      b.add(match("hasBitWidthExpr", lookup(s.getBitFieldSize())));
    }

    buildBindings(s, b);
    matcherMap.put(s, b);
  }

  static MatcherBuilder matchForRecordKind(AST.RecordKind k) {
    switch (k) {
    case STRUCT:
      return match("isStruct");

    case UNION:
      return match("isUnion");

    case CLASS:
      return match("isClass");

    default:
      throw new RuntimeException("Can't create matcher for enum yet.");
    }
  }

  @Override public void visit(AST.RecordDecl s) {
    MatcherBuilder b = match("recordDecl");
    if (s.isNamed()) {
      b.add(match("hasName", cst(s.getName())));
    }

    MatcherBuilder fields = match("fieldDistinct");
    boolean hasGaps = false;
    for (int i = 0; i < s.getNumDecl(); i++) {
      if (s.getDecl(i).isGap()) {
        hasGaps = true;
      } else {
        fields.add(lookup(s.getDecl(i)));
      }
    }

    b.add(matchForRecordKind(AST.RecordKind.fromTag(s.tagUsed)));

    if (s.completeDefinition) {
      b.add(match("isDefinition"));
    }

    b.add(fields);

    buildBindings(s, b);
    matcherMap.put(s, b);

  }

  @Override public void visit(AST.FunctionDecl d) {
    MatcherBuilder b = match("functionDecl");

    List<AST.ParmVarDecl> params = d.getParams();

    boolean seenGap = false;
    for (AST.ParmVarDecl pv : params) {
      if (pv.isGap())
        seenGap = true;
    }

    if (!seenGap) {
      b.add(match("parameterCountIs", integer(params.size())));
      for (int i = 0; i < params.size(); ++i) {
        b.add(match("hasParameter", integer(i), lookup(params.get(i))));
      }
    } else {
      MatcherBuilder distinct = match("paramDeclDistinct");
      for (AST.ParmVarDecl pv : params) {
        if (!pv.isGap()) {
          distinct.add(lookup(pv));
        }
      }
      b.add(distinct);
    }

    if (d.variadic) {
      b.add(match("isVariadic"));
    }

    // Disable matching the type.
    // In case where the pattern is <: $t $f(.., $pt $p, ..) :>
    // $pt will show up both in the matcher for the paramater (hasParameter)
    // and in the matcher for the type (functionProtoType). Since the matcher for the
    // type is independent from the matcher for the parameter, there will be
    // wrong matche, when $pt is bound to a type of a parameter and $p is
    // bound to the declaration of another parameter.
    // b.add(match("hasType", lookup(d.explicitType)));

    AST.FunctionType t = (AST.FunctionType) d.explicitType;
    b.add(match("hasReturnTypeLoc", match("loc", lookup(t.getReturnType()))));

    // Shallow hasType matcher, to discriminate between K&R style definitions, i.e. int f();
    // vs modern definition int f(void);
    if (t instanceof AST.FunctionProtoType) {
      b.add(match("hasType", ignoringParens(match("functionProtoType"))));
    } else {
      b.add(match("hasType", ignoringParens(match("functionNoProtoType"))));
    }

    if (d.getBody() != null) {
      b.add(match("hasBody", lookup(d.getBody())));
    } else {
      b.add(unless(match("hasBody", anything())));
    }

    buildBindings(d, b);
    matcherMap.put(d, b);
  }

  @Override public void visit(AST.LabelDecl l) {
    MatcherBuilder b = match("labelDecl");
    if (l.isNamed()) {
      b.add(match("hasName", cst(l.getName())));
    }

    buildBindings(l, b);
    matcherMap.put(l, b);
  }

  @Override public void visit(AST.DeclRefExpr e) {
    MatcherBuilder b = match("declRefExpr");

    b.add(match("to", match("namedDecl", match("hasName", cst(e.getName())))));

    buildBindings(e, b);
    matcherMap.put(e, b);
  }

  @Override public void visit(AST.CallExpr c) {
    MatcherBuilder b = match("callExpr");
    b.add(match("callee", lookup(c.getCallee())));

    boolean seenGap = false;
    for (int i = 0; i < c.getNumArgs(); ++i) {
      if (c.getArg(i).isGap()) {
        seenGap = true;
        break;
      }
    }

    if (seenGap) {
      MatcherBuilder distinct = match("argumentDistinct");
      for (int i = 0; i < c.getNumArgs(); ++i) {
        AST.Expr arg = c.getArg(i);
        if (!arg.isGap())
          distinct.add(lookup(arg));
      }
      b.add(distinct);
    } else {
      b.add(match("argumentCountIs", integer(c.getNumArgs())));
      for (int i = 0; i < c.getNumArgs(); ++i) {
        b.add(match("hasArgument", integer(i), lookup(c.getArg(i))));
      }
    }
    buildBindings(c, b);
    matcherMap.put(c, b);
  }

  @Override public void visit(AST.BinaryOperator op) {
    MatcherBuilder b = match("binaryOperator", match("hasAnyOperatorName", cst(op.opcode)));
    b.add(match("hasLHS", lookup(op.getLHS())));
    b.add(match("hasRHS", lookup(op.getRHS())));
    buildBindings(op, b);
    matcherMap.put(op, b);
  }

  @Override public void visit(AST.UnaryOperator op) {
    MatcherBuilder b = match("unaryOperator", match("hasOperatorName", cst(op.opcode)));
    b.add(match("hasUnaryOperand", lookup(op.getOperand())));
    buildBindings(op, b);
    matcherMap.put(op, b);
  }

  @Override public void visit(AST.ArraySubscriptExpr op) {
    MatcherBuilder b = match("arraySubscriptExpr",
                             match("hasLHS", lookup(op.getLHS())),
                             match("hasRHS", lookup(op.getRHS())));
    buildBindings(op, b);
    matcherMap.put(op, b);
  }

  @Override public void visit(AST.CStyleCastExpr op) {
    MatcherBuilder b = match("cStyleCastExpr",
                             match("hasDestinationType", lookup(op.getDestType())),
                             match("has", lookup(op.getOperand())));
    buildBindings(op, b);
    matcherMap.put(op, b);
  }

  @Override public void visit(AST.MemberExpr op) {
    MatcherBuilder b = match("memberExpr");
    if (op.isArrow)
      b.add(match("isArrow"));
    else
      b.add(unless(match("isArrow")));

    b.add(match("has", lookup(op.getBase())));
    b.add(match("member", lookup(op.getMember())));

    buildBindings(op, b);
    matcherMap.put(op, b);
  }

  @Override public void visit(AST.SizeofExpr op) {
    MatcherBuilder b = match("sizeOfExpr");
    b.add(match("has", lookup(op.getChild(0))));

    buildBindings(op, b);
    matcherMap.put(op, b);
  }

  @Override public void visit(AST.IntegerLiteral op) {
    MatcherBuilder b = match("integerLiteral", match("equals", integer(Long.decode(op.value))));
    buildBindings(op, b);
    matcherMap.put(op, b);

  }

  @Override public void visit(AST.FloatingLiteral op) {
    MatcherBuilder b = match("floatingLiteral", match("equals", floating(Double.parseDouble(op.value))));
    buildBindings(op, b);
    matcherMap.put(op, b);
  }
}
