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
public class FindTemplate extends Recipe {

    @Option(displayName = "template",
            description = "The template key to look for",
            example = "Terraform/Base.gitlab-ci.yml")
    String template;

    @Override
    public String getDisplayName() {
        return "Find GitLab Template";
    }

    @Override
    public String getDescription() {
        return "Find a GitLab Template in use.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Collections.singletonList(new FindKey("$.include[?(@.template =~ '" + template + "(?:@.+)?')].template"));
    }
}
