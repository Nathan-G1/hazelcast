/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.sql.impl.exec.agg;

import java.util.HashSet;
import java.util.Set;

/**
 * Stored value of a single aggregate.
 */
public abstract class AggregateCollector {
    /** Set of distinct values. */
    private final Set<Object> distinctSet;

    protected AggregateCollector(boolean distinct) {
        this.distinctSet = distinct ? new HashSet<>() : null;
    }

    public void collect(Object value) {
        if (distinctSet != null && !distinctSet.add(value)) {
            return;
        }

        collect0(value);
    }

    protected abstract void collect0(Object value);

    public abstract Object reduce();

    // TODO: Not needed on the general interface level.
    public abstract void reset();
}