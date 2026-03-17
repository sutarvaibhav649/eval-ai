#include "Anonymizer.h"
#include <opencv2/opencv.hpp>

bool Anonymizer::anonymize(cv::Mat& image, int pageNumber) {
    // Only anonymize page 1 — that's where student name and PRN appear
    if (pageNumber != 1) {
        return false;
    }

    if (image.empty()) {
        return false;
    }

    // Calculate mask height — top 15% of image
    int maskHeight = static_cast<int>(image.rows * MASK_HEIGHT_RATIO);

    // Define the region to mask (top portion)
    cv::Rect maskRegion(0, 0, image.cols, maskHeight);

    // Fill with white rectangle
    cv::rectangle(image, maskRegion, cv::Scalar(255, 255, 255), cv::FILLED);

    return true;
}