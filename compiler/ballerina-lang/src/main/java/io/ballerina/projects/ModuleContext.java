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
package io.ballerina.projects;

import io.ballerina.projects.environment.ModuleLoadRequest;
import io.ballerina.projects.environment.ModuleLoadResponse;
import io.ballerina.projects.environment.PackageResolver;
import io.ballerina.projects.environment.ProjectEnvironment;
import io.ballerina.projects.internal.CompilerPhaseRunner;
import io.ballerina.tools.diagnostics.Diagnostic;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.BIRPackageSymbolEnter;
import org.wso2.ballerinalang.compiler.CompiledJarFile;
import org.wso2.ballerinalang.compiler.PackageCache;
import org.wso2.ballerinalang.compiler.semantics.analyzer.SymbolEnter;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangTestablePackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.diagnotic.BDiagnosticSource;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;
import org.wso2.ballerinalang.programfile.CompiledBinaryFile;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maintains the internal state of a {@code Module} instance.
 * <p>
 * Works as a module cache.
 *
 * @since 2.0.0
 */
class ModuleContext {
    private final ModuleId moduleId;
    private final ModuleDescriptor moduleDescriptor;
    private final Collection<DocumentId> srcDocIds;
    private final boolean isDefaultModule;
    private final Map<DocumentId, DocumentContext> srcDocContextMap;
    private final Collection<DocumentId> testSrcDocIds;
    private final Map<DocumentId, DocumentContext> testDocContextMap;
    private final Project project;
    private final CompilationCache compilationCache;

    private Set<ModuleDependency> moduleDependencies;
    private BLangPackage bLangPackage;
    private BPackageSymbol bPackageSymbol;
    private byte[] birBytes = new byte[0];
    private final Bootstrap bootstrap;
    private ModuleCompilationState moduleCompState;

    ModuleContext(Project project,
                  ModuleId moduleId,
                  ModuleDescriptor moduleDescriptor,
                  boolean isDefaultModule,
                  Map<DocumentId, DocumentContext> srcDocContextMap,
                  Map<DocumentId, DocumentContext> testDocContextMap,
                  Set<ModuleDependency> moduleDependencies) {
        this.project = project;
        this.moduleId = moduleId;
        this.moduleDescriptor = moduleDescriptor;
        this.isDefaultModule = isDefaultModule;
        this.srcDocContextMap = srcDocContextMap;
        this.srcDocIds = Collections.unmodifiableCollection(srcDocContextMap.keySet());
        this.testDocContextMap = testDocContextMap;
        this.testSrcDocIds = Collections.unmodifiableCollection(testDocContextMap.keySet());
        this.moduleDependencies = Collections.unmodifiableSet(moduleDependencies);

        ProjectEnvironment projectEnvironment = project.projectEnvironmentContext();
        this.bootstrap = new Bootstrap(projectEnvironment.getService(PackageResolver.class));
        this.compilationCache = projectEnvironment.getService(CompilationCache.class);
    }

    private ModuleContext(Project project,
                          ModuleId moduleId,
                          ModuleDescriptor moduleDescriptor,
                          boolean isDefaultModule,
                          Map<DocumentId, DocumentContext> srcDocContextMap,
                          Map<DocumentId, DocumentContext> testDocContextMap) {
        this(project, moduleId, moduleDescriptor, isDefaultModule, srcDocContextMap,
                testDocContextMap, Collections.emptySet());
    }

    static ModuleContext from(Project project, ModuleConfig moduleConfig) {
        Map<DocumentId, DocumentContext> srcDocContextMap = new HashMap<>();
        for (DocumentConfig sourceDocConfig : moduleConfig.sourceDocs()) {
            srcDocContextMap.put(sourceDocConfig.documentId(), DocumentContext.from(sourceDocConfig));
        }

        Map<DocumentId, DocumentContext> testDocContextMap = new HashMap<>();
        for (DocumentConfig testSrcDocConfig : moduleConfig.testSourceDocs()) {
            testDocContextMap.put(testSrcDocConfig.documentId(), DocumentContext.from(testSrcDocConfig));
        }

        return new ModuleContext(project, moduleConfig.moduleId(),
                moduleConfig.moduleDescriptor(), moduleConfig.isDefaultModule(),
                srcDocContextMap, testDocContextMap);
    }

    ModuleId moduleId() {
        return this.moduleId;
    }

    ModuleDescriptor moduleDescriptor() {
        return moduleDescriptor;
    }

    ModuleName moduleName() {
        return moduleDescriptor.name();
    }

    Collection<DocumentId> srcDocumentIds() {
        return this.srcDocIds;
    }

    Collection<DocumentId> testSrcDocumentIds() {
        return this.testSrcDocIds;
    }

