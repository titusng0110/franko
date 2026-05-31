
// Generated from Franko.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "FrankoListener.h"


/**
 * This class provides an empty implementation of FrankoListener,
 * which can be extended to create a listener which only needs to handle a subset
 * of the available methods.
 */
class  FrankoBaseListener : public FrankoListener {
public:

  virtual void enterProgram(FrankoParser::ProgramContext * /*ctx*/) override { }
  virtual void exitProgram(FrankoParser::ProgramContext * /*ctx*/) override { }

  virtual void enterStatement(FrankoParser::StatementContext * /*ctx*/) override { }
  virtual void exitStatement(FrankoParser::StatementContext * /*ctx*/) override { }

  virtual void enterVarDecl(FrankoParser::VarDeclContext * /*ctx*/) override { }
  virtual void exitVarDecl(FrankoParser::VarDeclContext * /*ctx*/) override { }

  virtual void enterDelStmt(FrankoParser::DelStmtContext * /*ctx*/) override { }
  virtual void exitDelStmt(FrankoParser::DelStmtContext * /*ctx*/) override { }

  virtual void enterAssignStmt(FrankoParser::AssignStmtContext * /*ctx*/) override { }
  virtual void exitAssignStmt(FrankoParser::AssignStmtContext * /*ctx*/) override { }

  virtual void enterArrayInitStmt(FrankoParser::ArrayInitStmtContext * /*ctx*/) override { }
  virtual void exitArrayInitStmt(FrankoParser::ArrayInitStmtContext * /*ctx*/) override { }

  virtual void enterArrayUninitStmt(FrankoParser::ArrayUninitStmtContext * /*ctx*/) override { }
  virtual void exitArrayUninitStmt(FrankoParser::ArrayUninitStmtContext * /*ctx*/) override { }

  virtual void enterLvalue(FrankoParser::LvalueContext * /*ctx*/) override { }
  virtual void exitLvalue(FrankoParser::LvalueContext * /*ctx*/) override { }

  virtual void enterExprStmt(FrankoParser::ExprStmtContext * /*ctx*/) override { }
  virtual void exitExprStmt(FrankoParser::ExprStmtContext * /*ctx*/) override { }

  virtual void enterMulDiv(FrankoParser::MulDivContext * /*ctx*/) override { }
  virtual void exitMulDiv(FrankoParser::MulDivContext * /*ctx*/) override { }

  virtual void enterAddSub(FrankoParser::AddSubContext * /*ctx*/) override { }
  virtual void exitAddSub(FrankoParser::AddSubContext * /*ctx*/) override { }

  virtual void enterAtomExpr(FrankoParser::AtomExprContext * /*ctx*/) override { }
  virtual void exitAtomExpr(FrankoParser::AtomExprContext * /*ctx*/) override { }

  virtual void enterAtom(FrankoParser::AtomContext * /*ctx*/) override { }
  virtual void exitAtom(FrankoParser::AtomContext * /*ctx*/) override { }

  virtual void enterType(FrankoParser::TypeContext * /*ctx*/) override { }
  virtual void exitType(FrankoParser::TypeContext * /*ctx*/) override { }


  virtual void enterEveryRule(antlr4::ParserRuleContext * /*ctx*/) override { }
  virtual void exitEveryRule(antlr4::ParserRuleContext * /*ctx*/) override { }
  virtual void visitTerminal(antlr4::tree::TerminalNode * /*node*/) override { }
  virtual void visitErrorNode(antlr4::tree::ErrorNode * /*node*/) override { }

};

