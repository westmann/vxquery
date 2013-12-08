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
package org.apache.vxquery.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.vxquery.compiler.CompilerControlBlock;
import org.apache.vxquery.compiler.algebricks.VXQueryGlobalDataFactory;
import org.apache.vxquery.context.DynamicContext;
import org.apache.vxquery.context.DynamicContextImpl;
import org.apache.vxquery.context.RootStaticContextImpl;
import org.apache.vxquery.context.StaticContextImpl;
import org.apache.vxquery.exceptions.SystemException;
import org.apache.vxquery.result.ResultUtils;
import org.apache.vxquery.xmlquery.ast.ModuleNode;
import org.apache.vxquery.xmlquery.query.Module;
import org.apache.vxquery.xmlquery.query.XMLQueryCompiler;
import org.apache.vxquery.xmlquery.query.XQueryCompilationListener;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.core.algebra.prettyprint.LogicalOperatorPrettyPrintVisitor;
import edu.uci.ics.hyracks.algebricks.core.algebra.prettyprint.PlanPrettyPrinter;
import edu.uci.ics.hyracks.api.client.HyracksConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.client.NodeControllerInfo;
import edu.uci.ics.hyracks.api.comm.IFrameTupleAccessor;
import edu.uci.ics.hyracks.api.dataset.IHyracksDataset;
import edu.uci.ics.hyracks.api.dataset.IHyracksDatasetReader;
import edu.uci.ics.hyracks.api.dataset.ResultSetId;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.client.dataset.HyracksDataset;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.common.controllers.CCConfig;
import edu.uci.ics.hyracks.control.common.controllers.NCConfig;
import edu.uci.ics.hyracks.control.nc.NodeControllerService;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ResultFrameTupleAccessor;

public class VXQuery {
    private final CmdLineOptions opts;

    private ClusterControllerService cc;
    private NodeControllerService[] ncs;
    private IHyracksClientConnection hcc;
    private IHyracksDataset hds;

    private ResultSetId resultSetId;

    /**
     * Constructor to use command line options passed.
     * 
     * @param opts
     *            Command line options object
     */
    public VXQuery(CmdLineOptions opts) {
        this.opts = opts;
    }

    /**
     * Main method to get command line options and execute query process.
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        Date start = new Date();
        final CmdLineOptions opts = new CmdLineOptions();
        CmdLineParser parser = new CmdLineParser(opts);

        // parse command line options, give error message if no arguments passed
        try {
            parser.parseArgument(args);
        } catch (Exception e) {
            parser.printUsage(System.err);
            return;
        }
        if (opts.arguments.isEmpty()) {
            parser.printUsage(System.err);
            return;
        }
        VXQuery vxq = new VXQuery(opts);
        vxq.execute();
        Date end = new Date();
        // if -timing argument passed, show the starting and ending times
        if (opts.timing) {
            System.out.println("Execution time: " + (end.getTime() - start.getTime()) + "ms");
        }

    }

    /**
     * Creates a new Hyracks connection with: the client IP address and port provided, if IP address is provided in command line. Otherwise create a new virtual
     * cluster with Hyracks nodes. Queries passed are run either way. After running queries, if a virtual cluster has been created, it is shut down.
     * 
     * @throws Exception
     */
    private void execute() throws Exception {
        if (opts.clientNetIpAddress != null) {
            hcc = new HyracksConnection(opts.clientNetIpAddress, opts.clientNetPort);
            runQueries();
        } else {
            if (!opts.compileOnly) {
                startLocalHyracks();
            }
            try {
                runQueries();
            } finally {
                if (!opts.compileOnly) {
                    stopLocalHyracks();
                }
            }
        }
    }

