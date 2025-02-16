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
import mfix.core.node.ast.expr.Svd;
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
public class CatClause extends Node {

	private static final long serialVersionUID = 8636697940678019414L;
	private Svd _exception = null;
	private Blk _blk = null; 
	
	/**
	 * CatchClause
	 *    catch ( SingleVariableDeclaration ) Block
	 */
	public CatClause(String fileName, int startLine, int endLine, ASTNode oriNode) {
		this(fileName, startLine, endLine, oriNode, null);
	}
	
	public CatClause(String fileName, int startLine, int endLine, ASTNode oriNode, Node parent) {
		super(fileName, startLine, endLine, oriNode, parent);
		_nodeType = TYPE.CATCHCLAUSE;
		_fIndex = VIndex.STMT_CATCH;
	}
	
	public void setException(Svd svd) {
		_exception = svd;
	}
	
	public Svd getException() {
		return _exception;
	}
	
	public void setBody(Blk blk) {
		_blk = blk;
	}
	
	public Blk getBody() {
		return _blk;
	}

	@Override
	public StringBuffer toSrcString() {
		StringBuffer stringBuffer = new StringBuffer();
		stringBuffer.append("catch(");
		stringBuffer.append(_exception.toSrcString());
		stringBuffer.append(")");
		stringBuffer.append(_blk.toSrcString());
		return stringBuffer;
	}

	@Override
	protected StringBuffer toFormalForm0(NameMapping nameMapping, boolean parentConsidered, Set<String> keywords) {
		if (isAbstract() && !isConsidered()) return null;
		StringBuffer excep = _exception.formalForm(nameMapping, isConsidered(), keywords);
		StringBuffer blk = _blk.formalForm(nameMapping, isConsidered(), keywords);
		if (excep == null && blk == null) {
			return isConsidered() ? new StringBuffer("catch(").append(nameMapping.getExprID(_exception))
					.append(')').append("{}") : null;
		}
		StringBuffer buffer = new StringBuffer("catch(");
		buffer.append(excep == null ? nameMapping.getExprID(_exception) : excep)
				.append(')').append(blk == null ? "{}" : blk);
		return buffer;
	}

	@Override
	public boolean patternMatch(Node node, Map<Node, Node> matchedNode) {
		if (node == null || isConsidered() != node.isConsidered()) {
			return false;
		}
		if (isConsidered()) {
			return node.getNodeType() == TYPE.CATCHCLAUSE
					&& NodeUtils.patternMatch(this, node, matchedNode);
		}
		return node instanceof Stmt;
	}

	@Override
	protected void tokenize() {
		_tokens = new LinkedList<>();
		_tokens.add("catch");
		_tokens.add("(");
		_tokens.addAll(_exception.tokens());
		_tokens.add(")");
		_tokens.addAll(_blk.tokens());
	}

	@Override
	public Stmt getParentStmt() {
		return getParent().getParentStmt();
	}

	@Override
	public List<Stmt> getChildren() {
		List<Stmt> children = new ArrayList<>(1);
		children.add(_blk);
		return children;
	}
	
	@Override
	public List<Node> getAllChildren() {
		List<Node> children = new ArrayList<>(2);
		children.add(_exception);
		children.add(_blk);
		return children;
	}
	
	@Override
	public boolean compare(Node other) {
		if(other != null && other instanceof CatClause) {
			CatClause catClause = (CatClause) other;
			return _exception.compare(catClause._exception) && _blk.compare(catClause._blk);
		}
		return false;
	}
	
	@Override
	public void computeFeatureVector() {
		_selfFVector = new FVector();
		_selfFVector.inc(FVector.KEY_CATCH);

		_completeFVector = new FVector();
		_completeFVector.inc(FVector.KEY_CATCH);
		_completeFVector.combineFeature(_exception.getFeatureVector());
		_completeFVector.combineFeature(_blk.getFeatureVector());
	}

	@Override
	public boolean postAccurateMatch(Node node) {
		CatClause catClause = null;
		boolean match = false;
		if (getBindingNode() != null && (getBindingNode() == node || !compare(node))) {
			catClause = (CatClause) getBindingNode();
			match = false;
		} else if (canBinding(node)) {
			catClause = (CatClause) node;
			setBindingNode(node);
			match = true;
		}
		if(catClause == null) {
			continueTopDownMatchNull();
		} else {
			_exception.postAccurateMatch(catClause.getException());
			_blk.postAccurateMatch(catClause.getBody());
		}
		return match;
	}

	@Override
	public boolean genModifications() {
		if (getBindingNode() != null) {
			CatClause catClause = (CatClause) getBindingNode();
			if(_exception.getBindingNode() != catClause.getException()) {
				Update update = new Update(this, _exception, catClause.getException());
				_modifications.add(update);
			} else {
				_exception.genModifications();
			}
			_blk.genModifications();
			return true;
		}
		return false;
	}

	@Override
	public boolean ifMatch(Node node, Map<Node, Node> matchedNode, Map<String, String> matchedStrings, MatchLevel level) {
		if(node instanceof CatClause) {
			CatClause catClause = (CatClause) node;
			return _exception.ifMatch(catClause.getException(), matchedNode, matchedStrings, level)
					&& _blk.ifMatch(catClause.getBody(), matchedNode, matchedStrings, level)
					&& NodeUtils.checkDependency(this, node, matchedNode, matchedStrings, level)
					&& NodeUtils.matchSameNodeType(this, node, matchedNode, matchedStrings);
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
			stringBuffer.append("catch(");
			metric.inc();
			tmp = _exception.transfer(vars, exprMap, retType, exceptions, metric);
			if(tmp == null) return null;
			stringBuffer.append(tmp);
			stringBuffer.append(")");
			tmp = _blk.transfer(vars, exprMap, retType, exceptions, metric);
			if(tmp == null) return null;
			stringBuffer.append(tmp);
		}
		return stringBuffer;
	}

	@Override
	public StringBuffer adaptModifications(VarScope vars, Map<String, String> exprMap, String retType,
                                           Set<String> exceptions, Adaptee metric) {
		StringBuffer exception = null;
		Node pnode = NodeUtils.checkModification(this);
		if (pnode != null) {
			CatClause catClause = (CatClause) pnode;
			for (Modification modification : catClause.getModifications()) {
				if (modification instanceof Update) {
					Update update = (Update) modification;
					if (update.getSrcNode() == catClause._exception) {
						exception = update.apply(vars, exprMap, retType, exceptions, metric);
						if (exception == null) return null;
					}
				} else {
					LevelLogger.error("@CatClause Should not be this kind of modification : " + modification);
				}
			}
		}
		StringBuffer stringBuffer = new StringBuffer();
		StringBuffer tmp;
		stringBuffer.append("catch(");
		if(exception == null) {
			tmp = _exception.adaptModifications(vars, exprMap, retType, exceptions, metric);
			if(tmp == null) return null;
			stringBuffer.append(tmp);
		} else {
			stringBuffer.append(exception);
		}
		stringBuffer.append(")");
		tmp = _blk.adaptModifications(vars, exprMap, retType, exceptions, metric);
		if (tmp == null) return null;
		stringBuffer.append(tmp);
		return stringBuffer;
	}
}
