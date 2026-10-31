/*
 * Copyright 2026 the original author or authors.
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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import static java.util.Collections.emptySet;

/// Changes the image references of `image:` and `services:` entries. An image that comes in through an alias is
/// changed once, where its anchor is defined, so that every alias follows.
@RequiredArgsConstructor
class ChangeImageVisitor extends YamlIsoVisitor<ExecutionContext> {
    private static final String IMAGES = "IMAGES";

    // A compact flow mapping such as `{name: x}` is parsed as a scalar
    private static final Pattern REFERENCE = Pattern.compile("[^\\s{}\\[\\],]+");

    private final UnaryOperator<ImageReference> change;

    @Override
    public Yaml.Document visitDocument(Yaml.Document document, ExecutionContext ctx) {
        getCursor().putMessage(IMAGES, new Images(document).scalars(getCursor().getParentOrThrow()));
        return super.visitDocument(document, ctx);
    }

    @Override
    public Yaml.Scalar visitScalar(Yaml.Scalar scalar, ExecutionContext ctx) {
        Yaml.Scalar s = super.visitScalar(scalar, ctx);
        if (!getCursor().<Set<UUID>>getNearestMessage(IMAGES, emptySet()).contains(s.getId()) ||
            !REFERENCE.matcher(s.getValue()).matches()) {
            return s;
        }
        ImageReference reference = ImageReference.parse(s.getValue());
        if (reference.hasVariable()) {
            return s;
        }
        ImageReference changed = change.apply(reference);
        // A digest pins the content of the old tag
        if (!Objects.equals(changed.getTag(), reference.getTag())) {
            changed = changed.withDigest(null);
        }
        String value = changed.toString();
        return value.equals(s.getValue()) ? s : s.withValue(value);
    }

    private static class Images {
        private final Yaml.Document document;
        private final Map<UUID, Yaml> aliases;
        private final Set<UUID> hiddenJobValues = new HashSet<>();
        private final Set<UUID> imageAliases = new HashSet<>();
        private final Set<UUID> imageNodes = new HashSet<>();
        private final Set<UUID> directImageNodes = new HashSet<>();
        private final Map<UUID, Yaml> aliasedImageNodes = new HashMap<>();
        private final Set<UUID> scalars = new HashSet<>();

        Images(Yaml.Document document) {
            this.document = document;
            this.aliases = YamlAnchors.resolveAliases(document);
            if (document.getBlock() instanceof Yaml.Mapping) {
                for (Yaml.Mapping.Entry entry : ((Yaml.Mapping) document.getBlock()).getEntries()) {
                    if (entry.getKey().getValue().startsWith(".")) {
                        hiddenJobValues.add(entry.getValue().getId());
                    }
                }
            }
        }

        /// Returns the ids of the scalars holding an image reference that can be changed in place.
        Set<UUID> scalars(Cursor parent) {
            new YamlIsoVisitor<Images>() {
                @Override
                public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, Images images) {
                    if (ImageKeywords.isImage(getCursor())) {
                        images.image(entry.getValue());
                    } else if (ImageKeywords.isServices(getCursor())) {
                        images.services(entry.getValue());
                    }
                    return super.visitMappingEntry(entry, images);
                }
            }.visit(document, this, parent);

            Map<UUID, Yaml> shared = new HashMap<>();
            // The anchor is defined somewhere other than an image or a hidden job, such as a variable
            for (Yaml node : aliasedImageNodes.values()) {
                if (!directImageNodes.contains(node.getId()) && !hiddenJobValues.contains(node.getId())) {
                    shared.put(node.getId(), node);
                }
            }
            // The anchor is also aliased somewhere other than an image
            new YamlIsoVisitor<Map<UUID, Yaml>>() {
                @Override
                public Yaml.Alias visitAlias(Yaml.Alias alias, Map<UUID, Yaml> nodes) {
                    Yaml target = aliases.get(alias.getId());
                    if (target != null && !imageAliases.contains(alias.getId()) && imageNodes.contains(target.getId())) {
                        nodes.put(target.getId(), target);
                    }
                    return alias;
                }
            }.visit(document, shared);
            for (Yaml node : shared.values()) {
                scalars.removeAll(scalarsIn(node));
            }
            return scalars;
        }

        private void services(Yaml.Block services) {
            Yaml resolved = resolve(services);
            if (resolved instanceof Yaml.Sequence) {
                for (Yaml.Sequence.Entry entry : ((Yaml.Sequence) resolved).getEntries()) {
                    image(entry.getBlock());
                }
            }
        }

        private void image(Yaml.Block image) {
            Yaml resolved = resolve(image);
            if (resolved instanceof Yaml.Scalar) {
                scalars.add(resolved.getId());
            } else if (resolved instanceof Yaml.Mapping) {
                for (Yaml.Mapping.Entry entry : ((Yaml.Mapping) resolved).getEntries()) {
                    if ("name".equals(entry.getKey().getValue())) {
                        image(entry.getValue());
                    }
                }
            }
        }

        private @Nullable Yaml resolve(Yaml.Block block) {
            if (block instanceof Yaml.Alias) {
                imageAliases.add(block.getId());
                Yaml target = aliases.get(block.getId());
                if (target != null) {
                    imageNodes.add(target.getId());
                    aliasedImageNodes.put(target.getId(), target);
                }
                return target;
            }
            imageNodes.add(block.getId());
            directImageNodes.add(block.getId());
            return block;
        }

        private static Set<UUID> scalarsIn(Yaml node) {
            return new YamlIsoVisitor<Set<UUID>>() {
                @Override
                public Yaml.Scalar visitScalar(Yaml.Scalar scalar, Set<UUID> ids) {
                    ids.add(scalar.getId());
                    return scalar;
                }
            }.reduce(node, new HashSet<>());
        }
    }
}
