/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.ballerinalang.compiler.semantics.analyzer;

import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.symbols.SymbolKind;
import org.ballerinalang.model.tree.NodeKind;
import org.ballerinalang.model.tree.OperatorKind;
import org.ballerinalang.model.types.TypeKind;
import org.ballerinalang.util.diagnostic.DiagnosticCode;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BFutureType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BMapType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BRecordType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTupleType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotation;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotationAttachment;
import org.wso2.ballerinalang.compiler.tree.BLangCompilationUnit;
import org.wso2.ballerinalang.compiler.tree.BLangEndpoint;
import org.wso2.ballerinalang.compiler.tree.BLangFunction;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangImportPackage;
import org.wso2.ballerinalang.compiler.tree.BLangInvokableNode;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangNodeVisitor;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangRecordVariable;
import org.wso2.ballerinalang.compiler.tree.BLangRecordVariable.BLangRecordVariableKeyValue;
import org.wso2.ballerinalang.compiler.tree.BLangResource;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTupleVariable;
import org.wso2.ballerinalang.compiler.tree.BLangTypeDefinition;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.BLangWorker;
import org.wso2.ballerinalang.compiler.tree.BLangXMLNS;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangArrayLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangArrowFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBinaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangBracedOrTupleExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangCheckedExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangConstant;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangElvisExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangErrorConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangErrorVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangFieldBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIndexBasedAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangIntRangeExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLambdaFunction;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMatchExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangMatchExpression.BLangMatchExprPatternClause;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNamedArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral.BLangRecordKeyValue;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRestArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangServiceConstructorExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangStringTemplateLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTableLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTableQueryExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTernaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTrapExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTupleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeConversionExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeTestExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypedescExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangUnaryExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWaitExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWaitForAllExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWorkerFlushExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWorkerReceive;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangWorkerSyncSendExpr;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLAttribute;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLAttributeAccess;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLCommentLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLElementLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLProcInsLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLQName;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLQuotedString;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangXMLTextLiteral;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAbort;
import org.wso2.ballerinalang.compiler.tree.statements.BLangAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBreak;
import org.wso2.ballerinalang.compiler.tree.statements.BLangCatch;
import org.wso2.ballerinalang.compiler.tree.statements.BLangCompoundAssignment;
import org.wso2.ballerinalang.compiler.tree.statements.BLangContinue;
import org.wso2.ballerinalang.compiler.tree.statements.BLangErrorDestructure;
import org.wso2.ballerinalang.compiler.tree.statements.BLangErrorVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangExpressionStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForeach;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForever;
import org.wso2.ballerinalang.compiler.tree.statements.BLangForkJoin;
import org.wso2.ballerinalang.compiler.tree.statements.BLangIf;
import org.wso2.ballerinalang.compiler.tree.statements.BLangLock;
import org.wso2.ballerinalang.compiler.tree.statements.BLangMatch;
import org.wso2.ballerinalang.compiler.tree.statements.BLangMatch.BLangMatchStaticBindingPatternClause;
import org.wso2.ballerinalang.compiler.tree.statements.BLangMatch.BLangMatchStructuredBindingPatternClause;
import org.wso2.ballerinalang.compiler.tree.statements.BLangPanic;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRecordDestructure;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRecordVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangRetry;
import org.wso2.ballerinalang.compiler.tree.statements.BLangReturn;
import org.wso2.ballerinalang.compiler.tree.statements.BLangSimpleVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangStatement;
import org.wso2.ballerinalang.compiler.tree.statements.BLangThrow;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTryCatchFinally;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTupleDestructure;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTupleVariableDef;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWhile;
import org.wso2.ballerinalang.compiler.tree.statements.BLangWorkerSend;
import org.wso2.ballerinalang.compiler.tree.statements.BLangXMLNSStatement;
import org.wso2.ballerinalang.compiler.tree.types.BLangArrayType;
import org.wso2.ballerinalang.compiler.tree.types.BLangBuiltInRefTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangConstrainedType;
import org.wso2.ballerinalang.compiler.tree.types.BLangErrorType;
import org.wso2.ballerinalang.compiler.tree.types.BLangFunctionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangObjectTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangRecordTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangTupleTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangUnionTypeNode;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;
import org.wso2.ballerinalang.compiler.tree.types.BLangValueType;
import org.wso2.ballerinalang.compiler.util.BArrayState;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.compiler.util.diagnotic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;
import org.wso2.ballerinalang.programfile.WorkerDataChannelInfo;
import org.wso2.ballerinalang.util.Flags;
import org.wso2.ballerinalang.util.Lists;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.wso2.ballerinalang.compiler.tree.BLangInvokableNode.DEFAULT_WORKER_NAME;
import static org.wso2.ballerinalang.compiler.util.Constants.MAIN_FUNCTION_NAME;
import static org.wso2.ballerinalang.compiler.util.Constants.WORKER_LAMBDA_VAR_PREFIX;

/**
 * This represents the code analyzing pass of semantic analysis.
 * <p>
 * The following validations are done here:-
 * <p>
 * (*) Loop continuation statement validation.
 * (*) Function return path existence and unreachable code validation.
 * (*) Worker send/receive validation.
 */
public class CodeAnalyzer extends BLangNodeVisitor {

    private static final CompilerContext.Key<CodeAnalyzer> CODE_ANALYZER_KEY =
            new CompilerContext.Key<>();

    private final SymbolResolver symResolver;
    private int loopCount;
    private int transactionCount;
    private boolean statementReturns;
    private boolean lastStatement;
    private boolean withinRetryBlock;
    private int workerCount;
    private SymbolTable symTable;
    private Types types;
    private BLangDiagnosticLog dlog;
    private TypeChecker typeChecker;
    private Stack<WorkerActionSystem> workerActionSystemStack = new Stack<>();
    private Stack<Boolean> loopWithintransactionCheckStack = new Stack<>();
    private Stack<Boolean> returnWithintransactionCheckStack = new Stack<>();
    private Stack<Boolean> doneWithintransactionCheckStack = new Stack<>();
    private BLangNode parent;
    private Names names;
    private SymbolEnv env;
    private final Stack<HashSet<BType>> returnTypes = new Stack<>();
    private boolean withinAbortedBlock;
    private boolean withinCommittedBlock;

    public static CodeAnalyzer getInstance(CompilerContext context) {
        CodeAnalyzer codeGenerator = context.get(CODE_ANALYZER_KEY);
        if (codeGenerator == null) {
            codeGenerator = new CodeAnalyzer(context);
        }
        return codeGenerator;
    }

    public CodeAnalyzer(CompilerContext context) {
        context.put(CODE_ANALYZER_KEY, this);
        this.symTable = SymbolTable.getInstance(context);
        this.types = Types.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
        this.typeChecker = TypeChecker.getInstance(context);
        this.names = Names.getInstance(context);
        this.symResolver = SymbolResolver.getInstance(context);
    }

    private void resetFunction() {
        this.resetStatementReturns();
    }

    private void resetStatementReturns() {
        this.statementReturns = false;
    }

    private void resetLastStatement() {
        this.lastStatement = false;
    }

    public BLangPackage analyze(BLangPackage pkgNode) {
        pkgNode.accept(this);
        return pkgNode;
    }

    @Override
    public void visit(BLangPackage pkgNode) {
        if (pkgNode.completedPhases.contains(CompilerPhase.CODE_ANALYZE)) {
            return;
        }
        parent = pkgNode;
        SymbolEnv pkgEnv = this.symTable.pkgEnvMap.get(pkgNode.symbol);
        analyzeTopLevelNodes(pkgNode, pkgEnv);
        pkgNode.getTestablePkgs().forEach(testablePackage -> visit((BLangPackage) testablePackage));
    }

    private void analyzeTopLevelNodes(BLangPackage pkgNode, SymbolEnv pkgEnv) {
        pkgNode.topLevelNodes.forEach(topLevelNode -> analyzeNode((BLangNode) topLevelNode, pkgEnv));
        pkgNode.completedPhases.add(CompilerPhase.CODE_ANALYZE);
        parent = null;
    }

    private void analyzeNode(BLangNode node, SymbolEnv env) {
        SymbolEnv prevEnv = this.env;
        this.env = env;
        BLangNode myParent = parent;
        node.parent = parent;
        parent = node;
        node.accept(this);
        parent = myParent;
        this.env = prevEnv;
    }

    @Override
    public void visit(BLangCompilationUnit compUnitNode) {
        compUnitNode.topLevelNodes.forEach(e -> analyzeNode((BLangNode) e, env));
    }

    public void visit(BLangTypeDefinition typeDefinition) {
        if (typeDefinition.typeNode.getKind() == NodeKind.OBJECT_TYPE
                || typeDefinition.typeNode.getKind() == NodeKind.RECORD_TYPE) {
            analyzeNode(typeDefinition.typeNode, this.env);
        }
        if (!Symbols.isPublic(typeDefinition.symbol) ||
                typeDefinition.symbol.type != null && TypeKind.FINITE.equals(typeDefinition.symbol.type.getKind())) {
            return;
        }
        analyseType(typeDefinition.symbol.type, typeDefinition.pos);
    }

    @Override
    public void visit(BLangTupleVariableDef bLangTupleVariableDef) {
        // ignore
    }

    @Override
    public void visit(BLangRecordVariableDef bLangRecordVariableDef) {
        // ignore
    }

    @Override
    public void visit(BLangErrorVariableDef bLangErrorVariableDef) {
        // ignore
    }

    @Override
    public void visit(BLangFunction funcNode) {
        boolean isLambda = funcNode.flagSet.contains(Flag.LAMBDA);
        if (isLambda) {
            return;
        }

        this.validateMainFunction(funcNode);
        try {

            this.initNewWorkerActionSystem();
            this.workerActionSystemStack.peek().startWorkerActionStateMachine(DEFAULT_WORKER_NAME,
                                                                              funcNode.pos,
                                                                              funcNode);
            this.visitFunction(funcNode);
            this.workerActionSystemStack.peek().endWorkerActionStateMachine();
        } finally {
            this.finalizeCurrentWorkerActionSystem();
        }
    }

