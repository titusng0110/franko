
// Generated from Franko.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "FrankoParser.h"


/**
 * This interface defines an abstract listener for a parse tree produced by FrankoParser.
 */
class  FrankoListener : public antlr4::tree::ParseTreeListener {
public:

  virtual void enterProgram(FrankoParser::ProgramContext *ctx) = 0;
  virtual void exitProgram(FrankoParser::ProgramContext *ctx) = 0;

  virtual void enterStatement(FrankoParser::StatementContext *ctx) = 0;
  virtual void exitStatement(FrankoParser::StatementContext *ctx) = 0;

  virtual void enterVarDecl(FrankoParser::VarDeclContext *ctx) = 0;
  virtual void exitVarDecl(FrankoParser::VarDeclContext *ctx) = 0;

  virtual void enterDelStmt(FrankoParser::DelStmtContext *ctx) = 0;
  virtual void exitDelStmt(FrankoParser::DelStmtContext *ctx) = 0;

  virtual void enterAssignStmt(FrankoParser::AssignStmtContext *ctx) = 0;
  virtual void exitAssignStmt(FrankoParser::AssignStmtContext *ctx) = 0;

  virtual void enterArrayInitStmt(FrankoParser::ArrayInitStmtContext *ctx) = 0;
  virtual void exitArrayInitStmt(FrankoParser::ArrayInitStmtContext *ctx) = 0;

  virtual void enterArrayUninitStmt(FrankoParser::ArrayUninitStmtContext *ctx) = 0;
  virtual void exitArrayUninitStmt(FrankoParser::ArrayUninitStmtContext *ctx) = 0;

  virtual void enterLvalue(FrankoParser::LvalueContext *ctx) = 0;
  virtual void exitLvalue(FrankoParser::LvalueContext *ctx) = 0;

  virtual void enterExprStmt(FrankoParser::ExprStmtContext *ctx) = 0;
  virtual void exitExprStmt(FrankoParser::ExprStmtContext *ctx) = 0;

  virtual void enterMulDiv(FrankoParser::MulDivContext *ctx) = 0;
  virtual void exitMulDiv(FrankoParser::MulDivContext *ctx) = 0;

  virtual void enterAddSub(FrankoParser::AddSubContext *ctx) = 0;
  virtual void exitAddSub(FrankoParser::AddSubContext *ctx) = 0;

  virtual void enterAtomExpr(FrankoParser::AtomExprContext *ctx) = 0;
  virtual void exitAtomExpr(FrankoParser::AtomExprContext *ctx) = 0;

  virtual void enterAtom(FrankoParser::AtomContext *ctx) = 0;
  virtual void exitAtom(FrankoParser::AtomContext *ctx) = 0;

  virtual void enterType(FrankoParser::TypeContext *ctx) = 0;
  virtual void exitType(FrankoParser::TypeContext *ctx) = 0;


};

