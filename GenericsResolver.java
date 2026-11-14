import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        final String signatureKey;
        final String kindLabel;

        Template(
                boolean struct,
                String namespace,
                String name,
                List<AstNode> parameters,
                AstNode declaration,
                String signatureKey
        ) {
            this(
                    struct,
                    namespace,
                    name,
                    parameters,
                    declaration,
                    signatureKey,
                    struct ? "struct" : "function"
            );
        }

        Template(
                boolean struct,
                String namespace,
                String name,
                List<AstNode> parameters,
                AstNode declaration,
                String signatureKey,
                String kindLabel
        ) {
            this.struct = struct;
            this.namespace = namespace;
            this.name = name;
            this.parameters = parameters;
            this.declaration = declaration;
            this.signatureKey = signatureKey;
            this.kindLabel = kindLabel;
        }

        String key() {
            return qualifiedKey(namespace, name);
        }
    }

    private static final class MethodTemplate {
        final String ownerKey;
        final AstNode ownerNode;
        final Instance ownerInstance;
        final String namespace;
        final String name;
        final AstNode declaration;
        final Template bindingTemplate;
        final String signatureKey;

        MethodTemplate(
                String ownerKey,
                AstNode ownerNode,
                Instance ownerInstance,
                String namespace,
                AstNode declaration,
                String signatureKey
        ) {
            this.ownerKey = ownerKey;
            this.ownerNode = ownerNode;
            this.ownerInstance = ownerInstance;
            this.namespace = namespace;
            this.name = declaration.name;
            this.declaration = declaration;
            this.signatureKey = signatureKey;
            this.bindingTemplate = new Template(
                    false,
                    ownerKey,
                    declaration.name,
                    declaration.genericParams,
                    declaration,
                    signatureKey,
                    "method"
            );
        }
    }

    private static final class MethodInstance {
        final AstNode methodNode;

        MethodInstance(AstNode methodNode) {
            this.methodNode = methodNode;
        }
    }

    private static final class BodyEnv {
        final String namespace;
        final int depth;
        final Map<String, AstNode> valueBindings;
        final List<Map<String, AstNode>> scopes = new ArrayList<>();

        BodyEnv(
                String namespace,
                int depth,
                Map<String, AstNode> valueBindings
        ) {
            this.namespace = namespace;
            this.depth = depth;
            this.valueBindings = valueBindings == null
                    ? Map.of()
                    : valueBindings;
            scopes.add(new LinkedHashMap<>());
        }

        void pushScope() {
            scopes.add(new LinkedHashMap<>());
        }

        void popScope() {
            scopes.remove(scopes.size() - 1);
        }

        void declare(String name, AstNode type) {
            scopes.get(scopes.size() - 1).put(name, type);
        }

        AstNode resolveLocal(String name) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                AstNode type = scopes.get(i).get(name);

                if (type != null) {
                    return type;
                }
            }

            return null;
        }
    }

    private static final class Instance {
        final Template template;
        final String generatedName;
        final Map<String, AstNode> typeBindings;
        final Map<String, AstNode> valueBindings;
        final List<String> canonicalArguments;
        final String genericHash;
        final String genericGroup;
        final int depth;

        boolean complete;

        Instance(
                Template template,
                String generatedName,
                Map<String, AstNode> typeBindings,
                Map<String, AstNode> valueBindings,
                List<String> canonicalArguments,
                String genericHash,
                String genericGroup,
                int depth
        ) {
            this.template = template;
            this.generatedName = generatedName;
            this.typeBindings = typeBindings;
            this.valueBindings = valueBindings;
            this.canonicalArguments = canonicalArguments;
            this.genericHash = genericHash;
            this.genericGroup = genericGroup;
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
        boolean preserveGenericFunctions = false;

        Substitution(String namespace) {
            this.namespace = namespace;
        }
    }

    private final DiagnosticBag diagnostics;
    private final TargetInfo target;
    private final Map<String, List<Template>> structTemplates = new LinkedHashMap<>();
    private final Map<String, List<Template>> functionTemplates = new LinkedHashMap<>();
    private final Map<String, List<MethodTemplate>> methodTemplates =
            new LinkedHashMap<>();
    private final Set<String> concreteTypeKeys = new HashSet<>();
    private final Set<String> concreteFunctionKeys = new HashSet<>();
    private final Map<String, Instance> instances = new LinkedHashMap<>();
    private final Map<String, Instance> generatedStructInstances =
            new LinkedHashMap<>();
    private final Set<String> generatedInstanceNames = new HashSet<>();
    private final List<Instance> queue = new ArrayList<>();
    private final Map<String, List<AstNode>> generatedByNamespace =
            new LinkedHashMap<>();
    private final Map<String, AstNode> hoistedStringGlobals =
            new LinkedHashMap<>();
    private final Map<String, AstNode> structNodes = new LinkedHashMap<>();
    private final Map<String, Map<String, AstNode>> structFieldTypes =
            new LinkedHashMap<>();
    private final Map<String, Set<String>> concreteMethodNames =
            new LinkedHashMap<>();
    private final Map<String, AstNode> globalTypes = new LinkedHashMap<>();
    private final Map<String, MethodInstance> methodInstances =
            new LinkedHashMap<>();
    private final Map<AstNode, Integer> methodBodyDepths =
            new IdentityHashMap<>();
    private final Map<AstNode, Integer> functionBodyDepths =
            new IdentityHashMap<>();
    private final Map<AstNode, Map<String, AstNode>> bodyValueBindings =
            new IdentityHashMap<>();
    private final Set<AstNode> processedBodies =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final SpecializationDesugarer desugarer;
    private boolean instanceLimitReported = false;
    private int processedInstanceCount = 0;

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
                || containsGenericUse(node.sizeExpr)
                || containsGenericUse(node.defaultValue)) {
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
                                    + "' requires at least one generic parameter."
                                    + " A #generic declaration must name at least"
                                    + " one type or value parameter so each use"
                                    + " has something to substitute."
                                    + " Declare a parameter list such as"
                                    + " <typename T>, or remove #generic.");
                }

                if (item.isGeneric) {
                    if (concreteTypeKeys.contains(key)) {
                        error(item.span,
                                "Generic struct '"
                                        + key
                                        + "' conflicts with an existing concrete struct or enum name."
                                        + " Generic and concrete type names share"
                                        + " one namespace, so the compiler cannot"
                                        + " tell a template use from the concrete"
                                        + " type."
                                        + " Rename the generic struct or the"
                                        + " concrete struct/enum.");
                    }

                    checkGenericParameters(item.genericParams, item.span);
                    String signature = structTemplateSignature(
                            item.genericParams);
                    addTemplate(
                            structTemplates,
                            key,
                            new Template(
                                    true,
                                    namespace,
                                    item.name,
                                    item.genericParams,
                                    item,
                                    signature
                            ),
                            item.span
                    );
                } else {
                    if (structTemplates.containsKey(key)) {
                        error(item.span,
                                "Struct '"
                                        + key
                                        + "' conflicts with a generic struct template of the same name."
                                        + " Concrete and generic struct names share"
                                        + " one namespace, so this declaration"
                                        + " would collide with the template."
                                        + " Rename the concrete struct or the"
                                        + " generic template.");
                    }

                    concreteTypeKeys.add(key);
                }

                continue;
            }

            if (item.kind == AstKind.ENUM_DECL) {
                String key = qualifiedKey(namespace, item.name);

                if (structTemplates.containsKey(key)) {
                    error(item.span,
                            "Enum '"
                                    + key
                                    + "' conflicts with a generic struct template of the same name."
                                    + " Enums and generic structs share the same"
                                    + " type namespace, so the enum would collide"
                                    + " with the template."
                                    + " Rename the enum or the generic struct"
                                    + " template.");
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
                                    + "' requires at least one generic parameter."
                                    + " A #generic function is expanded once per"
                                    + " use, so it needs at least one type or value"
                                    + " parameter to specialize."
                                    + " Add a parameter list such as <typename T>,"
                                    + " or remove #generic.");
                }

                if (item.isGeneric) {
                    if ("main".equals(item.name) && namespace == null) {
                        error(item.span,
                                "The entry-point function 'main' cannot be generic."
                                        + " The runtime starts main as a fixed"
                                        + " concrete function, so there are no"
                                        + " generic arguments to expand it with."
                                        + " Remove #generic from main and call a"
                                        + " generic helper from its body.");
                    }

                    checkGenericParameters(item.genericParams, item.span);
                    String signature = functionTemplateSignature(
                            item.genericParams,
                            item.parameters
                    );
                    addTemplate(
                            functionTemplates,
                            key,
                            new Template(
                                    false,
                                    namespace,
                                    item.name,
                                    item.genericParams,
                                    item,
                                    signature
                            ),
                            item.span
                    );
                } else {
                    concreteFunctionKeys.add(key);
                }
            }
        }
    }

    private void addTemplate(
            Map<String, List<Template>> table,
            String key,
            Template template,
            SourceSpan span
    ) {
        List<Template> group = table.computeIfAbsent(
                key,
                ignored -> new ArrayList<>()
        );

        for (Template existing : group) {
            if (existing.signatureKey.equals(template.signatureKey)) {
                error(span,
                        "Duplicate generic template '"
                                + key
                                + template.signatureKey
                                + "'."
                                + " The signature is built from the generic"
                                + " parameter kinds and, for functions, the"
                                + " ordinary parameter types, so this pair"
                                + " would expand to the same template identity."
                                + " Remove one template or change its parameter"
                                + " kinds or types.");
                return;
            }
        }

        group.add(template);
    }

    private static String structTemplateSignature(List<AstNode> params) {
        List<String> kinds = new ArrayList<>();

        for (AstNode param : params) {
            kinds.add(genericParameterTypeKey(param));
        }

        return "<" + String.join(",", kinds) + ">";
    }

    private static String functionTemplateSignature(
            List<AstNode> genericParams,
            List<AstNode> parameters
    ) {
        Map<String, String> typeMarkers = new LinkedHashMap<>();
        Map<String, String> valueMarkers = new LinkedHashMap<>();

        for (int i = 0; i < genericParams.size(); i++) {
            AstNode param = genericParams.get(i);

            if (param.typeParameter) {
                typeMarkers.put(param.name, "T" + i);
            } else {
                valueMarkers.put(param.name, "V" + i);
            }
        }

        List<String> kinds = new ArrayList<>();

        for (AstNode param : genericParams) {
            kinds.add(genericParameterTypeKey(param));
        }

        List<String> parameterTypes = new ArrayList<>();

        for (AstNode parameter : parameters) {
            parameterTypes.add(templateTypeKey(
                    parameter.type,
                    typeMarkers,
                    valueMarkers
            ));
        }

        return "<"
                + String.join(",", kinds)
                + ">("
                + String.join(",", parameterTypes)
                + ")";
    }

    private static String genericParameterTypeKey(AstNode param) {
        if (param.typeParameter) {
            return "typename";
        }

        return param.type.primitive.name().toLowerCase();
    }

    private static String templateTypeKey(
            AstNode type,
            Map<String, String> typeMarkers,
            Map<String, String> valueMarkers
    ) {
        if (type == null) {
            return "void";
        }

        switch (type.kind) {
            case PRIMITIVE_TYPE:
                return type.primitive.name().toLowerCase();

            case VOID_TYPE:
                return "void";

            case DYNAMIC_ARRAY_TYPE:
                return "["
                        + templateTypeKey(
                                type.type,
                                typeMarkers,
                                valueMarkers)
                        + "]";

            case STATIC_ARRAY_TYPE:
                return "["
                        + templateTypeKey(
                                type.type,
                                typeMarkers,
                                valueMarkers)
                        + ","
                        + templateConstantKey(type.sizeExpr, valueMarkers)
                        + "]";

            case PTR_TYPE:
                return type.permission.name()
                        + "<"
                        + templateTypeKey(
                                type.type,
                                typeMarkers,
                                valueMarkers)
                        + ">";

            case TUPLE_TYPE: {
                List<String> parts = new ArrayList<>();

                for (AstNode element : type.elements) {
                    parts.add(templateTypeKey(
                            element.type,
                            typeMarkers,
                            valueMarkers
                    ));
                }

                return "(" + String.join(",", parts) + ")";
            }

            case STRUCT_TYPE: {
                if (type.names == null || type.names.isEmpty()) {
                    String marker = typeMarkers.get(type.name);

                    if (marker != null) {
                        return marker;
                    }
                }

                String name = displayName(type.names, type.name);

                if (type.genericArgs == null || type.genericArgs.isEmpty()) {
                    return name;
                }

                List<String> args = new ArrayList<>();

                for (AstNode arg : type.genericArgs) {
                    args.add(templateGenericArgKey(
                            arg,
                            typeMarkers,
                            valueMarkers
                    ));
                }

                return name + "<" + String.join(",", args) + ">";
            }

            default:
                return type.kind.jsonName();
        }
    }

    private static String templateGenericArgKey(
            AstNode arg,
            Map<String, String> typeMarkers,
            Map<String, String> valueMarkers
    ) {
        if (arg.typeParameter) {
            return "T:"
                    + templateTypeKey(
                            arg.type,
                            typeMarkers,
                            valueMarkers);
        }

        return "V:" + templateConstantKey(arg.value, valueMarkers);
    }

    private static String templateConstantKey(
            AstNode expr,
            Map<String, String> valueMarkers
    ) {
        if (expr == null) {
            return "?";
        }

        BigInteger folded = ConstExpressionEvaluator.tryFold(expr);

        if (folded != null) {
            return folded.toString(10);
        }

        if (expr.kind == AstKind.VAR) {
            String marker = valueMarkers.get(expr.name);
            return marker == null ? expr.name : marker;
        }

        if (expr.kind == AstKind.UNARY) {
            return "("
                    + expr.text
                    + templateConstantKey(expr.expr, valueMarkers)
                    + ")";
        }

        if (expr.kind == AstKind.BINARY) {
            return "("
                    + templateConstantKey(expr.left, valueMarkers)
                    + expr.text
                    + templateConstantKey(expr.right, valueMarkers)
                    + ")";
        }

        return expr.text == null ? expr.kind.jsonName() : expr.text;
    }

    private void checkGenericParameters(List<AstNode> params, SourceSpan span) {
        Set<String> seen = new HashSet<>();

        for (AstNode param : params) {
            if (!seen.add(param.name)) {
                error(param.span,
                        "Duplicate generic parameter name '"
                                + param.name
                                + "'."
                                + " Each name identifies one binding inside"
                                + " the template, so a repeated name would"
                                + " shadow the earlier parameter."
                                + " Rename one of the parameters.");
            }

            if (!param.typeParameter) {
                if (param.type == null
                        || param.type.kind != AstKind.PRIMITIVE_TYPE) {
                    error(param.span,
                            "Generic value parameter '"
                                    + param.name
                                    + "' must have one of the eight primitive integer types."
                                    + " Value parameters are folded into"
                                    + " constants in generated code, and only"
                                    + " primitive integer types can be used that"
                                    + " way."
                                    + " Declare it as int8, uint8, int16,"
                                    + " uint16, int32, uint32, int64, or uint64,"
                                    + " or use typename for a type parameter.");
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

            if (instance.template.struct) {
                clone.name = instance.generatedName;
                clone.genericHash = instance.genericHash;
                clone.genericBaseName = instance.template.name;
            } else {
                clone.genericGroup = instance.genericGroup;
                clone.genericHash = instance.genericHash;
            }

            AstNode desugared = desugarer.desugarSpecialization(clone);

            if (desugared == null) {
                continue;
            }

            processedInstanceCount++;

            if (instance.template.struct) {
                generatedStructInstances.put(
                        qualifiedKey(
                                instance.template.namespace,
                                instance.generatedName),
                        instance
                );
            } else {
                functionBodyDepths.put(desugared, instance.depth);
                bodyValueBindings.put(
                        desugared,
                        new LinkedHashMap<>(instance.valueBindings)
                );
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

        if (node.kind == AstKind.FUNCTION_DECL
                && node.isGeneric
                && env.preserveGenericFunctions) {
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

                if (selected == null) {
                    return AstNode.block(node.span, List.of());
                }

                return AstNode.compileTimeIf(
                        node.span,
                        AstNode.typedIntLiteral(node.span, "1", PrimitiveKind.UINT8),
                        substituteNode(selected, env),
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
        copy.defaultValue = substituteNode(node.defaultValue, env);

        copy.items = substituteList(node.items, env);
        copy.parameters = substituteList(node.parameters, env);
        copy.fields = substituteList(node.fields, env);

        boolean preserve = env.preserveGenericFunctions;
        env.preserveGenericFunctions = true;
        copy.methods = substituteList(node.methods, env);
        copy.members = substituteList(node.members, env);
        env.preserveGenericFunctions = preserve;

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
                                    + "' cannot take generic arguments."
                                    + " A type parameter stands for one concrete"
                                    + " type argument, so it cannot itself be"
                                    + " parameterized."
                                    + " Pass the parameter without angle"
                                    + " brackets, or use a concrete generic"
                                    + " struct name.");
                }

                return substituteNode(bound, env);
            }
        }

        boolean selfReference = env.selfTemplateName != null
                && node.name.equals(env.selfTemplateName)
                && (node.names == null || node.names.isEmpty());

        if (selfReference && (node.genericArgs == null
                || node.genericArgs.isEmpty())) {
            error(node.span,
                    "Generic struct '"
                            + node.name
                            + "' requires explicit type arguments inside its own body."
                            + " A bare generic struct name does not identify a"
                            + " concrete layout, even inside the template itself."
                            + " Write the current instance explicitly, such as "
                            + node.name
                            + "<T>, or supply the arguments of another"
                            + " instance.");
            return AstNode.structType(
                    node.span,
                    node.names,
                    node.name
            );
        }

        if (node.genericArgs != null && !node.genericArgs.isEmpty()) {
            List<Template> templates = resolveStructTemplates(
                    node.names,
                    node.name,
                    env
            );

            if (templates.isEmpty()) {
                error(node.span,
                        "Unknown generic struct '"
                                + displayName(node.names, node.name)
                                + "'."
                                + " No #generic struct template with that"
                                + " name is visible in this namespace or its"
                                + " imports."
                                + " Check the spelling and imports, or declare"
                                + " the template before using it.");

                return AstNode.structType(
                        node.span,
                        node.names,
                        node.name
                );
            }

            List<Candidate> candidates = collectCandidates(
                    templates,
                    node.genericArgs,
                    env,
                    node.span
            );
            candidates = preferNarrowestValueFit(candidates);

            if (candidates.isEmpty()) {
                reportNoStructCandidate(
                        templates,
                        node,
                        env
                );

                return AstNode.structType(
                        node.span,
                        node.names,
                        node.name
                );
            }

            if (candidates.size() > 1) {
                error(node.span,
                        "Generic struct '"
                                + displayName(node.names, node.name)
                                + "' is ambiguous for the supplied generic arguments."
                                + " More than one visible template matches"
                                + " the supplied argument kinds after value"
                                + " fitting, so the compiler cannot choose a"
                                + " layout."
                                + " Qualify the struct name or supply"
                                + " arguments that match only one template.");
            }

            Candidate chosen = candidates.get(0);

            if (selfReference
                    && chosen.bound.canonicalKeys.equals(
                            env.selfCanonicalArguments)) {
                return AstNode.structType(
                        node.span,
                        List.of(),
                        env.selfGeneratedName
                );
            }

            String genericHash = Mangler.hashHex(
                    genericArgsKey(node.genericArgs, env)
            );
            String generated = instantiate(
                    chosen.template,
                    chosen.bound,
                    env,
                    node.span,
                    genericHash,
                    null
            );

            return AstNode.structType(
                    node.span,
                    splitNamespace(chosen.template.namespace),
                    generated
            );
        }

        if (!resolveStructTemplates(node.names, node.name, env).isEmpty()) {
            error(node.span,
                    "Generic struct '"
                            + displayName(node.names, node.name)
                            + "' requires explicit type arguments."
                            + " Franko never infers generic arguments, so a"
                            + " bare template name does not identify a"
                            + " concrete layout."
                            + " Append arguments such as <int32> to the"
                            + " struct name.");
        }

        AstNode copy = node.copyShallow();
        copy.names = node.names;
        copy.name = node.name;
        return copy;
    }

    private AstNode substituteCall(AstNode node, Substitution env) {
        AstNode callee = substituteNode(node.callee, env);
        List<AstNode> args = substituteList(node.args, env);

        if (callee != null
                && callee.kind == AstKind.MEMBER_ACCESS
                && node.genericArgs != null
                && !node.genericArgs.isEmpty()) {
            AstNode call = AstNode.call(
                    node.span,
                    callee,
                    args,
                    substituteList(node.genericArgs, env),
                    node.argumentNames
            );
            call.genericGroup = node.genericGroup;
            return call;
        }

        if (node.genericArgs != null && !node.genericArgs.isEmpty()) {
            List<Template> templates = resolveFunctionTemplates(callee, env);

            if (templates.isEmpty()) {
                error(node.span,
                        "Unknown generic function '"
                                + calleeDisplayName(callee)
                                + "'."
                                + " No #generic function template with that"
                                + " name is visible in this namespace or its"
                                + " imports."
                                + " Check the spelling and imports, or declare"
                                + " the template before calling it.");

                return AstNode.call(
                        node.span,
                        callee,
                        args,
                        List.of(),
                        node.argumentNames
                );
            }

            List<Candidate> candidates = collectCandidates(
                    templates,
                    node.genericArgs,
                    env,
                    node.span
            );
            candidates = preferNarrowestValueFit(candidates);
            candidates = preferRawPointerPermissions(candidates, args);

            String group = genericGroupKey(
                    callee,
                    node.genericArgs,
                    env
            );
            String genericHash = Mangler.hashHex(
                    genericArgsKey(node.genericArgs, env)
            );

            if (candidates.isEmpty()) {
                reportNoFunctionCandidate(templates, node, env);
            }

            for (Candidate candidate : candidates) {
                instantiate(
                        candidate.template,
                        candidate.bound,
                        env,
                        node.span,
                        genericHash,
                        group
                );
            }

            AstNode call = AstNode.call(
                    node.span,
                    callee,
                    args,
                    List.of(),
                    node.argumentNames
            );
            call.genericGroup = group;
            return call;
        }

        if (callee != null
                && (callee.kind == AstKind.VAR
                        || callee.kind == AstKind.QUALIFIED_NAME)
                && !resolveFunctionTemplates(callee, env).isEmpty()
                && !concreteFunctionExists(callee, env)) {
            error(node.span,
                    "Generic function '"
                            + calleeDisplayName(callee)
                            + "' requires explicit type arguments."
                            + " Franko never infers generic arguments, so a"
                            + " call to a template must spell them out."
                            + " Append arguments such as <int32> to the"
                            + " function name at the call site.");
        }

        return AstNode.call(
                node.span,
                callee,
                args,
                List.of(),
                node.argumentNames
        );
    }

    private void reportNoStructCandidate(
            List<Template> templates,
            AstNode node,
            Substitution env
    ) {
        if (templates.size() == 1) {
            bindArguments(
                    templates.get(0),
                    node.genericArgs,
                    env,
                    node.span
            );
            return;
        }

        error(node.span,
                "No generic struct template '"
                        + displayName(node.names, node.name)
                        + "' matches generic arguments <"
                        + genericArgsDisplay(node.genericArgs, env)
                        + ">."
                        + " The number or kinds of the supplied arguments"
                        + " do not match any visible template signature."
                        + " Check the argument count and kinds, or adjust"
                        + " the template's parameter list.");
    }

    private void reportNoFunctionCandidate(
            List<Template> templates,
            AstNode node,
            Substitution env
    ) {
        if (templates.size() == 1) {
            bindArguments(
                    templates.get(0),
                    node.genericArgs,
                    env,
                    node.span
            );
            return;
        }

        error(node.span,
                "No generic function template '"
                        + calleeDisplayName(node.callee)
                        + "' matches generic arguments <"
                        + genericArgsDisplay(node.genericArgs, env)
                        + ">."
                        + " The number or kinds of the supplied arguments"
                        + " do not match any visible template signature."
                        + " Check the argument count and kinds, or adjust"
                        + " the template's parameter list.");
    }

    private static final class BoundArguments {
        final Map<String, AstNode> typeBindings = new LinkedHashMap<>();
        final Map<String, AstNode> valueBindings = new LinkedHashMap<>();
        final List<String> canonicalKeys = new ArrayList<>();
        int valueFitCost;
    }

    private static final class Candidate {
        final Template template;
        final BoundArguments bound;

        Candidate(Template template, BoundArguments bound) {
            this.template = template;
            this.bound = bound;
        }
    }

    private List<Candidate> collectCandidates(
            List<Template> templates,
            List<AstNode> args,
            Substitution env,
            SourceSpan span
    ) {
        List<Candidate> candidates = new ArrayList<>();

        for (Template template : templates) {
            BoundArguments bound = bindArguments(
                    template,
                    args,
                    env,
                    span,
                    false
            );

            if (bound != null) {
                candidates.add(new Candidate(template, bound));
            }
        }

        return candidates;
    }

    private List<Candidate> preferNarrowestValueFit(
            List<Candidate> candidates
    ) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        int best = Integer.MAX_VALUE;

        for (Candidate candidate : candidates) {
            best = Math.min(best, candidate.bound.valueFitCost);
        }

        List<Candidate> preferred = new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (candidate.bound.valueFitCost == best) {
                preferred.add(candidate);
            }
        }

        return preferred;
    }

    private List<Candidate> preferRawPointerPermissions(
            List<Candidate> candidates,
            List<AstNode> args
    ) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        int best = Integer.MAX_VALUE;

        for (Candidate candidate : candidates) {
            best = Math.min(
                    best,
                    rawPointerPermissionCost(candidate.template, args)
            );
        }

        List<Candidate> preferred = new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (rawPointerPermissionCost(candidate.template, args) == best) {
                preferred.add(candidate);
            }
        }

        return preferred;
    }

    private static int rawPointerPermissionCost(
            Template template,
            List<AstNode> args
    ) {
        List<AstNode> parameters = template.declaration.parameters;
        int count = Math.min(parameters.size(), args.size());
        int cost = 0;

        for (int i = 0; i < count; i++) {
            AstNode argument = args.get(i);
            AstNode parameterType = parameters.get(i).type;

            if (argument.kind != AstKind.GET_PTR
                    || parameterType == null
                    || parameterType.kind != AstKind.PTR_TYPE) {
                continue;
            }

            PtrPermission argumentPermission = argument.permission;
            PtrPermission parameterPermission = parameterType.permission;

            if (parameterPermission.ordinal() < argumentPermission.ordinal()) {
                return Integer.MAX_VALUE;
            }

            cost += parameterPermission.ordinal() - argumentPermission.ordinal();
        }

        return cost;
    }

    private BoundArguments bindArguments(
            Template template,
            List<AstNode> args,
            Substitution env,
            SourceSpan span
    ) {
        return bindArguments(template, args, env, span, true);
    }

    private BoundArguments bindArguments(
            Template template,
            List<AstNode> args,
            Substitution env,
            SourceSpan span,
            boolean report
    ) {
        if (args.size() != template.parameters.size()) {
            if (report) {
                error(span,
                        "Generic "
                                + template.kindLabel
                                + " '"
                                + template.key()
                                + "' expects "
                                + template.parameters.size()
                                + " type/value argument"
                                + (template.parameters.size() == 1 ? "" : "s")
                                + ", got "
                                + args.size()
                                + "."
                                + " Every generic parameter must be bound by"
                                + " exactly one argument at a use site."
                                + " Supply the expected number of arguments"
                                + " in the angle brackets.");
            }

            return null;
        }

        BoundArguments bound = new BoundArguments();

        for (int i = 0; i < template.parameters.size(); i++) {
            AstNode parameter = template.parameters.get(i);
            AstNode argument = args.get(i);

            if (parameter.typeParameter) {
                if (!argument.typeParameter) {
                    if (report) {
                        error(argument.span,
                                "Type argument expected for generic parameter '"
                                        + parameter.name
                                        + "', got a value argument."
                                        + " A typename parameter binds a type,"
                                        + " while an integer parameter binds a"
                                        + " compile-time value, and the two kinds"
                                        + " cannot be mixed."
                                        + " Pass a type such as int32 or"
                                        + " Box<uint8> in this position.");
                    }

                    return null;
                }

                AstNode typeArgument = substituteNode(argument.type, env);

                if (typeArgument == null) {
                    if (report) {
                        error(argument.span,
                                "Missing generic type argument."
                                        + " A typename parameter needs a"
                                        + " concrete type before the template"
                                        + " can be instantiated."
                                        + " Add the missing type in the angle"
                                        + " brackets.");
                    }

                    return null;
                }

                if (report
                        && !validateTypeArgument(
                                typeArgument,
                                env,
                                argument.span)) {
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
                    if (report) {
                        error(argument.span,
                                "Value argument expected for generic parameter '"
                                        + parameter.name
                                        + "', got a type argument."
                                        + " An integer value parameter must be"
                                        + " bound by a compile-time constant, not"
                                        + " a type."
                                        + " Pass an integer constant expression,"
                                        + " or change the parameter to typename.");
                    }

                    return null;
                }
            } else {
                valueExpression = argument.value;
            }

            AstNode substituted = substituteNode(valueExpression, env);
            BigInteger value = ConstExpressionEvaluator.tryFold(substituted);

            if (value == null) {
                if (report) {
                    error(argument.span,
                            "Generic value argument for parameter '"
                                    + parameter.name
                                    + "' is not a compile-time constant."
                                    + " Value parameters become literal"
                                    + " constants in the generated instance, so"
                                    + " they must be foldable before type"
                                    + " checking."
                                    + " Pass an integer literal or constant"
                                    + " expression, or use a runtime parameter"
                                    + " instead.");
                }

                return null;
            }

            PrimitiveKind kind = parameter.type.primitive;
            SemanticPrimitiveKind semanticKind =
                    SemanticPrimitiveKind.valueOf(kind.name());

            if (!TypeChecker.fitsBigIntegerToPrimitive(value, semanticKind)) {
                if (report) {
                    error(argument.span,
                            "Generic value argument "
                                    + value
                                    + " does not fit in "
                                    + kind.name().toLowerCase()
                                    + " for parameter '"
                                    + parameter.name
                                    + "'."
                                    + " Value arguments use the same"
                                    + " constant-fit rules as ordinary integer"
                                    + " literals, and an out-of-range value"
                                    + " cannot be stored without truncation."
                                    + " Use a value within the range of "
                                    + kind.name().toLowerCase()
                                    + " or widen the parameter's integer type.");
                }

                return null;
            }

            AstNode typed = AstNode.typedIntLiteral(
                    argument.span,
                    value.toString(10),
                    kind
            );

            bound.valueBindings.put(parameter.name, typed);
            bound.canonicalKeys.add(kind.name() + ":" + value.toString(10));
            bound.valueFitCost += TypeChecker.primitiveFitCost(
                    value,
                    semanticKind
            );
        }

        return bound;
    }

    private boolean validateTypeArgument(
            AstNode type,
            Substitution env,
            SourceSpan span
    ) {
        if (type == null) {
            error(span,
                    "Missing generic type argument."
                            + " A typename parameter needs a concrete type"
                            + " before the template can be instantiated."
                            + " Add the missing type in the angle brackets.");
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
                                        + "' is not a declared type or type parameter."
                                        + " Every type argument must resolve to"
                                        + " a concrete type or an enclosing type"
                                        + " parameter before substitution."
                                        + " Check the spelling and imports, or"
                                        + " declare the type before using it.");
                        return false;
                    }
                }
            }

            if (type.genericArgs != null && !type.genericArgs.isEmpty()) {
                List<Template> templates = resolveStructTemplates(
                        type.names,
                        type.name,
                        env
                );

                if (templates.isEmpty()) {
                    error(span,
                            "Unknown generic struct '"
                                    + displayName(type.names, type.name)
                                    + "'."
                                    + " No #generic struct template with that"
                                    + " name is visible in this namespace or its"
                                    + " imports."
                                    + " Check the spelling and imports, or"
                                    + " declare the template before using it.");
                    return false;
                }

                if (collectCandidates(
                        templates,
                        type.genericArgs,
                        env,
                        span
                ).isEmpty()) {
                    error(span,
                            "No generic struct template '"
                                    + displayName(type.names, type.name)
                                    + "' matches generic arguments <"
                                    + genericArgsDisplay(
                                            type.genericArgs,
                                            env)
                                    + ">."
                                    + " The number or kinds of the supplied"
                                    + " arguments do not match any visible"
                                    + " template signature."
                                    + " Check the argument count and kinds, or"
                                    + " adjust the template's parameter list.");
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

        return !resolveStructTemplates(null, name, env).isEmpty();
    }

    private String instantiate(
            Template template,
            BoundArguments bound,
            Substitution env,
            SourceSpan span,
            String genericHash,
            String genericGroup
    ) {
        String key = template.key()
                + template.signatureKey
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
                                + " generic instances in this compilation."
                                + " Each distinct argument set creates a"
                                + " separate concrete instance, so runaway"
                                + " recursion can exhaust memory and compile"
                                + " time."
                                + " Reduce template recursion or argument"
                                + " variation, or split the program into"
                                + " smaller compilations.");
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
                            + "'."
                            + " A template that keeps introducing new"
                            + " arguments without a base case eventually"
                            + " exceeds the "
                            + MAX_DEPTH
                            + "-level nesting limit."
                            + " Add or fix a base case so recursion reaches"
                            + " arguments that are already instantiated.");
            return template.name;
        }

        String generatedName = template.struct
                ? "GEN_" + genericHash + "_" + template.name
                : template.name;

        Instance instance = new Instance(
                template,
                generatedName,
                new LinkedHashMap<>(bound.typeBindings),
                new LinkedHashMap<>(bound.valueBindings),
                List.copyOf(bound.canonicalKeys),
                genericHash,
                genericGroup,
                depth
        );

        instances.put(key, instance);

        if (template.struct) {
            generatedInstanceNames.add(generatedName);
            generatedStructInstances.put(
                    qualifiedKey(template.namespace, generatedName),
                    instance
            );
        }

        queue.add(instance);

        return generatedName;
    }

    private List<Template> resolveStructTemplates(
            List<String> namespaceSegments,
            String name,
            Substitution env
    ) {
        return resolveTemplates(
                structTemplates,
                namespaceSegments,
                name,
                env
        );
    }

    private List<Template> resolveFunctionTemplates(
            AstNode callee,
            Substitution env
    ) {
        if (callee == null) {
            return List.of();
        }

        if (callee.kind == AstKind.VAR) {
            return resolveTemplates(
                    functionTemplates,
                    null,
                    callee.name,
                    env
            );
        }

        if (callee.kind == AstKind.QUALIFIED_NAME) {
            if (callee.names.isEmpty()) {
                return List.of();
            }

            List<String> namespaceSegments = new ArrayList<>(
                    callee.names.subList(0, callee.names.size() - 1)
            );
            String name = callee.names.get(callee.names.size() - 1);

            return resolveTemplates(
                    functionTemplates,
                    namespaceSegments,
                    name,
                    env
            );
        }

        return List.of();
    }

    private static List<Template> resolveTemplates(
            Map<String, List<Template>> table,
            List<String> namespaceSegments,
            String name,
            Substitution env
    ) {
        if (namespaceSegments != null && !namespaceSegments.isEmpty()) {
            return table.getOrDefault(
                    String.join("::", namespaceSegments) + "::" + name,
                    List.of()
            );
        }

        if (env.namespace != null) {
            List<Template> current = table.get(
                    env.namespace + "::" + name
            );

            if (current != null && !current.isEmpty()) {
                return current;
            }
        }

        return table.getOrDefault(name, List.of());
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

    private String genericGroupKey(
            AstNode callee,
            List<AstNode> args,
            Substitution env
    ) {
        return calleeSimpleName(callee)
                + "<"
                + genericArgsKey(args, env)
                + ">";
    }

    private static String calleeSimpleName(AstNode callee) {
        if (callee == null) {
            return "<unknown>";
        }

        if (callee.kind == AstKind.VAR) {
            return callee.name;
        }

        if (callee.kind == AstKind.QUALIFIED_NAME
                && !callee.names.isEmpty()) {
            return callee.names.get(callee.names.size() - 1);
        }

        return callee.kind.jsonName();
    }

    private String genericArgsDisplay(
            List<AstNode> args,
            Substitution env
    ) {
        return genericArgsKey(args, env);
    }

    private String genericArgsKey(
            List<AstNode> args,
            Substitution env
    ) {
        List<String> parts = new ArrayList<>();

        for (AstNode arg : args) {
            if (arg.typeParameter) {
                if (arg.type != null
                        && arg.type.kind == AstKind.STRUCT_TYPE
                        && (arg.type.names == null
                                || arg.type.names.isEmpty())
                        && (arg.type.genericArgs == null
                                || arg.type.genericArgs.isEmpty())) {
                    AstNode value = env.valueBindings.get(arg.type.name);

                    if (value != null) {
                        BigInteger folded =
                                ConstExpressionEvaluator.tryFold(value);
                        parts.add(folded == null
                                ? value.text
                                : folded.toString(10));
                        continue;
                    }
                }

                AstNode type = substituteNode(arg.type, env);
                parts.add(canonicalType(type, env));
                continue;
            }

            AstNode value = substituteNode(arg.value, env);
            BigInteger folded = ConstExpressionEvaluator.tryFold(value);
            parts.add(folded == null ? "?" : folded.toString(10));
        }

        return String.join(",", parts);
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

    AstNode expandMethods(AstNode program) {
        if (program == null || program.kind != AstKind.PROGRAM) {
            return program;
        }

        for (int guard = 0; guard < 100_000; guard++) {
            boolean changed = collectVisible(program.items, null);
            changed |= processVisibleBodies(program.items, null);

            int queueBefore = processedInstanceCount;
            int hoistedBefore = hoistedStringGlobals.size();
            processQueue();

            boolean queueChanged = processedInstanceCount != queueBefore;
            boolean generated = !generatedByNamespace.isEmpty();
            boolean hoistedChanged = hoistedStringGlobals.size() != hoistedBefore;

            if (generated || hoistedChanged) {
                program = rebuildProgram(program);
                changed = true;
            }

            if (!changed && !queueChanged) {
                break;
            }
        }

        return rebuildProgram(program);
    }

    private AstNode rebuildProgram(AstNode program) {
        List<AstNode> items = insertGenerated(program.items, null);
        items = appendMissingGeneratedNamespaces(items);
        items = addHoistedStringGlobals(items);
        return AstNode.program(program.span, items);
    }

    private boolean collectVisible(List<AstNode> items, String namespace) {
        boolean changed = false;

        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                changed |= collectVisible(
                        item.items,
                        join(namespace, item.name)
                );
                continue;
            }

            if (item.kind == AstKind.GLOBAL_VAR_DECL) {
                globalTypes.put(
                        qualifiedKey(namespace, item.name),
                        item.type
                );
                continue;
            }

            if (item.kind != AstKind.STRUCT_DECL || item.isGeneric) {
                continue;
            }

            String ownerKey = qualifiedKey(namespace, item.name);
            AstNode previous = structNodes.put(ownerKey, item);

            if (previous != item) {
                changed = true;
            }

            Instance ownerInstance = generatedStructInstances.get(ownerKey);
            Map<String, AstNode> fields = new LinkedHashMap<>();

            for (AstNode field : item.fields) {
                fields.put(field.name, field.type);
            }

            Set<String> concreteNames = concreteMethodNames.computeIfAbsent(
                    ownerKey,
                    ignored -> new LinkedHashSet<>()
            );

            List<AstNode> methods = new ArrayList<>();
            List<AstNode> members = new ArrayList<>();

            for (AstNode member : item.members) {
                if (member.kind == AstKind.FUNCTION_DECL
                        && member.isGeneric) {
                    if (registerMethodTemplate(
                            ownerKey,
                            item,
                            ownerInstance,
                            namespace,
                            member
                    )) {
                        changed = true;
                    }

                    continue;
                }

                members.add(member);

                if (member.kind == AstKind.FUNCTION_DECL) {
                    methods.add(member);
                    concreteNames.add(member.name);
                } else if (member.kind == AstKind.STRUCT_FIELD_DECL) {
                    fields.putIfAbsent(member.name, member.type);
                }
            }

            if (!sameNodes(item.methods, methods)) {
                item.methods = List.copyOf(methods);
                changed = true;
            }

            if (!sameNodes(item.members, members)) {
                item.members = List.copyOf(members);
                changed = true;
            }

            structFieldTypes.put(ownerKey, fields);
        }

        return changed;
    }

    private static boolean sameNodes(List<AstNode> a, List<AstNode> b) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }

        return true;
    }

    private boolean registerMethodTemplate(
            String ownerKey,
            AstNode ownerNode,
            Instance ownerInstance,
            String namespace,
            AstNode method
    ) {
        if (method.genericModifier && method.genericParams.isEmpty()) {
            error(method.span,
                    "Generic method '"
                            + ownerKey
                            + "."
                            + method.name
                            + "' requires at least one generic parameter."
                            + " A #generic method is expanded once per call,"
                            + " so it needs at least one type or value"
                            + " parameter to specialize."
                            + " Add a parameter list such as <typename T>,"
                            + " or remove #generic.");
            return true;
        }

        checkGenericParameters(method.genericParams, method.span);

        if (ownerInstance != null) {
            Set<String> ownerNames = new HashSet<>();

            for (AstNode ownerParam : ownerInstance.template.parameters) {
                ownerNames.add(ownerParam.name);
            }

            for (AstNode methodParam : method.genericParams) {
                if (ownerNames.contains(methodParam.name)) {
                    error(methodParam.span,
                            "Generic method parameter '"
                                    + methodParam.name
                                    + "' shadows a generic parameter of struct '"
                                    + ownerInstance.template.name
                                    + "'."
                                    + " The enclosing struct's parameters are"
                                    + " already bound by the receiver, so a"
                                    + " method parameter with the same name"
                                    + " would make the signature ambiguous."
                                    + " Rename the method parameter.");
                }
            }
        }

        if (OperatorMethods.isOperatorMethodName(method.name)) {
            error(method.span,
                    "Operator method '"
                            + method.name
                            + "' cannot be generic."
                            + " Operator methods have fixed signatures and"
                            + " are invoked through operator syntax, which"
                            + " cannot carry generic arguments."
                            + " Remove #generic or rename the method.");
        }

        String signature = methodTemplateSignature(
                ownerKey,
                method,
                ownerInstance
        );
        List<MethodTemplate> group = methodTemplates.computeIfAbsent(
                ownerKey + "." + method.name,
                ignored -> new ArrayList<>()
        );

        for (MethodTemplate existing : group) {
            if (existing.signatureKey.equals(signature)) {
                error(method.span,
                        "Duplicate generic method template '"
                                + ownerKey
                                + "."
                                + method.name
                                + signature
                                + "'."
                                + " The signature is built from the method's"
                                + " generic parameter kinds and its user"
                                + " parameter types with self excluded."
                                + " Remove one template or change its"
                                + " parameter kinds or types.");
                return true;
            }
        }

        group.add(new MethodTemplate(
                ownerKey,
                ownerNode,
                ownerInstance,
                namespace,
                method,
                signature
        ));

        return true;
    }

    private String methodTemplateSignature(
            String ownerKey,
            AstNode method,
            Instance ownerInstance
    ) {
        Map<String, String> typeMarkers = new LinkedHashMap<>();
        Map<String, String> valueMarkers = new LinkedHashMap<>();
        List<String> kinds = new ArrayList<>();

        for (int i = 0; i < method.genericParams.size(); i++) {
            AstNode param = method.genericParams.get(i);

            if (param.typeParameter) {
                typeMarkers.put(param.name, "T" + i);
            } else {
                valueMarkers.put(param.name, "V" + i);
            }

            kinds.add(genericParameterTypeKey(param));
        }

        List<String> parameterTypes = new ArrayList<>();

        for (int i = 1; i < method.parameters.size(); i++) {
            parameterTypes.add(methodTemplateTypeKey(
                    method.parameters.get(i).type,
                    ownerInstance,
                    typeMarkers,
                    valueMarkers
            ));
        }

        return "<"
                + String.join(",", kinds)
                + ">("
                + String.join(",", parameterTypes)
                + ")";
    }

    private String methodTemplateTypeKey(
            AstNode type,
            Instance ownerInstance,
            Map<String, String> typeMarkers,
            Map<String, String> valueMarkers
    ) {
        if (type == null) {
            return "void";
        }

        switch (type.kind) {
            case PRIMITIVE_TYPE:
                return type.primitive.name().toLowerCase();

            case VOID_TYPE:
                return "void";

            case DYNAMIC_ARRAY_TYPE:
                return "["
                        + methodTemplateTypeKey(
                                type.type,
                                ownerInstance,
                                typeMarkers,
                                valueMarkers)
                        + "]";

            case STATIC_ARRAY_TYPE:
                return "["
                        + methodTemplateTypeKey(
                                type.type,
                                ownerInstance,
                                typeMarkers,
                                valueMarkers)
                        + ","
                        + methodTemplateConstantKey(
                                type.sizeExpr,
                                ownerInstance,
                                valueMarkers)
                        + "]";

            case PTR_TYPE:
                return type.permission.name()
                        + "<"
                        + methodTemplateTypeKey(
                                type.type,
                                ownerInstance,
                                typeMarkers,
                                valueMarkers)
                        + ">";

            case TUPLE_TYPE: {
                List<String> parts = new ArrayList<>();

                for (AstNode element : type.elements) {
                    parts.add(methodTemplateTypeKey(
                            element.type,
                            ownerInstance,
                            typeMarkers,
                            valueMarkers
                    ));
                }

                return "(" + String.join(",", parts) + ")";
            }

            case STRUCT_TYPE:
                return methodTemplateStructKey(
                        type,
                        ownerInstance,
                        typeMarkers,
                        valueMarkers
                );

            default:
                return type.kind.jsonName();
        }
    }

    private String methodTemplateStructKey(
            AstNode type,
            Instance ownerInstance,
            Map<String, String> typeMarkers,
            Map<String, String> valueMarkers
    ) {
        if (type.names == null || type.names.isEmpty()) {
            String marker = typeMarkers.get(type.name);

            if (marker != null) {
                return marker;
            }

            if (ownerInstance != null) {
                AstNode bound = ownerInstance.typeBindings.get(type.name);

                if (bound != null) {
                    return canonicalType(
                            bound,
                            ownerSubstitution(ownerInstance, null)
                    );
                }

                if (isOwnerStructSpelling(type, ownerInstance)
                        && (type.genericArgs == null
                                || type.genericArgs.isEmpty())) {
                    return "SELF";
                }
            }
        }

        if (ownerInstance != null
                && isOwnerStructSpelling(type, ownerInstance)
                && type.genericArgs != null
                && !type.genericArgs.isEmpty()
                && ownerArgumentsMatch(type.genericArgs, ownerInstance)) {
            return "SELF";
        }

        String name = displayName(type.names, type.name);

        if (type.genericArgs == null || type.genericArgs.isEmpty()) {
            return name;
        }

        List<String> args = new ArrayList<>();

        for (AstNode arg : type.genericArgs) {
            if (arg.typeParameter) {
                args.add(methodTemplateTypeKey(
                        arg.type,
                        ownerInstance,
                        typeMarkers,
                        valueMarkers
                ));
            } else {
                args.add("V:" + methodTemplateConstantKey(
                        arg.value,
                        ownerInstance,
                        valueMarkers
                ));
            }
        }

        return name + "<" + String.join(",", args) + ">";
    }

    private static boolean isOwnerStructSpelling(
            AstNode type,
            Instance ownerInstance
    ) {
        if (!type.name.equals(ownerInstance.template.name)) {
            return false;
        }

        if (type.names == null || type.names.isEmpty()) {
            return true;
        }

        String namespace = ownerInstance.template.namespace;

        return namespace != null
                && String.join("::", type.names).equals(namespace);
    }

    private boolean ownerArgumentsMatch(
            List<AstNode> args,
            Instance ownerInstance
    ) {
        if (args.size() != ownerInstance.template.parameters.size()) {
            return false;
        }

        Substitution env = ownerSubstitution(ownerInstance, null);

        for (int i = 0; i < args.size(); i++) {
            AstNode parameter = ownerInstance.template.parameters.get(i);
            AstNode argument = args.get(i);

            if (parameter.typeParameter) {
                if (!argument.typeParameter
                        || argument.type == null) {
                    return false;
                }

                String key = canonicalType(argument.type, env);

                if (!key.equals(ownerInstance.canonicalArguments.get(i))) {
                    return false;
                }
            } else {
                if (argument.typeParameter || argument.value == null) {
                    return false;
                }

                AstNode substituted = substituteConstant(
                        argument.value,
                        env
                );
                BigInteger folded =
                        ConstExpressionEvaluator.tryFold(substituted);

                if (folded == null
                        || parameter.type == null
                        || parameter.type.kind != AstKind.PRIMITIVE_TYPE) {
                    return false;
                }

                String key = parameter.type.primitive.name()
                        + ":"
                        + folded.toString(10);

                if (!key.equals(ownerInstance.canonicalArguments.get(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    private String methodTemplateConstantKey(
            AstNode expr,
            Instance ownerInstance,
            Map<String, String> valueMarkers
    ) {
        if (expr == null) {
            return "?";
        }

        AstNode substituted = ownerInstance == null
                ? expr
                : substituteConstant(
                        expr,
                        ownerSubstitution(ownerInstance, null)
                );

        return templateConstantKey(substituted, valueMarkers);
    }

    private Substitution ownerSubstitution(
            Instance ownerInstance,
            String namespace
    ) {
        Substitution env = new Substitution(
                ownerInstance == null
                        ? namespace
                        : ownerInstance.template.namespace
        );

        if (ownerInstance != null) {
            env.typeBindings.putAll(ownerInstance.typeBindings);
            env.valueBindings.putAll(ownerInstance.valueBindings);
            env.selfTemplateName = ownerInstance.template.name;
            env.selfGeneratedName = ownerInstance.generatedName;
            env.selfCanonicalArguments =
                    ownerInstance.canonicalArguments;
            env.currentDepth = ownerInstance.depth;
        }

        return env;
    }

    private boolean processVisibleBodies(
            List<AstNode> items,
            String namespace
    ) {
        boolean changed = false;

        for (AstNode item : items) {
            if (item.kind == AstKind.NAMESPACE) {
                changed |= processVisibleBodies(
                        item.items,
                        join(namespace, item.name)
                );
                continue;
            }

            if (item.kind == AstKind.FUNCTION_DECL && !item.isGeneric) {
                if (processedBodies.add(item)) {
                    walkBody(
                            item,
                            namespace,
                            functionBodyDepths.getOrDefault(item, 0),
                            bodyValueBindings.get(item)
                    );
                    changed = true;
                }

                continue;
            }

            if (item.kind != AstKind.STRUCT_DECL || item.isGeneric) {
                continue;
            }

            String ownerKey = qualifiedKey(namespace, item.name);
            Instance ownerInstance = generatedStructInstances.get(ownerKey);
            int ownerDepth = ownerInstance == null
                    ? 0
                    : ownerInstance.depth;

            for (AstNode method : item.methods) {
                if (method.isGeneric || !processedBodies.add(method)) {
                    continue;
                }

                Map<String, AstNode> bindings =
                        bodyValueBindings.get(method);

                if (bindings == null && ownerInstance != null) {
                    bindings = ownerInstance.valueBindings;
                }

                walkBody(
                        method,
                        namespace,
                        methodBodyDepths.getOrDefault(method, ownerDepth),
                        bindings
                );
                changed = true;
            }
        }

        return changed;
    }

    private void walkBody(
            AstNode function,
            String namespace,
            int depth,
            Map<String, AstNode> valueBindings
    ) {
        BodyEnv env = new BodyEnv(namespace, depth, valueBindings);

        if (function.parameters != null) {
            for (AstNode parameter : function.parameters) {
                env.declare(parameter.name, parameter.type);
            }
        }

        if (function.body == null || function.body.items == null) {
            return;
        }

        for (AstNode stmt : function.body.items) {
            walkStmt(stmt, env);
        }
    }

    private void walkStmt(AstNode stmt, BodyEnv env) {
        if (stmt == null) {
            return;
        }

        switch (stmt.kind) {
            case BLOCK:
                env.pushScope();

                if (stmt.items != null) {
                    for (AstNode child : stmt.items) {
                        walkStmt(child, env);
                    }
                }

                env.popScope();
                return;

            case VAR_DECL:
                env.declare(stmt.name, stmt.type);
                return;

            case VAR_DECL_INIT:
                walkExpr(stmt.init, env);
                env.declare(stmt.name, stmt.type);
                return;

            case VAR_DECL_FIELDS_INIT:
                if (stmt.fields != null) {
                    for (AstNode field : stmt.fields) {
                        walkExpr(field.value, env);
                    }
                }

                env.declare(stmt.name, stmt.type);
                return;

            case ASSIGN:
                walkExpr(stmt.target, env);
                walkExpr(stmt.value, env);
                return;

            case INC_DEC:
                walkExpr(stmt.target, env);
                return;

            case IF:
                walkExpr(stmt.condition, env);
                walkBranch(stmt.thenBranch, env);
                walkBranch(stmt.elseBranch, env);
                return;

            case COMPILE_TIME_IF:
                walkCompileTimeIf(stmt, env);
                return;

            case WHILE:
                walkExpr(stmt.condition, env);
                walkBranch(stmt.body, env);

                if (stmt.updateItems != null) {
                    for (AstNode update : stmt.updateItems) {
                        walkStmt(update, env);
                    }
                }

                return;

            case RETURN:
                walkExpr(stmt.value, env);
                return;

            case EXPR_STMT:
                walkExpr(stmt.expr, env);
                return;

            case COMPILE_TIME_CALL:
            case COMPILE_TIME_NAME:
                walkExpr(stmt, env);
                return;

            case BREAK:
            case CONTINUE:
                return;

            default:
                walkExpr(stmt.expr, env);
        }
    }

    private void walkBranch(AstNode branch, BodyEnv env) {
        if (branch == null) {
            return;
        }

        env.pushScope();
        walkStmt(branch, env);
        env.popScope();
    }

    private void walkCompileTimeIf(AstNode node, BodyEnv env) {
        BigInteger folded = target == null
                ? null
                : CompileTimeIntrinsicCore.tryEvaluateInt(
                        node.condition,
                        target
                );

        if (folded != null) {
            AstNode selected = folded.signum() != 0
                    ? node.thenBranch
                    : node.elseBranch;
            walkCompileTimeBranch(selected, env);
            return;
        }

        env.pushScope();
        walkCompileTimeBranch(node.thenBranch, env);
        walkCompileTimeBranch(node.elseBranch, env);
        env.popScope();
    }

    private void walkCompileTimeBranch(AstNode branch, BodyEnv env) {
        if (branch == null) {
            return;
        }

        if (branch.kind == AstKind.BLOCK) {
            for (AstNode child : branch.items) {
                walkStmt(child, env);
            }

            return;
        }

        walkStmt(branch, env);
    }

    private AstNode walkExpr(AstNode expr, BodyEnv env) {
        if (expr == null) {
            return null;
        }

        if (expr.kind == AstKind.CALL) {
            return walkCall(expr, env);
        }

        if (expr.kind == AstKind.AGGREGATE_LITERAL) {
            if (expr.fields != null) {
                for (AstNode field : expr.fields) {
                    field.value = walkExpr(field.value, env);
                }
            }

            return expr;
        }

        if (expr.kind == AstKind.FIELD_INIT) {
            expr.value = walkExpr(expr.value, env);
            return expr;
        }

        if (expr.kind == AstKind.ARRAY_LITERAL) {
            List<AstNode> elements = new ArrayList<>();

            if (expr.elements != null) {
                for (AstNode element : expr.elements) {
                    elements.add(walkExpr(element, env));
                }
            }

            expr.elements = List.copyOf(elements);
            return expr;
        }

        expr.target = walkExpr(expr.target, env);
        expr.value = walkExpr(expr.value, env);
        expr.expr = walkExpr(expr.expr, env);
        expr.left = walkExpr(expr.left, env);
        expr.right = walkExpr(expr.right, env);
        expr.index = walkExpr(expr.index, env);
        expr.callee = walkExpr(expr.callee, env);
        expr.sizeExpr = walkExpr(expr.sizeExpr, env);

        List<AstNode> args = new ArrayList<>();

        if (expr.args != null) {
            for (AstNode arg : expr.args) {
                args.add(walkExpr(arg, env));
            }
        }

        expr.args = List.copyOf(args);

        return expr;
    }

    private AstNode walkCall(AstNode call, BodyEnv env) {
        if (call.callee != null
                && call.callee.kind == AstKind.MEMBER_ACCESS) {
            call.callee.target = walkExpr(call.callee.target, env);

            List<AstNode> args = new ArrayList<>();

            for (AstNode arg : call.args) {
                args.add(walkExpr(arg, env));
            }

            call.args = List.copyOf(args);
            return expandMemberCall(call, env);
        }

        call.callee = walkExpr(call.callee, env);

        List<AstNode> args = new ArrayList<>();

        for (AstNode arg : call.args) {
            args.add(walkExpr(arg, env));
        }

        call.args = List.copyOf(args);
        return call;
    }

    private AstNode expandMemberCall(AstNode call, BodyEnv env) {
        AstNode callee = call.callee;
        String methodName = callee.name;
        AstNode receiverType = resolveReceiverType(callee.target, env);

        if (call.genericArgs == null || call.genericArgs.isEmpty()) {
            if (receiverType != null
                    && receiverType.kind == AstKind.STRUCT_TYPE) {
                String ownerKey = resolveStructKey(
                        receiverType,
                        env.namespace
                );
                Set<String> concrete = concreteMethodNames.get(ownerKey);
                boolean hasConcrete = concrete != null
                        && concrete.contains(methodName);

                if (!hasConcrete
                        && methodTemplates.containsKey(
                                ownerKey + "." + methodName)) {
                    error(call.span,
                            "Generic method '"
                                    + ownerKey
                                    + "."
                                    + methodName
                                    + "' requires explicit type arguments."
                                    + " Franko never infers generic method"
                                    + " arguments, so a call to a method"
                                    + " template must spell them out."
                                    + " Append arguments such as <int32> to"
                                    + " the method name at the call site.");
                }
            }

            return call;
        }

        if (receiverType == null) {
            error(call.span,
                    "Generic method call '"
                            + methodName
                            + "' requires a struct receiver whose type"
                            + " can be resolved from declarations."
                            + " Generic arguments are expanded before type"
                            + " checking, so the receiver must be a local,"
                            + " parameter, global, field, array element, or"
                            + " $(...) expression with a declared type."
                            + " Store the receiver in a variable with a"
                            + " declared struct type.");
            call.genericArgs = List.of();
            return call;
        }

        if (receiverType.kind == AstKind.PTR_TYPE) {
            return call;
        }

        if (receiverType.kind != AstKind.STRUCT_TYPE) {
            error(call.span,
                    "Generic method '"
                            + methodName
                            + "' requires a struct receiver, got type "
                            + rawTypeName(receiverType)
                            + "."
                            + " Generic methods are declared on structs and"
                            + " are invoked on struct values."
                            + " Dereference a pointer receiver with $(...) or"
                            + " call a free function for this value.");
            call.genericArgs = List.of();
            return call;
        }

        String ownerKey = resolveStructKey(receiverType, env.namespace);
        List<MethodTemplate> templates = methodTemplates.get(
                ownerKey + "." + methodName
        );

        if (templates == null || templates.isEmpty()) {
            error(call.span,
                    "Unknown generic method '"
                            + ownerKey
                            + "."
                            + methodName
                            + "'."
                            + " No #generic method template with that name is"
                            + " declared on this struct."
                            + " Check the method name, or declare the"
                            + " template inside the struct.");
            call.genericArgs = List.of();
            return call;
        }

        Instance ownerInstance = generatedStructInstances.get(ownerKey);
        Substitution callEnv = ownerSubstitution(
                ownerInstance,
                env.namespace
        );
        callEnv.currentDepth = env.depth;
        callEnv.valueBindings.putAll(env.valueBindings);

        List<Candidate> candidates = collectMethodCandidates(
                templates,
                call.genericArgs,
                callEnv,
                call.span
        );
        candidates = preferNarrowestValueFit(candidates);
        candidates = preferRawPointerPermissions(
                candidates,
                call.args,
                1
        );

        String argumentsKey = genericArgsKey(
                call.genericArgs,
                callEnv
        );
        String group = ownerKey
                + "."
                + methodName
                + "<"
                + argumentsKey
                + ">";
        String genericHash = Mangler.hashHex(argumentsKey);

        if (candidates.isEmpty()) {
            reportNoMethodCandidate(
                    templates,
                    call.genericArgs,
                    callEnv,
                    call.span
            );
        }

        for (Candidate candidate : candidates) {
            instantiateMethod(
                    candidate.template,
                    candidate.bound,
                    ownerInstance,
                    call.span,
                    genericHash,
                    group,
                    env.depth
            );
        }

        call.genericArgs = List.of();
        call.genericGroup = group;
        return call;
    }

    private List<Candidate> collectMethodCandidates(
            List<MethodTemplate> templates,
            List<AstNode> args,
            Substitution env,
            SourceSpan span
    ) {
        List<Candidate> candidates = new ArrayList<>();

        for (MethodTemplate template : templates) {
            BoundArguments bound = bindArguments(
                    template.bindingTemplate,
                    args,
                    env,
                    span,
                    false
            );

            if (bound != null) {
                candidates.add(new Candidate(
                        template.bindingTemplate,
                        bound
                ));
            }
        }

        return candidates;
    }

    private void reportNoMethodCandidate(
            List<MethodTemplate> templates,
            List<AstNode> args,
            Substitution env,
            SourceSpan span
    ) {
        if (templates.size() == 1) {
            bindArguments(
                    templates.get(0).bindingTemplate,
                    args,
                    env,
                    span
            );
            return;
        }

        MethodTemplate first = templates.get(0);
        error(span,
                "No generic method template '"
                        + first.ownerKey
                        + "."
                        + first.name
                        + "' matches generic arguments <"
                        + genericArgsKey(args, env)
                        + ">."
                        + " The number or kinds of the supplied arguments"
                        + " do not match any method template signature."
                        + " Check the argument count and kinds, or adjust"
                        + " the template's parameter list.");
    }

    private AstNode instantiateMethod(
            Template bindingTemplate,
            BoundArguments bound,
            Instance ownerInstance,
            SourceSpan span,
            String genericHash,
            String genericGroup,
            int callerDepth
    ) {
        MethodTemplate template = findMethodTemplate(bindingTemplate);

        if (template == null) {
            return null;
        }

        String key = template.ownerKey
                + "|"
                + template.name
                + "|"
                + template.signatureKey
                + "<"
                + String.join(",", bound.canonicalKeys)
                + ">";

        MethodInstance existing = methodInstances.get(key);

        if (existing != null) {
            return existing.methodNode;
        }

        if (instances.size() + methodInstances.size() >= MAX_INSTANCES) {
            if (!instanceLimitReported) {
                error(span,
                        "Generic expansion limit exceeded: more than "
                                + MAX_INSTANCES
                                + " generic instances in this compilation."
                                + " Each distinct generic method argument set"
                                + " creates a separate concrete method, so"
                                + " runaway recursion can exhaust memory and"
                                + " compile time."
                                + " Reduce template recursion or argument"
                                + " variation, or split the program into"
                                + " smaller compilations.");
                instanceLimitReported = true;
            }

            return null;
        }

        int ownerDepth = template.ownerInstance == null
                ? 0
                : template.ownerInstance.depth;
        int depth = Math.max(ownerDepth, callerDepth) + 1;

        if (depth > MAX_DEPTH) {
            error(span,
                    "Generic instantiation depth limit exceeded (maximum "
                            + MAX_DEPTH
                            + ") while instantiating method '"
                            + template.ownerKey
                            + "."
                            + template.name
                            + "'."
                            + " A template that keeps introducing new"
                            + " arguments without a base case eventually"
                            + " exceeds the "
                            + MAX_DEPTH
                            + "-level nesting limit."
                            + " Add or fix a base case so recursion reaches"
                            + " arguments that are already instantiated.");
            return null;
        }

        Substitution env = ownerSubstitution(
                template.ownerInstance,
                template.namespace
        );
        env.typeBindings.putAll(bound.typeBindings);
        env.valueBindings.putAll(bound.valueBindings);
        env.currentDepth = depth;

        AstNode clone = template.declaration.copyShallow();
        clone.isGeneric = false;
        clone.genericModifier = false;
        clone.genericParams = List.of();

        AstNode substituted = substituteNode(clone, env);

        if (substituted == null) {
            return null;
        }

        AstNode desugared = desugarer.desugarSpecializationMethod(
                substituted
        );

        if (desugared == null) {
            return null;
        }

        desugared.genericGroup = genericGroup;
        desugared.genericHash = genericHash;

        MethodInstance instance = new MethodInstance(desugared);
        methodInstances.put(key, instance);
        methodBodyDepths.put(desugared, depth);
        bodyValueBindings.put(
                desugared,
                new LinkedHashMap<>(env.valueBindings)
        );

        List<AstNode> methods = new ArrayList<>(template.ownerNode.methods);
        methods.add(desugared);
        template.ownerNode.methods = List.copyOf(methods);

        List<AstNode> members = new ArrayList<>(template.ownerNode.members);
        members.add(desugared);
        template.ownerNode.members = List.copyOf(members);

        return desugared;
    }

    private MethodTemplate findMethodTemplate(Template bindingTemplate) {
        for (List<MethodTemplate> group : methodTemplates.values()) {
            for (MethodTemplate candidate : group) {
                if (candidate.bindingTemplate == bindingTemplate) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private AstNode resolveReceiverType(AstNode expr, BodyEnv env) {
        if (expr == null) {
            return null;
        }

        switch (expr.kind) {
            case VAR: {
                AstNode local = env.resolveLocal(expr.name);

                if (local != null) {
                    return local;
                }

                return resolveGlobalType(expr.name, env.namespace);
            }

            case QUALIFIED_NAME:
                return globalTypes.get(String.join("::", expr.names));

            case MEMBER_ACCESS: {
                AstNode base = resolveReceiverType(expr.target, env);

                if (base == null || base.kind != AstKind.STRUCT_TYPE) {
                    return null;
                }

                String key = resolveStructKey(base, env.namespace);
                Map<String, AstNode> fields = structFieldTypes.get(key);
                return fields == null ? null : fields.get(expr.name);
            }

            case ARRAY_ACCESS: {
                AstNode base = resolveReceiverType(expr.target, env);

                if (base == null) {
                    return null;
                }

                if (base.kind == AstKind.DYNAMIC_ARRAY_TYPE
                        || base.kind == AstKind.STATIC_ARRAY_TYPE) {
                    return base.type;
                }

                return null;
            }

            case DEREF: {
                AstNode base = resolveReceiverType(expr.expr, env);

                if (base != null && base.kind == AstKind.PTR_TYPE) {
                    return base.type;
                }

                return null;
            }

            default:
                return null;
        }
    }

    private AstNode resolveGlobalType(String name, String namespace) {
        if (namespace != null) {
            AstNode type = globalTypes.get(namespace + "::" + name);

            if (type != null) {
                return type;
            }
        }

        return globalTypes.get(name);
    }

    private String resolveStructKey(AstNode type, String namespace) {
        String key = displayName(type.names, type.name);

        if (structNodes.containsKey(key)
                || generatedStructInstances.containsKey(key)) {
            return key;
        }

        if ((type.names == null || type.names.isEmpty())
                && namespace != null) {
            String qualified = namespace + "::" + type.name;

            if (structNodes.containsKey(qualified)
                    || generatedStructInstances.containsKey(qualified)) {
                return qualified;
            }
        }

        return key;
    }

    private static String rawTypeName(AstNode type) {
        if (type == null) {
            return "<unknown>";
        }

        switch (type.kind) {
            case PRIMITIVE_TYPE:
                return type.primitive.name().toLowerCase();

            case VOID_TYPE:
                return "void";

            case DYNAMIC_ARRAY_TYPE:
                return "[" + rawTypeName(type.type) + "]";

            case STATIC_ARRAY_TYPE:
                return "[" + rawTypeName(type.type) + ",?]";

            case PTR_TYPE:
                return type.permission.name().toLowerCase()
                        + "<"
                        + rawTypeName(type.type)
                        + ">";

            case TUPLE_TYPE:
                return "tuple";

            case STRUCT_TYPE:
                return displayName(type.names, type.name);

            default:
                return type.kind.jsonName();
        }
    }

    private List<Candidate> preferRawPointerPermissions(
            List<Candidate> candidates,
            List<AstNode> args,
            int offset
    ) {
        if (candidates.size() <= 1) {
            return candidates;
        }

        int best = Integer.MAX_VALUE;

        for (Candidate candidate : candidates) {
            best = Math.min(
                    best,
                    rawPointerPermissionCost(
                            candidate.template,
                            args,
                            offset
                    )
            );
        }

        List<Candidate> preferred = new ArrayList<>();

        for (Candidate candidate : candidates) {
            if (rawPointerPermissionCost(
                    candidate.template,
                    args,
                    offset) == best) {
                preferred.add(candidate);
            }
        }

        return preferred;
    }

    private static int rawPointerPermissionCost(
            Template template,
            List<AstNode> args,
            int offset
    ) {
        List<AstNode> parameters = template.declaration.parameters;
        int count = Math.min(
                Math.max(0, parameters.size() - offset),
                args.size()
        );
        int cost = 0;

        for (int i = 0; i < count; i++) {
            AstNode argument = args.get(i);
            AstNode parameterType = parameters.get(i + offset).type;

            if (argument.kind != AstKind.GET_PTR
                    || parameterType == null
                    || parameterType.kind != AstKind.PTR_TYPE) {
                continue;
            }

            PtrPermission argumentPermission = argument.permission;
            PtrPermission parameterPermission = parameterType.permission;

            if (parameterPermission.ordinal() < argumentPermission.ordinal()) {
                return Integer.MAX_VALUE;
            }

            cost += parameterPermission.ordinal() - argumentPermission.ordinal();
        }

        return cost;
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
