/*
 * Copyright 2009 Thomas Bocek
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.tomp2p.futures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.tomp2p.p2p.EvaluatingSchemeDHT;
import net.tomp2p.p2p.VotingSchemeDHT;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.Number480;
import net.tomp2p.peers.PeerAddress;

/**
 * The future object for put() operations including routing.
 * 
 * @author Thomas Bocek
 */
public class FuturePut extends FutureDHT<FuturePut> {
    // The minimum number of expected results. This is also used for put()
    // operations to decide if a future failed or not.
    private final int min;

    // Since we receive multiple results, we have an evaluation scheme to
    // simplify the result
    private final EvaluatingSchemeDHT evaluationScheme;

    // A pointer to the routing process that run before the DHT operations
    private FutureRouting futureRouting;

    private final List<Cancel> cleanup = new ArrayList<Cancel>(1);

    // Storage of results
    private Map<PeerAddress, Map<Number480, Byte>> rawResult;

    private Number160 locationKey;

    private Number160 domainKey;

    // Flag indicating if the minimum operations for put have been reached.
    private boolean minReached;

    /**
     * Default constructor.
     */
    public FuturePut() {
        this(0, new VotingSchemeDHT());
    }

    /**
     * Creates a new DHT future object that keeps track of the status of the DHT operations.
     * 
     * @param min
     *            The minimum of expected results
     * @param evaluationScheme
     *            The scheme to evaluate results from multiple peers
     */
    public FuturePut(final int min, final EvaluatingSchemeDHT evaluationScheme) {
        this.min = min;
        this.evaluationScheme = evaluationScheme;
        self(this);
    }

    /**
     * Finish the future and set the keys that have been stored. Success or failure is determined if the communication
     * was successful. This means that we need to further check if the other peers have denied the storage (e.g., due to
     * no storage space, no security permissions). Further evaluation can be retrieved with {@link #getAvgStoredKeys()}
     * or if the evaluation should be done by the user, use {@link #getRawKeys()}.
     * 
     * @param domainKey
     *            The domain key
     * @param locationKey
     *            The location key
     * @param rawKeys
     *            The keys that have been stored with information on which peer it has been stored
     * @param rawKeys480
     *            The keys with locationKey and domainKey Flag if the user requested putIfAbsent
     */
    public void setStoredKeys(final Number160 locationKey, final Number160 domainKey,
            final Map<PeerAddress, Map<Number480, Byte>> rawResult) {
        synchronized (lock) {
            if (!setCompletedAndNotify()) {
                return;
            }
            this.rawResult = rawResult;
            this.locationKey = locationKey;
            this.domainKey = domainKey;
            final int size = rawResult == null ? 0 : rawResult.size();
            this.minReached = size >= min;
            this.type = minReached ? FutureType.OK : FutureType.FAILED;
            this.reason = minReached ? "Minimun number of results reached" : "Expected " + min
                    + " result, but got " + size;
        }
        notifyListeners();
    }

    /**
     * @return The average keys received from the DHT. Only evaluates rawKeys.
     */
    public double getAvgStoredKeys() {
        synchronized (lock) {
            final int size = rawResult.size();
            int total = 0;
            for (Map<Number480, Byte> map : rawResult.values()) {
                Collection<Number480> collection = map.keySet();
                if (collection != null) {
                    total += collection.size();
                }
            }
            return total / (double) size;
        }
    }

    /**
     * Returns the raw result from the storage or removal operation.
     * 
     * @return The raw keys and the information which peer has been contacted
     */
    public Map<PeerAddress, Map<Number480, Byte>> getRawResult() {
        synchronized (lock) {
            return rawResult;
        }
    }

    /**
     * Checks if the minimum of expected results have been reached. This flag is also used for determining the success
     * or failure of this future for put and send_direct.
     * 
     * @return True, if expected minimum results have been reached.
     */
    public boolean isMinReached() {
        synchronized (lock) {
            return minReached;
        }
    }

    /**
     * Returns the keys that have been stored or removed after evaluation. The evaluation gets rid of the PeerAddress
     * information, by either a majority vote or cumulation. Use {@link FuturePut#getEvalKeys()} instead of this method.
     * 
     * @return The keys that have been stored or removed
     */
    public Collection<Number480> getResult() {
        synchronized (lock) {
            return evaluationScheme.evaluate7(rawResult);
        }
    }

    /**
     * @return The location key used for this future request
     */
    public Number160 getLocationKey() {
        synchronized (lock) {
            return locationKey;
        }
    }

    /**
     * 
     * @return The domain key used for this future request
     */
    public Number160 getDomainKey() {
        synchronized (lock) {
            return domainKey;
        }
    }

    /**
     * Returns the future object that was used for the routing. Before the FutureDHT is used, FutureRouting has to be
     * completed successfully.
     * 
     * @return The future object during the previous routing, or null if routing failed completely.
     */
    public FutureRouting getFutureRouting() {
        synchronized (lock) {
            return futureRouting;
        }
    }

    /**
     * Sets the future object that was used for the routing. Before the FutureDHT is used, FutureRouting has to be
     * completed successfully.
     * 
     * @param futureRouting
     *            The future object to set
     */
    public void setFutureRouting(final FutureRouting futureRouting) {
        synchronized (lock) {
            this.futureRouting = futureRouting;
        }
    }

    /**
     * Add cancel operations. These operations are called when a future is done, and we want to cancel all pending
     * operations.
     * 
     * @param cancellable
     *            The operation that can be canceled.
     */
    public void addCleanup(final Cancel cancellable) {
        synchronized (lock) {
            cleanup.add(cancellable);
        }
    }

    /**
     * Shutdown cancels all pending futures.
     */
    public void shutdown() {
        // Even though, this future is completed, there may be tasks than can be
        // canceled due to scheduled futures attached to this event.
        synchronized (lock) {
            for (final Cancel cancellable : cleanup) {
                cancellable.cancel();
            }
        }
    }
}
