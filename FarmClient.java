package io.grpc.proxy;

import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FarmClient {
    private static final Logger logger = Logger.getLogger(FarmClient.class.getName());

    private final ManagedChannel originChannel;
    private final FarmGrpc.FarmBlockingStub blockingStub;
    private final FarmGrpc.FarmStub asyncStub;

    public FarmClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
    }
    /**
     * Construct client for accessing Farm server using the existing channel.
     * With metadata and header interceptor
     */
    public FarmClient(ManagedChannelBuilder<?> channelBuilder) {
        originChannel = channelBuilder.build();

        ClientInterceptor interceptor = new HeaderClientInterceptor();
        Channel channel = ClientInterceptors.intercept(originChannel, interceptor);

        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("farm-request-metadata", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "This is the very important metadata from the very important farm");

        blockingStub = MetadataUtils.attachHeaders(FarmGrpc.newBlockingStub(channel), metadata);
        asyncStub = FarmGrpc.newStub(channel);
    }

    /**
     * Construct client for accessing Farm server with SSL/TLS at {@code host:port}.
     * FIXME! Got java.net.ConnectException: Connection refused: no further information: localhost/127.0.0.1:8980
     */
   /* public FarmClient(String host, int port) throws SSLException {
        this(NettyChannelBuilder.forAddress(host, port));
    }*/

    /**
     * Construct client for accessing Farm server using the existing channel.
     */
    public FarmClient(NettyChannelBuilder channelBuilder) throws SSLException {
        Path rootsPath = Paths.get("target/classes/root.pem");
        originChannel = channelBuilder
                .sslContext(GrpcSslContexts.forClient().trustManager(rootsPath.toFile()).build())
                .build();

        ClientInterceptor interceptor = new HeaderClientInterceptor();
        Channel channel = ClientInterceptors.intercept(originChannel, interceptor);

        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("farm-request-metadata", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "Message created in client!! This is the very important metadata from the very important farm");

        blockingStub = MetadataUtils.attachHeaders(FarmGrpc.newBlockingStub(channel), metadata);
        asyncStub = FarmGrpc.newStub(channel);
    }

    /**
     * Issues several different requests and then exits.
     */
    public static void main(String[] args) throws InterruptedException, SSLException {
        List<VMSDataResponse> responses;
        try {
            responses = FarmUtil.parseResponse(FarmUtil.getDefaultVMSDataResponseFile());
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        FarmClient client = new FarmClient("127.0.0.1", 8980);
        try {
            client.getVMSDataResponse("407838352", 456124);

            client.getVMSDataResponse("0", 0);

            client.listVMSDataResponseBySmallWrapper(createRequest("407838351", 456123));

            List<Item> items = new ArrayList<>();
            for (VMSDataResponse response : responses) {
                items.add(response.getItem());
            }
            client.getFarmsSummaryMessage(items, 3);

             // Send and receive some notes.
           CountDownLatch finishLatch = client.farmChat();

            if (!finishLatch.await(1, TimeUnit.MINUTES)) {
                client.warning("routeChat can not finish within 1 minutes");
            }
        } finally {
            client.shutdown();
        }
    }

    private static VMSDataRequest createRequest(String guid, int sourceid) {
        return VMSDataRequest.newBuilder().setItem(Item.newBuilder().setGuid(guid).setSourceid(sourceid)).build();
    }

    public void shutdown() throws InterruptedException {
        originChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
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

    public void getFarmsSummaryMessage(List<Item> items, int numRequests) throws InterruptedException {
        info("*** getFarmsSummaryMessage");
        final CountDownLatch finishLatch = new CountDownLatch(1);

        StreamObserver<FarmsSummary> farmsStreamObserver = new StreamObserver<FarmsSummary>() {
            @Override
            public void onNext(FarmsSummary summary) {
                info("Found {0} requests", summary.getVMSDataRequestCount());
            }

            @Override
            public void onError(Throwable t) {
                warning("getFarmsSummaryMessage failed: {0}", Status.fromThrowable(t));
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                info("Finished getFarmsSummaryMessage");
                finishLatch.countDown();
            }
        };

        StreamObserver<Item> streamObserver = asyncStub.getFarmsSummaryMessage(farmsStreamObserver);
        try {
            // Send numRequests randomly selected from the responses list.
            for (int i = 0; i < numRequests; ++i) {
                Item item = items.get(i);
                streamObserver.onNext(item);
                // Sleep for a bit before sending the next one.
                 Thread.sleep(5);
                info("Sending " + item);
                if (finishLatch.getCount() == 0) {
                    // RPC completed or errored before we finished sending.
                    // Sending further requests won't error, but they will just be thrown away.
                    return;
                }
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            streamObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        streamObserver.onCompleted();

        // Receiving happens asynchronously
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            warning("recordRoute can not finish within 1 minutes");
        }
    }

    public CountDownLatch farmChat() {
        info("*** FarmChat");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<ItemNote> requestObserver =
                asyncStub.farmChat(new StreamObserver<ItemNote>() {
                    @Override
                    public void onNext(ItemNote note) {
                        info("Got message \"{0}\" at {1}, {2}", note.getMessage(), note.getItem().getOwner()
                                , note.getItem().getSourceid());
                    }

                    @Override
                    public void onError(Throwable t) {
                        warning("FarmChat Failed: {0}", Status.fromThrowable(t));
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        info("Finished FarmChat");
                        finishLatch.countDown();
                    }
                });

        try {
            ItemNote[] requests =
                    {newNote("First message", "storbonden", 10), newNote("Second message", "månskensbonden", 30),
                            newNote("Third message", "lillbonden", 20), newNote("Fourth message", "mellanbonden", 40)};

            for (ItemNote request : requests) {
                info("Sending message \"{0}\" at {1}, {2}", request.getMessage(), request.getItem().getOwner(),
                        request.getItem().getSourceid());
                requestObserver.onNext(request);
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

        // return the latch while receiving happens asynchronously
        return finishLatch;
    }

    private ItemNote newNote(String message, String owner, int sourceid) {
        return ItemNote.newBuilder().setMessage(message)
                .setItem(Item.newBuilder().setOwner(owner).setSourceid(sourceid).build()).build();
    }


    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }
}
