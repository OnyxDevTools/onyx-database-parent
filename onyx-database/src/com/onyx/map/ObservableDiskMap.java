package com.onyx.map;

import com.onyx.map.base.ObservableCountDownLock;
import com.onyx.map.node.Header;
import com.onyx.map.store.ReclaimedSpace;
import com.onyx.map.store.Store;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by tosborn1 on 8/30/16.
 *
 * This class was created in order to get around having table locks in Onyx Database.
 * Rather than doing that, we observe whether or not we should have the ability to
 * clean up stale deleted or updated data.  If not, we preserve it so that the data is in the state that
 * it was when a query was started.  When we get a signal that there are no pending queries in progress,
 * we now have the ability to clean up the stale data.
 */
public class ObservableDiskMap<K, V> extends DefaultDiskMap<K, V> implements Observer {

    protected List<ReclaimedSpace> blocksPendingToDeallocate = new ArrayList();

    // Indicator to dictate whether we shall be allowed to deallocate or not
    private volatile boolean canDeallocate = true;

    /**
     * Default Constructor with Storage and initial header
     *
     * @since 1.0.2
     * @param fileStore Store where the data is being stored in
     * @param header The initial header in the parent map builder
     */
    @SuppressWarnings("unused")
    public ObservableDiskMap(Store fileStore, Header header) {
        super(fileStore, header);
    }

    /**
     * Default Constructor with Storage and initial header
     *
     * @since 1.0.2
     * @param fileStore Store where the data is being stored in
     * @param header The initial header in the parent map builder
     * @param inMemory indicates the map is in memory or not
     */
    @SuppressWarnings("unused")
    public ObservableDiskMap(Store fileStore, Header header, boolean inMemory) {
        super(fileStore, header, inMemory);
    }


    /**
     * Deallocate the storage at position.  Overridden to allow custom logic for de-allocation.
     * If the observable value tells us we can dealloc, great, lets do it asap otherwise, we put it
     * on a queue to do it later.
     *
     * @since 1.0.2
     * @param position The position where the de0allocated disk space starts
     * @param size amount of bytes to deallocate and recycle
     */
    @Override
    protected void dealloc(long position, int size)
    {
        if(canDeallocate) {
            fileStore.deallocate(position, size);
        }
        else
        {
            blocksPendingToDeallocate.add(new ReclaimedSpace(position, size));
        }
    }

    /**
     * Observable value listener
     * @param o Observable
     * @param arg the value
     */
    @Override
    public synchronized void update(Observable o, Object arg) {
        if(o instanceof ObservableCountDownLock) {
            this.canDeallocate = (boolean) arg;
            if (this.canDeallocate) {
                deallocAllPendingBlocks();
            }
        }
    }

    /**
     * Iterate through all the pending blocks and send them to the queue to deallocate
     */
    protected void deallocAllPendingBlocks()
    {
        for (ReclaimedSpace block : blocksPendingToDeallocate)
            dealloc(block.position, block.size);

        blocksPendingToDeallocate.clear();
    }

}
