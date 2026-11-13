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
        final String signatureKey;

        Template(
                boolean struct,
                String namespace,
                String name,
                List<AstNode> parameters,
                AstNode declaration,
                String signatureKey
        ) {
            this.struct = struct;
            this.namespace = namespace;
            this.name = name;
            this.parameters = parameters;
            this.declaration = declaration;
            this.signatureKey = signatureKey;
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

        Substitution(String namespace) {
            this.namespace = namespace;
        }
    }

    private final DiagnosticBag diagnostics;
    private final TargetInfo target;
    private final Map<String, List<Template>> structTemplates = new LinkedHashMap<>();
    private final Map<String, List<Template>> functionTemplates = new LinkedHashMap<>();
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

                for (AstNode method : item.methods) {
                    if (method.isGeneric) {
                        error(method.span,
                                "Independent generic methods are not supported in this version: method '"
                                        + item.name
                                        + "."
                                        + method.name
                                        + "' may only use the struct's generic parameters."
                                        + " Each instance is generated from the"
                                        + " struct's own arguments, so a method"
                                        + " cannot introduce parameters that are"
                                        + " not bound by the struct."
                                        + " Remove the method's #generic modifier"
                                        + " and use the struct's parameters, or move"
                                        + " the generic logic into a generic free"
                                        + " function.");
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
        copy.defaultValue = substituteNode(node.defaultValue, env);

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
            return AstNode.structType(
                    node.span,
                    List.of(),
                    env.selfGeneratedName
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
                                + (template.struct ? "struct" : "function")
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
