package io.grpc.proxy;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

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
        server = serverBuilder.addService(new FarmService(response)).build();
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

        private VMSDataResponse checkVMSDataResponse(VMSDataRequest request) {
            for (VMSDataResponse response : responses) {
                if (response.getVmsdata().getGuid().equals(request.getGuid())
                        && response.getVmsdata().getSourceid() == request.getSourceid()) {
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