    private void visitFunction(BLangFunction funcNode) {
        SymbolEnv invokableEnv = SymbolEnv.createFunctionEnv(funcNode, funcNode.symbol.scope, env);
        this.returnWithintransactionCheckStack.push(true);
        this.doneWithintransactionCheckStack.push(true);
        this.returnTypes.push(new HashSet<>());
        this.resetFunction();
        if (Symbols.isNative(funcNode.symbol)) {
            return;
        }
        boolean invokableReturns = funcNode.returnTypeNode.type != symTable.nilType;
        if (isPublicInvokableNode(funcNode)) {
            analyzeNode(funcNode.returnTypeNode, invokableEnv);
        }
        /* the body can be null in the case of Object type function declarations */
        if (funcNode.body != null) {
            analyzeNode(funcNode.body, invokableEnv);
            /* the function returns, but none of the statements surely returns */
            if (invokableReturns && !this.statementReturns) {
                this.dlog.error(funcNode.pos, DiagnosticCode.INVOKABLE_MUST_RETURN,
                        funcNode.getKind().toString().toLowerCase());
            }
        }
        this.returnTypes.pop();
        this.returnWithintransactionCheckStack.pop();
        this.doneWithintransactionCheckStack.pop();
    }

    private boolean isPublicInvokableNode(BLangInvokableNode invNode) {
        return Symbols.isPublic(invNode.symbol) && (SymbolKind.PACKAGE.equals(invNode.symbol.owner.getKind()) ||
                Symbols.isPublic(invNode.symbol.owner));
    }

    @Override
    public void visit(BLangForkJoin forkJoin) {
         /* ignore */
    }

    @Override
    public void visit(BLangWorker worker) {
        /* ignore, remove later */
    }

    @Override
    public void visit(BLangEndpoint endpointNode) {
    }

    @Override
    public void visit(BLangTransaction transactionNode) {
        this.checkStatementExecutionValidity(transactionNode);
        //Check whether transaction is within a handler function or retry block. This can check for single level only.
        // We need data flow analysis to check for further levels.
        if (!isValidTransactionBlock()) {
            this.dlog.error(transactionNode.pos, DiagnosticCode.TRANSACTION_CANNOT_BE_USED_WITHIN_HANDLER);
            return;
        }
        this.loopWithintransactionCheckStack.push(false);
        this.returnWithintransactionCheckStack.push(false);
        this.doneWithintransactionCheckStack.push(false);
        this.transactionCount++;
        if (this.transactionCount > 1) {
            this.dlog.error(transactionNode.pos, DiagnosticCode.NESTED_TRANSACTIONS_ARE_INVALID);
        }
        analyzeNode(transactionNode.transactionBody, env);
        this.transactionCount--;
        this.resetLastStatement();
        if (transactionNode.onRetryBody != null) {
            this.withinRetryBlock = true;
            analyzeNode(transactionNode.onRetryBody, env);
            this.resetStatementReturns();
            this.resetLastStatement();
            this.withinRetryBlock = false;
        }

        if (transactionNode.abortedBody != null) {
            this.withinAbortedBlock = true;
            analyzeNode(transactionNode.abortedBody, env);
            this.resetStatementReturns();
            this.resetLastStatement();
            this.withinAbortedBlock = false;
        }

        if (transactionNode.committedBody != null) {
            this.withinCommittedBlock = true;
            analyzeNode(transactionNode.committedBody, env);
            this.resetStatementReturns();
            this.resetLastStatement();
            this.withinCommittedBlock = false;
        }

        this.returnWithintransactionCheckStack.pop();
        this.loopWithintransactionCheckStack.pop();
        this.doneWithintransactionCheckStack.pop();
        analyzeExpr(transactionNode.retryCount);
    }

    @Override
    public void visit(BLangAbort abortNode) {
        if (this.transactionCount == 0) {
            this.dlog.error(abortNode.pos, DiagnosticCode.ABORT_CANNOT_BE_OUTSIDE_TRANSACTION_BLOCK);
            return;
        }
        this.lastStatement = true;
    }

    @Override
    public void visit(BLangRetry retryNode) {
        if (this.transactionCount == 0) {
            this.dlog.error(retryNode.pos, DiagnosticCode.RETRY_CANNOT_BE_OUTSIDE_TRANSACTION_BLOCK);
            return;
        }
        this.lastStatement = true;
    }

    private void checkUnreachableCode(BLangStatement stmt) {
        if (this.statementReturns) {
            this.dlog.error(stmt.pos, DiagnosticCode.UNREACHABLE_CODE);
            this.resetStatementReturns();
        }
        if (lastStatement) {
            this.dlog.error(stmt.pos, DiagnosticCode.UNREACHABLE_CODE);
            this.resetLastStatement();
        }
    }

    private void checkStatementExecutionValidity(BLangStatement stmt) {
        this.checkUnreachableCode(stmt);
    }

    @Override
    public void visit(BLangBlockStmt blockNode) {
        final SymbolEnv blockEnv = SymbolEnv.createBlockEnv(blockNode, env);
        blockNode.stmts.forEach(e -> analyzeNode(e, blockEnv));
        this.resetLastStatement();
    }

    @Override
    public void visit(BLangReturn returnStmt) {
        this.checkStatementExecutionValidity(returnStmt);

        if (checkReturnValidityInTransaction()) {
            this.dlog.error(returnStmt.pos, DiagnosticCode.RETURN_CANNOT_BE_USED_TO_EXIT_TRANSACTION);
            return;
        }
        this.statementReturns = true;
        analyzeExpr(returnStmt.expr);
        this.returnTypes.peek().add(returnStmt.expr.type);
    }

    @Override
    public void visit(BLangIf ifStmt) {
        this.checkStatementExecutionValidity(ifStmt);
        analyzeNode(ifStmt.body, env);
        boolean ifStmtReturns = this.statementReturns;
        this.resetStatementReturns();
        if (ifStmt.elseStmt != null) {
            analyzeNode(ifStmt.elseStmt, env);
            this.statementReturns = ifStmtReturns && this.statementReturns;
        }
        analyzeExpr(ifStmt.expr);
    }

    @Override
    public void visit(BLangMatch matchStmt) {
        analyzeExpr(matchStmt.expr);

        boolean staticLastPattern = false;
        if (!matchStmt.getStaticPatternClauses().isEmpty()) {
            staticLastPattern = analyzeStaticMatchPatterns(matchStmt);
        }

        boolean structuredLastPattern = false;
        if (!matchStmt.getStructuredPatternClauses().isEmpty()) {
            structuredLastPattern = analyzeStructuredMatchPatterns(matchStmt);
        }

        if (!matchStmt.getPatternClauses().isEmpty()) {
            analyzeMatchedPatterns(matchStmt, staticLastPattern, structuredLastPattern);
        }
    }

    private void analyzeMatchedPatterns(BLangMatch matchStmt, boolean staticLastPattern,
                                        boolean structuredLastPattern) {
        if (staticLastPattern && structuredLastPattern) {
            dlog.error(matchStmt.pos, DiagnosticCode.MATCH_STMT_CONTAINS_TWO_DEFAULT_PATTERNS);
        }
        // Execute the following block if there are no unmatched expression types
        if (staticLastPattern || structuredLastPattern) {
            if (matchStmt.getPatternClauses().size() == 1) {
                dlog.error(matchStmt.getPatternClauses().get(0).pos, DiagnosticCode.MATCH_STMT_PATTERN_ALWAYS_MATCHES);
            }
            this.checkStatementExecutionValidity(matchStmt);
            boolean matchStmtReturns = true;
            for (BLangMatch.BLangMatchBindingPatternClause patternClause : matchStmt.getPatternClauses()) {
                analyzeNode(patternClause.body, env);
                matchStmtReturns = matchStmtReturns && this.statementReturns;
                this.resetStatementReturns();
            }
            this.statementReturns = matchStmtReturns;
        }
    }

    private boolean analyzeStructuredMatchPatterns(BLangMatch matchStmt) {
        if (matchStmt.exprTypes.isEmpty()) {
            return false;
        }

        return analyseStructuredBindingPatterns(matchStmt.getStructuredPatternClauses());
    }

    /**
     * This method is used to check the isLike test in a static match pattern.
     * @param matchStmt the match statment containing static match patterns.
     */
    private boolean analyzeStaticMatchPatterns(BLangMatch matchStmt) {
        if (matchStmt.exprTypes.isEmpty()) {
            return false;
        }
        List<BLangMatchStaticBindingPatternClause> matchedPatterns = new ArrayList<>();
        for (BLangMatchStaticBindingPatternClause pattern : matchStmt.getStaticPatternClauses()) {
            List<BType> matchedExpTypes = matchStmt.exprTypes
                    .stream()
                    .filter(exprType -> isValidStaticMatchPattern(exprType, pattern.literal))
                    .collect(Collectors.toList());

            if (matchedExpTypes.isEmpty()) {
                // log error if a pattern will not match to any of the expected types
                dlog.error(pattern.pos, DiagnosticCode.MATCH_STMT_UNMATCHED_PATTERN);
                continue;
            }

            matchedPatterns.add(pattern);
        }

        if (matchedPatterns.isEmpty()) {
            return false;
        }

        return analyzeStaticPatterns(matchedPatterns);
    }

    private boolean analyzeStaticPatterns(List<BLangMatchStaticBindingPatternClause> matchedPatterns) {
        BLangMatchStaticBindingPatternClause finalPattern = matchedPatterns.get(matchedPatterns.size() - 1);
        if (finalPattern.literal.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
            finalPattern.isLastPattern = true;
        }

        for (int i = 0; i < matchedPatterns.size() - 1; i++) {
            BLangExpression precedingPattern = matchedPatterns.get(i).literal;
            for (int j = i + 1; j < matchedPatterns.size(); j++) {
                BLangExpression pattern = matchedPatterns.get(j).literal;
                if (checkLiteralSimilarity(precedingPattern, pattern)) {
                    dlog.error(pattern.pos, DiagnosticCode.MATCH_STMT_UNREACHABLE_PATTERN);
                    matchedPatterns.remove(j--);
                }
            }
        }
        return finalPattern.isLastPattern;
    }

