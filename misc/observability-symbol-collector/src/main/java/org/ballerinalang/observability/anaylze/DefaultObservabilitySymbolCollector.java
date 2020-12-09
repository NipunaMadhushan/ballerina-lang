/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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
package org.ballerinalang.observability.anaylze;

import com.google.gson.JsonElement;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.projects.Document;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import org.ballerinalang.diagramutil.DiagramUtil;
import org.ballerinalang.observability.anaylze.model.DocumentHolder;
import org.ballerinalang.observability.anaylze.model.ModuleHolder;
import org.ballerinalang.observability.anaylze.model.PackageHolder;
import org.wso2.ballerinalang.compiler.spi.ObservabilitySymbolCollector;
import org.wso2.ballerinalang.util.RepoUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;

/**
 * Default implementation of {@link ObservabilitySymbolCollector}.
 *
 * @since 2.0.0
 */
public class DefaultObservabilitySymbolCollector implements ObservabilitySymbolCollector {

    private static final String SYNTAX_TREE_DIR = "syntax-tree";
    private static final String SYNTAX_TREE_FILE_NAME = "syntax-tree.json";
    private static final String SYNTAX_TREE_META_FILENAME = "meta.properties";

    // JSON Keys
    private static final String BALLERINA_VERSION_KEY = "ballerinaVersion";
    private static final String PACKAGE_ORG_KEY = "packageOrg";
    private static final String PACKAGE_NAME_KEY = "packageName";
    private static final String PACKAGE_VERSION_KEY = "packageVersion";
    private static final String PACKAGE_MODULES_KEY = "modules";
    private static final String MODULE_NAME_KEY = "moduleName";
    private static final String MODULE_DOCUMENTS_KEY = "documents";
    private static final String DOCUMENT_NAME_KEY = "documentName";
    private static final String DOCUMENT_SYNTAX_TREE_KEY = "syntaxTree";

    // Metadata Keys
    private static final String PROGRAM_HASH_KEY = "PROGRAM_HASH";

    private static final PrintStream out = System.out;

    private boolean isObservabilityIncluded = false;

    @Override
    public void process(Project project) {
        isObservabilityIncluded = project.buildOptions().observabilityIncluded();
        if (!isObservabilityIncluded) {
            return;
        }
        Package currentPackage = project.currentPackage();
        PackageHolder packageHolder = PackageHolder.getInstance();
        packageHolder.setOrg(currentPackage.packageOrg().toString());
        packageHolder.setName(currentPackage.packageName().toString());
        packageHolder.setVersion(currentPackage.packageVersion().toString());
        for (Module module : currentPackage.modules()) {
            SemanticModel semanticModel = module.getCompilation().getSemanticModel();
            for (Document document : module.documents()) {
                JsonElement syntaxTreeJSON = DiagramUtil.getSyntaxTreeJSON(document.syntaxTree(), semanticModel);
                packageHolder.addSyntaxTree(module.descriptor(), document.name(), syntaxTreeJSON);
            }
        }
    }

    @Override
    public void writeToExecutable(Path executableFile) throws IOException {
        if (!isObservabilityIncluded) {
            return;
        }
        try (FileSystem fs = FileSystems.newFileSystem(executableFile,
                DefaultObservabilitySymbolCollector.class.getClassLoader())) {
            Path syntaxTreeDirPath = fs.getPath(SYNTAX_TREE_DIR);
            Files.createDirectories(syntaxTreeDirPath);

            // Writing Syntax Tree Json
            String syntaxTreeDataString = generateCanonicalJsonString(PackageHolder.getInstance());
            Files.write(syntaxTreeDirPath.resolve(SYNTAX_TREE_FILE_NAME),
                    syntaxTreeDataString.getBytes(StandardCharsets.UTF_8));

            // Writing Syntax Tree Metadata
            Properties props = new Properties();
            try (OutputStream outputStream =
                         Files.newOutputStream(syntaxTreeDirPath.resolve(SYNTAX_TREE_META_FILENAME))) {
                props.setProperty(PROGRAM_HASH_KEY, getSyntaxTreeHash(syntaxTreeDataString));
                props.store(outputStream, null);
            } catch (NoSuchAlgorithmException e) {
                out.println("error: failed to store Enriched Syntax Tree Json into the Jar due to " + e.getMessage());
            }
        }
    }

    private String getSyntaxTreeHash(String syntaxTreeDataString) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedHash = digest.digest(syntaxTreeDataString.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encodedHash);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String generateCanonicalJsonString(PackageHolder packageHolder) throws IOException {
        final String ballerinaVersion = RepoUtils.getBallerinaVersion();
        StringBuilder jsonStringBuilder = new StringBuilder().append("{\"")
                .append(BALLERINA_VERSION_KEY).append("\":\"").append(ballerinaVersion).append("\",\"")
                .append(PACKAGE_ORG_KEY).append("\":\"").append(packageHolder.getOrg()).append("\",\"")
                .append(PACKAGE_NAME_KEY).append("\":\"").append(packageHolder.getName()).append("\",\"")
                .append(PACKAGE_VERSION_KEY).append("\":\"").append(packageHolder.getVersion()).append("\",\"")
                .append(PACKAGE_MODULES_KEY).append("\":{");

        String[] moduleKeys = packageHolder.getModules().keySet().toArray(new String[0]);
        Arrays.sort(moduleKeys);
        for (int i = 0, packageNamesLength = moduleKeys.length; i < packageNamesLength; i++) {
            String moduleKey = moduleKeys[i];
            ModuleHolder moduleHolder = packageHolder.getModules().get(moduleKey);

            if (i != 0) {
                jsonStringBuilder.append(",");
            }

            jsonStringBuilder.append("\"").append(moduleKey).append("\":{\"")
                    .append(MODULE_NAME_KEY).append("\":\"").append(moduleHolder.getName()).append("\",\"")
                    .append(MODULE_DOCUMENTS_KEY).append("\":{");

            String[] documentKeys = moduleHolder.getDocuments().keySet().toArray(new String[0]);
            Arrays.sort(documentKeys);
            for (int j = 0, documentNamesLength = documentKeys.length; j < documentNamesLength; j++) {
                String documentKey = documentKeys[j];
                DocumentHolder documentHolder = moduleHolder.getDocuments().get(documentKey);
                String syntaxTreeDataString = JsonCanonicalizer
                        .getEncodedString(documentHolder.getSyntaxTree().toString());

                if (j != 0) {
                    jsonStringBuilder.append(",");
                }
                jsonStringBuilder.append("\"").append(documentKey).append("\":{\"")
                        .append(DOCUMENT_NAME_KEY).append("\":\"")
                        .append(documentHolder.getDocumentName()).append("\",\"")
                        .append(DOCUMENT_SYNTAX_TREE_KEY).append("\":")
                        .append(syntaxTreeDataString)
                        .append("}");
            }
            jsonStringBuilder.append("}}");
        }
        return jsonStringBuilder.append("}}").toString();
    }
}
