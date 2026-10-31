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

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateToRules extends Recipe {

    private static final String INHERITANCE_CONFLICTS = "INHERITANCE_CONFLICTS";

    private static final Pattern PROJECT_QUALIFIED_REF = Pattern.compile("(/.*/|[^/@][^@]*)@([^@\\s]*[^@/\\s])");

    String displayName = "Migrate `only`/`except` to `rules`";

    String description = "Replace the deprecated `only` and `except` keywords with equivalent `rules` " +
            "in `.gitlab-ci.yml` job definitions. When both `only` and `except` are present, " +
            "they are combined into a single `rules` block. Handles simple ref list forms, " +
            "matching named refs as branches; complex object forms with `refs`, `variables`, or `changes` sub-keys are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles("**/.gitlab-ci.yml"),
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
                        getCursor().putMessage(INHERITANCE_CONFLICTS, inheritanceConflicts(document));
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

                        Yaml.Mapping result = onlyEntry != null ?
                                migrateWithOnly(m, onlyEntry, exceptEntry) :
                                migrateExceptOnly(m, exceptEntry);
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

    private static Yaml.Mapping migrateWithOnly(Yaml.Mapping m, Yaml.Mapping.Entry onlyEntry,
                                                 Yaml.Mapping.@Nullable Entry exceptEntry) {
        if (!(onlyEntry.getValue() instanceof Yaml.Sequence)) {
            return m;
        }

        List<String> onlyRefs = extractRefs((Yaml.Sequence) onlyEntry.getValue());
        if (onlyRefs == null || onlyRefs.isEmpty()) {
            return m;
        }

        List<String> exceptRefs = null;
        if (exceptEntry != null) {
            if (!(exceptEntry.getValue() instanceof Yaml.Sequence)) {
                return m;
            }
            exceptRefs = extractRefs((Yaml.Sequence) exceptEntry.getValue());
            if (exceptRefs == null) {
                return m;
            }
        }

        // visitMapping's autoFormat re-indents these entries to match the document
        StringBuilder sb = new StringBuilder("rules:");
        if (exceptRefs != null && !exceptRefs.isEmpty()) {
            for (String ref : exceptRefs) {
                sb.append("\n        - if: ").append(refToCondition(ref));
                sb.append("\n          when: never");
            }
        }
        for (String ref : onlyRefs) {
            sb.append("\n        - if: ").append(refToCondition(ref));
        }

        Yaml.Mapping.Entry rulesEntry = parseRulesEntry(sb.toString(), onlyEntry.getPrefix());
        if (rulesEntry == null) {
            return m;
        }
        boolean removeExcept = exceptEntry != null;
        return m.withEntries(ListUtils.map(m.getEntries(), entry -> {
            String key = entry.getKey().getValue();
            if ("only".equals(key)) {
                return rulesEntry;
            }
            if (removeExcept && "except".equals(key)) {
                return null;
            }
            return entry;
        }));
    }

    private static Yaml.Mapping migrateExceptOnly(Yaml.Mapping m, Yaml.Mapping.Entry exceptEntry) {
        if (!(exceptEntry.getValue() instanceof Yaml.Sequence)) {
            return m;
        }

        List<String> refs = extractRefs((Yaml.Sequence) exceptEntry.getValue());
        if (refs == null || refs.isEmpty()) {
            return m;
        }

        StringBuilder sb = new StringBuilder("rules:");
        for (String ref : refs) {
            sb.append("\n        - if: ").append(refToCondition(ref));
            sb.append("\n          when: never");
        }
        sb.append("\n        - when: always");

        Yaml.Mapping.Entry rulesEntry = parseRulesEntry(sb.toString(), exceptEntry.getPrefix());
        if (rulesEntry == null) {
            return m;
        }
        return m.withEntries(ListUtils.map(m.getEntries(), entry -> {
            if ("except".equals(entry.getKey().getValue())) {
                return rulesEntry;
            }
            return entry;
        }));
    }

    // GitLab combines `only` and `except` inherited through `extends` or `<<`, whereas `rules` replace inherited
    // ones. So no job or template in a lineage is migrated when more than one of them defines conditions, nor when the
    // lineage includes a template that is not in this file, such as one from an `include`.
    private static Set<UUID> inheritanceConflicts(Yaml.Document document) {
        if (!(document.getBlock() instanceof Yaml.Mapping)) {
            return emptySet();
        }
        Map<String, Yaml.Mapping> jobs = new HashMap<>();
        for (Yaml.Mapping.Entry entry : ((Yaml.Mapping) document.getBlock()).getEntries()) {
            if (entry.getValue() instanceof Yaml.Mapping) {
                jobs.put(entry.getKey().getValue(), (Yaml.Mapping) entry.getValue());
            }
        }
        Map<UUID, Yaml> aliases = YamlAnchors.resolveAliases(document);
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
}
