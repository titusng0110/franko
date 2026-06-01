// Generated from Franko.g4 by ANTLR 4.13.2
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class FrankoParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, IDENTIFIER=22, INT_LITERAL=23, 
		COMMENT=24, WS=25;
	public static final int
		RULE_program = 0, RULE_statement = 1, RULE_varDecl = 2, RULE_delStmt = 3, 
		RULE_assignStmt = 4, RULE_arrayInitStmt = 5, RULE_arrayUninitStmt = 6, 
		RULE_lvalue = 7, RULE_exprStmt = 8, RULE_expr = 9, RULE_atom = 10, RULE_type = 11;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "statement", "varDecl", "delStmt", "assignStmt", "arrayInitStmt", 
			"arrayUninitStmt", "lvalue", "exprStmt", "expr", "atom", "type"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "';'", "'alloc'", "'del'", "'='", "'('", "')'", "'.'", "'uninit'", 
			"'['", "']'", "'-'", "'*'", "'/'", "'+'", "'int32_t'", "'uint32_t'", 
			"'float32_t'", "'char8_t'", "'array'", "'<'", "'>'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, "IDENTIFIER", 
			"INT_LITERAL", "COMMENT", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Franko.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public FrankoParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ProgramContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(FrankoParser.EOF, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public ProgramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_program; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterProgram(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitProgram(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitProgram(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ProgramContext program() throws RecognitionException {
		ProgramContext _localctx = new ProgramContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_program);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(27);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 13600812L) != 0)) {
				{
				{
				setState(24);
				statement();
				}
				}
				setState(29);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(30);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class StatementContext extends ParserRuleContext {
		public VarDeclContext varDecl() {
			return getRuleContext(VarDeclContext.class,0);
		}
		public DelStmtContext delStmt() {
			return getRuleContext(DelStmtContext.class,0);
		}
		public AssignStmtContext assignStmt() {
			return getRuleContext(AssignStmtContext.class,0);
		}
		public ArrayInitStmtContext arrayInitStmt() {
			return getRuleContext(ArrayInitStmtContext.class,0);
		}
		public ArrayUninitStmtContext arrayUninitStmt() {
			return getRuleContext(ArrayUninitStmtContext.class,0);
		}
		public ExprStmtContext exprStmt() {
			return getRuleContext(ExprStmtContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterStatement(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitStatement(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_statement);
		try {
			setState(50);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(32);
				varDecl();
				setState(33);
				match(T__0);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(35);
				delStmt();
				setState(36);
				match(T__0);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(38);
				assignStmt();
				setState(39);
				match(T__0);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(41);
				arrayInitStmt();
				setState(42);
				match(T__0);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(44);
				arrayUninitStmt();
				setState(45);
				match(T__0);
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(47);
				exprStmt();
				setState(48);
				match(T__0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class VarDeclContext extends ParserRuleContext {
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TerminalNode IDENTIFIER() { return getToken(FrankoParser.IDENTIFIER, 0); }
		public VarDeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_varDecl; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterVarDecl(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitVarDecl(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitVarDecl(this);
			else return visitor.visitChildren(this);
		}
	}

	public final VarDeclContext varDecl() throws RecognitionException {
		VarDeclContext _localctx = new VarDeclContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_varDecl);
		try {
			setState(59);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__14:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
				enterOuterAlt(_localctx, 1);
				{
				setState(52);
				type();
				setState(53);
				match(IDENTIFIER);
				}
				break;
			case T__1:
				enterOuterAlt(_localctx, 2);
				{
				setState(55);
				match(T__1);
				setState(56);
				type();
				setState(57);
				match(IDENTIFIER);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class DelStmtContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(FrankoParser.IDENTIFIER, 0); }
		public DelStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_delStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterDelStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitDelStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitDelStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final DelStmtContext delStmt() throws RecognitionException {
		DelStmtContext _localctx = new DelStmtContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_delStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(61);
			match(T__2);
			setState(62);
			match(IDENTIFIER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AssignStmtContext extends ParserRuleContext {
		public LvalueContext lvalue() {
			return getRuleContext(LvalueContext.class,0);
		}
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public AssignStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterAssignStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitAssignStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitAssignStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignStmtContext assignStmt() throws RecognitionException {
		AssignStmtContext _localctx = new AssignStmtContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_assignStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(64);
			lvalue();
			setState(65);
			match(T__3);
			setState(66);
			expr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayInitStmtContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(FrankoParser.IDENTIFIER, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public ArrayInitStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayInitStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterArrayInitStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitArrayInitStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitArrayInitStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayInitStmtContext arrayInitStmt() throws RecognitionException {
		ArrayInitStmtContext _localctx = new ArrayInitStmtContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_arrayInitStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(68);
			match(IDENTIFIER);
			setState(69);
			match(T__4);
			setState(70);
			expr(0);
			setState(71);
			match(T__5);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ArrayUninitStmtContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(FrankoParser.IDENTIFIER, 0); }
		public ArrayUninitStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayUninitStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterArrayUninitStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitArrayUninitStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitArrayUninitStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayUninitStmtContext arrayUninitStmt() throws RecognitionException {
		ArrayUninitStmtContext _localctx = new ArrayUninitStmtContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_arrayUninitStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(73);
			match(IDENTIFIER);
			setState(74);
			match(T__6);
			setState(75);
			match(T__7);
			setState(76);
			match(T__4);
			setState(77);
			match(T__5);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class LvalueContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(FrankoParser.IDENTIFIER, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public LvalueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_lvalue; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterLvalue(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitLvalue(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitLvalue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LvalueContext lvalue() throws RecognitionException {
		LvalueContext _localctx = new LvalueContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_lvalue);
		try {
			setState(85);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,3,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(79);
				match(IDENTIFIER);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(80);
				match(IDENTIFIER);
				setState(81);
				match(T__8);
				setState(82);
				expr(0);
				setState(83);
				match(T__9);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprStmtContext extends ParserRuleContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public ExprStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exprStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterExprStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitExprStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitExprStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprStmtContext exprStmt() throws RecognitionException {
		ExprStmtContext _localctx = new ExprStmtContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_exprStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(87);
			expr(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ExprContext extends ParserRuleContext {
		public ExprContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expr; }
	 
		public ExprContext() { }
		public void copyFrom(ExprContext ctx) {
			super.copyFrom(ctx);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class MulDivContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public MulDivContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterMulDiv(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitMulDiv(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitMulDiv(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AddSubContext extends ExprContext {
		public Token op;
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public AddSubContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterAddSub(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitAddSub(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitAddSub(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class UnaryMinusContext extends ExprContext {
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public UnaryMinusContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterUnaryMinus(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitUnaryMinus(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitUnaryMinus(this);
			else return visitor.visitChildren(this);
		}
	}
	@SuppressWarnings("CheckReturnValue")
	public static class AtomExprContext extends ExprContext {
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public AtomExprContext(ExprContext ctx) { copyFrom(ctx); }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterAtomExpr(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitAtomExpr(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitAtomExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprContext expr() throws RecognitionException {
		return expr(0);
	}

	private ExprContext expr(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExprContext _localctx = new ExprContext(_ctx, _parentState);
		ExprContext _prevctx = _localctx;
		int _startState = 18;
		enterRecursionRule(_localctx, 18, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(93);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__10:
				{
				_localctx = new UnaryMinusContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(90);
				match(T__10);
				setState(91);
				expr(4);
				}
				break;
			case T__4:
			case IDENTIFIER:
			case INT_LITERAL:
				{
				_localctx = new AtomExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(92);
				atom();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(103);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(101);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
					case 1:
						{
						_localctx = new MulDivContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(95);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(96);
						((MulDivContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__11 || _la==T__12) ) {
							((MulDivContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(97);
						expr(4);
						}
						break;
					case 2:
						{
						_localctx = new AddSubContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(98);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(99);
						((AddSubContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__10 || _la==T__13) ) {
							((AddSubContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(100);
						expr(3);
						}
						break;
					}
					} 
				}
				setState(105);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class AtomContext extends ParserRuleContext {
		public TerminalNode INT_LITERAL() { return getToken(FrankoParser.INT_LITERAL, 0); }
		public TerminalNode IDENTIFIER() { return getToken(FrankoParser.IDENTIFIER, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterAtom(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitAtom(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_atom);
		try {
			setState(117);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(106);
				match(INT_LITERAL);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(107);
				match(IDENTIFIER);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(108);
				match(IDENTIFIER);
				setState(109);
				match(T__8);
				setState(110);
				expr(0);
				setState(111);
				match(T__9);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(113);
				match(T__4);
				setState(114);
				expr(0);
				setState(115);
				match(T__5);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class TypeContext extends ParserRuleContext {
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_type; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterType(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitType(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeContext type() throws RecognitionException {
		TypeContext _localctx = new TypeContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_type);
		try {
			setState(128);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__14:
				enterOuterAlt(_localctx, 1);
				{
				setState(119);
				match(T__14);
				}
				break;
			case T__15:
				enterOuterAlt(_localctx, 2);
				{
				setState(120);
				match(T__15);
				}
				break;
			case T__16:
				enterOuterAlt(_localctx, 3);
				{
				setState(121);
				match(T__16);
				}
				break;
			case T__17:
				enterOuterAlt(_localctx, 4);
				{
				setState(122);
				match(T__17);
				}
				break;
			case T__18:
				enterOuterAlt(_localctx, 5);
				{
				setState(123);
				match(T__18);
				setState(124);
				match(T__19);
				setState(125);
				type();
				setState(126);
				match(T__20);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 9:
			return expr_sempred((ExprContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expr_sempred(ExprContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 3);
		case 1:
			return precpred(_ctx, 2);
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0001\u0019\u0083\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0001\u0000\u0005\u0000\u001a\b\u0000\n\u0000\f\u0000\u001d\t\u0000\u0001"+
		"\u0000\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0003\u00013\b\u0001\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0003\u0002<\b"+
		"\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001"+
		"\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001"+
		"\u0007\u0003\u0007V\b\u0007\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0003\t^\b\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0005"+
		"\tf\b\t\n\t\f\ti\t\t\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0001\n\u0003\nv\b\n\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0003\u000b\u0081\b\u000b\u0001\u000b\u0000\u0001\u0012\f"+
		"\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0000\u0002"+
		"\u0001\u0000\f\r\u0002\u0000\u000b\u000b\u000e\u000e\u0088\u0000\u001b"+
		"\u0001\u0000\u0000\u0000\u00022\u0001\u0000\u0000\u0000\u0004;\u0001\u0000"+
		"\u0000\u0000\u0006=\u0001\u0000\u0000\u0000\b@\u0001\u0000\u0000\u0000"+
		"\nD\u0001\u0000\u0000\u0000\fI\u0001\u0000\u0000\u0000\u000eU\u0001\u0000"+
		"\u0000\u0000\u0010W\u0001\u0000\u0000\u0000\u0012]\u0001\u0000\u0000\u0000"+
		"\u0014u\u0001\u0000\u0000\u0000\u0016\u0080\u0001\u0000\u0000\u0000\u0018"+
		"\u001a\u0003\u0002\u0001\u0000\u0019\u0018\u0001\u0000\u0000\u0000\u001a"+
		"\u001d\u0001\u0000\u0000\u0000\u001b\u0019\u0001\u0000\u0000\u0000\u001b"+
		"\u001c\u0001\u0000\u0000\u0000\u001c\u001e\u0001\u0000\u0000\u0000\u001d"+
		"\u001b\u0001\u0000\u0000\u0000\u001e\u001f\u0005\u0000\u0000\u0001\u001f"+
		"\u0001\u0001\u0000\u0000\u0000 !\u0003\u0004\u0002\u0000!\"\u0005\u0001"+
		"\u0000\u0000\"3\u0001\u0000\u0000\u0000#$\u0003\u0006\u0003\u0000$%\u0005"+
		"\u0001\u0000\u0000%3\u0001\u0000\u0000\u0000&\'\u0003\b\u0004\u0000\'"+
		"(\u0005\u0001\u0000\u0000(3\u0001\u0000\u0000\u0000)*\u0003\n\u0005\u0000"+
		"*+\u0005\u0001\u0000\u0000+3\u0001\u0000\u0000\u0000,-\u0003\f\u0006\u0000"+
		"-.\u0005\u0001\u0000\u0000.3\u0001\u0000\u0000\u0000/0\u0003\u0010\b\u0000"+
		"01\u0005\u0001\u0000\u000013\u0001\u0000\u0000\u00002 \u0001\u0000\u0000"+
		"\u00002#\u0001\u0000\u0000\u00002&\u0001\u0000\u0000\u00002)\u0001\u0000"+
		"\u0000\u00002,\u0001\u0000\u0000\u00002/\u0001\u0000\u0000\u00003\u0003"+
		"\u0001\u0000\u0000\u000045\u0003\u0016\u000b\u000056\u0005\u0016\u0000"+
		"\u00006<\u0001\u0000\u0000\u000078\u0005\u0002\u0000\u000089\u0003\u0016"+
		"\u000b\u00009:\u0005\u0016\u0000\u0000:<\u0001\u0000\u0000\u0000;4\u0001"+
		"\u0000\u0000\u0000;7\u0001\u0000\u0000\u0000<\u0005\u0001\u0000\u0000"+
		"\u0000=>\u0005\u0003\u0000\u0000>?\u0005\u0016\u0000\u0000?\u0007\u0001"+
		"\u0000\u0000\u0000@A\u0003\u000e\u0007\u0000AB\u0005\u0004\u0000\u0000"+
		"BC\u0003\u0012\t\u0000C\t\u0001\u0000\u0000\u0000DE\u0005\u0016\u0000"+
		"\u0000EF\u0005\u0005\u0000\u0000FG\u0003\u0012\t\u0000GH\u0005\u0006\u0000"+
		"\u0000H\u000b\u0001\u0000\u0000\u0000IJ\u0005\u0016\u0000\u0000JK\u0005"+
		"\u0007\u0000\u0000KL\u0005\b\u0000\u0000LM\u0005\u0005\u0000\u0000MN\u0005"+
		"\u0006\u0000\u0000N\r\u0001\u0000\u0000\u0000OV\u0005\u0016\u0000\u0000"+
		"PQ\u0005\u0016\u0000\u0000QR\u0005\t\u0000\u0000RS\u0003\u0012\t\u0000"+
		"ST\u0005\n\u0000\u0000TV\u0001\u0000\u0000\u0000UO\u0001\u0000\u0000\u0000"+
		"UP\u0001\u0000\u0000\u0000V\u000f\u0001\u0000\u0000\u0000WX\u0003\u0012"+
		"\t\u0000X\u0011\u0001\u0000\u0000\u0000YZ\u0006\t\uffff\uffff\u0000Z["+
		"\u0005\u000b\u0000\u0000[^\u0003\u0012\t\u0004\\^\u0003\u0014\n\u0000"+
		"]Y\u0001\u0000\u0000\u0000]\\\u0001\u0000\u0000\u0000^g\u0001\u0000\u0000"+
		"\u0000_`\n\u0003\u0000\u0000`a\u0007\u0000\u0000\u0000af\u0003\u0012\t"+
		"\u0004bc\n\u0002\u0000\u0000cd\u0007\u0001\u0000\u0000df\u0003\u0012\t"+
		"\u0003e_\u0001\u0000\u0000\u0000eb\u0001\u0000\u0000\u0000fi\u0001\u0000"+
		"\u0000\u0000ge\u0001\u0000\u0000\u0000gh\u0001\u0000\u0000\u0000h\u0013"+
		"\u0001\u0000\u0000\u0000ig\u0001\u0000\u0000\u0000jv\u0005\u0017\u0000"+
		"\u0000kv\u0005\u0016\u0000\u0000lm\u0005\u0016\u0000\u0000mn\u0005\t\u0000"+
		"\u0000no\u0003\u0012\t\u0000op\u0005\n\u0000\u0000pv\u0001\u0000\u0000"+
		"\u0000qr\u0005\u0005\u0000\u0000rs\u0003\u0012\t\u0000st\u0005\u0006\u0000"+
		"\u0000tv\u0001\u0000\u0000\u0000uj\u0001\u0000\u0000\u0000uk\u0001\u0000"+
		"\u0000\u0000ul\u0001\u0000\u0000\u0000uq\u0001\u0000\u0000\u0000v\u0015"+
		"\u0001\u0000\u0000\u0000w\u0081\u0005\u000f\u0000\u0000x\u0081\u0005\u0010"+
		"\u0000\u0000y\u0081\u0005\u0011\u0000\u0000z\u0081\u0005\u0012\u0000\u0000"+
		"{|\u0005\u0013\u0000\u0000|}\u0005\u0014\u0000\u0000}~\u0003\u0016\u000b"+
		"\u0000~\u007f\u0005\u0015\u0000\u0000\u007f\u0081\u0001\u0000\u0000\u0000"+
		"\u0080w\u0001\u0000\u0000\u0000\u0080x\u0001\u0000\u0000\u0000\u0080y"+
		"\u0001\u0000\u0000\u0000\u0080z\u0001\u0000\u0000\u0000\u0080{\u0001\u0000"+
		"\u0000\u0000\u0081\u0017\u0001\u0000\u0000\u0000\t\u001b2;U]egu\u0080";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}