/**
 * Copyright (c) 2010, 2013 Darmstadt University of Technology.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Marcel Bruch - initial API and implementation.
 */
package org.eclipse.recommenders.calls;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Optional.*;
import static com.google.common.collect.Collections2.*;
import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Lists.newLinkedList;
import static org.eclipse.recommenders.calls.Recommendation.newRecommendation;
import static org.eclipse.recommenders.utils.Constants.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.recommenders.calls.DefinitionSite.Kind;
import org.eclipse.recommenders.commons.bayesnet.BayesianNetwork;
import org.eclipse.recommenders.commons.bayesnet.Node;
import org.eclipse.recommenders.jayes.BayesNet;
import org.eclipse.recommenders.jayes.BayesNode;
import org.eclipse.recommenders.jayes.inference.junctionTree.JunctionTreeAlgorithm;
import org.eclipse.recommenders.utils.Constants;
import org.eclipse.recommenders.utils.names.IFieldName;
import org.eclipse.recommenders.utils.names.IMethodName;
import org.eclipse.recommenders.utils.names.ITypeName;
import org.eclipse.recommenders.utils.names.VmMethodName;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Lists;

/**
 * Expected structure:
 * 
 * <ul>
 * <li>every node must have at least <b>2 states</b>!
 * <li>the first state is supposed to be a dummy state. Call it like
 * {@link Constants#N_STATE_DUMMY_CTX}
 * <li>the second state <b>may</b> to be a dummy state too if no valuable other
 * state could be found.
 * </ul>
 * 
 * <ul>
 * <li><b>callgroup node (formerly called pattern node):</b>
 * <ul>
 * <li>node name: {@link Constants#N_NODEID_CALL_GROUPS}
 * <li>state names: no constraints. recommended schema is to use 'p'#someNumber.
 * </ul>
 * <li><b>context node:</b>
 * <ul>
 * <li>node name: {@link Constants#N_NODEID_CONTEXT}
 * <li>state names: fully-qualified method names as returned by
 * {@link IMethodName#getIdentifier()}.
 * </ul>
 * <li><b>definition node:</b>
 * <ul>
 * <li>node name: {@link Constants#N_NODEID_DEF}
 * <li>state names: fully-qualified names as returned by
 * {@link IMethodName#getIdentifier()} or {@link IFieldName#getIdentifier()}.
 * </ul>
 * <li><b>definition kind node:</b>
 * <ul>
 * <li>node name: {@link Constants#N_NODEID_DEF_KIND}
 * <li>state names: one of {@link DefinitionSite.Kind}, i.e., METHOD_RETURN,
 * NEW, FIELD, PARAMETER, THIS, UNKNOWN, or ANY
 * </ul>
 * <li><b>method call node:</b>
 * <ul>
 * <li>node name: {@link IMethodName#getIdentifier()}
 * <li>state names: {@link Constants#N_STATE_TRUE} or
 * {@link Constants#N_STATE_FALSE}
 * </ul>
 * </ul>
 */

public class JayesCallRecommendationNet implements ICallRecommendationNet {

    private final class StringToMethodNameFunction implements Function<String, IMethodName> {
        @Override
        public IMethodName apply(String input) {
            return VmMethodName.get(input);
        }
    }

    private BayesNet net;
    private BayesNode callgroupNode;
    private BayesNode contextNode;
    private BayesNode definitionNode;
    private BayesNode kindNode;
    private JunctionTreeAlgorithm junctionTreeAlgorithm;

    private ITypeName typeName;
    private HashMap<IMethodName, BayesNode> callNodes;

    public JayesCallRecommendationNet(ITypeName name, BayesianNetwork network) {
        initalizeIndexes(name);
        initializeNetwork(network);
    }

    private void initalizeIndexes(ITypeName name) {
        this.typeName = name;
        callNodes = new HashMap<IMethodName, BayesNode>();
    }

    private void initializeNetwork(BayesianNetwork network) {
        net = new BayesNet();
        initializeNodes(network);
        initializeArcs(network);
        initializeProbabilities(network);

        junctionTreeAlgorithm = new JunctionTreeAlgorithm();
        junctionTreeAlgorithm.setNetwork(net);
    }

