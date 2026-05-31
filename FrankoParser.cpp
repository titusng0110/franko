
// Generated from Franko.g4 by ANTLR 4.13.2


#include "FrankoListener.h"

#include "FrankoParser.h"


using namespace antlrcpp;

using namespace antlr4;

namespace {

struct FrankoParserStaticData final {
  FrankoParserStaticData(std::vector<std::string> ruleNames,
                        std::vector<std::string> literalNames,
                        std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  FrankoParserStaticData(const FrankoParserStaticData&) = delete;
  FrankoParserStaticData(FrankoParserStaticData&&) = delete;
  FrankoParserStaticData& operator=(const FrankoParserStaticData&) = delete;
  FrankoParserStaticData& operator=(FrankoParserStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag frankoParserOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<FrankoParserStaticData> frankoParserStaticData = nullptr;

void frankoParserInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (frankoParserStaticData != nullptr) {
    return;
  }
#else
  assert(frankoParserStaticData == nullptr);
#endif
  auto staticData = std::make_unique<FrankoParserStaticData>(
    std::vector<std::string>{
      "program", "statement", "varDecl", "delStmt", "assignStmt", "arrayInitStmt", 
      "arrayUninitStmt", "lvalue", "exprStmt", "expr", "atom", "type"
    },
    std::vector<std::string>{
      "", "';'", "'alloc'", "'del'", "'='", "'('", "')'", "'.'", "'uninit'", 
      "'['", "']'", "'*'", "'/'", "'+'", "'-'", "'int32_t'", "'uint32_t'", 
      "'float32_t'", "'char8_t'", "'array'", "'<'", "'>'"
    },
    std::vector<std::string>{
      "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", 
      "", "", "", "", "", "IDENTIFIER", "INT_LITERAL", "WS"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,24,128,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,1,0,5,0,26,8,0,10,0,12,0,29,
  	9,0,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
  	1,1,1,1,1,1,1,3,1,51,8,1,1,2,1,2,1,2,1,2,1,2,1,2,1,2,3,2,60,8,2,1,3,1,
  	3,1,3,1,4,1,4,1,4,1,4,1,5,1,5,1,5,1,5,1,5,1,6,1,6,1,6,1,6,1,6,1,6,1,7,
  	1,7,1,7,1,7,1,7,1,7,3,7,86,8,7,1,8,1,8,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,
  	9,1,9,5,9,99,8,9,10,9,12,9,102,9,9,1,10,1,10,1,10,1,10,1,10,1,10,1,10,
  	1,10,1,10,1,10,1,10,3,10,115,8,10,1,11,1,11,1,11,1,11,1,11,1,11,1,11,
  	1,11,1,11,3,11,126,8,11,1,11,0,1,18,12,0,2,4,6,8,10,12,14,16,18,20,22,
  	0,2,1,0,11,12,1,0,13,14,132,0,27,1,0,0,0,2,50,1,0,0,0,4,59,1,0,0,0,6,
  	61,1,0,0,0,8,64,1,0,0,0,10,68,1,0,0,0,12,73,1,0,0,0,14,85,1,0,0,0,16,
  	87,1,0,0,0,18,89,1,0,0,0,20,114,1,0,0,0,22,125,1,0,0,0,24,26,3,2,1,0,
  	25,24,1,0,0,0,26,29,1,0,0,0,27,25,1,0,0,0,27,28,1,0,0,0,28,30,1,0,0,0,
  	29,27,1,0,0,0,30,31,5,0,0,1,31,1,1,0,0,0,32,33,3,4,2,0,33,34,5,1,0,0,
  	34,51,1,0,0,0,35,36,3,6,3,0,36,37,5,1,0,0,37,51,1,0,0,0,38,39,3,8,4,0,
  	39,40,5,1,0,0,40,51,1,0,0,0,41,42,3,10,5,0,42,43,5,1,0,0,43,51,1,0,0,
  	0,44,45,3,12,6,0,45,46,5,1,0,0,46,51,1,0,0,0,47,48,3,16,8,0,48,49,5,1,
  	0,0,49,51,1,0,0,0,50,32,1,0,0,0,50,35,1,0,0,0,50,38,1,0,0,0,50,41,1,0,
  	0,0,50,44,1,0,0,0,50,47,1,0,0,0,51,3,1,0,0,0,52,53,3,22,11,0,53,54,5,
  	22,0,0,54,60,1,0,0,0,55,56,5,2,0,0,56,57,3,22,11,0,57,58,5,22,0,0,58,
  	60,1,0,0,0,59,52,1,0,0,0,59,55,1,0,0,0,60,5,1,0,0,0,61,62,5,3,0,0,62,
  	63,5,22,0,0,63,7,1,0,0,0,64,65,3,14,7,0,65,66,5,4,0,0,66,67,3,18,9,0,
  	67,9,1,0,0,0,68,69,5,22,0,0,69,70,5,5,0,0,70,71,3,18,9,0,71,72,5,6,0,
  	0,72,11,1,0,0,0,73,74,5,22,0,0,74,75,5,7,0,0,75,76,5,8,0,0,76,77,5,5,
  	0,0,77,78,5,6,0,0,78,13,1,0,0,0,79,86,5,22,0,0,80,81,5,22,0,0,81,82,5,
  	9,0,0,82,83,3,18,9,0,83,84,5,10,0,0,84,86,1,0,0,0,85,79,1,0,0,0,85,80,
  	1,0,0,0,86,15,1,0,0,0,87,88,3,18,9,0,88,17,1,0,0,0,89,90,6,9,-1,0,90,
  	91,3,20,10,0,91,100,1,0,0,0,92,93,10,3,0,0,93,94,7,0,0,0,94,99,3,18,9,
  	4,95,96,10,2,0,0,96,97,7,1,0,0,97,99,3,18,9,3,98,92,1,0,0,0,98,95,1,0,
  	0,0,99,102,1,0,0,0,100,98,1,0,0,0,100,101,1,0,0,0,101,19,1,0,0,0,102,
  	100,1,0,0,0,103,115,5,23,0,0,104,115,5,22,0,0,105,106,5,22,0,0,106,107,
  	5,9,0,0,107,108,3,18,9,0,108,109,5,10,0,0,109,115,1,0,0,0,110,111,5,5,
  	0,0,111,112,3,18,9,0,112,113,5,6,0,0,113,115,1,0,0,0,114,103,1,0,0,0,
  	114,104,1,0,0,0,114,105,1,0,0,0,114,110,1,0,0,0,115,21,1,0,0,0,116,126,
  	5,15,0,0,117,126,5,16,0,0,118,126,5,17,0,0,119,126,5,18,0,0,120,121,5,
  	19,0,0,121,122,5,20,0,0,122,123,3,22,11,0,123,124,5,21,0,0,124,126,1,
  	0,0,0,125,116,1,0,0,0,125,117,1,0,0,0,125,118,1,0,0,0,125,119,1,0,0,0,
  	125,120,1,0,0,0,126,23,1,0,0,0,8,27,50,59,85,98,100,114,125
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  frankoParserStaticData = std::move(staticData);
}

}

FrankoParser::FrankoParser(TokenStream *input) : FrankoParser(input, antlr4::atn::ParserATNSimulatorOptions()) {}

FrankoParser::FrankoParser(TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options) : Parser(input) {
  FrankoParser::initialize();
  _interpreter = new atn::ParserATNSimulator(this, *frankoParserStaticData->atn, frankoParserStaticData->decisionToDFA, frankoParserStaticData->sharedContextCache, options);
}

FrankoParser::~FrankoParser() {
  delete _interpreter;
}

const atn::ATN& FrankoParser::getATN() const {
  return *frankoParserStaticData->atn;
}

std::string FrankoParser::getGrammarFileName() const {
  return "Franko.g4";
}

const std::vector<std::string>& FrankoParser::getRuleNames() const {
  return frankoParserStaticData->ruleNames;
}

const dfa::Vocabulary& FrankoParser::getVocabulary() const {
  return frankoParserStaticData->vocabulary;
}

antlr4::atn::SerializedATNView FrankoParser::getSerializedATN() const {
  return frankoParserStaticData->serializedATN;
}


//----------------- ProgramContext ------------------------------------------------------------------

FrankoParser::ProgramContext::ProgramContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrankoParser::ProgramContext::EOF() {
  return getToken(FrankoParser::EOF, 0);
}

std::vector<FrankoParser::StatementContext *> FrankoParser::ProgramContext::statement() {
  return getRuleContexts<FrankoParser::StatementContext>();
}

FrankoParser::StatementContext* FrankoParser::ProgramContext::statement(size_t i) {
  return getRuleContext<FrankoParser::StatementContext>(i);
}


size_t FrankoParser::ProgramContext::getRuleIndex() const {
  return FrankoParser::RuleProgram;
}

void FrankoParser::ProgramContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterProgram(this);
}

void FrankoParser::ProgramContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitProgram(this);
}

FrankoParser::ProgramContext* FrankoParser::program() {
  ProgramContext *_localctx = _tracker.createInstance<ProgramContext>(_ctx, getState());
  enterRule(_localctx, 0, FrankoParser::RuleProgram);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(27);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 13598764) != 0)) {
      setState(24);
      statement();
      setState(29);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(30);
    match(FrankoParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- StatementContext ------------------------------------------------------------------

FrankoParser::StatementContext::StatementContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrankoParser::VarDeclContext* FrankoParser::StatementContext::varDecl() {
  return getRuleContext<FrankoParser::VarDeclContext>(0);
}

FrankoParser::DelStmtContext* FrankoParser::StatementContext::delStmt() {
  return getRuleContext<FrankoParser::DelStmtContext>(0);
}

FrankoParser::AssignStmtContext* FrankoParser::StatementContext::assignStmt() {
  return getRuleContext<FrankoParser::AssignStmtContext>(0);
}

FrankoParser::ArrayInitStmtContext* FrankoParser::StatementContext::arrayInitStmt() {
  return getRuleContext<FrankoParser::ArrayInitStmtContext>(0);
}

FrankoParser::ArrayUninitStmtContext* FrankoParser::StatementContext::arrayUninitStmt() {
  return getRuleContext<FrankoParser::ArrayUninitStmtContext>(0);
}

FrankoParser::ExprStmtContext* FrankoParser::StatementContext::exprStmt() {
  return getRuleContext<FrankoParser::ExprStmtContext>(0);
}


size_t FrankoParser::StatementContext::getRuleIndex() const {
  return FrankoParser::RuleStatement;
}

void FrankoParser::StatementContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterStatement(this);
}

void FrankoParser::StatementContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitStatement(this);
}

