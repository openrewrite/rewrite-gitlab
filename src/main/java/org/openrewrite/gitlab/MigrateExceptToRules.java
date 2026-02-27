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
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateExceptToRules extends Recipe {

    String displayName = "Migrate `except` to `rules`";

    String description = "Replace the deprecated `except` keyword with equivalent `rules` " +
            "in `.gitlab-ci.yml` job definitions. Each excluded ref becomes a rule with " +
            "`when: never`, followed by a `when: always` fallback. Handles simple ref list " +
            "forms; complex object forms are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles("**/.gitlab-ci.yml"),
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                        Yaml.Mapping m = super.visitMapping(mapping, ctx);

                        Yaml.Mapping.Entry exceptEntry = null;
                        boolean hasRules = false;
                        boolean hasOnly = false;

                        for (Yaml.Mapping.Entry entry : m.getEntries()) {
                            String key = entry.getKey().getValue();
                            if ("except".equals(key)) {
                                exceptEntry = entry;
                            } else if ("rules".equals(key)) {
                                hasRules = true;
                            } else if ("only".equals(key)) {
                                hasOnly = true;
                            }
                        }

                        // Skip when only is present (MigrateOnlyToRules handles the combined case)
                        if (exceptEntry == null || hasRules || hasOnly) {
                            return m;
                        }

                        if (!(exceptEntry.getValue() instanceof Yaml.Sequence)) {
                            return m;
                        }

                        List<String> refs = MigrateOnlyToRules.extractRefs((Yaml.Sequence) exceptEntry.getValue());
                        if (refs == null || refs.isEmpty()) {
                            return m;
                        }

                        String entryPrefix = exceptEntry.getPrefix();
                        String baseIndent = entryPrefix.contains("\n") ?
                                entryPrefix.substring(entryPrefix.lastIndexOf('\n') + 1) : "  ";
                        String seqIndent = baseIndent + "  ";
                        String contentIndent = seqIndent + "  ";

                        StringBuilder sb = new StringBuilder("rules:");
                        for (String ref : refs) {
                            sb.append("\n").append(seqIndent).append("- if: ").append(MigrateOnlyToRules.refToCondition(ref));
                            sb.append("\n").append(contentIndent).append("when: never");
                        }
                        sb.append("\n").append(seqIndent).append("- when: always");

                        Yaml.Mapping.Entry rulesEntry = MigrateOnlyToRules.parseRulesEntry(
                                sb.toString(), exceptEntry.getPrefix());
                        if (rulesEntry == null) {
                            return m;
                        }

                        final Yaml.Mapping.Entry finalRulesEntry = rulesEntry;
                        return m.withEntries(ListUtils.map(m.getEntries(), entry -> {
                            if ("except".equals(entry.getKey().getValue())) {
                                return finalRulesEntry;
                            }
                            return entry;
                        }));
                    }
                }
        );
    }
}
