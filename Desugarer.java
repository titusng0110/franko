import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Desugarer {

    private final DiagnosticBag diagnostics;

    private final Map<String, AstNode> hoistedStringGlobals;

    public Desugarer() {
        this(new DiagnosticBag(), new LinkedHashMap<>());
    }

    public Desugarer(DiagnosticBag diagnostics) {
        this(diagnostics, new LinkedHashMap<>());
    }

    public Desugarer(
            DiagnosticBag diagnostics,
            Map<String, AstNode> hoistedStringGlobals
    ) {
        this.diagnostics = diagnostics == null
                ? new DiagnosticBag()
                : diagnostics;
        this.hoistedStringGlobals = hoistedStringGlobals == null
                ? new LinkedHashMap<>()
                : hoistedStringGlobals;
    }

    private static final class InferenceFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        InferenceFailure(String message) {
            super(message);
        }
    }

    public AstNode desugar(AstNode root) {
        if (root == null) return null;

        if (root.kind == AstKind.PROGRAM) {
            List<AstNode> items =
                desugarTopLevelItemList(root.items);

            items.addAll(0, hoistedStringGlobals.values());

            return AstNode.program(root.span, items);
        }

        return desugarStmt(root);
    }

    private AstNode hoistStringLiteral(AstNode literal) {
        List<BigInteger> bytes =
            StringLiteralDecoder.toUtf8Bytes(literal);

        if (bytes.isEmpty()) {
            return AstNode.nullLiteral(literal.span);
        }

        StringBuilder contentKey = new StringBuilder();

        for (BigInteger b : bytes) {
            contentKey.append(b.toString(10)).append(',');
        }

        String key = contentKey.toString();

        AstNode decl = hoistedStringGlobals.get(key);

        if (decl == null) {
            decl = AstNode.globalVarDecl(
                literal.span,
                AstNode.staticArrayType(
                    literal.span,
                    AstNode.primitiveType(literal.span, PrimitiveKind.UINT8),
                    AstNode.intLiteral(literal.span, Integer.toString(bytes.size()))
                ),
                "STRLIT_" + Mangler.hashHex64(key),
                null
            );

            decl.literalBytes = bytes;
            hoistedStringGlobals.put(key, decl);
        }

        return AstNode.getPtr(
            literal.span,
            PtrPermission.READ_ONLY,
            AstNode.var(literal.span, decl.name)
        );
    }

    private List<AstNode> desugarTopLevelItemList(List<AstNode> items) {
        List<AstNode> out = new ArrayList<>();

        for (AstNode item : items) {
            appendDesugaredTopLevelItem(item, out);
        }

        return out;
    }

    AstNode desugarSingleTopLevelItem(AstNode item) {
        List<AstNode> out = new ArrayList<>();
        appendDesugaredTopLevelItem(item, out);
        return out.isEmpty() ? null : out.get(0);
    }

    List<AstNode> hoistedStringGlobalNodes() {
        return new ArrayList<>(hoistedStringGlobals.values());
    }

    protected void appendDesugaredTopLevelItem(AstNode item, List<AstNode> out) {
        if (item == null) return;

        diagnostics.push(item.span);
        try {

        if (item.kind == AstKind.IMPORT) {
            out.add(item);
            return;
        }

        if (item.kind == AstKind.FUNCTION_DECL) {
            out.add(desugarFunctionDecl(item));
            return;
        }

        if (item.kind == AstKind.STRUCT_DECL) {
            out.add(desugarStructDecl(item));
            return;
        }

        if (item.kind == AstKind.ENUM_DECL) {
            out.add(desugarEnumDecl(item));
            return;
        }

        if (item.kind == AstKind.GLOBAL_VAR_DECL) {
            out.add(desugarGlobalVarDecl(item));
            return;
        }

        if (item.kind == AstKind.NAMESPACE) {
            out.add(desugarNamespaceDecl(item));
            return;
        }

        if (item.kind == AstKind.DECL_IF) {
            out.add(desugarDeclIfTopLevel(item));
            return;
        }

        if (item.kind == AstKind.COMPILE_TIME_CALL
                || item.kind == AstKind.COMPILE_TIME_NAME) {
            out.add(desugarExpr(item));
            return;
        }

        appendDesugaredStmt(item, out);
        } finally {
            diagnostics.pop();
        }
    }

    private AstNode desugarNamespaceDecl(AstNode n) {
        return AstNode.namespace(n.span, n.name, desugarTopLevelItemList(n.items));
    }

    private AstNode desugarDeclIfTopLevel(AstNode n) {
        return AstNode.declIf(
            n.span,
            desugarExpr(n.condition),
            desugarTopLevelItemList(n.thenItems),
            desugarTopLevelItemList(n.elseItems)
        );
    }

    private AstNode desugarDeclIfStruct(AstNode n) {
        return AstNode.declIf(
            n.span,
            desugarExpr(n.condition),
            desugarStructMemberList(n.thenItems),
            desugarStructMemberList(n.elseItems)
        );
    }

    private List<AstNode> desugarStructMemberList(List<AstNode> items) {
        List<AstNode> out = new ArrayList<>();

        for (AstNode item : items) {
            out.add(desugarStructMember(item));
        }

        return out;
    }

    private AstNode desugarStructMember(AstNode item) {
        if (item.kind == AstKind.STRUCT_FIELD_DECL) {
            if (item.isConst) {
                diagnostics.error("Struct fields cannot be const. A struct is "
                    + "made const as a whole through its declaration or "
                    + "receiver, and individual fields cannot carry a separate "
                    + "const qualifier. Remove 'const' from the field and make "
                    + "the struct value const where needed.");
            }

            return AstNode.structFieldDecl(
                item.span,
                desugarType(item.type),
                item.name,
                evalConstExpr(item.value, "struct field initializer")
            );
        }

        if (item.kind == AstKind.FUNCTION_DECL) {
            return desugarFunctionDecl(item);
        }

        if (item.kind == AstKind.DECL_IF) {
            return desugarDeclIfStruct(item);
        }

        if (item.kind == AstKind.COMPILE_TIME_CALL
                || item.kind == AstKind.COMPILE_TIME_NAME) {
            return desugarExpr(item);
        }

        throw new RuntimeException(
            "Unsupported struct member in desugarer: "
                + item.kind.jsonName()
        );
    }

    private AstNode desugarStructDecl(AstNode n) {
        List<AstNode> fields = new ArrayList<>();
        List<AstNode> methods = new ArrayList<>();
        List<AstNode> members = new ArrayList<>();

        for (AstNode member : n.members) {
            AstNode desugared = desugarStructMember(member);
            members.add(desugared);

            if (desugared.kind == AstKind.STRUCT_FIELD_DECL) {
                fields.add(desugared);
            } else if (desugared.kind == AstKind.FUNCTION_DECL) {
                methods.add(desugared);
            }
        }

        AstNode node = AstNode.structDecl(
            n.span,
            n.name,
            fields,
            methods,
            members,
            n.genericParams
        );
        node.genericHash = n.genericHash;
        node.genericBaseName = n.genericBaseName;
        return node;
    }

    private AstNode desugarEnumDecl(AstNode n) {
        AstNode backing = desugarType(n.type);
        List<AstNode> variants = new ArrayList<>();

        for (AstNode variant : n.variants) {
            AstNode folded = null;

            if (variant.value != null) {
                folded = evalConstExpr(variant.value, "enum variant value");
            }

            variants.add(AstNode.enumVariant(variant.span, variant.name, folded));
        }

        return AstNode.enumDecl(n.span, n.name, backing, variants);
    }

    private AstNode desugarGlobalVarDecl(AstNode n) {
        if (usesInferredSize(n.type)) {
            diagnostics.error("'?' array size is not allowed for global variable '"
                + n.name
                + "'. Global storage is laid out before main runs, and '?' "
                + "can only be inferred from a local declaration's "
                + "array-literal initializer. Replace '?' with an explicit "
                + "size or move the declaration into a function.");

            return globalVarDecl(
                n,
                desugarType(replaceInferredSizesWithFallback(n.type)),
                evalConstExpr(n.value, "global variable initializer")
            );
        }

        AstNode type = desugarType(n.type);
        AstNode init = n.value;

        if (init != null && init.kind == AstKind.STRING_LITERAL) {
            AstNode hoisted = hoistStringLiteral(init);

            if (type.kind != AstKind.PTR_TYPE) {
                diagnostics.error("Global variable '"
                    + n.name
                    + "': a string literal is a view<[byte, N]> value "
                    + "and cannot initialize a non-pointer global. The global "
                    + "itself needs pointer storage to hold the view produced "
                    + "by the literal. Declare the global as a pointer type "
                    + "such as view<[byte, N]> or initialize it with a value "
                    + "of its declared type.");

                return globalVarDecl(n, type, null);
            }

            if (hoisted.kind == AstKind.NULL) {
                return globalVarDecl(n, type, null);
            }

            return globalVarDecl(n, type, hoisted);
        }

        if (init != null && init.kind == AstKind.ARRAY_LITERAL) {
            if (type.kind != AstKind.PTR_TYPE) {
                diagnostics.error("Global variable '"
                    + n.name
                    + "': only pointer-typed globals may be initialized with "
                    + "an array literal. An array literal is a read-only "
                    + "view<[T, N]> value, not an array value, so it cannot "
                    + "initialize array or scalar storage. Declare the global "
                    + "as a view<[T, N]> or copy the literal into an array "
                    + "with memcpy.");

                return globalVarDecl(n, type, null);
            }

            if (init.elements.isEmpty()) {
                return globalVarDecl(n, type, null);
            }

            return globalVarDecl(n, type, desugarExpr(init));
        }

        if (init != null && init.kind == AstKind.GET_PTR) {
            if (type.kind != AstKind.PTR_TYPE) {
                diagnostics.error("Global variable '"
                    + n.name
                    + "': only pointer-typed globals may be initialized with "
                    + "getptr/getmview/getview. These operations produce a "
                    + "pointer or view, so a non-pointer global has no "
                    + "compatible storage for the result. Change the global's "
                    + "type to the matching ptr, view, or mview type.");

                return globalVarDecl(n, type, null);
            }

            return globalVarDecl(n, type, desugarExpr(init));
        }

        if (init != null && init.kind == AstKind.NULL) {
            if (type.kind != AstKind.PTR_TYPE) {
                diagnostics.error("Global variable '"
                    + n.name
                    + "': null is only valid for pointer types. Franko uses "
                    + "null specifically as the absent pointer value, and "
                    + "scalar or aggregate globals have no null state. Declare "
                    + "the global with a ptr, view, or mview type or initialize "
                    + "it with a concrete value.");

                return globalVarDecl(n, type, null);
            }

            return globalVarDecl(n, type, null);
        }

        return globalVarDecl(
            n,
            type,
            evalConstExpr(init, "global variable initializer")
        );
    }

    private AstNode globalVarDecl(
        AstNode source,
        AstNode type,
        AstNode init
    ) {
        AstNode node = AstNode.globalVarDecl(
            source.span,
            type,
            source.name,
            init,
            source.isConst
        );
        node.compileTimeConstant = source.compileTimeConstant;
        return node;
    }

    private AstNode desugarFunctionDecl(AstNode fn) {
        List<AstNode> parameters = new ArrayList<>();

        for (AstNode param : fn.parameters) {
            parameters.add(desugarParameter(param));
        }

        AstNode body = null;
        if (fn.body != null) {
            body = AstNode.block(fn.body.span, desugarStmtList(fn.body.items));
        }

        AstNode node = AstNode.functionDecl(
            fn.span,
            fn.name,
            parameters,
            desugarType(fn.type),
            body,
            fn.inlineMode,
            fn.genericParams
        );
        node.genericGroup = fn.genericGroup;
        node.genericHash = fn.genericHash;
        return node;
    }

    private AstNode desugarParameter(AstNode param) {
        return AstNode.parameter(
            param.span,
            desugarType(param.type),
            param.name,
            param.isConst,
            param.defaultValue == null
                    ? null
                    : desugarExpr(param.defaultValue)
        );
    }

    private List<AstNode> desugarStmtList(List<AstNode> stmts) {
        List<AstNode> out = new ArrayList<>();

        for (AstNode stmt : stmts) {
            appendDesugaredStmt(stmt, out);
        }

        return out;
    }

    private void appendDesugaredStmt(AstNode stmt, List<AstNode> out) {
        if (stmt == null) return;

        diagnostics.push(stmt.span);
        try {

        if (stmt.kind == AstKind.VAR_DECL_INIT) {
            appendDesugaredVarDeclInit(stmt, out);
            return;
        }

        if (stmt.kind == AstKind.VAR_DECL_FIELDS_INIT) {
            appendDesugaredVarDeclFieldsInit(stmt, out);
            return;
        }

        if (stmt.kind == AstKind.ASSIGN) {
            appendDesugaredAssign(stmt, out);
            return;
        }

        if (stmt.kind == AstKind.INC_DEC) {
            out.add(lowerIncDec(stmt));
            return;
        }

        if (stmt.kind == AstKind.BLOCK) {
            out.add(AstNode.block(stmt.span, desugarStmtList(stmt.items)));
            return;
        }

        if (stmt.kind == AstKind.COMPILE_TIME_IF) {
            out.add(desugarCompileTimeIf(stmt));
            return;
        }

        if (stmt.kind == AstKind.IF) {
            out.add(AstNode.ifStmt(
                stmt.span,
                desugarExpr(stmt.condition),
                desugarControlledStmt(stmt.thenBranch),
                stmt.elseBranch != null
                        ? desugarControlledStmt(stmt.elseBranch)
                        : null
            ));

            return;
        }

        if (stmt.kind == AstKind.WHILE) {
            List<AstNode> loweredUpdate = new ArrayList<>();

            for (AstNode updateStmt : stmt.updateItems) {
                appendDesugaredStmt(updateStmt, loweredUpdate);
            }

            out.add(AstNode.whileStmt(
                stmt.span,
                desugarExpr(stmt.condition),
                desugarControlledStmt(stmt.body),
                loweredUpdate
            ));

            return;
        }

        if (stmt.kind == AstKind.FOR) {
            out.add(desugarForStmt(stmt));
            return;
        }

        if (stmt.kind == AstKind.BREAK) {
            out.add(AstNode.breakStmt(stmt.span,
                stmt.level != null ? desugarExpr(stmt.level) : null
            ));
            return;
        }

        if (stmt.kind == AstKind.CONTINUE) {
            out.add(AstNode.continueStmt(stmt.span,
                stmt.level != null ? desugarExpr(stmt.level) : null
            ));
            return;
        }

        out.add(desugarStmt(stmt));
        } finally {
            diagnostics.pop();
        }
    }

    private void appendDesugaredVarDeclInit(
        AstNode n,
        List<AstNode> out
    ) {
        AstNode rawInit = n.init;

        AstNode loweredType;

        if (usesInferredSize(n.type)) {
            loweredType = inferStaticSizes(n.type, rawInit, n.name);
        } else {
            loweredType = desugarType(n.type);
        }

        out.add(AstNode.varDeclInit(
            n.span,
            loweredType,
            n.name,
            rawInit == null ? null : desugarExpr(rawInit),
            n.isConst
        ));
    }

    private void appendDesugaredVarDeclFieldsInit(
            AstNode n,
            List<AstNode> out
    ) {
        AstNode type = desugarType(n.type);

        if (type.kind != AstKind.STRUCT_TYPE
                && type.kind != AstKind.TUPLE_TYPE) {
            diagnostics.error(
                    "Field initializer requires a struct or tuple declaration. "
                    + "The '{ field = value }' initializer form only has "
                    + "meaning for aggregate types with named members. Declare "
                    + "the variable as a struct or tuple type or use '=' with "
                    + "a normal expression initializer."
            );

            out.add(AstNode.varDecl(n.span, type, n.name, n.isConst));
            return;
        }

        boolean anyPositional = false;
        boolean anyNamed = false;

        for (AstNode init : n.fields) {
            if (init.positional) {
                anyPositional = true;
            } else {
                anyNamed = true;
            }
        }

        boolean unnamedTuple = type.kind == AstKind.TUPLE_TYPE
                && allUnnamedTupleElements(type);

        if (anyPositional) {
            if (!unnamedTuple) {
                diagnostics.error("Positional field initializers are only "
                        + "allowed for all-unnamed tuple declarations. Struct "
                        + "fields and named tuple elements must be initialized "
                        + "by name so the target member is unambiguous. Name "
                        + "each entry or use a tuple type whose elements are "
                        + "all unnamed.");

                out.add(AstNode.varDeclFieldsInit(
                        n.span,
                        type,
                        n.name,
                        List.of(),
                        n.isConst
                ));
                return;
            }

            if (anyNamed) {
                diagnostics.error("Cannot mix positional and named tuple "
                        + "element initializers. A tuple initializer must "
                        + "choose one mapping from entries to elements, "
                        + "because positional order and explicit names can "
                        + "disagree. Use either all positional entries or all "
                        + "named entries.");

                out.add(AstNode.varDeclFieldsInit(
                        n.span,
                        type,
                        n.name,
                        List.of(),
                        n.isConst
                ));
                return;
            }

            if (n.fields.size() > type.elements.size()) {
                diagnostics.error("Positional initializer for '"
                        + n.name
                        + "' has "
                        + n.fields.size()
                        + " entries but the tuple type has only "
                        + type.elements.size()
                        + " elements. Each positional entry consumes one tuple "
                        + "element, so extra entries have no target. Remove "
                        + "the extra entries or declare a tuple type with more "
                        + "elements.");

                out.add(AstNode.varDeclFieldsInit(
                        n.span,
                        type,
                        n.name,
                        List.of(),
                        n.isConst
                ));
                return;
            }
        }

        Set<String> seen = new HashSet<>();
        int position = 0;
        List<AstNode> entries = new ArrayList<>();

        for (AstNode init : n.fields) {
            String fieldName;

            if (init.positional) {
                fieldName = "_" + position;
                position++;
            } else {
                fieldName = init.name;
            }

            if (!seen.add(fieldName)) {
                diagnostics.push(init.span);
                diagnostics.error(
                        "Duplicate initializer for field '" + fieldName
                                + "'. A field-wise initializer assigns each "
                                + "member at most once, so a second entry would "
                                + "silently overwrite the first. Remove or "
                                + "rename the duplicate entry."
                );
                diagnostics.pop();
                continue;
            }

            entries.add(AstNode.fieldInit(
                    init.span,
                    init.name,
                    desugarExpr(init.value),
                    init.positional
            ));
        }

        out.add(AstNode.varDeclFieldsInit(
                n.span,
                type,
                n.name,
                entries,
                n.isConst
        ));
    }

    private static boolean allUnnamedTupleElements(AstNode tupleType) {
        for (AstNode element : tupleType.elements) {
            if (element.name != null) {
                return false;
            }
        }

        return true;
    }

    private boolean usesInferredSize(AstNode type) {
        if (type == null) {
            return false;
        }

        if (type.kind == AstKind.STATIC_ARRAY_TYPE) {
            return type.sizeExpr.kind == AstKind.INFERRED_SIZE
                || usesInferredSize(type.type);
        }

        if (type.kind == AstKind.DYNAMIC_ARRAY_TYPE) {
            return usesInferredSize(type.type);
        }

        if (type.kind == AstKind.PTR_TYPE) {
            return usesInferredSize(type.type);
        }

        return false;
    }

    private boolean isFullyStaticArrayNest(AstNode type) {
        if (type.kind == AstKind.STATIC_ARRAY_TYPE) {
            return isFullyStaticArrayNest(type.type);
        }

        if (type.kind == AstKind.DYNAMIC_ARRAY_TYPE) {
            return false;
        }

        if (type.kind == AstKind.PTR_TYPE) {
            return false;
        }

        return true;
    }

    private boolean isStaticArrayLevel(AstNode type) {
        return type.kind == AstKind.STATIC_ARRAY_TYPE;
    }

    private AstNode inferStaticSizes(AstNode declared, AstNode init, String varName) {
        if (declared.kind != AstKind.PTR_TYPE
                || declared.permission != PtrPermission.READ_ONLY
                || declared.type.kind != AstKind.STATIC_ARRAY_TYPE) {
            diagnostics.error("'?' array size for '"
                + varName
                + "' requires a 'view<[...]>' declaration with an "
                + "array-literal initializer. Inference reads the literal's "
                + "element count, and only a view declaration can hold the "
                + "resulting read-only array view. Write 'view<[int32, ?]> "
                + varName
                + " = [...]' or give the type an explicit size.");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        AstNode nest = declared.type;

        if (!isFullyStaticArrayNest(nest)) {
            diagnostics.error("'?' size inference for '"
                + varName
                + "' requires a fully static array type. A dynamic array "
                + "level has no compile-time element count for '?' to infer, "
                + "so every dimension in the nest must be a static array. "
                + "Replace each dynamic '[T]' level with a static '[T, N]' or "
                + "'?' level, or use an explicit dynamic view type.");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        if (init != null && init.kind == AstKind.STRING_LITERAL) {
            diagnostics.error("'?' array size for '"
                + varName
                + "' cannot be inferred from a string literal: a string "
                + "literal is a view<[byte, N]> value. Its byte count comes "
                + "from the string contents, not from an array-literal level "
                + "that '?' can inspect. Declare the size explicitly and copy "
                + "the bytes with '"
                + varName
                + ".memcpy(\"...\")'.");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        AstNode literal = normalizeInitializer(init);

        if (literal == null) {
            diagnostics.error("'?' array size for '"
                + varName
                + "' requires an array-literal initializer. The size is "
                + "inferred from the literal's element count at the matching "
                + "level, so another initializer form provides no count. "
                + "Initialize '"
                + varName
                + "' with '[...]' or write an explicit size.");

            return desugarType(replaceInferredSizesWithFallback(declared));
        }

        try {
            return AstNode.ptrType(
                declared.span,
                PtrPermission.READ_ONLY,
                desugarType(inferLevel(nest, literal, varName))
            );
        } catch (InferenceFailure f) {
            diagnostics.error(f.getMessage());

            return desugarType(replaceInferredSizesWithFallback(declared));
        }
    }

    private AstNode normalizeInitializer(AstNode init) {
        if (init != null && init.kind == AstKind.ARRAY_LITERAL) {
            return init;
        }

        return null;
    }

    private AstNode inferLevel(AstNode type, AstNode literal, String varName) {
        if (type.kind != AstKind.STATIC_ARRAY_TYPE) {
            throw new InferenceFailure("Internal inference error: expected a static array level for '"
                + varName
                + "'");
        }

        int count = literal.elements.size();

        AstNode sizeNode;

        if (type.sizeExpr.kind == AstKind.INFERRED_SIZE) {
            if (count == 0) {
                throw new InferenceFailure("'?' array size for '"
                    + varName
                    + "' cannot be inferred from an empty initializer list. "
                    + "An empty literal is the null view and carries no element "
                    + "count for '?' to use. Add at least one element or write "
                    + "an explicit size.");
            }

            sizeNode = AstNode.intLiteral(type.sizeExpr.span, Integer.toString(count));
        } else {
            BigInteger declared = ConstExpressionEvaluator.tryFold(type.sizeExpr);

            if (declared != null && !declared.equals(BigInteger.valueOf(count))) {
                throw new InferenceFailure("Initializer for '"
                    + varName
                    + "' has "
                    + count
                    + " elements but declared size is "
                    + declared
                    + " (partial initialization is not allowed when '?' is used). "
                    + "When any dimension uses '?', the literal must match the "
                    + "declared sizes exactly and may not leave elements "
                    + "uninitialized. Add or remove elements so the count "
                    + "matches "
                    + declared
                    + ", or remove '?' and initialize the array separately.");
            }

            sizeNode = type.sizeExpr;
        }

        AstNode inferredElem = type.type;

        if (isStaticArrayLevel(type.type)) {
            List<AstNode> rows = new ArrayList<>();

            for (AstNode element : literal.elements) {
                AstNode row = normalizeInitializer(element);

                if (row == null) {
                    throw new InferenceFailure("'?' array size inference for '"
                        + varName
                        + "' requires nested array literals. The declared type "
                        + "has another static array level, so each element must "
                        + "itself be a literal with a row shape. Wrap each row "
                        + "in '[...]' or change the declared type to a scalar "
                        + "element type.");
                }

                rows.add(row);
            }

            requireRectangular(rows, varName);

            inferredElem = inferLevel(type.type, rows.get(0), varName);

            for (int i = 1; i < rows.size(); i++) {
                checkLevel(inferredElem, rows.get(i), varName);
            }
        }

        return AstNode.staticArrayType(type.span, inferredElem, sizeNode);
    }

    private void checkLevel(AstNode concrete, AstNode literal, String varName) {
        if (concrete.kind != AstKind.STATIC_ARRAY_TYPE) {
            return;
        }

        int count = literal.elements.size();
        BigInteger expected = ConstExpressionEvaluator.tryFold(concrete.sizeExpr);

        if (expected != null && !expected.equals(BigInteger.valueOf(count))) {
            throw new InferenceFailure("Initializer for '"
                + varName
                + "' has "
                + count
                + " elements but inferred size is "
                + expected
                + ". The first row fixed the array width at "
                + expected
                + ", and every sibling row must match that static size. Add "
                + "or remove elements so this row also has "
                + expected
                + " elements.");
        }

        if (!isStaticArrayLevel(concrete.type)) {
            return;
        }

        List<AstNode> rows = new ArrayList<>();

        for (AstNode element : literal.elements) {
            AstNode row = normalizeInitializer(element);

            if (row == null) {
                throw new InferenceFailure("'?' array size inference for '"
                    + varName
                    + "' requires nested array literals. The declared type has "
                    + "another static array level, so each element must itself "
                    + "be a literal with a row shape. Wrap each row in '[...]' "
                    + "or change the declared type to a scalar element type.");
            }

            rows.add(row);
        }

        requireRectangular(rows, varName);

        for (AstNode row : rows) {
            checkLevel(concrete.type, row, varName);
        }
    }

    private void requireRectangular(List<AstNode> rows, String varName) {
        if (rows.isEmpty()) {
            return;
        }

        int first = rows.get(0).elements.size();

        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).elements.size() != first) {
                throw new InferenceFailure("'?' array size inference for '"
                    + varName
                    + "' requires a rectangular initializer list. Nested "
                    + "array literals are static at every level, so all rows "
                    + "must have the same length for one size to describe "
                    + "them. Add or remove elements so every row at each level "
                    + "has the same length.");
            }
        }
    }

    private AstNode replaceInferredSizesWithFallback(AstNode type) {
        if (type.kind == AstKind.STATIC_ARRAY_TYPE) {
            AstNode size = type.sizeExpr.kind == AstKind.INFERRED_SIZE
                    ? AstNode.intLiteral(type.sizeExpr.span, "1")
                    : type.sizeExpr;

            return AstNode.staticArrayType(
                type.span,
                replaceInferredSizesWithFallback(type.type),
                size
            );
        }

        if (type.kind == AstKind.DYNAMIC_ARRAY_TYPE) {
            return AstNode.dynamicArrayType(
                type.span,
                replaceInferredSizesWithFallback(type.type)
            );
        }

        if (type.kind == AstKind.PTR_TYPE) {
            return AstNode.ptrType(
                type.span,
                type.permission,
                replaceInferredSizesWithFallback(type.type)
            );
        }

        return type;
    }

    private void appendDesugaredAssign(
        AstNode n,
        List<AstNode> out
    ) {
        AstNode target = desugarLValue(n.target);

        out.add(AstNode.assign(
            n.span,
            n.text,
            target,
            desugarExpr(n.value)
        ));
    }

    private AstNode lowerIncDec(AstNode n) {
        return AstNode.assign(
            n.span,
            "++".equals(n.text) ? "+=" : "-=",
            desugarLValue(n.target),
            AstNode.intLiteral(n.span, "1")
        );
    }

    private AstNode desugarStmt(AstNode stmt) {
        if (stmt == null) return null;

        if (stmt.kind == AstKind.VAR_DECL_INIT) {
            List<AstNode> lowered = new ArrayList<>();
            appendDesugaredVarDeclInit(stmt, lowered);
            return AstNode.block(stmt.span, lowered);
        }

        if (stmt.kind == AstKind.VAR_DECL_FIELDS_INIT) {
            List<AstNode> lowered = new ArrayList<>();
            appendDesugaredVarDeclFieldsInit(stmt, lowered);
            return AstNode.block(stmt.span, lowered);
        }

        if (stmt.kind == AstKind.ASSIGN) {
            return AstNode.assign(
                stmt.span,
                stmt.text,
                desugarLValue(stmt.target),
                desugarExpr(stmt.value)
            );
        }

        if (stmt.kind == AstKind.INC_DEC) {
            return lowerIncDec(stmt);
        }

        if (stmt.kind == AstKind.PROGRAM) {
            return AstNode.program(stmt.span, desugarTopLevelItemList(stmt.items));
        }

        if (stmt.kind == AstKind.FUNCTION_DECL) {
            return desugarFunctionDecl(stmt);
        }

        if (stmt.kind == AstKind.BLOCK) {
            return AstNode.block(stmt.span, desugarStmtList(stmt.items));
        }

        if (stmt.kind == AstKind.IF) {
            return AstNode.ifStmt(
                stmt.span,
                desugarExpr(stmt.condition),
                desugarControlledStmt(stmt.thenBranch),
                stmt.elseBranch != null
                        ? desugarControlledStmt(stmt.elseBranch)
                        : null
            );
        }

        if (stmt.kind == AstKind.COMPILE_TIME_IF) {
            return desugarCompileTimeIf(stmt);
        }

        if (stmt.kind == AstKind.WHILE) {
            List<AstNode> loweredUpdate = new ArrayList<>();

            for (AstNode updateStmt : stmt.updateItems) {
                appendDesugaredStmt(updateStmt, loweredUpdate);
            }

            return AstNode.whileStmt(
                stmt.span,
                desugarExpr(stmt.condition),
                desugarControlledStmt(stmt.body),
                loweredUpdate
            );
        }

        if (stmt.kind == AstKind.FOR) {
            return desugarForStmt(stmt);
        }

        if (stmt.kind == AstKind.BREAK) {
            return AstNode.breakStmt(stmt.span,
                stmt.level != null ? desugarExpr(stmt.level) : null
            );
        }

        if (stmt.kind == AstKind.CONTINUE) {
            return AstNode.continueStmt(stmt.span,
                stmt.level != null ? desugarExpr(stmt.level) : null
            );
        }

        if (stmt.kind == AstKind.EXPR_STMT) {
            return AstNode.exprStmt(stmt.span, desugarExpr(stmt.expr));
        }

        if (stmt.kind == AstKind.RETURN) {
            return AstNode.returnStmt(stmt.span,
                stmt.value != null ? desugarExpr(stmt.value) : null
            );
        }

        if (stmt.kind == AstKind.VAR_DECL) {
            return AstNode.varDecl(
                stmt.span,
                desugarType(stmt.type),
                stmt.name,
                stmt.isConst
            );
        }

        throw new RuntimeException(
            "Unsupported statement in desugarer: " + stmt.kind.jsonName()
        );
    }

    private AstNode desugarCompileTimeIf(AstNode n) {
        return AstNode.compileTimeIf(
            n.span,
            desugarExpr(n.condition),
            desugarControlledStmt(n.thenBranch),
            n.elseBranch != null
                    ? desugarControlledStmt(n.elseBranch)
                    : null
        );
    }

    private AstNode desugarControlledStmt(AstNode stmt) {
        AstNode lowered = desugarStmt(stmt);

        if (lowered != null && lowered.kind == AstKind.BLOCK) {
            return lowered;
        }

        List<AstNode> wrapped = new ArrayList<>();
        wrapped.add(lowered);

        return AstNode.block(stmt.span, wrapped);
    }

    private AstNode desugarForStmt(AstNode n) {
        List<AstNode> outer = new ArrayList<>();

        if (n.init != null) {
            appendDesugaredStmt(n.init, outer);
        }

        AstNode loweredBody = desugarControlledStmt(n.body);

        if (loweredBody.kind != AstKind.BLOCK) {
            throw new RuntimeException(
                "Internal desugarer error: for-loop body did not lower to a block"
            );
        }

        List<AstNode> loweredUpdate = new ArrayList<>();

        if (n.update != null) {
            appendDesugaredStmt(n.update, loweredUpdate);
        }

        outer.add(AstNode.whileStmt(n.span,
            desugarExpr(n.condition),
            loweredBody,
            loweredUpdate
        ));

        return AstNode.block(n.span, outer);
    }

    private List<AstNode> desugarGenericArguments(List<AstNode> args) {
        if (args == null || args.isEmpty()) {
            return List.of();
        }

        List<AstNode> out = new ArrayList<>();

        for (AstNode arg : args) {
            if (arg.kind != AstKind.GENERIC_ARG) {
                out.add(arg);
                continue;
            }

            if (arg.typeParameter) {
                out.add(AstNode.genericArgType(
                        arg.span,
                        desugarType(arg.type)
                ));
            } else {
                out.add(AstNode.genericArgValue(
                        arg.span,
                        desugarExpr(arg.value)
                ));
            }
        }

        return out;
    }

    private AstNode desugarReflectionArgument(AstNode arg) {
        if (arg.kind == AstKind.STRING_LITERAL) {
            return arg;
        }

        if (arg.isType()) {
            return desugarType(arg);
        }

        return desugarExpr(arg);
    }

    private AstNode desugarExpr(AstNode expr) {
        if (expr == null) return null;

        diagnostics.push(expr.span);
        try {

        if (expr.kind == AstKind.INT) {
            return expr;
        }

        if (expr.kind == AstKind.STRING_LITERAL) {
            return hoistStringLiteral(expr);
        }

        if (expr.kind == AstKind.VAR) {
            return expr;
        }

        if (expr.kind == AstKind.QUALIFIED_NAME) {
            return expr;
        }

        if (expr.kind == AstKind.NULL) {
            return expr;
        }

        if (expr.kind == AstKind.UNARY) {
            return AstNode.unary(expr.span,
                expr.text,
                desugarExpr(expr.expr)
            );
        }

        if (expr.kind == AstKind.BINARY) {
            return AstNode.binary(expr.span,
                expr.text,
                desugarExpr(expr.left),
                desugarExpr(expr.right)
            );
        }

        if (expr.kind == AstKind.ARRAY_ACCESS) {
            return AstNode.arrayAccess(expr.span,
                desugarExpr(expr.target),
                desugarExpr(expr.index)
            );
        }

        if (expr.kind == AstKind.MEMBER_ACCESS) {
            return AstNode.memberAccess(expr.span,
                desugarExpr(expr.target),
                expr.name
            );
        }

        if (expr.kind == AstKind.COMPILE_TIME_CALL) {
            List<AstNode> args = new ArrayList<>();

            for (AstNode arg : expr.args) {
                args.add(desugarReflectionArgument(arg));
            }

            return AstNode.compileTimeCall(expr.span, expr.name, args);
        }

        if (expr.kind == AstKind.COMPILE_TIME_NAME) {
            return expr;
        }

        if (expr.kind == AstKind.CALL) {
            List<AstNode> args = new ArrayList<>();

            for (AstNode arg : expr.args) {
                args.add(desugarExpr(arg));
            }

            AstNode call = AstNode.call(expr.span,
                desugarExpr(expr.callee),
                args,
                desugarGenericArguments(expr.genericArgs),
                expr.argumentNames
            );
            call.genericGroup = expr.genericGroup;
            return call;
        }

        if (expr.kind == AstKind.GET_PTR) {
            return AstNode.getPtr(expr.span,
                expr.permission,
                desugarLValue(expr.target)
            );
        }

        if (expr.kind == AstKind.DEREF) {
            return AstNode.deref(expr.span,
                desugarExpr(expr.expr)
            );
        }

        if (expr.kind == AstKind.CAST) {
            return AstNode.cast(expr.span,
                desugarType(expr.type),
                desugarExpr(expr.expr),
                expr.unsafe
            );
        }

        if (expr.kind == AstKind.ARRAY_LITERAL) {
            if (expr.elements.isEmpty()) {
                return AstNode.nullLiteral(expr.span);
            }

            List<AstNode> elements = new ArrayList<>();

            for (AstNode element : expr.elements) {
                elements.add(desugarExpr(element));
            }

            return AstNode.arrayLiteral(expr.span, elements);
        }

        if (expr.kind == AstKind.AGGREGATE_LITERAL) {
            List<AstNode> fields = new ArrayList<>();

            for (AstNode field : expr.fields) {
                fields.add(AstNode.fieldInit(
                        field.span,
                        field.name,
                        desugarExpr(field.value),
                        field.positional
                ));
            }

            return AstNode.aggregateLiteral(
                    expr.span,
                    desugarType(expr.type),
                    fields
            );
        }

        throw new RuntimeException(
            "Unsupported expression in desugarer: " + expr.kind.jsonName()
        );
        } finally {
            diagnostics.pop();
        }
    }

    private AstNode evalConstExpr(AstNode expr, String role) {
        if (expr == null) return null;

        diagnostics.push(expr.span);
        try {
        try {
            return AstNode.intLiteral(
                expr.span,
                ConstExpressionEvaluator.evaluatePure(expr).toString(10)
            );
        } catch (ConstExpressionEvaluator.UnevaluableConstant e) {
            if (e.getDiagnostic() != null) {
                diagnostics.error(e.getDiagnostic());
            } else {
                diagnostics.error("Invalid constant expression for "
                    + role
                    + ". This position requires a value the compiler can fold "
                    + "before code generation, and the expression contains "
                    + "something it cannot evaluate. Use integer literals and "
                    + "operators over them, or move the computation out of the "
                    + "constant context.");
            }

            return AstNode.intLiteral(expr.span, "1");
        }
        } finally {
            diagnostics.pop();
        }
    }

    private AstNode desugarLValue(AstNode node) {
        if (node == null) return null;

        if (node.kind == AstKind.VAR) {
            return node;
        }

        if (node.kind == AstKind.QUALIFIED_NAME) {
            return node;
        }

        if (node.kind == AstKind.DEREF) {
            return AstNode.deref(node.span,
                desugarExpr(node.expr)
            );
        }

        if (node.kind == AstKind.ARRAY_ACCESS) {
            return AstNode.arrayAccess(node.span,
                desugarLValue(node.target),
                desugarExpr(node.index)
            );
        }

        if (node.kind == AstKind.MEMBER_ACCESS) {
            return AstNode.memberAccess(node.span,
                desugarLValue(node.target),
                node.name
            );
        }

        throw new RuntimeException(
            "Unsupported lvalue in desugarer: " + node.kind.jsonName()
        );
    }

    private AstNode desugarType(AstNode type) {
        if (type == null) return null;

        diagnostics.push(type.span);
        try {

        if (type.kind == AstKind.PRIMITIVE_TYPE) {
            return type;
        }

        if (type.kind == AstKind.VOID_TYPE) {
            return type;
        }

        if (type.kind == AstKind.STRUCT_TYPE) {
            return type;
        }

        if (type.kind == AstKind.TUPLE_TYPE) {
            List<AstNode> elements = new ArrayList<>();

            for (AstNode element : type.elements) {
                elements.add(AstNode.tupleTypeElement(
                    element.span,
                    desugarType(element.type),
                    element.name
                ));
            }

            return AstNode.tupleType(type.span, elements);
        }

        if (type.kind == AstKind.DYNAMIC_ARRAY_TYPE) {
            return AstNode.dynamicArrayType(
                type.span,
                desugarType(type.type)
            );
        }

        if (type.kind == AstKind.STATIC_ARRAY_TYPE) {
            if (type.sizeExpr.kind == AstKind.INFERRED_SIZE) {
                diagnostics.error("'?' array size is only allowed in a "
                    + "declaration with an array-literal initializer. There is "
                    + "no literal at this position whose element count could "
                    + "supply the missing size. Replace '?' with an explicit "
                    + "size or move the declaration to a local 'view<[...]>' "
                    + "with an array-literal initializer.");

                return AstNode.staticArrayType(
                    type.span,
                    desugarType(type.type),
                    AstNode.intLiteral(type.sizeExpr.span, "1")
                );
            }

            return AstNode.staticArrayType(
                type.span,
                desugarType(type.type),
                evalConstExpr(type.sizeExpr, "static array size")
            );
        }

        if (type.kind == AstKind.PTR_TYPE) {
            return AstNode.ptrType(
                type.span,
                type.permission,
                desugarType(type.type)
            );
        }

        throw new RuntimeException(
            "Unsupported type in desugarer: " + type.kind.jsonName()
        );
        } finally {
            diagnostics.pop();
        }
    }
}