FrankoParser::StatementContext* FrankoParser::statement() {
  StatementContext *_localctx = _tracker.createInstance<StatementContext>(_ctx, getState());
  enterRule(_localctx, 2, FrankoParser::RuleStatement);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(50);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 1, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(32);
      varDecl();
      setState(33);
      match(FrankoParser::T__0);
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(35);
      delStmt();
      setState(36);
      match(FrankoParser::T__0);
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(38);
      assignStmt();
      setState(39);
      match(FrankoParser::T__0);
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(41);
      arrayInitStmt();
      setState(42);
      match(FrankoParser::T__0);
      break;
    }

    case 5: {
      enterOuterAlt(_localctx, 5);
      setState(44);
      arrayUninitStmt();
      setState(45);
      match(FrankoParser::T__0);
      break;
    }

    case 6: {
      enterOuterAlt(_localctx, 6);
      setState(47);
      exprStmt();
      setState(48);
      match(FrankoParser::T__0);
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VarDeclContext ------------------------------------------------------------------

FrankoParser::VarDeclContext::VarDeclContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrankoParser::TypeContext* FrankoParser::VarDeclContext::type() {
  return getRuleContext<FrankoParser::TypeContext>(0);
}

tree::TerminalNode* FrankoParser::VarDeclContext::IDENTIFIER() {
  return getToken(FrankoParser::IDENTIFIER, 0);
}


size_t FrankoParser::VarDeclContext::getRuleIndex() const {
  return FrankoParser::RuleVarDecl;
}

void FrankoParser::VarDeclContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterVarDecl(this);
}

