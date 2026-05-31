
// Generated from Franko.g4 by ANTLR 4.13.2


#include "FrankoLexer.h"


using namespace antlr4;



using namespace antlr4;

namespace {

struct FrankoLexerStaticData final {
  FrankoLexerStaticData(std::vector<std::string> ruleNames,
                          std::vector<std::string> channelNames,
                          std::vector<std::string> modeNames,
                          std::vector<std::string> literalNames,
                          std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), channelNames(std::move(channelNames)),
        modeNames(std::move(modeNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  FrankoLexerStaticData(const FrankoLexerStaticData&) = delete;
  FrankoLexerStaticData(FrankoLexerStaticData&&) = delete;
  FrankoLexerStaticData& operator=(const FrankoLexerStaticData&) = delete;
  FrankoLexerStaticData& operator=(FrankoLexerStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> channelNames;
  const std::vector<std::string> modeNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag frankolexerLexerOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<FrankoLexerStaticData> frankolexerLexerStaticData = nullptr;

void frankolexerLexerInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (frankolexerLexerStaticData != nullptr) {
    return;
  }
#else
  assert(frankolexerLexerStaticData == nullptr);
#endif
  auto staticData = std::make_unique<FrankoLexerStaticData>(
    std::vector<std::string>{
      "T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
      "T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
      "T__17", "T__18", "T__19", "T__20", "IDENTIFIER", "INT_LITERAL", "WS"
    },
    std::vector<std::string>{
      "DEFAULT_TOKEN_CHANNEL", "HIDDEN"
    },
    std::vector<std::string>{
      "DEFAULT_MODE"
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
  	4,0,24,152,6,-1,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,
  	6,2,7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,
  	7,14,2,15,7,15,2,16,7,16,2,17,7,17,2,18,7,18,2,19,7,19,2,20,7,20,2,21,
  	7,21,2,22,7,22,2,23,7,23,1,0,1,0,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,2,1,2,
  	1,2,1,3,1,3,1,4,1,4,1,5,1,5,1,6,1,6,1,7,1,7,1,7,1,7,1,7,1,7,1,7,1,8,1,
  	8,1,9,1,9,1,10,1,10,1,11,1,11,1,12,1,12,1,13,1,13,1,14,1,14,1,14,1,14,
  	1,14,1,14,1,14,1,14,1,15,1,15,1,15,1,15,1,15,1,15,1,15,1,15,1,15,1,16,
  	1,16,1,16,1,16,1,16,1,16,1,16,1,16,1,16,1,16,1,17,1,17,1,17,1,17,1,17,
  	1,17,1,17,1,17,1,18,1,18,1,18,1,18,1,18,1,18,1,19,1,19,1,20,1,20,1,21,
  	1,21,5,21,136,8,21,10,21,12,21,139,9,21,1,22,4,22,142,8,22,11,22,12,22,
  	143,1,23,4,23,147,8,23,11,23,12,23,148,1,23,1,23,0,0,24,1,1,3,2,5,3,7,
  	4,9,5,11,6,13,7,15,8,17,9,19,10,21,11,23,12,25,13,27,14,29,15,31,16,33,
  	17,35,18,37,19,39,20,41,21,43,22,45,23,47,24,1,0,4,3,0,65,90,95,95,97,
  	122,4,0,48,57,65,90,95,95,97,122,1,0,48,57,3,0,9,10,13,13,32,32,154,0,
  	1,1,0,0,0,0,3,1,0,0,0,0,5,1,0,0,0,0,7,1,0,0,0,0,9,1,0,0,0,0,11,1,0,0,
  	0,0,13,1,0,0,0,0,15,1,0,0,0,0,17,1,0,0,0,0,19,1,0,0,0,0,21,1,0,0,0,0,
  	23,1,0,0,0,0,25,1,0,0,0,0,27,1,0,0,0,0,29,1,0,0,0,0,31,1,0,0,0,0,33,1,
  	0,0,0,0,35,1,0,0,0,0,37,1,0,0,0,0,39,1,0,0,0,0,41,1,0,0,0,0,43,1,0,0,
  	0,0,45,1,0,0,0,0,47,1,0,0,0,1,49,1,0,0,0,3,51,1,0,0,0,5,57,1,0,0,0,7,
  	61,1,0,0,0,9,63,1,0,0,0,11,65,1,0,0,0,13,67,1,0,0,0,15,69,1,0,0,0,17,
  	76,1,0,0,0,19,78,1,0,0,0,21,80,1,0,0,0,23,82,1,0,0,0,25,84,1,0,0,0,27,
  	86,1,0,0,0,29,88,1,0,0,0,31,96,1,0,0,0,33,105,1,0,0,0,35,115,1,0,0,0,
  	37,123,1,0,0,0,39,129,1,0,0,0,41,131,1,0,0,0,43,133,1,0,0,0,45,141,1,
  	0,0,0,47,146,1,0,0,0,49,50,5,59,0,0,50,2,1,0,0,0,51,52,5,97,0,0,52,53,
  	5,108,0,0,53,54,5,108,0,0,54,55,5,111,0,0,55,56,5,99,0,0,56,4,1,0,0,0,
  	57,58,5,100,0,0,58,59,5,101,0,0,59,60,5,108,0,0,60,6,1,0,0,0,61,62,5,
  	61,0,0,62,8,1,0,0,0,63,64,5,40,0,0,64,10,1,0,0,0,65,66,5,41,0,0,66,12,
  	1,0,0,0,67,68,5,46,0,0,68,14,1,0,0,0,69,70,5,117,0,0,70,71,5,110,0,0,
  	71,72,5,105,0,0,72,73,5,110,0,0,73,74,5,105,0,0,74,75,5,116,0,0,75,16,
  	1,0,0,0,76,77,5,91,0,0,77,18,1,0,0,0,78,79,5,93,0,0,79,20,1,0,0,0,80,
  	81,5,42,0,0,81,22,1,0,0,0,82,83,5,47,0,0,83,24,1,0,0,0,84,85,5,43,0,0,
  	85,26,1,0,0,0,86,87,5,45,0,0,87,28,1,0,0,0,88,89,5,105,0,0,89,90,5,110,
  	0,0,90,91,5,116,0,0,91,92,5,51,0,0,92,93,5,50,0,0,93,94,5,95,0,0,94,95,
  	5,116,0,0,95,30,1,0,0,0,96,97,5,117,0,0,97,98,5,105,0,0,98,99,5,110,0,
  	0,99,100,5,116,0,0,100,101,5,51,0,0,101,102,5,50,0,0,102,103,5,95,0,0,
  	103,104,5,116,0,0,104,32,1,0,0,0,105,106,5,102,0,0,106,107,5,108,0,0,
  	107,108,5,111,0,0,108,109,5,97,0,0,109,110,5,116,0,0,110,111,5,51,0,0,
  	111,112,5,50,0,0,112,113,5,95,0,0,113,114,5,116,0,0,114,34,1,0,0,0,115,
  	116,5,99,0,0,116,117,5,104,0,0,117,118,5,97,0,0,118,119,5,114,0,0,119,
  	120,5,56,0,0,120,121,5,95,0,0,121,122,5,116,0,0,122,36,1,0,0,0,123,124,
  	5,97,0,0,124,125,5,114,0,0,125,126,5,114,0,0,126,127,5,97,0,0,127,128,
  	5,121,0,0,128,38,1,0,0,0,129,130,5,60,0,0,130,40,1,0,0,0,131,132,5,62,
  	0,0,132,42,1,0,0,0,133,137,7,0,0,0,134,136,7,1,0,0,135,134,1,0,0,0,136,
  	139,1,0,0,0,137,135,1,0,0,0,137,138,1,0,0,0,138,44,1,0,0,0,139,137,1,
  	0,0,0,140,142,7,2,0,0,141,140,1,0,0,0,142,143,1,0,0,0,143,141,1,0,0,0,
  	143,144,1,0,0,0,144,46,1,0,0,0,145,147,7,3,0,0,146,145,1,0,0,0,147,148,
  	1,0,0,0,148,146,1,0,0,0,148,149,1,0,0,0,149,150,1,0,0,0,150,151,6,23,
  	0,0,151,48,1,0,0,0,4,0,137,143,148,1,6,0,0
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  frankolexerLexerStaticData = std::move(staticData);
}

}

FrankoLexer::FrankoLexer(CharStream *input) : Lexer(input) {
  FrankoLexer::initialize();
  _interpreter = new atn::LexerATNSimulator(this, *frankolexerLexerStaticData->atn, frankolexerLexerStaticData->decisionToDFA, frankolexerLexerStaticData->sharedContextCache);
}

FrankoLexer::~FrankoLexer() {
  delete _interpreter;
}

std::string FrankoLexer::getGrammarFileName() const {
  return "Franko.g4";
}

const std::vector<std::string>& FrankoLexer::getRuleNames() const {
  return frankolexerLexerStaticData->ruleNames;
}

const std::vector<std::string>& FrankoLexer::getChannelNames() const {
  return frankolexerLexerStaticData->channelNames;
}

const std::vector<std::string>& FrankoLexer::getModeNames() const {
  return frankolexerLexerStaticData->modeNames;
}

const dfa::Vocabulary& FrankoLexer::getVocabulary() const {
  return frankolexerLexerStaticData->vocabulary;
}

antlr4::atn::SerializedATNView FrankoLexer::getSerializedATN() const {
  return frankolexerLexerStaticData->serializedATN;
}

const atn::ATN& FrankoLexer::getATN() const {
  return *frankolexerLexerStaticData->atn;
}




void FrankoLexer::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  frankolexerLexerInitialize();
#else
  ::antlr4::internal::call_once(frankolexerLexerOnceFlag, frankolexerLexerInitialize);
#endif
}
