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

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddStages extends Recipe {
    @Option(displayName = "Stages",
            description = "Stages to add, in the order they run. A stage missing from an existing `stages` list goes after " +
                          "the listed stage before it.",
            example = "build,test,deploy")
    List<String> stages;

    @Option(displayName = "Accept theirs",
            description = "Leave an existing `stages` list untouched, so stages are only added to files that declare none.",
            required = false)
    @Nullable
    Boolean acceptTheirs;

    String displayName = "Add GitLab stages";

    String description = "Add or Update the set of stages defined in `.gitlab-ci.yml`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return MergeKeyword.list("stages", stages, acceptTheirs).getVisitor();
    }
}
