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
package org.openrewrite.gitlab.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.search.FindKey;

import java.util.Collections;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class FindComponent extends Recipe {

    @Option(displayName = "Component",
            description = "The component key to look for",
            example = "$CI_SERVER_FQDN/components/opentofu/full-pipeline")
    String component;

    @Override
    public String getDisplayName() {
        return "Find GitLab Component";
    }

    @Override
    public String getDescription() {
        return "Find a GitLab Component in use.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Collections.singletonList(new FindKey("$.include[?(@.component =~ '" + component + "(?:@.+)?')].component"));
    }
}
