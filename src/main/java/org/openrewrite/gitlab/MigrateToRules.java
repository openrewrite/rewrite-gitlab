/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gitlab;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.*;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateToRules extends Recipe {

    private static final String ALIASES = "ALIASES";
    private static final String INHERITANCE_CONFLICTS = "INHERITANCE_CONFLICTS";

    private static final Pattern PROJECT_QUALIFIED_REF = Pattern.compile("(/.*/|[^/@][^@]*)@([^@\\s]*[^@/\\s])");

    // GitLab applies these refs to an `only` that doesn't list any
    private static final List<String> DEFAULT_ONLY_REFS = Arrays.asList("branches", "tags");

    private static final String KUBERNETES_ACTIVE = "$CI_KUBERNETES_ACTIVE";

    String displayName = "Migrate `only`/`except` to `rules`";

    String description = "Replace the deprecated `only` and `except` keywords with equivalent `rules` " +
            "in `.gitlab-ci.yml` job definitions. When both `only` and `except` are present, " +
            "they are combined into a single `rules` block. Handles ref lists and the object form with " +
            "`refs`, `variables`, `changes` and `kubernetes`, matching named refs as branches.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles("**/.gitlab-ci.yml"),
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                        Map<UUID, Yaml> aliases = YamlAnchors.resolveAliases(document);
                        getCursor().putMessage(ALIASES, aliases);
                        getCursor().putMessage(INHERITANCE_CONFLICTS, inheritanceConflicts(document, aliases));
                        return super.visitDocument(document, ctx);
                    }

                    @Override
                    public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                        Yaml.Mapping m = super.visitMapping(mapping, ctx);

                        Yaml.Mapping.Entry onlyEntry = null;
                        Yaml.Mapping.Entry exceptEntry = null;

                        for (Yaml.Mapping.Entry entry : m.getEntries()) {
                            String key = entry.getKey().getValue();
                            if ("only".equals(key)) {
                                onlyEntry = entry;
                            } else if ("except".equals(key)) {
                                exceptEntry = entry;
                            } else if ("rules".equals(key)) {
                                return m;
                            }
                        }

                        Set<UUID> conflicts = getCursor().getNearestMessage(INHERITANCE_CONFLICTS, emptySet());
                        if (onlyEntry == null && exceptEntry == null || conflicts.contains(m.getId()) ||
                            definesAnchor(onlyEntry) || definesAnchor(exceptEntry)) {
                            return m;
                        }

                        Map<UUID, Yaml> aliases = getCursor().getNearestMessage(ALIASES, emptyMap());
                        Yaml.Mapping result = migrate(m, onlyEntry, exceptEntry, aliases);
                        if (result == m) {
                            return m;
                        }
                        // The job had no `rules` before, so this formats only the new entry
                        return result.withEntries(ListUtils.map(result.getEntries(), entry ->
                                "rules".equals(entry.getKey().getValue()) ? autoFormat(entry, ctx, getCursor()) : entry));
                    }
                }
        );
    }

    private static Yaml.Mapping migrate(Yaml.Mapping m, Yaml.Mapping.@Nullable Entry onlyEntry,
                                        Yaml.Mapping.@Nullable Entry exceptEntry, Map<UUID, Yaml> aliases) {
        Conditions only = onlyEntry == null ? null : conditions(onlyEntry.getValue(), aliases);
        Conditions except = exceptEntry == null ? null : conditions(exceptEntry.getValue(), aliases);
        if (onlyEntry != null && (only == null || only.refs != null && only.refs.isEmpty()) ||
            exceptEntry != null && (except == null || onlyEntry == null && except.isEmpty())) {
            return m;
        }

        // GitLab skips the job when any of the `except` sub-keys matches
        List<Rule> rules = new ArrayList<>();
        if (except != null) {
            if (except.refs != null) {
                for (String ref : except.refs) {
                    rules.add(new Rule(refToCondition(ref), null, "never"));
                }
            }
            for (String expression : except.variables) {
                rules.add(new Rule(expression, null, "never"));
            }
            if (except.changes != null) {
                rules.add(new Rule(null, except.changes, "never"));
            }
            if (except.kubernetes) {
                rules.add(new Rule(KUBERNETES_ACTIVE, null, "never"));
            }
        }
        if (only != null) {
            List<String> conjuncts = new ArrayList<>();
            if (!only.variables.isEmpty()) {
                conjuncts.add(String.join(" || ", only.variables));
            }
            if (only.kubernetes) {
                conjuncts.add(KUBERNETES_ACTIVE);
            }
            for (String ref : only.refs == null ? DEFAULT_ONLY_REFS : only.refs) {
                rules.add(new Rule(and(refToCondition(ref), conjuncts), only.changes, null));
            }
        } else {
            rules.add(new Rule(null, null, "always"));
        }

        Yaml.Mapping.Entry replaced = onlyEntry != null ? onlyEntry : exceptEntry;
        Yaml.Mapping.Entry parsed = parseRulesEntry(render(rules), replaced.getPrefix());
        if (parsed == null) {
            return m;
        }
        Yaml.Mapping.Entry rulesEntry = withChangesAliases(parsed, rules);
        return m.withEntries(ListUtils.map(m.getEntries(), entry -> {
            if (entry == replaced) {
                return rulesEntry;
            }
            String key = entry.getKey().getValue();
            return "only".equals(key) || "except".equals(key) ? null : entry;
        }));
    }

    // visitMapping's autoFormat re-indents these entries to match the document
    private static String render(List<Rule> rules) {
        StringBuilder sb = new StringBuilder("rules:");
        for (Rule rule : rules) {
            String separator = "\n        - ";
            if (rule.condition != null) {
                sb.append(separator).append("if: ").append(scalar(rule.condition));
                separator = "\n          ";
            }
            if (rule.changes instanceof Yaml.Sequence) {
                sb.append(separator).append("changes:");
                for (Yaml.Sequence.Entry path : ((Yaml.Sequence) rule.changes).getEntries()) {
                    sb.append("\n            - ").append(print((Yaml.Scalar) path.getBlock()));
                }
                separator = "\n          ";
            } else if (rule.changes != null) {
                sb.append(separator).append("changes: []");
                separator = "\n          ";
            }
            if (rule.when != null) {
                sb.append(separator).append("when: ").append(rule.when);
            }
        }
        return sb.toString();
    }

    // Reusing the alias keeps the paths shared with the anchor, which a standalone parse can't resolve
    private static Yaml.Mapping.Entry withChangesAliases(Yaml.Mapping.Entry rulesEntry, List<Rule> rules) {
        Yaml.Sequence sequence = (Yaml.Sequence) rulesEntry.getValue();
        return rulesEntry.withValue(sequence.withEntries(ListUtils.map(sequence.getEntries(), (i, seqEntry) -> {
            Yaml changes = rules.get(i).changes;
            if (!(changes instanceof Yaml.Alias)) {
                return seqEntry;
            }
            Yaml.Mapping rule = (Yaml.Mapping) seqEntry.getBlock();
            return seqEntry.withBlock(rule.withEntries(ListUtils.map(rule.getEntries(), entry ->
                    "changes".equals(entry.getKey().getValue()) ?
                            entry.withValue(((Yaml.Alias) changes).withId(Tree.randomId()).withPrefix(" ")) : entry)));
        })));
    }

    private static String print(Yaml.Scalar scalar) {
        switch (scalar.getStyle()) {
            case SINGLE_QUOTED:
                return '\'' + scalar.getValue() + '\'';
            case DOUBLE_QUOTED:
                return '"' + scalar.getValue() + '"';
            default:
                return scalar.getValue();
        }
    }

    private static String and(String condition, List<String> conjuncts) {
        StringBuilder sb = new StringBuilder(condition);
        for (String conjunct : conjuncts) {
            sb.append(" && ").append(conjunct.contains("||") ? "(" + conjunct + ")" : conjunct);
        }
        return sb.toString();
    }

    private static @Nullable Conditions conditions(Yaml value, Map<UUID, Yaml> aliases) {
        if (value instanceof Yaml.Sequence) {
            List<String> refs = extractRefs((Yaml.Sequence) value);
            return refs == null ? null : new Conditions(refs, emptyList(), null, false);
        }
        if (!(value instanceof Yaml.Mapping) || ((Yaml.Mapping) value).getEntries().isEmpty()) {
            return null;
        }
        List<String> refs = null;
        List<String> variables = emptyList();
        Yaml changes = null;
        boolean kubernetes = false;
        for (Yaml.Mapping.Entry entry : ((Yaml.Mapping) value).getEntries()) {
            Yaml subValue = entry.getValue();
            switch (entry.getKey().getValue()) {
                case "refs":
                    refs = subValue instanceof Yaml.Sequence ? extractRefs((Yaml.Sequence) subValue) : null;
                    if (refs == null || refs.isEmpty()) {
                        return null;
                    }
                    break;
                case "variables":
                    List<String> expressions = subValue instanceof Yaml.Sequence ?
                            expressions((Yaml.Sequence) subValue) : null;
                    if (expressions == null || expressions.isEmpty()) {
                        return null;
                    }
                    variables = expressions;
                    break;
                case "changes":
                    if (!isPathList(subValue instanceof Yaml.Alias ? aliases.get(subValue.getId()) : subValue)) {
                        return null;
                    }
                    changes = subValue;
                    break;
                case "kubernetes":
                    if (!(subValue instanceof Yaml.Scalar) || !"active".equals(((Yaml.Scalar) subValue).getValue())) {
                        return null;
                    }
                    kubernetes = true;
                    break;
                default:
                    return null;
            }
        }
        return new Conditions(refs, variables, changes, kubernetes);
    }

    // Unlike `only:changes`, `rules:changes` expands CI/CD variables in paths
    private static boolean isPathList(@Nullable Yaml paths) {
        if (!(paths instanceof Yaml.Sequence) || ((Yaml.Sequence) paths).getEntries().isEmpty()) {
            return false;
        }
        for (Yaml.Sequence.Entry seqEntry : ((Yaml.Sequence) paths).getEntries()) {
            if (!(seqEntry.getBlock() instanceof Yaml.Scalar)) {
                return false;
            }
            Yaml.Scalar path = (Yaml.Scalar) seqEntry.getBlock();
            if (path.getTag() != null || path.getValue().contains("$") ||
                path.getStyle() != Yaml.Scalar.Style.PLAIN && path.getStyle() != Yaml.Scalar.Style.SINGLE_QUOTED &&
                path.getStyle() != Yaml.Scalar.Style.DOUBLE_QUOTED) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable List<String> expressions(Yaml.Sequence seq) {
        List<String> expressions = new ArrayList<>();
        for (Yaml.Sequence.Entry seqEntry : seq.getEntries()) {
            String expression = seqEntry.getBlock() instanceof Yaml.Scalar ?
                    expression((Yaml.Scalar) seqEntry.getBlock()) : null;
            if (expression == null) {
                return null;
            }
            expressions.add(expression);
        }
        return expressions;
    }

    // The parser keeps the source text of quoted scalars, escapes included
    private static @Nullable String expression(Yaml.Scalar scalar) {
        String value = scalar.getValue();
        if (scalar.getTag() != null || value.isEmpty() || value.contains("\n")) {
            return null;
        }
        switch (scalar.getStyle()) {
            case PLAIN:
                return value;
            case SINGLE_QUOTED:
                return value.replace("''", "'");
            case DOUBLE_QUOTED:
                return value.contains("\\") ? null : value;
            default:
                return null;
        }
    }

    private static String scalar(String value) {
        if (isPlainScalar(value)) {
            return value;
        }
        if (value.indexOf('"') < 0 && value.indexOf('\\') < 0) {
            return '"' + value + '"';
        }
        return '\'' + value.replace("'", "''") + '\'';
    }

    private static boolean isPlainScalar(String value) {
        return YamlParser.builder().build()
                .parse("if: " + value)
                .filter(Yaml.Documents.class::isInstance)
                .map(docs -> ((Yaml.Documents) docs).getDocuments().get(0).getBlock())
                .filter(Yaml.Mapping.class::isInstance)
                .map(block -> ((Yaml.Mapping) block).getEntries().get(0).getValue())
                .anyMatch(parsed -> parsed instanceof Yaml.Scalar &&
                                    ((Yaml.Scalar) parsed).getStyle() == Yaml.Scalar.Style.PLAIN &&
                                    ((Yaml.Scalar) parsed).getTag() == null &&
                                    value.equals(((Yaml.Scalar) parsed).getValue()));
    }

    // GitLab combines `only` and `except` inherited through `extends` or `<<`, whereas `rules` replace inherited
    // ones. So no job or template in a lineage is migrated when more than one of them defines conditions, nor when the
    // lineage includes a template that is not in this file, such as one from an `include`.
    private static Set<UUID> inheritanceConflicts(Yaml.Document document, Map<UUID, Yaml> aliases) {
        if (!(document.getBlock() instanceof Yaml.Mapping)) {
            return emptySet();
        }
        Map<String, Yaml.Mapping> jobs = new HashMap<>();
        for (Yaml.Mapping.Entry entry : ((Yaml.Mapping) document.getBlock()).getEntries()) {
            if (entry.getValue() instanceof Yaml.Mapping) {
                jobs.put(entry.getKey().getValue(), (Yaml.Mapping) entry.getValue());
            }
        }
        Set<UUID> conflicts = new HashSet<>();
        for (Yaml.Mapping job : jobs.values()) {
            List<Yaml.Mapping> lineage = new ArrayList<>();
            lineage.add(job);
            Set<UUID> visited = new HashSet<>();
            visited.add(job.getId());
            boolean resolved = addAncestors(job, jobs, aliases, visited, lineage);
            if (!resolved || lineage.stream().filter(MigrateToRules::definesConditions).count() > 1) {
                lineage.forEach(mapping -> conflicts.add(mapping.getId()));
            }
        }
        return conflicts;
    }

    private static boolean definesConditions(Yaml.Mapping mapping) {
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            String key = entry.getKey().getValue();
            if ("only".equals(key) || "except".equals(key) || "rules".equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean addAncestors(Yaml.Mapping mapping, Map<String, Yaml.Mapping> jobs, Map<UUID, Yaml> aliases,
                                        Set<UUID> visited, List<Yaml.Mapping> lineage) {
        boolean resolved = true;
        for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
            String key = entry.getKey().getValue();
            Function<Yaml, Yaml.@Nullable Mapping> resolve;
            if ("extends".equals(key)) {
                resolve = ref -> ref instanceof Yaml.Scalar ? jobs.get(((Yaml.Scalar) ref).getValue()) : null;
            } else if ("<<".equals(key)) {
                resolve = ref -> YamlAnchors.mergedMapping(ref, aliases);
            } else {
                continue;
            }
            for (Yaml ref : references(entry.getValue())) {
                Yaml.Mapping parent = resolve.apply(ref);
                if (parent == null) {
                    resolved = false;
                } else if (visited.add(parent.getId())) {
                    lineage.add(parent);
                    resolved &= addAncestors(parent, jobs, aliases, visited, lineage);
                }
            }
        }
        return resolved;
    }

    private static List<Yaml> references(Yaml value) {
        if (!(value instanceof Yaml.Sequence)) {
            return singletonList(value);
        }
        List<Yaml> references = new ArrayList<>();
        for (Yaml.Sequence.Entry entry : ((Yaml.Sequence) value).getEntries()) {
            references.add(entry.getBlock());
        }
        return references;
    }

    // Replacing the entry would drop the anchor and leave its aliases dangling
    private static boolean definesAnchor(Yaml.Mapping.@Nullable Entry entry) {
        return entry != null && !YamlAnchors.anchorsIn(entry.getValue()).isEmpty();
    }

    static @Nullable List<String> extractRefs(Yaml.Sequence seq) {
        List<String> refs = new ArrayList<>();
        for (Yaml.Sequence.Entry seqEntry : seq.getEntries()) {
            if (!(seqEntry.getBlock() instanceof Yaml.Scalar)) {
                return null;
            }
            refs.add(((Yaml.Scalar) seqEntry.getBlock()).getValue());
        }
        return refs;
    }

    static Yaml.Mapping.@Nullable Entry parseRulesEntry(String yamlString, String prefix) {
        return YamlParser.builder().build()
                .parse(yamlString)
                .map(Yaml.Documents.class::cast)
                .map(docs -> (Yaml.Mapping) docs.getDocuments().get(0).getBlock())
                .map(mapping -> mapping.getEntries().get(0).withPrefix(prefix))
                .findFirst()
                .orElse(null);
    }

    static String refToCondition(String ref) {
        Matcher projectQualified = PROJECT_QUALIFIED_REF.matcher(ref);
        if (projectQualified.matches()) {
            return refToCondition(projectQualified.group(1)) +
                    " && $CI_PROJECT_PATH == '" + projectQualified.group(2) + "'";
        }
        switch (ref) {
            case "branches":
                return "$CI_COMMIT_BRANCH";
            case "tags":
                return "$CI_COMMIT_TAG";
            case "merge_requests":
                return "$CI_PIPELINE_SOURCE == 'merge_request_event'";
            case "schedules":
                return "$CI_PIPELINE_SOURCE == 'schedule'";
            case "web":
                return "$CI_PIPELINE_SOURCE == 'web'";
            case "api":
                return "$CI_PIPELINE_SOURCE == 'api'";
            case "pipelines":
                return "$CI_PIPELINE_SOURCE == 'pipeline'";
            case "triggers":
                return "$CI_PIPELINE_SOURCE == 'trigger'";
            case "pushes":
                return "$CI_PIPELINE_SOURCE == 'push'";
            case "external":
                return "$CI_PIPELINE_SOURCE == 'external'";
            case "external_pull_requests":
                return "$CI_PIPELINE_SOURCE == 'external_pull_request_event'";
            case "chat":
                return "$CI_PIPELINE_SOURCE == 'chat'";
            default:
                if (ref.startsWith("/") && ref.endsWith("/")) {
                    return "$CI_COMMIT_BRANCH =~ " + ref;
                }
                return "$CI_COMMIT_BRANCH == '" + ref + "'";
        }
    }

    @Value
    private static class Conditions {
        @Nullable
        List<String> refs;

        List<String> variables;

        @Nullable
        Yaml changes;

        boolean kubernetes;

        boolean isEmpty() {
            return (refs == null || refs.isEmpty()) && variables.isEmpty() && changes == null && !kubernetes;
        }
    }

    @Value
    private static class Rule {
        @Nullable
        String condition;

        @Nullable
        Yaml changes;

        @Nullable
        String when;
    }
}
