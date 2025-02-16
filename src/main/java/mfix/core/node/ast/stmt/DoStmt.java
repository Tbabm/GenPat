/**
 * Copyright (C) SEI, PKU, PRC. - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited Proprietary and Confidential.
 * Written by Jiajun Jiang<jiajun.jiang@pku.edu.cn>.
 */
package mfix.core.node.ast.stmt;

import mfix.common.util.LevelLogger;
import mfix.core.node.NodeUtils;
import mfix.core.node.match.MatchLevel;
import mfix.core.node.ast.Node;
import mfix.core.node.ast.VarScope;
import mfix.core.node.ast.expr.Expr;
import mfix.core.node.match.metric.FVector;
import mfix.core.node.modify.Adaptee;
import mfix.core.node.modify.Modification;
import mfix.core.node.modify.Update;
import mfix.core.pattern.cluster.NameMapping;
import mfix.core.pattern.cluster.VIndex;
import org.eclipse.jdt.core.dom.ASTNode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author: Jiajun
 * @date: 2018/9/21
 */
public class DoStmt extends Stmt {

	private static final long serialVersionUID = -8707533085331564948L;
	private Stmt _stmt = null;
	private Expr _expression = null;
	
	/**
	 * DoStatement:
     *	do Statement while ( Expression ) ;
	 */
	public DoStmt(String fileName, int startLine, int endLine, ASTNode node) {
		this(fileName, startLine, endLine, node, null);
	}

	public DoStmt(String fileName, int startLine, int endLine, ASTNode node, Node parent) {
		super(fileName, startLine, endLine, node, parent);
		_nodeType = TYPE.DO;
		_fIndex = VIndex.STMT_DO;
	}
	
	public void setBody(Stmt stmt){
		_stmt = stmt;
	}
	
	public Stmt getBody() {
		return _stmt;
	}
	
	public void setExpression(Expr expression){
		_expression = expression;
	}
	
	public Expr getExpression() {
		return _expression;
	}

	@Override
	public StringBuffer toSrcString() {
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("do ");
		stringBuffer.append(_stmt.toSrcString());
		stringBuffer.append(" while(");
		stringBuffer.append(_expression.toSrcString());
		stringBuffer.append(");");
		return stringBuffer;
	}

	@Override
	protected StringBuffer toFormalForm0(NameMapping nameMapping, boolean parentConsidered, Set<String> keywords) {
		if (isAbstract() && !isConsidered()) return null;
		StringBuffer body = _stmt.formalForm(nameMapping, false, keywords);
		StringBuffer cond = _expression.formalForm(nameMapping, isConsidered(), keywords);
		if (body == null && cond == null) {
			if (isConsidered()) {
				return new StringBuffer("do {} while(").append(nameMapping.getExprID(_expression)).append(')');
			} else {
				return null;
			}
		}
		StringBuffer buffer = new StringBuffer("do ");
		buffer.append(body == null ? "{}" : body).append(" while(")
				.append(cond == null ? nameMapping.getExprID(_expression) : cond).append(')');
		return buffer;
	}

	@Override
	protected void tokenize() {
		_tokens = new LinkedList<>();
		_tokens.add("do");
		_tokens.addAll(_stmt.tokens());
		_tokens.add("while");
		_tokens.add("(");
		_tokens.addAll(_expression.tokens());
		_tokens.add(")");
	}
	
	@Override
	public List<Node> getAllChildren() {
		List<Node> children = new ArrayList<>(2);
		children.add(_stmt);
		children.add(_expression);
		return children;
	}
	
	@Override
	public List<Stmt> getChildren() {
		List<Stmt> children = new ArrayList<>(1);
		if(_stmt != null) {
			children.add(_stmt);
		}
		return children;
	}
	
	@Override
	public boolean compare(Node other) {
		boolean match = false;
		if(other != null && other instanceof DoStmt) {
			DoStmt doStmt = (DoStmt) other;
			match = _expression.compare(doStmt._expression) && _stmt.compare(doStmt._stmt);
		}
		return match;
	}
	
	@Override
	public void computeFeatureVector() {
		_selfFVector = new FVector();
		_selfFVector.inc(FVector.KEY_DO);

		_completeFVector = new FVector();
		_completeFVector.inc(FVector.KEY_DO);
		_completeFVector.combineFeature(_expression.getFeatureVector());
		_completeFVector.combineFeature(_stmt.getFeatureVector());
	}

