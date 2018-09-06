/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.cli.utils;

import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.spi.EmbeddedExecutor;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static org.ballerinalang.util.BLangConstants.COLON;
import static org.ballerinalang.util.BLangConstants.MAIN_FUNCTION_NAME;

/**
 * This represents the Ballerina package provider.
 *
 * @since 0.964
 */
@JavaSPIService("org.ballerinalang.spi.EmbeddedExecutor")
public class BVMEmbeddedExecutor implements EmbeddedExecutor {
    @Override
    public void execute(String programArg, boolean isFunction, String... args) {
        String balxPath = programArg;
        String functionName = MAIN_FUNCTION_NAME;

        if (isFunction && programArg.contains(COLON)) {
            String[] programArgConstituents = programArg.split(COLON);
            functionName = programArgConstituents[programArgConstituents.length - 1];
            if (functionName.isEmpty() || programArg.endsWith(COLON)) {
                throw new BLangCompilerException("usage error: expected function name after final ':'");
            }
            balxPath = programArg.replace(COLON.concat(functionName), "");
        }

        URL resource = BVMEmbeddedExecutor.class.getClassLoader()
                                                .getResource("META-INF/ballerina/" + balxPath);
        if (resource == null) {
            throw new BLangCompilerException("Missing internal modules when retrieving remote package");
        }
        try {
            URI balxResource = resource.toURI();
            // TODO: 8/5/18 return BValue[] ignored 
            ExecutorUtils.execute(balxResource, isFunction, isFunction ? functionName : null, args);
        } catch (URISyntaxException e) {
            throw new BLangCompilerException("Error loading internal modules when retrieving remote package");
        }
    }
}
