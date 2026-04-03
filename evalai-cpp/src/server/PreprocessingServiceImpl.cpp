#include "PreprocessingServiceImpl.h"
#include "../preprocessing/ImageProcessor.h"
#include <filesystem>
#include <iostream>
#include <sstream>
#include <future>      // ✅ add
#include <vector>      // ✅ add
#include <mutex>       // ✅ add

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

    int pageCount = request->pages_size();

    // ✅ Result struct to hold per-page output
    struct PageResult {
        int pageNumber;
        std::string rawPath;
        std::string cleanedPath;
        float skewAngle;
        bool anonymized;
        bool success;
        std::string errorMessage;
    };

    // ✅ Launch all pages in parallel with std::async
    std::vector<std::future<PageResult>> futures;

    for (int i = 0; i < pageCount; i++) {
        const ::preprocessing::RawImagePage& page = request->pages(i);

        std::string cleanedPath = buildCleanedImagePath(
            request->output_base_path(),
            request->exam_id(),
            request->student_id(),
            page.page_number()
        );

        std::string imagePath = page.image_path();
        int pageNumber = page.page_number();

        futures.push_back(std::async(std::launch::async,
            [imagePath, cleanedPath, pageNumber]() -> PageResult {
                ImageProcessor::ProcessResult result = ImageProcessor::processPage(
                    imagePath, cleanedPath, pageNumber
                );
                return PageResult{
                    pageNumber,
                    imagePath,
                    result.cleanedPath,
                    result.skewAngle,
                    result.anonymized,
                    result.success,
                    result.errorMessage
                };
            }
        ));
    }

    // ✅ Collect results in order
    int successCount = 0;
    int failCount = 0;

    // Store results indexed by page number for ordered insertion
    std::vector<PageResult> pageResults;
    for (auto& future : futures) {
        pageResults.push_back(future.get());
    }

    // Sort by page number to maintain order
    std::sort(pageResults.begin(), pageResults.end(),
        [](const PageResult& a, const PageResult& b) {
            return a.pageNumber < b.pageNumber;
        }
    );

    for (const auto& pr : pageResults) {
        ::preprocessing::CleanedImagePage* cleanedPage = response->add_pages();
        cleanedPage->set_page_number(pr.pageNumber);
        cleanedPage->set_raw_path(pr.rawPath);
        cleanedPage->set_skew_angle(pr.skewAngle);
        cleanedPage->set_anonymized(pr.anonymized);

        if (pr.success) {
            cleanedPage->set_cleaned_path(pr.cleanedPath);
            cleanedPage->set_status(::preprocessing::PAGE_STATUS_SUCCESS);
            successCount++;
            std::cout << "[INFO] Page " << pr.pageNumber
                << " processed | skew: " << pr.skewAngle
                << " | anonymized: " << pr.anonymized << std::endl;
        }
        else {
            cleanedPage->set_status(::preprocessing::PAGE_STATUS_FAILED);
            cleanedPage->set_error_message(pr.errorMessage);
            failCount++;
            std::cout << "[ERROR] Page " << pr.pageNumber
                << " failed: " << pr.errorMessage << std::endl;
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

// HealthCheck and helpers unchanged
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