void FrankoParser::VarDeclContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitVarDecl(this);
}

FrankoParser::VarDeclContext* FrankoParser::varDecl() {
  VarDeclContext *_localctx = _tracker.createInstance<VarDeclContext>(_ctx, getState());
  enterRule(_localctx, 4, FrankoParser::RuleVarDecl);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(59);
    _errHandler->sync(this);
    switch (_input->LA(1)) {
      case FrankoParser::T__14:
      case FrankoParser::T__15:
      case FrankoParser::T__16:
      case FrankoParser::T__17:
      case FrankoParser::T__18: {
        enterOuterAlt(_localctx, 1);
        setState(52);
        type();
        setState(53);
        match(FrankoParser::IDENTIFIER);
        break;
      }

      case FrankoParser::T__1: {
        enterOuterAlt(_localctx, 2);
        setState(55);
        match(FrankoParser::T__1);
        setState(56);
        type();
        setState(57);
        match(FrankoParser::IDENTIFIER);
        break;
      }

    default:
      throw NoViableAltException(this);
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- DelStmtContext ------------------------------------------------------------------

FrankoParser::DelStmtContext::DelStmtContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrankoParser::DelStmtContext::IDENTIFIER() {
  return getToken(FrankoParser::IDENTIFIER, 0);
}


size_t FrankoParser::DelStmtContext::getRuleIndex() const {
  return FrankoParser::RuleDelStmt;
}

void FrankoParser::DelStmtContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterDelStmt(this);
}

void FrankoParser::DelStmtContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitDelStmt(this);
}

