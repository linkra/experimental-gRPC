package io.grpc.proxy;

import io.grpc.*;

import java.util.logging.Logger;

public class HeaderServerInterceptor implements ServerInterceptor {

    private static final Logger logger = Logger.getLogger(HeaderServerInterceptor.class.getName());

    static final Metadata.Key<String> SERVER_HEADER_KEY =
            Metadata.Key.of("server-metadata", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            final Metadata requestHeaders,
            ServerCallHandler<ReqT, RespT> next) {

        logger.info("------------->> header keys received from client:" + requestHeaders.keys().toString());

        return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void sendHeaders(Metadata responseHeaders) {
                responseHeaders.put(SERVER_HEADER_KEY, "customRespondValueFrom ServerInterceptor");
                super.sendHeaders(responseHeaders);
            }
        }, requestHeaders);
    }
}
