package org.openrewrite.gitlab.core;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.DeleteKey;

@Value
@EqualsAndHashCode
public class RemoveTemplate extends Recipe {

    @Option(displayName = "Template",
            description = "The name of the template to match.",
            example = "Terraform/Base.gitlab-ci.yml")
    String oldTemplate;

    @Override
    public String getDisplayName() {
        return "Change GitLab template";
    }

    @Override
    public String getDescription() {
        return "Change a GitLab template in use.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new DeleteKey(
                "$.include[?(@.template =~ '" + oldTemplate + "(?:@.+)?')]",
                ".gitlab-ci.yml").getVisitor();
    }
}
