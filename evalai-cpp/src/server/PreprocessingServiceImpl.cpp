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
                SEMAPHORE GUARD
-----------------------------------------------------------*/
struct SemaphoreGuard {
    std::counting_semaphore<>& sem;
    SemaphoreGuard(std::counting_semaphore<>& s) : sem(s) { sem.acquire(); }
    ~SemaphoreGuard() { sem.release(); }
};

/*-----------------------------------------------------------
                HEALTH CHECK
-----------------------------------------------------------*/
::grpc::Status PreprocessingServiceImpl::HealthCheck(
    ::grpc::ServerContext* context,
    const ::preprocessing::HealthCheckRequest* request,
    ::preprocessing::HealthCheckResponse* response)
{
    response->set_status("HEALTHY");
    response->set_version("1.2.0");
    response->set_message("C++ preprocessing service running");
    return ::grpc::Status::OK;
}

/*-----------------------------------------------------------
            MAIN FUNCTION
-----------------------------------------------------------*/
::grpc::Status PreprocessingServiceImpl::PreprocessStudentImages(
    ::grpc::ServerContext* context,
    const ::preprocessing::PreprocessRequest* request,
    ::preprocessing::PreprocessResponse* response)
{
    auto totalStart = std::chrono::high_resolution_clock::now();

    std::cout << "[TASK " << request->task_id()
        << "] Pages: " << request->pages_size() << std::endl;

    response->set_task_id(request->task_id());

    int cores = std::thread::hardware_concurrency();
    int MAX_THREADS = std::max(1, cores - 1);

    std::counting_semaphore<> semaphore(MAX_THREADS);

    struct PageResult {
        int pageNumber;
        std::string rawPath;
        std::string cleanedPath;
        bool success;
        std::string error;
    };

    std::vector<std::future<PageResult>> futures;

    for (const auto& page : request->pages()) {

        std::string cleanedPath = buildCleanedImagePath(
            request->output_base_path(),
            request->exam_id(),
            request->student_id(),
            page.page_number()
        );

        std::string imagePath = page.image_path();
        int pageNumber = page.page_number();

        futures.push_back(std::async(std::launch::async,
            [imagePath, cleanedPath, pageNumber, &semaphore]() {

                SemaphoreGuard guard(semaphore);

                try {
                    auto result = ImageProcessor::processPage(
                        imagePath, cleanedPath, pageNumber
                    );

                    return PageResult{
                        pageNumber,
                        imagePath,
                        result.success ? result.cleanedPath : imagePath,
                        result.success,
                        result.errorMessage
                    };

                }
                catch (const std::exception& e) {
                    return PageResult{
                        pageNumber,
                        imagePath,
                        imagePath,
                        false,
                        e.what()
                    };
                }
            }
        ));
    }

    std::vector<PageResult> results;
    for (auto& f : futures) {
        results.push_back(f.get());
    }

    std::sort(results.begin(), results.end(),
        [](const PageResult& a, const PageResult& b) {
            return a.pageNumber < b.pageNumber;
        });

    for (const auto& r : results) {
        auto* p = response->add_pages();

        p->set_page_number(r.pageNumber);
        p->set_raw_path(r.rawPath);
        p->set_cleaned_path(r.cleanedPath);

        if (r.success) {
            p->set_status(::preprocessing::PAGE_STATUS_SUCCESS);
        }
        else {
            p->set_status(::preprocessing::PAGE_STATUS_FAILED);
            p->set_error_message(r.error);
        }
    }

    response->set_status(::preprocessing::PREPROCESS_STATUS_COMPLETED);

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