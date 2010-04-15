/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.vxquery.runtime.nodes;

import org.apache.vxquery.datamodel.NodeFactory;
import org.apache.vxquery.datamodel.atomic.AtomicValueFactory;
import org.apache.vxquery.datamodel.atomic.QNameValue;
import org.apache.vxquery.datamodel.atomic.StringValue;
import org.apache.vxquery.exceptions.SystemException;
import org.apache.vxquery.runtime.CallStackFrame;
import org.apache.vxquery.runtime.RegisterAllocator;
import org.apache.vxquery.runtime.base.AbstractEagerlyEvaluatedIterator;
import org.apache.vxquery.runtime.base.RuntimeIterator;

public class PIConstructorIterator extends AbstractEagerlyEvaluatedIterator {
    private final RuntimeIterator ni;
    private final RuntimeIterator ci;

    public PIConstructorIterator(RegisterAllocator rAllocator, RuntimeIterator ni, RuntimeIterator ci) {
        super(rAllocator);
        this.ni = ni;
        this.ci = ci;
    }

    @Override
    public Object evaluateEagerly(CallStackFrame frame) throws SystemException {
        final NodeFactory nf = frame.getRuntimeControlBlock().getNodeFactory();
        final StringValue target = (StringValue) ni.evaluateEagerly(frame);
        final AtomicValueFactory avf = frame.getRuntimeControlBlock().getAtomicValueFactory();
        final QNameValue targetQName = avf.createQName("", "", target.getStringValue());
        final StringValue data = (StringValue) ci.evaluateEagerly(frame);
        return nf.createProcessingInstruction(targetQName, data);
    }
}