    DocumentContext documentContext(DocumentId documentId) {
        if (this.srcDocIds.contains(documentId)) {
            return this.srcDocContextMap.get(documentId);
        } else {
            return this.testDocContextMap.get(documentId);
        }
    }

    Project project() {
        return this.project;
    }

    boolean isDefaultModule() {
        return this.isDefaultModule;
    }

    Collection<ModuleDependency> dependencies() {
        return moduleDependencies;
    }

    boolean entryPointExists() {
        // TODO this is temporary method. We should remove this ASAP
        BLangPackage bLangPackage = getBLangPackageOrThrow();
        return bLangPackage.symbol.entryPointExists;
    }

    BLangPackage bLangPackage() {
        return getBLangPackageOrThrow();
    }

    CompiledJarFile compiledJarEntries() {
        BPackageSymbol packageSymbol;
        if (bLangPackage != null) {
            packageSymbol = bLangPackage.symbol;
        } else if (bPackageSymbol != null) {
            packageSymbol = bPackageSymbol;
        } else {
            throw new IllegalStateException("Compile the module first!");
        }
        return packageSymbol.compiledJarFile;
    }

    CompiledBinaryFile.BIRPackageFile bir() {
        BPackageSymbol packageSymbol;
        if (bLangPackage != null) {
            packageSymbol = bLangPackage.symbol;
        } else if (bPackageSymbol != null) {
            packageSymbol = bPackageSymbol;
        } else {
            throw new IllegalStateException("Compile the module first!");
        }
        return packageSymbol.birPackageFile;
    }

    private BLangPackage getBLangPackageOrThrow() {
        if (bLangPackage == null) {
            throw new IllegalStateException("Compile the module first!");
        }

        return bLangPackage;
    }

    /**
     * Returns the list of compilation diagnostics of this module.
     *
     * @return Returns the list of compilation diagnostics of this module
     */
    List<Diagnostic> diagnostics() {
        // Try to get the diagnostics from the bLangPackage, if the module is already compiled
        if (bLangPackage != null) {
            return bLangPackage.getDiagnostics();
        }

        return Collections.emptyList();
    }

    private void parseTestSources(BLangPackage pkgNode, PackageID pkgId, CompilerContext compilerContext) {
        BLangTestablePackage testablePkg = TreeBuilder.createTestablePackageNode();
        // TODO Not sure why we need to do this. It is there in the current implementation
        testablePkg.packageID = pkgId;
        testablePkg.flagSet.add(Flag.TESTABLE);
        // TODO Why we need two different diagnostic positions. This is how it is done in the current compiler.
        //  So I kept this as is for now.
        testablePkg.pos = new DiagnosticPos(new BDiagnosticSource(pkgId,
                this.moduleName().toString()), 1, 1, 1, 1);
        pkgNode.addTestablePkg(testablePkg);
        for (DocumentContext documentContext : testDocContextMap.values()) {
            testablePkg.addCompilationUnit(documentContext.compilationUnit(compilerContext, pkgId));
        }
    }

    private ModuleCompilationState currentCompilationState() {
        if (moduleCompState != null) {
            return moduleCompState;
        }

        // TODO This logic needs to be updated. We need a proper way to decide on the initial state
        if (compilationCache.getBir(moduleDescriptor.name()).length == 0) {
            moduleCompState = ModuleCompilationState.LOADED_FROM_SOURCES;
        } else {
            moduleCompState = ModuleCompilationState.LOADED_FROM_CACHE;
        }
        return moduleCompState;
    }

    void setCompilationState(ModuleCompilationState moduleCompState) {
        this.moduleCompState = moduleCompState;
    }

    void parse() {
        currentCompilationState().parse(this);
    }

    boolean resolveDependencies() {
        // TODO refactor the boolean return
        ModuleCompilationState moduleState = currentCompilationState();
        if (moduleState == ModuleCompilationState.DEPENDENCIES_RESOLVED_FROM_BALO ||
                moduleState == ModuleCompilationState.DEPENDENCIES_RESOLVED_FROM_SOURCES) {
            return false;
        } else {
            moduleState.resolveDependencies(this);
            return true;
        }
    }

    void compile(CompilerContext compilerContext) {
        currentCompilationState().compile(this, compilerContext);
    }

    void generatePlatformSpecificCode(CompilerContext compilerContext, CompilerBackend compilerBackend) {
        currentCompilationState().generatePlatformSpecificCode(this, compilerContext, compilerBackend);
    }

    static void parseInternal(ModuleContext moduleContext) {
        for (DocumentContext docContext : moduleContext.srcDocContextMap.values()) {
            docContext.parse();
        }
    }

