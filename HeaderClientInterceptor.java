package io.grpc.proxy;

import io.grpc.*;

import java.util.logging.Logger;

public class HeaderClientInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> CLIENT_HEADER_KEY =
            Metadata.Key.of("client-metadata", Metadata.ASCII_STRING_MARSHALLER);

    private static final Logger logger = Logger.getLogger(HeaderClientInterceptor.class.getName());

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> methodDescriptor,
                                                               CallOptions callOptions, Channel channel) {
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(channel.newCall(methodDescriptor, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                /* put custom header */
                headers.put(CLIENT_HEADER_KEY, "customRequestValueInClientInterceptor");
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onHeaders(Metadata headers) {
                        /**
                         * if you don't need receive header from server,
                         * you can use {@link io.grpc.stub.MetadataUtils#attachHeaders}
                         * directly to send header
                         */
                        logger.info(" =========>> header keys received from server:" + headers.keys().toString());
                        super.onHeaders(headers);
                    }
                }, headers);
            }
        };
    }
}
