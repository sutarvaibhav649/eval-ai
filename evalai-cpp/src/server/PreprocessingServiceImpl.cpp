#include "PreprocessingServiceImpl.h"
#include "../preprocessing/ImageProcessor.h"

#include <filesystem>
#include <iostream>
#include <future>
#include <vector>
#include <semaphore>
#include <algorithm>
#include <chrono>
#include <thread>

namespace fs = std::filesystem;

/*-----------------------------------------------------------
                HEALTH CHECK
-----------------------------------------------------------*/
::grpc::Status PreprocessingServiceImpl::HealthCheck(
    ::grpc::ServerContext* context,
    const ::preprocessing::HealthCheckRequest* request,
    ::preprocessing::HealthCheckResponse* response)
{
    response->set_status("HEALTHY");
    response->set_version("1.1.0");
    response->set_message("EvalAI C++ preprocessing service running (optimized)");
    return ::grpc::Status::OK;
}

/*-----------------------------------------------------------
            MAIN PREPROCESS FUNCTION
-----------------------------------------------------------*/
::grpc::Status PreprocessingServiceImpl::PreprocessStudentImages(
    ::grpc::ServerContext* context,
    const ::preprocessing::PreprocessRequest* request,
    ::preprocessing::PreprocessResponse* response)
{
    auto totalStart = std::chrono::high_resolution_clock::now();

    std::cout << "[INFO] Task: " << request->task_id()
        << " | Pages: " << request->pages_size() << std::endl;

    response->set_task_id(request->task_id());

    int pageCount = request->pages_size();

    struct PageResult {
        int pageNumber;
        std::string rawPath;
        std::string cleanedPath;
        float skewAngle;
        bool anonymized;
        bool success;
        std::string errorMessage;
        double timeMs;
    };

    // 🔥 Dynamic thread count
    int cores = std::thread::hardware_concurrency();
    int MAX_THREADS = std::max(1, cores - 1);

    std::counting_semaphore<> semaphore(MAX_THREADS);

    std::vector<std::future<PageResult>> futures;
    futures.reserve(pageCount);

    for (int i = 0; i < pageCount; i++) {
        const auto& page = request->pages(i);

        std::string cleanedPath = buildCleanedImagePath(
            request->output_base_path(),
            request->exam_id(),
            request->student_id(),
            page.page_number()
        );

        std::string imagePath = page.image_path();
        int pageNumber = page.page_number();

        futures.push_back(std::async(std::launch::async,
            [imagePath, cleanedPath, pageNumber, &semaphore]() -> PageResult {

                semaphore.acquire();

                auto start = std::chrono::high_resolution_clock::now();

                std::cout << "[INFO] Page " << pageNumber << " started\n";

                // 🔥 Load image
                cv::Mat image = cv::imread(imagePath);

                if (image.empty()) {
                    semaphore.release();
                    return PageResult{
                        pageNumber, imagePath, "", 0, false,
                        false, "Failed to load image", 0
                    };
                }

                // 🔥 Downscale large images (BIG PERFORMANCE BOOST)
                if (image.cols > 1200) {
                    double scale = 1200.0 / image.cols;
                    cv::resize(image, image, cv::Size(), scale, scale);
                }

                // 🔥 Process image
                ImageProcessor::ProcessResult result =
                    ImageProcessor::processPage(imagePath, cleanedPath, pageNumber);

                auto end = std::chrono::high_resolution_clock::now();
                double ms = std::chrono::duration<double, std::milli>(end - start).count();

                std::cout << "[INFO] Page " << pageNumber
                    << " done | " << ms << " ms\n";

                semaphore.release();

                return PageResult{
                    pageNumber,
                    imagePath,
                    result.cleanedPath,
                    result.skewAngle,
                    result.anonymized,
                    result.success,
                    result.errorMessage,
                    ms
                };
            }
        ));
    }

    // Collect results
    std::vector<PageResult> results;
    for (auto& f : futures) {
        results.push_back(f.get());
    }

    std::sort(results.begin(), results.end(),
        [](const PageResult& a, const PageResult& b) {
            return a.pageNumber < b.pageNumber;
        });

    int success = 0, fail = 0;

    for (const auto& r : results) {
        auto* page = response->add_pages();

        page->set_page_number(r.pageNumber);
        page->set_raw_path(r.rawPath);
        page->set_skew_angle(r.skewAngle);
        page->set_anonymized(r.anonymized);

        if (r.success) {
            page->set_cleaned_path(r.cleanedPath);
            page->set_status(::preprocessing::PAGE_STATUS_SUCCESS);
            success++;
        }
        else {
            page->set_status(::preprocessing::PAGE_STATUS_FAILED);
            page->set_error_message(r.errorMessage);
            fail++;
        }
    }

    if (fail == 0)
        response->set_status(::preprocessing::PREPROCESS_STATUS_COMPLETED);
    else if (success > 0)
        response->set_status(::preprocessing::PREPROCESS_STATUS_COMPLETED_WITH_FAILURES);
    else {
        response->set_status(::preprocessing::PREPROCESS_STATUS_FAILED);
        response->set_error_message("All pages failed");
    }

    auto totalEnd = std::chrono::high_resolution_clock::now();
    double totalMs = std::chrono::duration<double, std::milli>(totalEnd - totalStart).count();

    std::cout << "[INFO] Done | Success: " << success
        << " | Fail: " << fail
        << " | Time: " << totalMs << " ms\n";

    return ::grpc::Status::OK;
}

/*-----------------------------------------------------------
            PATH BUILDER
-----------------------------------------------------------*/
std::string PreprocessingServiceImpl::buildCleanedImagePath(
    const std::string& base,
    const std::string& examId,
    const std::string& studentId,
    int pageNumber)
{
    fs::path dir = fs::path(base) / "cleaned_images" / examId / studentId;
    fs::create_directories(dir);

    return (dir / ("page_" + std::to_string(pageNumber) + ".png")).string();
}

/*-----------------------------------------------------------
            DIRECTORY CREATOR
-----------------------------------------------------------*/
bool PreprocessingServiceImpl::createDirectories(const std::string& path)
{
    try {
        fs::create_directories(path);
        return true;
    }
    catch (...) {
        return false;
    }
}