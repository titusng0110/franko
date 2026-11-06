import java.math.BigInteger;
import java.util.List;

final class Cpp14ExpressionEmitter {

    private Cpp14Codegen owner;

    String emitExpr(Cpp14Codegen owner, SemanticExprNode node) {
        this.owner = owner;

        if (node == null) {
            throw new IllegalStateException(
                    "Internal compiler error: cannot emit null expression"
            );
        }

        if (node instanceof SemanticIntLiteralNode literal) {
            return emitIntLiteral(literal);
        }

        if (node instanceof SemanticNullLiteralNode) {

            return "nullptr";
        }

        if (node instanceof SemanticVarExprNode var) {
            return owner.emitSymbolName(var.symbol);
        }

        if (node instanceof SemanticUnaryOpNode unary) {
            return emitUnaryOp(unary);
        }

        if (node instanceof SemanticBinOpNode bin) {
            return emitBinOp(bin);
        }

        if (node instanceof SemanticArrayAccessNode access) {
            return emitArrayAccess(access);
        }

        if (node instanceof SemanticFunctionCallNode call) {
            return emitFunctionCall(call);
        }

        if (node instanceof SemanticArrayIntrinsicCallNode intrinsic) {
            return emitArrayIntrinsicCall(intrinsic);
        }

        if (node instanceof SemanticAggregateLiteralNode aggregate) {
            return emitAggregateLiteral(aggregate);
        }

        if (node instanceof SemanticGetPtrNode getPtr) {
            return emitGetPtr(getPtr);
        }

        if (node instanceof SemanticDerefNode deref) {
            return emitDeref(deref);
        }

        if (node instanceof SemanticCastExprNode cast) {
            return emitCast(cast);
        }

        if (node instanceof SemanticEnumVariantNode enumVariant) {
            return owner.emitEnumVariant(enumVariant);
        }

        if (node instanceof SemanticMemberAccessNode member) {
            return emitExpr(owner, member.target) + "." + member.field.emittedName();
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported semantic expression for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    private String emitIntLiteral(SemanticIntLiteralNode node) {

        String raw = node.rawValue;

        if (isPlainDecimal(raw)
                && node.constantValue != null
                && node.constantValue.compareTo(
                        BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            return raw + "ULL";
        }

        return raw;
    }

    private boolean isPlainDecimal(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        if (raw.startsWith("0x") || raw.startsWith("0X")
                || raw.startsWith("0b") || raw.startsWith("0B")) {
            return false;
        }

        return true;
    }

    String emitBigIntegerLiteral(
            Cpp14Codegen owner,
            BigInteger value
    ) {
        this.owner = owner;
        boolean negative = value.signum() < 0;

        BigInteger magnitude = value.abs();
        String digits = magnitude.toString();

        boolean needsUllSuffix =
                magnitude.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0;

        String core = needsUllSuffix ? digits + "ULL" : digits;

        return negative ? "-" + core : core;
    }

    private String emitUnaryOp(SemanticUnaryOpNode node) {

        if (node.isConstant() && node.constantValue != null) {
            return emitBigIntegerLiteral(owner, node.constantValue);
        }

        String inner = emitExpr(owner, node.expr);
        String raw = "(" + node.op + inner + ")";

        return castToType(owner, node, raw);
    }

    private String emitBinOp(SemanticBinOpNode node) {

        if (node.isConstant() && node.constantValue != null) {
            return emitBigIntegerLiteral(owner, node.constantValue);
        }

        String left = emitExpr(owner, node.left);
        String right = emitExpr(owner, node.right);

        String raw = "(" + left + " " + node.op + " " + right + ")";

        return castToType(owner, node, raw);
    }

    private String emitArrayAccess(SemanticArrayAccessNode node) {
        String index = emitAsType(owner, node.index, Cpp14Codegen.UINT32_TYPE);

        if (node.target instanceof SemanticDerefNode deref) {
            return "(" + emitExpr(owner, deref.expr) + "[" + index + "])";
        }

        if (node.target.type.kind == SastTypeKind.STATIC_ARRAY
                && !owner.uncheckedStaticArrayAccess) {
            return "(franko_static_array_at("
                    + emitExpr(owner, node.target) + ", " + index + "))";
        }

        return "(" + emitExpr(owner, node.target) + "[" + index + "])";
    }

    private String emitFunctionCall(SemanticFunctionCallNode node) {
        Symbol fn = node.function;

        if (fn == null) {
            throw new IllegalStateException(
                    "Internal compiler error: function call has null resolved function"
            );
        }

        if (owner.ffiHandler != null) {
            FfiMatch match = owner.ffiHandler.matchFunction(fn);

            if (match != null) {
                owner.usedFfiMatches.put(match.key(), match);
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append(owner.emitFunctionName(fn));
        sb.append("(");

        List<SastType> parameterTypes = fn.parameterTypes();

        if (parameterTypes.size() != node.args.size()) {
            throw new IllegalStateException(
                    "Internal compiler error: resolved function call arity mismatch for "
                            + fn.fullSignatureString()
                            + ": expected "
                            + parameterTypes.size()
                            + ", got "
                            + node.args.size()
            );
        }

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            SemanticExprNode arg = node.args.get(i);
            SastType paramType = parameterTypes.get(i);

            sb.append(emitAsType(owner, arg, paramType));
        }

        sb.append(")");

        return sb.toString();
    }

    private String emitArrayIntrinsicCall(SemanticArrayIntrinsicCallNode node) {
        if (node.kind == null) {
            throw new IllegalStateException(
                    "Internal compiler error: array intrinsic call has null kind"
            );
        }

        if (node.kind == SemanticArrayIntrinsicKind.LENGTH) {
            return emitArrayLengthCall(node);
        }

        validateArrayIntrinsicArityForCodegen(node);

        StringBuilder sb = new StringBuilder();

        sb.append(emitIntrinsicReceiver(node));
        sb.append(".");
        sb.append(node.kind.runtimeName);
        sb.append("(");

        for (int i = 0; i < node.args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }

            sb.append(emitArrayIntrinsicArg(node, i));
        }

        sb.append(")");

        return sb.toString();
    }

    private String emitIntrinsicReceiver(SemanticArrayIntrinsicCallNode node) {
        if (node.kind.dynamicOnly) {
            return emitLValue(owner, node.receiver);
        }

        if (node.receiver instanceof SemanticDerefNode deref) {
            return emitExpr(owner, deref.expr);
        }

        if (node.receiver.type.kind == SastTypeKind.STATIC_ARRAY) {
            return "franko_make_fat_pointer("
                    + emitLValue(owner, node.receiver)
                    + ", "
                    + node.receiver.type.sizeLiteral
                    + ", 2)";
        }

        if (node.receiver.type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return "franko_make_dynamic_fat_pointer(&"
                    + emitLValue(owner, node.receiver)
                    + ")";
        }

        throw new IllegalStateException(
                "Internal compiler error: array intrinsic receiver is not an array"
        );
    }

    private String emitArrayLengthCall(SemanticArrayIntrinsicCallNode node) {
        if (!node.args.isEmpty()) {
            throw new IllegalStateException(
                    "Internal compiler error: invalid arity for array intrinsic 'length'"
            );
        }

        if (node.receiver instanceof SemanticDerefNode deref) {
            return emitExpr(owner, deref.expr) + ".length()";
        }

        if (node.receiver.type.kind == SastTypeKind.STATIC_ARRAY) {
            return "static_cast<uint32_t>(" + node.receiver.type.sizeLiteral + ")";
        }

        if (node.receiver.type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return emitExpr(owner, node.receiver) + ".length()";
        }

        throw new IllegalStateException(
                "Internal compiler error: length() receiver is not an array"
        );
    }

    private String emitArrayIntrinsicArg(
            SemanticArrayIntrinsicCallNode node,
            int index
    ) {
        SemanticExprNode arg = node.args.get(index);
        SemanticIntrinsicArgKind argKind = node.kind.argKind(index);

        if (argKind.isPointer()) {
            return emitExpr(owner, arg);
        }

        return emitAsType(owner, arg, argKind.primitiveType());
    }

    private void validateArrayIntrinsicArityForCodegen(
            SemanticArrayIntrinsicCallNode node
    ) {
        if (!node.kind.acceptsArity(node.args.size())) {
            throw new IllegalStateException(
                    "Internal compiler error: invalid arity for array intrinsic '"
                            + node.kind.runtimeName
                            + "': got "
                            + node.args.size()
            );
        }
    }

    private String emitAggregateLiteral(
            SemanticAggregateLiteralNode node
    ) {
        String type = owner.emitType(node.type);
        StringBuilder sb = new StringBuilder();

        sb.append("([&]() -> ").append(type).append(" { ");
        sb.append(type).append(" _franko_literal; ");

        for (SemanticAggregateFieldInit init : node.fields) {
            sb.append("_franko_literal.")
              .append(init.field.emittedName())
              .append(" = ")
              .append(emitAsType(owner, init.value, init.field.type))
              .append("; ");
        }

        sb.append("return _franko_literal; }())");
        return sb.toString();
    }

    private String emitGetPtr(SemanticGetPtrNode node) {
        if (node.temporary) {
            String value = emitExpr(owner, node.target);

            if (node.target.isConstant()
                    && node.type.kind == SastTypeKind.PTR
                    && node.type.referencedType.kind == SastTypeKind.PRIMITIVE
                    && node.target.type.kind == SastTypeKind.PRIMITIVE
                    && !node.type.referencedType.equals(node.target.type)) {
                value = "static_cast<"
                        + owner.emitType(node.type.referencedType)
                        + ">("
                        + value
                        + ")";
            }

            String borrowed = "franko_borrow(" + value + ")";

            if (node.type.kind == SastTypeKind.PTR
                    && !(node.type.referencedType.kind == SastTypeKind.STATIC_ARRAY)
                    && !(node.type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY)) {
                return "franko_make_thin_pointer(" + borrowed + ")";
            }

            return borrowed;
        }

        if (node.target instanceof SemanticDerefNode deref) {
            return emitExpr(owner, deref.expr);
        }

        if (node.target.type.kind == SastTypeKind.STATIC_ARRAY) {
            return "franko_make_fat_pointer("
                    + emitLValue(owner, node.target)
                    + ", "
                    + node.target.type.sizeLiteral
                    + ", 2)";
        }

        if (node.target.type.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return "franko_make_dynamic_fat_pointer(&"
                    + emitLValue(owner, node.target)
                    + ")";
        }

        return "franko_make_thin_pointer(&" + emitLValue(owner, node.target) + ")";
    }

    private String emitDeref(SemanticDerefNode node) {
        if (!(node.expr.type.kind == SastTypeKind.PTR)) {
            throw new IllegalStateException(
                    "Internal compiler error: dereference operand is not a pointer type"
            );
        }

        String pointer = emitExpr(owner, node.expr);

        if (node.expr.type.referencedType.kind == SastTypeKind.STATIC_ARRAY) {
            String elementType = owner.emitType(node.expr.type.referencedType.elementType);

            return "franko_static_array_ref<"
                    + elementType
                    + ", "
                    + node.expr.type.referencedType.sizeLiteral
                    + ">(static_cast<"
                    + elementType
                    + "*>("
                    + pointer
                    + ".data))";
        }

        if (node.expr.type.referencedType.kind == SastTypeKind.DYNAMIC_ARRAY) {
            return "(*static_cast<Franko_Dynamic_Array<"
                    + owner.emitType(node.expr.type.referencedType.elementType)
                    + ">*>("
                    + pointer
                    + ".data))";
        }

        return "(*(" + pointer + ").data)";
    }

    private String emitCast(SemanticCastExprNode node) {

        if (node.type.kind == SastTypeKind.ENUM) {
            String raw = emitExpr(owner, node.expr);

            if (node.expr.type.kind == SastTypeKind.ENUM
                    && node.expr.type.enumSymbol == node.type.enumSymbol) {
                return raw;
            }

            return owner.emitType(node.type) + "::from_integer_checked(" + raw + ")";
        }

        if (node.expr.type.kind == SastTypeKind.ENUM) {
            String raw = emitExpr(owner, node.expr);
            String valueAccess = "(" + raw + ").value";

            if (!node.checked) {
                return "static_cast<" + owner.emitType(node.type) + ">(" + valueAccess + ")";
            }

            return "franko_integer_unsafe_cast<" + owner.emitType(node.type) + ">(" + valueAccess + ")";
        }

        if (node.isConstant() && node.constantValue != null) {
            return emitBigIntegerLiteral(owner, node.constantValue);
        }

        String raw = emitExpr(owner, node.expr);

        if (!node.checked) {

            return "static_cast<" + owner.emitType(node.type) + ">(" + raw + ")";
        }

        return "franko_integer_unsafe_cast<" + owner.emitType(node.type) + ">(" + raw + ")";
    }

    String emitLValue(Cpp14Codegen owner, SemanticExprNode node) {
        this.owner = owner;

        if (node instanceof SemanticVarExprNode var) {
            return owner.emitSymbolName(var.symbol);
        }

        if (node instanceof SemanticArrayAccessNode access) {
            return emitArrayAccess(access);
        }

        if (node instanceof SemanticDerefNode deref) {
            return emitDeref(deref);
        }

        if (node instanceof SemanticMemberAccessNode member) {
            return emitExpr(owner, member.target) + "." + member.field.emittedName();
        }

        throw new IllegalStateException(
                "Internal compiler error: unsupported lvalue expression for codegen: "
                        + node.getClass().getSimpleName()
        );
    }

    String emitAsType(
            Cpp14Codegen owner,
            SemanticExprNode expr,
            SastType targetType
    ) {
        this.owner = owner;
        String raw = emitExpr(owner, expr);

        if (targetType.kind == SastTypeKind.PRIMITIVE) {

            if (expr.isConstant()) {
                return "static_cast<" + owner.emitType(targetType) + ">(" + raw + ")";
            }

            if (expr.type != null && expr.type.equals(targetType)) {
                return raw;
            }

            return "static_cast<" + owner.emitType(targetType) + ">(" + raw + ")";
        }

        return raw;
    }

    String castToType(
            Cpp14Codegen owner,
            SemanticExprNode node,
            String raw
    ) {
        this.owner = owner;

        if (node.type.kind == SastTypeKind.PRIMITIVE) {
            return "static_cast<" + owner.emitType(node.type) + ">(" + raw + ")";
        }

        return raw;
    }
}
