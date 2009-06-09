/* Copyright (c) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.google.gdata.model;

import com.google.gdata.util.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import java.util.Map;

/**
 * An immutable metadata registry that stores an entire set of metadata.
 * Metadata registries can be created using {@link MetadataRegistryBuilder}.
 *
 * 
 */
public final class MetadataRegistry {

  /**
   * Static factory method to allow the standard code of
   * {@code MetadataRegistry.builder()} to return something useful.
   */
  public static MetadataRegistryBuilder builder() {
    return new MetadataRegistryBuilder();
  }

  /**
   * Calculate the root key for a given element key.  This is the key we use
   * when finding the appropriate element registry to store information in.
   */
  static RootKey getRootKey(ElementKey<?, ?> key) {
    return RootKey.get(key);
  }

  /**
   * Calculate the root key for a given attribute key.  This is the key we use
   * when finding the appropriate attribute registry to store information in,
   * and is just the ID with a dummy datatype.
   */
  static RootKey getRootKey(AttributeKey<?> key) {
    return RootKey.get(key);
  }

  // The element registries store the actual data, this map is immutable.
  private final Map<RootKey, ElementMetadataRegistry> elements;

  // The attribute registries store the actual data, this map is immutable.
  private final Map<RootKey, AttributeMetadataRegistry> attributes;

  /**
   * Create a metadata registry from the given metadata registry builder.
   */
  MetadataRegistry(MetadataRegistryBuilder registryBuilder) {
    this.elements = buildElements(registryBuilder, this);
    this.attributes = buildAttributes(registryBuilder, this);
  }

  /**
   * Creates the immutable map of attribute key -> attribute registry.  Once
   * this is created there is no more modifying attribute metadata, its all set.
   */
  private static ImmutableMap<RootKey, AttributeMetadataRegistry>
      buildAttributes(MetadataRegistryBuilder registryBuilder,
          MetadataRegistry registry) {
    Builder<RootKey, AttributeMetadataRegistry> attributeBuilder
        = ImmutableMap.builder();
    for (Map.Entry<RootKey, AttributeMetadataRegistryBuilder> entry
        : registryBuilder.getAttributes().entrySet()) {
      attributeBuilder.put(entry.getKey(), entry.getValue().create(registry));
    }
    return attributeBuilder.build();
  }

  /**
   * Creates the immutable map of element key -> element registry.  Once this
   * is created there is no more modifying element metadata, its all set.
   */
  private static ImmutableMap<RootKey, ElementMetadataRegistry>
      buildElements(MetadataRegistryBuilder registryBuilder,
          MetadataRegistry registry) {
    Builder<RootKey, ElementMetadataRegistry> elementBuilder
        = ImmutableMap.builder();
    for (Map.Entry<RootKey, ElementMetadataRegistryBuilder> entry
        : registryBuilder.getElements().entrySet()) {
      elementBuilder.put(entry.getKey(), entry.getValue().create(registry));
    }
    return elementBuilder.build();
  }

  /**
   * Returns the default metadata for the element key.
   */
  public <D, E extends Element> ElementMetadata<D, E> bind(
      ElementKey<D, E> key) {
    return bind(null, key, null);
  }

  /**
   * Returns the metadata for the element key bound to the context.
   */
  public <D, E extends Element> ElementMetadata<D, E> bind(
      ElementKey<D, E> key, MetadataContext context) {
    return bind(null, key, context);
  }

  /**
   * Returns the metadata for the child element in the parent.
   */
  public <D, E extends Element> ElementMetadata<D, E> bind(
      ElementKey<?, ?> parent, ElementKey<D, E> child) {
    return bind(parent, child, null);
  }

  /**
   * Returns the metadata for the child element in the parent, bound to the
   * context.
   */
  public <D, E extends Element> ElementMetadata<D, E> bind(
      ElementKey<?, ?> parent, ElementKey<D, E> child,
      MetadataContext context) {
    ElementMetadataRegistry childRegistry = getElement(child);
    return (childRegistry == null) ? null
        : childRegistry.bind(parent, child, context);
  }