    private boolean analyseStructuredBindingPatterns(List<BLangMatchStructuredBindingPatternClause> clauses) {
        BLangMatchStructuredBindingPatternClause finalPattern = clauses.get(clauses.size() - 1);
        if (finalPattern.bindingPatternVariable.getKind() == NodeKind.VARIABLE && finalPattern.typeGuardExpr == null) {
            finalPattern.isLastPattern = true;
        }

        for (int i = 0; i < clauses.size(); i++) {
            for (int j = i + 1; j < clauses.size(); j++) {
                BLangVariable precedingVar = clauses.get(i).bindingPatternVariable;
                BLangVariable currentVar = clauses.get(j).bindingPatternVariable;
                if (checkStructuredPatternSimilarity(precedingVar, currentVar) &&
                        checkTypeGuardSimilarity(clauses.get(i).typeGuardExpr, clauses.get(j).typeGuardExpr)) {
                    dlog.error(currentVar.pos, DiagnosticCode.MATCH_STMT_UNREACHABLE_PATTERN);
                    clauses.remove(j--);
                }
            }
        }
        return finalPattern.isLastPattern;
    }

    /**
     * This method will check if two patterns are similar to each other.
     * Having similar patterns in the match block will result in unreachable pattern.
     *
     * @param precedingPattern pattern taken to compare similarity.
     * @param pattern          the pattern that the precedingPattern is checked for similarity.
     * @return true if both patterns are similar..
     */
    private boolean checkLiteralSimilarity(BLangExpression precedingPattern, BLangExpression pattern) {
        if (precedingPattern.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
            return true;
        }

        if (precedingPattern.type.tag == TypeTags.MAP && pattern.type.tag == TypeTags.MAP) {
            BLangRecordLiteral precedingRecordLiteral = (BLangRecordLiteral) precedingPattern;
            Map<String, BLangExpression> recordLiteral = ((BLangRecordLiteral) pattern).keyValuePairs
                    .stream()
                    .collect(Collectors.toMap(
                            keyValuePair -> ((BLangSimpleVarRef) keyValuePair.key.expr).variableName.value,
                            BLangRecordKeyValue::getValue
                    ));

            for (int i = 0; i < precedingRecordLiteral.keyValuePairs.size(); i++) {
                BLangRecordKeyValue bLangRecordKeyValue = precedingRecordLiteral.keyValuePairs.get(i);
                String key = ((BLangSimpleVarRef) bLangRecordKeyValue.key.expr).variableName.value;
                if (!recordLiteral.containsKey(key)) {
                    return false;
                }
                if (!checkLiteralSimilarity(bLangRecordKeyValue.valueExpr, recordLiteral.get(key))) {
                    return false;
                }
            }
            return true;
        }

        if (precedingPattern.type.tag == TypeTags.TUPLE && pattern.type.tag == TypeTags.TUPLE) {
            BLangBracedOrTupleExpr precedingTupleLiteral = (BLangBracedOrTupleExpr) precedingPattern;
            BLangBracedOrTupleExpr tupleLiteral = (BLangBracedOrTupleExpr) pattern;
            if (precedingTupleLiteral.expressions.size() != tupleLiteral.expressions.size()) {
                return false;
            }
            return IntStream.range(0, precedingTupleLiteral.expressions.size())
                    .allMatch(i -> checkLiteralSimilarity(precedingTupleLiteral.expressions.get(i),
                            tupleLiteral.expressions.get(i)));
        }

        if (types.isValueType(precedingPattern.type) && types.isValueType(pattern.type)) {
            BLangLiteral literal = pattern.getKind() == NodeKind.BRACED_TUPLE_EXPR ?
                    (BLangLiteral) ((BLangBracedOrTupleExpr) pattern).expressions.get(0) : (BLangLiteral) pattern;

            BLangLiteral precedingLiteral = precedingPattern.getKind() == NodeKind.BRACED_TUPLE_EXPR ?
                    (BLangLiteral) ((BLangBracedOrTupleExpr) precedingPattern).expressions.get(0) :
                    (BLangLiteral) precedingPattern;

            return (precedingLiteral.value.equals(literal.value));
        }
        return false;
    }

    /**
     * This method will determine if the type guard of the preceding pattern will result in the current pattern
     * being unreachable.
     *
     * @param precedingGuard type guard of the preceding structured pattern
     * @param currentGuard   type guard of the cuurent structured pattern
     * @return true if the current pattern is unreachable due to the type guard of the preceding pattern
     */
    private boolean checkTypeGuardSimilarity(BLangExpression precedingGuard, BLangExpression currentGuard) {
        // check if type guard is a type test expr and compare the variable ref and type node
        if (precedingGuard != null && currentGuard != null) {
            if (precedingGuard.getKind() == NodeKind.TYPE_TEST_EXPR &&
                    currentGuard.getKind() == NodeKind.TYPE_TEST_EXPR &&
                    ((BLangTypeTestExpr) precedingGuard).expr.getKind() == NodeKind.SIMPLE_VARIABLE_REF &&
                    ((BLangTypeTestExpr) currentGuard).expr.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                BLangTypeTestExpr precedingTypeTest = (BLangTypeTestExpr) precedingGuard;
                BLangTypeTestExpr currentTypeTest = (BLangTypeTestExpr) currentGuard;
                return ((BLangSimpleVarRef) precedingTypeTest.expr).variableName.toString().equals(
                        ((BLangSimpleVarRef) currentTypeTest.expr).variableName.toString()) &&
                        precedingTypeTest.typeNode.type.tag == currentTypeTest.typeNode.type.tag;
            }
            return false;
        }

        return currentGuard != null || precedingGuard == null;
    }

    /**
     * This method will determine if the current structured pattern will be unreachable due to a preceding pattern.
     *
     * @param precedingVar the structured pattern that appears on top
     * @param var          the structured pattern that appears after the precedingVar
     * @return true if the the current pattern is unreachable due to the preceding pattern
     */
    private boolean checkStructuredPatternSimilarity(BLangVariable precedingVar, BLangVariable var) {
        if (precedingVar.type.tag == TypeTags.SEMANTIC_ERROR || var.type.tag == TypeTags.SEMANTIC_ERROR) {
            return false;
        }

        if (precedingVar.getKind() == NodeKind.RECORD_VARIABLE && var.getKind() == NodeKind.RECORD_VARIABLE) {
            BLangRecordVariable precedingRecVar = (BLangRecordVariable) precedingVar;
            BLangRecordVariable recVar = (BLangRecordVariable) var;
            Map<String, BLangVariable> recVarAsMap = recVar.variableList.stream()
                    .collect(Collectors.toMap(
                            keyValue -> keyValue.key.value,
                            keyValue -> keyValue.valueBindingPattern
                    ));

            if (precedingRecVar.variableList.size() != recVar.variableList.size()) {
                return false;
            }

            for (int i = 0; i < precedingRecVar.variableList.size(); i++) {
                BLangRecordVariableKeyValue precedingKeyValue = precedingRecVar.variableList.get(i);
                if (!recVarAsMap.containsKey(precedingKeyValue.key.value)) {
                    return false;
                }

                if (!checkStructuredPatternSimilarity(
                        precedingKeyValue.valueBindingPattern, recVarAsMap.get(precedingKeyValue.key.value))) {
                    return false;
                }
            }

            return !precedingRecVar.isClosed || recVar.isClosed;
        }

        if (precedingVar.getKind() == NodeKind.TUPLE_VARIABLE && var.getKind() == NodeKind.TUPLE_VARIABLE) {
            List<BLangVariable> precedingMemberVars = ((BLangTupleVariable) precedingVar).memberVariables;
            List<BLangVariable> memberVars = ((BLangTupleVariable) var).memberVariables;
            if (precedingMemberVars.size() != memberVars.size()) {
                return false;
            }

            for (int i = 0; i < memberVars.size(); i++) {
                if (!checkStructuredPatternSimilarity(precedingMemberVars.get(i), memberVars.get(i))) {
                    return false;
                }
            }
            return true;
        }

        if (precedingVar.getKind() == NodeKind.VARIABLE &&
                (var.getKind() == NodeKind.RECORD_VARIABLE || var.getKind() == NodeKind.TUPLE_VARIABLE)) {
            return true;
        }

        return precedingVar.getKind() == NodeKind.VARIABLE;
    }

    /**
     * This method will check if the static match pattern is valid based on the matching type.
     *
     * @param matchType type of the expression being matched.
     * @param literal   the static match pattern.
     * @return true if the pattern is valid, else false.
     */
    private boolean isValidStaticMatchPattern(BType matchType, BLangExpression literal) {
        if (literal.type.tag == TypeTags.NONE) {
            return true; // When matching '_'
        }

        if (types.isSameType(literal.type, matchType)) {
            return true;
        }

        if (TypeTags.ANY == literal.type.tag) {
            return true;
        }

        switch (matchType.tag) {
            case TypeTags.ANY:
            case TypeTags.ANYDATA:
            case TypeTags.JSON:
                return true;
            case TypeTags.UNION:
                BUnionType unionMatchType = (BUnionType) matchType;
                return unionMatchType.memberTypes
                        .stream()
                        .anyMatch(memberMatchType -> isValidStaticMatchPattern(memberMatchType, literal));
            case TypeTags.TUPLE:
                if (literal.type.tag == TypeTags.TUPLE) {
                    BLangBracedOrTupleExpr tupleLiteral = (BLangBracedOrTupleExpr) literal;
                    BTupleType literalTupleType = (BTupleType) literal.type;
                    BTupleType matchTupleType = (BTupleType) matchType;
                    if (literalTupleType.tupleTypes.size() != matchTupleType.tupleTypes.size()) {
                        return false;
                    }
                    return IntStream.range(0, literalTupleType.tupleTypes.size())
                            .allMatch(i ->
                                    isValidStaticMatchPattern(matchTupleType.tupleTypes.get(i),
                                            tupleLiteral.expressions.get(i)));
                }
                break;
            case TypeTags.MAP:
                if (literal.type.tag == TypeTags.MAP) {
                    // if match type is map, check if literals match to the constraint
                    BLangRecordLiteral mapLiteral = (BLangRecordLiteral) literal;
                    return IntStream.range(0, mapLiteral.keyValuePairs.size())
                            .allMatch(i -> isValidStaticMatchPattern(((BMapType) matchType).constraint,
                                    mapLiteral.keyValuePairs.get(i).valueExpr));
                }
                break;
            case TypeTags.RECORD:
                if (literal.type.tag == TypeTags.MAP) {
                    // if match type is record, the fields must match to the static pattern fields
                    BLangRecordLiteral mapLiteral = (BLangRecordLiteral) literal;
                    BRecordType recordMatchType = (BRecordType) matchType;
                    Map<String, BType> recordFields = recordMatchType.fields
                            .stream()
                            .collect(Collectors.toMap(
                                    field -> field.getName().getValue(),
                                    BField::getType
                            ));

                    for (BLangRecordKeyValue literalKeyValue : mapLiteral.keyValuePairs) {
                        String literalKeyName;
                        if (literalKeyValue.key.expr.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                            literalKeyName = ((BLangSimpleVarRef) literalKeyValue.key.expr).variableName.value;
                        } else if (literalKeyValue.key.expr.getKind() == NodeKind.LITERAL) {
                            literalKeyName = ((BLangLiteral) literalKeyValue.key.expr).value.toString();
                        } else {
                            return false;
                        }

                        if (recordFields.containsKey(literalKeyName)) {
                            if (!isValidStaticMatchPattern(
                                    recordFields.get(literalKeyName), literalKeyValue.valueExpr)) {
                                return false;
                            }
                        } else if (recordMatchType.sealed ||
                                !isValidStaticMatchPattern(recordMatchType.restFieldType, literalKeyValue.valueExpr)) {
                            return false;
                        }
                    }
                    return true;
                }
                break;
            case TypeTags.BYTE:
                if (literal.type.tag == TypeTags.INT) {
                    return true;
                }
                break;
            case TypeTags.FINITE:
                if (literal.getKind() == NodeKind.LITERAL) {
                    return types.isAssignableToFiniteType(matchType, (BLangLiteral) literal);
                }
                break;
        }
        return false;
    }