    static void resolveDependenciesInternal(ModuleContext moduleContext) {
        // 1) Combine all the moduleLoadRequests of documents
        Set<ModuleLoadRequest> moduleLoadRequests = new HashSet<>();
        for (DocumentContext docContext : moduleContext.srcDocContextMap.values()) {
            moduleLoadRequests.addAll(docContext.moduleLoadRequests());
        }

        // 2) Resolve all the dependencies of this module
        PackageResolver packageResolver = moduleContext.project.projectEnvironmentContext().
                getService(PackageResolver.class);
        Collection<ModuleLoadResponse> moduleLoadResponses = packageResolver.loadPackages(moduleLoadRequests);

        // The usage of Set eliminates duplicates
        Set<ModuleDependency> moduleDependencies = new HashSet<>();
        for (ModuleLoadResponse moduleLoadResponse : moduleLoadResponses) {
            ModuleDependency moduleDependency = new ModuleDependency(
                    new PackageDependency(moduleLoadResponse.packageId()),
                    moduleLoadResponse.moduleId());
            moduleDependencies.add(moduleDependency);
        }

        moduleContext.moduleDependencies = Collections.unmodifiableSet(moduleDependencies);
    }

    static void compileInternal(ModuleContext moduleContext, CompilerContext compilerContext) {
        PackageID moduleCompilationId = moduleContext.moduleDescriptor().moduleCompilationId();
        String bootstrapLangLibName = System.getProperty("BOOTSTRAP_LANG_LIB");
        if (bootstrapLangLibName != null) {
            moduleContext.bootstrap.loadLangLib(compilerContext, moduleCompilationId);
        }

        PackageCache packageCache = PackageCache.getInstance(compilerContext);
        SymbolEnter symbolEnter = SymbolEnter.getInstance(compilerContext);
        CompilerPhaseRunner compilerPhaseRunner = CompilerPhaseRunner.getInstance(compilerContext);

        BLangPackage pkgNode = (BLangPackage) TreeBuilder.createPackageNode();
        packageCache.put(moduleCompilationId, pkgNode);

        // Parse source files
        for (DocumentContext documentContext : moduleContext.srcDocContextMap.values()) {
            pkgNode.addCompilationUnit(documentContext.compilationUnit(compilerContext, moduleCompilationId));
        }

        // Parse test source files
        // TODO use the compilerOption such as --skip-tests to enable or disable tests
        if (!moduleContext.testSrcDocumentIds().isEmpty()) {
            moduleContext.parseTestSources(pkgNode, moduleCompilationId, compilerContext);
        }

        pkgNode.pos = new DiagnosticPos(new BDiagnosticSource(moduleCompilationId,
                moduleContext.moduleName().toString()), 0, 0, 0, 0);
        symbolEnter.definePackage(pkgNode);
        packageCache.putSymbol(pkgNode.packageID, pkgNode.symbol);

        if (bootstrapLangLibName != null) {
            compilerPhaseRunner.compileLangLibs(pkgNode);
        } else {
            compilerPhaseRunner.compile(pkgNode);
        }
        moduleContext.bLangPackage = pkgNode;
    }

    static void generateCodeInternal(ModuleContext moduleContext,
                                     CompilerContext compilerContext,
                                     CompilerBackend compilerBackend) {
        // Skip the code generation phase if there diagnostics
        if (!moduleContext.diagnostics().isEmpty()) {
            return;
        }
        CompilerPhaseRunner compilerPhaseRunner = CompilerPhaseRunner.getInstance(compilerContext);
        compilerPhaseRunner.codeGen(moduleContext.moduleId, compilerBackend, moduleContext.bLangPackage);
    }

    static void loadBirBytesInternal(ModuleContext moduleContext) {
        moduleContext.birBytes = moduleContext.compilationCache.getBir(moduleContext.moduleName());
    }

    static void resolveDependenciesFromBALOInternal(ModuleContext moduleContext) {
        // TODO implement
    }

    static void loadPackageSymbolInternal(ModuleContext moduleContext, CompilerContext compilerContext) {
        PackageCache packageCache = PackageCache.getInstance(compilerContext);
        BIRPackageSymbolEnter birPackageSymbolEnter = BIRPackageSymbolEnter.getInstance(compilerContext);

        PackageID moduleCompilationId = moduleContext.moduleDescriptor().moduleCompilationId();
        moduleContext.bPackageSymbol = birPackageSymbolEnter.definePackage(
                moduleCompilationId, null, moduleContext.birBytes);
        packageCache.putSymbol(moduleCompilationId, moduleContext.bPackageSymbol);
    }

    static void loadPlatformSpecificCodeInternal(ModuleContext moduleContext, CompilerBackend compilerBackend) {
        // TODO implement
    }
}
