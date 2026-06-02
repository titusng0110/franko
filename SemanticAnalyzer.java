import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SemanticAnalyzer orchestrates the semantic pass by delegating work to:
 *   - DeclarationChecker
 *   - ExpressionChecker
 *   - StatementChecker
 *   - TypeChecker
 */
public class SemanticAnalyzer {

    public static final class SemanticException extends RuntimeException {
        public SemanticException(String message) {
            super(message);
        }
    }

    static final class Symbol {
        final TypeNode type;
        final boolean isHeap;
        boolean deleted;

        Symbol(TypeNode type, boolean isHeap) {
            this.type = type;
            this.isHeap = isHeap;
            this.deleted = false;
        }
    }

    static final class Context {
        private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
        private final List<String> errors = new ArrayList<>();

        void clear() {
            scopes.clear();
            errors.clear();
        }

        void pushScope() {
            scopes.push(new LinkedHashMap<>());
        }

        void popScope() {
            scopes.pop();
        }

        void declare(String name, TypeNode type, boolean isHeap) {
            Map<String, Symbol> current = scopes.peek();
            if (current == null) {
                throw new IllegalStateException("Internal compiler error: no active scope when declaring '" + name + "'");
            }
            if (current.containsKey(name)) {
                error("Duplicate declaration of variable '" + name + "' in the same scope");
                return;
            }
            current.put(name, new Symbol(type, isHeap));
        }

        Symbol resolve(String name) {
            for (Map<String, Symbol> scope : scopes) {
                Symbol sym = scope.get(name);
                if (sym != null) {
                    return sym;
                }
            }
            return null;
        }

        void error(String msg) {
            errors.add(msg);
        }

        List<String> getErrors() {
            return List.copyOf(errors);
        }

        boolean hasErrors() {
            return !errors.isEmpty();
        }

        String formatErrors() {
            StringBuilder sb = new StringBuilder();
            sb.append("Semantic analysis failed with ")
              .append(errors.size())
              .append(" error(s):\n");
            for (int i = 0; i < errors.size(); i++) {
                sb.append("  ")
                  .append(i + 1)
                  .append(". ")
                  .append(errors.get(i))
                  .append('\n');
            }
            return sb.toString();
        }
    }

    private final Context ctx;
    private final TypeChecker typeChecker;
    private final ExpressionChecker expressionChecker;
    private final DeclarationChecker declarationChecker;
    private final StatementChecker statementChecker;

    public SemanticAnalyzer() {
        this.ctx = new Context();
        this.typeChecker = new TypeChecker(ctx);
        this.expressionChecker = new ExpressionChecker(ctx, typeChecker);
        this.declarationChecker = new DeclarationChecker(ctx, expressionChecker, typeChecker);
        this.statementChecker = new StatementChecker(ctx, declarationChecker, expressionChecker, typeChecker);
    }

    public void analyze(ASTNode root) {
        ctx.clear();
        ctx.pushScope();
        statementChecker.visitStatement(root);
        ctx.popScope();

        if (ctx.hasErrors()) {
            throw new SemanticException(ctx.formatErrors());
        }
    }

    public List<String> getErrors() {
        return ctx.getErrors();
    }
}