FrankoParser::DelStmtContext* FrankoParser::delStmt() {
  DelStmtContext *_localctx = _tracker.createInstance<DelStmtContext>(_ctx, getState());
  enterRule(_localctx, 6, FrankoParser::RuleDelStmt);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(61);
    match(FrankoParser::T__2);
    setState(62);
    match(FrankoParser::IDENTIFIER);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- AssignStmtContext ------------------------------------------------------------------

FrankoParser::AssignStmtContext::AssignStmtContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrankoParser::LvalueContext* FrankoParser::AssignStmtContext::lvalue() {
  return getRuleContext<FrankoParser::LvalueContext>(0);
}

FrankoParser::ExprContext* FrankoParser::AssignStmtContext::expr() {
  return getRuleContext<FrankoParser::ExprContext>(0);
}


size_t FrankoParser::AssignStmtContext::getRuleIndex() const {
  return FrankoParser::RuleAssignStmt;
}

void FrankoParser::AssignStmtContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterAssignStmt(this);
}

void FrankoParser::AssignStmtContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitAssignStmt(this);
}

FrankoParser::AssignStmtContext* FrankoParser::assignStmt() {
  AssignStmtContext *_localctx = _tracker.createInstance<AssignStmtContext>(_ctx, getState());
  enterRule(_localctx, 8, FrankoParser::RuleAssignStmt);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(64);
    lvalue();
    setState(65);
    match(FrankoParser::T__3);
    setState(66);
    expr(0);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ArrayInitStmtContext ------------------------------------------------------------------

FrankoParser::ArrayInitStmtContext::ArrayInitStmtContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrankoParser::ArrayInitStmtContext::IDENTIFIER() {
  return getToken(FrankoParser::IDENTIFIER, 0);
}

FrankoParser::ExprContext* FrankoParser::ArrayInitStmtContext::expr() {
  return getRuleContext<FrankoParser::ExprContext>(0);
}


size_t FrankoParser::ArrayInitStmtContext::getRuleIndex() const {
  return FrankoParser::RuleArrayInitStmt;
}

void FrankoParser::ArrayInitStmtContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterArrayInitStmt(this);
}

void FrankoParser::ArrayInitStmtContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitArrayInitStmt(this);
}