	@Override
	public boolean postAccurateMatch(Node node) {
		boolean match = false;
		DoStmt doStmt = null;
		if (getBindingNode() != null && (getBindingNode() == node || !compare(node))) {
			doStmt = (DoStmt) getBindingNode();
			_expression.postAccurateMatch(doStmt.getExpression());
			_stmt.postAccurateMatch(doStmt.getBody());
			return doStmt == node;
		} else if (canBinding(node)) {
			doStmt = (DoStmt) node;
			if (_expression.postAccurateMatch(doStmt.getExpression())) {
				_stmt.postAccurateMatch(doStmt.getBody());
				setBindingNode(node);
				match = true;
			} else {
				doStmt = null;
			}
		}

		if(doStmt == null) {
			continueTopDownMatchNull();
		}
		return match;
	}

	@Override
	public boolean genModifications() {
		if(super.genModifications()) {
			DoStmt doStmt = (DoStmt) getBindingNode();
			if(_expression.getBindingNode() != doStmt.getExpression()) {
				Update update = new Update(this, _expression, doStmt.getExpression());
				_modifications.add(update);
			} else {
				_expression.genModifications();
			}
			_stmt.genModifications();
			return true;
		}
		return false;
	}

	@Override
	public void greedyMatchBinding(Node node, Map<Node, Node> matchedNode, Map<String, String> matchedStrings) {
		if (node instanceof DoStmt) {
			DoStmt doStmt = (DoStmt) node;
			NodeUtils.matchSameNodeType(getExpression(), doStmt.getExpression(), matchedNode, matchedStrings);
			NodeUtils.matchSameNodeType(getBody(), doStmt.getBody(), matchedNode, matchedStrings);
		}
	}

	@Override
	public boolean ifMatch(Node node, Map<Node, Node> matchedNode, Map<String, String> matchedStrings, MatchLevel level) {
		if(node instanceof DoStmt) {
			DoStmt doStmt = (DoStmt) node;
			return _expression.ifMatch(doStmt.getExpression(), matchedNode, matchedStrings, level)
					&& _stmt.ifMatch(doStmt.getBody(), matchedNode, matchedStrings, level)
					&& super.ifMatch(node, matchedNode, matchedStrings, level);
		}
		return false;
	}

	@Override
	public StringBuffer transfer(VarScope vars, Map<String, String> exprMap, String retType, Set<String> exceptions,
                                 Adaptee metric) {
		StringBuffer stringBuffer = super.transfer(vars, exprMap, retType, exceptions, metric);
		if (stringBuffer == null) {
			stringBuffer = new StringBuffer();
			StringBuffer tmp;
			stringBuffer.append("do ");
			metric.inc();
			tmp = _stmt.transfer(vars, exprMap, retType, exceptions, metric);
			if(tmp == null) return null;
			stringBuffer.append(tmp);
			stringBuffer.append(" while(");
			tmp = _expression.transfer(vars, exprMap, retType, exceptions, metric);
			if(tmp == null) return null;
			stringBuffer.append(tmp);
			stringBuffer.append(");");
		}
		return stringBuffer;
	}

	@Override
	public StringBuffer adaptModifications(VarScope vars, Map<String, String> exprMap, String retType,
                                           Set<String> exceptions, Adaptee metric) {
		StringBuffer stmt = null;
		StringBuffer expression = null;
		Node pnode = NodeUtils.checkModification(this);
		if (pnode != null) {
			DoStmt doStmt = (DoStmt) pnode;
			for (Modification modification : doStmt.getModifications()) {
				if (modification instanceof Update) {
					Update update = (Update) modification;
					if (update.getSrcNode() == doStmt._expression) {
						expression = update.apply(vars, exprMap, retType, exceptions, metric);
						if (expression == null) return null;
					}
				} else {
					LevelLogger.error("@DoStmt Should not be this kind of modification : " + modification);
				}
			}
		}
		StringBuffer stringBuffer = new StringBuffer();
		StringBuffer tmp;
		stringBuffer.append("do ");
		if (stmt == null) {
			tmp = _stmt.adaptModifications(vars, exprMap, retType, exceptions, metric);
			if (tmp == null) return null;
			stringBuffer.append(tmp);
		} else {
			stringBuffer.append(stmt);
		}
		stringBuffer.append(" while(");
		if (expression == null) {
			tmp = _expression.adaptModifications(vars, exprMap, retType, exceptions, metric);
			if (tmp == null) return null;
			stringBuffer.append(tmp);
		} else {
			stringBuffer.append(expression);
		}
		stringBuffer.append(");");
		return stringBuffer;
	}
}
