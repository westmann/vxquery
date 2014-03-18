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
package org.apache.vxquery.metadata;

import java.io.File;

import org.apache.commons.io.filefilter.IOFileFilter;

public class VXQueryIOFileFilter implements IOFileFilter {

    @Override
    public boolean accept(final File file) {
        return accept(file, file.getName());
    }

    @Override
    public boolean accept(final File file, final String name) {
        if (name.toLowerCase().endsWith(".xml") || name.toLowerCase().endsWith(".xml.gz")) {
            return true;
        }
        return false;
    }
}