FrankoParser::ArrayInitStmtContext* FrankoParser::arrayInitStmt() {
  ArrayInitStmtContext *_localctx = _tracker.createInstance<ArrayInitStmtContext>(_ctx, getState());
  enterRule(_localctx, 10, FrankoParser::RuleArrayInitStmt);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(68);
    match(FrankoParser::IDENTIFIER);
    setState(69);
    match(FrankoParser::T__4);
    setState(70);
    expr(0);
    setState(71);
    match(FrankoParser::T__5);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ArrayUninitStmtContext ------------------------------------------------------------------

FrankoParser::ArrayUninitStmtContext::ArrayUninitStmtContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrankoParser::ArrayUninitStmtContext::IDENTIFIER() {
  return getToken(FrankoParser::IDENTIFIER, 0);
}


size_t FrankoParser::ArrayUninitStmtContext::getRuleIndex() const {
  return FrankoParser::RuleArrayUninitStmt;
}

void FrankoParser::ArrayUninitStmtContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterArrayUninitStmt(this);
}

void FrankoParser::ArrayUninitStmtContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitArrayUninitStmt(this);
}

FrankoParser::ArrayUninitStmtContext* FrankoParser::arrayUninitStmt() {
  ArrayUninitStmtContext *_localctx = _tracker.createInstance<ArrayUninitStmtContext>(_ctx, getState());
  enterRule(_localctx, 12, FrankoParser::RuleArrayUninitStmt);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(73);
    match(FrankoParser::IDENTIFIER);
    setState(74);
    match(FrankoParser::T__6);
    setState(75);
    match(FrankoParser::T__7);
    setState(76);
    match(FrankoParser::T__4);
    setState(77);
    match(FrankoParser::T__5);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- LvalueContext ------------------------------------------------------------------

FrankoParser::LvalueContext::LvalueContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrankoParser::LvalueContext::IDENTIFIER() {
  return getToken(FrankoParser::IDENTIFIER, 0);
}

FrankoParser::ExprContext* FrankoParser::LvalueContext::expr() {
  return getRuleContext<FrankoParser::ExprContext>(0);
}


size_t FrankoParser::LvalueContext::getRuleIndex() const {
  return FrankoParser::RuleLvalue;
}

void FrankoParser::LvalueContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterLvalue(this);
}

void FrankoParser::LvalueContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitLvalue(this);
}

FrankoParser::LvalueContext* FrankoParser::lvalue() {
  LvalueContext *_localctx = _tracker.createInstance<LvalueContext>(_ctx, getState());
  enterRule(_localctx, 14, FrankoParser::RuleLvalue);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(85);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 3, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(79);
      match(FrankoParser::IDENTIFIER);
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(80);
      match(FrankoParser::IDENTIFIER);
      setState(81);
      match(FrankoParser::T__8);
      setState(82);
      expr(0);
      setState(83);
      match(FrankoParser::T__9);
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ExprStmtContext ------------------------------------------------------------------

FrankoParser::ExprStmtContext::ExprStmtContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrankoParser::ExprContext* FrankoParser::ExprStmtContext::expr() {
  return getRuleContext<FrankoParser::ExprContext>(0);
}


size_t FrankoParser::ExprStmtContext::getRuleIndex() const {
  return FrankoParser::RuleExprStmt;
}

void FrankoParser::ExprStmtContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterExprStmt(this);
}

void FrankoParser::ExprStmtContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitExprStmt(this);
}

FrankoParser::ExprStmtContext* FrankoParser::exprStmt() {
  ExprStmtContext *_localctx = _tracker.createInstance<ExprStmtContext>(_ctx, getState());
  enterRule(_localctx, 16, FrankoParser::RuleExprStmt);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(87);
    expr(0);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ExprContext ------------------------------------------------------------------

FrankoParser::ExprContext::ExprContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}


size_t FrankoParser::ExprContext::getRuleIndex() const {
  return FrankoParser::RuleExpr;
}

void FrankoParser::ExprContext::copyFrom(ExprContext *ctx) {
  ParserRuleContext::copyFrom(ctx);
}

//----------------- MulDivContext ------------------------------------------------------------------

std::vector<FrankoParser::ExprContext *> FrankoParser::MulDivContext::expr() {
  return getRuleContexts<FrankoParser::ExprContext>();
}

FrankoParser::ExprContext* FrankoParser::MulDivContext::expr(size_t i) {
  return getRuleContext<FrankoParser::ExprContext>(i);
}

FrankoParser::MulDivContext::MulDivContext(ExprContext *ctx) { copyFrom(ctx); }

