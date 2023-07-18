/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.ballerina.compiler.api.impl.symbols.resourcepath;

import io.ballerina.compiler.api.impl.SymbolFactory;
import io.ballerina.compiler.api.symbols.PathParameterSymbol;
import io.ballerina.compiler.api.symbols.resourcepath.PathSegmentList;
import io.ballerina.compiler.api.symbols.resourcepath.util.PathSegment;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BResourcePathSegmentSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Name;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Represents an implementation of a path segment list.
 *
 * @since 2.0.0
 */
public class BallerinaPathSegmentList implements PathSegmentList {

    private final List<BVarSymbol> internalPathParams;
    private final BVarSymbol internalPathRestParam;
    private final List<BResourcePathSegmentSymbol> internalPathSegmentSymbols;
    private final CompilerContext context;

    private List<PathParameterSymbol> pathParams;
    private PathParameterSymbol pathRestParam;
    private List<PathSegment> resourcePathSymbols;
    private String signature;

    public BallerinaPathSegmentList(List<BResourcePathSegmentSymbol> resourcePathSymbols, List<BVarSymbol> pathParams,
                                    BVarSymbol pathRestParam, CompilerContext context) {
        this.internalPathSegmentSymbols = resourcePathSymbols;
        this.internalPathParams = pathParams;
        this.internalPathRestParam = pathRestParam;
        this.context = context;
    }

    @Override
    public List<PathParameterSymbol> pathParameters() {
        if (this.pathParams != null) {
            return this.pathParams;
        }

        SymbolFactory symbolFactory = SymbolFactory.getInstance(this.context);
        List<PathParameterSymbol> pathParams = new ArrayList<>();

        int internalPathParamCount = 0;
        List<Name> segments = this.internalPathSegmentSymbols.stream().map(s -> s.name).collect(Collectors.toList());
        for (int i = 0; i < segments.size(); i++) {
            Name internalSegment = segments.get(i);
            switch (internalSegment.value) {
                case "^":
                    pathParams.add(symbolFactory.createPathParamSymbol(
                            this.internalPathParams.get(internalPathParamCount++), PathSegment.Kind.PATH_PARAMETER));
                    break;
                case "$^":
                    pathParams.add(
                            symbolFactory.createPathParamSymbol(this.internalPathSegmentSymbols.get(i),
                                    PathSegment.Kind.PATH_PARAMETER, true)
                    );
                    break;
                default:
                    break;
            }
        }

        this.pathParams = Collections.unmodifiableList(pathParams);
        return this.pathParams;
    }

    @Override
    public Optional<PathParameterSymbol> pathRestParameter() {
        if (this.pathRestParam != null) {
            return Optional.of(this.pathRestParam);
        }

        SymbolFactory symbolFactory = SymbolFactory.getInstance(this.context);
        this.pathRestParam = symbolFactory.createPathParamSymbol(this.internalPathRestParam,
                                                                 PathSegment.Kind.PATH_REST_PARAMETER);
        return Optional.ofNullable(this.pathRestParam);
    }

    @Override
    public List<PathSegment> list() {
        if (this.resourcePathSymbols != null) {
            return this.resourcePathSymbols;
        }

        List<PathParameterSymbol> pathParams = this.pathParameters();
        List<PathSegment> segments = new ArrayList<>();

        SymbolFactory symbolFactory = SymbolFactory.getInstance(this.context);

        for (int i = 0, pathParamCount = 0, pathSegmentCount = this.internalPathSegmentSymbols.size();
             i < pathSegmentCount; i++) {
            BResourcePathSegmentSymbol pathSegmentSymbol = this.internalPathSegmentSymbols.get(i);
            PathSegment segment;
            switch (pathSegmentSymbol.getName().getValue()) {
                case "$^":
                case "^":
                    segment = pathParams.get(pathParamCount++);
                    break;
                case "^^":
                    segment = pathRestParameter().get();
                    break;
                default:
                    segment = symbolFactory.createResourceNamedPathSegment(pathSegmentSymbol);
            }
            segments.add(segment);
        }

        this.resourcePathSymbols = Collections.unmodifiableList(segments);
        return this.resourcePathSymbols;
    }

    @Override
    public Kind kind() {
        return Kind.PATH_SEGMENT_LIST;
    }

    @Override
    public String signature() {
        if (this.signature != null) {
            return this.signature;
        }

        StringJoiner stringJoiner = new StringJoiner("/");
        List<PathSegment> segments = list();
        
        for (PathSegment segment : segments) {
            stringJoiner.add(segment.signature());
        }

        this.signature = stringJoiner.toString();
        return this.signature;
    }
}