    @Override
    public void visit(BLangForeach foreach) {
        this.loopWithintransactionCheckStack.push(true);
        this.checkStatementExecutionValidity(foreach);
        this.loopCount++;
        analyzeNode(foreach.body, env);
        this.loopCount--;
        this.resetLastStatement();
        this.loopWithintransactionCheckStack.pop();
        analyzeExpr(foreach.collection);
    }

    @Override
    public void visit(BLangWhile whileNode) {
        this.loopWithintransactionCheckStack.push(true);
        this.checkStatementExecutionValidity(whileNode);
        this.loopCount++;
        analyzeNode(whileNode.body, env);
        this.loopCount--;
        this.resetLastStatement();
        this.loopWithintransactionCheckStack.pop();
        analyzeExpr(whileNode.expr);
    }

    @Override
    public void visit(BLangLock lockNode) {
        this.checkStatementExecutionValidity(lockNode);
        lockNode.body.stmts.forEach(e -> analyzeNode(e, env));
    }

    @Override
    public void visit(BLangContinue continueNode) {
        this.checkStatementExecutionValidity(continueNode);
        if (this.loopCount == 0) {
            this.dlog.error(continueNode.pos, DiagnosticCode.CONTINUE_CANNOT_BE_OUTSIDE_LOOP);
            return;
        }
        if (checkNextBreakValidityInTransaction()) {
            this.dlog.error(continueNode.pos, DiagnosticCode.CONTINUE_CANNOT_BE_USED_TO_EXIT_TRANSACTION);
            return;
        }
        this.lastStatement = true;
    }

    public void visit(BLangImportPackage importPkgNode) {
        BPackageSymbol pkgSymbol = importPkgNode.symbol;
        SymbolEnv pkgEnv = this.symTable.pkgEnvMap.get(pkgSymbol);
        if (pkgEnv == null) {
            return;
        }

        analyzeNode(pkgEnv.node, env);
    }

    public void visit(BLangXMLNS xmlnsNode) {
        /* ignore */
    }

    public void visit(BLangService serviceNode) {
    }

    public void visit(BLangResource resourceNode) {
        throw new RuntimeException("Deprecated lang feature");
    }

    public void visit(BLangForever foreverStatement) {
        this.lastStatement = true;
    }

    public void visit(BLangObjectTypeNode objectTypeNode) {
        SymbolEnv objectEnv = SymbolEnv.createTypeEnv(objectTypeNode, objectTypeNode.symbol.scope, env);
        if (objectTypeNode.isFieldAnalyseRequired && Symbols.isPublic(objectTypeNode.symbol)) {
            objectTypeNode.fields.stream()
                    .filter(field -> (Symbols.isPublic(field.symbol)))
                    .forEach(field -> analyzeNode(field, objectEnv));
        }
        objectTypeNode.functions.forEach(e -> this.analyzeNode(e, objectEnv));
        if (Symbols.isFlagOn(objectTypeNode.symbol.flags, Flags.CLIENT) && objectTypeNode.functions.stream()
                .noneMatch(func -> Symbols.isFlagOn(func.symbol.flags, Flags.REMOTE))) {
            this.dlog.error(objectTypeNode.pos, DiagnosticCode.CLIENT_HAS_NO_REMOTE_FUNCTION);
        }
    }

    private void analyseType(BType type, DiagnosticPos pos) {
        if (type == null || type.tsymbol == null) {
            return;
        }
        BSymbol symbol = type.tsymbol;
        if (!Symbols.isPublic(symbol)) {
            dlog.error(pos, DiagnosticCode.ATTEMPT_EXPOSE_NON_PUBLIC_SYMBOL, symbol.name);
        }
    }

    public void visit(BLangRecordTypeNode recordTypeNode) {
        SymbolEnv recordEnv = SymbolEnv.createTypeEnv(recordTypeNode, recordTypeNode.symbol.scope, env);
        if (recordTypeNode.isFieldAnalyseRequired && Symbols.isPublic(recordTypeNode.symbol)) {
            recordTypeNode.fields.stream()
                    .filter(field -> (Symbols.isPublic(field.symbol)))
                    .forEach(field -> analyzeNode(field, recordEnv));
        }
    }

    public void visit(BLangSimpleVariable varNode) {
        analyzeExpr(varNode.expr);

        if (Objects.isNull(varNode.symbol)) {
            return;
        }

        if (!Symbols.isPublic(varNode.symbol)) {
            if (varNode.expr == null && Symbols.isFlagOn(varNode.symbol.flags, Flags.LISTENER)) {
                dlog.error(varNode.pos, DiagnosticCode.UNINITIALIZED_VARIABLE, varNode.name);
            }
            return;
        }

        int ownerSymTag = this.env.scope.owner.tag;
        if (((ownerSymTag & SymTag.INVOKABLE) != SymTag.INVOKABLE) || (varNode.type != null && varNode.parent != null &&
                NodeKind.FUNCTION.equals(varNode.parent.getKind()))) {
            analyseType(varNode.type, varNode.pos);
        }

        if (varNode.expr == null && ownerSymTag == SymTag.PACKAGE) {
            this.dlog.error(varNode.pos, DiagnosticCode.UNINITIALIZED_VARIABLE, varNode.name);
        }
    }

    public void visit(BLangIdentifier identifierNode) {
        /* ignore */
    }

    public void visit(BLangAnnotation annotationNode) {
        /* ignore */
    }

    public void visit(BLangAnnotationAttachment annAttachmentNode) {
        /* ignore */
    }

    public void visit(BLangSimpleVariableDef varDefNode) {
        this.checkStatementExecutionValidity(varDefNode);
        analyzeNode(varDefNode.var, env);
        // validate for endpoints here.
        validateEndpointDeclaration(varDefNode);
    }

    public void visit(BLangCompoundAssignment compoundAssignment) {
        this.checkStatementExecutionValidity(compoundAssignment);
        analyzeExpr(compoundAssignment.varRef);
        analyzeExpr(compoundAssignment.expr);
    }

    public void visit(BLangAssignment assignNode) {
        this.checkStatementExecutionValidity(assignNode);
        analyzeExpr(assignNode.varRef);
        analyzeExpr(assignNode.expr);
    }

    public void visit(BLangRecordDestructure stmt) {
        this.checkStatementExecutionValidity(stmt);
        analyzeExpr(stmt.varRef);
        analyzeExpr(stmt.expr);
    }

    public void visit(BLangErrorDestructure stmt) {
        this.checkStatementExecutionValidity(stmt);
        analyzeExpr(stmt.varRef);
        analyzeExpr(stmt.expr);
    }

    @Override
    public void visit(BLangTupleDestructure stmt) {
        this.checkStatementExecutionValidity(stmt);
        analyzeExpr(stmt.varRef);
        analyzeExpr(stmt.expr);
    }

    public void visit(BLangBreak breakNode) {
        this.checkStatementExecutionValidity(breakNode);
        if (this.loopCount == 0) {
            this.dlog.error(breakNode.pos, DiagnosticCode.BREAK_CANNOT_BE_OUTSIDE_LOOP);
            return;
        }
        if (checkNextBreakValidityInTransaction()) {
            this.dlog.error(breakNode.pos, DiagnosticCode.BREAK_CANNOT_BE_USED_TO_EXIT_TRANSACTION);
            return;
        }
        this.lastStatement = true;
    }

    public void visit(BLangThrow throwNode) {
        /* ignore */
    }

    public void visit(BLangPanic panicNode) {
        this.checkStatementExecutionValidity(panicNode);
        this.statementReturns = true;
        analyzeExpr(panicNode.expr);
    }

    public void visit(BLangXMLNSStatement xmlnsStmtNode) {
        this.checkStatementExecutionValidity(xmlnsStmtNode);
    }

    public void visit(BLangExpressionStmt exprStmtNode) {
        this.checkStatementExecutionValidity(exprStmtNode);
        analyzeExpr(exprStmtNode.expr);
        validateExprStatementExpression(exprStmtNode);
    }

    private void validateExprStatementExpression(BLangExpressionStmt exprStmtNode) {
        BLangExpression expr = exprStmtNode.expr;
        while (expr.getKind() == NodeKind.MATCH_EXPRESSION || expr.getKind() == NodeKind.CHECK_EXPR) {
            if (expr.getKind() == NodeKind.MATCH_EXPRESSION) {
                expr = ((BLangMatchExpression) expr).expr;
            } else if (expr.getKind() == NodeKind.CHECK_EXPR) {
                expr = ((BLangCheckedExpr) expr).expr;
            }
        }
        // Allowed expression kinds
        if (expr.getKind() == NodeKind.INVOCATION || expr.getKind() == NodeKind.WAIT_EXPR) {
            return;
        }
        // For other expressions, error is logged already.
        if (expr.type == symTable.nilType) {
            dlog.error(exprStmtNode.pos, DiagnosticCode.INVALID_EXPR_STATEMENT);
        }
    }

