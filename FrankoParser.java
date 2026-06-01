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
		ALLOC=1, DEL=2, PRINT=3, UNINIT=4, MEMSET=5, MEMCPY=6, INT32_T=7, UINT32_T=8, 
		FLOAT32_T=9, CHAR8_T=10, ARRAY=11, ASSIGN=12, PLUS=13, MINUS=14, STAR=15, 
		SLASH=16, DOT=17, COMMA=18, SEMI=19, LT=20, GT=21, LPAREN=22, RPAREN=23, 
		LBRACK=24, RBRACK=25, IDENTIFIER=26, INT_LITERAL=27, COMMENT=28, WS=29, 
		NEWLINE=30;
	public static final int
		RULE_program = 0, RULE_statement = 1, RULE_separators = 2, RULE_varDecl = 3, 
		RULE_delStmt = 4, RULE_assignStmt = 5, RULE_arrayInitStmt = 6, RULE_arrayUninitStmt = 7, 
		RULE_arrayMemsetStmt = 8, RULE_arrayMemcpyStmt = 9, RULE_printStmt = 10, 
		RULE_exprList = 11, RULE_lvalue = 12, RULE_exprStmt = 13, RULE_expr = 14, 
		RULE_atom = 15, RULE_type = 16;
	private static String[] makeRuleNames() {
		return new String[] {
			"program", "statement", "separators", "varDecl", "delStmt", "assignStmt", 
			"arrayInitStmt", "arrayUninitStmt", "arrayMemsetStmt", "arrayMemcpyStmt", 
			"printStmt", "exprList", "lvalue", "exprStmt", "expr", "atom", "type"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'alloc'", "'del'", "'print'", "'uninit'", "'memset'", "'memcpy'", 
			"'int32_t'", "'uint32_t'", "'float32_t'", "'char8_t'", "'array'", "'='", 
			"'+'", "'-'", "'*'", "'/'", "'.'", "','", "';'", "'<'", "'>'", "'('", 
			"')'", "'['", "']'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "ALLOC", "DEL", "PRINT", "UNINIT", "MEMSET", "MEMCPY", "INT32_T", 
			"UINT32_T", "FLOAT32_T", "CHAR8_T", "ARRAY", "ASSIGN", "PLUS", "MINUS", 
			"STAR", "SLASH", "DOT", "COMMA", "SEMI", "LT", "GT", "LPAREN", "RPAREN", 
			"LBRACK", "RBRACK", "IDENTIFIER", "INT_LITERAL", "COMMENT", "WS", "NEWLINE"
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
		public List<SeparatorsContext> separators() {
			return getRuleContexts(SeparatorsContext.class);
		}
		public SeparatorsContext separators(int i) {
			return getRuleContext(SeparatorsContext.class,i);
		}
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
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(37);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(34);
					separators();
					}
					} 
				}
				setState(39);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			}
			setState(53);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 205541262L) != 0)) {
				{
				setState(40);
				statement();
				setState(50);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
				while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
					if ( _alt==1 ) {
						{
						{
						setState(42); 
						_errHandler.sync(this);
						_la = _input.LA(1);
						do {
							{
							{
							setState(41);
							separators();
							}
							}
							setState(44); 
							_errHandler.sync(this);
							_la = _input.LA(1);
						} while ( _la==SEMI || _la==NEWLINE );
						setState(46);
						statement();
						}
						} 
					}
					setState(52);
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,2,_ctx);
				}
				}
			}

			setState(58);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==SEMI || _la==NEWLINE) {
				{
				{
				setState(55);
				separators();
				}
				}
				setState(60);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(61);
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
		public ArrayMemsetStmtContext arrayMemsetStmt() {
			return getRuleContext(ArrayMemsetStmtContext.class,0);
		}
		public ArrayMemcpyStmtContext arrayMemcpyStmt() {
			return getRuleContext(ArrayMemcpyStmtContext.class,0);
		}
		public PrintStmtContext printStmt() {
			return getRuleContext(PrintStmtContext.class,0);
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
			setState(72);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(63);
				varDecl();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(64);
				delStmt();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(65);
				assignStmt();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(66);
				arrayInitStmt();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(67);
				arrayUninitStmt();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(68);
				arrayMemsetStmt();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(69);
				arrayMemcpyStmt();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(70);
				printStmt();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(71);
				exprStmt();
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
	public static class SeparatorsContext extends ParserRuleContext {
		public List<TerminalNode> SEMI() { return getTokens(FrankoParser.SEMI); }
		public TerminalNode SEMI(int i) {
			return getToken(FrankoParser.SEMI, i);
		}
		public List<TerminalNode> NEWLINE() { return getTokens(FrankoParser.NEWLINE); }
		public TerminalNode NEWLINE(int i) {
			return getToken(FrankoParser.NEWLINE, i);
		}
		public SeparatorsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_separators; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterSeparators(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitSeparators(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitSeparators(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SeparatorsContext separators() throws RecognitionException {
		SeparatorsContext _localctx = new SeparatorsContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_separators);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(75); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(74);
					_la = _input.LA(1);
					if ( !(_la==SEMI || _la==NEWLINE) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(77); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,6,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
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
		public TerminalNode ALLOC() { return getToken(FrankoParser.ALLOC, 0); }
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
		enterRule(_localctx, 6, RULE_varDecl);
		try {
			setState(86);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case INT32_T:
			case UINT32_T:
			case FLOAT32_T:
			case CHAR8_T:
			case ARRAY:
				enterOuterAlt(_localctx, 1);
				{
				setState(79);
				type();
				setState(80);
				match(IDENTIFIER);
				}
				break;
			case ALLOC:
				enterOuterAlt(_localctx, 2);
				{
				setState(82);
				match(ALLOC);
				setState(83);
				type();
				setState(84);
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
		public TerminalNode DEL() { return getToken(FrankoParser.DEL, 0); }
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
		enterRule(_localctx, 8, RULE_delStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(88);
			match(DEL);
			setState(89);
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
		public TerminalNode ASSIGN() { return getToken(FrankoParser.ASSIGN, 0); }
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
		enterRule(_localctx, 10, RULE_assignStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(91);
			lvalue();
			setState(92);
			match(ASSIGN);
			setState(93);
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
		public TerminalNode LPAREN() { return getToken(FrankoParser.LPAREN, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(FrankoParser.RPAREN, 0); }
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
		enterRule(_localctx, 12, RULE_arrayInitStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(95);
			match(IDENTIFIER);
			setState(96);
			match(LPAREN);
			setState(97);
			expr(0);
			setState(98);
			match(RPAREN);
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
		public TerminalNode DOT() { return getToken(FrankoParser.DOT, 0); }
		public TerminalNode UNINIT() { return getToken(FrankoParser.UNINIT, 0); }
		public TerminalNode LPAREN() { return getToken(FrankoParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(FrankoParser.RPAREN, 0); }
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
		enterRule(_localctx, 14, RULE_arrayUninitStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(100);
			match(IDENTIFIER);
			setState(101);
			match(DOT);
			setState(102);
			match(UNINIT);
			setState(103);
			match(LPAREN);
			setState(104);
			match(RPAREN);
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
	public static class ArrayMemsetStmtContext extends ParserRuleContext {
		public TerminalNode IDENTIFIER() { return getToken(FrankoParser.IDENTIFIER, 0); }
		public TerminalNode DOT() { return getToken(FrankoParser.DOT, 0); }
		public TerminalNode MEMSET() { return getToken(FrankoParser.MEMSET, 0); }
		public TerminalNode LPAREN() { return getToken(FrankoParser.LPAREN, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RPAREN() { return getToken(FrankoParser.RPAREN, 0); }
		public ArrayMemsetStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayMemsetStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterArrayMemsetStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitArrayMemsetStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitArrayMemsetStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayMemsetStmtContext arrayMemsetStmt() throws RecognitionException {
		ArrayMemsetStmtContext _localctx = new ArrayMemsetStmtContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_arrayMemsetStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(106);
			match(IDENTIFIER);
			setState(107);
			match(DOT);
			setState(108);
			match(MEMSET);
			setState(109);
			match(LPAREN);
			setState(110);
			expr(0);
			setState(111);
			match(RPAREN);
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
	public static class ArrayMemcpyStmtContext extends ParserRuleContext {
		public List<TerminalNode> IDENTIFIER() { return getTokens(FrankoParser.IDENTIFIER); }
		public TerminalNode IDENTIFIER(int i) {
			return getToken(FrankoParser.IDENTIFIER, i);
		}
		public TerminalNode DOT() { return getToken(FrankoParser.DOT, 0); }
		public TerminalNode MEMCPY() { return getToken(FrankoParser.MEMCPY, 0); }
		public TerminalNode LPAREN() { return getToken(FrankoParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(FrankoParser.RPAREN, 0); }
		public ArrayMemcpyStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayMemcpyStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterArrayMemcpyStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitArrayMemcpyStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitArrayMemcpyStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayMemcpyStmtContext arrayMemcpyStmt() throws RecognitionException {
		ArrayMemcpyStmtContext _localctx = new ArrayMemcpyStmtContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_arrayMemcpyStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(113);
			match(IDENTIFIER);
			setState(114);
			match(DOT);
			setState(115);
			match(MEMCPY);
			setState(116);
			match(LPAREN);
			setState(117);
			match(IDENTIFIER);
			setState(118);
			match(RPAREN);
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
	public static class PrintStmtContext extends ParserRuleContext {
		public TerminalNode PRINT() { return getToken(FrankoParser.PRINT, 0); }
		public TerminalNode LPAREN() { return getToken(FrankoParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(FrankoParser.RPAREN, 0); }
		public ExprListContext exprList() {
			return getRuleContext(ExprListContext.class,0);
		}
		public PrintStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_printStmt; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterPrintStmt(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitPrintStmt(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitPrintStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PrintStmtContext printStmt() throws RecognitionException {
		PrintStmtContext _localctx = new PrintStmtContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_printStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(120);
			match(PRINT);
			setState(121);
			match(LPAREN);
			setState(123);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & 205537280L) != 0)) {
				{
				setState(122);
				exprList();
				}
			}

			setState(125);
			match(RPAREN);
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
	public static class ExprListContext extends ParserRuleContext {
		public List<ExprContext> expr() {
			return getRuleContexts(ExprContext.class);
		}
		public ExprContext expr(int i) {
			return getRuleContext(ExprContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(FrankoParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(FrankoParser.COMMA, i);
		}
		public ExprListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_exprList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).enterExprList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof FrankoListener ) ((FrankoListener)listener).exitExprList(this);
		}
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof FrankoVisitor ) return ((FrankoVisitor<? extends T>)visitor).visitExprList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExprListContext exprList() throws RecognitionException {
		ExprListContext _localctx = new ExprListContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_exprList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(127);
			expr(0);
			setState(132);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==COMMA) {
				{
				{
				setState(128);
				match(COMMA);
				setState(129);
				expr(0);
				}
				}
				setState(134);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
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
		public TerminalNode LBRACK() { return getToken(FrankoParser.LBRACK, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RBRACK() { return getToken(FrankoParser.RBRACK, 0); }
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
		enterRule(_localctx, 24, RULE_lvalue);
		try {
			setState(141);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,10,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(135);
				match(IDENTIFIER);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(136);
				match(IDENTIFIER);
				setState(137);
				match(LBRACK);
				setState(138);
				expr(0);
				setState(139);
				match(RBRACK);
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
		enterRule(_localctx, 26, RULE_exprStmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(143);
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
		public TerminalNode STAR() { return getToken(FrankoParser.STAR, 0); }
		public TerminalNode SLASH() { return getToken(FrankoParser.SLASH, 0); }
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
		public TerminalNode PLUS() { return getToken(FrankoParser.PLUS, 0); }
		public TerminalNode MINUS() { return getToken(FrankoParser.MINUS, 0); }
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
		public TerminalNode MINUS() { return getToken(FrankoParser.MINUS, 0); }
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
		int _startState = 28;
		enterRecursionRule(_localctx, 28, RULE_expr, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(149);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case MINUS:
				{
				_localctx = new UnaryMinusContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(146);
				match(MINUS);
				setState(147);
				expr(4);
				}
				break;
			case LPAREN:
			case IDENTIFIER:
			case INT_LITERAL:
				{
				_localctx = new AtomExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(148);
				atom();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(159);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,13,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(157);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,12,_ctx) ) {
					case 1:
						{
						_localctx = new MulDivContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(151);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(152);
						((MulDivContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==STAR || _la==SLASH) ) {
							((MulDivContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(153);
						expr(4);
						}
						break;
					case 2:
						{
						_localctx = new AddSubContext(new ExprContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expr);
						setState(154);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(155);
						((AddSubContext)_localctx).op = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==PLUS || _la==MINUS) ) {
							((AddSubContext)_localctx).op = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(156);
						expr(3);
						}
						break;
					}
					} 
				}
				setState(161);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,13,_ctx);
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
		public TerminalNode LBRACK() { return getToken(FrankoParser.LBRACK, 0); }
		public ExprContext expr() {
			return getRuleContext(ExprContext.class,0);
		}
		public TerminalNode RBRACK() { return getToken(FrankoParser.RBRACK, 0); }
		public TerminalNode LPAREN() { return getToken(FrankoParser.LPAREN, 0); }
		public TerminalNode RPAREN() { return getToken(FrankoParser.RPAREN, 0); }
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
		enterRule(_localctx, 30, RULE_atom);
		try {
			setState(173);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,14,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(162);
				match(INT_LITERAL);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(163);
				match(IDENTIFIER);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(164);
				match(IDENTIFIER);
				setState(165);
				match(LBRACK);
				setState(166);
				expr(0);
				setState(167);
				match(RBRACK);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(169);
				match(LPAREN);
				setState(170);
				expr(0);
				setState(171);
				match(RPAREN);
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
		public TerminalNode INT32_T() { return getToken(FrankoParser.INT32_T, 0); }
		public TerminalNode UINT32_T() { return getToken(FrankoParser.UINT32_T, 0); }
		public TerminalNode FLOAT32_T() { return getToken(FrankoParser.FLOAT32_T, 0); }
		public TerminalNode CHAR8_T() { return getToken(FrankoParser.CHAR8_T, 0); }
		public TerminalNode ARRAY() { return getToken(FrankoParser.ARRAY, 0); }
		public TerminalNode LT() { return getToken(FrankoParser.LT, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TerminalNode GT() { return getToken(FrankoParser.GT, 0); }
		public TerminalNode COMMA() { return getToken(FrankoParser.COMMA, 0); }
		public TerminalNode INT_LITERAL() { return getToken(FrankoParser.INT_LITERAL, 0); }
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
		enterRule(_localctx, 32, RULE_type);
		try {
			setState(191);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(175);
				match(INT32_T);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(176);
				match(UINT32_T);
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(177);
				match(FLOAT32_T);
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(178);
				match(CHAR8_T);
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(179);
				match(ARRAY);
				setState(180);
				match(LT);
				setState(181);
				type();
				setState(182);
				match(GT);
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(184);
				match(ARRAY);
				setState(185);
				match(LT);
				setState(186);
				type();
				setState(187);
				match(COMMA);
				setState(188);
				match(INT_LITERAL);
				setState(189);
				match(GT);
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

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 14:
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
		"\u0004\u0001\u001e\u00c2\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0001\u0000\u0005\u0000$\b\u0000\n\u0000"+
		"\f\u0000\'\t\u0000\u0001\u0000\u0001\u0000\u0004\u0000+\b\u0000\u000b"+
		"\u0000\f\u0000,\u0001\u0000\u0001\u0000\u0005\u00001\b\u0000\n\u0000\f"+
		"\u00004\t\u0000\u0003\u00006\b\u0000\u0001\u0000\u0005\u00009\b\u0000"+
		"\n\u0000\f\u0000<\t\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0003\u0001I\b\u0001\u0001\u0002\u0004\u0002L\b\u0002\u000b"+
		"\u0002\f\u0002M\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0003\u0001\u0003\u0001\u0003\u0003\u0003W\b\u0003\u0001\u0004\u0001"+
		"\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005\u0001"+
		"\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\b\u0001\b"+
		"\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001\t\u0001\t\u0001\t\u0001"+
		"\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001\n\u0003\n|\b\n\u0001"+
		"\n\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b\u0005\u000b\u0083\b\u000b"+
		"\n\u000b\f\u000b\u0086\t\u000b\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f"+
		"\u0001\f\u0003\f\u008e\b\f\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0003\u000e\u0096\b\u000e\u0001\u000e\u0001\u000e\u0001"+
		"\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0005\u000e\u009e\b\u000e\n"+
		"\u000e\f\u000e\u00a1\t\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001"+
		"\u000f\u0001\u000f\u0003\u000f\u00ae\b\u000f\u0001\u0010\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001"+
		"\u0010\u0001\u0010\u0003\u0010\u00c0\b\u0010\u0001\u0010\u0000\u0001\u001c"+
		"\u0011\u0000\u0002\u0004\u0006\b\n\f\u000e\u0010\u0012\u0014\u0016\u0018"+
		"\u001a\u001c\u001e \u0000\u0003\u0002\u0000\u0013\u0013\u001e\u001e\u0001"+
		"\u0000\u000f\u0010\u0001\u0000\r\u000e\u00cd\u0000%\u0001\u0000\u0000"+
		"\u0000\u0002H\u0001\u0000\u0000\u0000\u0004K\u0001\u0000\u0000\u0000\u0006"+
		"V\u0001\u0000\u0000\u0000\bX\u0001\u0000\u0000\u0000\n[\u0001\u0000\u0000"+
		"\u0000\f_\u0001\u0000\u0000\u0000\u000ed\u0001\u0000\u0000\u0000\u0010"+
		"j\u0001\u0000\u0000\u0000\u0012q\u0001\u0000\u0000\u0000\u0014x\u0001"+
		"\u0000\u0000\u0000\u0016\u007f\u0001\u0000\u0000\u0000\u0018\u008d\u0001"+
		"\u0000\u0000\u0000\u001a\u008f\u0001\u0000\u0000\u0000\u001c\u0095\u0001"+
		"\u0000\u0000\u0000\u001e\u00ad\u0001\u0000\u0000\u0000 \u00bf\u0001\u0000"+
		"\u0000\u0000\"$\u0003\u0004\u0002\u0000#\"\u0001\u0000\u0000\u0000$\'"+
		"\u0001\u0000\u0000\u0000%#\u0001\u0000\u0000\u0000%&\u0001\u0000\u0000"+
		"\u0000&5\u0001\u0000\u0000\u0000\'%\u0001\u0000\u0000\u0000(2\u0003\u0002"+
		"\u0001\u0000)+\u0003\u0004\u0002\u0000*)\u0001\u0000\u0000\u0000+,\u0001"+
		"\u0000\u0000\u0000,*\u0001\u0000\u0000\u0000,-\u0001\u0000\u0000\u0000"+
		"-.\u0001\u0000\u0000\u0000./\u0003\u0002\u0001\u0000/1\u0001\u0000\u0000"+
		"\u00000*\u0001\u0000\u0000\u000014\u0001\u0000\u0000\u000020\u0001\u0000"+
		"\u0000\u000023\u0001\u0000\u0000\u000036\u0001\u0000\u0000\u000042\u0001"+
		"\u0000\u0000\u00005(\u0001\u0000\u0000\u000056\u0001\u0000\u0000\u0000"+
		"6:\u0001\u0000\u0000\u000079\u0003\u0004\u0002\u000087\u0001\u0000\u0000"+
		"\u00009<\u0001\u0000\u0000\u0000:8\u0001\u0000\u0000\u0000:;\u0001\u0000"+
		"\u0000\u0000;=\u0001\u0000\u0000\u0000<:\u0001\u0000\u0000\u0000=>\u0005"+
		"\u0000\u0000\u0001>\u0001\u0001\u0000\u0000\u0000?I\u0003\u0006\u0003"+
		"\u0000@I\u0003\b\u0004\u0000AI\u0003\n\u0005\u0000BI\u0003\f\u0006\u0000"+
		"CI\u0003\u000e\u0007\u0000DI\u0003\u0010\b\u0000EI\u0003\u0012\t\u0000"+
		"FI\u0003\u0014\n\u0000GI\u0003\u001a\r\u0000H?\u0001\u0000\u0000\u0000"+
		"H@\u0001\u0000\u0000\u0000HA\u0001\u0000\u0000\u0000HB\u0001\u0000\u0000"+
		"\u0000HC\u0001\u0000\u0000\u0000HD\u0001\u0000\u0000\u0000HE\u0001\u0000"+
		"\u0000\u0000HF\u0001\u0000\u0000\u0000HG\u0001\u0000\u0000\u0000I\u0003"+
		"\u0001\u0000\u0000\u0000JL\u0007\u0000\u0000\u0000KJ\u0001\u0000\u0000"+
		"\u0000LM\u0001\u0000\u0000\u0000MK\u0001\u0000\u0000\u0000MN\u0001\u0000"+
		"\u0000\u0000N\u0005\u0001\u0000\u0000\u0000OP\u0003 \u0010\u0000PQ\u0005"+
		"\u001a\u0000\u0000QW\u0001\u0000\u0000\u0000RS\u0005\u0001\u0000\u0000"+
		"ST\u0003 \u0010\u0000TU\u0005\u001a\u0000\u0000UW\u0001\u0000\u0000\u0000"+
		"VO\u0001\u0000\u0000\u0000VR\u0001\u0000\u0000\u0000W\u0007\u0001\u0000"+
		"\u0000\u0000XY\u0005\u0002\u0000\u0000YZ\u0005\u001a\u0000\u0000Z\t\u0001"+
		"\u0000\u0000\u0000[\\\u0003\u0018\f\u0000\\]\u0005\f\u0000\u0000]^\u0003"+
		"\u001c\u000e\u0000^\u000b\u0001\u0000\u0000\u0000_`\u0005\u001a\u0000"+
		"\u0000`a\u0005\u0016\u0000\u0000ab\u0003\u001c\u000e\u0000bc\u0005\u0017"+
		"\u0000\u0000c\r\u0001\u0000\u0000\u0000de\u0005\u001a\u0000\u0000ef\u0005"+
		"\u0011\u0000\u0000fg\u0005\u0004\u0000\u0000gh\u0005\u0016\u0000\u0000"+
		"hi\u0005\u0017\u0000\u0000i\u000f\u0001\u0000\u0000\u0000jk\u0005\u001a"+
		"\u0000\u0000kl\u0005\u0011\u0000\u0000lm\u0005\u0005\u0000\u0000mn\u0005"+
		"\u0016\u0000\u0000no\u0003\u001c\u000e\u0000op\u0005\u0017\u0000\u0000"+
		"p\u0011\u0001\u0000\u0000\u0000qr\u0005\u001a\u0000\u0000rs\u0005\u0011"+
		"\u0000\u0000st\u0005\u0006\u0000\u0000tu\u0005\u0016\u0000\u0000uv\u0005"+
		"\u001a\u0000\u0000vw\u0005\u0017\u0000\u0000w\u0013\u0001\u0000\u0000"+
		"\u0000xy\u0005\u0003\u0000\u0000y{\u0005\u0016\u0000\u0000z|\u0003\u0016"+
		"\u000b\u0000{z\u0001\u0000\u0000\u0000{|\u0001\u0000\u0000\u0000|}\u0001"+
		"\u0000\u0000\u0000}~\u0005\u0017\u0000\u0000~\u0015\u0001\u0000\u0000"+
		"\u0000\u007f\u0084\u0003\u001c\u000e\u0000\u0080\u0081\u0005\u0012\u0000"+
		"\u0000\u0081\u0083\u0003\u001c\u000e\u0000\u0082\u0080\u0001\u0000\u0000"+
		"\u0000\u0083\u0086\u0001\u0000\u0000\u0000\u0084\u0082\u0001\u0000\u0000"+
		"\u0000\u0084\u0085\u0001\u0000\u0000\u0000\u0085\u0017\u0001\u0000\u0000"+
		"\u0000\u0086\u0084\u0001\u0000\u0000\u0000\u0087\u008e\u0005\u001a\u0000"+
		"\u0000\u0088\u0089\u0005\u001a\u0000\u0000\u0089\u008a\u0005\u0018\u0000"+
		"\u0000\u008a\u008b\u0003\u001c\u000e\u0000\u008b\u008c\u0005\u0019\u0000"+
		"\u0000\u008c\u008e\u0001\u0000\u0000\u0000\u008d\u0087\u0001\u0000\u0000"+
		"\u0000\u008d\u0088\u0001\u0000\u0000\u0000\u008e\u0019\u0001\u0000\u0000"+
		"\u0000\u008f\u0090\u0003\u001c\u000e\u0000\u0090\u001b\u0001\u0000\u0000"+
		"\u0000\u0091\u0092\u0006\u000e\uffff\uffff\u0000\u0092\u0093\u0005\u000e"+
		"\u0000\u0000\u0093\u0096\u0003\u001c\u000e\u0004\u0094\u0096\u0003\u001e"+
		"\u000f\u0000\u0095\u0091\u0001\u0000\u0000\u0000\u0095\u0094\u0001\u0000"+
		"\u0000\u0000\u0096\u009f\u0001\u0000\u0000\u0000\u0097\u0098\n\u0003\u0000"+
		"\u0000\u0098\u0099\u0007\u0001\u0000\u0000\u0099\u009e\u0003\u001c\u000e"+
		"\u0004\u009a\u009b\n\u0002\u0000\u0000\u009b\u009c\u0007\u0002\u0000\u0000"+
		"\u009c\u009e\u0003\u001c\u000e\u0003\u009d\u0097\u0001\u0000\u0000\u0000"+
		"\u009d\u009a\u0001\u0000\u0000\u0000\u009e\u00a1\u0001\u0000\u0000\u0000"+
		"\u009f\u009d\u0001\u0000\u0000\u0000\u009f\u00a0\u0001\u0000\u0000\u0000"+
		"\u00a0\u001d\u0001\u0000\u0000\u0000\u00a1\u009f\u0001\u0000\u0000\u0000"+
		"\u00a2\u00ae\u0005\u001b\u0000\u0000\u00a3\u00ae\u0005\u001a\u0000\u0000"+
		"\u00a4\u00a5\u0005\u001a\u0000\u0000\u00a5\u00a6\u0005\u0018\u0000\u0000"+
		"\u00a6\u00a7\u0003\u001c\u000e\u0000\u00a7\u00a8\u0005\u0019\u0000\u0000"+
		"\u00a8\u00ae\u0001\u0000\u0000\u0000\u00a9\u00aa\u0005\u0016\u0000\u0000"+
		"\u00aa\u00ab\u0003\u001c\u000e\u0000\u00ab\u00ac\u0005\u0017\u0000\u0000"+
		"\u00ac\u00ae\u0001\u0000\u0000\u0000\u00ad\u00a2\u0001\u0000\u0000\u0000"+
		"\u00ad\u00a3\u0001\u0000\u0000\u0000\u00ad\u00a4\u0001\u0000\u0000\u0000"+
		"\u00ad\u00a9\u0001\u0000\u0000\u0000\u00ae\u001f\u0001\u0000\u0000\u0000"+
		"\u00af\u00c0\u0005\u0007\u0000\u0000\u00b0\u00c0\u0005\b\u0000\u0000\u00b1"+
		"\u00c0\u0005\t\u0000\u0000\u00b2\u00c0\u0005\n\u0000\u0000\u00b3\u00b4"+
		"\u0005\u000b\u0000\u0000\u00b4\u00b5\u0005\u0014\u0000\u0000\u00b5\u00b6"+
		"\u0003 \u0010\u0000\u00b6\u00b7\u0005\u0015\u0000\u0000\u00b7\u00c0\u0001"+
		"\u0000\u0000\u0000\u00b8\u00b9\u0005\u000b\u0000\u0000\u00b9\u00ba\u0005"+
		"\u0014\u0000\u0000\u00ba\u00bb\u0003 \u0010\u0000\u00bb\u00bc\u0005\u0012"+
		"\u0000\u0000\u00bc\u00bd\u0005\u001b\u0000\u0000\u00bd\u00be\u0005\u0015"+
		"\u0000\u0000\u00be\u00c0\u0001\u0000\u0000\u0000\u00bf\u00af\u0001\u0000"+
		"\u0000\u0000\u00bf\u00b0\u0001\u0000\u0000\u0000\u00bf\u00b1\u0001\u0000"+
		"\u0000\u0000\u00bf\u00b2\u0001\u0000\u0000\u0000\u00bf\u00b3\u0001\u0000"+
		"\u0000\u0000\u00bf\u00b8\u0001\u0000\u0000\u0000\u00c0!\u0001\u0000\u0000"+
		"\u0000\u0010%,25:HMV{\u0084\u008d\u0095\u009d\u009f\u00ad\u00bf";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}