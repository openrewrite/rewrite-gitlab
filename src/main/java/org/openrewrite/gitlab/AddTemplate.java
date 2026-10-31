/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.MergeYaml.InsertMode;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddTemplate extends Recipe {

    @Option(displayName = "Template",
            description = "Name of the template to use instead.",
            example = "OpenTofu/Base.gitlab-ci.yml")
    String newTemplate;

    @Option(displayName = "Insert mode",
            description = "Where to insert the template in an existing `include` list. Default is `Last`.",
            valid = {"Before", "After", "Last"},
            required = false)
    @Nullable
    InsertMode insertMode;

    @Option(displayName = "Insert property",
            description = "The existing `include` entry to insert the template before or after, written as its first key and value. " +
                          "Only used when `insertMode` is `Before` or `After`.",
            example = "component: $CI_SERVER_FQDN/components/opentofu/full-pipeline@0.10.0",
            required = false)
    @Nullable
    String insertProperty;

    String displayName = "Add GitLab template";

    String description = "Add a GitLab template to an existing list, or add a new list where none was present.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return MergeKeyword.global(
                "include",
                "include:\n - template: " + newTemplate,
                false,
                "template",
                insertMode,
                insertProperty)
                .getVisitor();
    }
}
