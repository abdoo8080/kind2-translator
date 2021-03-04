package edu.uiowa.cs.clc.translator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;
import edu.uiowa.cs.clc.kind2.lustre.ComponentBuilder;
import edu.uiowa.cs.clc.kind2.lustre.ContractBodyBuilder;
import edu.uiowa.cs.clc.kind2.lustre.Expr;
import edu.uiowa.cs.clc.kind2.lustre.ExprUtil;
import edu.uiowa.cs.clc.kind2.lustre.ImportedComponentBuilder;
import edu.uiowa.cs.clc.kind2.lustre.Program;
import edu.uiowa.cs.clc.kind2.lustre.ProgramBuilder;
import edu.uiowa.cs.clc.kind2.lustre.Type;
import edu.uiowa.cs.clc.kind2.lustre.TypeUtil;
import edu.uiowa.cs.clc.translator.LustreParser.ArrayTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.AssertionContext;
import edu.uiowa.cs.clc.translator.LustreParser.BinaryExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.BoolExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.BoolTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.CallExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.CastExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.ConstantContext;
import edu.uiowa.cs.clc.translator.LustreParser.EnumTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.EquationContext;
import edu.uiowa.cs.clc.translator.LustreParser.ExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.FunctionContext;
import edu.uiowa.cs.clc.translator.LustreParser.IdExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.IfThenElseExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.IntExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.IntTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.NegateExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.NodeContext;
import edu.uiowa.cs.clc.translator.LustreParser.NotExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.PlainTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.PreExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.PropertyContext;
import edu.uiowa.cs.clc.translator.LustreParser.RealExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.RealTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.RealizabilityInputsContext;
import edu.uiowa.cs.clc.translator.LustreParser.RecordAccessExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.RecordExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.RecordTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.SubrangeTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.TupleExprContext;
import edu.uiowa.cs.clc.translator.LustreParser.TypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.TypedefContext;
import edu.uiowa.cs.clc.translator.LustreParser.UserTypeContext;
import edu.uiowa.cs.clc.translator.LustreParser.VarDeclGroupContext;

public class AntlrToLustre extends LustreBaseVisitor<Object> {
  private ProgramBuilder pb;

  AntlrToLustre() {
    pb = new ProgramBuilder();
  }

  public static Program parseLustreText(String values) {
    values = values.replaceAll("~", "_");
    values = values.replaceAll("state", "state_");
    CharStream charStream = CharStreams.fromString(values);
    LustreLexer lexer = new LustreLexer(charStream);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    AntlrToLustre visitor = new AntlrToLustre();
    return (Program) visitor.visit(new LustreParser(tokenStream).program());
  }

  @Override
  public Program visitProgram(LustreParser.ProgramContext ctx) {
    for (TypedefContext typedef : ctx.typedef()) {
      typedef.accept(this);
    }
    for (ConstantContext constant : ctx.constant()) {
      constant.accept(this);
    }
    for (NodeContext node : ctx.node()) {
      node.accept(this);
    }
    for (FunctionContext function : ctx.function()) {
      function.accept(this);
    }
    return pb.build();
  }

  @Override
  public Void visitTypedef(LustreParser.TypedefContext ctx) {
    pb.defineType(ctx.ID().getText(), (Type) ctx.topLevelType().accept(this));
    return null;
  }

  @Override
  public Void visitConstant(LustreParser.ConstantContext ctx) {
    pb.createConst(ctx.ID().toString(), type(ctx.type()), expr(ctx.expr()));
    return null;
  }