void FrankoParser::MulDivContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterMulDiv(this);
}
void FrankoParser::MulDivContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitMulDiv(this);
}
//----------------- AddSubContext ------------------------------------------------------------------

std::vector<FrankoParser::ExprContext *> FrankoParser::AddSubContext::expr() {
  return getRuleContexts<FrankoParser::ExprContext>();
}

FrankoParser::ExprContext* FrankoParser::AddSubContext::expr(size_t i) {
  return getRuleContext<FrankoParser::ExprContext>(i);
}

FrankoParser::AddSubContext::AddSubContext(ExprContext *ctx) { copyFrom(ctx); }

void FrankoParser::AddSubContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterAddSub(this);
}
void FrankoParser::AddSubContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitAddSub(this);
}
//----------------- AtomExprContext ------------------------------------------------------------------

FrankoParser::AtomContext* FrankoParser::AtomExprContext::atom() {
  return getRuleContext<FrankoParser::AtomContext>(0);
}

FrankoParser::AtomExprContext::AtomExprContext(ExprContext *ctx) { copyFrom(ctx); }

void FrankoParser::AtomExprContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterAtomExpr(this);
}
void FrankoParser::AtomExprContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitAtomExpr(this);
}

FrankoParser::ExprContext* FrankoParser::expr() {
   return expr(0);
}

FrankoParser::ExprContext* FrankoParser::expr(int precedence) {
  ParserRuleContext *parentContext = _ctx;
  size_t parentState = getState();
  FrankoParser::ExprContext *_localctx = _tracker.createInstance<ExprContext>(_ctx, parentState);
  FrankoParser::ExprContext *previousContext = _localctx;
  (void)previousContext; // Silence compiler, in case the context is not used by generated code.
  size_t startState = 18;
  enterRecursionRule(_localctx, 18, FrankoParser::RuleExpr, precedence);

    size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    unrollRecursionContexts(parentContext);
  });
  try {
    size_t alt;
    enterOuterAlt(_localctx, 1);
    _localctx = _tracker.createInstance<AtomExprContext>(_localctx);
    _ctx = _localctx;
    previousContext = _localctx;

    setState(90);
    atom();
    _ctx->stop = _input->LT(-1);
    setState(100);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 5, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        if (!_parseListeners.empty())
          triggerExitRuleEvent();
        previousContext = _localctx;
        setState(98);
        _errHandler->sync(this);
        switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 4, _ctx)) {
        case 1: {
          auto newContext = _tracker.createInstance<MulDivContext>(_tracker.createInstance<ExprContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleExpr);
          setState(92);

          if (!(precpred(_ctx, 3))) throw FailedPredicateException(this, "precpred(_ctx, 3)");
          setState(93);
          antlrcpp::downCast<MulDivContext *>(_localctx)->op = _input->LT(1);
          _la = _input->LA(1);
          if (!(_la == FrankoParser::T__10

          || _la == FrankoParser::T__11)) {
            antlrcpp::downCast<MulDivContext *>(_localctx)->op = _errHandler->recoverInline(this);
          }
          else {
            _errHandler->reportMatch(this);
            consume();
          }
          setState(94);
          expr(4);
          break;
        }

        case 2: {
          auto newContext = _tracker.createInstance<AddSubContext>(_tracker.createInstance<ExprContext>(parentContext, parentState));
          _localctx = newContext;
          pushNewRecursionContext(newContext, startState, RuleExpr);
          setState(95);

          if (!(precpred(_ctx, 2))) throw FailedPredicateException(this, "precpred(_ctx, 2)");
          setState(96);
          antlrcpp::downCast<AddSubContext *>(_localctx)->op = _input->LT(1);
          _la = _input->LA(1);
          if (!(_la == FrankoParser::T__12

          || _la == FrankoParser::T__13)) {
            antlrcpp::downCast<AddSubContext *>(_localctx)->op = _errHandler->recoverInline(this);
          }
          else {
            _errHandler->reportMatch(this);
            consume();
          }
          setState(97);
          expr(3);
          break;
        }

        default:
          break;
        } 
      }
      setState(102);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 5, _ctx);
    }
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }
  return _localctx;
}

