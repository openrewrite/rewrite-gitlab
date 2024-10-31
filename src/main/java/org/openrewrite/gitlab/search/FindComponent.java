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

    @Option(displayName = "component",
            description = "The template key to look for",
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