    public void visit(BLangTryCatchFinally tryNode) {
        /* ignore */
    }

    public void visit(BLangCatch catchNode) {
        /* ignore */
    }

    private boolean isTopLevel() {
        SymbolEnv env = this.env;
        return env.enclInvokable.body == env.node;
    }

    private boolean isInWorker() {
        return env.enclInvokable.flagSet.contains(Flag.WORKER);
    }

    private boolean isCommunicationAllowedLocation(String workerIdentifier) {
        return (isDefaultWorkerCommunication(workerIdentifier) && isInWorker()) || isTopLevel();
    }

    private boolean isDefaultWorkerCommunication(String workerIdentifier) {
        return workerIdentifier.equals(DEFAULT_WORKER_NAME);
    }

    private boolean workerExists(BType type, String workerName) {
        if (isDefaultWorkerCommunication(workerName) && isInWorker()) {
            return true;
        }
        if (type == symTable.semanticError) {
            return false;
        }
        return type.tag == TypeTags.FUTURE && ((BFutureType) type).workerDerivative;
    }


    // Asynchronous Send Statement
    public void visit(BLangWorkerSend workerSendNode) {
        this.checkStatementExecutionValidity(workerSendNode);
        if (workerSendNode.isChannel) {
            analyzeExpr(workerSendNode.expr);
            if (workerSendNode.keyExpr != null) {
                analyzeExpr(workerSendNode.keyExpr);
            }
            return;
        }

        WorkerActionSystem was = this.workerActionSystemStack.peek();

        BType type = workerSendNode.expr.type;
        if (type == symTable.semanticError) {
            // Error of this is already printed as undef-var
            was.hasErrors = true;
        } else if (!types.isAnydata(type)) {
            this.dlog.error(workerSendNode.pos, DiagnosticCode.INVALID_TYPE_FOR_SEND, type);
        }

        String workerName = workerSendNode.workerIdentifier.getValue();
        boolean allowedLocation = isCommunicationAllowedLocation(workerName);
        if (!allowedLocation) {
            this.dlog.error(workerSendNode.pos, DiagnosticCode.INVALID_WORKER_SEND_POSITION);
            was.hasErrors = true;
        }

        if (!this.workerExists(workerSendNode.type, workerName)) {
            this.dlog.error(workerSendNode.pos, DiagnosticCode.UNDEFINED_WORKER, workerName);
            was.hasErrors = true;
        }

        workerSendNode.type = createAccumulatedErrorTypeForMatchingRecive(workerSendNode);
        was.addWorkerAction(workerSendNode);
        analyzeExpr(workerSendNode.expr);
    }

    private BType createAccumulatedErrorTypeForMatchingRecive(BLangWorkerSend workerSendNode) {
        Set<BType> returnTypesUpToNow = this.returnTypes.peek();
        LinkedHashSet<BType> returnTypeAndSendType = new LinkedHashSet<BType>() {
            {
                Comparator.comparing(BType::toString);
            }
        };
        for (BType returnType : returnTypesUpToNow) {
            if (returnType.tag == TypeTags.ERROR) {
                returnTypeAndSendType.add(returnType);
            } else {
                this.dlog.error(workerSendNode.pos, DiagnosticCode.WORKER_SEND_AFTER_RETURN);
            }
        }
        returnTypeAndSendType.add(workerSendNode.expr.type);
        if (returnTypeAndSendType.size() > 1) {
            return new BUnionType(null, returnTypeAndSendType, false);
        } else {
            return workerSendNode.expr.type;
        }
    }

    @Override
    public void visit(BLangWorkerSyncSendExpr syncSendExpr) {
        // Validate worker synchronous send
        validateActions(syncSendExpr.pos, syncSendExpr);
        String workerName = syncSendExpr.workerIdentifier.getValue();
        WorkerActionSystem was = this.workerActionSystemStack.peek();

        boolean allowedLocation = isCommunicationAllowedLocation(workerName);
        if (!allowedLocation) {
            this.dlog.error(syncSendExpr.pos, DiagnosticCode.INVALID_WORKER_SEND_POSITION);
            was.hasErrors = true;
        }

        if (!this.workerExists(syncSendExpr.workerType, workerName)) {
            this.dlog.error(syncSendExpr.pos, DiagnosticCode.UNDEFINED_WORKER, workerName);
            was.hasErrors = true;
        }
        was.addWorkerAction(syncSendExpr);
        analyzeExpr(syncSendExpr.expr);
    }

    @Override
    public void visit(BLangWorkerReceive workerReceiveNode) {
        // Validate worker receive
        validateActions(workerReceiveNode.pos, workerReceiveNode);

        if (workerReceiveNode.isChannel) {
            if (workerReceiveNode.keyExpr != null) {
                analyzeExpr(workerReceiveNode.keyExpr);
            }
            return;
        }
        WorkerActionSystem was = this.workerActionSystemStack.peek();

        String workerName = workerReceiveNode.workerIdentifier.getValue();
        boolean allowedLocation = isCommunicationAllowedLocation(workerName);
        if (!allowedLocation) {
            this.dlog.error(workerReceiveNode.pos, DiagnosticCode.INVALID_WORKER_RECEIVE_POSITION);
            was.hasErrors = true;
        }

        if (!this.workerExists(workerReceiveNode.workerType, workerName)) {
            this.dlog.error(workerReceiveNode.pos, DiagnosticCode.UNDEFINED_WORKER, workerName);
            was.hasErrors = true;
        }

        workerReceiveNode.matchingSendsError = createAccumulatedErrorTypeForMatchingSyncSend(workerReceiveNode);

        was.addWorkerAction(workerReceiveNode);

    }

    public BType createAccumulatedErrorTypeForMatchingSyncSend(BLangWorkerReceive workerReceiveNode) {
        Set<BType> returnTypesUpToNow = this.returnTypes.peek();
        LinkedHashSet<BType> returnTypeAndSendType = new LinkedHashSet<>();
        for (BType returnType : returnTypesUpToNow) {
            if (returnType.tag == TypeTags.ERROR) {
                returnTypeAndSendType.add(returnType);
            } else {
                this.dlog.error(workerReceiveNode.pos, DiagnosticCode.WORKER_RECEIVE_AFTER_RETURN);
            }
        }
        returnTypeAndSendType.add(symTable.nilType);
        if (returnTypeAndSendType.size() > 1) {
            return new BUnionType(null, returnTypeAndSendType, true);
        } else {
            return symTable.nilType;
        }
    }

    public void visit(BLangLiteral literalExpr) {
        /* ignore */
    }

    public void visit(BLangArrayLiteral arrayLiteral) {
        analyzeExprs(arrayLiteral.exprs);
    }

    public void visit(BLangRecordLiteral recordLiteral) {
        List<BLangRecordKeyValue> keyValuePairs = recordLiteral.keyValuePairs;
        keyValuePairs.forEach(kv -> {
            analyzeExpr(kv.valueExpr);
        });

        Set<Object> names = new TreeSet<>((l, r) -> l.equals(r) ? 0 : 1);
        for (BLangRecordKeyValue recFieldDecl : keyValuePairs) {
            BLangExpression key = recFieldDecl.getKey();
            if (key.getKind() == NodeKind.SIMPLE_VARIABLE_REF) {
                BLangSimpleVarRef keyRef = (BLangSimpleVarRef) key;
                if (names.contains(keyRef.variableName.value)) {
                    String assigneeType = recordLiteral.parent.type.getKind().typeName();
                    this.dlog.error(key.pos, DiagnosticCode.DUPLICATE_KEY_IN_RECORD_LITERAL, assigneeType, keyRef);
                }
                names.add(keyRef.variableName.value);
            } else if (key.getKind() == NodeKind.LITERAL) {
                BLangLiteral keyLiteral = (BLangLiteral) key;
                if (names.contains(keyLiteral.value)) {
                    String assigneeType = recordLiteral.parent.type.getKind().typeName();
                    this.dlog.error(key.pos, DiagnosticCode.DUPLICATE_KEY_IN_RECORD_LITERAL, assigneeType, keyLiteral);
                }
                names.add(keyLiteral.value);
            }
        }
    }

    public void visit(BLangTableLiteral tableLiteral) {
        /* ignore */
    }

    public void visit(BLangSimpleVarRef varRefExpr) {
        /* ignore */
    }

    public void visit(BLangRecordVarRef varRefExpr) {
        /* ignore */
    }

    public void visit(BLangErrorVarRef varRefExpr) {
        /* ignore */
    }

    public void visit(BLangTupleVarRef varRefExpr) {
        /* ignore */
    }

    public void visit(BLangFieldBasedAccess fieldAccessExpr) {
        /* ignore */
    }

    public void visit(BLangIndexBasedAccess indexAccessExpr) {
        analyzeExpr(indexAccessExpr.indexExpr);
        analyzeExpr(indexAccessExpr.expr);

        if (indexAccessExpr.indexExpr.type.tag == TypeTags.SEMANTIC_ERROR) {
            return;
        }

        if (indexAccessExpr.expr.type.tag == TypeTags.ARRAY &&
                indexAccessExpr.indexExpr.getKind() == NodeKind.LITERAL) {
            BArrayType bArrayType = (BArrayType) indexAccessExpr.expr.type;
            BLangLiteral indexExpr = (BLangLiteral) indexAccessExpr.indexExpr;
            Long indexVal = (Long) indexExpr.getValue();   // indexExpr.getBValue() will always be a long at this stage
            if (bArrayType.state == BArrayState.CLOSED_SEALED && (bArrayType.size <= indexVal)) {
                dlog.error(indexExpr.pos, DiagnosticCode.ARRAY_INDEX_OUT_OF_RANGE, indexVal, bArrayType.size);
            }
        }
    }

