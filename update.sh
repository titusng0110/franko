#!/usr/bin/env bash
set -e

ANTLR_JAR="/usr/local/lib/antlr-4.13.2-complete.jar"
ANTLR_CP=".:$ANTLR_JAR:${CLASSPATH:-}"

rm -f *.class out.out out.cpp
java -Xmx500M -cp "$ANTLR_CP" org.antlr.v4.Tool -visitor Franko.g4
javac *.java
java Main
g++ -std=c++14 -Wall -Wextra -Wpedantic -Wshadow out.cpp -o out.out
./out.out