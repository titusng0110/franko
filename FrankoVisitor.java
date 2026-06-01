// Generated from Franko.g4 by ANTLR 4.13.2
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link FrankoParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface FrankoVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link FrankoParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProgram(FrankoParser.ProgramContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(FrankoParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#separators}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSeparators(FrankoParser.SeparatorsContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#varDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDecl(FrankoParser.VarDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#delStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDelStmt(FrankoParser.DelStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#assignStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignStmt(FrankoParser.AssignStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#arrayInitStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayInitStmt(FrankoParser.ArrayInitStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#arrayUninitStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayUninitStmt(FrankoParser.ArrayUninitStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#arrayMemsetStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayMemsetStmt(FrankoParser.ArrayMemsetStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#arrayMemcpyStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayMemcpyStmt(FrankoParser.ArrayMemcpyStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#printStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrintStmt(FrankoParser.PrintStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#exprList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprList(FrankoParser.ExprListContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#lvalue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLvalue(FrankoParser.LvalueContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#exprStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprStmt(FrankoParser.ExprStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MulDiv}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMulDiv(FrankoParser.MulDivContext ctx);
	/**
	 * Visit a parse tree produced by the {@code AddSub}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAddSub(FrankoParser.AddSubContext ctx);
	/**
	 * Visit a parse tree produced by the {@code UnaryMinus}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryMinus(FrankoParser.UnaryMinusContext ctx);
	/**
	 * Visit a parse tree produced by the {@code AtomExpr}
	 * labeled alternative in {@link FrankoParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtomExpr(FrankoParser.AtomExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtom(FrankoParser.AtomContext ctx);
	/**
	 * Visit a parse tree produced by {@link FrankoParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(FrankoParser.TypeContext ctx);
}