  @Override
  public Void visitNode(LustreParser.NodeContext ctx) {
    if (ctx.realizabilityInputs().isEmpty()) {
      ComponentBuilder cb = new ComponentBuilder(ctx.ID().getText());
      if (ctx.input != null) {
        for (VarDeclGroupContext group : ctx.input.varDeclGroup()) {
          for (TerminalNode input : group.ID()) {
            cb.createVarInput(input.getText(), type(group.type()));
          }
        }
      }
      if (ctx.output != null) {
        for (VarDeclGroupContext group : ctx.output.varDeclGroup()) {
          for (TerminalNode output : group.ID()) {
            cb.createVarOutput(output.getText(), type(group.type()));
          }
        }
      }
      if (ctx.local != null) {
        for (VarDeclGroupContext group : ctx.local.varDeclGroup()) {
          for (TerminalNode local : group.ID()) {
            cb.createLocalVar(local.getText(), type(group.type()));
          }
        }
      }
      for (AssertionContext assertion : ctx.assertion()) {
        cb.addAssertion((Expr) assertion.accept(this));
      }
      for (EquationContext equation : ctx.equation()) {
        for (TerminalNode local : equation.lhs().ID()) {
          cb.addEquation(ExprUtil.id(local.getText()), expr(equation.expr()));
        }
      }
      for (PropertyContext property : ctx.property()) {
        cb.addProperty((Expr) property.accept(this));
      }
      pb.addNode(cb);
      if (!ctx.main().isEmpty()) {
        pb.setMain(ctx.ID().getText());
      }
    } else {
      // convert node to an imported node with a contract
      Set<String> realInputs = new HashSet<>();
      for (RealizabilityInputsContext inputs : ctx.realizabilityInputs()) {
        for (TerminalNode input : inputs.ID()) {
          realInputs.add(input.getText());
        }
      }

      ImportedComponentBuilder icb = new ImportedComponentBuilder(ctx.ID().getText());

      // the variables specified by --%REALIZABLE are inputs and the rest are outputs
      for (VarDeclGroupContext group : ctx.input.varDeclGroup()) {
        for (TerminalNode input : group.ID()) {
          if (realInputs.contains(input.getText())) {
            icb.createVarInput(input.getText(), type(group.type()));
          } else {
            icb.createVarOutput(input.getText(), type(group.type()));
          }
        }
      }

      Map<String, Type> locals = new HashMap<>();

      // consider outputs here as local
      if (ctx.output != null) {
        for (VarDeclGroupContext group : ctx.output.varDeclGroup()) {
          for (TerminalNode output : group.ID()) {
            locals.put(output.getText(), type(group.type()));
          }
        }
      }

      if (ctx.local != null) {
        for (VarDeclGroupContext group : ctx.local.varDeclGroup()) {
          for (TerminalNode local : group.ID()) {
            locals.put(local.getText(), type(group.type()));
          }
        }
      }

      ContractBodyBuilder cbb = new ContractBodyBuilder();

      for (EquationContext equation : ctx.equation()) {
        for (var v : equation.lhs().ID()) {
          cbb.createVarDef(v.getText(), locals.get(v.getText()), expr(equation.expr()));
        }
      }
      for (AssertionContext assertion : ctx.assertion()) {
        cbb.assume((Expr) assertion.accept(this));
      }
      for (PropertyContext property : ctx.property()) {
        cbb.guarantee((Expr) property.accept(this));
      }
      icb.setContractBody(cbb);
      pb.importNode(icb);
    }
    return null;
  }

  @Override
  public Void visitFunction(LustreParser.FunctionContext ctx) {
    ImportedComponentBuilder cb = new ImportedComponentBuilder(ctx.ID().getText());

    for (VarDeclGroupContext group : ctx.input.varDeclGroup()) {
      for (TerminalNode input : group.ID()) {
        cb.createVarInput(input.getText(), type(group.type()));
      }
    }

    for (VarDeclGroupContext group : ctx.output.varDeclGroup()) {
      for (TerminalNode output : group.ID()) {
        cb.createVarInput(output.getText(), type(group.type()));
      }
    }

    pb.importFunction(cb);

    return null;
  }

  @Override
  public Type visitPlainType(PlainTypeContext ctx) {
    return type(ctx.type());
  }

  private Type type(TypeContext ctx) {
    return ctx == null ? null : (Type) ctx.accept(this);
  }

  @Override
  public Type visitIntType(IntTypeContext ctx) {
    return TypeUtil.INT;
  }

  @Override
  public Type visitSubrangeType(SubrangeTypeContext ctx) {
    return TypeUtil.intSubrange(ctx.bound(0).getText(), ctx.bound(1).getText());
  }

  @Override
  public Type visitBoolType(BoolTypeContext ctx) {
    return TypeUtil.BOOL;
  }

  @Override
  public Type visitRealType(RealTypeContext ctx) {
    return TypeUtil.REAL;
  }

  @Override
  public Type visitArrayType(ArrayTypeContext ctx) {
    try {
      int index = Integer.parseInt(ctx.INT().getText());
      return TypeUtil.array(type(ctx.type()), index);
    } catch (NumberFormatException nfe) {
      return null;
    }
  }

  @Override
  public Type visitRecordType(RecordTypeContext ctx) {
    RecordTypeContext rctx = (RecordTypeContext) ctx;
    Map<String, Type> fields = new HashMap<>();
    for (int i = 0; i < rctx.ID().size(); i++) {
      String field = rctx.ID(i).getText();
      fields.put(field, type(rctx.type(i)));
    }
    return TypeUtil.record(fields);
  }

  @Override
  public Type visitEnumType(EnumTypeContext ctx) {
    EnumTypeContext ectx = (EnumTypeContext) ctx;
    List<String> values = new ArrayList<>();
    for (TerminalNode node : ectx.ID()) {
      values.add(node.getText());
    }
    return TypeUtil.enumeration(values);
  }

  @Override
  public Type visitUserType(UserTypeContext ctx) {
    return TypeUtil.named(ctx.ID().getText());
  }

  private Expr expr(ExprContext ctx) {
    return ctx == null ? null : (Expr) ctx.accept(this);
  }

