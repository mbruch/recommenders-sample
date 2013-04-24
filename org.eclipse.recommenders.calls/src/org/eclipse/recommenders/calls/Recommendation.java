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

import static com.google.common.base.Objects.toStringHelper;
import static org.eclipse.recommenders.utils.Checks.*;

public class Recommendation<T> {

    public static <S, T extends S> Recommendation<S> newRecommendation(T proposal, double relevance) {
        return new Recommendation<S>(proposal, relevance);
    }

    private final T proposal;
    private final double relevance;

    protected Recommendation(T proposal, double relevance) {
        this.proposal = ensureIsNotNull(proposal, "proposal cannot be null");
        this.relevance = ensureIsInRange(relevance, 0, 1, "relevance '%f' must be in range [0..1]", relevance);
    }

    T getProposal() {
        return proposal;
    };

    double getRelevance() {
        return relevance;
    };

    @Override
    public String toString() {
        return toStringHelper(this).add("proposal", proposal).add("probability", String.format("%.4f", relevance))
                .toString();
    }
}
