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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
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
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                if (!documents.getSourcePath().endsWith(".gitlab-ci.yml")) {
                    return documents;
                }
                return super.visitDocuments(documents, ctx);
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);

                int exceptIdx = -1;
                boolean hasRules = false;
                boolean hasOnly = false;

                for (int i = 0; i < m.getEntries().size(); i++) {
                    String key = m.getEntries().get(i).getKey().getValue();
                    if ("except".equals(key)) {
                        exceptIdx = i;
                    } else if ("rules".equals(key)) {
                        hasRules = true;
                    } else if ("only".equals(key)) {
                        hasOnly = true;
                    }
                }

                if (exceptIdx < 0 || hasRules || hasOnly) {
                    return m;
                }

                Yaml.Mapping.Entry exceptEntry = m.getEntries().get(exceptIdx);

                if (!(exceptEntry.getValue() instanceof Yaml.Sequence)) {
                    return m;
                }

                Yaml.Sequence seq = (Yaml.Sequence) exceptEntry.getValue();
                List<String> refs = new ArrayList<>();
                for (Yaml.Sequence.Entry seqEntry : seq.getEntries()) {
                    if (!(seqEntry.getBlock() instanceof Yaml.Scalar)) {
                        return m;
                    }
                    refs.add(((Yaml.Scalar) seqEntry.getBlock()).getValue());
                }

                if (refs.isEmpty()) {
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

                List<Yaml.Mapping.Entry> newEntries = new ArrayList<>(m.getEntries());
                newEntries.set(exceptIdx, rulesEntry);
                return m.withEntries(newEntries);
            }
        };
    }
}
