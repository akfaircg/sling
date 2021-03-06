/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.resourcemerger.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceProvider;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ValueMap;

/**
 * The <code>MergedResourceProvider</code> is the resource provider providing
 * access to {@link MergedResource} objects.
 */
public class MergedResourceProvider
    implements ResourceProvider {

    private final String mergeRootPath;

    public MergedResourceProvider(final String mergeRootPath) {
        this.mergeRootPath = mergeRootPath;
    }

    /**
     * {@inheritDoc}
     */
    public Resource getResource(final ResourceResolver resolver, final HttpServletRequest request, final String path) {
        return getResource(resolver, path);
    }

    private static final class ParentHidingHandler {

        private final String[] childrenToHideArray;

        public ParentHidingHandler(final Resource parent) {
            if ( parent == null ) {
                this.childrenToHideArray = null;
            } else {
                final ValueMap parentProps = parent.getValueMap();
                this.childrenToHideArray = parentProps.get(MergedResourceConstants.PN_HIDE_CHILDREN, String[].class);
            }
        }

        public boolean isHidden(final String name) {
            boolean hidden = false;
            if ( this.childrenToHideArray != null ) {
                for(final String entry : childrenToHideArray) {
                    if ( entry.equals("*") || entry.equals(name) ) {
                        hidden = true;
                        break;
                    }
                }
            }
            return hidden;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Resource getResource(final ResourceResolver resolver, final String path) {
        final String relativePath = getRelativePath(path);

        if ( relativePath != null ) {
            final ResourceHolder holder = new ResourceHolder(ResourceUtil.getName(path));

            // Loop over provided base paths, start with least import
            final String[] searchPaths = resolver.getSearchPath();
            for(int i=searchPaths.length-1; i >= 0; i--) {
                final String basePath = searchPaths[i];

                // Try to get the corresponding physical resource for this base path
                final String fullPath = basePath + relativePath;

                // check parent for hiding
                // SLING 3521 : if parent is not readable, nothing is hidden
                final Resource parent = resolver.getResource(ResourceUtil.getParent(fullPath));
                final boolean hidden = new ParentHidingHandler(parent).isHidden(holder.name);
                if ( hidden ) {
                    holder.resources.clear();
                } else {
                    final Resource baseRes = resolver.getResource(fullPath);
                    if (baseRes != null) {
                        holder.resources.add(baseRes);
                    }
                }
            }
            return createMergedResource(resolver, relativePath, holder);
        }

        return null;
    }

    private static final class ResourceHolder {
        public final String name;
        public final List<Resource> resources = new ArrayList<Resource>();
        public final List<ValueMap> valueMaps = new ArrayList<ValueMap>();

        public ResourceHolder(final String n) {
            this.name = n;
        }
    }

    /**
     * Create the merged resource based on the provided resources
     */
    private Resource createMergedResource(final ResourceResolver resolver,
            final String relativePath,
            final ResourceHolder holder) {
        int index = 0;
        while ( index < holder.resources.size() ) {
            final Resource baseRes = holder.resources.get(index);
            // check if resource is hidden
            final ValueMap props = baseRes.getValueMap();
            holder.valueMaps.add(props);
            if ( props.get(MergedResourceConstants.PN_HIDE_RESOURCE, Boolean.FALSE) ) {
                // clear everything up to now
                for(int i=0;i<=index;i++) {
                    holder.resources.remove(0);
                }
                holder.valueMaps.clear();
                index = 0; // start at zero
            } else {
                index++;
            }
        }

        if (!holder.resources.isEmpty()) {
            // create a new merged resource based on the list of mapped physical resources
            return new MergedResource(resolver, mergeRootPath, relativePath, holder.resources, holder.valueMaps);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Iterator<Resource> listChildren(final Resource resource) {
        final ResourceResolver resolver = resource.getResourceResolver();

        final String relativePath = getRelativePath(resource.getPath());

        if ( relativePath != null ) {
            final List<ResourceHolder> candidates = new ArrayList<ResourceHolder>();

            // Loop over provided base paths, start with least import
            final String[] searchPaths = resolver.getSearchPath();
            for(int i=searchPaths.length-1; i >= 0; i--) {
                final String basePath = searchPaths[i];
                final Resource parentResource = resolver.getResource(basePath + relativePath);
                if ( parentResource != null ) {
                    final ParentHidingHandler handler = new ParentHidingHandler(parentResource);
                    for(final Resource child : parentResource.getChildren()) {
                        final String rsrcName = child.getName();
                        ResourceHolder holder = null;
                        for(final ResourceHolder current : candidates) {
                            if ( current.name.equals(rsrcName) ) {
                                holder = current;
                                break;
                            }
                        }
                        if ( holder == null ) {
                            holder = new ResourceHolder(rsrcName);
                            candidates.add(holder);
                        }
                        holder.resources.add(child);

                        // Check if children need reordering
                        int orderBeforeIndex = -1;
                        final ValueMap vm = child.getValueMap();
                        final String orderBefore = vm.get(MergedResourceConstants.PN_ORDER_BEFORE, String.class);
                        if (orderBefore != null && !orderBefore.equals(rsrcName)) {
                            // search entry
                            int index = 0;
                            while (index < candidates.size()) {
                                final ResourceHolder current = candidates.get(index);
                                if ( current.name.equals(orderBefore) ) {
                                    orderBeforeIndex = index;
                                    break;
                                }
                                index++;
                            }
                        }

                        if (orderBeforeIndex > -1) {
                            candidates.add(orderBeforeIndex, holder);
                            candidates.remove(candidates.size() - 1);
                        }
                    }
                    final Iterator<ResourceHolder> iter = candidates.iterator();
                    while ( iter.hasNext() ) {
                        final ResourceHolder holder = iter.next();
                        if ( handler.isHidden(holder.name) ) {
                            iter.remove();
                        }
                    }
                }
            }
            final List<Resource> children = new ArrayList<Resource>();
            for(final ResourceHolder holder : candidates) {
                final Resource mergedResource = this.createMergedResource(resolver, (relativePath.length() == 0 ? holder.name : relativePath + '/' + holder.name), holder);
                if ( mergedResource != null ) {
                    children.add(mergedResource);
                }
            }
            return children.iterator();
        }

        return null;
    }

    /**
     * Gets the relative path out of merge root path
     *
     * @param path Absolute path
     * @return Relative path
     */
    private String getRelativePath(String path) {
        if ( path.startsWith(mergeRootPath) ) {
            path = path.substring(mergeRootPath.length());
            if ( path.length() == 0 ) {
                return path;
            } else if ( path.charAt(0) == '/' ) {
                return path.substring(1);
            }
        }
        return null;
    }
}