    public void visit(BLangInvocation invocationExpr) {
        analyzeExpr(invocationExpr.expr);
        analyzeExprs(invocationExpr.requiredArgs);
        analyzeExprs(invocationExpr.namedArgs);
        analyzeExprs(invocationExpr.restArgs);

        checkDuplicateNamedArgs(invocationExpr.namedArgs);

        // Null check is to ignore Negative path where symbol does not get resolved at TypeChecker.
        if ((invocationExpr.symbol != null) && invocationExpr.symbol.kind == SymbolKind.FUNCTION) {
            BSymbol funcSymbol = invocationExpr.symbol;
            if (Symbols.isFlagOn(funcSymbol.flags, Flags.DEPRECATED)) {
                dlog.warning(invocationExpr.pos, DiagnosticCode.USAGE_OF_DEPRECATED_FUNCTION,
                        names.fromIdNode(invocationExpr.name));
            }
        }

        if (invocationExpr.actionInvocation) {
            validateActionInvocation(invocationExpr.pos, invocationExpr);
        }
    }

    private void validateEndpointDeclaration(BLangSimpleVariableDef varDefNode) {
        if (Objects.isNull(varDefNode.var.symbol) || varDefNode.var.symbol.tag != SymTag.ENDPOINT ||
                Objects.isNull(varDefNode.parent) || Objects
                .isNull(varDefNode.parent.parent)) {
            return;
        }
        // Check for valid parents nodes. (immediate parent is block node)
        switch (varDefNode.parent.parent.getKind()) {
            case RESOURCE:
            case FUNCTION:
                break;
            default:
                dlog.error(varDefNode.pos, DiagnosticCode.INVALID_ENDPOINT_DECLARATION);
                return;
        }
        // Check with siblings now.
        BLangBlockStmt blockStmt = (BLangBlockStmt) varDefNode.parent;
        for (BLangStatement statement : blockStmt.stmts) {
            if (statement == varDefNode) {
                break;
            }
            if (statement.getKind() != NodeKind.VARIABLE_DEF) {
                dlog.error(varDefNode.pos, DiagnosticCode.INVALID_ENDPOINT_DECLARATION);
                break;
            }
            BLangSimpleVariableDef def = (BLangSimpleVariableDef) statement;
            if (def.var.symbol.tag != SymTag.ENDPOINT) {
                dlog.error(varDefNode.pos, DiagnosticCode.INVALID_ENDPOINT_DECLARATION);
                break;
            }
        }
    }

    private void validateActionInvocation(DiagnosticPos pos, BLangInvocation iExpr) {
        final NodeKind clientNodeKind = iExpr.expr.getKind();
        // Validation against node kind.
        if (clientNodeKind != NodeKind.SIMPLE_VARIABLE_REF && clientNodeKind != NodeKind.FIELD_BASED_ACCESS_EXPR) {
            dlog.error(pos, DiagnosticCode.INVALID_ACTION_INVOCATION_AS_EXPR);
        } else if (clientNodeKind == NodeKind.FIELD_BASED_ACCESS_EXPR) {
            final BLangFieldBasedAccess fieldBasedAccess = (BLangFieldBasedAccess) iExpr.expr;
            if (fieldBasedAccess.expr.getKind() != NodeKind.SIMPLE_VARIABLE_REF) {
                dlog.error(pos, DiagnosticCode.INVALID_ACTION_INVOCATION_AS_EXPR);
            } else {
                final BLangSimpleVarRef selfName = (BLangSimpleVarRef) fieldBasedAccess.expr;
                if (!Names.SELF.equals(selfName.symbol.name)) {
                    dlog.error(pos, DiagnosticCode.INVALID_ACTION_INVOCATION_AS_EXPR);
                }
            }
        }

        // Validate for parent nodes.
        BLangNode parent = iExpr.parent;
        while (parent != null) {
            final NodeKind kind = parent.getKind();
            // Allowed node types.
            if (kind == NodeKind.ASSIGNMENT || kind == NodeKind.EXPRESSION_STATEMENT || kind == NodeKind.RETURN
                    || kind == NodeKind.TUPLE_DESTRUCTURE || kind == NodeKind.VARIABLE) {
                return;
            } else if (kind == NodeKind.CHECK_EXPR || kind == NodeKind.MATCH_EXPRESSION || kind == NodeKind.TRAP_EXPR) {
                parent = parent.parent;
                continue;
            } else if (kind == NodeKind.ELVIS_EXPR
                    && ((BLangElvisExpr) parent).lhsExpr.getKind() == NodeKind.INVOCATION
                    && ((BLangInvocation) ((BLangElvisExpr) parent).lhsExpr).actionInvocation) {
                parent = parent.parent;
                continue;
            }
            break;
        }
        dlog.error(pos, DiagnosticCode.INVALID_ACTION_INVOCATION_AS_EXPR);
    }

    private void validateActions(DiagnosticPos pos, BLangNode bLangNode) {
        BLangNode parent = bLangNode.parent;
        while (parent != null) {
            final NodeKind kind = parent.getKind();
            // Allowed node types.
            if (kind == NodeKind.ASSIGNMENT || kind == NodeKind.EXPRESSION_STATEMENT
                    || kind == NodeKind.TUPLE_DESTRUCTURE || kind == NodeKind.VARIABLE) {
                return;
            } else if (kind == NodeKind.CHECK_EXPR || kind == NodeKind.BRACED_TUPLE_EXPR ||
                       kind == NodeKind.MATCH_EXPRESSION || kind == NodeKind.TRAP_EXPR) {
                parent = parent.parent;
                continue;
            } else if (kind == NodeKind.ELVIS_EXPR
                    && ((BLangElvisExpr) parent).lhsExpr.getKind() == NodeKind.INVOCATION
                    && ((BLangInvocation) ((BLangElvisExpr) parent).lhsExpr).actionInvocation) {
                parent = parent.parent;
                continue;
            }
            break;
        }
        dlog.error(pos, DiagnosticCode.INVALID_ACTION_INVOCATION_AS_EXPR);
    }

    public void visit(BLangTypeInit cIExpr) {
        if (cIExpr.type.tag == TypeTags.STREAM && (cIExpr.parent == null ||
                (cIExpr.parent.getKind() != NodeKind.ASSIGNMENT && cIExpr.parent.getKind() != NodeKind.VARIABLE))) {
            // stream initialization is only allowed as an assignment or variable definition
            dlog.error(cIExpr.pos, DiagnosticCode.STREAM_INIT_NOT_ALLOWED_HERE);
            return;
        }
        analyzeExprs(cIExpr.argsExpr);
        analyzeExpr(cIExpr.initInvocation);
    }

    public void visit(BLangTernaryExpr ternaryExpr) {
        analyzeExpr(ternaryExpr.expr);
        analyzeExpr(ternaryExpr.thenExpr);
        analyzeExpr(ternaryExpr.elseExpr);
    }

    public void visit(BLangWaitExpr awaitExpr) {
        analyzeExpr(awaitExpr.getExpression());
    }

    public void visit(BLangWaitForAllExpr waitForAllExpr) {
        waitForAllExpr.keyValuePairs.forEach(keyValue -> {
            BLangExpression expr = keyValue.valueExpr != null ? keyValue.valueExpr : keyValue.keyExpr;
            analyzeExpr(expr);
        });
    }

    @Override
    public void visit(BLangWorkerFlushExpr workerFlushExpr) {
        // Two scenarios should be handled
        // 1) flush w1 -> Wait till all the asynchronous sends to worker w1 is completed
        // 2) flush -> Wait till all asynchronous sends to all workers are completed
        BLangIdentifier flushWrkIdentifier = workerFlushExpr.workerIdentifier;
        Stack<WorkerActionSystem> workerActionSystems = this.workerActionSystemStack;
        WorkerActionSystem currentWrkerAction = workerActionSystems.peek();
        List<BLangWorkerSend> sendStmts = getAsyncSendStmtsOfWorker(currentWrkerAction);
        if (flushWrkIdentifier != null) {
            List<BLangWorkerSend> sendsToGivenWrkr = sendStmts.stream()
                                                              .filter(bLangNode -> bLangNode.workerIdentifier.equals
                                                                      (flushWrkIdentifier))
                                                              .collect(Collectors.toList());
            if (sendsToGivenWrkr.size() == 0) {
                this.dlog.error(workerFlushExpr.pos, DiagnosticCode.INVALID_WORKER_FLUSH_FOR_WORKER, flushWrkIdentifier,
                                currentWrkerAction.currentWorkerId());
                return;
            } else {
                sendStmts = sendsToGivenWrkr;
            }
        } else {
            if (sendStmts.size() == 0) {
                this.dlog.error(workerFlushExpr.pos, DiagnosticCode.INVALID_WORKER_FLUSH,
                                currentWrkerAction.currentWorkerId());
                return;
            }
        }
        workerFlushExpr.cachedWorkerSendStmts = sendStmts;
    }

    private List<BLangWorkerSend> getAsyncSendStmtsOfWorker(WorkerActionSystem currentWorkerAction) {
        List<BLangNode> actions = currentWorkerAction.workerActionStateMachines.peek().actions;
        return actions.stream()
                      .filter(CodeAnalyzer::isWorkerSend)
                      .map(bLangNode -> (BLangWorkerSend) bLangNode)
                      .collect(Collectors.toList());
    }
    @Override
    public void visit(BLangTrapExpr trapExpr) {
        analyzeExpr(trapExpr.expr);
    }

    public void visit(BLangBinaryExpr binaryExpr) {
        if (validateBinaryExpr(binaryExpr)) {
            analyzeExpr(binaryExpr.lhsExpr);
            analyzeExpr(binaryExpr.rhsExpr);
        }
    }

    private boolean validateBinaryExpr(BLangBinaryExpr binaryExpr) {
        // 1) For usual binary expressions the lhs or rhs can never be future types, so return true if both of
        // them are not future types
        if (binaryExpr.lhsExpr.type.tag != TypeTags.FUTURE && binaryExpr.rhsExpr.type.tag != TypeTags.FUTURE) {
            return true;
        }

        // 2) For binary expressions followed with wait lhs and rhs are always future types and this is allowed so
        // return true : wait f1 | f2
        BLangNode parentNode = binaryExpr.parent;
        if (binaryExpr.lhsExpr.type.tag == TypeTags.FUTURE || binaryExpr.rhsExpr.type.tag == TypeTags.FUTURE) {
            if (parentNode == null) {
                return false;
            }
            if (parentNode.getKind() == NodeKind.WAIT_EXPR) {
                return true;
            }
        }

        // 3) For binary expressions of future type which are not followed by the wait expression are not allowed.
        // So check if immediate parent is a binary expression and if the current binary expression operator kind
        // is bitwise OR
        if (parentNode.getKind() != NodeKind.BINARY_EXPR && binaryExpr.opKind == OperatorKind.BITWISE_OR) {
            dlog.error(binaryExpr.pos, DiagnosticCode.OPERATOR_NOT_SUPPORTED, OperatorKind.BITWISE_OR,
                       symTable.futureType);
                return false;
        }

        if (parentNode.getKind() == NodeKind.BINARY_EXPR) {
            return validateBinaryExpr((BLangBinaryExpr) parentNode);
        }
        return true;
    }

