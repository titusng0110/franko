// Generated from Franko.g4 by ANTLR 4.13.2
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link FrankoParser}.
 */
public interface FrankoListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link FrankoParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(FrankoParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(FrankoParser.ProgramContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(FrankoParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(FrankoParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#varDecl}.
	 * @param ctx the parse tree
	 */
	void enterVarDecl(FrankoParser.VarDeclContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#varDecl}.
	 * @param ctx the parse tree
	 */
	void exitVarDecl(FrankoParser.VarDeclContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#delStmt}.
	 * @param ctx the parse tree
	 */
	void enterDelStmt(FrankoParser.DelStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#delStmt}.
	 * @param ctx the parse tree
	 */
	void exitDelStmt(FrankoParser.DelStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#assignStmt}.
	 * @param ctx the parse tree
	 */
	void enterAssignStmt(FrankoParser.AssignStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#assignStmt}.
	 * @param ctx the parse tree
	 */
	void exitAssignStmt(FrankoParser.AssignStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#arrayInitStmt}.
	 * @param ctx the parse tree
	 */
	void enterArrayInitStmt(FrankoParser.ArrayInitStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#arrayInitStmt}.
	 * @param ctx the parse tree
	 */
	void exitArrayInitStmt(FrankoParser.ArrayInitStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#arrayUninitStmt}.
	 * @param ctx the parse tree
	 */
	void enterArrayUninitStmt(FrankoParser.ArrayUninitStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#arrayUninitStmt}.
	 * @param ctx the parse tree
	 */
	void exitArrayUninitStmt(FrankoParser.ArrayUninitStmtContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#lvalue}.
	 * @param ctx the parse tree
	 */
	void enterLvalue(FrankoParser.LvalueContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#lvalue}.
	 * @param ctx the parse tree
	 */
	void exitLvalue(FrankoParser.LvalueContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#exprStmt}.
	 * @param ctx the parse tree
	 */
	void enterExprStmt(FrankoParser.ExprStmtContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#exprStmt}.
	 * @param ctx the parse tree
	 */
	void exitExprStmt(FrankoParser.ExprStmtContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MulDiv}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterMulDiv(FrankoParser.MulDivContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MulDiv}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitMulDiv(FrankoParser.MulDivContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AddSub}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAddSub(FrankoParser.AddSubContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AddSub}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAddSub(FrankoParser.AddSubContext ctx);
	/**
	 * Enter a parse tree produced by the {@code UnaryMinus}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterUnaryMinus(FrankoParser.UnaryMinusContext ctx);
	/**
	 * Exit a parse tree produced by the {@code UnaryMinus}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitUnaryMinus(FrankoParser.UnaryMinusContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AtomExpr}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterAtomExpr(FrankoParser.AtomExprContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AtomExpr}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitAtomExpr(FrankoParser.AtomExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#atom}.
	 * @param ctx the parse tree
	 */
	void enterAtom(FrankoParser.AtomContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#atom}.
	 * @param ctx the parse tree
	 */
	void exitAtom(FrankoParser.AtomContext ctx);
	/**
	 * Enter a parse tree produced by {@link FrankoParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(FrankoParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link FrankoParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(FrankoParser.TypeContext ctx);
}