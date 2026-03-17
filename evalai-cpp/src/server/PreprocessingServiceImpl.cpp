#include "PreprocessingServiceImpl.h"
#include "../preprocessing/ImageProcessor.h"
#include <filesystem>
#include <iostream>
#include <sstream>

::grpc::Status PreprocessingServiceImpl::HealthCheck(
    ::grpc::ServerContext* context,
    const ::preprocessing::HealthCheckRequest* request,
    ::preprocessing::HealthCheckResponse* response)
{
    response->set_status("HEALTHY");
    response->set_version("1.0.0");
    response->set_message("EvalAI C++ preprocessing service is running");
    return ::grpc::Status::OK;
}

::grpc::Status PreprocessingServiceImpl::PreprocessStudentImages(
    ::grpc::ServerContext* context,
    const ::preprocessing::PreprocessRequest* request,
    ::preprocessing::PreprocessResponse* response)
{
    std::cout << "[INFO] Preprocessing request received | task_id: "
        << request->task_id() << " | student: "
        << request->student_id() << " | pages: "
        << request->pages_size() << std::endl;

    response->set_task_id(request->task_id());

    int successCount = 0;
    int failCount = 0;

    for (int i = 0; i < request->pages_size(); i++) {
        const ::preprocessing::RawImagePage& page = request->pages(i);

        std::string cleanedPath = buildCleanedImagePath(
            request->output_base_path(),
            request->exam_id(),
            request->student_id(),
            page.page_number()
        );

        ImageProcessor::ProcessResult result = ImageProcessor::processPage(
            page.image_path(),
            cleanedPath,
            page.page_number()
        );

        ::preprocessing::CleanedImagePage* cleanedPage = response->add_pages();
        cleanedPage->set_page_number(page.page_number());
        cleanedPage->set_raw_path(page.image_path());
        cleanedPage->set_skew_angle(result.skewAngle);
        cleanedPage->set_anonymized(result.anonymized);

        if (result.success) {
            cleanedPage->set_cleaned_path(result.cleanedPath);
            cleanedPage->set_status(::preprocessing::PAGE_STATUS_SUCCESS);
            successCount++;
            std::cout << "[INFO] Page " << page.page_number()
                << " processed | skew: " << result.skewAngle
                << " | anonymized: " << result.anonymized << std::endl;
        }
        else {
            cleanedPage->set_status(::preprocessing::PAGE_STATUS_FAILED);
            cleanedPage->set_error_message(result.errorMessage);
            failCount++;
            std::cout << "[ERROR] Page " << page.page_number()
                << " failed: " << result.errorMessage << std::endl;
        }
    }

    if (failCount == 0) {
        response->set_status(::preprocessing::PREPROCESS_STATUS_COMPLETED);
    }
    else if (successCount > 0) {
        response->set_status(::preprocessing::PREPROCESS_STATUS_COMPLETED_WITH_FAILURES);
    }
    else {
        response->set_status(::preprocessing::PREPROCESS_STATUS_FAILED);
        response->set_error_message("All pages failed to process");
    }

    std::cout << "[INFO] Preprocessing complete | success: " << successCount
        << " | failed: " << failCount << std::endl;

    return ::grpc::Status::OK;
}

std::string PreprocessingServiceImpl::buildCleanedImagePath(
    const std::string& outputBasePath,
    const std::string& examId,
    const std::string& studentId,
    int pageNumber)
{
    std::filesystem::path basePath(outputBasePath);
    std::filesystem::path cleanedDir = basePath / "cleaned_images"
        / examId
        / studentId;
    std::filesystem::create_directories(cleanedDir);
    std::string filename = "page_" + std::to_string(pageNumber) + "_clean.png";
    return (cleanedDir / filename).string();
}

bool PreprocessingServiceImpl::createDirectories(const std::string& path)
{
    try {
        std::filesystem::create_directories(path);
        return true;
    }
    catch (const std::exception& e) {
        std::cerr << "[ERROR] Failed to create directories: "
            << e.what() << std::endl;
        return false;
    }
}