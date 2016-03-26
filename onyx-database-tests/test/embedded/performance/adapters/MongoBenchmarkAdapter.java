package embedded.performance.adapters;

import embedded.performance.framework.IBenchmarkAdapter;
import embedded.performance.types.MongoEntity;

import java.util.Date;

/**
 * Created by cosbor11 on 1/6/2015.
 */
public class MongoBenchmarkAdapter implements IBenchmarkAdapter<MongoEntity> {


    @Override
    public void initialize() {

    }

    @Override
    public void close() {

    }

    @Override
    public void clean() {

    }

    @Override
    public void populateRecord(MongoEntity record, int i)
    {
        record.id = Integer.toString(i);
        record.stringValue = record.id + "_StringValue";
        record.longPrimitive = new Long(i).longValue();
        record.longValue = new Long(i);
        record.booleanPrimitive = true;
        record.booleanValue = new Boolean(true);
        record.dateValue = new Date();
        record.intPrimitive = i;
        record.intValue = new Integer(i);
    }

    @Override
    public long timeToCreateXRecords(int x)
    {
        return 5000;
    }

    @Override
    public long timeToFetchXRecords(int x)
    {
        return 0;
    }
}
