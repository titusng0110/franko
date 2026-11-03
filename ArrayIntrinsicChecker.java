final class ArrayIntrinsicChecker {

    private ExpressionChecker owner;
    private final DiagnosticBag diagnostics;
    private final TypeChecker types;

    ArrayIntrinsicChecker(
            DiagnosticBag diagnostics,
            TypeChecker types
    ) {
        this.diagnostics = diagnostics;
        this.types = types;
    }

    void check(
            ExpressionChecker owner,
            SemanticArrayIntrinsicCallNode node
    ) {
        this.owner = owner;

        if (node.kind == null) {
            diagnostics.error("Array intrinsic call has null kind");
            return;
        }

        owner.checkExpr(node.receiver);

        switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE, UNINIT -> owner.ensureRegionPermission(
                    node.receiver,
                    PtrPermission.OWNER,
                    node.kind.runtimeName + "()"
            );

            case MEMSET, MEMCPY, MEMMOVE,
                 WRITE_UINT16_LE, WRITE_UINT16_BE,
                 WRITE_UINT32_LE, WRITE_UINT32_BE,
                 WRITE_UINT64_LE, WRITE_UINT64_BE,
                 WRITE_INT16_LE, WRITE_INT16_BE,
                 WRITE_INT32_LE, WRITE_INT32_BE,
                 WRITE_INT64_LE, WRITE_INT64_BE,
                 WRITE_BIT, FLIP_BIT,
                 BITWISE_NOT_MASK, BITWISE_XOR_MASK,
                 BITWISE_AND_MASK, BITWISE_OR_MASK,
                 SHIFT_BITS_LEFT, SHIFT_BITS_RIGHT -> owner.ensureRegionPermission(
                    node.receiver,
                    PtrPermission.MUTABLE,
                    node.kind.runtimeName + "()"
            );

            case LENGTH, MEMCMP, MEMCHR, MEMMEM,
                 READ_UINT16_LE, READ_UINT16_BE,
                 READ_UINT32_LE, READ_UINT32_BE,
                 READ_UINT64_LE, READ_UINT64_BE,
                 READ_INT16_LE, READ_INT16_BE,
                 READ_INT32_LE, READ_INT32_BE,
                 READ_INT64_LE, READ_INT64_BE,
                 READ_BIT, COUNT_SET_BITS -> { }
        }

        switch (node.kind) {
            case INIT, INIT_ZERO, RESIZE -> visitArrayAllocIntrinsic(node);
            case UNINIT -> visitArrayUninitIntrinsic(node);
            case MEMSET -> visitArrayMemsetIntrinsic(node);
            case MEMCPY -> visitArrayMemcpyIntrinsic(node);
            case MEMMOVE -> visitArrayMemmoveIntrinsic(node);
            case LENGTH -> visitArrayLengthIntrinsic(node);

            case MEMCMP -> visitArrayMemcmpIntrinsic(node);
            case MEMCHR -> visitArrayMemchrIntrinsic(node);
            case MEMMEM -> visitArrayMemmemIntrinsic(node);

            case READ_UINT16_LE, READ_UINT16_BE,
                 READ_UINT32_LE, READ_UINT32_BE,
                 READ_UINT64_LE, READ_UINT64_BE,
                 READ_INT16_LE, READ_INT16_BE,
                 READ_INT32_LE, READ_INT32_BE,
                 READ_INT64_LE, READ_INT64_BE -> visitArrayReadIntrinsic(node);

            case WRITE_UINT16_LE, WRITE_UINT16_BE,
                 WRITE_UINT32_LE, WRITE_UINT32_BE,
                 WRITE_UINT64_LE, WRITE_UINT64_BE,
                 WRITE_INT16_LE, WRITE_INT16_BE,
                 WRITE_INT32_LE, WRITE_INT32_BE,
                 WRITE_INT64_LE, WRITE_INT64_BE -> visitArrayWriteIntrinsic(node);

            case READ_BIT, WRITE_BIT, FLIP_BIT,
                 BITWISE_NOT_MASK, BITWISE_XOR_MASK,
                 BITWISE_AND_MASK, BITWISE_OR_MASK,
                 SHIFT_BITS_LEFT, SHIFT_BITS_RIGHT,
                 COUNT_SET_BITS -> visitArrayBitIntrinsic(node);
        }
    }

    private void visitArrayMemcmpIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1 && node.args.size() != 4) {
            diagnostics.error("Array intrinsic 'memcmp' expects either 1 or 4 arguments, got "
                    + node.args.size());
            return;
        }

        checkNewIntrinsicReceiver(node);

        SemanticExprNode sourcePtr = node.args.get(0);
        owner.checkExpr(sourcePtr);

        SemanticType sourceArrayType = null;

        if (types.ensureValueExpression(
                sourcePtr,
                "memcmp() source pointer"
        )) {
            if (!(sourcePtr.type instanceof SemanticPtrType ptr)) {
                diagnostics.error("memcmp() source must be an pointer to an array, got "
                        + types.describeSafe(sourcePtr.type));
            } else if (!types.isArrayType(ptr.referencedType)) {
                diagnostics.error("memcmp() source must be an pointer to an array, got ptr<"
                        + types.describeSafe(ptr.referencedType)
                        + ">");
            } else {
                sourceArrayType = ptr.referencedType;
            }
        }

        SemanticType targetArrayType = node.receiver.type;

        if (sourceArrayType != null) {
            SemanticType targetElem = types.elementTypeOfArray(targetArrayType);
            SemanticType sourceElem = types.elementTypeOfArray(sourceArrayType);

            if (targetElem != null && sourceElem != null
                    && !TypeChecker.sameType(targetElem, sourceElem)) {
                diagnostics.error("memcmp() requires source and target arrays to have identical element types, got "
                        + targetElem.describe()
                        + " and "
                        + sourceElem.describe());
            }

            if (sourceElem != null && !types.isCopiable(sourceElem)) {
                diagnostics.error("memcmp() source array element type is not copiable: "
                        + sourceElem.describe());
            }
        }

        if (node.args.size() == 4) {
            SemanticExprNode dstStart = node.args.get(1);
            SemanticExprNode srcStart = node.args.get(2);
            SemanticExprNode count = node.args.get(3);

            owner.ensureUInt32Compatible(dstStart, "memcmp() destination start", true);
            owner.ensureUInt32Compatible(srcStart, "memcmp() source start", true);
            owner.ensureUInt32Compatible(count, "memcmp() count", true);

            owner.ensureStaticArrayRangeInBounds(
                    targetArrayType,
                    dstStart,
                    count,
                    "memcmp() destination range"
            );

            owner.ensureStaticArrayRangeInBounds(
                    sourceArrayType,
                    srcStart,
                    count,
                    "memcmp() source range"
            );
        }

        ensureArrayIntrinsicReturnType(node, node.kind.returnType());
    }

    private void visitArrayMemchrIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1 && node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memchr' expects either 1 or 3 arguments, got "
                    + node.args.size());
            return;
        }

        checkNewIntrinsicReceiver(node);

        owner.ensureScalarCompatible(
                node.args.get(0),
                SemanticPrimitiveKind.UINT8,
                "memchr() value"
        );

        if (node.args.size() == 3) {
            owner.ensureUInt32Compatible(node.args.get(1), "memchr() start", true);
            owner.ensureUInt32Compatible(node.args.get(2), "memchr() count", true);

            owner.ensureStaticArrayRangeInBounds(
                    node.receiver.type,
                    node.args.get(1),
                    node.args.get(2),
                    "memchr() range"
            );
        }

        ensureArrayIntrinsicReturnType(node, node.kind.returnType());
    }

    private void visitArrayMemmemIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1 && node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memmem' expects either 1 or 3 arguments, got "
                    + node.args.size());
            return;
        }

        checkNewIntrinsicReceiver(node);

        SemanticExprNode needle = node.args.get(0);
        owner.checkExpr(needle);

        if (types.ensureValueExpression(needle, "memmem() needle pointer")) {
            if (!(needle.type instanceof SemanticPtrType ptr)) {
                diagnostics.error("memmem() needle must be an pointer to an array, got "
                        + types.describeSafe(needle.type));
            } else if (!types.isArrayType(ptr.referencedType)) {
                diagnostics.error("memmem() needle must be an pointer to an array, got ptr<"
                        + types.describeSafe(ptr.referencedType)
                        + ">");
            } else {
                SemanticType needleElem =
                        types.elementTypeOfArray(ptr.referencedType);

                if (needleElem != null && !TypeChecker.isByteType(needleElem)) {
                    diagnostics.error("memmem() needle array element type must be byte, got "
                            + types.describeSafe(needleElem));
                }
            }
        }

        if (node.args.size() == 3) {
            owner.ensureUInt32Compatible(node.args.get(1), "memmem() start", true);
            owner.ensureUInt32Compatible(node.args.get(2), "memmem() count", true);

            owner.ensureStaticArrayRangeInBounds(
                    node.receiver.type,
                    node.args.get(1),
                    node.args.get(2),
                    "memmem() range"
            );
        }

        ensureArrayIntrinsicReturnType(node, node.kind.returnType());
    }

    private void visitArrayReadIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1) {
            diagnostics.error("Array intrinsic '"
                    + node.kind.runtimeName
                    + "' expects exactly 1 argument, got "
                    + node.args.size());
            return;
        }

        checkNewIntrinsicReceiver(node);

        SemanticExprNode offset = node.args.get(0);

        owner.ensureUInt32Compatible(
                offset,
                node.kind.runtimeName + "() offset",
                true
        );

        owner.ensureStaticArrayByteAccessInBounds(
                node.receiver.type,
                offset,
                TypeChecker.bitWidth(node.kind.returnKind) / 8,
                node.kind.runtimeName + "() offset"
        );

        ensureArrayIntrinsicReturnType(node, node.kind.returnType());
    }

    private void visitArrayWriteIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 2) {
            diagnostics.error("Array intrinsic '"
                    + node.kind.runtimeName
                    + "' expects exactly 2 arguments, got "
                    + node.args.size());
            return;
        }

        checkNewIntrinsicReceiver(node);

        SemanticExprNode offset = node.args.get(0);

        owner.ensureUInt32Compatible(
                offset,
                node.kind.runtimeName + "() offset",
                true
        );

        owner.ensureStaticArrayByteAccessInBounds(
                node.receiver.type,
                offset,
                TypeChecker.bitWidth(node.kind.argKind(1).primitiveKind) / 8,
                node.kind.runtimeName + "() offset"
        );

        SemanticIntrinsicArgKind valueKind = node.kind.argKind(1);

        owner.ensureScalarCompatible(
                node.args.get(1),
                valueKind.primitiveKind,
                node.kind.runtimeName + "() value"
        );

        ensureArrayIntrinsicReturnType(node, new SemanticVoidType());
    }

    private void visitArrayBitIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (!node.kind.acceptsArity(node.args.size())) {
            diagnostics.error("Array intrinsic '"
                    + node.kind.runtimeName
                    + "' expects "
                    + node.kind.arityDescription()
                    + " arguments, got "
                    + node.args.size());
            return;
        }

        checkNewIntrinsicReceiver(node);

        for (int i = 0; i < node.args.size(); i++) {
            SemanticIntrinsicArgKind argKind = node.kind.argKind(i);

            owner.ensureScalarCompatible(
                    node.args.get(i),
                    argKind.primitiveKind,
                    node.kind.runtimeName + "() argument " + (i + 1)
            );
        }

        if (node.kind == SemanticArrayIntrinsicKind.READ_BIT
                || node.kind == SemanticArrayIntrinsicKind.WRITE_BIT
                || node.kind == SemanticArrayIntrinsicKind.FLIP_BIT) {
            owner.ensureStaticArrayBitIndexInBounds(
                    node.receiver.type,
                    node.args.get(0),
                    node.kind.runtimeName + "() bit index"
            );
        }

        ensureArrayIntrinsicReturnType(node, node.kind.returnType());
    }

    private void checkNewIntrinsicReceiver(SemanticArrayIntrinsicCallNode node) {
        if (node.kind.requiresStorageLValue()
                && !owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error(node.kind.runtimeName
                    + "() receiver must be a storage-backed lvalue");
        }

        if (!types.isArrayType(node.receiver.type)) {
            diagnostics.error(node.kind.runtimeName
                    + "() receiver must be an array, got "
                    + types.describeSafe(node.receiver.type));
            return;
        }

        SemanticType elementType = types.elementTypeOfArray(node.receiver.type);

        if (elementType == null) {
            return;
        }

        switch (node.kind.elementRule) {
            case NONE -> { }

            case TRIVIAL -> {
                if (!types.isTrivial(elementType)) {
                    diagnostics.error(node.kind.runtimeName
                            + "() receiver element type is not trivial: "
                            + elementType.describe());
                }
            }

            case COPIABLE -> {
                if (!types.isCopiable(elementType)) {
                    diagnostics.error(node.kind.runtimeName
                            + "() receiver element type is not copiable: "
                            + elementType.describe());
                }
            }

            case BYTE -> {
                if (!TypeChecker.isByteType(elementType)) {
                    diagnostics.error(node.kind.runtimeName
                            + "() receiver element type must be byte, got "
                            + types.describeSafe(elementType));
                }
            }
        }
    }

    private void visitArrayAllocIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1) {
            diagnostics.error("Array intrinsic '"
                    + node.kind.runtimeName
                    + "' expects exactly 1 argument, got "
                    + node.args.size());
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error(node.kind.runtimeName
                    + "() receiver must be a storage-backed lvalue");
        }

        if (!types.isDynamicArrayType(node.receiver.type)) {
            diagnostics.error(node.kind.runtimeName
                    + "() receiver must be a dynamic array, got "
                    + types.describeSafe(node.receiver.type));
        }

        owner.ensureArraySizeCompatible(
                node.args.get(0),
                node.kind.runtimeName + "() size"
        );

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticPrimitiveType(SemanticPrimitiveKind.INT32)
        );
    }

    private void visitArrayUninitIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 0) {
            diagnostics.error("Array intrinsic 'uninit' expects exactly 0 arguments, got "
                    + node.args.size());
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("uninit() receiver must be a storage-backed lvalue");
        }

        if (!types.isDynamicArrayType(node.receiver.type)) {
            diagnostics.error("uninit() receiver must be a dynamic array, got "
                    + types.describeSafe(node.receiver.type));
        }

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void visitArrayMemsetIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1 && node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memset' expects either 1 or 3 arguments, got "
                    + node.args.size());
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memset() receiver must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memset() receiver must be an array"
        );

        SemanticType elementType = types.elementTypeOfArray(node.receiver.type);

        if (elementType != null && !types.isTrivial(elementType)) {
            diagnostics.error("memset() receiver element type is not trivial: "
                    + elementType.describe());
        }

        SemanticExprNode value = node.args.get(0);

        owner.checkExpr(value);

        if (types.ensureValueExpression(
                value,
                "memset() fill value"
        )) {
            owner.ensureMemsetValueCompatible(value);
        }

        if (node.args.size() == 3) {
            owner.ensureUInt32Compatible(
                    node.args.get(1),
                    "memset() start",
                    true
            );

            owner.ensureUInt32Compatible(
                    node.args.get(2),
                    "memset() count",
                    true
            );

            owner.ensureStaticArrayRangeInBounds(
                    node.receiver.type,
                    node.args.get(1),
                    node.args.get(2),
                    "memset() range"
            );
        }

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void visitArrayMemcpyIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 1 && node.args.size() != 4) {
            diagnostics.error("Array intrinsic 'memcpy' expects either 1 or 4 arguments, got "
                    + node.args.size());
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memcpy() target must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memcpy() target must be an array"
        );

        SemanticExprNode sourcePtr = node.args.get(0);
        owner.checkExpr(sourcePtr);

        SemanticType sourceArrayType = null;

        if (types.ensureValueExpression(
                sourcePtr,
                "memcpy() source pointer"
        )) {
            if (!(sourcePtr.type instanceof SemanticPtrType ptr)) {
                diagnostics.error("memcpy() source must be an pointer to an array, got "
                        + types.describeSafe(sourcePtr.type));
            } else if (!types.isArrayType(ptr.referencedType)) {
                diagnostics.error("memcpy() source must be an pointer to an array, got ptr<"
                        + types.describeSafe(ptr.referencedType)
                        + ">");
            } else {
                sourceArrayType = ptr.referencedType;
            }
        }

        SemanticType targetArrayType = node.receiver.type;

        if (targetArrayType != null && sourceArrayType != null) {
            SemanticType targetElem = types.elementTypeOfArray(targetArrayType);
            SemanticType sourceElem = types.elementTypeOfArray(sourceArrayType);

            if (targetElem != null && sourceElem != null
                    && !TypeChecker.sameType(targetElem, sourceElem)) {
                diagnostics.error("memcpy() requires source and target arrays to have identical element types, got "
                        + targetElem.describe()
                        + " and "
                        + sourceElem.describe());
            }

            if (targetElem != null && !types.isCopiable(targetElem)) {
                diagnostics.error("memcpy() target array element type is not copiable: "
                        + targetElem.describe());
            }

            if (sourceElem != null && !types.isCopiable(sourceElem)) {
                diagnostics.error("memcpy() source array element type is not copiable: "
                        + sourceElem.describe());
            }
        }

        if (node.args.size() == 4) {
            SemanticExprNode dstStart = node.args.get(1);
            SemanticExprNode srcStart = node.args.get(2);
            SemanticExprNode count = node.args.get(3);

            owner.ensureUInt32Compatible(
                    dstStart,
                    "memcpy() destination start",
                    true
            );

            owner.ensureUInt32Compatible(
                    srcStart,
                    "memcpy() source start",
                    true
            );

            owner.ensureUInt32Compatible(
                    count,
                    "memcpy() count",
                    true
            );

            owner.ensureStaticArrayRangeInBounds(
                    targetArrayType,
                    dstStart,
                    count,
                    "memcpy() destination range"
            );

            owner.ensureStaticArrayRangeInBounds(
                    sourceArrayType,
                    srcStart,
                    count,
                    "memcpy() source range"
            );
        }

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void visitArrayMemmoveIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memmove' expects exactly 3 arguments, got "
                    + node.args.size());
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memmove() receiver must be a storage-backed lvalue");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memmove() receiver must be an array"
        );

        SemanticType receiverElem =
                types.elementTypeOfArray(node.receiver.type);

        if (receiverElem != null && !types.isCopiable(receiverElem)) {
            diagnostics.error("memmove() receiver array element type is not copiable: "
                    + receiverElem.describe());
        }

        SemanticExprNode dstStart = node.args.get(0);
        SemanticExprNode srcStart = node.args.get(1);
        SemanticExprNode count = node.args.get(2);

        owner.ensureUInt32Compatible(
                dstStart,
                "memmove() destination start",
                true
        );

        owner.ensureUInt32Compatible(
                srcStart,
                "memmove() source start",
                true
        );

        owner.ensureUInt32Compatible(
                count,
                "memmove() count",
                true
        );

        owner.ensureStaticArrayRangeInBounds(
                node.receiver.type,
                dstStart,
                count,
                "memmove() destination range"
        );

        owner.ensureStaticArrayRangeInBounds(
                node.receiver.type,
                srcStart,
                count,
                "memmove() source range"
        );

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticVoidType()
        );
    }

    private void visitArrayLengthIntrinsic(SemanticArrayIntrinsicCallNode node) {
        if (node.args.size() != 0) {
            diagnostics.error("Array intrinsic 'length' expects exactly 0 arguments, got "
                    + node.args.size());
            return;
        }

        types.ensureArrayType(
                node.receiver.type,
                "length() receiver must be an array"
        );

        ensureArrayIntrinsicReturnType(
                node,
                new SemanticPrimitiveType(SemanticPrimitiveKind.UINT32)
        );
    }

    private void ensureArrayIntrinsicReturnType(
            SemanticArrayIntrinsicCallNode node,
            SemanticType expected
    ) {
        if (!TypeChecker.sameType(node.type, expected)) {
            diagnostics.error("Array intrinsic '"
                    + node.kind.runtimeName
                    + "' expression type mismatch: node has "
                    + types.describeSafe(node.type)
                    + ", expected "
                    + types.describeSafe(expected));
        }
    }
}
