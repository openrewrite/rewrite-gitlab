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
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;

import java.util.List;

import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddWorkflowRules extends Recipe {

    @Option(displayName = "Rules",
            description = "The YAML snippet defining the workflow rules.",
            example = "- if: $CI_PIPELINE_SOURCE == 'merge_request_event'\\n- if: $CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS\\n  when: never\\n- if: $CI_COMMIT_BRANCH")
    String rules;

    @Option(displayName = "Accept theirs",
            description = "When workflow rules already exist, prefer the original value.",
            required = false)
    @Nullable
    Boolean acceptTheirs;

    String displayName = "Add workflow rules";

    String description = "Add `workflow:rules` to `.gitlab-ci.yml` to control pipeline creation.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(
                new MergeYaml(
                        "$",
                        //language=yml
                        "workflow:\n  rules:\n    " + (rules == null ? "" : rules.replace("\n", "\n    ")),
                        acceptTheirs,
                        null,
                        ".gitlab-ci.yml",
                        null,
                        null,
                        null)
        );
    }
}