//----------------- AtomContext ------------------------------------------------------------------

FrankoParser::AtomContext::AtomContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrankoParser::AtomContext::INT_LITERAL() {
  return getToken(FrankoParser::INT_LITERAL, 0);
}

tree::TerminalNode* FrankoParser::AtomContext::IDENTIFIER() {
  return getToken(FrankoParser::IDENTIFIER, 0);
}

FrankoParser::ExprContext* FrankoParser::AtomContext::expr() {
  return getRuleContext<FrankoParser::ExprContext>(0);
}


size_t FrankoParser::AtomContext::getRuleIndex() const {
  return FrankoParser::RuleAtom;
}

void FrankoParser::AtomContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterAtom(this);
}

void FrankoParser::AtomContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitAtom(this);
}

FrankoParser::AtomContext* FrankoParser::atom() {
  AtomContext *_localctx = _tracker.createInstance<AtomContext>(_ctx, getState());
  enterRule(_localctx, 20, FrankoParser::RuleAtom);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(114);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 6, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(103);
      match(FrankoParser::INT_LITERAL);
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(104);
      match(FrankoParser::IDENTIFIER);
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(105);
      match(FrankoParser::IDENTIFIER);
      setState(106);
      match(FrankoParser::T__8);
      setState(107);
      expr(0);
      setState(108);
      match(FrankoParser::T__9);
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(110);
      match(FrankoParser::T__4);
      setState(111);
      expr(0);
      setState(112);
      match(FrankoParser::T__5);
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- TypeContext ------------------------------------------------------------------

FrankoParser::TypeContext::TypeContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrankoParser::TypeContext* FrankoParser::TypeContext::type() {
  return getRuleContext<FrankoParser::TypeContext>(0);
}


size_t FrankoParser::TypeContext::getRuleIndex() const {
  return FrankoParser::RuleType;
}

void FrankoParser::TypeContext::enterRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->enterType(this);
}

void FrankoParser::TypeContext::exitRule(tree::ParseTreeListener *listener) {
  auto parserListener = dynamic_cast<FrankoListener *>(listener);
  if (parserListener != nullptr)
    parserListener->exitType(this);
}

FrankoParser::TypeContext* FrankoParser::type() {
  TypeContext *_localctx = _tracker.createInstance<TypeContext>(_ctx, getState());
  enterRule(_localctx, 22, FrankoParser::RuleType);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(125);
    _errHandler->sync(this);
    switch (_input->LA(1)) {
      case FrankoParser::T__14: {
        enterOuterAlt(_localctx, 1);
        setState(116);
        match(FrankoParser::T__14);
        break;
      }

      case FrankoParser::T__15: {
        enterOuterAlt(_localctx, 2);
        setState(117);
        match(FrankoParser::T__15);
        break;
      }

      case FrankoParser::T__16: {
        enterOuterAlt(_localctx, 3);
        setState(118);
        match(FrankoParser::T__16);
        break;
      }

      case FrankoParser::T__17: {
        enterOuterAlt(_localctx, 4);
        setState(119);
        match(FrankoParser::T__17);
        break;
      }

      case FrankoParser::T__18: {
        enterOuterAlt(_localctx, 5);
        setState(120);
        match(FrankoParser::T__18);
        setState(121);
        match(FrankoParser::T__19);
        setState(122);
        type();
        setState(123);
        match(FrankoParser::T__20);
        break;
      }

    default:
      throw NoViableAltException(this);
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

bool FrankoParser::sempred(RuleContext *context, size_t ruleIndex, size_t predicateIndex) {
  switch (ruleIndex) {
    case 9: return exprSempred(antlrcpp::downCast<ExprContext *>(context), predicateIndex);

  default:
    break;
  }
  return true;
}

bool FrankoParser::exprSempred(ExprContext *_localctx, size_t predicateIndex) {
  switch (predicateIndex) {
    case 0: return precpred(_ctx, 3);
    case 1: return precpred(_ctx, 2);

  default:
    break;
  }
  return true;
}

void FrankoParser::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  frankoParserInitialize();
#else
  ::antlr4::internal::call_once(frankoParserOnceFlag, frankoParserInitialize);
#endif
}
