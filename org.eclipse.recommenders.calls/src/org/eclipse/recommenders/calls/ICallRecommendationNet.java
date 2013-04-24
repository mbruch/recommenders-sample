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

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.eclipse.recommenders.calls.DefinitionSite.Kind;
import org.eclipse.recommenders.utils.names.IMethodName;
import org.eclipse.recommenders.utils.names.ITypeName;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;

/**
 * Thin layer around a Bayesian network designed for recommending method calls,
 * definitions, and unordered object usage patterns (list of calls).
 */
public interface ICallRecommendationNet {

    /**
     * Returns the type this net makes recommendations for.
     */
    ITypeName getType();

    /**
     * Clears all observations and puts the network in its initial state.
     */
    void reset();

    /**
     * Adds a new observed call to the list of already observed calls. If the
     * given method does not exist in this net it gets
     * 
     * @return returns <code>true</code> when a matching method node was found
     *         and put into observed state
     */
    boolean setObservedCall(final IMethodName additionalCalledMethod);

    /**
     * Sets the observed state for the given called methods. Note that the state
     * previous called methods is kept.
     * 
     * @return returns <code>true</code> when for <b>all</b> given methods a
     *         matching method node was found and put into observed state
     * @see #setObservedCall(IMethodName)
     */
    boolean setObservedCalls(final Set<IMethodName> additionalCalledMethods);

    /**
     * Sets the enclosing method context. This is usually the name of the
     * topmost declaration method of the enlcosing method.
     * 
     * @return returns <code>true</code> when the enclosing method context is
     *         known
     */
    boolean setObservedEnclosingMethod(final IMethodName newEnclosingMethod);

    boolean setObservedKind(final DefinitionSite.Kind newKind);

    /**
     * Sets the information how the variable was initially defined.
     * 
     * Depending on the definition kind, the method specified here will be
     * <ul>
     * <li> {@link DefinitionSite.Kind#METHOD_RETURN}: the method whose return
     * value defines the variable,
     * <li>{@link DefinitionSite.Kind#PARAMETER}: the method this variable was
     * defined as a parameter for,
     * <li>{@link DefinitionSite.Kind#NEW}: the constructor this variable
     * initialized with
     * </ul>
     * 
     * @return
     */
    boolean setObservedDefinition(final IMethodName newDefinition);

    /**
     * Sets the given pattern as observed. Does nothing when the given name is
     * not known.
     */
    boolean setObservedPattern(final String newPatternName);

    /**
     * Returns the currently observed enclosing method - if any.
     */
    Optional<IMethodName> getObservedEnclosingMethod();

    /**
     * Returns the currently observed method that defined the variable under
     * recommendation - if any.
     */
    Optional<IMethodName> getObservedDefinition();

    /**
     * Returns the observed variable kind - if any.
     */
    Optional<Kind> getObservedKind();

    /**
     * Returns a list of observed methods flagged as being observed.
     */
    ImmutableSet<IMethodName> getObservedCalls();

    /**
     * Returns the list of all known callable methods that can be observed.
     * 
     * @see #setObservedCall(IMethodName)
     * */
    ImmutableSet<IMethodName> getKnownCalls();

    /**
     * Returns the list of all known enclosing methods that can be observed.
     * 
     * @see #setObservedEnclosingMethod(IMethodName)
     */
    ImmutableSet<IMethodName> getKnownEnclosingMethods();

    /**
     * Returns the list of all known patterns names that can be observed.
     * 
     * @see #setObservedPattern(String)
     */
    ImmutableSet<String> getKnownPatterns();

    /**
     * Returns a sorted set of recommended variable definitions.
     * 
     * @see Helpers#filterByMinRelevance(double)
     * @see Helpers#compareByRelevance()
     */
    List<Recommendation<IMethodName>> getRecommendedDefinitions(Predicate<? super Recommendation<IMethodName>> filter,
            Comparator<? super Recommendation<IMethodName>> comparator, int maxLength);

    /**
     * Returns a sorted set of recommended usage patterns.
     * 
     * @see Helpers#filterByMinRelevance(double)
     * @see Helpers#compareByRelevance()
     */
    List<Recommendation<String>> getRecommendedPatterns(Predicate<? super Recommendation<String>> filter,
            Comparator<? super Recommendation<String>> comparator, int maxLength);

    /**
     * Returns a sorted set of recommended method calls.
     * 
     * @see Helpers#filterByMinRelevance(double)
     * @see Helpers#compareByRelevance()
     */
    List<Recommendation<IMethodName>> getRecommendedCalls(Predicate<? super Recommendation<IMethodName>> filter,
            Comparator<? super Recommendation<IMethodName>> comparator, int maxLength);

}
