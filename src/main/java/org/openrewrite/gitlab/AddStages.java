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
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;
import org.openrewrite.yaml.MergeYaml.InsertMode;

import java.util.List;

import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddStages extends Recipe {
    @Option(displayName = "Stages",
            description = "Stages to add.",
            example = "build,test,deploy")
    List<String> stages;

    @Option(displayName = "Accept theirs",
            description = "When the set of stages would conflict, prefer the original value.",
            required = false)
    @Nullable
    Boolean acceptTheirs;

    @Option(displayName = "Insert mode",
            description = "Choose an insertion point when multiple mappings exist. Default is `Last`.",
            valid = {"Before", "After", "Last"},
            required = false)
    @Nullable
    InsertMode insertMode;

    String displayName = "Add GitLab stages";

    String description = "Add or Update the set of stages defined in `.gitlab-ci.yml`.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(
                new MergeYaml(
                        "$",
                        //language=yml
                        "stages:\n" +
                                ((stages == null) ? "" : ("  - " + String.join("\n  - ", stages))),
                        acceptTheirs,
                        "stages",
                        ".gitlab-ci.yml",
                        insertMode,
                        null,
                        null)
        );
    }
}
