package org.apache.vxquery.runtime.functions.datetime;

import java.io.DataOutput;
import java.io.IOException;

import org.apache.vxquery.datamodel.accessors.TaggedValuePointable;
import org.apache.vxquery.datamodel.api.ITimezone;
import org.apache.vxquery.datamodel.util.DateTime;
import org.apache.vxquery.datamodel.values.ValueTag;
import org.apache.vxquery.exceptions.ErrorCode;
import org.apache.vxquery.exceptions.SystemException;
import org.apache.vxquery.runtime.functions.base.AbstractTaggedValueArgumentScalarEvaluator;
import org.apache.vxquery.runtime.functions.base.AbstractTaggedValueArgumentScalarEvaluatorFactory;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.runtime.base.IScalarEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.data.std.api.IPointable;
import edu.uci.ics.hyracks.data.std.util.ArrayBackedValueStorage;

public abstract class AbstractValueFromDateTimeScalarEvaluatorFactory extends
        AbstractTaggedValueArgumentScalarEvaluatorFactory {
    private static final long serialVersionUID = 1L;

    public AbstractValueFromDateTimeScalarEvaluatorFactory(IScalarEvaluatorFactory[] args) {
        super(args);
    }

    @Override
    protected IScalarEvaluator createEvaluator(IHyracksTaskContext ctx, IScalarEvaluator[] args)
            throws AlgebricksException {
        final ArrayBackedValueStorage abvsInner = new ArrayBackedValueStorage();
        final DataOutput dOutInner = abvsInner.getDataOutput();

        return new AbstractTaggedValueArgumentScalarEvaluator(args) {
            @Override
            protected void evaluate(TaggedValuePointable[] args, IPointable result) throws SystemException {
                TaggedValuePointable tvp1 = args[0];
                if (tvp1.getTag() != getInputTag()) {
                    throw new SystemException(ErrorCode.FORG0006);
                }

                try {
                    abvsInner.reset();
                    switch (getReturnTag()) {
                        case ValueTag.XS_INTEGER_TAG:
                            dOutInner.write(ValueTag.XS_INTEGER_TAG);
                            dOutInner.writeLong(getInteger(tvp1));
                            break;
                        case ValueTag.XS_DAY_TIME_DURATION_TAG:
                            dOutInner.write(ValueTag.XS_DAY_TIME_DURATION_TAG);
                            dOutInner.writeLong(getInteger(tvp1));
                            break;
                        case ValueTag.XS_DECIMAL_TAG:
                            long value = getInteger(tvp1);
                            long decimalPlace = 3;

                            // Normalize to decimal.
                            if (value % 1000 == 0) {
                                value = value / 1000;
                                decimalPlace = 0;
                            } else if (value % 100 == 0) {
                                value = value / 100;
                                decimalPlace = 1;
                            } else if (value % 10 == 0) {
                                value = value / 10;
                                decimalPlace = 2;
                            }

                            dOutInner.write(ValueTag.XS_DECIMAL_TAG);
                            dOutInner.write((byte) decimalPlace);
                            dOutInner.writeLong(value);
                            break;
                    }

                    result.set(abvsInner);
                } catch (IOException e) {
                    throw new SystemException(ErrorCode.SYSE0001, e);
                }
            }
        };
    }

    protected abstract int getInputTag();

    protected int getReturnTag() {
        return ValueTag.XS_INTEGER_TAG;
    }

    protected abstract long getInteger(TaggedValuePointable tvp);

    protected long getTimezone(ITimezone timezonep) {
        return timezonep.getTimezoneHour() * DateTime.CHRONON_OF_HOUR + timezonep.getTimezoneMinute()
                * DateTime.CHRONON_OF_MINUTE;
    }

}