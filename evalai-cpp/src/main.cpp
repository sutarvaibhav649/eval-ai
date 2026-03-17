#include <iostream>
#include <string>
#include <grpcpp/grpcpp.h>
#include "server/PreprocessingServiceImpl.h"

void RunServer(const std::string& serverAddress)
{
    PreprocessingServiceImpl service;

    grpc::ServerBuilder builder;
    builder.AddListeningPort(serverAddress, grpc::InsecureServerCredentials());
    builder.RegisterService(&service);
    builder.SetMaxReceiveMessageSize(100 * 1024 * 1024);
    builder.SetMaxSendMessageSize(100 * 1024 * 1024);

    std::unique_ptr<grpc::Server> server(builder.BuildAndStart());

    std::cout << "[INFO] EvalAI C++ Preprocessing Service started" << std::endl;
    std::cout << "[INFO] Listening on: " << serverAddress << std::endl;
    std::cout << "[INFO] Press Ctrl+C to stop" << std::endl;

    // Block until server shuts down
    server->Wait();
}

int main(int argc, char** argv)
{
    std::string serverAddress = "0.0.0.0:50051";

    if (argc > 1) {
        serverAddress = "0.0.0.0:" + std::string(argv[1]);
    }

    std::cout << "[INFO] Starting EvalAI C++ Preprocessing Service..." << std::endl;
    RunServer(serverAddress);
    return 0;
}