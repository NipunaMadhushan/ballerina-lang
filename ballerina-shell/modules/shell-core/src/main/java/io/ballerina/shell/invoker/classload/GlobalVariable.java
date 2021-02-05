/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.shell.invoker.classload;

import java.util.Collection;
import java.util.Objects;

/**
 * A global variable in the REPL.
 * Needs information such as variable name, its code and
 * whether it is an any/error/(any|error)
 *
 * @since 2.0.0
 */
public class GlobalVariable {
    private final String type;
    private final QuotedIdentifier variableName;
    private final ElevatedType elevatedType;
    private final String qualifiersAndMetadata;

    public GlobalVariable(String type, QuotedIdentifier variableName,
                          ElevatedType elevatedType, String qualifiersAndMetadata) {
        this.type = Objects.requireNonNull(type);
        this.elevatedType = Objects.requireNonNull(elevatedType);
        this.qualifiersAndMetadata = Objects.requireNonNull(qualifiersAndMetadata);
        this.variableName = variableName;
    }

    private GlobalVariable(QuotedIdentifier variableName) {
        // This constructor is only used to create a temp global
        // variable to search in a hashmap using the variable name.
        this.type = null;
        this.variableName = variableName;
        this.elevatedType = null;
        this.qualifiersAndMetadata = null;
    }

    /**
     * Function to check whether a variable name is defined inside a collection.
     *
     * @param globalVariables Global variable collection to search.
     * @param variableName    Variable name to search.
     * @return Whether the variable is contained inside the collection.
     */
    public static boolean isDefined(Collection<GlobalVariable> globalVariables, QuotedIdentifier variableName) {
        return globalVariables.contains(new GlobalVariable(variableName));
    }

    public String getType() {
        return type;
    }

    public QuotedIdentifier getVariableName() {
        return variableName;
    }

    public ElevatedType getElevatedType() {
        return elevatedType;
    }

    public String getQualifiersAndMetadata() {
        return qualifiersAndMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GlobalVariable that = (GlobalVariable) o;
        return variableName.equals(that.variableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableName);
    }

    @Override
    public String toString() {
        return String.format("<%s> %s %s %s", elevatedType, qualifiersAndMetadata, type, variableName);
    }
}