    public void visit(BLangElvisExpr elvisExpr) {
        analyzeExpr(elvisExpr.lhsExpr);
        analyzeExpr(elvisExpr.rhsExpr);
    }

    @Override
    public void visit(BLangBracedOrTupleExpr bracedOrTupleExpr) {
        analyzeExprs(bracedOrTupleExpr.expressions);
    }

    public void visit(BLangUnaryExpr unaryExpr) {
        analyzeExpr(unaryExpr.expr);
    }

    public void visit(BLangTypedescExpr accessExpr) {
        /* ignore */
    }

    public void visit(BLangTypeConversionExpr conversionExpr) {
        analyzeExpr(conversionExpr.expr);
    }

    public void visit(BLangXMLQName xmlQName) {
        /* ignore */
    }

    public void visit(BLangXMLAttribute xmlAttribute) {
        analyzeExpr(xmlAttribute.name);
        analyzeExpr(xmlAttribute.value);
    }

    public void visit(BLangXMLElementLiteral xmlElementLiteral) {
        analyzeExpr(xmlElementLiteral.startTagName);
        analyzeExpr(xmlElementLiteral.endTagName);
        analyzeExprs(xmlElementLiteral.attributes);
        analyzeExprs(xmlElementLiteral.children);
    }

    public void visit(BLangXMLTextLiteral xmlTextLiteral) {
        analyzeExprs(xmlTextLiteral.textFragments);
    }

    public void visit(BLangXMLCommentLiteral xmlCommentLiteral) {
        analyzeExprs(xmlCommentLiteral.textFragments);
    }

    public void visit(BLangXMLProcInsLiteral xmlProcInsLiteral) {
        analyzeExprs(xmlProcInsLiteral.dataFragments);
        analyzeExpr(xmlProcInsLiteral.target);
    }

    public void visit(BLangXMLQuotedString xmlQuotedString) {
        analyzeExprs(xmlQuotedString.textFragments);
    }

    public void visit(BLangStringTemplateLiteral stringTemplateLiteral) {
        analyzeExprs(stringTemplateLiteral.exprs);
    }

    public void visit(BLangLambdaFunction bLangLambdaFunction) {
        boolean isWorker = false;
        if (bLangLambdaFunction.parent.getKind() == NodeKind.VARIABLE) {
            String workerVarName = ((BLangSimpleVariable) bLangLambdaFunction.parent).name.value;
            if (workerVarName.startsWith(WORKER_LAMBDA_VAR_PREFIX)) {
                String workerName = workerVarName.substring(1);
                isWorker = true;
                this.workerActionSystemStack.peek().startWorkerActionStateMachine(workerName,
                                                                                  bLangLambdaFunction.function.pos,
                                                                                  bLangLambdaFunction.function);
            }
        }

        boolean statementReturn = this.statementReturns;

        this.visitFunction(bLangLambdaFunction.function);

        this.statementReturns = statementReturn;

        if (isWorker) {
            this.workerActionSystemStack.peek().endWorkerActionStateMachine();
        }
    }

    public void visit(BLangArrowFunction bLangArrowFunction) {
        /* ignore */
    }

    public void visit(BLangXMLAttributeAccess xmlAttributeAccessExpr) {
        analyzeExpr(xmlAttributeAccessExpr.expr);
        analyzeExpr(xmlAttributeAccessExpr.indexExpr);
    }

    public void visit(BLangIntRangeExpression intRangeExpression) {
        analyzeExpr(intRangeExpression.startExpr);
        analyzeExpr(intRangeExpression.endExpr);
    }

    public void visit(BLangValueType valueType) {
        /* ignore */
    }

    public void visit(BLangArrayType arrayType) {
        /* ignore */
    }

    public void visit(BLangBuiltInRefTypeNode builtInRefType) {
        /* ignore */
    }

    public void visit(BLangConstrainedType constrainedType) {
        /* ignore */
    }

    public void visit(BLangErrorType errorType) {
        /* ignore */
    }

    public void visit(BLangUserDefinedType userDefinedType) {
        analyseType(userDefinedType.type, userDefinedType.pos);
    }

    public void visit(BLangTupleTypeNode tupleTypeNode) {
        tupleTypeNode.memberTypeNodes.forEach(memberType -> analyzeNode(memberType, env));
    }

    public void visit(BLangUnionTypeNode unionTypeNode) {
        unionTypeNode.memberTypeNodes.forEach(memberType -> analyzeNode(memberType, env));
    }

    public void visit(BLangFunctionTypeNode functionTypeNode) {
        analyseType(functionTypeNode.type, functionTypeNode.pos);
    }

    @Override
    public void visit(BLangTableQueryExpression tableQueryExpression) {
        /* ignore */
    }

    @Override
    public void visit(BLangRestArgsExpression bLangVarArgsExpression) {
        /* ignore */
    }

    @Override
    public void visit(BLangNamedArgsExpression bLangNamedArgsExpression) {
        /* ignore */
    }

    @Override
    public void visit(BLangMatchExpression bLangMatchExpression) {
        analyzeExpr(bLangMatchExpression.expr);
        List<BType> exprTypes;

        if (bLangMatchExpression.expr.type.tag == TypeTags.UNION) {
            BUnionType unionType = (BUnionType) bLangMatchExpression.expr.type;
            exprTypes = new ArrayList<>(unionType.memberTypes);
        } else {
            exprTypes = Lists.of(bLangMatchExpression.expr.type);
        }

        List<BType> unmatchedExprTypes = new ArrayList<>();
        for (BType exprType : exprTypes) {
            boolean assignable = false;
            for (BLangMatchExprPatternClause pattern : bLangMatchExpression.patternClauses) {
                BType patternType = pattern.variable.type;
                if (exprType.tag == TypeTags.SEMANTIC_ERROR || patternType.tag == TypeTags.SEMANTIC_ERROR) {
                    return;
                }

                assignable = this.types.isAssignable(exprType, patternType);
                if (assignable) {
                    pattern.matchedTypesDirect.add(exprType);
                    break;
                } else if (exprType.tag == TypeTags.ANY) {
                    pattern.matchedTypesIndirect.add(exprType);
                } else if (exprType.tag == TypeTags.JSON && this.types.isAssignable(patternType, exprType)) {
                    pattern.matchedTypesIndirect.add(exprType);
                } else if ((exprType.tag == TypeTags.OBJECT || exprType.tag == TypeTags.RECORD)
                        && this.types.isAssignable(patternType, exprType)) {
                    pattern.matchedTypesIndirect.add(exprType);
                } else if (exprType.tag == TypeTags.BYTE && patternType.tag == TypeTags.INT) {
                    pattern.matchedTypesDirect.add(exprType);
                    break;
                } else {
                    // TODO Support other assignable types
                }
            }

            // check if the exprType can be added to implicit default pattern 
            if (!assignable && !this.types.isAssignable(exprType, bLangMatchExpression.type)) {
                unmatchedExprTypes.add(exprType);
            }
        }

        if (!unmatchedExprTypes.isEmpty()) {
            dlog.error(bLangMatchExpression.pos, DiagnosticCode.MATCH_STMT_CANNOT_GUARANTEE_A_MATCHING_PATTERN,
                    unmatchedExprTypes);
        }

        boolean matchedPatternsAvailable = false;
        for (int i = bLangMatchExpression.patternClauses.size() - 1; i >= 0; i--) {
            BLangMatchExprPatternClause pattern = bLangMatchExpression.patternClauses.get(i);
            if (pattern.matchedTypesDirect.isEmpty() && pattern.matchedTypesIndirect.isEmpty()) {
                if (matchedPatternsAvailable) {
                    dlog.error(pattern.pos, DiagnosticCode.MATCH_STMT_UNMATCHED_PATTERN);
                } else {
                    dlog.error(pattern.pos, DiagnosticCode.MATCH_STMT_UNREACHABLE_PATTERN);
                }
            } else {
                matchedPatternsAvailable = true;
            }
        }
    }

    @Override
    public void visit(BLangCheckedExpr checkedExpr) {
        analyzeExpr(checkedExpr.expr);
        boolean enclInvokableHasErrorReturn = false;
        BType exprType = env.enclInvokable.getReturnTypeNode().type;
        if (exprType.tag == TypeTags.UNION) {
            BUnionType unionType = (BUnionType) env.enclInvokable.getReturnTypeNode().type;
            enclInvokableHasErrorReturn = unionType.memberTypes.stream()
                    .anyMatch(memberType -> types.isAssignable(memberType, symTable.errorType));
        } else if (types.isAssignable(exprType, symTable.errorType)) {
            enclInvokableHasErrorReturn = true;
        }

        if (!enclInvokableHasErrorReturn) {
            dlog.error(checkedExpr.expr.pos, DiagnosticCode.CHECKED_EXPR_NO_ERROR_RETURN_IN_ENCL_INVOKABLE);
        }

        returnTypes.peek().add(exprType);
    }

    @Override
    public void visit(BLangErrorConstructorExpr errorConstructorExpr) {
        // TODO: Fix me.
    }

    @Override
    public void visit(BLangServiceConstructorExpr serviceConstructorExpr) {
    }

    @Override
    public void visit(BLangTypeTestExpr typeTestExpr) {
        analyzeNode(typeTestExpr.expr, env);
        if (typeTestExpr.typeNode.type == symTable.semanticError) {
            return;
        }

        // Check whether the condition is always true. If the variable type is assignable to target type,
        // then type check will always evaluate to true. 
        if (types.isAssignable(typeTestExpr.expr.type, typeTestExpr.typeNode.type)) {
            dlog.error(typeTestExpr.pos, DiagnosticCode.UNNECESSARY_CONDITION);
            return;
        }

        // Check whether the target type can ever be present as the type of the source.
        // It'll be only possible iff, the target type has been assigned to the source
        // variable at some point. To do that, a value of target type should be assignable 
        // to the type of the source variable.
        if (!types.isAssignable(typeTestExpr.typeNode.type, typeTestExpr.expr.type)) {
            dlog.error(typeTestExpr.pos, DiagnosticCode.INCOMPATIBLE_TYPE_CHECK, typeTestExpr.expr.type,
                    typeTestExpr.typeNode.type);
        }
    }