  /**
   * Provides direct access to the transform for other classes in this package,
   * to avoid circular dependencies causing infinite loops.  This allows
   * interested classes to access the metadata information for a key without
   * fully binding it.
   */
  ElementTransform getTransform(ElementKey<?, ?> parent,
      ElementKey<?, ?> child, MetadataContext context) {
    ElementMetadataRegistry childRegistry = getElement(child);
    return (childRegistry == null) ? null
        : childRegistry.getTransform(parent, child, context);
  }
  
  /**
   * Gets an element from the element map by first finding the appropriate
   * root key and then indexing into the elements based on that key.
   */
  private ElementMetadataRegistry getElement(ElementKey<?, ?> key) {
    return elements.get(getRootKey(key));
  }

  /**
   * Returns the default metadata for the given attribute.
   */
  public <D> AttributeMetadata<D> bind(ElementKey<?, ?> parent,
      AttributeKey<D> attribute) {
    return bind(parent, attribute, null);
  }

  /**
   * Returns the metadata for the attribute, bound to the context.
   */
  public <D> AttributeMetadata<D> bind(ElementKey<?, ?> parent,
      AttributeKey<D> attribute, MetadataContext context) {
    AttributeMetadataRegistry attRegistry = getAttribute(attribute);
    return (attRegistry == null) ? null
        : attRegistry.bind(parent, attribute, context);
  }

  /**
   * Gets an attribute from the attribute map by first finding the appropriate
   * root key and then indexing into the attributes based on that key.
   */
  private AttributeMetadataRegistry getAttribute(AttributeKey<?> key) {
    return attributes.get(getRootKey(key));
  }

  /**
   * A root key to a particular attribute or element.  A root key represents
   * the root of an attribute or element metadata tree.  For attributes, the
   * root is based on the ID of the attribute.  For elements, if the element
   * type is {@link Element}, the root is based on the ID.  If the element type
   * is some subclass of {@code Element}, the root will be based on the first
   * class after element in the type hierarchy, and ID will be ignored.
   */
  static class RootKey {

    /**
     * Calculate the root key for a given element key based on the ID or type.
     */
    private static RootKey get(ElementKey<?, ?> key) {
      QName id = key.getId();
      Class<? extends Element> elementType = key.getElementType();
      if (elementType != Element.class) {
        Class<? extends Element> superClass = getSuper(elementType);
        while (superClass != Element.class) {
          elementType = superClass;
          superClass = getSuper(elementType);
        }

        // For element keys using a subclass of Element, index by type.
        return new RootKey(elementType);
      } else {
        // For element keys that reference Element directly, index by root ID.
        return new RootKey(getRootId(id));
      }
    }

    /**
     * Creates a root key from the given attribute key's ID.
     */
    private static RootKey get(AttributeKey<?> key) {
      return new RootKey(getRootId(key.getId()));
    }

    /**
     * Gets the root ID for a qualified name.  All names in the same namespace
     * will be stored together, to allow transforms to affect all properties in
     * a namespace through transforms on uri:*.
     */
    private static QName getRootId(QName id) {
      if (id.getNs() != null && !"*".equals(id.getLocalName())) {
        return new QName(id.getNs(), "*");
      }
      return id;
    }

    /**
     * Suppress a warning on getting the super class from something we know is
     * at least two classes away from Element.
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Element> getSuper(
        Class<? extends Element> type) {
      return (Class<? extends Element>) type.getSuperclass();
    }

    private final QName id;
    private final Class<?> type;

    private RootKey(QName id) {
      Preconditions.checkNotNull(id);
      this.id = id;
      this.type = null;
    }

    private RootKey(Class<?> type) {
      Preconditions.checkNotNull(type);
      this.id = null;
      this.type = type;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof RootKey)) {
        return false;
      }
      RootKey other = (RootKey) obj;
      if (type != null) {
        return type == other.type;
      }
      return id.equals(other.id);
    }

    @Override
    public int hashCode() {
      if (type != null) {
        return type.hashCode();
      }
      return id.hashCode();
    }

    @Override
    public String toString() {
      return (type == null) ? "{Root (" + id + ")}"
          : "{Root (" + type + ")}";
    }
  }
}