  @Override
  public Expr visitIdExpr(IdExprContext ctx) {
    return ExprUtil.id(ctx.ID().getText());
  }

  @Override
  public Expr visitIntExpr(IntExprContext ctx) {
    return ExprUtil.integer(ctx.INT().getText());
  }

  @Override
  public Expr visitRealExpr(RealExprContext ctx) {
    return ExprUtil.real(ctx.REAL().getText());
  }

  @Override
  public Expr visitBoolExpr(BoolExprContext ctx) {
    return ctx.BOOL().getText().equals("true") ? ExprUtil.TRUE : ExprUtil.FALSE;
  }

  @Override
  public Expr visitCastExpr(CastExprContext ctx) {
    return ctx.op.getText() == "real" ? ExprUtil.castReal(expr(ctx.expr()))
        : ExprUtil.castInt(expr(ctx.expr()));
  }

  @Override
  public Expr visitCallExpr(CallExprContext ctx) {
    List<Expr> args = new ArrayList<>();
    for (ExprContext arg : ctx.expr()) {
      args.add(expr(arg));
    }
    // use node call for now
    return ExprUtil.nodeCall(ExprUtil.id(ctx.ID().getText()), args);
  }

  @Override
  public Expr visitRecordAccessExpr(RecordAccessExprContext ctx) {
    return ExprUtil.recordAccess(expr(ctx.expr()), ctx.ID().getText());
  }

  @Override
  public Expr visitPreExpr(PreExprContext ctx) {
    return ExprUtil.pre(expr(ctx.expr()));
  }

  @Override
  public Expr visitNotExpr(NotExprContext ctx) {
    return ExprUtil.not(expr(ctx.expr()));
  }

  @Override
  public Expr visitNegateExpr(NegateExprContext ctx) {
    return ExprUtil.negative(expr(ctx.expr()));
  }

  @Override
  public Expr visitBinaryExpr(BinaryExprContext ctx) {
    String op = ctx.op.getText();
    Expr left = expr(ctx.expr(0));
    Expr right = expr(ctx.expr(1));
    switch (op) {
      case "*":
        return ExprUtil.multiply(left, right);
      case "/":
        return ExprUtil.divide(left, right);
      case "div":
        return ExprUtil.intDivide(left, right);
      case "mod":
        return ExprUtil.mod(left, right);
      case "+":
        return ExprUtil.plus(left, right);
      case "-":
        return ExprUtil.minus(left, right);
      case "<":
        return ExprUtil.less(left, right);
      case "<=":
        return ExprUtil.lessEqual(left, right);
      case ">":
        return ExprUtil.greater(left, right);
      case ">=":
        return ExprUtil.greaterEqual(left, right);
      case "=":
        return ExprUtil.equal(left, right);
      case "<>":
        return ExprUtil.notEqual(left, right);
      case "and":
        return ExprUtil.and(left, right);
      case "or":
        return ExprUtil.or(left, right);
      case "xor":
        return ExprUtil.xor(left, right);
      case "=>":
        return ExprUtil.implies(left, right);
      default: // "->":
        return ExprUtil.arrow(left, right);
    }
  }

  @Override
  public Expr visitIfThenElseExpr(IfThenElseExprContext ctx) {
    return ExprUtil.ite(expr(ctx.expr(0)), expr(ctx.expr(1)), expr(ctx.expr(2)));
  }

  @Override
  public Expr visitRecordExpr(RecordExprContext ctx) {
    Map<String, Expr> fields = new HashMap<>();
    for (int i = 0; i < ctx.expr().size(); i++) {
      String field = ctx.ID(i + 1).getText();
      fields.put(field, expr(ctx.expr(i)));
    }
    return ExprUtil.recordLiteral(ctx.ID(0).getText(), fields);
  }

  /*
   * @Override public Expr visitArrayExpr(ArrayExprContext ctx) { List<Expr> elements = new
   * ArrayList<>(); for (int i = 0; i < ctx.expr().size(); i++) { elements.add(expr(ctx.expr(i))); }
   * return ExprUtil.array(elements); }
   */

  @Override
  public Expr visitTupleExpr(TupleExprContext ctx) {
    // Treat singleton tuples as simply parentheses. This increases parsing
    // performance by not having to decide between parenExpr and tupleExpr.
    if (ctx.expr().size() == 1) {
      return expr(ctx.expr(0));
    }

    List<Expr> elements = new ArrayList<>();
    for (int i = 0; i < ctx.expr().size(); i++) {
      elements.add(expr(ctx.expr(i)));
    }
    return ExprUtil.array(elements);
  }

  @Override
  public Expr visitAssertion(LustreParser.AssertionContext ctx) {
    return expr(ctx.expr());
  }

  @Override
  public Expr visitProperty(LustreParser.PropertyContext ctx) {
    return ExprUtil.id(ctx.ID().getText());
  }
}