    private void initializeNodes(BayesianNetwork network) {
        Collection<Node> nodes = network.getNodes();
        for (Node node : nodes) {
            BayesNode bayesNode = new BayesNode(node.getIdentifier());
            String[] states = node.getStates();
            for (int i = 0; i < states.length; i++) {
                bayesNode.addOutcome(states[i]);
            }
            net.addNode(bayesNode);

            if (node.getIdentifier().equals(N_NODEID_CONTEXT)) {
                contextNode = bayesNode;
            } else if (node.getIdentifier().equals(N_NODEID_CALL_GROUPS)) {
                callgroupNode = bayesNode;
            } else if (node.getIdentifier().equals(N_NODEID_DEF_KIND)) {
                kindNode = bayesNode;
            } else if (node.getIdentifier().equals(N_NODEID_DEF)) {
                definitionNode = bayesNode;
            } else {
                VmMethodName vmMethodName = VmMethodName.get(node.getIdentifier());
                callNodes.put(vmMethodName, bayesNode);
            }
        }
    }

    private void initializeArcs(BayesianNetwork network) {
        Collection<Node> nodes = network.getNodes();
        for (Node node : nodes) {
            Node[] parents = node.getParents();
            BayesNode children = net.getNode(node.getIdentifier());
            LinkedList<BayesNode> bnParents = new LinkedList<BayesNode>();
            for (int i = 0; i < parents.length; i++) {
                bnParents.add(net.getNode(parents[i].getIdentifier()));
            }
            children.setParents(bnParents);
        }
    }

    private void initializeProbabilities(BayesianNetwork network) {
        Collection<Node> nodes = network.getNodes();
        for (Node node : nodes) {
            BayesNode bayesNode = net.getNode(node.getIdentifier());
            bayesNode.setProbabilities(node.getProbabilities());
        }
    }

    private Optional<IMethodName> computeMethodNameFromState(BayesNode node) {
        String stateId = junctionTreeAlgorithm.getEvidence().get(node);
        if (stateId == null) {
            return absent();
        }
        return Optional.<IMethodName> of(VmMethodName.get(stateId));
    }

    @Override
    public ImmutableSet<IMethodName> getKnownCalls() {
        return ImmutableSet.<IMethodName> builder().addAll(callNodes.keySet()).build();
    }

    @Override
    public ImmutableSet<IMethodName> getKnownEnclosingMethods() {
        Collection<IMethodName> tmp = transform(contextNode.getOutcomes(), new StringToMethodNameFunction());
        return copyOf(tmp);
    }

    @Override
    public ImmutableSet<String> getKnownPatterns() {
        return copyOf(callgroupNode.getOutcomes());
    }

    @Override
    public ImmutableSet<IMethodName> getObservedCalls() {
        Builder<IMethodName> builder = ImmutableSet.<IMethodName> builder();
        Map<BayesNode, String> evidence = junctionTreeAlgorithm.getEvidence();
        for (Entry<IMethodName, BayesNode> pair : callNodes.entrySet()) {
            BayesNode node = pair.getValue();
            IMethodName method = pair.getKey();
            if (evidence.containsKey(node) && evidence.get(node).equals(Constants.N_STATE_TRUE)
                    // remove the NULL that may have been introduced by
                    // res.add(compute...)
                    && !VmMethodName.NULL.equals(method)) {
                builder.add(method);
            }
        }
        return builder.build();
    }

    @Override
    public Optional<IMethodName> getObservedDefinition() {
        return computeMethodNameFromState(definitionNode);
    }

    @Override
    public Optional<IMethodName> getObservedEnclosingMethod() {
        return computeMethodNameFromState(contextNode);
    }

    @Override
    public Optional<Kind> getObservedKind() {
        String stateId = junctionTreeAlgorithm.getEvidence().get(kindNode);
        if (stateId == null) {
            return absent();
        }
        return of(Kind.valueOf(stateId));
    }

    @Override
    public List<Recommendation<IMethodName>> getRecommendedCalls(Predicate<? super Recommendation<IMethodName>> filter,
            Comparator<? super Recommendation<IMethodName>> comparator, int maxLength) {
        List<Recommendation<IMethodName>> l = Lists.newLinkedList();
        for (IMethodName method : callNodes.keySet()) {
            BayesNode bayesNode = callNodes.get(method);
            boolean isAlreadyUsedAsEvidence = junctionTreeAlgorithm.getEvidence().containsKey(bayesNode);
            if (!isAlreadyUsedAsEvidence) {
                int indexForTrue = bayesNode.getOutcomeIndex(N_STATE_TRUE);
                double[] probabilities = junctionTreeAlgorithm.getBeliefs(bayesNode);
                double probability = probabilities[indexForTrue];
                l.add(newRecommendation(method, probability));
            }
        }
        return filterSortAndChomp(l, filter, comparator, maxLength);
    }