    /**
     * Reads the contents of the files passed in the list of arguments to a string. If -showquery argument is passed, output the query as string. Run the query
     * for the string.
     * 
     * @throws IOException
     * @throws SystemException
     * @throws Exception
     */
    private void runQueries() throws IOException, SystemException, Exception {
        for (String query : opts.arguments) {
            String qStr = slurp(query);
            if (opts.showQuery) {
                System.err.println(qStr);
            }
            XQueryCompilationListener listener = new XQueryCompilationListener() {

                /**
                 * On providing -showrp argument, output the query inputs, outputs and user constraints for each module as result of code generation.
                 * 
                 * @param module
                 */
                @Override
                public void notifyCodegenResult(Module module) {
                    if (opts.showRP) {
                        JobSpecification jobSpec = module.getHyracksJobSpecification();
                        System.err.println(jobSpec.toString());
                    }
                }

                /**
                 * On providing -showtet argument, output the syntax translation tree for the module in the format: "-- logical operator(if exists) | execution mode |"
                 * where execution mode can be one of: UNPARTITIONED,PARTITIONED,LOCAL
                 * 
                 * @param module
                 */
                @Override
                public void notifyTranslationResult(Module module) {
                    if (opts.showTET) {
                        try {
                            LogicalOperatorPrettyPrintVisitor v = new LogicalOperatorPrettyPrintVisitor();
                            StringBuilder buffer = new StringBuilder();
                            PlanPrettyPrinter.printPlan(module.getBody(), buffer, v, 0);
                            System.err.println(buffer.toString());
                        } catch (AlgebricksException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void notifyTypecheckResult(Module module) {
                }

                /**
                 * On providing -showoet argument, output the optimized expression tree for the module in the format:
                 * "-- logical operator(if exists) | execution mode |" where execution mode can be one of: UNPARTITIONED,PARTITIONED,LOCAL
                 * 
                 * @param module
                 */
                @Override
                public void notifyOptimizedResult(Module module) {
                    if (opts.showOET) {
                        try {
                            LogicalOperatorPrettyPrintVisitor v = new LogicalOperatorPrettyPrintVisitor();
                            StringBuilder buffer = new StringBuilder();
                            PlanPrettyPrinter.printPlan(module.getBody(), buffer, v, 0);
                            System.err.println(buffer.toString());
                        } catch (AlgebricksException e) {
                            e.printStackTrace();
                        }
                    }
                }

                /**
                 * On providing -showast argument, output the abstract syntax tree obtained from parsing by serializing the DomDriver object to a pretty-printed XML
                 * String.
                 * 
                 * @param moduleNode
                 */
                @Override
                public void notifyParseResult(ModuleNode moduleNode) {
                    if (opts.showAST) {
                        System.err.println(new XStream(new DomDriver()).toXML(moduleNode));
                    }
                }
            };

            XMLQueryCompiler compiler = new XMLQueryCompiler(listener, getNodeList(), opts.frameSize);
            resultSetId = createResultSetId();
            CompilerControlBlock ccb = new CompilerControlBlock(new StaticContextImpl(RootStaticContextImpl.INSTANCE),
                    resultSetId);
            compiler.compile(query, new StringReader(qStr), ccb, opts.optimizationLevel);
            if (opts.compileOnly) {
                continue;
            }

            Module module = compiler.getModule();
            JobSpecification js = module.getHyracksJobSpecification();

            DynamicContext dCtx = new DynamicContextImpl(module.getModuleContext());
            js.setGlobalJobDataFactory(new VXQueryGlobalDataFactory(dCtx.createFactory()));

            PrintWriter writer = new PrintWriter(System.out, true);
            // Repeat execution for number of times provided in -repeatexec argument
            for (int i = 0; i < opts.repeatExec; ++i) {
                runJob(js, writer);
            }
        }
    }

    /**
     * Get cluster node configuration.
     * 
     * @return Configuration of node controllers as array of Strings.
     * @throws Exception
     */
    private String[] getNodeList() throws Exception {
        Map<String, NodeControllerInfo> nodeControllerInfos = hcc.getNodeControllerInfos();
        String[] nodeList = new String[nodeControllerInfos.size()];
        int index = 0;
        for (String node : nodeControllerInfos.keySet()) {
            nodeList[index++] = node;
        }
        return nodeList;
    }

    /**
     * Creates a Hyracks dataset, if not already existing with the job frame size, and 1 reader. Allocates a new buffer of size specified in the frame of Hyracks
     * node. Creates new dataset reader with the current job ID and result set ID. Outputs the string in buffer for each frame.
     * 
     * @param spec
     *            JobSpecification object, containing frame size. Current specified job.
     * @param writer
     *            Writer for output of job.
     * @throws Exception
     */
    private void runJob(JobSpecification spec, PrintWriter writer) throws Exception {
        if (hds == null) {
            hds = new HyracksDataset(hcc, spec.getFrameSize(), 1);
        }

        JobId jobId = hcc.startJob(spec, EnumSet.of(JobFlag.PROFILE_RUNTIME));

        ByteBuffer buffer = ByteBuffer.allocate(spec.getFrameSize());
        IHyracksDatasetReader reader = hds.createReader(jobId, resultSetId);
        IFrameTupleAccessor frameTupleAccessor = new ResultFrameTupleAccessor(spec.getFrameSize());
        buffer.clear();

        while (reader.read(buffer) > 0) {
            buffer.clear();
            writer.print(ResultUtils.getStringFromBuffer(buffer, frameTupleAccessor));
            writer.flush();
        }

        hcc.waitForCompletion(jobId);
    }

    /**
     * Create a unique result set id to get the correct query back from the cluster.
     * 
     * @return Result Set id generated with current system time.
     */
    protected ResultSetId createResultSetId() {
        return new ResultSetId(System.nanoTime());
    }

    /**
     * Start local virtual cluster with cluster controller node and node controller nodes. IP address provided for node controller is localhost. Unassigned ports
     * 39000 and 39001 are used for client and cluster port respectively. Creates a new Hyracks connection with the IP address and client ports.
     * 
     * @throws Exception
     */
    public void startLocalHyracks() throws Exception {
        CCConfig ccConfig = new CCConfig();
        ccConfig.clientNetIpAddress = "127.0.0.1";
        ccConfig.clientNetPort = 39000;
        ccConfig.clusterNetIpAddress = "127.0.0.1";
        ccConfig.clusterNetPort = 39001;
        ccConfig.profileDumpPeriod = 10000;
        File outDir = new File("target/ClusterController");
        outDir.mkdirs();
        File ccRoot = File.createTempFile(VXQuery.class.getName(), ".data", outDir);
        ccRoot.delete();
        ccRoot.mkdir();
        ccConfig.ccRoot = ccRoot.getAbsolutePath();
        cc = new ClusterControllerService(ccConfig);
        cc.start();

        ncs = new NodeControllerService[opts.localNodeControllers];
        for (int i = 0; i < ncs.length; i++) {
            NCConfig ncConfig = new NCConfig();
            ncConfig.ccHost = "localhost";
            ncConfig.ccPort = 39001;
            ncConfig.clusterNetIPAddress = "127.0.0.1";
            ncConfig.dataIPAddress = "127.0.0.1";
            ncConfig.datasetIPAddress = "127.0.0.1";
            ncConfig.nodeId = "nc" + (i + 1);
            ncs[i] = new NodeControllerService(ncConfig);
            ncs[i].start();
        }

        hcc = new HyracksConnection(ccConfig.clientNetIpAddress, ccConfig.clientNetPort);
    }

    /**
     * Shuts down the virtual cluster, alongwith all nodes and node execution, network and queue managers.
     * 
     * @throws Exception
     */
    public void stopLocalHyracks() throws Exception {
        for (int i = 0; i < ncs.length; i++) {
            ncs[i].stop();
        }
        cc.stop();
    }

    /**
     * Reads the contents of file given in query into a String. The file is always closed. For XML files UTF-8 encoding is used.
     * 
     * @param query
     *            The query with filename to be processed
     * @return UTF-8 formatted query string
     * @throws IOException
     */
    private static String slurp(String query) throws IOException {
        return FileUtils.readFileToString(new File(query), "UTF-8");
    }

    /**
     * Helper class with fields and methods to handle all command line options
     */
    private static class CmdLineOptions {
        @Option(name = "-client-net-ip-address", usage = "IP Address of the ClusterController")
        public String clientNetIpAddress = null;

        @Option(name = "-client-net-port", usage = "Port of the ClusterController (default 1098)")
        public int clientNetPort = 1098;

        @Option(name = "-local-node-controllers", usage = "Number of local node controllers (default 1)")
        public int localNodeControllers = 1;

        @Option(name = "-frame-size", usage = "Frame size in bytes. (default 65536)")
        public int frameSize = 65536;

        @Option(name = "-O", usage = "Optimization Level. Default: Full Optimization")
        private int optimizationLevel = Integer.MAX_VALUE;

        @Option(name = "-showquery", usage = "Show query string")
        private boolean showQuery;

        @Option(name = "-showast", usage = "Show abstract syntax tree")
        private boolean showAST;

        @Option(name = "-showtet", usage = "Show translated expression tree")
        private boolean showTET;

        @Option(name = "-showoet", usage = "Show optimized expression tree")
        private boolean showOET;

        @Option(name = "-showrp", usage = "Show Runtime plan")
        private boolean showRP;

        @Option(name = "-compileonly", usage = "Compile the query and stop")
        private boolean compileOnly;

        @Option(name = "-repeatexec", usage = "Number of times to repeat execution")
        private int repeatExec = 1;

        @Option(name = "-timing", usage = "Produce timing information")
        private boolean timing;

        @Option(name = "-x", usage = "Bind an external variable")
        private Map<String, String> bindings = new HashMap<String, String>();

        @Argument
        private List<String> arguments = new ArrayList<String>();
    }

}
