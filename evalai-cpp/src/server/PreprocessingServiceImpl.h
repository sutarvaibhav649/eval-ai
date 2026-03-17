#pragma once
#include <grpcpp/grpcpp.h>
#include "preprocessing.grpc.pb.h"
#include "preprocessing.pb.h"

class PreprocessingServiceImpl final
    : public preprocessing::PreprocessingService::Service {

public:
    ::grpc::Status PreprocessStudentImages(
        ::grpc::ServerContext* context,
        const ::preprocessing::PreprocessRequest* request,
        ::preprocessing::PreprocessResponse* response
    ) override;

    ::grpc::Status HealthCheck(
        ::grpc::ServerContext* context,
        const ::preprocessing::HealthCheckRequest* request,
        ::preprocessing::HealthCheckResponse* response
    ) override;

private:
    std::string buildCleanedImagePath(
        const std::string& outputBasePath,
        const std::string& examId,
        const std::string& studentId,
        int pageNumber
    );

    bool createDirectories(const std::string& path);
};