package org.openrewrite.gitlab.core;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.yaml.ChangeValue;

@Value
@EqualsAndHashCode(callSuper = false)
public class ChangeTemplate extends Recipe {

    @Option(displayName = "Template",
            description = "The name of the template to match.",
            example = "Terraform/Base.gitlab-ci.yml")
    String oldTemplate;

    @Option(displayName = "Template",
            description = "Name of the template to use instead.",
            example = "OpenTofu/Base.gitlab-ci.yml")
    String newTemplate;

    @Override
    public String getDisplayName() {
        return "Change GitLab Template";
    }

    @Override
    public String getDescription() {
        return "Change a GitLab Template in use.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new FindSourceFiles(".gitlab-ci.yml"),
                new ChangeValue(
                        "$.include[?(@.template =~ '" + oldTemplate + "(?:@.+)?')].template",
                        newTemplate,
                        null).getVisitor()
        );
    }
}
