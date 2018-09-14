package io.grpc.proxy;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FarmClient {
    private static final Logger logger = Logger.getLogger(FarmClient.class.getName());

    private final ManagedChannel channel;
    private final FarmGrpc.FarmBlockingStub blockingStub;
    private final FarmGrpc.FarmStub asyncStub;

    private Random random = new Random();

    /**
     * Construct client for accessing Farm server at {@code host:port}.
     */
    public FarmClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
    }

    /**
     * Construct client for accessing Farm server using the existing channel.
     */
    public FarmClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        blockingStub = FarmGrpc.newBlockingStub(channel);
        asyncStub = FarmGrpc.newStub(channel);
    }

    /** Issues several different requests and then exits. */
    public static void main(String[] args) throws InterruptedException {
        List<VMSDataResponse> responses;
        try {
            responses = FarmUtil.parseResponse(FarmUtil.getDefaultVMSDataResponseFile());
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        FarmClient client = new FarmClient("localhost", 8980);
        try {
            //client.getVMSDataResponse("407838352", 456124);

            //client.getVMSDataResponse("0", 0);

            /*client.listVMSDataResponse(createRequest("407838353", 456125),
                                                       createRequest("407838354",  456126),
                                                       createRequest("407838355",  456127),
                                                       createRequest("407838356",  456128));
            */
            client.listVMSDataResponseBySmallWrapper(createRequest("407838351", 456123));

            /*// Record a few randomly selected points from the features file.
            client.recordRoute(features, 10);

            // Send and receive some notes.
            CountDownLatch finishLatch = client.vmsChat();

            if (!finishLatch.await(1, TimeUnit.MINUTES)) {
                client.warning("routeChat can not finish within 1 minutes");
            }*/
        } finally {
            client.shutdown();
        }
    }

    private static VMSDataRequest createRequest(String guid, int sourceid) {
        return VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid(guid).setSourceid(sourceid)).build();
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void getVMSDataResponse(String guid, int sourceid) {
        info("*** getVMSDataResponse: guid={0} sourceid={1}", guid, sourceid);
        VMSDataRequest request = VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid(guid).setSourceid(sourceid)).build();

        VMSDataResponse vmsDataResponse;
        try {
            vmsDataResponse = blockingStub.getFarmMessage(request);
        } catch (StatusRuntimeException e) {
            warning("RPC failed: {0}", e.getStatus());
            return;
        }
        if (FarmUtil.exists(vmsDataResponse)) {
            info("Found vmsDataResponse with an item having guid {0} and  sourceid={1}", vmsDataResponse.getItem().getGuid(), vmsDataResponse.getItem().getSourceid());
        } else {
            info("Found no vmsDataResponse");
        }
    }

    /**
     * Blocking server-streaming example. Calls listFarmMessage with a requestWrapper. Prints each
     * response  as it arrives.
     */
    public void listVMSDataResponse(VMSDataRequest req1, VMSDataRequest req2, VMSDataRequest req3, VMSDataRequest req4) {
        info("*** listVMSDataResponse: req1={0} req2={1} req3={2} req4={3}", req1, req2, req3, req4);

        RequestWrapper requestWrapper =
                RequestWrapper.newBuilder()
                        .setReq1(VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid("407838353").setSourceid(456125)))
                        .setReq2(VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid("407838354").setSourceid(456126)))
                        .setReq3(VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid("407838355").setSourceid(456127)))
                        .setReq4(VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid("407838356").setSourceid(456128)))
                .build();

        Iterator<VMSDataResponse> responseIterator;
        try {
            responseIterator = blockingStub.listFarmMessage(requestWrapper);
            for (int i = 1; responseIterator.hasNext(); i++) {
                VMSDataResponse response = responseIterator.next();
                info("Result #" + i + ": {0}", response);
            }
        } catch (StatusRuntimeException e) {
            warning("RPC failed: {0}", e.getStatus());
        }
    }

    public void listVMSDataResponseBySmallWrapper(VMSDataRequest req1) {
        info("*** listVMSDataResponse: req1={0}", req1);

        RequestWrapperSmall requestWrapper =
                RequestWrapperSmall.newBuilder()
                        .setReq1(VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid("407838353").setSourceid(456125)))
                        .build();

        Iterator<VMSDataResponse> responseIterator;
        try {
            responseIterator = blockingStub.listFarmMessageBySmallWrapper(requestWrapper);
            for (int i = 1; responseIterator.hasNext(); i++) {
                VMSDataResponse response = responseIterator.next();
                info("Result #" + i + ": {0}", response);
            }
        } catch (StatusRuntimeException e) {
            warning("RPC failed: {0}", e.getStatus());
        }
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }
}
