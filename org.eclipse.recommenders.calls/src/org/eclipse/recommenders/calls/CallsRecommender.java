package org.eclipse.recommenders.calls;

import static org.eclipse.recommenders.calls.Helpers.*;
import static org.eclipse.recommenders.utils.Constants.UNKNOWN_METHOD;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.eclipse.recommenders.commons.bayesnet.BayesianNetwork;
import org.eclipse.recommenders.utils.Zips;
import org.eclipse.recommenders.utils.names.IMethodName;
import org.eclipse.recommenders.utils.names.VmTypeName;

public class CallsRecommender {

    private static final File MODELS = new File("jre-1.0.0-call.zip");
    private final ZipFile zip;

    public static void main(String[] args) throws Exception {
        CallsRecommender r = new CallsRecommender(MODELS);
        ObjectUsage query = createSampleQuery();

        for (Recommendation<IMethodName> rec : r.computeRecommendations(query)) {
            System.out.println(rec);
        }
    }

    private static ObjectUsage createSampleQuery() {
        ObjectUsage query = new ObjectUsage();
        query.type = VmTypeName.STRING;
        return query;
    }

    public CallsRecommender(File models) throws ZipException, IOException {
        zip = new ZipFile(models);
    }

    public List<Recommendation<IMethodName>> computeRecommendations(ObjectUsage query) throws IOException, Exception {
        String path = Zips.path(query.type, ".data");
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) {
            return Collections.emptyList();
        }
        BayesianNetwork bayesNet = BayesianNetwork.read(zip.getInputStream(entry));
        ICallRecommendationNet callNet = new JayesCallRecommendationNet(query.type, bayesNet);
        callNet.reset();
        callNet.setObservedEnclosingMethod(query.contextFirst);
        callNet.setObservedKind(query.kind);
        if (query.definition != null && !query.definition.equals(UNKNOWN_METHOD)) {
            callNet.setObservedDefinition(query.definition);
        }
        callNet.setObservedCalls(query.calls);
        // query the recommender:
        return callNet.getRecommendedCalls(filterByMinRelevance(0.1d), compareByRelevance(), 5);
    }
}