    // private methods

    private <E extends BLangExpression> void analyzeExpr(E node) {
        if (node == null) {
            return;
        }
        BLangNode myParent = parent;
        node.parent = parent;
        parent = node;
        node.accept(this);
        parent = myParent;
        checkAccess(node);
    }

    @Override
    public void visit(BLangConstant constant) {
        /* ignore */
    }

    /**
     * This method checks for private symbols being accessed or used outside of package and|or private symbols being
     * used in public fields of objects/records and will fail those occurrences.
     *
     * @param node expression node to analyze
     */
    private <E extends BLangExpression> void checkAccess(E node) {
        if (node.type != null) {
            checkAccessSymbol(node.type.tsymbol, node.pos);
        }

        //check for object new invocation
        if (node.getKind() == NodeKind.INVOCATION) {
            BLangInvocation bLangInvocation = (BLangInvocation) node;
            checkAccessSymbol(bLangInvocation.symbol, bLangInvocation.pos);
        }
    }

    private void checkAccessSymbol(BSymbol symbol, DiagnosticPos position) {
        if (symbol == null) {
            return;
        }

        if (env.enclPkg.symbol.pkgID != symbol.pkgID && !Symbols.isPublic(symbol)) {
            dlog.error(position, DiagnosticCode.ATTEMPT_REFER_NON_ACCESSIBLE_SYMBOL, symbol.name);
        }
    }

    private <E extends BLangExpression> void analyzeExprs(List<E> nodeList) {
        for (int i = 0; i < nodeList.size(); i++) {
            analyzeExpr(nodeList.get(i));
        }
    }

    private void initNewWorkerActionSystem() {
        this.workerActionSystemStack.push(new WorkerActionSystem());
    }

    private void finalizeCurrentWorkerActionSystem() {
        WorkerActionSystem was = this.workerActionSystemStack.pop();
        if (!was.hasErrors) {
            this.validateWorkerInteractions(was);
        }
    }

    private static boolean isWorkerSend(BLangNode action) {
        return action.getKind() == NodeKind.WORKER_SEND;
    }

    private static boolean isWorkerSyncSend(BLangNode action) {
        return action.getKind() == NodeKind.WORKER_SYNC_SEND;
    }

    private String extractWorkerId(BLangNode action) {
        if (isWorkerSend(action)) {
            return ((BLangWorkerSend) action).workerIdentifier.value;
        } else if (isWorkerSyncSend(action)) {
            return ((BLangWorkerSyncSendExpr) action).workerIdentifier.value;
        } else {
            return ((BLangWorkerReceive) action).workerIdentifier.value;
        }
    }

    private void validateWorkerInteractions(WorkerActionSystem workerActionSystem) {
        BLangNode currentAction;
        boolean systemRunning;
        do {
            systemRunning = false;
            for (WorkerActionStateMachine worker : workerActionSystem.finshedWorkers) {
                if (worker.done()) {
                    continue;
                }
                currentAction = worker.currentAction();
                if (!isWorkerSend(currentAction) && !isWorkerSyncSend(currentAction)) {
                    continue;
                }
                WorkerActionStateMachine otherSM = workerActionSystem.find(this.extractWorkerId(currentAction));
                if (otherSM == null || !otherSM.currentIsReceive(worker.workerId)) {
                    continue;
                }
                BLangWorkerReceive receive = (BLangWorkerReceive) otherSM.currentAction();
                if (isWorkerSyncSend(currentAction)) {
                    this.validateWorkerActionParameters((BLangWorkerSyncSendExpr) currentAction, receive);
                } else {
                    this.validateWorkerActionParameters((BLangWorkerSend) currentAction, receive);
                }
                otherSM.next();
                worker.next();

                systemRunning = true;
                String channelName = WorkerDataChannelInfo.generateChannelName(worker.workerId, otherSM.workerId);
                otherSM.node.sendsToThis.add(channelName);

                channelName = WorkerDataChannelInfo.generateChannelName(otherSM.workerId, worker.workerId);
                worker.node.sendsToThis.add(channelName);
            }
        } while (systemRunning);
        if (!workerActionSystem.everyoneDone()) {
            this.reportInvalidWorkerInteractionDiagnostics(workerActionSystem);
        }
    }

    private void reportInvalidWorkerInteractionDiagnostics(WorkerActionSystem workerActionSystem) {
        this.dlog.error(workerActionSystem.getRootPosition(), DiagnosticCode.INVALID_WORKER_INTERACTION,
                workerActionSystem.toString());
    }

    private void validateWorkerActionParameters(BLangWorkerSend send, BLangWorkerReceive receive) {
        types.checkType(receive, send.type, receive.type);
        addImplicitCast(send.type, receive);
        NodeKind kind = receive.parent.getKind();
        if (kind == NodeKind.TRAP_EXPR || kind == NodeKind.CHECK_EXPR) {
            typeChecker.checkExpr((BLangExpression) receive.parent, receive.env);
        }
    }

    private void validateWorkerActionParameters(BLangWorkerSyncSendExpr send, BLangWorkerReceive receive) {
        this.typeChecker.checkExpr(send.expr, send.env, receive.type);
        types.checkType(send, send.type, receive.matchingSendsError);
        addImplicitCast(send.expr.type, receive);
    }

    private void addImplicitCast(BType actualType, BLangWorkerReceive receive) {
        if (receive.type != null && receive.type != symTable.semanticError) {
            types.setImplicitCastExpr(receive, actualType, receive.type);
            receive.type = actualType;
        }
    }

    private boolean checkNextBreakValidityInTransaction() {
        return !this.loopWithintransactionCheckStack.peek() && transactionCount > 0;
    }

    private boolean checkReturnValidityInTransaction() {
        return (this.returnWithintransactionCheckStack.empty() || !this.returnWithintransactionCheckStack.peek())
                && transactionCount > 0;
    }

    private boolean isValidTransactionBlock() {
        return !(this.withinRetryBlock || this.withinAbortedBlock || this.withinCommittedBlock);
    }

    private void validateMainFunction(BLangFunction funcNode) {
        if (!MAIN_FUNCTION_NAME.equals(funcNode.name.value)) {
            return;
        }
        if (!Symbols.isPublic(funcNode.symbol)) {
            this.dlog.error(funcNode.pos, DiagnosticCode.MAIN_SHOULD_BE_PUBLIC);
        }
    }

    private void checkDuplicateNamedArgs(List<BLangExpression> args) {
        List<BLangIdentifier> existingArgs = new ArrayList<>();
        args.forEach(arg -> {
            BLangNamedArgsExpression namedArg = (BLangNamedArgsExpression) arg;
            if (existingArgs.contains(namedArg.name)) {
                dlog.error(namedArg.pos, DiagnosticCode.DUPLICATE_NAMED_ARGS, namedArg.name);
            }
            existingArgs.add(namedArg.name);
        });
    }

    /**
     * This class contains the state machines for a set of workers.
     */
    private static class WorkerActionSystem {

        public List<WorkerActionStateMachine> finshedWorkers = new ArrayList<>();
        private Stack<WorkerActionStateMachine> workerActionStateMachines = new Stack<>();
        private boolean hasErrors = false;


        public void startWorkerActionStateMachine(String workerId, DiagnosticPos pos, BLangFunction node) {
            workerActionStateMachines.push(new WorkerActionStateMachine(pos, workerId, node));
        }

        public void endWorkerActionStateMachine() {
            finshedWorkers.add(workerActionStateMachines.pop());
        }

        public void addWorkerAction(BLangNode action) {
            this.workerActionStateMachines.peek().actions.add(action);
        }

        public WorkerActionStateMachine find(String workerId) {
            for (WorkerActionStateMachine worker : this.finshedWorkers) {
                if (worker.workerId.equals(workerId)) {
                    return worker;
                }
            }
            throw new AssertionError("Reference to non existing worker " + workerId);
        }

        public boolean everyoneDone() {
            return this.finshedWorkers.stream().allMatch(WorkerActionStateMachine::done);
        }

        public DiagnosticPos getRootPosition() {
            return this.finshedWorkers.iterator().next().pos;
        }

        @Override
        public String toString() {
            return this.finshedWorkers.toString();
        }

        public String currentWorkerId() {
            return workerActionStateMachines.peek().workerId;
        }
    }

    /**
     * This class represents a state machine to maintain the state of the send/receive
     * actions of a worker.
     */
    private static class WorkerActionStateMachine {

        private static final String WORKER_SM_FINISHED = "FINISHED";

        public int currentState;

        public List<BLangNode> actions = new ArrayList<>();

        public DiagnosticPos pos;
        public String workerId;
        public BLangFunction node;

        public WorkerActionStateMachine(DiagnosticPos pos, String workerId, BLangFunction node) {
            this.pos = pos;
            this.workerId = workerId;
            this.node = node;
        }

        public boolean done() {
            return this.actions.size() == this.currentState;
        }

        public BLangNode currentAction() {
            return this.actions.get(this.currentState);
        }

        public boolean currentIsReceive(String sourceWorkerId) {
            if (this.done()) {
                return false;
            }
            BLangNode action = this.currentAction();
            return !isWorkerSend(action) && !isWorkerSyncSend(action) &&
                    ((BLangWorkerReceive) action).workerIdentifier.value.equals(sourceWorkerId);
        }

        public void next() {
            this.currentState++;
        }

        @Override
        public String toString() {
            if (this.done()) {
                return WORKER_SM_FINISHED;
            } else {
                BLangNode action = this.currentAction();
                if (isWorkerSend(action)) {
                    return ((BLangWorkerSend) action).toActionString();
                } else if (isWorkerSyncSend(action)) {
                    return ((BLangWorkerSyncSendExpr) action).toActionString();
                } else {
                    return ((BLangWorkerReceive) action).toActionString();
                }
            }
        }
    }
}
