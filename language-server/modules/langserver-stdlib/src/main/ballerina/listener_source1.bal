import ballerina/lang.'object as lang;

public class Listener {

    *lang:Listener;

    private int port = 0;

    public function __start() returns error? {
        return self.startEndpoint();
    }

    public function __gracefulStop() returns error? {
        return self.gracefulStop();
    }

    public function __immediateStop() returns error? {
        error err = error("not implemented");
        return err;
    }

    public function __attach(service object {} s, string[]? name = ()) returns error? {
        return self.register(s, name);
    }

    public function __detach(service object {} s) returns error? {
        return self.detach(s);
    }

    public function init(int port) {
    }
    
    public function initEndpoint() returns error? {
        return ();
    }

    function register(service object {} s, string? name) returns error? {
        return ();
    }

    function startEndpoint() returns error? {
        return ();
    }

    function gracefulStop() returns error? {
        return ();
    }

    function detach(service object {} s) returns error? {
        return ();
    }
}
