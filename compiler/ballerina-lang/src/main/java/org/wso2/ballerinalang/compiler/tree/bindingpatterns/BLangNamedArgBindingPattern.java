/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler.tree.bindingpatterns;

import org.ballerinalang.model.tree.IdentifierNode;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.bindingpattern.BindingPatternNode;
import org.ballerinalang.model.tree.bindingpattern.NamedArgBindingPatternNode;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangNodeAnalyzer;
import org.wso2.ballerinalang.compiler.tree.BLangNodeTransformer;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;

/**
 * Represent named-arg-binding-pattern.
 *
 * @since 2.0.0
 */
public class BLangNamedArgBindingPattern extends BLangBindingPattern implements NamedArgBindingPatternNode {

    // BLangNodes
    public BLangIdentifier argName;
    public BLangBindingPattern bindingPattern;

    @Override
    public IdentifierNode getIdentifier() {
        return argName;
    }

    @Override
    public void setIdentifier(IdentifierNode variableName) {
        this.argName = (BLangIdentifier) variableName;
    }

    @Override
    public BindingPatternNode getBindingPattern() {
        return bindingPattern;
    }

    @Override
    public void setBindingPattern(BindingPatternNode bindingPattern) {
        this.bindingPattern = (BLangBindingPattern) bindingPattern;
    }

    @Override
    public void accept(BLangNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <T> void accept(BLangNodeAnalyzer<T> analyzer, T props) {
        analyzer.visit(this, props);
    }

    @Override
    public <T, R> R apply(BLangNodeTransformer<T, R> modifier, T props) {
        return modifier.transform(this, props);
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.NAMED_ARG_BINDING_PATTERN;
    }
}
