#include <iostream>
#include <fstream>

#include "antlr4-runtime.h"
#include "FrankoLexer.h"
#include "FrankoParser.h"

using namespace antlr4;

int main(int argc, const char* argv[]) {
    // ✅ Input file (default = test.fr)
    std::string filename = "test.fr";
    if (argc > 1) {
        filename = argv[1];
    }

    std::ifstream stream(filename);
    if (!stream.is_open()) {
        std::cerr << "Error: cannot open file " << filename << std::endl;
        return 1;
    }

    // ✅ ANTLR pipeline
    ANTLRInputStream input(stream);
    FrankoLexer lexer(&input);
    CommonTokenStream tokens(&lexer);
    tokens.fill();  // optional but useful for debugging

    FrankoParser parser(&tokens);

    // ✅ Parse entry rule
    tree::ParseTree* tree = parser.program();

    // ✅ Print parse tree (like grun -tree)
    std::cout << tree->toStringTree(&parser) << std::endl;

    return 0;
}