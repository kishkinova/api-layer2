/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.product.eureka.client;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.zowe.apiml.product.eureka.client.ApimlPeerEurekaNode.ReplicationTaskProcessor;
import static org.zowe.apiml.product.eureka.client.TestableInstanceReplicationTask.ProcessingState;
import static org.zowe.apiml.product.eureka.client.TestableInstanceReplicationTask.aReplicationTask;

public class ReplicationTaskProcessorTest {

    private final TestableHttpReplicationClient replicationClient = new TestableHttpReplicationClient();

    private ReplicationTaskProcessor replicationTaskProcessor;

    @BeforeEach
    public void setUp() throws Exception {
        replicationTaskProcessor = new ReplicationTaskProcessor("peerId#test", replicationClient);
    }

    @Test
    public void testNonBatchableTaskExecution() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withReplyStatusCode(200).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.Success));
    }

    @Test
    public void testNonBatchableTaskCongestionFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withReplyStatusCode(503).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.Congestion));
        assertThat(task.getProcessingState(), is(TestableInstanceReplicationTask.ProcessingState.Pending));
    }

    @Test
    public void testNonBatchableTaskNetworkFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withNetworkFailures(1).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.TransientError));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }

    @Test
    public void testNonBatchableTaskSSLFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withException(new SSLException("handshake error")).withNetworkFailures(1).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.PermanentError));
    }

    @Test
    public void testNonBatchableTaskPermanentFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().withAction(Action.Heartbeat).withReplyStatusCode(406).build();
        ProcessingResult status = replicationTaskProcessor.process(task);
        assertThat(status, is(ProcessingResult.PermanentError));
        assertThat(task.getProcessingState(), is(ProcessingState.Failed));
    }

    @Test
    public void testBatchableTaskListExecution() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withBatchReply(200);
        replicationClient.withNetworkStatusCode(200);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Success));
        assertThat(task.getProcessingState(), is(ProcessingState.Finished));
    }

    @Test
    public void testBatchableTaskCongestionFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withNetworkStatusCode(503);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Congestion));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }

    @Test
    public void testBatchableTaskNetworkReadTimeOutHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withReadtimeOut(1);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Congestion));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }


    @Test
    public void testBatchableTaskNetworkFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withNetworkError(1);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.TransientError));
        assertThat(task.getProcessingState(), is(ProcessingState.Pending));
    }

    @Test
    public void testBatchableTaskSSLFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();

        replicationClient.withNetworkError(1);
        replicationClient.withException(new SSLException("handshake error"));
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.PermanentError));
    }

    @Test
    public void testBatchableTaskPermanentFailureHandling() throws Exception {
        TestableInstanceReplicationTask task = aReplicationTask().build();
        InstanceInfo instanceInfoFromPeer = ApimlPeerEurekaNodeTest.instanceInfo;

        replicationClient.withNetworkStatusCode(200);
        replicationClient.withBatchReply(400);
        replicationClient.withInstanceInfo(instanceInfoFromPeer);
        ProcessingResult status = replicationTaskProcessor.process(Collections.<ReplicationTask>singletonList(task));

        assertThat(status, is(ProcessingResult.Success));
        assertThat(task.getProcessingState(), is(ProcessingState.Failed));
    }
}
