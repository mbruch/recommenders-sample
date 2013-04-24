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

import com.google.common.base.Predicate;
import com.google.common.collect.Ordering;

public class Helpers {

    public static Predicate<Recommendation<?>> filterByMinRelevance(final double min) {
        return new Predicate<Recommendation<?>>() {
            @Override
            public boolean apply(Recommendation<?> r) {
                return r.getRelevance() >= min;
            }
        };
    }

    public static Comparator<Recommendation<?>> compareByRelevance() {
        return new Comparator<Recommendation<?>>() {

            @Override
            public int compare(Recommendation<?> o1, Recommendation<?> o2) {
                int res = -1 * Double.compare(o1.getRelevance(), o2.getRelevance());
                return res != 0 ? res : Ordering.usingToString().compare(o1, o2);
            }
        };
    }

}
