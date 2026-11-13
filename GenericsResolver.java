import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GenericsResolver {

    static final int MAX_INSTANCES = 1024;
    static final int MAX_DEPTH = 64;

    private static final class Template {
        final boolean struct;
        final String namespace;
        final String name;
        final List<AstNode> parameters;
        final AstNode declaration;

        Template(
                boolean struct,
                String namespace,
                String name,
                List<AstNode> parameters,
                AstNode declaration
        ) {
            this.struct = struct;
            this.namespace = namespace;
            this.name = name;
            this.parameters = parameters;
            this.declaration = declaration;
        }

        String key() {
            return qualifiedKey(namespace, name);
        }
    }

    private static final class Instance {
        final Template template;
        final String generatedName;
        final Map<String, AstNode> typeBindings;
        final Map<String, AstNode> valueBindings;
        final List<String> canonicalArguments;
        final int depth;

        boolean complete;

        Instance(
                Template template,
                String generatedName,
                Map<String, AstNode> typeBindings,
                Map<String, AstNode> valueBindings,
                List<String> canonicalArguments,
                int depth
        ) {
            this.template = template;
            this.generatedName = generatedName;
            this.typeBindings = typeBindings;
            this.valueBindings = valueBindings;
            this.canonicalArguments = canonicalArguments;
            this.depth = depth;
        }
    }

    private static final class Substitution {
        final String namespace;
        final Map<String, AstNode> typeBindings = new LinkedHashMap<>();
        final Map<String, AstNode> valueBindings = new LinkedHashMap<>();

        String selfTemplateName;
        String selfGeneratedName;
        List<String> selfCanonicalArguments;
        int currentDepth = 0;

        Substitution(String namespace) {
            this.namespace = namespace;
        }
    }

    private final DiagnosticBag diagnostics;
    private final TargetInfo target;
    private final Map<String, Template> templates = new LinkedHashMap<>();
    private final Set<String> concreteTypeKeys = new HashSet<>();
    private final Set<String> concreteFunctionKeys = new HashSet<>();
    private final Map<String, Instance> instances = new LinkedHashMap<>();
    private final Set<String> generatedInstanceNames = new HashSet<>();
    private final List<Instance> queue = new ArrayList<>();
    private final Map<String, List<AstNode>> generatedByNamespace =
            new LinkedHashMap<>();
    private final Map<String, AstNode> hoistedStringGlobals =
            new LinkedHashMap<>();
    private final SpecializationDesugarer desugarer;
    private boolean instanceLimitReported = false;

    GenericsResolver(DiagnosticBag diagnostics) {
        this(diagnostics, null);
    }

    GenericsResolver(DiagnosticBag diagnostics, TargetInfo target) {
        this.diagnostics = diagnostics == null ? new DiagnosticBag() : diagnostics;
        this.target = target;
        this.desugarer = new SpecializationDesugarer(
                this.diagnostics,
                hoistedStringGlobals
        );
    }

    AstNode expand(AstNode program) {
        collect(program.items, null);

        AstNode rewritten = AstNode.program(
                program.span,
                rewriteItems(program.items, null)
        );

        processQueue();

        List<AstNode> items = insertGenerated(rewritten.items, null);
        items = appendMissingGeneratedNamespaces(items);
        items = addHoistedStringGlobals(items);

        return AstNode.program(program.span, items);
    }

    static boolean containsGenericUse(AstNode node) {
        if (node == null) {
            return false;
        }

        if (node.kind == AstKind.STRUCT_TYPE
                && node.genericArgs != null
                && !node.genericArgs.isEmpty()) {
            return true;
        }

        if (node.kind == AstKind.CALL
                && node.genericArgs != null
                && !node.genericArgs.isEmpty()) {
            return true;
        }

        if (containsGenericUse(node.type)
                || containsGenericUse(node.target)
                || containsGenericUse(node.value)
                || containsGenericUse(node.condition)
                || containsGenericUse(node.thenBranch)
                || containsGenericUse(node.elseBranch)
                || containsGenericUse(node.body)
                || containsGenericUse(node.expr)
                || containsGenericUse(node.left)
                || containsGenericUse(node.right)
                || containsGenericUse(node.index)
                || containsGenericUse(node.callee)
                || containsGenericUse(node.init)
                || containsGenericUse(node.update)
                || containsGenericUse(node.level)
                || containsGenericUse(node.sizeExpr)) {
            return true;
        }

        return containsGenericUse(node.items)
                || containsGenericUse(node.parameters)
                || containsGenericUse(node.fields)
                || containsGenericUse(node.methods)
                || containsGenericUse(node.members)
                || containsGenericUse(node.variants)
                || containsGenericUse(node.args)
                || containsGenericUse(node.elements)
                || containsGenericUse(node.thenItems)
                || containsGenericUse(node.elseItems)
                || containsGenericUse(node.updateItems)
                || containsGenericUse(node.genericArgs);
    }

    private static boolean containsGenericUse(List<AstNode> nodes) {
        if (nodes == null) {
            return false;
        }

        for (AstNode node : nodes) {
            if (containsGenericUse(node)) {
                return true;
            }
        }

        return false;
    }

    private void collect(List<AstNode> items, String namespace) {
        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                collect(item.items, join(namespace, item.name));
                continue;
            }

            if (item.kind == AstKind.DECL_IF) {
                collect(item.thenItems, namespace);
                collect(item.elseItems, namespace);
                continue;
            }

            if (item.kind == AstKind.STRUCT_DECL) {
                String key = qualifiedKey(namespace, item.name);

                if (item.genericModifier && item.genericParams.isEmpty()) {
                    error(item.span,
                            "Generic struct '"
                                    + key
                                    + "' requires at least one generic parameter");
                }

                if (item.isGeneric) {
                    if (concreteTypeKeys.contains(key)) {
                        error(item.span,
                                "Generic struct '"
                                        + key
                                        + "' conflicts with an existing concrete struct or enum name");
                    }

                    if (templates.containsKey(key)) {
                        error(item.span,
                                "Duplicate generic template '"
                                        + key
                                        + "': generic template overloading is not supported in this version");
                    } else {
                        checkGenericParameters(item.genericParams, item.span);
                        templates.put(key, new Template(
                                true,
                                namespace,
                                item.name,
                                item.genericParams,
                                item
                        ));
                    }
                } else {
                    Template existing = templates.get(key);

                    if (existing != null && existing.struct) {
                        error(item.span,
                                "Struct '"
                                        + key
                                        + "' conflicts with a generic struct template of the same name");
                    }

                    concreteTypeKeys.add(key);
                }

                for (AstNode method : item.methods) {
                    if (method.isGeneric) {
                        error(method.span,
                                "Independent generic methods are not supported in this version: method '"
                                        + item.name
                                        + "."
                                        + method.name
                                        + "' may only use the struct's generic parameters");
                    }
                }

                item.methods = item.methods.stream()
                        .filter(method -> !method.isGeneric)
                        .toList();
                item.members = item.members.stream()
                        .filter(member -> !(member.kind == AstKind.FUNCTION_DECL
                                && member.isGeneric))
                        .toList();

                continue;
            }

            if (item.kind == AstKind.ENUM_DECL) {
                String key = qualifiedKey(namespace, item.name);
                Template existing = templates.get(key);

                if (existing != null && existing.struct) {
                    error(item.span,
                            "Enum '"
                                    + key
                                    + "' conflicts with a generic struct template of the same name");
                }

                concreteTypeKeys.add(key);
                continue;
            }

            if (item.kind == AstKind.FUNCTION_DECL) {
                String key = qualifiedKey(namespace, item.name);

                if (item.genericModifier && item.genericParams.isEmpty()) {
                    error(item.span,
                            "Generic function '"
                                    + key
                                    + "' requires at least one generic parameter");
                }

                if (item.isGeneric) {
                    if ("main".equals(item.name) && namespace == null) {
                        error(item.span,
                                "The entry-point function 'main' cannot be generic");
                    }

                    if (templates.containsKey(key)) {
                        error(item.span,
                                "Duplicate generic template '"
                                        + key
                                        + "': generic template overloading is not supported in this version");
                    } else {
                        checkGenericParameters(item.genericParams, item.span);
                        templates.put(key, new Template(
                                false,
                                namespace,
                                item.name,
                                item.genericParams,
                                item
                        ));
                    }
                } else {
                    concreteFunctionKeys.add(key);
                }
            }
        }
    }

    private void checkGenericParameters(List<AstNode> params, SourceSpan span) {
        Set<String> seen = new HashSet<>();

        for (AstNode param : params) {
            if (!seen.add(param.name)) {
                error(param.span,
                        "Duplicate generic parameter name '"
                                + param.name
                                + "'");
            }

            if (!param.typeParameter) {
                if (param.type == null
                        || param.type.kind != AstKind.PRIMITIVE_TYPE) {
                    error(param.span,
                            "Generic value parameter '"
                                    + param.name
                                    + "' must have one of the eight primitive integer types");
                }
            }
        }
    }

    private List<AstNode> rewriteItems(List<AstNode> items, String namespace) {
        List<AstNode> out = new ArrayList<>();

        for (AstNode item : items) {
            if (item.isGeneric) {
                continue;
            }

            if (item.kind == AstKind.NAMESPACE) {
                out.add(AstNode.namespace(
                        item.span,
                        item.name,
                        rewriteItems(item.items, join(namespace, item.name))
                ));
                continue;
            }

            out.add(substituteNode(item, new Substitution(namespace)));
        }

        return out;
    }

    private void processQueue() {
        for (int i = 0; i < queue.size(); i++) {
            Instance instance = queue.get(i);

            if (instance.complete) {
                continue;
            }

            instance.complete = true;

            Substitution env = new Substitution(instance.template.namespace);
            env.typeBindings.putAll(instance.typeBindings);
            env.valueBindings.putAll(instance.valueBindings);
            env.currentDepth = instance.depth;

            if (instance.template.struct) {
                env.selfTemplateName = instance.template.name;
                env.selfGeneratedName = instance.generatedName;
                env.selfCanonicalArguments = instance.canonicalArguments;
            }

            AstNode clone = substituteNode(instance.template.declaration, env);

            if (clone == null) {
                continue;
            }

            clone.genericParams = List.of();
            clone.isGeneric = false;
            clone.name = instance.generatedName;

            AstNode desugared = desugarer.desugarSpecialization(clone);

            if (desugared == null) {
                continue;
            }

            generatedByNamespace
                    .computeIfAbsent(namespaceKey(instance.template.namespace),
                            ignored -> new ArrayList<>())
                    .add(desugared);
        }
    }

    private AstNode substituteNode(AstNode node, Substitution env) {
        if (node == null) {
            return null;
        }

        if (node.kind == AstKind.INT) {
            return node.copyShallow();
        }

        if (node.kind == AstKind.VAR) {
            AstNode bound = env.valueBindings.get(node.name);

            if (bound != null) {
                return AstNode.typedIntLiteral(
                        node.span,
                        bound.text,
                        bound.literalPrimitive
                );
            }

            return node.copyShallow();
        }

        if (node.kind == AstKind.STRUCT_TYPE) {
            return substituteStructType(node, env);
        }

        if (node.kind == AstKind.CALL) {
            return substituteCall(node, env);
        }

        if (node.kind == AstKind.GENERIC_ARG) {
            if (node.typeParameter) {
                return AstNode.genericArgType(
                        node.span,
                        substituteNode(node.type, env)
                );
            }

            return AstNode.genericArgValue(
                    node.span,
                    substituteNode(node.value, env)
            );
        }

        if (node.kind == AstKind.GENERIC_PARAM) {
            if (node.typeParameter) {
                return AstNode.genericParam(
                        node.span,
                        node.name,
                        null,
                        true
                );
            }

            return AstNode.genericParam(
                    node.span,
                    node.name,
                    substituteNode(node.type, env),
                    false
            );
        }

        if (node.kind == AstKind.COMPILE_TIME_IF) {
            AstNode condition = substituteNode(node.condition, env);
            BigInteger folded = target == null
                    ? null
                    : CompileTimeIntrinsicCore.tryEvaluateInt(condition, target);

            if (folded != null) {
                AstNode selected = folded.signum() != 0
                        ? node.thenBranch
                        : node.elseBranch;

                return AstNode.compileTimeIf(
                        node.span,
                        AstNode.typedIntLiteral(node.span, "1", PrimitiveKind.UINT8),
                        selected == null ? null : substituteNode(selected, env),
                        null
                );
            }

            return AstNode.compileTimeIf(
                    node.span,
                    condition,
                    substituteNode(node.thenBranch, env),
                    substituteNode(node.elseBranch, env)
            );
        }

        if (node.kind == AstKind.DECL_IF) {
            AstNode condition = substituteNode(node.condition, env);
            BigInteger folded = target == null
                    ? null
                    : CompileTimeIntrinsicCore.tryEvaluateInt(condition, target);

            if (folded != null) {
                List<AstNode> selected = folded.signum() != 0
                        ? node.thenItems
                        : node.elseItems;

                return AstNode.declIf(
                        node.span,
                        AstNode.typedIntLiteral(node.span, "1", PrimitiveKind.UINT8),
                        substituteList(selected, env),
                        List.of()
                );
            }

            return AstNode.declIf(
                    node.span,
                    condition,
                    substituteList(node.thenItems, env),
                    substituteList(node.elseItems, env)
            );
        }

        AstNode copy = node.copyShallow();

        copy.type = substituteNode(node.type, env);
        copy.target = substituteNode(node.target, env);
        copy.value = substituteNode(node.value, env);
        copy.condition = substituteNode(node.condition, env);
        copy.thenBranch = substituteNode(node.thenBranch, env);
        copy.elseBranch = substituteNode(node.elseBranch, env);
        copy.body = substituteNode(node.body, env);
        copy.expr = substituteNode(node.expr, env);
        copy.left = substituteNode(node.left, env);
        copy.right = substituteNode(node.right, env);
        copy.index = substituteNode(node.index, env);
        copy.callee = substituteNode(node.callee, env);
        copy.init = substituteNode(node.init, env);
        copy.update = substituteNode(node.update, env);
        copy.level = substituteNode(node.level, env);
        copy.sizeExpr = substituteNode(node.sizeExpr, env);

        copy.items = substituteList(node.items, env);
        copy.parameters = substituteList(node.parameters, env);
        copy.fields = substituteList(node.fields, env);
        copy.methods = substituteList(node.methods, env);
        copy.members = substituteList(node.members, env);
        copy.variants = substituteList(node.variants, env);
        copy.args = substituteList(node.args, env);
        copy.elements = substituteList(node.elements, env);
        copy.thenItems = substituteList(node.thenItems, env);
        copy.elseItems = substituteList(node.elseItems, env);
        copy.updateItems = substituteList(node.updateItems, env);
        copy.genericParams = substituteList(node.genericParams, env);
        copy.genericArgs = substituteList(node.genericArgs, env);

        return copy;
    }

    private List<AstNode> substituteList(List<AstNode> nodes, Substitution env) {
        if (nodes == null || nodes.isEmpty()) {
            return nodes;
        }

        List<AstNode> out = new ArrayList<>(nodes.size());

        for (AstNode node : nodes) {
            AstNode substituted = substituteNode(node, env);

            if (substituted != null) {
                out.add(substituted);
            }
        }

        return List.copyOf(out);
    }

    private AstNode substituteStructType(AstNode node, Substitution env) {
        if (node.names == null || node.names.isEmpty()) {
            AstNode bound = env.typeBindings.get(node.name);

            if (bound != null) {
                if (node.genericArgs != null && !node.genericArgs.isEmpty()) {
                    error(node.span,
                            "Type parameter '"
                                    + node.name
                                    + "' cannot take generic arguments");
                }

                return substituteNode(bound, env);
            }
        }

        boolean selfReference = env.selfTemplateName != null
                && node.name.equals(env.selfTemplateName)
                && (node.names == null || node.names.isEmpty());

        if (selfReference && (node.genericArgs == null
                || node.genericArgs.isEmpty())) {
            return AstNode.structType(
                    node.span,
                    List.of(),
                    env.selfGeneratedName
            );
        }

        if (node.genericArgs != null && !node.genericArgs.isEmpty()) {
            Template template = resolveTemplate(node.names, node.name, true, env);

            if (template == null || !template.struct) {
                if (template == null) {
                    error(node.span,
                            "Unknown generic struct '"
                                    + displayName(node.names, node.name)
                                    + "'");
                } else {
                    error(node.span,
                            "'"
                                    + displayName(node.names, node.name)
                                    + "' is not a generic struct");
                }

                return AstNode.structType(
                        node.span,
                        node.names,
                        node.name
                );
            }

            BoundArguments bound = bindArguments(
                    template,
                    node.genericArgs,
                    env,
                    node.span
            );

            if (bound == null) {
                return AstNode.structType(
                        node.span,
                        node.names,
                        node.name
                );
            }

            if (selfReference
                    && bound.canonicalKeys.equals(env.selfCanonicalArguments)) {
                return AstNode.structType(
                        node.span,
                        List.of(),
                        env.selfGeneratedName
                );
            }

            String generated = instantiate(template, bound, env, node.span);

            return AstNode.structType(
                    node.span,
                    splitNamespace(template.namespace),
                    generated
            );
        }

        Template template = resolveTemplate(node.names, node.name, true, env);

        if (template != null && template.struct) {
            error(node.span,
                    "Generic struct '"
                            + displayName(node.names, node.name)
                            + "' requires explicit type arguments");
        }

        AstNode copy = node.copyShallow();
        copy.names = node.names;
        copy.name = node.name;
        return copy;
    }

    private AstNode substituteCall(AstNode node, Substitution env) {
        AstNode callee = substituteNode(node.callee, env);
        List<AstNode> args = substituteList(node.args, env);

        if (node.genericArgs != null && !node.genericArgs.isEmpty()) {
            Template template = resolveCallTemplate(callee, env);

            if (template == null || template.struct) {
                if (template == null) {
                    error(node.span,
                            "Unknown generic function '"
                                    + calleeDisplayName(callee)
                                    + "'");
                } else {
                    error(node.span,
                            "'"
                                    + calleeDisplayName(callee)
                                    + "' is not a generic function");
                }

                return AstNode.call(node.span, callee, args);
            }

            BoundArguments bound = bindArguments(
                    template,
                    node.genericArgs,
                    env,
                    node.span
            );

            if (bound == null) {
                return AstNode.call(node.span, callee, args);
            }

            String generated = instantiate(template, bound, env, node.span);

            return AstNode.call(
                    node.span,
                    rewriteCalleeName(callee, template.namespace, generated),
                    args
            );
        }

        if (callee != null
                && (callee.kind == AstKind.VAR
                        || callee.kind == AstKind.QUALIFIED_NAME)) {
            Template template = resolveCallTemplate(callee, env);

            if (template != null
                    && !template.struct
                    && !concreteFunctionExists(callee, env)) {
                error(node.span,
                        "Generic function '"
                                + calleeDisplayName(callee)
                                + "' requires explicit type arguments");
            }
        }

        return AstNode.call(node.span, callee, args);
    }

    private AstNode rewriteCalleeName(
            AstNode callee,
            String templateNamespace,
            String generatedName
    ) {
        if (callee.kind == AstKind.VAR) {
            return AstNode.var(callee.span, generatedName);
        }

        if (callee.kind == AstKind.QUALIFIED_NAME) {
            List<String> segments = new ArrayList<>(callee.names);

            if (!segments.isEmpty()) {
                segments.set(segments.size() - 1, generatedName);
            }

            return AstNode.qualifiedName(callee.span, segments);
        }

        return callee;
    }

    private static final class BoundArguments {
        final Map<String, AstNode> typeBindings = new LinkedHashMap<>();
        final Map<String, AstNode> valueBindings = new LinkedHashMap<>();
        final List<String> canonicalKeys = new ArrayList<>();
    }

    private BoundArguments bindArguments(
            Template template,
            List<AstNode> args,
            Substitution env,
            SourceSpan span
    ) {
        if (args.size() != template.parameters.size()) {
            error(span,
                    "Generic "
                            + (template.struct ? "struct" : "function")
                            + " '"
                            + template.key()
                            + "' expects "
                            + template.parameters.size()
                            + " type/value argument"
                            + (template.parameters.size() == 1 ? "" : "s")
                            + ", got "
                            + args.size());
            return null;
        }

        BoundArguments bound = new BoundArguments();

        for (int i = 0; i < template.parameters.size(); i++) {
            AstNode parameter = template.parameters.get(i);
            AstNode argument = args.get(i);

            if (parameter.typeParameter) {
                if (!argument.typeParameter) {
                    error(argument.span,
                            "Type argument expected for generic parameter '"
                                    + parameter.name
                                    + "', got a value argument");
                    return null;
                }

                AstNode typeArgument = substituteNode(argument.type, env);

                if (!validateTypeArgument(typeArgument, env, argument.span)) {
                    return null;
                }

                bound.typeBindings.put(parameter.name, typeArgument);
                bound.canonicalKeys.add(canonicalType(typeArgument, env));
                continue;
            }

            AstNode valueExpression;

            if (argument.typeParameter) {
                if (argument.type != null
                        && argument.type.kind == AstKind.STRUCT_TYPE
                        && (argument.type.names == null
                                || argument.type.names.isEmpty())
                        && (argument.type.genericArgs == null
                                || argument.type.genericArgs.isEmpty())) {
                    valueExpression = AstNode.var(
                            argument.span,
                            argument.type.name
                    );
                } else {
                    error(argument.span,
                            "Value argument expected for generic parameter '"
                                    + parameter.name
                                    + "', got a type argument");
                    return null;
                }
            } else {
                valueExpression = argument.value;
            }

            AstNode substituted = substituteNode(valueExpression, env);
            BigInteger value = ConstExpressionEvaluator.tryFold(substituted);

            if (value == null) {
                error(argument.span,
                        "Generic value argument for parameter '"
                                + parameter.name
                                + "' is not a compile-time constant");
                return null;
            }

            PrimitiveKind kind = parameter.type.primitive;
            SemanticPrimitiveKind semanticKind =
                    SemanticPrimitiveKind.valueOf(kind.name());

            if (!TypeChecker.fitsBigIntegerToPrimitive(value, semanticKind)) {
                error(argument.span,
                        "Generic value argument "
                                + value
                                + " does not fit in "
                                + kind.name().toLowerCase()
                                + " for parameter '"
                                + parameter.name
                                + "'");
                return null;
            }

            AstNode typed = AstNode.typedIntLiteral(
                    argument.span,
                    value.toString(10),
                    kind
            );

            bound.valueBindings.put(parameter.name, typed);
            bound.canonicalKeys.add(kind.name() + ":" + value.toString(10));
        }

        return bound;
    }

    private boolean validateTypeArgument(
            AstNode type,
            Substitution env,
            SourceSpan span
    ) {
        if (type == null) {
            error(span, "Missing generic type argument");
            return false;
        }

        if (type.kind == AstKind.STRUCT_TYPE) {
            if (type.names == null || type.names.isEmpty()) {
                AstNode bound = env.typeBindings.get(type.name);

                if (bound != null) {
                    return validateTypeArgument(bound, env, span);
                }

                if (type.genericArgs == null || type.genericArgs.isEmpty()) {
                    if (!isKnownTypeName(type.name, env)) {
                        error(span,
                                "Generic type argument '"
                                        + type.name
                                        + "' is not a declared type or type parameter");
                        return false;
                    }
                }
            }

            if (type.genericArgs != null && !type.genericArgs.isEmpty()) {
                Template template = resolveTemplate(
                        type.names,
                        type.name,
                        true,
                        env
                );

                if (template == null) {
                    error(span,
                            "Unknown generic struct '"
                                    + displayName(type.names, type.name)
                                    + "'");
                    return false;
                }
            }
        }

        if (type.kind == AstKind.DYNAMIC_ARRAY_TYPE
                || type.kind == AstKind.PTR_TYPE) {
            return validateTypeArgument(type.type, env, span);
        }

        if (type.kind == AstKind.STATIC_ARRAY_TYPE) {
            return validateTypeArgument(type.type, env, span);
        }

        if (type.kind == AstKind.TUPLE_TYPE) {
            for (AstNode element : type.elements) {
                if (!validateTypeArgument(element.type, env, span)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isKnownTypeName(String name, Substitution env) {
        if (env.typeBindings.containsKey(name)) {
            return true;
        }

        if (generatedInstanceNames.contains(name)) {
            return true;
        }

        if (concreteTypeKeys.contains(qualifiedKey(env.namespace, name))
                || concreteTypeKeys.contains(name)) {
            return true;
        }

        Template template = templates.get(qualifiedKey(env.namespace, name));

        if (template != null && template.struct) {
            return true;
        }

        return templates.get(name) != null && templates.get(name).struct;
    }

    private String instantiate(
            Template template,
            BoundArguments bound,
            Substitution env,
            SourceSpan span
    ) {
        String key = template.key()
                + "<"
                + String.join(",", bound.canonicalKeys)
                + ">";

        Instance existing = instances.get(key);

        if (existing != null) {
            return existing.generatedName;
        }

        if (instances.size() >= MAX_INSTANCES) {
            if (!instanceLimitReported) {
                error(span,
                        "Generic expansion limit exceeded: more than "
                                + MAX_INSTANCES
                                + " generic instances in this compilation");
                instanceLimitReported = true;
            }

            return template.name;
        }

        int depth = env.currentDepth + 1;

        if (depth > MAX_DEPTH) {
            error(span,
                    "Generic instantiation depth limit exceeded (maximum "
                            + MAX_DEPTH
                            + ") while instantiating '"
                            + key
                            + "'");
            return template.name;
        }

        String generatedName = template.name
                + "__"
                + Mangler.hashHex(key);

        Instance instance = new Instance(
                template,
                generatedName,
                new LinkedHashMap<>(bound.typeBindings),
                new LinkedHashMap<>(bound.valueBindings),
                List.copyOf(bound.canonicalKeys),
                depth
        );

        instances.put(key, instance);
        generatedInstanceNames.add(generatedName);
        queue.add(instance);

        return generatedName;
    }

    private Template resolveCallTemplate(AstNode callee, Substitution env) {
        if (callee == null) {
            return null;
        }

        if (callee.kind == AstKind.VAR) {
            return resolveTemplate(null, callee.name, false, env);
        }

        if (callee.kind == AstKind.QUALIFIED_NAME) {
            if (callee.names.isEmpty()) {
                return null;
            }

            List<String> namespaceSegments = new ArrayList<>(
                    callee.names.subList(0, callee.names.size() - 1)
            );
            String name = callee.names.get(callee.names.size() - 1);

            return resolveTemplate(namespaceSegments, name, false, env);
        }

        return null;
    }

    private boolean concreteFunctionExists(AstNode callee, Substitution env) {
        if (callee.kind == AstKind.VAR) {
            return concreteFunctionKeys.contains(
                    qualifiedKey(env.namespace, callee.name))
                    || concreteFunctionKeys.contains(callee.name);
        }

        if (callee.kind == AstKind.QUALIFIED_NAME) {
            return concreteFunctionKeys.contains(
                    String.join("::", callee.names));
        }

        return false;
    }

    private Template resolveTemplate(
            List<String> namespaceSegments,
            String name,
            boolean structOnly,
            Substitution env
    ) {
        if (namespaceSegments != null && !namespaceSegments.isEmpty()) {
            Template template = templates.get(
                    String.join("::", namespaceSegments) + "::" + name
            );

            if (template != null && (!structOnly || template.struct)) {
                return template;
            }

            return null;
        }

        Template current = env.namespace == null
                ? null
                : templates.get(env.namespace + "::" + name);

        if (current != null && (!structOnly || current.struct)) {
            return current;
        }

        Template global = templates.get(name);

        if (global != null && (!structOnly || global.struct)) {
            return global;
        }

        return null;
    }

    private static String canonicalType(AstNode type, Substitution env) {
        if (type == null) {
            return "void";
        }

        switch (type.kind) {
            case PRIMITIVE_TYPE:
                return type.primitive.name();

            case VOID_TYPE:
                return "void";

            case DYNAMIC_ARRAY_TYPE:
                return "[" + canonicalType(type.type, env) + "]";

            case STATIC_ARRAY_TYPE:
                return "[" + canonicalType(type.type, env)
                        + ","
                        + canonicalConstant(type.sizeExpr, env)
                        + "]";

            case PTR_TYPE:
                return type.permission.name()
                        + "<"
                        + canonicalType(type.type, env)
                        + ">";

            case TUPLE_TYPE: {
                List<String> parts = new ArrayList<>();

                for (AstNode element : type.elements) {
                    parts.add(canonicalType(element.type, env));
                }

                return "(" + String.join(",", parts) + ")";
            }

            case STRUCT_TYPE: {
                if (type.names == null || type.names.isEmpty()) {
                    AstNode bound = env.typeBindings.get(type.name);

                    if (bound != null) {
                        return canonicalType(bound, env);
                    }
                }

                if (type.genericArgs != null && !type.genericArgs.isEmpty()) {
                    List<String> parts = new ArrayList<>();

                    for (AstNode arg : type.genericArgs) {
                        if (arg.typeParameter) {
                            parts.add(canonicalType(arg.type, env));
                        } else {
                            parts.add("V:" + canonicalConstant(arg.value, env));
                        }
                    }

                    return displayName(type.names, type.name)
                            + "<"
                            + String.join(",", parts)
                            + ">";
                }

                return displayName(type.names, type.name);
            }

            default:
                return type.kind.jsonName();
        }
    }

    private static String canonicalConstant(AstNode expr, Substitution env) {
        if (expr == null) {
            return "?";
        }

        BigInteger folded = ConstExpressionEvaluator.tryFold(
                substituteConstant(expr, env)
        );

        return folded == null ? "?" : folded.toString(10);
    }

    private static AstNode substituteConstant(AstNode expr, Substitution env) {
        if (expr == null) {
            return null;
        }

        if (expr.kind == AstKind.VAR) {
            AstNode bound = env.valueBindings.get(expr.name);

            if (bound != null) {
                return AstNode.typedIntLiteral(
                        expr.span,
                        bound.text,
                        bound.literalPrimitive
                );
            }

            return expr.copyShallow();
        }

        AstNode copy = expr.copyShallow();
        copy.expr = substituteConstant(expr.expr, env);
        copy.left = substituteConstant(expr.left, env);
        copy.right = substituteConstant(expr.right, env);
        return copy;
    }

    private List<AstNode> insertGenerated(List<AstNode> items, String namespace) {
        List<AstNode> out = new ArrayList<>();

        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                out.add(AstNode.namespace(
                        item.span,
                        item.name,
                        insertGenerated(
                                item.items,
                                join(namespace, item.name)
                        )
                ));
                continue;
            }

            out.add(item);
        }

        List<AstNode> generated = generatedByNamespace.remove(
                namespaceKey(namespace)
        );

        if (generated != null) {
            out.addAll(generated);
        }

        return out;
    }

    private List<AstNode> appendMissingGeneratedNamespaces(List<AstNode> items) {
        List<AstNode> out = new ArrayList<>(items);

        for (Map.Entry<String, List<AstNode>> entry
                : generatedByNamespace.entrySet()) {
            String path = entry.getKey();

            if (path.isEmpty()) {
                out.addAll(entry.getValue());
                continue;
            }

            out.add(AstNode.namespace(
                    SourceSpan.NONE,
                    path,
                    entry.getValue()
            ));
        }

        return out;
    }

    private List<AstNode> addHoistedStringGlobals(List<AstNode> items) {
        if (hoistedStringGlobals.isEmpty()) {
            return items;
        }

        Set<String> existingNames = new HashSet<>();

        for (AstNode item : items) {
            if (item.kind == AstKind.GLOBAL_VAR_DECL) {
                existingNames.add(item.name);
            }
        }

        List<AstNode> globals = new ArrayList<>();

        for (AstNode global : hoistedStringGlobals.values()) {
            if (existingNames.add(global.name)) {
                globals.add(global);
            }
        }

        if (globals.isEmpty()) {
            return items;
        }

        List<AstNode> out = new ArrayList<>(globals);
        out.addAll(items);
        return out;
    }

    private void error(SourceSpan span, String message) {
        diagnostics.push(span);
        try {
            diagnostics.error(message);
        } finally {
            diagnostics.pop();
        }
    }

    private static String join(String namespace, String name) {
        if (namespace == null || namespace.isEmpty()) {
            return name;
        }

        return namespace + "::" + name;
    }

    private static String qualifiedKey(String namespace, String name) {
        return namespace == null || namespace.isEmpty()
                ? name
                : namespace + "::" + name;
    }

    private static String namespaceKey(String namespace) {
        return namespace == null ? "" : namespace;
    }

    private static String displayName(List<String> namespace, String name) {
        if (namespace == null || namespace.isEmpty()) {
            return name;
        }

        return String.join("::", namespace) + "::" + name;
    }

    private static List<String> splitNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return List.of();
        }

        return List.of(namespace.split("::"));
    }

    private static String calleeDisplayName(AstNode callee) {
        if (callee == null) {
            return "<unknown>";
        }

        if (callee.kind == AstKind.VAR) {
            return callee.name;
        }

        if (callee.kind == AstKind.QUALIFIED_NAME) {
            return String.join("::", callee.names);
        }

        return callee.kind.jsonName();
    }
}
