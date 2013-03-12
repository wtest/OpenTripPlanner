/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.algorithm;

import java.util.Collection;

import org.opentripplanner.common.pqueue.BinHeap;
import org.opentripplanner.common.pqueue.OTPPriorityQueue;
import org.opentripplanner.common.pqueue.OTPPriorityQueueFactory;
import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.services.SPTService;
import org.opentripplanner.routing.spt.EarliestArrivalShortestPathTree;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
 * Compute full SPT for earliest arrival problem. 
 * Always builds a full shortest path tree ("batch mode"). 
 * 
 * Note that walk limiting must be turned off -- resource limiting is not algorithmically correct.
 */
public class EarliestArrivalSPTService implements SPTService { 

    private static final Logger LOG = LoggerFactory.getLogger(EarliestArrivalSPTService.class);

    private boolean _verbose = false;

    @Override
    public ShortestPathTree getShortestPathTree(RoutingRequest req) {
        return getShortestPathTree(req, -1, null); // negative timeout means no timeout
    }
    
    @Override
    public ShortestPathTree getShortestPathTree(RoutingRequest req, double timeoutSeconds) {
        return this.getShortestPathTree(req, timeoutSeconds, null);
    }

    public ShortestPathTree getShortestPathTree(RoutingRequest options, double relTimeout,
            SearchTerminationStrategy terminationStrategy) {

        RoutingContext rctx = options.getRoutingContext();
        long abortTime = DateUtils.absoluteTimeout(relTimeout);
        ShortestPathTree spt = new EarliestArrivalShortestPathTree(options); 
        State initialState = new State(options);
        spt.add(initialState);

        OTPPriorityQueueFactory qFactory = BinHeap.FACTORY;
        int initialSize = rctx.graph.getVertices().size();
        initialSize = (int) Math.ceil(2 * (Math.sqrt((double) initialSize + 1)));
        OTPPriorityQueue<State> pq = qFactory.create(initialSize);
        pq.insert(initialState, 0);

        while (!pq.empty()) { // Until the priority queue is empty:
//            if (abortTime < Long.MAX_VALUE  && System.currentTimeMillis() > abortTime) {
//                LOG.warn("Search timeout. origin={} target={}", rctx.origin, rctx.target);
//                return null;
//            }
            State u = pq.extract_min();
            Vertex u_vertex = u.getVertex();
            Collection<Edge> edges = options.isArriveBy() ? u_vertex.getIncoming() : u_vertex.getOutgoing();
            for (Edge edge : edges) {
                for (State v = edge.traverse(u); v != null; v = v.getNextResult()) {
                    if (isWorstTimeExceeded(v, options)) {
                        continue;
                    }
                    if (spt.add(v)) {
                        pq.insert(v, v.getElapsedTime()); // activeTime?
                    } 
                }
            }
        }
        return spt;
    }

    private boolean isWorstTimeExceeded(State v, RoutingRequest opt) {
        if (opt.isArriveBy())
            return v.getTime() < opt.worstTime;
        else
            return v.getTime() > opt.worstTime;
    }

}