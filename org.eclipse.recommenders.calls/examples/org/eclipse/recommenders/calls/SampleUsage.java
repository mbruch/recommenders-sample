package org.eclipse.recommenders.calls;

import static org.eclipse.recommenders.calls.Helpers.*;

public class SampleUsage {

    void sample01(ICallRecommendationNet net) {
        net.getRecommendedCalls(filterByMinRelevance(0.3d), compareByRelevance(), 5);
        net.getRecommendedPatterns(filterByMinRelevance(0.3d), compareByRelevance(), 5);
        net.getRecommendedDefinitions(filterByMinRelevance(0.3d), compareByRelevance(), 5);
    }
}
