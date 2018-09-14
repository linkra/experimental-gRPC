package io.grpc.proxy;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.logging.Logger;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class FarmServer {
    private static final Logger logger = Logger.getLogger(FarmServer.class.getName());

    private  int port;
    private final Server server;

    public FarmServer(int port)  throws IOException{
        this(port, FarmUtil.getDefaultVMSDataResponseFile());
    }

    public FarmServer(int port, URL responseFile) throws IOException {
        this(ServerBuilder.forPort(port), port, FarmUtil.parseResponse(responseFile));
    }

    /**
     * Create a RouteGuide server using serverBuilder as a base and features as data.
     */
    public FarmServer(ServerBuilder<?> serverBuilder, int port, Collection<VMSDataResponse> response) {
        this.port = port;
        server = serverBuilder.addService((BindableService) new FarmService(response)).build();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may has been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                FarmServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main method.  This comment makes the linter happy.
     */
    public static void main(String[] args) throws Exception {
        FarmServer server = new FarmServer(8980);
        server.start();
        server.blockUntilShutdown();
    }

    private static class FarmService extends FarmGrpc.FarmImplBase {
        private final Collection<VMSDataResponse> responses;


        FarmService(Collection<VMSDataResponse> responses) {
            this.responses = responses;
        }

        @Override
        public void getFarmMessage(VMSDataRequest request, StreamObserver<VMSDataResponse> responseObserver) {
            responseObserver.onNext(checkVMSDataResponse(request));
            responseObserver.onCompleted();
        }

        @Override
        public void listFarmMessage(RequestWrapper request, StreamObserver<VMSDataResponse> responseObserver) {
            for (VMSDataResponse response : responses) {
                if (!FarmUtil.exists(response)) {
                    continue;
                }
                if (request.hasReq1() && request.hasReq2() && request.hasReq3() && request.hasReq4()) {
                    responseObserver.onNext(response);
                }
            }
            responseObserver.onCompleted();
        }

        @Override
        public void listFarmMessageBySmallWrapper(RequestWrapperSmall request, StreamObserver<VMSDataResponse> responseObserver) {
            for (VMSDataResponse response : responses) {
                if (!FarmUtil.exists(response)) {
                    continue;
                }
                if (request.hasReq1()) {
                    responseObserver.onNext(response);
                }
            }
            responseObserver.onCompleted();
        }

        private VMSDataResponse checkVMSDataResponse(VMSDataRequest request) {
            for (VMSDataResponse response : responses) {
                if (response.getItem().getGuid().equals(request.getItem().getGuid())
                        && response.getItem().getSourceid() == request.getItem().getSourceid()) {
                    return response;
                }
            }

            // FIXME!
            // No VMSDataResponse was found, return a new VMSDataResponse
            //return VMSDataResponse.newBuilder().setVmsdata(Item.newBuilder());
            logger.info("No VMSDataResponse was found!!");
            return null;
        }
    }
}


