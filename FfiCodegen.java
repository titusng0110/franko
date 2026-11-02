import java.util.List;

final class FfiCodegen {

    private final FfiHandler handler;

    FfiCodegen(FfiHandler handler) {
        this.handler = handler;
    }

    boolean isBridgedFunction(FunctionSymbol function) {
        return handler.matchFunction(function) != null;
    }

    FfiMatch matchFunction(FunctionSymbol function) {
        return handler.matchFunction(function);
    }

    String bridgePrelude(List<FfiMatch> used) {
        return handler.buildBridge(used);
    }
}
