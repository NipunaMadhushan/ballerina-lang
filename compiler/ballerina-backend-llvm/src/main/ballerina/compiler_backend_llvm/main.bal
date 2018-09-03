import ballerina/io;

function main(string... args) {
    var (srcFilePath, destFilePath) = parseArgs(args);
    io:ByteChannel channel = openFileForReading(srcFilePath);
    checkBirValidity(channel);

    BirParser p = new(channel);
    p.readCp();
    genPackage(p.readPackage(), destFilePath, true);
}

function genObjectFile(byte[] birBinary, string destFilePath, boolean dumpLLVMIR) {
    io:ByteChannel channel = io:createMemoryChannel(birBinary);
    checkBirValidity(channel);

    BirParser p = new(channel);
    p.readCp();
    genPackage(p.readPackage(), destFilePath, dumpLLVMIR);
}

function parseArgs(string[] args) returns (string, string) {
    var argLen = lengthof args;
    if (argLen != 2){
        error err = { message: "Usage: compiler_backend_llvm <path-to-bir> <part-to-output-obj>" };
        throw err;
    }
    return (untaint args[0], untaint args[1]);
}

function checkBirValidity(io:ByteChannel channel) {
    checkMagic(channel);
    checkVersion(channel);
}

function checkMagic(io:ByteChannel channel) {
    byte[] baloCodeHexSpeak = [0xba, 0x10, 0xc0, 0xde];

    var (magic, _mustBe4) = check channel.read(4);
    if (!arrayEq(baloCodeHexSpeak, magic)){
        error err = { message: "Invalid BIR binary content, unexptected header" };
        throw err;
    }
}

function checkVersion(io:ByteChannel channel) {
    var birVersion = readInt(channel);
    var supportedBirVersion = 1;
    if (birVersion != 1){
        error err = { message: "Unsupported BIR version " + birVersion + ", supports version " + supportedBirVersion };
        throw err;
    }
}


function openFileForReading(string filePath) returns io:ByteChannel {
    io:ByteChannel channel = io:openFile(filePath, io:READ);
    return channel;
}

function arrayEq(byte[] x, byte[] y) returns boolean {
    var xLen = lengthof x;

    if xLen != lengthof y{
        return false;
    }

    int i;
    while i < xLen {
        if (x[i] != y[i]){
            return false;
        }
        i++;
    }
    return true;
}
