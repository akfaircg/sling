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
package org.apache.sling.extensions.featureflags;

import aQute.bnd.annotation.ProviderType;

/**
 * The feature service is the central gateway for feature handling.
 * It can be used to query the available features and to
 * check whether a feature is enabled for the current execution
 * context.
 */
@ProviderType
public interface Feature {


    /**
     * Checks whether the feature is enabled for the given
     * execution context.
     *
     * The actual check is delegated to the {@link FeatureProvider}
     * providing the feature.
     */
    boolean isEnabled(String featureName, ExecutionContext context);

    /** Get the list of active feature flags */
    String[] getFeatureNames();

    /**
     * Checks whether a feature with the given name is available.
     * A feature is available if there is a {@link FeatureProvider}
     * for that feature.
     * @param featureName
     * @return
     */
    boolean isAvailable(String featureName);

}