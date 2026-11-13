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
            SastNode node
    ) {
        this.owner = owner;

        if (node.intrinsicKind == null) {
            diagnostics.error("Array intrinsic call has null kind");
            return;
        }

        owner.checkExpr(node.receiver);

        switch (node.intrinsicKind) {
            case INIT, INIT_ZERO, RESIZE, UNINIT -> owner.ensureRegionPermission(
                    node.receiver,
                    PtrPermission.OWNER,
                    node.intrinsicKind.runtimeName + "()"
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
                    node.intrinsicKind.runtimeName + "()"
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

        switch (node.intrinsicKind) {
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

    private void visitArrayMemcmpIntrinsic(SastNode node) {
        if (node.args.size() != 1 && node.args.size() != 4) {
            diagnostics.error("Array intrinsic 'memcmp' expects either 1 or 4 arguments, got "
                    + node.args.size()
                    + ". memcmp has separate whole-array and ranged forms, so any other"
                    + " argument count has no defined meaning. Pass one source pointer, or"
                    + " add destination start, source start, and count.");
            return;
        }

        checkNewIntrinsicReceiver(node);

        SastNode sourcePtr = node.args.get(0);
        owner.checkExpr(sourcePtr);

        SastType sourceArrayType = null;

        if (types.ensureValueExpression(
                sourcePtr,
                "memcmp() source pointer"
        )) {
            if (!(sourcePtr.type.kind == SastTypeKind.PTR)) {
                diagnostics.error("memcmp() source must be an pointer to an array, got "
                        + types.describeSafe(sourcePtr.type)
                        + ". memcmp reads the source through a pointer so it can use the"
                        + " array's element type and length. Pass getview(array),"
                        + " getptr(array), or an existing pointer/view instead of the"
                        + " array value.");
            } else if (!types.isArrayType(sourcePtr.type.referencedType)) {
                diagnostics.error("memcmp() source must be an pointer to an array, got ptr<"
                        + types.describeSafe(sourcePtr.type.referencedType)
                        + ">. memcmp needs a pointer whose referent is an array, because"
                        + " a pointer to one value has no length or element stride to"
                        + " compare. Pass a pointer/view of the whole array, such as"
                        + " getview(array), rather than a pointer to one element.");
            } else {
                sourceArrayType = sourcePtr.type.referencedType;
            }
        }

        SastType targetArrayType = node.receiver.type;

        if (sourceArrayType != null) {
            SastType targetElem = types.elementTypeOfArray(targetArrayType);
            SastType sourceElem = types.elementTypeOfArray(sourceArrayType);

            if (targetElem != null && sourceElem != null
                    && !TypeChecker.sameType(targetElem, sourceElem)) {
                diagnostics.error("memcmp() requires source and target arrays to have identical element types, got "
                        + targetElem.describe()
                        + " and "
                        + sourceElem.describe()
                        + ". memcmp compares elements at a shared stride, so both sides"
                        + " must interpret the bytes with the same element layout."
                        + " Compare arrays with the same element type, or copy both into"
                        + " [byte] buffers and compare those.");
            }

            if (sourceElem != null && !types.isMemcpySafe(sourceElem)) {
                diagnostics.error("memcmp() source array element type is not memcpy-safe: "
                        + sourceElem.describe()
                        + ". memcmp examines raw bytes, which is only well-defined for"
                        + " types with no managed storage or construction state. Use a"
                        + " memcpy-safe element type, or compare dynamic-array elements"
                        + " individually.");
            }
        }

        if (node.args.size() == 4) {
            SastNode dstStart = node.args.get(1);
            SastNode srcStart = node.args.get(2);
            SastNode count = node.args.get(3);

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

        ensureArrayIntrinsicReturnType(node, node.intrinsicKind.returnType());
    }

    private void visitArrayMemchrIntrinsic(SastNode node) {
        if (node.args.size() != 1 && node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memchr' expects either 1 or 3 arguments, got "
                    + node.args.size()
                    + ". memchr has separate whole-array and ranged search forms, so any"
                    + " other argument count has no defined meaning. Pass the value alone,"
                    + " or add start and count.");
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

        ensureArrayIntrinsicReturnType(node, node.intrinsicKind.returnType());
    }

    private void visitArrayMemmemIntrinsic(SastNode node) {
        if (node.args.size() != 1 && node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memmem' expects either 1 or 3 arguments, got "
                    + node.args.size()
                    + ". memmem has separate whole-array and ranged search forms, so any"
                    + " other argument count has no defined meaning. Pass the needle"
                    + " pointer alone, or add start and count.");
            return;
        }

        checkNewIntrinsicReceiver(node);

        SastNode needle = node.args.get(0);
        owner.checkExpr(needle);

        if (types.ensureValueExpression(needle, "memmem() needle pointer")) {
            if (!(needle.type.kind == SastTypeKind.PTR)) {
                diagnostics.error("memmem() needle must be an pointer to an array, got "
                        + types.describeSafe(needle.type)
                        + ". memmem reads the needle through a pointer so it can use the"
                        + " pattern array's byte length and data. Pass getview(pattern),"
                        + " getptr(pattern), or an existing pointer/view instead of the"
                        + " array value.");
            } else if (!types.isArrayType(needle.type.referencedType)) {
                diagnostics.error("memmem() needle must be an pointer to an array, got ptr<"
                        + types.describeSafe(needle.type.referencedType)
                        + ">. memmem needs a pointer whose referent is an array, because"
                        + " a pointer to one byte has no needle length. Pass a pointer/view"
                        + " of the whole byte array, such as getview(pattern), rather than"
                        + " a pointer to one element.");
            } else {
                SastType needleElem =
                        types.elementTypeOfArray(needle.type.referencedType);

                if (needleElem != null && !TypeChecker.isByteType(needleElem)) {
                    diagnostics.error("memmem() needle array element type must be byte, got "
                            + types.describeSafe(needleElem)
                            + ". memmem searches a byte pattern, so each needle element"
                            + " must be exactly one byte. Store the pattern in a [byte] or"
                            + " [byte, N] array, or search other element types element by"
                            + " element.");
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

        ensureArrayIntrinsicReturnType(node, node.intrinsicKind.returnType());
    }

    private void visitArrayReadIntrinsic(SastNode node) {
        if (node.args.size() != 1) {
            diagnostics.error("Array intrinsic '"
                    + node.intrinsicKind.runtimeName
                    + "' expects exactly 1 argument, got "
                    + node.args.size()
                    + ". A read_* intrinsic extracts exactly one fixed-width value from"
                    + " one offset, so extra arguments have no meaning. Pass the offset as"
                    + " the only argument.");
            return;
        }

        checkNewIntrinsicReceiver(node);

        SastNode offset = node.args.get(0);

        owner.ensureUInt32Compatible(
                offset,
                node.intrinsicKind.runtimeName + "() offset",
                true
        );

        owner.ensureStaticArrayByteAccessInBounds(
                node.receiver.type,
                offset,
                TypeChecker.bitWidth(node.intrinsicKind.returnKind) / 8,
                node.intrinsicKind.runtimeName + "() offset"
        );

        ensureArrayIntrinsicReturnType(node, node.intrinsicKind.returnType());
    }

    private void visitArrayWriteIntrinsic(SastNode node) {
        if (node.args.size() != 2) {
            diagnostics.error("Array intrinsic '"
                    + node.intrinsicKind.runtimeName
                    + "' expects exactly 2 arguments, got "
                    + node.args.size()
                    + ". A write_* intrinsic stores exactly one fixed-width value at one"
                    + " offset, so extra arguments have no meaning. Pass the offset and the"
                    + " value as the only two arguments.");
            return;
        }

        checkNewIntrinsicReceiver(node);

        SastNode offset = node.args.get(0);

        owner.ensureUInt32Compatible(
                offset,
                node.intrinsicKind.runtimeName + "() offset",
                true
        );

        owner.ensureStaticArrayByteAccessInBounds(
                node.receiver.type,
                offset,
                TypeChecker.bitWidth(node.intrinsicKind.argKind(1).primitiveKind) / 8,
                node.intrinsicKind.runtimeName + "() offset"
        );

        SemanticIntrinsicArgKind valueKind = node.intrinsicKind.argKind(1);

        owner.ensureScalarCompatible(
                node.args.get(1),
                valueKind.primitiveKind,
                node.intrinsicKind.runtimeName + "() value"
        );

        ensureArrayIntrinsicReturnType(node, SastType.voidType());
    }

    private void visitArrayBitIntrinsic(SastNode node) {
        if (!node.intrinsicKind.acceptsArity(node.args.size())) {
            diagnostics.error("Array intrinsic '"
                    + node.intrinsicKind.runtimeName
                    + "' expects "
                    + node.intrinsicKind.arityDescription()
                    + " arguments, got "
                    + node.args.size()
                    + ". Each bit intrinsic has one fixed argument list, so missing or"
                    + " extra arguments cannot form a valid call. Pass exactly the"
                    + " documented arguments.");
            return;
        }

        checkNewIntrinsicReceiver(node);

        for (int i = 0; i < node.args.size(); i++) {
            SemanticIntrinsicArgKind argKind = node.intrinsicKind.argKind(i);

            owner.ensureScalarCompatible(
                    node.args.get(i),
                    argKind.primitiveKind,
                    node.intrinsicKind.runtimeName + "() argument " + (i + 1)
            );
        }

        if (node.intrinsicKind == SemanticArrayIntrinsicKind.READ_BIT
                || node.intrinsicKind == SemanticArrayIntrinsicKind.WRITE_BIT
                || node.intrinsicKind == SemanticArrayIntrinsicKind.FLIP_BIT) {
            owner.ensureStaticArrayBitIndexInBounds(
                    node.receiver.type,
                    node.args.get(0),
                    node.intrinsicKind.runtimeName + "() bit index"
            );
        }

        ensureArrayIntrinsicReturnType(node, node.intrinsicKind.returnType());
    }

    private void checkNewIntrinsicReceiver(SastNode node) {
        if (node.intrinsicKind.requiresStorageLValue()
                && !owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error(node.intrinsicKind.runtimeName
                    + "() receiver must be a storage-backed lvalue. This intrinsic"
                    + " writes through the receiver, so a temporary or call result would"
                    + " lose the update. Bind the array to a variable, field, or $(ptr)"
                    + " expression and call the intrinsic on that lvalue.");
        }

        if (!types.isArrayType(node.receiver.type)) {
            diagnostics.error(node.intrinsicKind.runtimeName
                    + "() receiver must be an array, got "
                    + types.describeSafe(node.receiver.type)
                    + ". Array intrinsics read the receiver's element type and length,"
                    + " so non-array values have no elements to operate on. Use a dynamic"
                    + " array ([T]) or static array ([T, N]); dereference an array"
                    + " pointer with $(...) first.");
            return;
        }

        SastType elementType = types.elementTypeOfArray(node.receiver.type);

        if (elementType == null) {
            return;
        }

        switch (node.intrinsicKind.elementRule) {
            case NONE -> { }

            case MEMSET_SAFE -> {
                if (!types.isMemsetSafe(elementType)) {
                    diagnostics.error(node.intrinsicKind.runtimeName
                            + "() receiver element type is not memset-safe: "
                            + elementType.describe()
                            + ". Byte-wise memset cannot initialize enums, pointers, or"
                            + " dynamic arrays correctly, because those types require valid"
                            + " representations or managed storage that a repeated byte"
                            + " cannot guarantee. Use a memset-safe element type (a"
                            + " primitive integer, static array, or struct of such fields),"
                            + " or fill the elements explicitly.");
                }
            }

            case MEMCPY_SAFE -> {
                if (!types.isMemcpySafe(elementType)) {
                    diagnostics.error(node.intrinsicKind.runtimeName
                            + "() receiver element type is not memcpy-safe: "
                            + elementType.describe()
                            + ". Byte-wise copying would duplicate managed storage in"
                            + " dynamic arrays and aggregates that contain them. Use a"
                            + " memcpy-safe element type, or copy the elements with a"
                            + " loop that handles inner arrays explicitly.");
                }
            }

            case BYTE -> {
                if (!TypeChecker.isByteType(elementType)) {
                    diagnostics.error(node.intrinsicKind.runtimeName
                            + "() receiver element type must be byte, got "
                            + types.describeSafe(elementType)
                            + ". This operation addresses the receiver as raw bytes, so"
                            + " each element must occupy exactly one byte. Use a [byte]"
                            + " or [byte, N] array, or convert the data to byte storage"
                            + " first.");
                }
            }
        }
    }

    private void visitArrayAllocIntrinsic(SastNode node) {
        if (node.args.size() != 1) {
            diagnostics.error("Array intrinsic '"
                    + node.intrinsicKind.runtimeName
                    + "' expects exactly 1 argument, got "
                    + node.args.size()
                    + ". An init, init_zero, or resize call is fully described by one"
                    + " element count, so extra arguments have no meaning. Pass exactly one"
                    + " size argument.");
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error(node.intrinsicKind.runtimeName
                    + "() receiver must be a storage-backed lvalue. init, init_zero,"
                    + " and resize allocate or resize storage owned by the receiver, so"
                    + " they must update a durable variable, field, or $(ptr) target."
                    + " Bind the dynamic array to such an lvalue before calling it.");
        }

        if (!types.isDynamicArrayType(node.receiver.type)) {
            diagnostics.error(node.intrinsicKind.runtimeName
                    + "() receiver must be a dynamic array, got "
                    + types.describeSafe(node.receiver.type)
                    + ". init, init_zero, and resize manage a runtime length and owned"
                    + " heap storage, which static arrays do not have. Declare the"
                    + " receiver as a dynamic array ([T]), or use a static array without"
                    + " init/resize.");
        }

        owner.ensureArraySizeCompatible(
                node.args.get(0),
                node.intrinsicKind.runtimeName + "() size"
        );

        ensureArrayIntrinsicReturnType(
                node,
                SastType.primitive(SemanticPrimitiveKind.INT32)
        );
    }

    private void visitArrayUninitIntrinsic(SastNode node) {
        if (node.args.size() != 0) {
            diagnostics.error("Array intrinsic 'uninit' expects exactly 0 arguments, got "
                    + node.args.size()
                    + ". uninit releases the whole dynamic array and takes no options or"
                    + " ranges. Call it with no arguments.");
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("uninit() receiver must be a storage-backed lvalue. This"
                    + " intrinsic frees storage owned by the receiver, so it must update"
                    + " a durable variable, field, or $(ptr) target. Bind the dynamic"
                    + " array to such an lvalue before calling it.");
        }

        if (!types.isDynamicArrayType(node.receiver.type)) {
            diagnostics.error("uninit() receiver must be a dynamic array, got "
                    + types.describeSafe(node.receiver.type)
                    + ". uninit() frees element storage owned by a dynamic array, so"
                    + " there is nothing to free on a static array. Declare the receiver"
                    + " as [T], or drop uninit() for fixed-size storage.");
        }

        ensureArrayIntrinsicReturnType(
                node,
                SastType.voidType()
        );
    }

    private void visitArrayMemsetIntrinsic(SastNode node) {
        if (node.args.size() != 1 && node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memset' expects either 1 or 3 arguments, got "
                    + node.args.size()
                    + ". memset fills the whole array or one element range, so those are"
                    + " the only valid call shapes. Pass the fill value alone, or the"
                    + " value, start, and count.");
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memset() receiver must be a storage-backed lvalue. This"
                    + " intrinsic writes through the receiver, so a temporary or call"
                    + " result would lose the fill. Bind the array to a variable, field,"
                    + " or $(ptr) expression and call memset() on that lvalue.");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memset() receiver must be an array"
        );

        SastType elementType = types.elementTypeOfArray(node.receiver.type);

        if (elementType != null && !types.isMemsetSafe(elementType)) {
            diagnostics.error("memset() receiver element type is not memset-safe: "
                    + elementType.describe()
                    + ". Byte-wise memset cannot initialize enums, pointers, or dynamic"
                    + " arrays correctly, because those types require valid"
                    + " representations or managed storage that a repeated byte cannot"
                    + " guarantee. Use a memset-safe element type (a primitive"
                    + " integer, static array, or struct of such fields), or fill the"
                    + " elements explicitly.");
        }

        SastNode value = node.args.get(0);

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
                SastType.voidType()
        );
    }

    private void visitArrayMemcpyIntrinsic(SastNode node) {
        if (node.args.size() != 1 && node.args.size() != 4) {
            diagnostics.error("Array intrinsic 'memcpy' expects either 1 or 4 arguments, got "
                    + node.args.size()
                    + ". memcpy copies the whole source or one source range, so those"
                    + " are the only valid call shapes. Pass the source pointer alone, or"
                    + " add destination start, source start, and count.");
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memcpy() target must be a storage-backed lvalue. memcpy"
                    + " writes into the target, so a temporary or call result would"
                    + " discard the copied bytes. Bind the target to a variable, field,"
                    + " or $(ptr) expression first.");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memcpy() target must be an array"
        );

        SastNode sourcePtr = node.args.get(0);
        owner.checkExpr(sourcePtr);

        SastType sourceArrayType = null;

        if (types.ensureValueExpression(
                sourcePtr,
                "memcpy() source pointer"
        )) {
            if (!(sourcePtr.type.kind == SastTypeKind.PTR)) {
                diagnostics.error("memcpy() source must be an pointer to an array, got "
                        + types.describeSafe(sourcePtr.type)
                        + ". memcpy reads the source through a pointer so it can use the"
                        + " array's element type and length. Pass getview(source),"
                        + " getptr(source), or an existing pointer/view instead of the"
                        + " array value.");
            } else if (!types.isArrayType(sourcePtr.type.referencedType)) {
                diagnostics.error("memcpy() source must be an pointer to an array, got ptr<"
                        + types.describeSafe(sourcePtr.type.referencedType)
                        + ">. memcpy needs a pointer whose referent is an array, because"
                        + " a pointer to one value has no array length or element stride"
                        + " to copy. Pass a pointer/view of the whole array; to copy one"
                        + " element, assign it directly.");
            } else {
                sourceArrayType = sourcePtr.type.referencedType;
            }
        }

        SastType targetArrayType = node.receiver.type;

        if (targetArrayType != null && sourceArrayType != null) {
            SastType targetElem = types.elementTypeOfArray(targetArrayType);
            SastType sourceElem = types.elementTypeOfArray(sourceArrayType);

            if (targetElem != null && sourceElem != null
                    && !TypeChecker.sameType(targetElem, sourceElem)) {
                diagnostics.error("memcpy() requires source and target arrays to have identical element types, got "
                        + targetElem.describe()
                        + " and "
                        + sourceElem.describe()
                        + ". memcpy copies elements at the destination's stride, so"
                        + " mismatched element layouts would corrupt values. Copy"
                        + " between arrays with the same element type, or copy both into"
                        + " [byte] buffers first.");
            }

            if (targetElem != null && !types.isMemcpySafe(targetElem)) {
                diagnostics.error("memcpy() target array element type is not memcpy-safe: "
                        + targetElem.describe()
                        + ". A byte-wise copy would duplicate managed storage in dynamic"
                        + " arrays and their containing aggregates. Use a memcpy-safe"
                        + " target element type, or copy elements individually with a"
                        + " loop.");
            }

            if (sourceElem != null && !types.isMemcpySafe(sourceElem)) {
                diagnostics.error("memcpy() source array element type is not memcpy-safe: "
                        + sourceElem.describe()
                        + ". A byte-wise copy would duplicate managed storage in dynamic"
                        + " arrays and their containing aggregates. Use a memcpy-safe"
                        + " source element type, or copy elements individually with a"
                        + " loop.");
            }
        }

        if (node.args.size() == 4) {
            SastNode dstStart = node.args.get(1);
            SastNode srcStart = node.args.get(2);
            SastNode count = node.args.get(3);

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
                SastType.voidType()
        );
    }

    private void visitArrayMemmoveIntrinsic(SastNode node) {
        if (node.args.size() != 3) {
            diagnostics.error("Array intrinsic 'memmove' expects exactly 3 arguments, got "
                    + node.args.size()
                    + ". memmove moves one range within the receiver and needs every"
                    + " endpoint explicitly. Pass destination start, source start, and"
                    + " count.");
            return;
        }

        if (!owner.isStorageBackedLValue(node.receiver)) {
            diagnostics.error("memmove() receiver must be a storage-backed lvalue."
                    + " memmove writes through the receiver, so a temporary or call"
                    + " result would lose the moved elements. Bind the array to a"
                    + " variable, field, or $(ptr) expression and call memmove() on that"
                    + " lvalue.");
        }

        types.ensureArrayType(
                node.receiver.type,
                "memmove() receiver must be an array"
        );

        SastType receiverElem =
                types.elementTypeOfArray(node.receiver.type);

        if (receiverElem != null && !types.isMemcpySafe(receiverElem)) {
            diagnostics.error("memmove() receiver array element type is not memcpy-safe: "
                    + receiverElem.describe()
                    + ". memmove copies bytes within the receiver, which would duplicate"
                    + " managed storage in dynamic arrays and their containing"
                    + " aggregates. Use a memcpy-safe element type, or move inner arrays"
                    + " element by element.");
        }

        SastNode dstStart = node.args.get(0);
        SastNode srcStart = node.args.get(1);
        SastNode count = node.args.get(2);

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
                SastType.voidType()
        );
    }

    private void visitArrayLengthIntrinsic(SastNode node) {
        if (node.args.size() != 0) {
            diagnostics.error("Array intrinsic 'length' expects exactly 0 arguments, got "
                    + node.args.size()
                    + ". length() describes the whole array and takes no parameters or"
                    + " range. Call it with no arguments.");
            return;
        }

        types.ensureArrayType(
                node.receiver.type,
                "length() receiver must be an array"
        );

        ensureArrayIntrinsicReturnType(
                node,
                SastType.primitive(SemanticPrimitiveKind.UINT32)
        );
    }

    private void ensureArrayIntrinsicReturnType(
            SastNode node,
            SastType expected
    ) {
        if (!TypeChecker.sameType(node.type, expected)) {
            diagnostics.error("Array intrinsic '"
                    + node.intrinsicKind.runtimeName
                    + "' expression type mismatch: node has "
                    + types.describeSafe(node.type)
                    + ", expected "
                    + types.describeSafe(expected));
        }
    }
}
