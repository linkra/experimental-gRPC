package io.grpc.proxy;

import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.stub.StreamObserver;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.logging.Logger;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class FarmServer {
    private static final Logger logger = Logger.getLogger(FarmServer.class.getName());

    private  int port;
    private final Server server;
    private  Metadata headers;
    private final String certChainFilePath;
    private final String privateKeyFilePath;
    private final String trustCertCollectionFilePath;

    public FarmServer(int port, String certChainFilePath, String privateKeyFilePath, String trustCertCollectionFilePath)  throws IOException{

        this(port, FarmUtil.getDefaultVMSDataResponseFile(),
                certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath);



    }

    public FarmServer(int port, URL responseFile,  String certChainFilePath, String privateKeyFilePath, String trustCertCollectionFilePath) throws IOException {
        this(NettyServerBuilder.forAddress(new InetSocketAddress("localhost", port)),
                port, FarmUtil.parseResponse(responseFile), certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath);
    }

    /**
     * Create a RouteGuide server using serverBuilder as a base and features as data.
     */
    public FarmServer(NettyServerBuilder serverBuilder, int port, Collection<VMSDataResponse> response,
                      String certChainFilePath, String privateKeyFilePath, String trustCertCollectionFilePath) throws SSLException {
        this.certChainFilePath =  ClassLoader.getSystemResource(certChainFilePath).toString();
        this.privateKeyFilePath = ClassLoader.getSystemResource(privateKeyFilePath).toString();
        this.trustCertCollectionFilePath = ClassLoader.getSystemResource(trustCertCollectionFilePath).toString();
        this.port = port;
        server = serverBuilder
                .addService(ServerInterceptors.intercept(new FarmService(response), new HeaderServerInterceptor()))
                .sslContext(getSslContextBuilder().build())
                .build();
    }
    private SslContextBuilder getSslContextBuilder() {
        SslContextBuilder sslClientContextBuilder = SslContextBuilder.forServer(new File(certChainFilePath),
                new File(privateKeyFilePath));
        if (trustCertCollectionFilePath != null) {
            sslClientContextBuilder.trustManager(new File(trustCertCollectionFilePath));
            sslClientContextBuilder.clientAuth(ClientAuth.REQUIRE);
        }
        return GrpcSslContexts.configure(sslClientContextBuilder,
                SslProvider.OPENSSL);
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
        FarmServer server = new FarmServer(8980, "serverchain.pem" , "ui-key.pem" ,"root.pem");
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


