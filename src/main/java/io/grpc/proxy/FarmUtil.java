package io.grpc.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import com.google.protobuf.util.JsonFormat;

public class FarmUtil {
    /**
     * Gets the file from classpath.
     */
    public static URL getDefaultVMSDataResponseFile() {
        return FarmServer.class.getResource("vms_data_db.json");
    }

    /**
     * Parses the JSON input file containing the list of features.
     */
    public static List<VMSDataResponse> parseResponse(URL file) throws IOException {
        InputStream input = file.openStream();
        try {
            Reader reader = new InputStreamReader(input, Charset.forName("UTF-8"));
            try {
                VMSDatabase.Builder database = VMSDatabase.newBuilder();
                JsonFormat.parser().merge(reader, database);
                return database.getResponseList();
            } finally {
                reader.close();
            }
        } finally {
            input.close();
        }
    }
}