    private <T> List<Recommendation<T>> filterSortAndChomp(List<Recommendation<T>> l,
            Predicate<? super Recommendation<T>> filter, Comparator<? super Recommendation<T>> comparator, int maxLength) {
        List<Recommendation<T>> filtered = newLinkedList(filter(l, filter));
        Collections.sort(filtered, comparator);
        return filtered.subList(0, Math.min(filtered.size(), maxLength));
    }

    @Override
    public List<Recommendation<IMethodName>> getRecommendedDefinitions(
            Predicate<? super Recommendation<IMethodName>> filter,
                    Comparator<? super Recommendation<IMethodName>> comparator, int maxLength) {
        List<Recommendation<IMethodName>> l = Lists.newLinkedList();
        double[] beliefs = junctionTreeAlgorithm.getBeliefs(definitionNode);
        for (int i = definitionNode.getOutcomeCount(); i-- > 0;) {
            if (beliefs[i] > 0.05) {
                String outcomeName = definitionNode.getOutcomeName(i);
                if (outcomeName.equals("LNone.none()V")) {
                    continue;
                }
                if (outcomeName.equals(UNKNOWN_METHOD.getIdentifier())) {
                    continue;
                }
                VmMethodName definition = VmMethodName.get(outcomeName);
                Recommendation<IMethodName> r = newRecommendation(definition, beliefs[i]);
                l.add(r);
            }
        }
        return filterSortAndChomp(l, filter, comparator, maxLength);
    }

    @Override
    public List<Recommendation<String>> getRecommendedPatterns(Predicate<? super Recommendation<String>> filter,
            Comparator<? super Recommendation<String>> comparator, int maxLength) {
        List<Recommendation<String>> l = Lists.newLinkedList();
        double[] probs = junctionTreeAlgorithm.getBeliefs(callgroupNode);
        for (String outcome : callgroupNode.getOutcomes()) {
            int probIndex = callgroupNode.getOutcomeIndex(outcome);
            double p = probs[probIndex];
            l.add(newRecommendation(outcome, p));
        }
        return filterSortAndChomp(l, filter, comparator, maxLength);
    }

    @Override
    public ITypeName getType() {
        return typeName;
    }

    @Override
    public void reset() {
        junctionTreeAlgorithm.setEvidence(new HashMap<BayesNode, String>());
    }

    public boolean setCalled(IMethodName calledMethod, String state) {
        BayesNode node = net.getNode(calledMethod.getIdentifier());
        if (node != null) {
            junctionTreeAlgorithm.addEvidence(node, state);
        }
        return node != null;
    }

    @Override
    public boolean setObservedCall(IMethodName calledMethod) {
        return setCalled(calledMethod, N_STATE_TRUE);
    }

    @Override
    public boolean setObservedCalls(Set<IMethodName> additionalCalledMethods) {
        boolean pass = true;
        for (IMethodName m : additionalCalledMethods) {
            IMethodName rebased = VmMethodName.rebase(typeName, m);
            pass &= setObservedCall(rebased);
        }

        IMethodName no = VmMethodName.rebase(typeName, Constants.NO_METHOD);
        pass &= setCalled(no, N_STATE_FALSE);
        return pass;
    }

    @Override
    public boolean setObservedDefinition(IMethodName newDefinition) {
        newDefinition = firstNonNull(newDefinition, DUMMY_METHOD);
        String identifier = newDefinition.getIdentifier();
        boolean contains = definitionNode.getOutcomes().contains(identifier);
        if (contains) {
            junctionTreeAlgorithm.addEvidence(definitionNode, identifier);
        }
        return contains;
    }

    @Override
    public boolean setObservedEnclosingMethod(IMethodName newEnclosingMethod) {
        newEnclosingMethod = firstNonNull(newEnclosingMethod, UNKNOWN_METHOD);
        String id = newEnclosingMethod.getIdentifier();
        boolean contains = contextNode.getOutcomes().contains(id);
        if (contains) {
            junctionTreeAlgorithm.addEvidence(contextNode, id);
        }
        return contains;
    }

    @Override
    public boolean setObservedKind(DefinitionSite.Kind newKind) {
        newKind = firstNonNull(newKind, Kind.UNKNOWN);
        String identifier = newKind.toString();
        boolean contains = kindNode.getOutcomes().contains(identifier);
        if (contains) {
            junctionTreeAlgorithm.addEvidence(kindNode, identifier);
        }
        return contains;
    }

    @Override
    public boolean setObservedPattern(String patternName) {
        boolean contains = callgroupNode.getOutcomes().contains(patternName);
        if (contains) {
            junctionTreeAlgorithm.addEvidence(callgroupNode, patternName);
        }
        ;
        return contains;
    }
}
