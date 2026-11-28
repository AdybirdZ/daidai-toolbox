package org.daidai.toolbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Publishes complete immutable GNSS snapshots across callback and UI threads. */
public final class StatusSatelliteStore<T> {
    public static final class Snapshot<T> {
        public final List<T> values;
        public final long revision;
        public final long capturedAt;

        private Snapshot(Collection<? extends T> values,long revision,long capturedAt){
            this.values=Collections.unmodifiableList(new ArrayList<>(values));
            this.revision=revision;
            this.capturedAt=capturedAt;
        }
    }

    private volatile Snapshot<T> current=new Snapshot<>(Collections.emptyList(),0,0);

    public Snapshot<T> snapshot(){return current;}

    public synchronized Snapshot<T> publish(Collection<? extends T> values,long capturedAt){
        Snapshot<T> next=new Snapshot<>(values,current.revision+1,capturedAt);
        current=next;
        return next;
    }
}
