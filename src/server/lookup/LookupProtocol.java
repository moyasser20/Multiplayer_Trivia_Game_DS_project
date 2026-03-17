package server.lookup;

public final class LookupProtocol {
    private LookupProtocol() {}

    public static final String REQ_PING = "PING";
    public static final String REQ_GET_ONE = "GET_ONE";
    public static final String REQ_GET_BATCH = "GET_BATCH";

    public static final String RES_PONG = "PONG";
    public static final String RES_OK = "OK";
    public static final String RES_NONE = "NONE";
    public static final String RES_ERR = "ERR";
    public static final String RES_END = "END";
}

