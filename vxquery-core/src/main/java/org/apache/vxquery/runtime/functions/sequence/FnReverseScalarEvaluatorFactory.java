/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.vxquery.runtime.functions.sequence;

import java.io.IOException;

import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluator;
import org.apache.hyracks.algebricks.runtime.base.IScalarEvaluatorFactory;
import org.apache.hyracks.api.context.IHyracksTaskContext;
import org.apache.hyracks.api.exceptions.HyracksDataException;
import org.apache.hyracks.data.std.api.IPointable;
import org.apache.hyracks.data.std.primitive.VoidPointable;
import org.apache.hyracks.data.std.util.ArrayBackedValueStorage;
import org.apache.vxquery.datamodel.accessors.SequencePointable;
import org.apache.vxquery.datamodel.accessors.TaggedValuePointable;
import org.apache.vxquery.datamodel.builders.sequence.SequenceBuilder;
import org.apache.vxquery.datamodel.values.ValueTag;
import org.apache.vxquery.exceptions.ErrorCode;
import org.apache.vxquery.exceptions.SystemException;
import org.apache.vxquery.runtime.functions.base.AbstractTaggedValueArgumentScalarEvaluator;
import org.apache.vxquery.runtime.functions.base.AbstractTaggedValueArgumentScalarEvaluatorFactory;

public class FnReverseScalarEvaluatorFactory extends AbstractTaggedValueArgumentScalarEvaluatorFactory {
    private static final long serialVersionUID = 1L;

    public FnReverseScalarEvaluatorFactory(IScalarEvaluatorFactory[] args) {
        super(args);
    }

    @Override
    protected IScalarEvaluator createEvaluator(IHyracksTaskContext ctx, IScalarEvaluator[] args)
            throws HyracksDataException {
        final ArrayBackedValueStorage abvs = new ArrayBackedValueStorage();
        final SequenceBuilder sb = new SequenceBuilder();
        final SequencePointable seq = (SequencePointable) SequencePointable.FACTORY.createPointable();
        final VoidPointable p = (VoidPointable) VoidPointable.FACTORY.createPointable();
        return new AbstractTaggedValueArgumentScalarEvaluator(args) {
            @Override
            protected void evaluate(TaggedValuePointable[] args, IPointable result) throws SystemException {
                try {
                    TaggedValuePointable tvp = args[0];
                    if (tvp.getTag() == ValueTag.SEQUENCE_TAG) {
                        abvs.reset();
                        sb.reset(abvs);
                        tvp.getValue(seq);
                        int seqLen = seq.getEntryCount();
                        for (int j = 0; j < seqLen; ++j) {
                            seq.getEntry(seqLen - j - 1, p);
                            sb.addItem(p);
                        }
                        sb.finish();
                        result.set(abvs);
                    } else {
                        result.set(tvp);
                    }
                } catch (IOException e) {
                    throw new SystemException(ErrorCode.SYSE0001);
                }
            }
        };
    }
}
